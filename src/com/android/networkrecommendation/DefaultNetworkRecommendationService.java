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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkScoreManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * Provides network recommendations for the platform.
 *
 * <p>This example evaluates networks in a scan and picks the "least bad" network, returning a
 * result to the RecommendedNetworkEvaluator, regardless of configuration point.
 *
 * <p>This recommender is not yet recommended for non-development devices.
 *
 * <p>To debug:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService
 *
 * <p>Clear stored scores:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService clear
 *
 * <p>Score a network:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService addScore $SCORE
 *
 * <p>SCORE: "Quoted SSID",bssid|$RSSI_CURVE|metered|captivePortal
 *
 * <p>RSSI_CURVE: start,bucketWidth,score,score,score,score,...
 *
 * <p>curve, metered and captive portal are optional, as expressed by an empty value.
 *
 * <p>All commands should be executed on one line, no spaces between each line of the command..
 * <p>Eg, A high quality, paid network with captive portal:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService addScore \
 * '\"Metered\",aa:bb:cc:dd:ee:ff\|
 * -150,10,-128,-128,-128,-128,-128,-128,-128,-128,27,27,27,27,27,-128\|1\|1'
 *
 * <p>Eg, A high quality, unmetered network with captive portal:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService addScore \
 * '\"Captive\",aa:bb:cc:dd:ee:ff\|
 * -150,10,-128,-128,-128,-128,-128,-128,-128,-128,28,28,28,28,28,-128\|0\|1'
 *
 * <p>Eg, A high quality, unmetered network with any bssid:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService addScore \
 * '\"AnySsid\",00:00:00:00:00:00\|
 * -150,10,-128,-128,-128,-128,-128,-128,-128,-128,29,29,29,29,29,-128\|0\|0'
 */
public class DefaultNetworkRecommendationService extends Service {

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private DefaultNetworkRecommendationProvider mProvider;
    private WifiNotificationController mWifiNotificationController;

    @Override
    public void onCreate() {
        mHandlerThread = new HandlerThread("RecommendationProvider");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mProvider = new DefaultNetworkRecommendationProvider(mHandler,
                (NetworkScoreManager) getSystemService(Context.NETWORK_SCORE_SERVICE),
                new DefaultNetworkRecommendationProvider.ScoreStorage());
        mWifiNotificationController = new WifiNotificationController(
                this, mHandler.getLooper(), null);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mProvider.getBinder();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mProvider.dump(fd, writer, args);
        mWifiNotificationController.dump(fd, writer, args);
    }
}
