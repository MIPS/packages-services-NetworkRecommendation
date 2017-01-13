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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.Looper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

/**
 * Unit tests for {@link WifiWakeupNetworkSelector}
 */
@RunWith(AndroidJUnit4.class)
public class WifiWakeupNotificationHelperTest {
    private static final String SSID = "ssid";
    private static final WifiConfiguration WIFI_CONFIGURATION = new WifiConfiguration();

    static {
        WIFI_CONFIGURATION.SSID = "\"" + SSID + "\"";
    }

    private Context mContext;
    @Mock private NotificationManager mNotificationManager;
    @Mock private WifiManager mWifiManager;
    private SharedPreferences mSharedPreferences;
    private String mSharedPreferenceName;

    private WifiWakeupNotificationHelper mWifiWakeupNotificationHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mContext = InstrumentationRegistry.getTargetContext();
        mSharedPreferenceName = "WifiWakeupNotificationHelperTest";
        mSharedPreferences = mContext.getSharedPreferences(mSharedPreferenceName,
                Context.MODE_PRIVATE);

        mWifiWakeupNotificationHelper = new WifiWakeupNotificationHelper(mContext,
                mContext.getResources(), new Handler(Looper.getMainLooper()), mNotificationManager,
                mWifiManager, mSharedPreferences);
    }

    @After
    public void tearDown() throws Exception {
        mContext.deleteSharedPreferences(mSharedPreferenceName);
    }

    @Test
    public void notificationShowsOncePerSsid() {
        mWifiWakeupNotificationHelper.maybeShowWifiEnabledNotification(WIFI_CONFIGURATION);
        mWifiWakeupNotificationHelper.maybeShowWifiEnabledNotification(WIFI_CONFIGURATION);

        verify(mNotificationManager, times(1))
                .notify(anyString(), anyInt(), any(Notification.class));
        Set<String> ssidSet = mSharedPreferences.getStringSet(
                WifiWakeupNotificationHelper.KEY_SHOWN_SSIDS, null);
        assertEquals(1, ssidSet.size());
        assertTrue(ssidSet.contains(WIFI_CONFIGURATION.SSID));
    }

    @Test
    public void notificationCanceledWhenNeverConnected() {
        mWifiWakeupNotificationHelper.maybeShowWifiEnabledNotification(WIFI_CONFIGURATION);

        mWifiWakeupNotificationHelper.mCancelNotification.run();

        verify(mNotificationManager).cancel(anyString(), anyInt());
    }

    @Test
    public void notificationCanceledWhenWifiDisabled() {
        mWifiWakeupNotificationHelper.maybeShowWifiEnabledNotification(WIFI_CONFIGURATION);

        when(mWifiManager.isWifiEnabled()).thenReturn(false);

        mWifiWakeupNotificationHelper.mBroadcastReceiver.onReceive(mContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));

        verify(mNotificationManager).cancel(anyString(), anyInt());
    }

    @Test
    public void notificationCanceledWhenSsidChanged() {
        WifiInfo firstWifiInfo = new WifiInfo();
        firstWifiInfo.setSSID(WifiSsid.createFromAsciiEncoded(SSID));
        WifiInfo secondWifiInfo = new WifiInfo();
        firstWifiInfo.setSSID(WifiSsid.createFromAsciiEncoded("blah"));

        mWifiWakeupNotificationHelper.maybeShowWifiEnabledNotification(WIFI_CONFIGURATION);

        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.getConnectionInfo()).thenReturn(firstWifiInfo, secondWifiInfo);

        mWifiWakeupNotificationHelper.mBroadcastReceiver.onReceive(mContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));

        verify(mNotificationManager, never()).cancel(anyString(), anyInt());

        mWifiWakeupNotificationHelper.mBroadcastReceiver.onReceive(mContext,
                new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION));

        verify(mNotificationManager).cancel(anyString(), anyInt());
    }
}
