package org.dkf.jed2k.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;
import org.dkf.jed2k.R;
import org.dkf.jed2k.Session;
import org.dkf.jed2k.alert.Alert;
import org.dkf.jed2k.alert.SearchResultAlert;
import org.dkf.jed2k.alert.ServerMessageAlert;
import org.dkf.jed2k.alert.ServerStatusAlert;
import org.dkf.jed2k.protocol.server.search.SearchResult;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ED2KService extends Service {
    private final Logger log = LoggerFactory.getLogger(ED2KService.class);
    private Binder binder;
    private Session session;
    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    private final String NOTIFICATION_INTENT_OPEN = "org.dkf.jed2k.android.INTENT_OPEN";

    private final String NOTIFICATION_INTENT_CLOSE = "org.dkf.jed2k.android.INTENT_CLOSE";

    /**
     * Notification ID
     */
    private static final int NOTIFICATION_ID = 001;

    private int smallImage = R.drawable.default_art;

    /**
     * Notification manager
     */
    private NotificationManager mNotificationManager;

    public ED2KService() {
        binder = new ED2KServiceBinder();
        // create session here
        // start alerts loop
    }

    public class ED2KServiceBinder extends Binder {
        public ED2KService getService() {
            return ED2KService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();

        if (intent == null) {
            return 0;
        }

        String action = intent.getAction();

        if (action.equals(NOTIFICATION_INTENT_CLOSE)) {
            if (mNotificationManager != null)
                mNotificationManager.cancel(NOTIFICATION_ID);
        }
        else if (action.equals(NOTIFICATION_INTENT_OPEN)) {

        }

        log.info("ED2K service started by this intent: {} flags {} startId {}", intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        log.debug("ED2K service onDestroy");

        // stop alerts processing
        scheduledExecutorService.shutdown();

        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancelAll();

        // stop session
        session.interrupt();
        try {
            session.join();
        } catch (InterruptedException e) {
            log.error("wait session interrupted error {}", e);
        }
    }

    private void alertsLoop() {
        assert(session != null);
        scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                Alert a = session.popAlert();
                while(a != null) {
                    if (a instanceof SearchResultAlert) {
                        SearchResult sr = ((SearchResultAlert)a).results;
                    }
                    else if (a instanceof ServerMessageAlert) {
                        //System.out.println("Server message: " + ((ServerMessageAlert)a).msg);
                    }
                    else if (a instanceof ServerStatusAlert) {
                        ServerStatusAlert ssa = (ServerStatusAlert)a;
                        //System.out.println("Files count = " + ssa.filesCount + " users count = " + ssa.usersCount);
                    }
                    else {
                        //System.out.println("Unknown alert received: " + a.toString());
                    }

                    a = session.popAlert();
                }
            }
        },  100, 500, TimeUnit.MILLISECONDS);
    }

    private void buildNotification(final String fileName, final String fileHash, Bitmap artImage) {
        /**
         * Intents
         */
        Intent intentOpen = new Intent(NOTIFICATION_INTENT_OPEN);
        Intent intentClose = new Intent(NOTIFICATION_INTENT_CLOSE);

        /**
         * Pending intents
         */
        PendingIntent openPending = PendingIntent.getService(this, 0, intentOpen, 0);
        PendingIntent closePending = PendingIntent.getService(this, 0, intentClose, 0);

        /**
         * Remote view for normal view
         */

        RemoteViews mNotificationTemplate = new RemoteViews(this.getPackageName(), R.layout.notification);
        Notification.Builder notificationBuilder = new Notification.Builder(this);

        /**
         * set small notification texts and image
         */
        if (artImage == null)
            artImage = BitmapFactory.decodeResource(getResources(), R.drawable.default_art);

        mNotificationTemplate.setTextViewText(R.id.notification_line_one, fileName);
        mNotificationTemplate.setTextViewText(R.id.notification_line_two, fileHash);
        mNotificationTemplate.setImageViewResource(R.id.notification_play, R.drawable.btn_playback_pause /* : R.drawable.btn_playback_play*/);
        mNotificationTemplate.setImageViewBitmap(R.id.notification_image, artImage);

        /**
         * OnClickPending intent for collapsed notification
         */
        mNotificationTemplate.setOnClickPendingIntent(R.id.notification_collapse, openPending);
        mNotificationTemplate.setOnClickPendingIntent(R.id.notification_play, closePending);

        /**
         * Create notification instance
         */
        Notification notification = notificationBuilder
                .setSmallIcon(smallImage)
                .setContentIntent(openPending)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setContent(mNotificationTemplate)
                .setUsesChronometer(true)
                .build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;

        /**
         * Expanded notification
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {

            RemoteViews mExpandedView = new RemoteViews(this.getPackageName(), R.layout.notification_expanded);

            mExpandedView.setTextViewText(R.id.notification_line_one, fileName);
            mExpandedView.setTextViewText(R.id.notification_line_two, fileHash);
            mExpandedView.setImageViewResource(R.id.notification_expanded_play, R.drawable.btn_playback_pause/* : R.drawable.btn_playback_play*/);
            mExpandedView.setImageViewBitmap(R.id.notification_image, artImage);

            mExpandedView.setOnClickPendingIntent(R.id.notification_collapse, openPending);
            mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_play, closePending);
            notification.bigContentView = mExpandedView;
        }

        if (mNotificationManager != null)
            mNotificationManager.notify(NOTIFICATION_ID, notification);
    }

    public void updateNotification(final String fileName, final String fileHash, int smallImage, int artImage) {
        updateNotification(fileName, fileHash, smallImage, BitmapFactory.decodeResource(getResources(), artImage));
    }

    public void updateNotification(final String fileName, final String fileHash, int smallImage, Bitmap artImage) {
        this.smallImage = smallImage;
        buildNotification(fileName, fileHash, artImage);
    }
}