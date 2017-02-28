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
package com.android.networkrecommendation.notify;

import static com.android.networkrecommendation.PlatformTestObjectFactory.createOpenNetworkScanResult;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkInfo.State;
import android.net.RecommendationRequest;
import android.net.RecommendationResult;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.provider.Settings;
import com.android.networkrecommendation.BroadcastIntentTestHelper;
import com.android.networkrecommendation.SynchronousNetworkRecommendationProvider;
import com.android.networkrecommendation.TestData;
import com.android.networkrecommendation.util.RoboCompatUtil;
import com.google.common.collect.Lists;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowSettings;

/**
 * Instrumentation tests for {@link com.android.networkrecommendation.WifiNotificationController}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/services/NetworkRecommendation/AndroidManifest.xml", sdk = 23)
public class WifiNotificationControllerTest {

    @Mock private WifiManager mWifiManager;
    @Mock private NotificationManager mNotificationManager;
    @Mock private WifiNotificationHelper mWifiNotificationHelper;
    @Mock private SynchronousNetworkRecommendationProvider mNetworkRecommendationProvider;
    @Mock private NetworkInfo mNetworkInfo;
    @Mock private RoboCompatUtil mRoboCompatUtil;
    @Captor private ArgumentCaptor<RecommendationRequest> mRecommendationRequestCaptor;
    private ContentResolver mContentResolver;
    private Handler mHandler;
    private WifiNotificationController mWifiNotificationController;
    private BroadcastIntentTestHelper mBroadcastIntentTestHelper;

    /** Initialize objects before each test run. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Needed for the NotificationEnabledSettingObserver.
        mContentResolver = RuntimeEnvironment.application.getContentResolver();
        ShadowSettings.ShadowGlobal.putInt(
                mContentResolver, Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON, 1);
        mHandler = new Handler(ShadowLooper.getMainLooper());

        RoboCompatUtil.setInstanceForTesting(mRoboCompatUtil);

        mWifiNotificationController =
                new WifiNotificationController(
                        RuntimeEnvironment.application,
                        mContentResolver,
                        mHandler,
                        mNetworkRecommendationProvider,
                        mWifiManager,
                        mNotificationManager,
                        mWifiNotificationHelper);
        mWifiNotificationController.start();

        when(mNetworkInfo.getState()).thenReturn(State.UNKNOWN);
        mBroadcastIntentTestHelper = new BroadcastIntentTestHelper(RuntimeEnvironment.application);
    }

    private void setOpenAccessPoints() {
        List<ScanResult> scanResults =
                Lists.newArrayList(
                        createOpenNetworkScanResult(TestData.UNQUOTED_SSID_1, TestData.BSSID_1),
                        createOpenNetworkScanResult(TestData.UNQUOTED_SSID_2, TestData.BSSID_2),
                        createOpenNetworkScanResult(TestData.UNQUOTED_SSID_3, TestData.BSSID_3));
        assertFalse(scanResults.isEmpty());
        when(mWifiManager.getScanResults()).thenReturn(scanResults);
    }

    private static WifiConfiguration createFakeConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TestData.SSID_1;
        config.BSSID = TestData.BSSID_1;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        return config;
    }

    private void createFakeBitmap() {
        when(mWifiNotificationHelper.createNotificationBadgeBitmap(any(), any()))
                .thenReturn(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
    }

    /**
     * When the NetworkRecommendationService associated with this WifiNotificationController is
     * unbound, this WifiWakeupController should no longer function.
     */
    @Test
    public void wifiNotificationControllerStopped() {
        mWifiNotificationController.stop();

        assertFalse(
                ShadowApplication.getInstance()
                        .hasReceiverForIntent(
                                new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)));
    }

    /** Verifies that a notification is displayed (and retracted) given system events. */
    @Test
    public void verifyNotificationDisplayedWhenNetworkRecommended() throws Exception {
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        setOpenAccessPoints();
        createFakeBitmap();

        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createConnectRecommendation(createFakeConfig()));

        // The notification should not be displayed after only two scan results.
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        verify(mNotificationManager, never())
                .notify(anyString(), anyInt(), any(Notification.class));

        verify(mWifiManager, times(2)).getScanResults();

        // Changing to and from "SCANNING" state should not affect the counter.
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.SCANNING);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);

        verify(mNotificationManager, never())
                .notify(anyString(), anyInt(), any(Notification.class));

        // The third scan result notification will trigger the notification.
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verify(mWifiNotificationHelper)
                .createMainNotification(any(WifiConfiguration.class), any(Bitmap.class));
        verify(mNotificationManager).notify(anyString(), anyInt(), any(Notification.class));
        verify(mNotificationManager, never()).cancel(anyString(), anyInt());
    }

    /** Verifies that a notification is not displayed for bad networks. */
    @Test
    public void verifyNotificationNotDisplayedWhenNoNetworkRecommended() throws Exception {
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        setOpenAccessPoints();
        createFakeBitmap();

        // Recommendation result with no WifiConfiguration returned.
        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createDoNotConnectRecommendation());

        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        verify(mNotificationManager, never())
                .notify(anyString(), anyInt(), any(Notification.class));
    }

    /**
     * Verifies the notifications flow (Connect -> connecting -> connected) when user clicks on
     * Connect button.
     */
    @Test
    public void verifyNotificationsFlowOnConnectToNetwork() throws Exception {
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        setOpenAccessPoints();
        createFakeBitmap();

        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createConnectRecommendation(createFakeConfig()));

        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        verify(mWifiNotificationHelper)
                .createMainNotification(any(WifiConfiguration.class), any(Bitmap.class));
        verify(mNotificationManager).notify(anyString(), anyInt(), any(Notification.class));

        // Send connect intent, should attempt to connect to Wi-Fi
        Intent intent =
                new Intent(WifiNotificationController.ACTION_CONNECT_TO_RECOMMENDED_NETWORK);
        ShadowApplication.getInstance().sendBroadcast(intent);
        verify(mRoboCompatUtil).connectToWifi(any(WifiManager.class), any(WifiConfiguration.class));
        verify(mWifiNotificationHelper)
                .createConnectingNotification(any(WifiConfiguration.class), any(Bitmap.class));

        // Show connecting notification.
        verify(mNotificationManager, times(2))
                .notify(anyString(), anyInt(), any(Notification.class));

        // Verify show connected notification.
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.CONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        verify(mWifiNotificationHelper)
                .createConnectedNotification(any(WifiConfiguration.class), any(Bitmap.class));
        verify(mNotificationManager, times(3))
                .notify(anyString(), anyInt(), any(Notification.class));

        // Dismissed the connected notification.
        ShadowLooper.runMainLooperToNextTask();
        verify(mNotificationManager).cancel(anyString(), anyInt());
    }

    /** Verifies the Failure to Connect notification after attempting to connect. */
    @Test
    public void verifyNotificationsFlowOnFailedToConnectToNetwork() throws Exception {
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        setOpenAccessPoints();
        createFakeBitmap();

        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createConnectRecommendation(createFakeConfig()));

        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        verify(mWifiNotificationHelper)
                .createMainNotification(any(WifiConfiguration.class), any(Bitmap.class));
        verify(mNotificationManager).notify(anyString(), anyInt(), any(Notification.class));

        // Send connect intent, should attempt to connect to Wi-Fi
        Intent intent =
                new Intent(WifiNotificationController.ACTION_CONNECT_TO_RECOMMENDED_NETWORK);
        ShadowApplication.getInstance().sendBroadcast(intent);
        verify(mRoboCompatUtil).connectToWifi(any(WifiManager.class), any(WifiConfiguration.class));
        verify(mWifiNotificationHelper)
                .createConnectingNotification(any(WifiConfiguration.class), any(Bitmap.class));

        // Show connecting notification.
        verify(mNotificationManager, times(2))
                .notify(anyString(), anyInt(), any(Notification.class));

        // Show failed to connect notification.
        ShadowLooper.runMainLooperToNextTask();
        verify(mWifiNotificationHelper)
                .createFailedToConnectNotification(any(WifiConfiguration.class));

        // Dismissed the cancel notification.
        ShadowLooper.runMainLooperToNextTask();
        verify(mNotificationManager).cancel(anyString(), anyInt());
    }

    /** Verifies the flow where notification is dismissed. */
    @Test
    public void verifyNotificationsFlowOnDismissMainNotification() throws Exception {
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        setOpenAccessPoints();
        createFakeBitmap();

        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createConnectRecommendation(createFakeConfig()));

        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        // Show main notification
        verify(mWifiNotificationHelper)
                .createMainNotification(any(WifiConfiguration.class), any(Bitmap.class));
        verify(mNotificationManager).notify(anyString(), anyInt(), any(Notification.class));

        // Send dismiss intent
        Intent intent = new Intent(WifiNotificationController.ACTION_NOTIFICATION_DELETED);
        ShadowApplication.getInstance().sendBroadcast(intent);
    }

    /** Verifies the flow where "Settings" button on notification is clicked. */
    @Test
    public void verifyNotificationsFlowOnClickSettingsFromMainNotification() throws Exception {
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        setOpenAccessPoints();
        createFakeBitmap();

        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createConnectRecommendation(createFakeConfig()));

        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        // Show main notification
        verify(mWifiNotificationHelper)
                .createMainNotification(any(WifiConfiguration.class), any(Bitmap.class));
        verify(mNotificationManager).notify(anyString(), anyInt(), any(Notification.class));

        // Send click settings intent
        Intent intent = new Intent(WifiNotificationController.ACTION_PICK_WIFI_NETWORK);
        ShadowApplication.getInstance().sendBroadcast(intent);
    }

    /** Verifies the flow when notification is reset on captive portal check. */
    @Test
    public void verifyNotificationsFlowResetNotificationOnCaptivePortalCheck() throws Exception {
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        setOpenAccessPoints();
        createFakeBitmap();

        when(mNetworkRecommendationProvider.requestRecommendation(any(RecommendationRequest.class)))
                .thenReturn(RecommendationResult.createConnectRecommendation(createFakeConfig()));

        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        // Show main notification
        verify(mWifiNotificationHelper)
                .createMainNotification(any(WifiConfiguration.class), any(Bitmap.class));
        verify(mNotificationManager).notify(anyString(), anyInt(), any(Notification.class));

        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.CAPTIVE_PORTAL_CHECK);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);
        verify(mNotificationManager).cancel(anyString(), anyInt());
    }

    /** Verifies saved networks are skipped when getting network recommendations */
    @Test
    public void verifyNotificationsFlowSkipSavedNetworks() throws Exception {
        when(mWifiManager.getWifiState()).thenReturn(WifiManager.WIFI_STATE_ENABLED);

        mBroadcastIntentTestHelper.sendWifiStateChanged();
        when(mNetworkInfo.getDetailedState()).thenReturn(DetailedState.DISCONNECTED);
        mBroadcastIntentTestHelper.sendNetworkStateChanged(mNetworkInfo);

        // First scan result and saved WifiConfiguration should be equal
        when(mWifiManager.getScanResults())
                .thenReturn(
                        Lists.newArrayList(
                                createOpenNetworkScanResult(
                                        TestData.UNQUOTED_SSID_1, TestData.BSSID_1)));
        when(mWifiManager.getConfiguredNetworks())
                .thenReturn(Lists.newArrayList(createFakeConfig()));
        mBroadcastIntentTestHelper.sendScanResultsAvailable();

        verify(mNetworkRecommendationProvider)
                .requestRecommendation(mRecommendationRequestCaptor.capture());

        assertEquals(0, mRecommendationRequestCaptor.getValue().getScanResults().length);
    }

    /** Test dump() does not crash. */
    @Test
    public void testDump() {
        StringWriter stringWriter = new StringWriter();
        mWifiNotificationController.dump(
                new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
    }
}
