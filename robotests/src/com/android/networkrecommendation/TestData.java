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

import static com.android.networkrecommendation.PlatformTestObjectFactory.createNetworkKey;

import android.net.NetworkKey;
import com.android.networkrecommendation.util.SsidUtil;

/**
 * Stock objects which can be re-used in multiple tests.
 *
 * <p>Objects here should be kept simple and generic; test-specific variants should be created
 * inside tests as opposed to here.
 */
public class TestData {

    // SSID and BSSID values
    public static final String UNQUOTED_SSID_1 = "ssid1";
    public static final String UNQUOTED_SSID_2 = "ssid2";
    public static final String UNQUOTED_SSID_3 = "ssid3";
    public static final String SSID_1 = SsidUtil.quoteSsid(UNQUOTED_SSID_1);
    public static final String SSID_2 = SsidUtil.quoteSsid(UNQUOTED_SSID_2);
    public static final String SSID_3 = SsidUtil.quoteSsid(UNQUOTED_SSID_3);
    public static final String BSSID_1 = "01:01:01:01:01:01";
    public static final String BSSID_2 = "02:02:02:02:02:02";
    public static final String BSSID_3 = "03:03:03:03:03:03";

    // Platform objects.
    public static final NetworkKey NETWORK_KEY1 = createNetworkKey(SSID_1, BSSID_1);
    public static final NetworkKey NETWORK_KEY2 = createNetworkKey(SSID_2, BSSID_2);

    // Can't instantiate.
    private TestData() {}
}
