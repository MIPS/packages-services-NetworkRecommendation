package com.android.networkrecommendation;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiSsid;
import android.os.SystemClock;

/**
 * Ugly bag of helpers for tests.
 */
public class Util {

    /** Create a scan result with some basic properties. */
    static ScanResult createMockScanResult(int i) {
        ScanResult scanResult = new ScanResult();
        scanResult.level = i;
        scanResult.SSID = "ssid-" + i;
        scanResult.wifiSsid = WifiSsid.createFromAsciiEncoded("ssid-" + i);
        scanResult.BSSID = "aa:bb:cc:dd:ee:0" + i;
        scanResult.capabilities = "[ESS]";
        scanResult.timestamp = SystemClock.elapsedRealtime() * 1000;
        return scanResult;
    }
}
