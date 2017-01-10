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

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;

/**
 * Scan result utility for any {@link ScanResult} related operations.
 * TODO(b/34125341): Delete this class once exposed as a SystemApi
 */
public class ScanResultUtil {

    /**
     * Helper method to check if the provided |scanResult| corresponds to a PSK network or not.
     * This checks if the provided capabilities string contains PSK encryption type or not.
     */
    public static boolean isScanResultForPskNetwork(ScanResult scanResult) {
        return scanResult.capabilities.contains("PSK");
    }

    /**
     * Helper method to check if the provided |scanResult| corresponds to a EAP network or not.
     * This checks if the provided capabilities string contains EAP encryption type or not.
     */
    public static boolean isScanResultForEapNetwork(ScanResult scanResult) {
        return scanResult.capabilities.contains("EAP");
    }

    /**
     * Helper method to check if the provided |scanResult| corresponds to a WEP network or not.
     * This checks if the provided capabilities string contains WEP encryption type or not.
     */
    public static boolean isScanResultForWepNetwork(ScanResult scanResult) {
        return scanResult.capabilities.contains("WEP");
    }

    /**
     * Helper method to check if the provided |scanResult| corresponds to an open network or not.
     * This checks if the provided capabilities string does not contain either of WEP, PSK or EAP
     * encryption types or not.
     */
    public static boolean isScanResultForOpenNetwork(ScanResult scanResult) {
        return !(isScanResultForWepNetwork(scanResult) || isScanResultForPskNetwork(scanResult)
                || isScanResultForEapNetwork(scanResult));
    }

    /**
     * Helper method to quote the SSID in Scan result to use for comparing/filling SSID stored in
     * WifiConfiguration object.
     */
    @VisibleForTesting
    public static String createQuotedSSID(String ssid) {
        return "\"" + ssid + "\"";
    }

    /**
     * Checks if the provided |scanResult| match with the provided |config|. Essentially checks
     * if the network config and scan result have the same SSID and encryption type.
     */
    public static boolean doesScanResultMatchWithNetwork(
            ScanResult scanResult, WifiConfiguration config) {
        // Add the double quotes to the scan result SSID for comparison with the network configs.
        String configSSID = createQuotedSSID(scanResult.SSID);
        if (TextUtils.equals(config.SSID, configSSID)) {
            if (ScanResultUtil.isScanResultForPskNetwork(scanResult)
                    && WifiConfigurationUtil.isConfigForPskNetwork(config)) {
                return true;
            }
            if (ScanResultUtil.isScanResultForEapNetwork(scanResult)
                    && WifiConfigurationUtil.isConfigForEapNetwork(config)) {
                return true;
            }
            if (ScanResultUtil.isScanResultForWepNetwork(scanResult)
                    && WifiConfigurationUtil.isConfigForWepNetwork(config)) {
                return true;
            }
            if (ScanResultUtil.isScanResultForOpenNetwork(scanResult)
                    && WifiConfigurationUtil.isConfigForOpenNetwork(config)) {
                return true;
            }
        }
        return false;
    }
}
