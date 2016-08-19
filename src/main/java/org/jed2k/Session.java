package org.jed2k;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.*;

import org.jed2k.alert.Alert;
import org.jed2k.exception.BaseErrorCode;
import org.jed2k.exception.JED2KException;
import org.jed2k.exception.ErrorCode;
import org.jed2k.protocol.Hash;
import org.jed2k.protocol.NetworkIdentifier;
import org.jed2k.protocol.server.search.SearchRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Session extends Thread {
    private static Logger log = LoggerFactory.getLogger(Session.class);
    Selector selector = null;
    private ConcurrentLinkedQueue<Runnable> commands = new ConcurrentLinkedQueue<Runnable>();
    ServerConnection serverConection = null;
    private ServerSocketChannel ssc = null;

    Map<Hash, Transfer> transfers = new HashMap<Hash, Transfer>();
    ArrayList<PeerConnection> connections = new ArrayList<PeerConnection>(); // incoming connections
    Settings settings = null;
    long lastTick = Time.currentTime();
    HashMap<Integer, Hash> callbacks = new HashMap<Integer, Hash>();
    private ByteBuffer skipDataBuffer = null;
    BufferPool bufferPool = null;
    ExecutorService diskIOService = Executors.newSingleThreadExecutor();

    // from last established server connection
    int clientId    = 0;
    int tcpFlags    = 0;
    int auxPort     = 0;

    private BlockingQueue<Alert> alerts = new LinkedBlockingQueue<Alert>();

    public Session(final Settings st) {
        // TODO - validate settings before usage
        settings = st;
        bufferPool = new BufferPool(st.bufferPoolSize);
    }

    /**
     * start listening server socket
     */
    private void listen() {
        try {
            if (ssc != null) ssc.close();
        }
        catch(IOException e) {
            log.error("Unable to close server socket channel: {}", e.getMessage());
        }

        try {
            log.info("Start listening on port {}", settings.listenPort);
            ssc = ServerSocketChannel.open();
            ssc.socket().bind(new InetSocketAddress(settings.listenPort));
            ssc.configureBlocking(false);
            ssc.register(selector, SelectionKey.OP_ACCEPT);
        }
        catch(IOException e) {
            log.error("listen failed {}", e.getMessage());
        }
        finally {
            try {
                ssc.close();
                ssc = null;
            } catch(IOException e) {
                log.error("server socket close failed {}", e.getMessage());
            }
        }
    }

    /**
     * synchronized session internal processing method
     * @param ec
     * @throws IOException
     */
    private synchronized void on_tick(BaseErrorCode ec, int channelCount) {

        if (channelCount != 0) {
            // process channels
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while(keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();

                if (key.isValid()) {

                    if(key.isAcceptable()) {
                        // a connection was accepted by a ServerSocketChannel.
                        //log.trace("Key is acceptable");
                        incomingConnection();
                    } else if (key.isConnectable()) {
                        // a connection was established with a remote server/peer.
                        //log.trace("Key is connectable");
                        ((Connection)key.attachment()).onConnectable();
                    } else if (key.isReadable()) {
                        // a channel is ready for reading
                        //log.trace("Key is readable");
                        ((Connection)key.attachment()).onReadable();
                    } else if (key.isWritable()) {
                        // a channel is ready for writing
                        //log.trace("Key is writeable");
                        ((Connection)key.attachment()).onWriteable();
                    }
                }

                keyIterator.remove();
            }
        }

        /**
         * handle user's command and process internal tasks in
         * transfers, peers and other structures every 1 second
         */
        long tickIntervalMs = Time.currentTime() - lastTick;
        if (tickIntervalMs >= 1000) {
            lastTick = Time.currentTime();
            secondTick(Time.currentTime());
        }
    }

    public void secondTick(long currentSessionTime) {
        for(Map.Entry<Hash, Transfer> entry : transfers.entrySet()) {
            Hash key = entry.getKey();
            entry.getValue().secondTick(currentSessionTime);
        }

        // second tick on server connection
        if (serverConection != null) serverConection.secondTick(currentSessionTime);

        // TODO - run second tick on peer connections
        // execute user's commands
        Runnable r = commands.poll();
        while(r != null) {
            r.run();
            r = commands.poll();
        }

        connectNewPeers();
    }

    @Override
    public void run() {
        // TODO - remove all possible exceptions from this cycle!
        try {
            log.debug("Session started");
            selector = Selector.open();
            listen();

            while(!isInterrupted()) {
                int channelCount = selector.select(1000);
                Time.currentCachedTime = Time.currentTimeHiRes();
                on_tick(ErrorCode.NO_ERROR, channelCount);
            }
        }
        catch(IOException e) {
            log.error("session interrupted with error {}", e.getMessage());
        }
        finally {
            log.info("Session is closing");
            try {
                if (selector != null) selector.close();
            }
            catch(IOException e) {
                log.error("close selector failed {}", e.getMessage());
            }

            // stop service
            diskIOService.shutdown();

            for(final Transfer t: transfers.values()) {
                t.abort();
            }

            log.info("Session finsihed");
        }
    }

    /**
     * create new peer connection for incoming connection
     */
    void incomingConnection() {
        try {
            SocketChannel sc = ssc.accept();
            PeerConnection p = PeerConnection.make(sc, this);
            connections.add(p);
        }
        catch(IOException e) {
            log.error("Socket accept failed {}", e);
        }
        catch (JED2KException e) {
            log.error("Peer connection creation failed {}", e);
        }
    }

    void closeConnection(PeerConnection p) {
        connections.remove(p);
    }

    void openConnection(NetworkIdentifier point) throws JED2KException {
        if (findPeerConnection(point) == null) {
            PeerConnection p = PeerConnection.make(Session.this, point, null, null);
            if (p != null) connections.add(p);
            p.connect();
        }
    }

    public void connectoTo(final InetSocketAddress point) {
        commands.add(new Runnable() {
            @Override
            public void run() {
                if (serverConection != null) {
                    serverConection.close(ErrorCode.NO_ERROR);
                }

                try {
                    serverConection = ServerConnection.makeConnection(Session.this);
                    serverConection.connect(point);
                    NetworkIdentifier endpoint = new NetworkIdentifier(point);
                    log.debug("connect to server {}", endpoint);
                } catch(JED2KException e) {
                    // emit alert - connect to server failed
                    log.error("server connection failed {}", e);
                }
            }
        });
    }

    public void disconnectFrom() {
        commands.add(new Runnable() {
            @Override
            public void run() {
                if (serverConection != null) {
                    serverConection.close(ErrorCode.NO_ERROR);
                }
            }
        });
    }

    public void search(final SearchRequest value) {
        commands.add(new Runnable() {
            @Override
            public void run() {
                if (serverConection != null) {
                    serverConection.write(value);
                }
            }
        });
    }

    // TODO - remove only
    public void connectToPeer(final NetworkIdentifier point) {
        commands.add(new Runnable() {
            @Override
            public void run() {
                    try {
                        PeerConnection pc = PeerConnection.make(Session.this, point, null, null);
                        connections.add(pc);
                        pc.connect(point.toInetSocketAddress());
                    } catch(JED2KException e) {
                        log.error("new peer connection failed {}", e);
                    }
            }
        });
    }

    private PeerConnection findPeerConnection(NetworkIdentifier endpoint) {
        for(PeerConnection p: connections) {
            if (p.hasEndpoint() && endpoint.compareTo(p.getEndpoint()) == 0) return p;
        }

        return null;
    }

    /**
     *
     * @param s contains configuration parameters for session
     */
    public void configureSession(final Settings s) {
    	commands.add(new Runnable() {
			@Override
			public void run() {
				boolean relisten = (settings.listenPort != s.listenPort);
				settings = s;
				listen();
			}
    	});
    }

    public void pushAlert(Alert e) {
        assert(e != null);
        try {
            alerts.put(e);
        }
        catch (InterruptedException ex) {
            // handle exception
        }
    }

    public Alert  popAlert() {
        return alerts.poll();
    }

    public long getCurrentTime() {
        return lastTick;
    }

    /**
     * create new transfer in session or return previous
     * method synchronized with session second tick method
     * @param h hash of file(transfer)
     * @param size of file
     * @return TransferHandle with valid transfer of without
     */
    public final synchronized TransferHandle addTransfer(Hash h, long size, String filepath) throws JED2KException {
        Transfer t = transfers.get(h);

        if (t == null) {
            t = new Transfer(this, new AddTransferParams(h, size, filepath, false));
            transfers.put(h, t);
        }

        return new TransferHandle(this, t);
    }

    void removeTransfer(Hash h) {
        Transfer t = transfers.get(h);
        transfers.remove(h);
        if (t != null) {
            t.abort();
        }
    }

    void sendSourcesRequest(final Hash h, final long size) {
        if (serverConection != null) serverConection.sendFileSourcesRequest(h, size);
    }

    void connectNewPeers() {
        int stepsSinceLastConnect = 0;
        int maxConnectionsPerSecond = settings.maxConnectionsPerSecond;
        int numTransfers = transfers.size();
        boolean enumerateCandidates = true;

        if (numTransfers > 0 && connections.size() < settings.sessionConnectionsLimit) {
            //log.finest("connectNewPeers with transfers count " + numTransfers);
            while (enumerateCandidates) {
                for (Map.Entry<Hash, Transfer> entry : transfers.entrySet()) {
                    Hash key = entry.getKey();
                    Transfer t = entry.getValue();

                    if (t.wantMorePeers()) {
                        try {
                            if (t.tryConnectPeer(Time.currentTime())) {
                                --maxConnectionsPerSecond;
                                stepsSinceLastConnect = 0;
                            }
                        } catch (JED2KException e) {
                            log.error("exception on connect new peer {}", e);
                        }
                    }

                    ++stepsSinceLastConnect;

                    // if we have gone two whole loops without
                    // handing out a single connection, break
                    if (stepsSinceLastConnect > numTransfers*2) {
                        enumerateCandidates = false;
                        break;
                    }

                    // if we should not make any more connections
                    // attempts this tick, abort
                    if (maxConnectionsPerSecond == 0) {
                        enumerateCandidates = false;
                        break;
                    }
                }

                // must not happen :) but still
                if (transfers.isEmpty()) break;
            }
        }
    }

    /**
     * sometimes we need to skip some data received from peer
     * skip data buffer is one shared data buffer for all connections
     * @return byte buffer
     */
    ByteBuffer allocateSkipDataBufer() {
        if (skipDataBuffer == null) {
            skipDataBuffer = ByteBuffer.allocate(Constants.BLOCK_SIZE_INT);
        }

        return skipDataBuffer.duplicate();
    }

    @Override
    public String toString() {
        return "Session";
    }
}
