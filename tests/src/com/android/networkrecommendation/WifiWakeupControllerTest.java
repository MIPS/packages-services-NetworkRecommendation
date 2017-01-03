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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.net.wifi.WifiScanner;
import android.os.Looper;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Unit tests for {@link com.android.server.wifi.WifiWakeupController}.
 */
@RunWith(AndroidJUnit4.class)
public class WifiWakeupControllerTest {
    public static final String TAG = "WifiScanningServiceTest";

    @Mock private Context mContext;
    @Mock private WifiScanner mWifiScanner;
    private ContentResolver mContentResolver;

    private WifiWakeupController mWifiWakeupController;
    private int mWifiWakeupEnabledOriginalValue;

    /** Initialize objects before each test run. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContentResolver = InstrumentationRegistry.getTargetContext().getContentResolver();

        mWifiWakeupEnabledOriginalValue =
                Settings.Global.getInt(mContentResolver, Settings.Global.WIFI_WAKEUP_ENABLED, 0);
        Settings.Global.putInt(mContentResolver, Settings.Global.WIFI_WAKEUP_ENABLED, 1);
        mWifiWakeupController = new WifiWakeupController(mContext, mContentResolver,
                Looper.getMainLooper());
        mWifiWakeupController.start();
    }

    @After
    public void tearDown() {
        Settings.Global.putInt(mContentResolver, Settings.Global.WIFI_WAKEUP_ENABLED,
                mWifiWakeupEnabledOriginalValue);
    }

    /** Test WifiWakeupEnabledSettingObserver enables feature correctly. */
    @Test
    public void testEnableWifiWakeup() {
        assertTrue(mWifiWakeupController.mWifiWakeupEnabled);

        Settings.Global.putInt(mContentResolver, Settings.Global.WIFI_WAKEUP_ENABLED, 0);
        mWifiWakeupController.mContentObserver.onChange(true);
        assertFalse(mWifiWakeupController.mWifiWakeupEnabled);
    }

    /** Test dump() does not crash. */
    @Test
    public void testDump() {
        StringWriter stringWriter = new StringWriter();
        mWifiWakeupController.dump(
                new FileDescriptor(), new PrintWriter(stringWriter), new String[0]);
    }
}
