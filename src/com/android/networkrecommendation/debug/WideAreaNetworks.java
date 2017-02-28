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
package com.android.networkrecommendation.debug;

import com.google.common.collect.ImmutableSet;

/**
 * This class contains a list of known wide area netowrks. TODO(netrec): replace this with a flag
 * value or a flag controlled bloom filter.
 */
public class WideAreaNetworks {
    private WideAreaNetworks() {}

    /**
     * @param ssid canonical SSID for a network (with quotes removed)
     * @return {@code true} if {@code ssid} is in the set of wide area networks.
     */
    public static final boolean contains(String ssid) {
        return WIDE_AREA_NETWORK_SSIDS.contains(ssid);
    }

    /** List of wide area networks. */
    private static final ImmutableSet<String> WIDE_AREA_NETWORK_SSIDS =
            ImmutableSet.of("xfinitywifi");
}
