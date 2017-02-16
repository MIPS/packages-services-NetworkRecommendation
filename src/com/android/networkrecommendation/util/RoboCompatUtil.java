/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.networkrecommendation.util;

import android.content.res.Resources.Theme;
import android.graphics.drawable.Drawable;
import android.net.NetworkBadging;
import android.net.RecommendationRequest;
import android.net.RssiCurve;
import android.net.ScoredNetwork;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;
import java.util.List;

/**
 * This class provides access to @SystemApi methods that were added in Android O and not yet
 * available in using the --config robo_experimental configuration when testing.
 */
public class RoboCompatUtil {

    private static RoboCompatUtil mRoboCompatUtil;

    private RoboCompatUtil() {}

    /** Get a shared instance of this utility. */
    public static synchronized RoboCompatUtil getInstance() {
        if (mRoboCompatUtil == null) {
            mRoboCompatUtil = new RoboCompatUtil();
        }
        return mRoboCompatUtil;
    }

    @VisibleForTesting
    public static void setInstanceForTesting(RoboCompatUtil roboCompatUtil) {
        mRoboCompatUtil = roboCompatUtil;
    }

    /** Wraps creating a RecommendationResult.Builder. */
    @SuppressWarnings("unchecked")
    public RecommendationRequest createRecommendationRequest(List<ScanResult> scanResults) {
        return new RecommendationRequest.Builder()
                .setScanResults(scanResults.toArray(new ScanResult[scanResults.size()]))
                .build();
    }

    /** Wraps WifiManager.connect. */
    public void connectToWifi(WifiManager wifiManager, WifiConfiguration wifiConfiguration) {
        wifiManager.connect(wifiConfiguration, null /* actionListener */);
    }

    /** Wraps WifiConfiguration.hasNoInternetAccess. */
    @SuppressWarnings("unchecked")
    public boolean hasNoInternetAccess(WifiConfiguration wifiConfiguration) {
        return wifiConfiguration.hasNoInternetAccess();
    }

    /** Wraps WifiConfiguration.isNoInternetAccessExpected. */
    public boolean isNoInternetAccessExpected(WifiConfiguration wifiConfiguration) {
        return wifiConfiguration.isNoInternetAccessExpected();
    }

    /** Wraps WifiConfiguration.useExternalScores. */
    public boolean useExternalScores(WifiConfiguration wifiConfiguration) {
        return wifiConfiguration.useExternalScores;
    }

    /** Wraps WifiConfiguration.isPasspoint. */
    public boolean isPasspoint(WifiConfiguration wifiConfiguration) {
        return wifiConfiguration.isPasspoint();
    }

    /** Wraps ScoredNetwork.calculateBadge. */
    public int calculateBadge(ScoredNetwork scoredNetwork, int rssi) {
        return scoredNetwork.calculateBadge(rssi);
    }

    /** Wraps NetworkBadging.getWifiIcon. */
    public Drawable getWifiIcon(int signalLevel, int badging, Theme theme) {
        return NetworkBadging.getWifiIcon(signalLevel, badging, theme);
    }

    /** Wraps RssiCurve.activeNetworkRssiBoost. */
    public int activeNetworkRssiBoost(RssiCurve curve) {
        return curve.activeNetworkRssiBoost;
    }

    /** Wraps ScoredNetwork.attributes. */
    public Bundle attributes(ScoredNetwork scoredNetwork) {
        return scoredNetwork.attributes;
    }
}
