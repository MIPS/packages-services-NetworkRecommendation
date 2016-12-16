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
import android.net.NetworkKey;
import android.net.NetworkRecommendationProvider;
import android.net.NetworkScoreManager;
import android.net.RecommendationRequest;
import android.net.RecommendationResult;
import android.net.ScoredNetwork;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import com.android.internal.annotations.GuardedBy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides network recommendations for the platform.
 *
 * This example evaluates networks in a scan and picks the "least bad" network, returning a result
 * to the RecommendedNetworkEvaluator, regardless of configuration point.
 *
 * This recommender is not yet recommended for non-development devices.
 */
public class DefaultNetworkRecommendationService extends Service {
    private static final String TAG = "DefaultNetRecSvc";

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private DefaultNetworkRecommendationProvider mProvider;

    @Override
    public void onCreate() {
        mHandlerThread = new HandlerThread("RecommendationProvider");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mProvider = new DefaultNetworkRecommendationProvider(mHandler,
                (NetworkScoreManager) getSystemService(Context.NETWORK_SCORE_SERVICE));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mProvider.getBinder();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mProvider.dump(fd, writer, args);
    }

    static class DefaultNetworkRecommendationProvider
            extends NetworkRecommendationProvider {

        private final NetworkScoreManager mScoreManager;

        @GuardedBy("this")
        private int mRecommendationCounter = 0;
        @GuardedBy("this")
        private WifiConfiguration mLastRecommended = null;
        @GuardedBy("this")
        private int mScoreCounter = 0;

        public DefaultNetworkRecommendationProvider(Handler handler,
                NetworkScoreManager scoreManager) {
            super(handler);
            mScoreManager = scoreManager;
        }

        /** Recommend the wireless network with the highest RSSI. */
        @Override
        public void onRequestRecommendation(RecommendationRequest request,
                NetworkRecommendationProvider.ResultCallback callback) {
            ScanResult recommendedScanResult = null;
            ScanResult[] results = request.getScanResults();
            for (int i = 0; i < results.length; i++) {
                ScanResult result = results[i];

                // We only want to recommend open networks. This check is taken from
                // places like WifiNotificationController and will be extracted to ScanResult in
                // a future CL.
                if (!"[ESS]".equals(result.capabilities)) {
                    continue;
                }
                // If we don't have a candidate recommendation, use the first network we see.
                if (recommendedScanResult == null) {
                    recommendedScanResult = result;
                    continue;
                }
                // If the candidate network has a higher rssi, use it.
                if (result.level > recommendedScanResult.level) {
                    recommendedScanResult = result;
                    continue;
                }
            }

            if (recommendedScanResult == null) {
                callback.onResult(new RecommendationResult(null));
                return;
            }

            // Build a configuration based on the scan.
            WifiConfiguration recommendedConfig = new WifiConfiguration();
            recommendedConfig.SSID = "\"" + recommendedScanResult.SSID + "\"";
            recommendedConfig.BSSID = recommendedScanResult.BSSID;
            recommendedConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            synchronized (this) {
                mLastRecommended = recommendedConfig;
                mRecommendationCounter++;
            }
            callback.onResult(new RecommendationResult(recommendedConfig));
        }

        /** Score networks based on a few properties ... */
        public void onRequestScores(NetworkKey[] networks) {
            synchronized (this) {
                mScoreCounter++;
            }
            List<ScoredNetwork> scoredNetworks = new ArrayList();
            for (int i = 0; i < networks.length; i++) {
                NetworkKey key = networks[i];

                // We only want to score wifi networks at the moment.
                if (key.type != NetworkKey.TYPE_WIFI) {
                    continue;
                }

                // TODO: Develop a scoring algorithm.
                //ScoredNetwork score = new ScoredNetwork(key, null, false /* meteredHint */,
                //        new Bundle());
                //scoredNetworks.add(score);
            }
            mScoreManager.updateScores(
                    scoredNetworks.toArray(new ScoredNetwork[scoredNetworks.size()]));
        }

        void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            synchronized (this) {
                writer.print("Recommendation requests: " + mRecommendationCounter);
                writer.print("Last Recommended: " + mLastRecommended);
                writer.print("Score requests: " + mScoreCounter);
            }
        }
    }
}
