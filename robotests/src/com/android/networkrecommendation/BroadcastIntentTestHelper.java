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

import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.PowerManager;

/** Convenience methods for sending Intent broadcasts. */
public class BroadcastIntentTestHelper {

    private final Context mContext;

    public BroadcastIntentTestHelper(Context context) {
        mContext = context;
    }

    public void sendPowerSaveModeChanged() {
        Intent intent = new Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
        mContext.sendBroadcast(intent);
    }

    public void sendWifiStateChanged() {
        Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mContext.sendBroadcast(intent);
    }

    public void sendNetworkStateChanged(NetworkInfo networkInfo) {
        Intent intent = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_NETWORK_INFO, networkInfo);
        mContext.sendBroadcast(intent);
    }

    public void sendScanResultsAvailable() {
        Intent intent = new Intent(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        mContext.sendBroadcast(intent);
    }

    public void sendWifiApStateChanged() {
        Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        mContext.sendBroadcast(intent);
    }

    public void sendConfiguredNetworksChanged() {
        Intent intent = new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        mContext.sendBroadcast(intent);
    }
}
