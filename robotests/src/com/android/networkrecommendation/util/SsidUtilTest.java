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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link SsidUtil}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = "packages/services/NetworkRecommendation/AndroidManifest.xml", sdk = 23)
public class SsidUtilTest {

    @Rule public ExpectedException thrownException = ExpectedException.none();

    private static final String QUOTED_SSID = "\"foo\"";
    private static final String UNQUOTED_SSID = "foo";

    @Test
    public void testQuote() {
        assertEquals(QUOTED_SSID, SsidUtil.quoteSsid(UNQUOTED_SSID));
        assertEquals(QUOTED_SSID, SsidUtil.quoteSsid(QUOTED_SSID));
        assertEquals(QUOTED_SSID, SsidUtil.quoteSsid(SsidUtil.quoteSsid(UNQUOTED_SSID)));
        assertNull(SsidUtil.quoteSsid(null));
    }

    @Test
    public void testVerify() {
        assertFalse(SsidUtil.isValidQuotedSsid(UNQUOTED_SSID));
        assertTrue(SsidUtil.isValidQuotedSsid(QUOTED_SSID));
    }

    @Test
    public void testCheck() {
        thrownException.expect(IllegalArgumentException.class);
        SsidUtil.checkIsValidQuotedSsid(UNQUOTED_SSID);
    }
}
