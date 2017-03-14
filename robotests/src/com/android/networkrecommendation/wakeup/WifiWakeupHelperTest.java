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

import static com.google.common.truth.Truth.assertThat;
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
import android.os.Handler;
import android.os.Looper;
import com.android.networkrecommendation.config.Flag;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Unit tests for {@link WifiWakeupHelper} */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/services/NetworkRecommendation/AndroidManifest.xml", sdk = 23)
public class WifiWakeupHelperTest {

    private static final String SSID = "ssid";

    private Context mContext;
    private WifiConfiguration mWifiConfiguration;
    @Mock private NotificationManager mNotificationManager;
    @Mock private WifiManager mWifiManager;
    @Mock private WifiInfo mWifiInfo;
    private SharedPreferences mSharedPreferences;

    private WifiWakeupHelper mWifiWakeupHelper;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Flag.initForTest();
        mWifiConfiguration = new WifiConfiguration();
        mWifiConfiguration.SSID = "\"" + SSID + "\"";

        mContext = RuntimeEnvironment.application;
        mSharedPreferences = mContext.getSharedPreferences("wifi_wakeup", Context.MODE_PRIVATE);

        when(mWifiManager.getConnectionInfo()).thenReturn(mWifiInfo);

        mWifiWakeupHelper =
                new WifiWakeupHelper(
                        mContext,
                        mContext.getResources(),
                        new Handler(Looper.getMainLooper()),
                        mNotificationManager,
                        mWifiManager,
                        mSharedPreferences);
    }

    @Test
    public void notificationShowsOncePerSsid() {
        mWifiWakeupHelper.startWifiSession(mWifiConfiguration);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        mWifiWakeupHelper.startWifiSession(mWifiConfiguration);

        verify(mNotificationManager, times(1))
                .notify(anyString(), anyInt(), any(Notification.class));
        Set<String> ssidSet =
                mSharedPreferences.getStringSet(WifiWakeupHelper.KEY_SHOWN_SSIDS, null);
        assertEquals(1, ssidSet.size());
        assertTrue(ssidSet.contains(mWifiConfiguration.SSID));
    }

    @Test
    public void notificationCanceledWhenNeverConnected() {
        mWifiWakeupHelper.startWifiSession(mWifiConfiguration);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        verify(mNotificationManager).cancel(anyString(), anyInt());
    }

    @Test
    public void notificationCanceledWhenWifiDisabled() {
        mWifiWakeupHelper.startWifiSession(mWifiConfiguration);

        when(mWifiInfo.getSSID()).thenReturn(SSID);
        when(mWifiManager.isWifiEnabled()).thenReturn(true, false);

        mContext.sendBroadcast(new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        mContext.sendBroadcast(new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        verify(mNotificationManager, times(1))
                .notify(anyString(), anyInt(), any(Notification.class));
        verify(mNotificationManager).cancel(anyString(), anyInt());
    }

    @Test
    public void notificationCanceledWhenSsidChanged() throws Exception {
        mWifiWakeupHelper.startWifiSession(mWifiConfiguration);

        when(mWifiInfo.getSSID()).thenReturn(SSID, "blah");
        when(mWifiManager.isWifiEnabled()).thenReturn(true);

        mContext.sendBroadcast(new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        verify(mNotificationManager, never()).cancel(anyString(), anyInt());

        mContext.sendBroadcast(new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        verify(mNotificationManager).cancel(anyString(), anyInt());
    }

    @Test
    public void sessionLoggedWithoutNotification() {
        mSharedPreferences
                .edit()
                .putStringSet(
                        WifiWakeupHelper.KEY_SHOWN_SSIDS, ImmutableSet.of(mWifiConfiguration.SSID))
                .commit();
        when(mWifiInfo.getSSID()).thenReturn(SSID, "blah");
        when(mWifiManager.isWifiEnabled()).thenReturn(true);

        mWifiWakeupHelper.startWifiSession(mWifiConfiguration);
        mContext.sendBroadcast(new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION));
        mContext.sendBroadcast(new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION));

        verify(mNotificationManager, never())
                .notify(anyString(), anyInt(), any(Notification.class));
        verify(mNotificationManager, never()).cancel(anyString(), anyInt());
    }

    @Test
    public void tappingOnSettingsFromNotificationOpensSettingsActivity() {
        mWifiWakeupHelper.startWifiSession(mWifiConfiguration);

        mContext.sendBroadcast(new Intent(WifiWakeupHelper.ACTION_WIFI_SETTINGS));

        Intent intent = Shadows.shadowOf(RuntimeEnvironment.application).getNextStartedActivity();

        assertThat(intent.getAction()).isEqualTo("android.settings.CONFIGURE_WIFI_SETTINGS");
    }

    @Test
    public void dismissingNotificationCancelsNotification() {
        mWifiWakeupHelper.startWifiSession(mWifiConfiguration);

        mContext.sendBroadcast(
                new Intent(WifiWakeupHelper.ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION));

        verify(mNotificationManager, times(1))
                .notify(anyString(), anyInt(), any(Notification.class));
        verify(mNotificationManager).cancel(anyString(), anyInt());
    }
}
