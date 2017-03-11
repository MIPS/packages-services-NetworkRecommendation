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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.NetworkBadging;
import android.net.ScoredNetwork;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import com.android.networkrecommendation.SynchronousNetworkRecommendationProvider;
import com.android.networkrecommendation.TestData;
import com.android.networkrecommendation.shadows.BitmapGetPixelsShadow;
import com.android.networkrecommendation.util.RoboCompatUtil;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSettings;

/** Unit tests for {@link WifiNotificationHelper} */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/services/NetworkRecommendation/AndroidManifest.xml", sdk = 23,
shadows={BitmapGetPixelsShadow.class})
public class WifiNotificationHelperTest {

    private Context mContext;

    @Mock
    private SynchronousNetworkRecommendationProvider mSynchronousNetworkRecommendationProvider;

    @Mock private RoboCompatUtil mRoboCompatUtil;

    private WifiNotificationHelper mWifiNotificationHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        RoboCompatUtil.setInstanceForTesting(mRoboCompatUtil);
        mContext = RuntimeEnvironment.application;

        mWifiNotificationHelper =
                new WifiNotificationHelper(mContext, mSynchronousNetworkRecommendationProvider);
    }

    private static WifiConfiguration createFakeConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TestData.SSID_1;
        config.BSSID = TestData.BSSID_1;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        return config;
    }

    private static Bitmap createFakeIcon() {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    }

    private static void assertValidNotification(Notification notification) {
        assertNotNull(notification);
        assertNotNull(notification.getSmallIcon());
        assertNotNull(notification.getLargeIcon());
    }

    @Test
    public void createMainNotification() {
        assertValidNotification(
                mWifiNotificationHelper.createMainNotification(
                        createFakeConfig(), createFakeIcon()));
    }

    @Test
    public void createConnectingNotification() {
        assertValidNotification(
                mWifiNotificationHelper.createConnectingNotification(
                        createFakeConfig(), createFakeIcon()));
    }

    @Test
    public void createConnectedNotification() {
        assertValidNotification(
                mWifiNotificationHelper.createConnectedNotification(
                        createFakeConfig(), createFakeIcon()));
    }

    @Test
    public void createFailedToConnectNotification() {
        assertValidNotification(
                mWifiNotificationHelper.createFailedToConnectNotification(createFakeConfig()));
    }

    @Test
    public void createNotificationBadgeBitmap() {
        WifiConfiguration wifiConfig = createFakeConfig();
        List<ScanResult> scanResultList =
                Lists.newArrayList(createOpenNetworkScanResult(wifiConfig.SSID, wifiConfig.BSSID));
        when(mSynchronousNetworkRecommendationProvider.getCachedScoredNetwork(any()))
                .thenReturn(Mockito.mock(ScoredNetwork.class));
        when(mRoboCompatUtil.calculateBadge(any(), anyInt())).thenReturn(NetworkBadging.BADGING_4K);
        when(mRoboCompatUtil.getWifiIcon(anyInt(), anyInt(), any()))
                .thenReturn(mContext.getDrawable(android.R.drawable.stat_sys_warning));

        assertNotNull(
                mWifiNotificationHelper.createNotificationBadgeBitmap(wifiConfig, scanResultList));

        ShadowSettings.ShadowGlobal.putInt(
                mContext.getContentResolver(),
                WifiNotificationHelper.NETWORK_SCORING_UI_ENABLED,
                1);
        assertNotNull(
                mWifiNotificationHelper.createNotificationBadgeBitmap(wifiConfig, scanResultList));
    }

    @Test
    public void createNotificationBadgeBitmap_noMatchingScanResults() {
        WifiConfiguration wifiConfig = createFakeConfig();
        List<ScanResult> scanResultList = new ArrayList<>();

        assertNull(
                mWifiNotificationHelper.createNotificationBadgeBitmap(wifiConfig, scanResultList));
    }
}
