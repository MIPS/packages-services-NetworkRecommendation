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
import android.net.RssiCurve;
import android.net.ScoredNetwork;
import android.net.WifiKey;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

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
 *
 * To debug:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService addScore $SCORE
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService clear
 *
 * curve, metered, captive portal are optional, as expressed by an empty value.
 * SCORE: "Quoted SSID",bssid|$RSSI_CURVE|metered|captivePortal
 * RSSI_CURVE: start,bucketWidth,score,score,score,score
 * RSSI_CURVE:
 * Eg, A high quality, paid network with captive portal:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService addScore \
 *   '\"Paid\ Network\",aa:bb:cc:dd:ee:ff\|-150,10,-128,-128,-128,-128,-128,-128,-128,-128,29,29,29,29,29,-128\|1\|1'
 *
 * Eg, My network, a high quality, unmetered network:
 * $ adb shell dumpsys activity service DefaultNetworkRecommendationService addScore \
 *   '\"Free\ Network\",aa:bb:cc:dd:ee:ff\|-150,10,-128,-128,-128,-128,-128,-128,-128,-128,29,29,29,29,29,-128\|0\|1'
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
                (NetworkScoreManager) getSystemService(Context.NETWORK_SCORE_SERVICE),
                new ScoreStorage());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mProvider.getBinder();
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mProvider.dump(fd, writer, args);
    }

    /** Stores scores about networks. Initial implementation is in-memory-only. */
    @VisibleForTesting
    static class ScoreStorage {

        private final Object mScoresLock = new Object();
        @GuardedBy("mScoresLock")
        private final ArrayMap<NetworkKey, ScoredNetwork> mScores = new ArrayMap();

        void addScore(ScoredNetwork scoredNetwork) {
            synchronized (mScoresLock) {
                mScores.put(scoredNetwork.networkKey, scoredNetwork);
            }
        }

        ScoredNetwork get(NetworkKey key) {
            synchronized (mScoresLock) {
                return mScores.get(key);
            }
        }

        void clear() {
            synchronized (mScoresLock) {
                mScores.clear();
            }
        }

        void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            synchronized (mScoresLock) {
                for (ScoredNetwork score : mScores.values()) {
                    writer.println("" + score);
                }
            }
        }
    }

    @VisibleForTesting
    static class DefaultNetworkRecommendationProvider
            extends NetworkRecommendationProvider {

        private final NetworkScoreManager mScoreManager;
        private final ScoreStorage mStorage;

        private final Object mStatsLock = new Object();
        @GuardedBy("mStatsLock")
        private int mRecommendationCounter = 0;
        @GuardedBy("mStatsLock")
        private WifiConfiguration mLastRecommended = null;
        @GuardedBy("mStatsLock")
        private int mScoreCounter = 0;

        public DefaultNetworkRecommendationProvider(Handler handler,
                NetworkScoreManager scoreManager, ScoreStorage storage) {
            super(handler);
            mScoreManager = scoreManager;
            mStorage = storage;
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
            synchronized (mStatsLock) {
                mLastRecommended = recommendedConfig;
                mRecommendationCounter++;
            }
            callback.onResult(new RecommendationResult(recommendedConfig));
        }

        /** Score networks based on a few properties ... */
        public void onRequestScores(NetworkKey[] networks) {
            synchronized (mStatsLock) {
                mScoreCounter++;
            }
            List<ScoredNetwork> scoredNetworks = new ArrayList();
            for (int i = 0; i < networks.length; i++) {
                NetworkKey key = networks[i];

                // We only want to score wifi networks at the moment.
                if (key.type != NetworkKey.TYPE_WIFI) {
                    continue;
                }
            }
        }

        void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            for (int i = 0; i < args.length; i++) {
                if ("clear".equals(args[i])) {
                    i++;
                    mStorage.clear();
                } else if ("addScore".equals(args[i])) {
                    i++;
                    mStorage.addScore(parseScore(args[i]));
                }
            }
            synchronized (mStatsLock) {
                writer.println("Recommendation requests: " + mRecommendationCounter);
                writer.println("Last Recommended: " + mLastRecommended);
                writer.println("Score requests: " + mScoreCounter);
            }
        }

        @VisibleForTesting
        static ScoredNetwork parseScore(String score) {
            String[] splitScore = score.split("\\|");
            String[] splitWifiKey = splitScore[0].split(",");
            NetworkKey networkKey = new NetworkKey(
                    new WifiKey(splitWifiKey[0], splitWifiKey[1]));

            boolean meteredHint = "1".equals(splitScore[2]);
            Bundle attributes = new Bundle();
            if (!TextUtils.isEmpty(splitScore[3])) {
                attributes.putBoolean(ScoredNetwork.EXTRA_HAS_CAPTIVE_PORTAL,
                        "1".equals(splitScore[3]));
            }

            String[] splitRssiCurve = splitScore[1].split(",");
            int start = Integer.valueOf(splitRssiCurve[0]);
            int bucketWidth = Integer.valueOf(splitRssiCurve[1]);
            byte[] rssiBuckets = new byte[splitRssiCurve.length - 2];
            for (int i = 2; i < splitRssiCurve.length; i++) {
                rssiBuckets[i - 2] = Integer.valueOf(splitRssiCurve[i]).byteValue();
            }
            RssiCurve rssiCurve = new RssiCurve(start, bucketWidth, rssiBuckets, 0);
            return new ScoredNetwork(networkKey, rssiCurve, meteredHint, attributes);
        }
    }
}
