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
package com.android.networkrecommendation.wakeup;

import static com.android.networkrecommendation.TestData.BSSID_3;
import static com.android.networkrecommendation.TestData.UNQUOTED_SSID_1;
import static com.android.networkrecommendation.TestData.UNQUOTED_SSID_2;
import static com.android.networkrecommendation.TestData.UNQUOTED_SSID_3;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.RecommendationRequest;
import android.net.RecommendationResult;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.util.ArrayMap;
import com.android.networkrecommendation.R;
import com.android.networkrecommendation.SynchronousNetworkRecommendationProvider;
import com.android.networkrecommendation.util.RoboCompatUtil;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit tests for {@link WifiWakeupNetworkSelector} */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/services/NetworkRecommendation/AndroidManifest.xml", sdk = 23)
public class WifiWakeupNetworkSelectorTest {
    private static ScanResult buildScanResult(String ssid, int level, int frequency, String caps) {
        try {
            ScanResult scanResult = ScanResult.class.getConstructor().newInstance();
            scanResult.SSID = ssid;
            scanResult.level = level;
            scanResult.frequency = frequency;
            scanResult.capabilities = caps;
            return scanResult;
        } catch (Exception e) {
            return null;
        }
    }

    private static final int FREQUENCY_24 = 2450;
    private static final int FREQUENCY_5 = 5000;
    private static final String CAPABILITIES_NONE = "";
    private static final String CAPABILITIES_PSK = "PSK";

    private WifiConfiguration mWifiConfigurationPsk;
    private WifiConfiguration mWifiConfigurationNone;
    private WifiConfiguration mWifiConfigurationPskExternal;
    private ArrayMap<String, WifiConfiguration> mSavedWifiConfigurationMap;
    private int mMinQualified24;
    private int mMinQualified5;

    @Mock private RoboCompatUtil mRoboCompatUtil;
    @Mock private SynchronousNetworkRecommendationProvider mNetworkRecommendationProvider;
    @Captor private ArgumentCaptor<RecommendationRequest> mRecommendationRequestCaptor;

    private WifiWakeupNetworkSelector mWifiWakeupNetworkSelector;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        RoboCompatUtil.setInstanceForTesting(mRoboCompatUtil);

        mSavedWifiConfigurationMap = new ArrayMap<>();
        mWifiConfigurationPsk = new WifiConfiguration();
        mWifiConfigurationPsk.SSID = "\"" + UNQUOTED_SSID_1 + "\"";
        mWifiConfigurationPsk.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        mSavedWifiConfigurationMap.put(UNQUOTED_SSID_1, mWifiConfigurationPsk);

        mWifiConfigurationNone = new WifiConfiguration();
        mWifiConfigurationNone.SSID = "\"" + UNQUOTED_SSID_2 + "\"";
        mWifiConfigurationNone.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mSavedWifiConfigurationMap.put(UNQUOTED_SSID_2, mWifiConfigurationNone);

        mWifiConfigurationPskExternal = new WifiConfiguration();
        mWifiConfigurationPskExternal.SSID = "\"" + UNQUOTED_SSID_3 + "\"";
        mWifiConfigurationPskExternal.BSSID = BSSID_3;
        mWifiConfigurationPskExternal.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        when(mRoboCompatUtil.useExternalScores(mWifiConfigurationPskExternal)).thenReturn(true);
        mSavedWifiConfigurationMap.put(UNQUOTED_SSID_3, mWifiConfigurationPskExternal);

        mMinQualified24 =
                RuntimeEnvironment.application
                        .getResources()
                        .getInteger(R.integer.config_netrec_wifi_score_low_rssi_threshold_24GHz);
        mMinQualified5 =
                RuntimeEnvironment.application
                        .getResources()
                        .getInteger(R.integer.config_netrec_wifi_score_low_rssi_threshold_5GHz);

