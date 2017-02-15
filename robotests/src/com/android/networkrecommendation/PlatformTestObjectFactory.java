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
package com.android.networkrecommendation;

import android.net.NetworkKey;
import android.net.WifiKey;
import android.net.wifi.ScanResult;

/** Creates platform objects which can be used for testing. */
public class PlatformTestObjectFactory {

    private PlatformTestObjectFactory() {} // do not instantiate

    /** Use reflection to create a ScanResult. */
    public static ScanResult createScanResult(
            String unquotedSsid, String bssid, int level, boolean open) {
        try {
            ScanResult scanResult = ScanResult.class.getConstructor().newInstance();
            scanResult.capabilities = open ? "[ESS]" : "[WEP]";
            scanResult.SSID = unquotedSsid;
            scanResult.BSSID = bssid;
            scanResult.level = level;
            return scanResult;
        } catch (Exception e) {
            return null;
        }
    }

    /** Use reflection to create an open network ScanResult. */
    public static ScanResult createOpenNetworkScanResult(String ssid, String bssid) {
        return createScanResult(ssid, bssid, 1, true);
    }

    /** Use reflection to create a closed network ScanResult. */
    public static ScanResult createClosedNetworkScanResult(String unquotedSsid, String bssid) {
        return createScanResult(unquotedSsid, bssid, 1, false);
    }
    /** Create a NetworkKey based on the given Wifi SSID/BSSID. */
    public static NetworkKey createNetworkKey(String ssid, String bssid) {
        return new NetworkKey(new WifiKey(ssid, bssid));
    }
}
