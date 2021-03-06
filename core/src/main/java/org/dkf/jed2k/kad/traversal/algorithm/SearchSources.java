package org.dkf.jed2k.kad.traversal.algorithm;

import org.dkf.jed2k.kad.Listener;
import org.dkf.jed2k.kad.NodeImpl;
import org.dkf.jed2k.kad.traversal.observer.Observer;
import org.dkf.jed2k.kad.traversal.observer.SearchObserver;
import org.dkf.jed2k.protocol.Endpoint;
import org.dkf.jed2k.protocol.kad.Kad2SearchSourcesReq;
import org.dkf.jed2k.protocol.kad.KadId;

/**
 * Created by inkpot on 08.12.2016.
 * direct search sources request
 */
public class SearchSources extends Direct {
    private long size;

    public SearchSources(NodeImpl ni, KadId t, long size, final Listener listener) {
        super(ni, t, listener);
        this.size = size;
    }

    @Override
    public Observer newObserver(Endpoint endpoint, KadId id, int portTcp, byte version) {
        return new SearchObserver(this, endpoint, id, portTcp, version);
    }

    @Override
    public boolean invoke(Observer o) {
        Kad2SearchSourcesReq ssr = new Kad2SearchSourcesReq();
        ssr.setTarget(target);
        ssr.getSize().assign(size);
        ssr.getStartPos().assign(0);
        return nodeImpl.invoke(ssr, o.getEndpoint(), o);
    }
}