        mWifiWakeupNetworkSelector =
                new WifiWakeupNetworkSelector(
                        RuntimeEnvironment.application.getResources(),
                        mNetworkRecommendationProvider);
    }

    @Test
    public void testSelectNetwork_noSavedNetworksInScanResults() {
        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createDoNotConnectRecommendation());
        List<ScanResult> scanResults =
                Lists.newArrayList(
                        buildScanResult("blah", mMinQualified5 + 1, FREQUENCY_5, CAPABILITIES_NONE),
                        buildScanResult(
                                "blahtoo", mMinQualified24 + 1, FREQUENCY_24, CAPABILITIES_NONE));

        WifiConfiguration selectedNetwork =
                mWifiWakeupNetworkSelector.selectNetwork(mSavedWifiConfigurationMap, scanResults);

        assertNull(selectedNetwork);
        verify(mNetworkRecommendationProvider, never())
                .requestRecommendation(any(RecommendationRequest.class));
    }

    @Test
    public void testSelectNetwork_noQualifiedSavedNetworks() {
        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createDoNotConnectRecommendation());
        List<ScanResult> scanResults =
                Lists.newArrayList(
                        buildScanResult(
                                UNQUOTED_SSID_2,
                                mMinQualified5 - 1,
                                FREQUENCY_5,
                                CAPABILITIES_NONE),
                        buildScanResult(
                                UNQUOTED_SSID_2,
                                mMinQualified24 - 1,
                                FREQUENCY_24,
                                CAPABILITIES_NONE));

        WifiConfiguration selectedNetwork =
                mWifiWakeupNetworkSelector.selectNetwork(mSavedWifiConfigurationMap, scanResults);

        assertNull(selectedNetwork);
        verify(mNetworkRecommendationProvider, never())
                .requestRecommendation(any(RecommendationRequest.class));
    }

    @Test
    public void testSelectNetwork_noMatchingScanResults() {
        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createDoNotConnectRecommendation());
        List<ScanResult> scanResults =
                Lists.newArrayList(
                        buildScanResult(
                                UNQUOTED_SSID_1,
                                mMinQualified5 + 1,
                                FREQUENCY_5,
                                CAPABILITIES_NONE),
                        buildScanResult(
                                UNQUOTED_SSID_1,
                                mMinQualified24 + 1,
                                FREQUENCY_24,
                                CAPABILITIES_NONE));

        WifiConfiguration selectedNetwork =
                mWifiWakeupNetworkSelector.selectNetwork(mSavedWifiConfigurationMap, scanResults);

        assertNull(selectedNetwork);
        verify(mNetworkRecommendationProvider, never())
                .requestRecommendation(any(RecommendationRequest.class));
    }

    @Test
    public void testSelectNetwork_secureNetworkOverUnsecure() {
        List<ScanResult> scanResults =
                Lists.newArrayList(
                        buildScanResult(
                                UNQUOTED_SSID_1, mMinQualified5 + 1, FREQUENCY_5, CAPABILITIES_PSK),
                        buildScanResult(
                                UNQUOTED_SSID_2,
                                mMinQualified5 + 1,
                                FREQUENCY_5,
                                CAPABILITIES_NONE));

        WifiConfiguration selectedNetwork =
                mWifiWakeupNetworkSelector.selectNetwork(mSavedWifiConfigurationMap, scanResults);

        assertEquals(mWifiConfigurationPsk.networkId, selectedNetwork.networkId);
        verify(mNetworkRecommendationProvider, never())
                .requestRecommendation(any(RecommendationRequest.class));
    }

    @Test
    public void testSelectNetwork_deferToProviderForOpenAndUseExternalScores() {
        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(
                        RecommendationResult.createConnectRecommendation(
                                mWifiConfigurationPskExternal));
        List<ScanResult> scanResults =
                Lists.newArrayList(
                        buildScanResult(
                                UNQUOTED_SSID_3, mMinQualified5 + 1, FREQUENCY_5, CAPABILITIES_PSK),
                        buildScanResult(
                                UNQUOTED_SSID_2,
                                mMinQualified5 + 1,
                                FREQUENCY_5,
                                CAPABILITIES_NONE));

        WifiConfiguration selectedNetwork =
                mWifiWakeupNetworkSelector.selectNetwork(mSavedWifiConfigurationMap, scanResults);

        assertEquals(mWifiConfigurationPskExternal, selectedNetwork);
        verify(mNetworkRecommendationProvider)
                .requestRecommendation(mRecommendationRequestCaptor.capture());

        ScanResult[] openOrExternalScanResults =
                mRecommendationRequestCaptor.getValue().getScanResults();
        assertEquals(2, openOrExternalScanResults.length);
        assertEquals(UNQUOTED_SSID_3, openOrExternalScanResults[0].SSID);
        assertEquals(UNQUOTED_SSID_2, openOrExternalScanResults[1].SSID);
    }
}
