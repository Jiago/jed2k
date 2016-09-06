/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dkf.jdonkey;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import org.dkf.jed2k.android.ED2KService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Engine {
    private static final Logger LOG = LoggerFactory.getLogger(Engine.class);
    private ED2KService service;
    private ServiceConnection connection;
    private Context context;

    private static Engine instance;

    public synchronized static void create(Application context) {
        if (instance != null) {
            return;
        }
        instance = new Engine(context);
    }

    public static Engine instance() {
        if (instance == null) {
            throw new RuntimeException("Engine not created");
        }
        return instance;
    }

    private Engine(Application context) {
        this.context = context;
        startEngineService(context);
    }

    public void startServices() {
        if (service != null) {
            service.startServices();
        }
    }

    public void stopServices(boolean disconnected) {
        if (service != null) {
            service.stopServices();
        }
    }

    public void shutdown() {
        if (service != null) {
            if (connection != null) {
                try {
                    context.unbindService(connection);
                } catch (IllegalArgumentException e) {
                }
            }

            /*if (receiver != null) {
                try {
                    getApplication().unregisterReceiver(receiver);
                } catch (IllegalArgumentException e) {
                }
            }
            */

            service.shutdown();
        }
    }

    /**
     * @param context This must be the application context, otherwise there will be a leak.
     */
    private void startEngineService(final Context context) {
        Intent i = new Intent();
        i.setClass(context, ED2KService.class);
        context.startService(i);
        context.bindService(i, connection = new ServiceConnection() {
            public void onServiceDisconnected(ComponentName name) {

            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                if (service instanceof ED2KService.ED2KServiceBinder) {
                    Engine.this.service = ((ED2KService.ED2KServiceBinder) service).getService();
                    //registerStatusReceiver(context);
                } else {
                    throw new IllegalArgumentException("IBinder on service connected class is not instance of ED2KService.ED2KServiceBinder");
                }
            }
        }, Context.BIND_AUTO_CREATE);
    }
}