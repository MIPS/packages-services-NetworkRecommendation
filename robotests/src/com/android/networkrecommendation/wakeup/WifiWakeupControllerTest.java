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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.Status;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import com.android.networkrecommendation.BroadcastIntentTestHelper;
import com.android.networkrecommendation.config.Flag;
import com.android.networkrecommendation.config.WideAreaNetworks;
import com.android.networkrecommendation.util.RoboCompatUtil;
import com.google.common.collect.Lists;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;

/** Unit tests for {@link WifiWakeupController}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/services/NetworkRecommendation/AndroidManifest.xml", sdk = 23)
public class WifiWakeupControllerTest {
    private static final ScanResult OPEN_SCAN_RESULT = buildScanResult("ssid");
    private static final ScanResult SAVED_SCAN_RESULT = buildScanResult("ssid1");
    private static final ScanResult SAVED_SCAN_RESULT2 = buildScanResult("ssid2");
    private static final ScanResult SAVED_SCAN_RESULT_EXTERNAL = buildScanResult("ssid3");
    private static final ScanResult SAVED_SCAN_RESULT_WIDE_AREA = buildScanResult("xfinitywifi");

    private static ScanResult buildScanResult(String ssid) {
        try {
            ScanResult scanResult = ScanResult.class.getConstructor().newInstance();
            scanResult.SSID = ssid;
            return scanResult;
        } catch (Exception e) {
            return null;
        }
    }

    private ContentResolver mContentResolver;
    @Mock private NotificationManager mNotificationManager;
    @Mock private WifiWakeupNetworkSelector mWifiWakeupNetworkSelector;
    @Mock private WifiWakeupHelper mWifiWakeupHelper;
    @Mock private WifiManager mWifiManager;
    @Mock private RoboCompatUtil mRoboCompatUtil;
    @Mock private PowerManager mPowerManager;

    private WifiConfiguration mSavedWifiConfiguration;
    private WifiConfiguration mSavedWifiConfiguration2;
    private WifiConfiguration mSavedWifiConfigurationExternal;
    private WifiConfiguration mSavedWifiConfigurationWideArea;

    private WifiWakeupController mWifiWakeupController;
    private BroadcastIntentTestHelper mBroadcastIntentTestHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Flag.initForTest();
        RoboCompatUtil.setInstanceForTesting(mRoboCompatUtil);

        mSavedWifiConfiguration = new WifiConfiguration();
        mSavedWifiConfiguration.SSID = "\"" + SAVED_SCAN_RESULT.SSID + "\"";
        mSavedWifiConfiguration.status = WifiConfiguration.Status.CURRENT;
        mSavedWifiConfiguration2 = new WifiConfiguration();
        mSavedWifiConfiguration2.SSID = "\"" + SAVED_SCAN_RESULT2.SSID + "\"";
        mSavedWifiConfiguration2.status = WifiConfiguration.Status.ENABLED;
        mSavedWifiConfigurationExternal = new WifiConfiguration();
        mSavedWifiConfigurationExternal.SSID = "\"" + SAVED_SCAN_RESULT_EXTERNAL.SSID + "\"";
        // TODO(netrec): why is this needed when this field is accessible when compiling the main apk?
        // is robo_experimental behind backend_experimental?
        when(mRoboCompatUtil.useExternalScores(mSavedWifiConfigurationExternal)).thenReturn(true);
        mSavedWifiConfigurationExternal.status = WifiConfiguration.Status.ENABLED;
        mSavedWifiConfigurationWideArea = new WifiConfiguration();
        mSavedWifiConfigurationWideArea.SSID = "\"" + SAVED_SCAN_RESULT_WIDE_AREA.SSID + "\"";
        mSavedWifiConfigurationWideArea.status = WifiConfiguration.Status.ENABLED;
        assertTrue(WideAreaNetworks.contains(SAVED_SCAN_RESULT_WIDE_AREA.SSID));

        mContentResolver = RuntimeEnvironment.application.getContentResolver();
        Settings.Global.putInt(mContentResolver, Settings.Global.WIFI_WAKEUP_ENABLED, 1);
        Settings.Global.putInt(mContentResolver, Settings.Global.AIRPLANE_MODE_ON, 0);
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_DISABLED);
        ShadowLooper.resetThreadLoopers();

        mWifiWakeupController =
                new WifiWakeupController(
                        RuntimeEnvironment.application,
                        mContentResolver,
                        new Handler(ShadowLooper.getMainLooper()),
                        mWifiManager,
                        mPowerManager,
                        mWifiWakeupNetworkSelector,
                        mWifiWakeupHelper);
        mWifiWakeupController.start();
        mBroadcastIntentTestHelper = new BroadcastIntentTestHelper(RuntimeEnvironment.application);
    }

    /**
     * When the NetworkRecommendationService associated with this WifiWakeupController is unbound,
     * this WifiWakeupController should no longer function.
     */
    @Test
    public void wifiWakeupControllerStopped() {
        mWifiWakeupController.stop();

        assertFalse(
                ShadowApplication.getInstance()
                        .hasReceiverForIntent(
                                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)));
    }

    /**
     * When Wi-Fi is disabled and a saved network is in the scan list, and then this network is not
     * in the scan list 3x, and then it is, Wi-Fi should be enabled.
     */
    @Test
    public void wifiEnabled_userDisabledWifiNearSavedNetwork_thenLeaves_thenMovesBack() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(Lists.newArrayList(mSavedWifiConfiguration));
        when(mWifiManager.getScanResults())
                .thenReturn(
                        Lists.newArrayList(SAVED_SCAN_RESULT),
                        Lists.newArrayList(OPEN_SCAN_RESULT),
                        Lists.newArrayList(OPEN_SCAN_RESULT),
                        Lists.newArrayList(OPEN_SCAN_RESULT),
                        Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiWakeupNetworkSelector.selectNetwork(anyMap(), anyList()))
                .thenReturn(mSavedWifiConfiguration);
        when(mWifiManager.getWifiState())
                .thenReturn(WifiManager.WIFI_STATE_ENABLED, WifiManager.WIFI_STATE_DISABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verify(mWifiManager, never()).setWifiEnabled(true);

        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verify(mWifiManager).setWifiEnabled(true);
        verify(mWifiWakeupHelper).startWifiSession(mSavedWifiConfiguration);
    }

    /**
     * When Wi-Fi is disabled and a saved network is in the scan list, and then another scan result
     * comes in 3x with only a different saved network, Wi-Fi should be enabled.
     */
    @Test
    public void wifiEnabled_userDisabledWifiNearSavedNetwork_thenMovesToAnotherSavedNetwork() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(Lists.newArrayList(mSavedWifiConfiguration, mSavedWifiConfiguration2));
        when(mWifiManager.getScanResults())
                .thenReturn(
                        Lists.newArrayList(SAVED_SCAN_RESULT),
                        Lists.newArrayList(SAVED_SCAN_RESULT2));
        when(mWifiWakeupNetworkSelector.selectNetwork(anyMap(), anyList()))
                .thenReturn(mSavedWifiConfiguration2);
        when(mWifiManager.getWifiState())
                .thenReturn(WifiManager.WIFI_STATE_ENABLED, WifiManager.WIFI_STATE_DISABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendWifiStateChanged();

        verify(mWifiManager, never()).setWifiEnabled(true);

        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verify(mWifiManager).setWifiEnabled(true);
        verify(mWifiWakeupHelper).startWifiSession(mSavedWifiConfiguration2);
    }

    /**
     * If Wi-Fi is disabled when a saved network is in the scan list, and then scan results come in
     * for a saved wide area network 3x, Wi-Fi should not be enabled.
     */
    @Test
    public void wifiNotEnabled_userDisablesWifiNearSavedNetwork_thenMovesToWideAreaNetwork() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration, mSavedWifiConfigurationWideArea));
        when(mWifiManager.getScanResults())
                .thenReturn(
                        Lists.newArrayList(SAVED_SCAN_RESULT),
                        Lists.newArrayList(SAVED_SCAN_RESULT_WIDE_AREA));
        when(mWifiManager.getWifiState())
                .thenReturn(WifiManager.WIFI_STATE_ENABLED, WifiManager.WIFI_STATE_DISABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verifyZeroInteractions(mWifiWakeupNetworkSelector);
        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * When Wi-Fi is enabled and a saved network is in the scan list, Wi-Fi should not be enabled.
     */
    @Test
    public void wifiNotEnabled_wifiAlreadyEnabled() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration, mSavedWifiConfigurationExternal));
        when(mWifiManager.getScanResults()).thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verifyZeroInteractions(mWifiWakeupNetworkSelector);
        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * When Wi-Fi is disabled and a saved network is in the scan list, but {@link
     * WifiWakeupNetworkSelector}, does not choose this network, Wi-Fi should not be enabled.
     */
    @Test
    public void wifiNotEnabled_userNearSavedNetworkButNotSelected() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration, mSavedWifiConfigurationExternal));
        when(mWifiManager.getScanResults()).thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiWakeupNetworkSelector.selectNetwork(anyMap(), anyList())).thenReturn(null);
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);

        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * If Wi-Fi is disabled and a saved network is in the scan list, Wi-Fi should not be enabled if
     * the user has not enabled the wifi wakeup feature.
     */
    @Test
    public void wifiNotEnabled_userDisablesWifiWakeupFeature() {
        Settings.Global.putInt(mContentResolver, Settings.Global.WIFI_WAKEUP_ENABLED, 0);
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration, mSavedWifiConfigurationExternal));
        when(mWifiManager.getScanResults()).thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);

        mWifiWakeupController.mContentObserver.onChange(true);
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verifyZeroInteractions(mWifiWakeupNetworkSelector);
        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * If Wi-Fi is disabled and a saved network is in the scan list, Wi-Fi should not be enabled if
     * the user is in airplane mode.
     */
    @Test
    public void wifiNotEnabled_userIsInAirplaneMode() {
        Settings.Global.putInt(mContentResolver, Settings.Global.AIRPLANE_MODE_ON, 1);
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration, mSavedWifiConfigurationExternal));
        when(mWifiManager.getScanResults()).thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);

        mWifiWakeupController.mContentObserver.onChange(true);
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verifyZeroInteractions(mWifiWakeupNetworkSelector);
        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * If Wi-Fi is disabled and a saved network is in the scan list, Wi-Fi should not be enabled if
     * the wifi AP state is not disabled.
     */
    @Test
    public void wifiNotEnabled_wifiApStateIsNotDisabled() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration, mSavedWifiConfigurationExternal));
        when(mWifiManager.getScanResults()).thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        when(mWifiManager.getWifiApState()).thenReturn(WifiManager.WIFI_AP_STATE_ENABLED);

        mWifiWakeupController.mContentObserver.onChange(true);
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendWifiApStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verifyZeroInteractions(mWifiWakeupNetworkSelector);
        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * If Wi-Fi is disabled and a saved network is in the scan list, Wi-Fi should not be enabled if
     * power saving mode is on.
     */
    @Test
    public void wifiNotEnabled_userInPowerSaveMode() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration, mSavedWifiConfigurationExternal));
        when(mWifiManager.getScanResults()).thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_DISABLED);
        when(mPowerManager.isPowerSaveMode()).thenReturn(true);

        mWifiWakeupController.mContentObserver.onChange(true);
        mBroadcastIntentTestHelper.sendPowerSaveModeChanged();
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendWifiApStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verifyZeroInteractions(mWifiWakeupNetworkSelector);
        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * If Wi-Fi is disabled when a saved network is the scan list, Wi-Fi should not be enabled no
     * matter how many scans are performed that include the saved network.
     */
    @Test
    public void wifiNotEnabled_userDisablesWifiNearSavedNetwork_thenDoesNotLeave() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration, mSavedWifiConfigurationExternal));
        when(mWifiManager.getScanResults()).thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verifyZeroInteractions(mWifiWakeupNetworkSelector);
        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * If Wi-Fi is disabled when a saved network is in the scan list, and then that saved network is
     * removed, Wi-Fi is not enabled even if the user leaves range of that network and returns.
     */
    @Test
    public void wifiNotEnabled_userDisablesWifiNearSavedNetwork_thenRemovesNetwork_thenStays() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(mSavedWifiConfiguration),
                        Lists.<WifiConfiguration>newArrayList());
        when(mWifiManager.getScanResults())
                .thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT))
                .thenReturn(Lists.<ScanResult>newArrayList())
                .thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiWakeupNetworkSelector.selectNetwork(anyMap(), anyList())).thenReturn(null);
        when(mWifiManager.getWifiState())
                .thenReturn(WifiManager.WIFI_STATE_ENABLED, WifiManager.WIFI_STATE_DISABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    /**
     * If Wi-Fi is disabled when 2 saved networks are in the scan list, and then a scan result comes
     * in with only 1 saved network 3x, Wi-Fi should not be enabled.
     */
    @Test
    public void wifiNotEnabled_userDisablesWifiNear2SavedNetworks_thenLeavesRangeOfOneOfThem() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(Lists.newArrayList(mSavedWifiConfiguration, mSavedWifiConfiguration2));
        when(mWifiManager.getScanResults())
                .thenReturn(
                        Lists.newArrayList(SAVED_SCAN_RESULT, SAVED_SCAN_RESULT2),
                        Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiManager.getWifiState())
                .thenReturn(WifiManager.WIFI_STATE_ENABLED, WifiManager.WIFI_STATE_DISABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verifyZeroInteractions(mWifiWakeupNetworkSelector);
        verify(mWifiManager, never()).setWifiEnabled(true);
    }

    @Test
    public void logWifiEnabled_autopilotEnabledWifi() {
        WifiConfiguration noInternetAccessNetwork = new WifiConfiguration();
        noInternetAccessNetwork.SSID = "Bof";
        when(mRoboCompatUtil.hasNoInternetAccess(noInternetAccessNetwork)).thenReturn(true);

        WifiConfiguration noInternetAccessExpectedNetwork = new WifiConfiguration();
        noInternetAccessExpectedNetwork.SSID = "fri";
        when(mRoboCompatUtil.isNoInternetAccessExpected(noInternetAccessExpectedNetwork))
                .thenReturn(true);

        WifiConfiguration disabledNetwork = new WifiConfiguration();
        disabledNetwork.SSID = "fleu";
        disabledNetwork.status = Status.DISABLED;

        WifiConfiguration noSsidNetwork = new WifiConfiguration();

        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(
                        Lists.newArrayList(
                                mSavedWifiConfiguration,
                                mSavedWifiConfiguration2,
                                mSavedWifiConfigurationExternal,
                                mSavedWifiConfigurationWideArea,
                                noInternetAccessNetwork,
                                noInternetAccessExpectedNetwork,
                                disabledNetwork,
                                noSsidNetwork));
        when(mWifiManager.getScanResults()).thenReturn(Lists.newArrayList(SAVED_SCAN_RESULT));
        when(mWifiWakeupNetworkSelector.selectNetwork(anyMap(), anyList()))
                .thenReturn(mSavedWifiConfiguration);
        when(mWifiManager.getWifiState())
                .thenReturn(WifiManager.WIFI_STATE_DISABLED, WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendWifiStateChanged();

        verify(mWifiManager).setWifiEnabled(true);
    }

    @Test
    public void logWifiEnabled_userEnabledWifi() {
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(Lists.newArrayList(mSavedWifiConfiguration, mSavedWifiConfiguration2));
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendConfiguredNetworksChanged();
        mBroadcastIntentTestHelper.sendWifiStateChanged();
    }

    /** Test dump() does not crash. */
    @Test
    public void testDump() {
        StringWriter stringWriter = new StringWriter();
        mWifiWakeupController.dump(
                new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
    }
}
