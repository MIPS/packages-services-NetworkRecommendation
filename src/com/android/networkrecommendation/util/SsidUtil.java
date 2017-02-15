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
package com.android.networkrecommendation.util;

import static com.android.networkrecommendation.Constants.TAG;

import android.net.WifiKey;
import android.support.annotation.Nullable;
import com.android.networkrecommendation.config.G;

/** Utility methods for Wifi Network SSID and BSSID manipulation. */
public final class SsidUtil {

    // A special BSSID used to indicate a wildcard/ignore.
    // The MAC address this refers to is reserved by IANA
    // http://www.iana.org/assignments/ethernet-numbers/ethernet-numbers.xhtml
    public static final String BSSID_IGNORE = "00:00:5E:00:00:00";

    /** Quote an SSID if it hasn't already been quoted. */
    @Nullable
    public static String quoteSsid(String ssid) {
        if (ssid == null) {
            return null;
        }
        if (ssid.startsWith("\"")) {
            return ssid;
        }
        return "\"" + ssid + "\"";
    }

    /**
     * Create a WifiKey for the given SSID/BSSID. Returns null if the key could not be created
     * (ssid/bssid are not valid patterns).
     */
    @Nullable
    public static WifiKey createWifiKey(String ssid, String bssid) {
        try {
            return new WifiKey(quoteSsid(ssid), bssid);
        } catch (IllegalArgumentException | NullPointerException e) {
            // Expect IllegalArgumentException only in Android O.
            Blog.e(
                    TAG,
                    e,
                    "Couldn't make a wifi key from %s/%s",
                    Blog.pii(ssid, G.Netrec.enableSensitiveLogging.get()),
                    Blog.pii(bssid, G.Netrec.enableSensitiveLogging.get()));
            return null;
        }
    }

    // Can't instantiate.
    private SsidUtil() {}
}
