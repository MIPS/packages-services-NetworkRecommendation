/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.networkrecommendation;

import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources;
import android.net.NetworkScoreManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import com.android.networkrecommendation.notify.WifiNotificationController;
import com.android.networkrecommendation.notify.WifiNotificationHelper;
import com.android.networkrecommendation.wakeup.WifiWakeupController;
import com.android.networkrecommendation.wakeup.WifiWakeupNetworkSelector;
import com.android.networkrecommendation.wakeup.WifiWakeupNotificationHelper;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Provides network recommendations for the platform.
 */
public class NetworkRecommendationService extends Service {

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private DefaultNetworkRecommendationProvider mProvider;
    private WifiNotificationController mWifiNotificationController;
    private WifiWakeupController mWifiWakeupController;

    @Override
    public void onCreate() {
        mHandlerThread = new HandlerThread("RecommendationProvider");
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        mHandler = new Handler(looper);
        NetworkScoreManager networkScoreManager = getSystemService(NetworkScoreManager.class);
        mProvider = new DefaultNetworkRecommendationProvider(mHandler,
                networkScoreManager, new DefaultNetworkRecommendationProvider.ScoreStorage());
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        WifiManager wifiManager = getSystemService(WifiManager.class);
        Resources resources = getResources();
        ContentResolver contentResolver = getContentResolver();
        mWifiNotificationController = new WifiNotificationController(
                this, contentResolver, new Handler(looper), mProvider,
                wifiManager, notificationManager,
                new WifiNotificationHelper(this, mProvider));
        WifiWakeupNetworkSelector wifiWakeupNetworkSelector =
                new WifiWakeupNetworkSelector(resources);
        WifiWakeupNotificationHelper wifiWakeupNotificationHelper =
                new WifiWakeupNotificationHelper(this, resources, new Handler(looper),
                        notificationManager, wifiManager);
        mWifiWakeupController = new WifiWakeupController(this, contentResolver, looper,
                wifiManager, wifiWakeupNetworkSelector, wifiWakeupNotificationHelper);
    }

    @Override
    public IBinder onBind(Intent intent) {
        mWifiWakeupController.start();
        mWifiNotificationController.start();
        return mProvider.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mWifiWakeupController.stop();
        mWifiNotificationController.stop();
        return super.onUnbind(intent);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mProvider.dump(fd, writer, args);
        mWifiNotificationController.dump(fd, writer, args);
        mWifiWakeupController.dump(fd, writer, args);
    }
}
