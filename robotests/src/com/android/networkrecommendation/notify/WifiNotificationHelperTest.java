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

import static org.junit.Assert.assertNotNull;

import android.app.Notification;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import com.android.networkrecommendation.SynchronousNetworkRecommendationProvider;
import com.android.networkrecommendation.TestData;
import com.android.networkrecommendation.shadows.BitmapGetPixelsShadow;
import com.android.networkrecommendation.shadows.ShadowNotificationChannelUtil;
import com.android.networkrecommendation.config.Flag;
import com.android.networkrecommendation.util.RoboCompatUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Unit tests for {@link WifiNotificationHelper} */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/services/NetworkRecommendation/AndroidManifest.xml", sdk = 23,
shadows={BitmapGetPixelsShadow.class, ShadowNotificationChannelUtil.class})
public class WifiNotificationHelperTest {

    private Context mContext;

    @Mock
    private SynchronousNetworkRecommendationProvider mSynchronousNetworkRecommendationProvider;

    @Mock private RoboCompatUtil mRoboCompatUtil;

    private WifiNotificationHelper mWifiNotificationHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Flag.initForTest();
        RoboCompatUtil.setInstanceForTesting(mRoboCompatUtil);
        mContext = RuntimeEnvironment.application;

        mWifiNotificationHelper = new WifiNotificationHelper(mContext);
    }

    private static WifiConfiguration createFakeConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = TestData.SSID_1;
        config.BSSID = TestData.BSSID_1;
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        return config;
    }

    private static void assertValidNotification(Notification notification) {
        assertNotNull(notification);
        assertNotNull(notification.getSmallIcon());
    }

    @Test
    public void createMainNotification() {
        assertValidNotification(mWifiNotificationHelper.createMainNotification(createFakeConfig()));
    }

    @Test
    public void createConnectingNotification() {
        assertValidNotification(
                mWifiNotificationHelper.createConnectingNotification(createFakeConfig()));
    }

    @Test
    public void createConnectedNotification() {
        assertValidNotification(
                mWifiNotificationHelper.createConnectedNotification(createFakeConfig()));
    }

    @Test
    public void createFailedToConnectNotification() {
        assertValidNotification(mWifiNotificationHelper.createFailedToConnectNotification());
    }
}
