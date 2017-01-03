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
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
    private static final String TAG = "DefaultNetRecSvc";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private static final String WILDCARD_MAC = "00:00:00:00:00:00";

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

        @GuardedBy("mScores")
        private final ArrayMap<NetworkKey, ScoredNetwork> mScores = new ArrayMap();

        /**
         * Store a score in storage.
         *
         * @param scoredNetwork the network to score.
         *     If {@code scoredNetwork.networkKey.wifiKey.bssid} is "00:00:00:00:00:00", treat this
         *     score as applying to any bssid with the provided ssid.
         */
        public void addScore(ScoredNetwork scoredNetwork) {
            if (DEBUG) Log.d(TAG, "addScore: " + scoredNetwork);
            synchronized (mScores) {
                mScores.put(scoredNetwork.networkKey, scoredNetwork);
            }
        }

        public ScoredNetwork get(NetworkKey key) {
            synchronized (mScores) {
                // Try to find a score for the requested bssid.
                ScoredNetwork scoredNetwork = mScores.get(key);
                if (scoredNetwork != null) {
                    return scoredNetwork;
                }
                // Try to find a score for a wildcard ssid.
                NetworkKey wildcardKey = new NetworkKey(
                        new WifiKey(key.wifiKey.ssid, WILDCARD_MAC));
                scoredNetwork = mScores.get(wildcardKey);
                if (scoredNetwork != null) {
                    // If the fetched score was a wildcard score, construct a synthetic score
                    // for the requested bssid and return it.
                    return new ScoredNetwork(
                            key, scoredNetwork.rssiCurve, scoredNetwork.meteredHint,
                            scoredNetwork.attributes);
                }
                return null;
            }
        }

        public void clear() {
            synchronized (mScores) {
                mScores.clear();
            }
        }

        public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            synchronized (mScores) {
                for (ScoredNetwork score : mScores.values()) {
                    writer.println(score);
                }
            }
        }
    }

    @VisibleForTesting
    public static class DefaultNetworkRecommendationProvider
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
            int recommendedScore = Integer.MIN_VALUE;

            ScanResult[] results = request.getScanResults();
            if (results != null) {
                for (int i = 0; i < results.length; i++) {
                    final ScanResult scanResult = results[i];
                    if (VERBOSE) Log.v(TAG, "Scan: " + scanResult + " " + i);

                    // We only want to recommend open networks. This check is taken from
                    // places like WifiNotificationController and will be extracted to ScanResult in
                    // a future CL.
                    if (!"[ESS]".equals(scanResult.capabilities)) {
                        if (VERBOSE) Log.v(TAG, "Discarding closed network: " + scanResult);
                        continue;
                    }

                    final NetworkKey networkKey = new NetworkKey(
                            new WifiKey(quoteSsid(scanResult), scanResult.BSSID));
                    if (VERBOSE) Log.v(TAG, "Evaluating network: " + networkKey);

                    // We will only score networks we know about.
                    final ScoredNetwork network = mStorage.get(networkKey);
                    if (network == null) {
                        if (VERBOSE) Log.v(TAG, "Discarding unscored network: " + scanResult);
                        continue;
                    }

                    final int score = network.rssiCurve.lookupScore(scanResult.level);
                    if (VERBOSE) Log.d(TAG, "Scored " + scanResult + ": " + score);
                    if (score > recommendedScore) {
                        recommendedScanResult = scanResult;
                        recommendedScore = score;
                        if (VERBOSE) Log.d(TAG, "New recommended network: " + scanResult);
                        continue;
                    }
                }
            } else {
                Log.w(TAG, "Received null scan results in request.");
            }

            // If we ended up without a recommendation, recommend the provided configuration
            // instead. If we wanted the platform to avoid this network, too, we could send back an
            // empty recommendation.
            RecommendationResult recommendationResult;
            if (recommendedScanResult == null) {
                if (request.getCurrentSelectedConfig() != null) {
                    recommendationResult = RecommendationResult
                        .createConnectRecommendation(request.getCurrentSelectedConfig());
                } else {
                    recommendationResult = RecommendationResult.createDoNotConnectRecommendation();
                }
            } else {
                // Build a configuration based on the scan.
                WifiConfiguration recommendedConfig = new WifiConfiguration();
                recommendedConfig.SSID = quoteSsid(recommendedScanResult);
                recommendedConfig.BSSID = recommendedScanResult.BSSID;
                recommendedConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                recommendationResult = RecommendationResult
                    .createConnectRecommendation(recommendedConfig);
            }
            synchronized (mStatsLock) {
                mLastRecommended = recommendationResult.getWifiConfiguration();
                mRecommendationCounter++;
                if (DEBUG) Log.d(TAG, "Recommending network: " + configToString(mLastRecommended));
            }
            callback.onResult(recommendationResult);
        }

        /** Score networks based on a few properties ... */
        public void onRequestScores(NetworkKey[] networks) {
            synchronized (mStatsLock) {
                mScoreCounter++;
            }
            List<ScoredNetwork> scoredNetworks = new ArrayList<>();
            for (int i = 0; i < networks.length; i++) {
                NetworkKey key = networks[i];

                // Score a network if we know about it.
                ScoredNetwork scoredNetwork = mStorage.get(key);
                if (scoredNetwork != null) {
                    scoredNetworks.add(scoredNetwork);
                    continue;
                }

                // We only want to score wifi networks at the moment.
                if (key.type != NetworkKey.TYPE_WIFI) {
                    scoredNetworks.add(new ScoredNetwork(key, null, false /* meteredHint */));
                    continue;
                }

                // We don't know about this network, even though its a wifi network. Inject
                // an empty score to satisfy the cache.
                scoredNetworks.add(new ScoredNetwork(key, null, false /* meteredHint */));
                continue;
            }
            if (scoredNetworks.isEmpty()) {
                return;
            }

            if (DEBUG) Log.d(TAG, "Scored networks: " + scoredNetworks.toString());
            safelyUpdateScores(scoredNetworks.toArray(new ScoredNetwork[scoredNetworks.size()]));
        }

        void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
            for (int i = 0; i < args.length; i++) {
                if ("clear".equals(args[i])) {
                    i++;
                    clearScoresForTest();
                    writer.println("Clearing store");
                    return;
                } else if ("addScore".equals(args[i])) {
                    i++;
                    ScoredNetwork scoredNetwork = parseScore(args[i]);
                    addScoreForTest(scoredNetwork);
                    writer.println("Added: " + scoredNetwork);
                    return;
                } else {
                    writer.println("Unrecognized command: " + args[i]);
                }
            }
            mStorage.dump(fd, writer, args);
            synchronized (mStatsLock) {
                writer.println("Recommendation requests: " + mRecommendationCounter);
                writer.println("Last Recommended: " + configToString(mLastRecommended));
                writer.println("Score requests: " + mScoreCounter);
            }
        }

        @VisibleForTesting
        void addScoreForTest(ScoredNetwork scoredNetwork) {
            mStorage.addScore(scoredNetwork);
            if (!WILDCARD_MAC.equals(scoredNetwork.networkKey.wifiKey.bssid)) {
                safelyUpdateScores(new ScoredNetwork[] {scoredNetwork});
            }
        }

        @VisibleForTesting
        void clearScoresForTest() {
            mStorage.clear();
            safelyClearScores();
        }

        private void safelyUpdateScores(ScoredNetwork[] networkScores) {
            // Depending on races, etc, we might be alive when not the active scorer. Safely catch
            // and ignore security exceptions
            try {
                mScoreManager.updateScores(networkScores);
            } catch (SecurityException e) {
                Log.w(TAG, "Tried to update scores when not the active scorer.");
            }
        }

        private void safelyClearScores() {
            // Depending on races, etc, we might be alive when not the active scorer. Safely catch
            // and ignore security exceptions
            try {
                mScoreManager.clearScores();
            } catch (SecurityException e) {
                Log.w(TAG, "Tried to update scores when not the active scorer.");
            }
        }

        private static ScoredNetwork parseScore(String score) {
            String[] splitScore = score.split("\\|");
            String[] splitWifiKey = splitScore[0].split(",");
            NetworkKey networkKey = new NetworkKey(
                    new WifiKey(splitWifiKey[0], splitWifiKey[1]));

            boolean meteredHint = "1".equals(splitScore[2]);
            Bundle attributes = new Bundle();
            if (!TextUtils.isEmpty(splitScore[3])) {
                attributes.putBoolean(ScoredNetwork.ATTRIBUTES_KEY_HAS_CAPTIVE_PORTAL,
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

        /** Print a shorter config string, for dumpsys. */
        private static String configToString(WifiConfiguration config) {
            if (config == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder()
                    .append("ID=").append(config.networkId)
                    .append(",SSID=").append(config.SSID)
                    .append(",useExternalScores=" ).append(config.useExternalScores)
                    .append(",meteredHint=" ).append(config.meteredHint)
                    .append(",meteredOverride=" ).append(config.meteredOverride);
            return sb.toString();
        }

        /**
         * Add quotes to ScanResult ssids. WifiConfigurations, WifiKeys and ScoredNetworks have the
         * SSID quoted but scan results don't.
         */
        private static String quoteSsid(ScanResult scanResult) {
            if (scanResult.wifiSsid != null) {
                return "\"" + scanResult.wifiSsid.toString() + "\"";
            } else if (scanResult.SSID != null) {
                return "\"" + scanResult.SSID + "\"";
            } else {
                throw new IllegalArgumentException("ScanResult is missing an SSID.");
            }
        }
    }
}
