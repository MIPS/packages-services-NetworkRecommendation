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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.networkrecommendation.R;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for building and showing notifications for {@link WifiWakeupController}.
 */
public class WifiWakeupNotificationHelper {
    private static final String TAG = "WifiWakeupNotifHelper";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** Unique ID used for the Wi-Fi Enabled notification. */
    private static final int NOTIFICATION_ID = R.string.wifi_wakeup_enabled_notification_title;
    @VisibleForTesting
    static final String KEY_SHOWN_SSIDS = "key_shown_ssids";
    private static final String ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION =
            "com.android.networkrecommendation.ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION";
    private static final IntentFilter INTENT_FILTER = new IntentFilter();
    private static final long NETWORK_CONNECTED_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(30);

    static {
        INTENT_FILTER.addAction(ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION);
        INTENT_FILTER.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    }

    private final Context mContext;
    private final Resources mResources;
    private final NotificationManager mNotificationManager;
    private final Handler mHandler;
    private final WifiManager mWifiManager;
    private final SharedPreferences mSharedPreferences;

    @VisibleForTesting
    final Runnable mCancelNotification = new Runnable() {
        @Override
        public void run() {
            cancelNotificationAndUnregisterReceiver();
        }
    };

    @VisibleForTesting
    final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION.equals(intent.getAction())) {
                cancelNotificationAndUnregisterReceiver();
            } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                networkStateChanged();
            }
        }
    };
    private boolean mNotificationShown;
    private String mConnectedSsid;

    public WifiWakeupNotificationHelper(Context context, Resources resources, Handler handler,
            NotificationManager notificationManager, WifiManager wifiManager) {
        this(context, resources, handler, notificationManager, wifiManager,
                context.getSharedPreferences("wifi_wakeup", Context.MODE_PRIVATE));
    }

    @VisibleForTesting
    WifiWakeupNotificationHelper(Context context, Resources resources, Handler handler,
            NotificationManager notificationManager, WifiManager wifiManager,
            SharedPreferences sharedPreferences) {
        mContext = context;
        mResources = resources;
        mNotificationManager = notificationManager;
        mHandler = handler;
        mWifiManager = wifiManager;
        mSharedPreferences = sharedPreferences;
        mNotificationShown = false;
        mConnectedSsid = null;
    }

    /**
     * Show a notification that Wi-Fi has been enabled by Wi-Fi Wakeup.
     *
     * @param wifiConfiguration the {@link WifiConfiguration} that triggered Wi-Fi to wakeup
     */
    public void maybeShowWifiEnabledNotification(@NonNull WifiConfiguration wifiConfiguration) {
        Set<String> ssidSet = mSharedPreferences.getStringSet(KEY_SHOWN_SSIDS, null);
        if (ssidSet == null) {
            ssidSet = new ArraySet<>();
        } else if (ssidSet.contains(wifiConfiguration.SSID)) {
            if (DEBUG) {
                Log.d(TAG, "Already showed Wi-Fi Enabled notification for ssid: "
                        + wifiConfiguration.SSID);
            }
            return;
        }
        ssidSet.add(wifiConfiguration.SSID);
        mSharedPreferences.edit().putStringSet(KEY_SHOWN_SSIDS, ssidSet).apply();

        String title = mResources.getString(
                R.string.wifi_wakeup_enabled_notification_title);
        String summary = mResources.getString(
                R.string.wifi_wakeup_enabled_notification_context, wifiConfiguration.SSID);
        PendingIntent savedNetworkSettingsPendingIntent = PendingIntent.getActivity(mContext, 0,
                new Intent(Settings.ACTION_CONFIGURE_WIFI_SETTINGS),
                PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent deletePendingIntent = PendingIntent.getActivity(mContext, 0,
                new Intent(ACTION_DISMISS_WIFI_ENABLED_NOTIFICATION),
                PendingIntent.FLAG_UPDATE_CURRENT);
        Bundle extras = new Bundle();
        extras.putString(Notification.EXTRA_SUBSTITUTE_APP_NAME,
                mResources.getString(R.string.android_system_label));
        Notification notification = new Notification.Builder(mContext)
                .setContentTitle(title)
                .setSmallIcon(R.drawable.ic_wifi_signal_4)
                .setStyle(new Notification.BigTextStyle().bigText(summary))
                .setAutoCancel(true)
                .setDeleteIntent(deletePendingIntent)
                .setPriority(Notification.PRIORITY_LOW)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(savedNetworkSettingsPendingIntent)
                .addExtras(extras)
                .build();
        mNotificationManager.notify(TAG, NOTIFICATION_ID, notification);
        mNotificationShown = true;
        mContext.registerReceiver(mBroadcastReceiver, INTENT_FILTER, null /* broadcastPermission*/,
                mHandler);
        mHandler.postDelayed(mCancelNotification, NETWORK_CONNECTED_TIMEOUT_MILLIS);
    }

    private void cancelNotificationAndUnregisterReceiver() {
        if (mNotificationShown) {
            mNotificationShown = false;
            mConnectedSsid = null;
            mNotificationManager.cancel(TAG, NOTIFICATION_ID);
            mContext.unregisterReceiver(mBroadcastReceiver);
        }
    }

    private void networkStateChanged() {
        if (!mWifiManager.isWifiEnabled()) {
            cancelNotificationAndUnregisterReceiver();
            return;
        }

        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        String ssid = wifiInfo == null ? null : wifiInfo.getSSID();
        if (mConnectedSsid == null) {
            mConnectedSsid = ssid;
            mHandler.removeCallbacks(mCancelNotification);
        } else {
            if (!TextUtils.equals(ssid, mConnectedSsid)) {
                cancelNotificationAndUnregisterReceiver();
            }
        }
    }
}
