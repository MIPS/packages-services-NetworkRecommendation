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

import static com.android.networkrecommendation.PlatformTestObjectFactory.createOpenNetworkScanResult;
import static com.android.networkrecommendation.util.SsidUtil.quoteSsid;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.net.NetworkKey;
import android.net.WifiKey;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import com.android.networkrecommendation.config.Flag;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Unit tests for {@link ScanResultUtil}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/services/NetworkRecommendation/AndroidManifest.xml", sdk = 23)
public class ScanResultUtilTest {

    @Before
    public void setUp() {
        Flag.initForTest();
    }

    @Test
    public void testCreateNetworkKey() {
        ScanResult scanResult = createOpenNetworkScanResult("testSSID", "00:00:00:00:00:00");
        NetworkKey networkKey = ScanResultUtil.createNetworkKey(scanResult);

        assertEquals(quoteSsid(scanResult.SSID), networkKey.wifiKey.ssid);
        assertEquals(scanResult.BSSID, networkKey.wifiKey.bssid);
    }

    @Test
    public void testCreateWifiKey_validScanResult() {
        ScanResult scanResult = createOpenNetworkScanResult("testSSID", "00:00:00:00:00:00");
        WifiKey wifiKey = ScanResultUtil.createWifiKey(scanResult);

        assertEquals(quoteSsid(scanResult.SSID), wifiKey.ssid);
        assertEquals(scanResult.BSSID, wifiKey.bssid);
    }

    @Test
    public void testCreateWifiKey_invalidScanResultReturnsNull() {
        ScanResult scanResult1 = createOpenNetworkScanResult(null, "00:00:00:00:00:00");

        assertNull(ScanResultUtil.createWifiKey(scanResult1));

        ScanResult scanResult2 = createOpenNetworkScanResult("testSSID", null);

        assertNull(ScanResultUtil.createWifiKey(scanResult2));

        ScanResult scanResult3 = createOpenNetworkScanResult("testSSID", "invalidBSSID");

        assertNull(ScanResultUtil.createWifiKey(scanResult3));
    }

    @Test
    public void testDoesScanResultMatchWithNetwork_matching() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = quoteSsid("testSSID");
        config.BSSID = "00:00:00:00:00:00";
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        ScanResult scanResult = createOpenNetworkScanResult(config.SSID, config.BSSID);

        assertTrue(ScanResultUtil.doesScanResultMatchWithNetwork(scanResult, config));
    }

    @Test
    public void testDoesScanResultMatchWithNetwork_onlyOneOpenNetworkDoesNotMatch() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = quoteSsid("testSSID");
        config.BSSID = "00:00:00:00:00:00";
        config.allowedKeyManagement.set(KeyMgmt.WPA_EAP);
        ScanResult scanResult = createOpenNetworkScanResult(config.SSID, config.BSSID);

        assertFalse(ScanResultUtil.doesScanResultMatchWithNetwork(scanResult, config));
    }
}
