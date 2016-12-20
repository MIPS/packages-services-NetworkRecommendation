package com.android.networkrecommendation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.NetworkCapabilities;
import android.net.NetworkKey;
import android.net.NetworkScoreManager;
import android.net.RecommendationRequest;
import android.net.RecommendationResult;
import android.net.RssiCurve;
import android.net.ScoredNetwork;
import android.net.WifiKey;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiSsid;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.test.runner.AndroidJUnit4;

import com.android.networkrecommendation.DefaultNetworkRecommendationService.DefaultNetworkRecommendationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Tests the recommendation provider directly, to test internals of the provider rather than the
 * service's API.
 */
@RunWith(AndroidJUnit4.class)
public class DefaultNetworkRecommendationProviderTest {

    private static final String GOOD_METERED_NETWORK_STRING_UNQUOTED = "Metered";
    private static final String GOOD_METERED_NETWORK_STRING = "\"Metered\",aa:bb:cc:dd:ee:ff" +
            "|-150,10,-128,-128,-128,-128,-128,-128,-128,-128,29,29,29,29,-128|1|0";

    private static final RssiCurve GOOD_METERED_NETWORK_CURVE = new RssiCurve(
            -150 /* start */, 10 /* bucketWidth */,
            new byte[]{-128, -128, -128, -128, -128, -128, -128, -128, 29, 29, 29, 29, -128});
    private static final ScoredNetwork GOOD_METERED_NETWORK = new ScoredNetwork(
            new NetworkKey(new WifiKey("\"Metered\"", "aa:bb:cc:dd:ee:ff")),
            GOOD_METERED_NETWORK_CURVE, true /* meteredHint */, new Bundle());

    private static final String GOOD_CAPTIVE_NETWORK_STRING_UNQUOTED = "Captive";
    private static final String GOOD_CAPTIVE_NETWORK_STRING =
            "\"Captive\",ff:ee:dd:cc:bb:aa|-160,18,-128,-128,-128,-128,-128,-128,31,31,31,-128|0|1";
    private static final RssiCurve GOOD_CAPTIVE_NETWORK_CURVE = new RssiCurve(
            -160 /* start */, 18 /* bucketWidth */,
            new byte[]{-128, -128, -128, -128, -128, -128, 31, 31, 31, -128});
    private static final ScoredNetwork GOOD_CAPTIVE_NETWORK;

    static {
        Bundle attributes = new Bundle();
        attributes.putBoolean(ScoredNetwork.EXTRA_HAS_CAPTIVE_PORTAL, true);
        GOOD_CAPTIVE_NETWORK = new ScoredNetwork(
                new NetworkKey(new WifiKey("\"Captive\"", "ff:ee:dd:cc:bb:aa")),
                GOOD_CAPTIVE_NETWORK_CURVE, false /* meteredHint */, attributes);
    }

    @Mock
    private DefaultNetworkRecommendationProvider.CallbackWrapper mCallback;

    @Mock
    private NetworkScoreManager mNetworkScoreManager;

    private DefaultNetworkRecommendationService.ScoreStorage mStorage;
    private DefaultNetworkRecommendationProvider mProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mProvider = new DefaultNetworkRecommendationProvider(
                new Handler(Looper.getMainLooper()), mNetworkScoreManager,
                new DefaultNetworkRecommendationService.ScoreStorage());
    }

    @Test
    public void basicRecommendation() throws Exception {

        ScanResult[] scanResults = new ScanResult[6];
        for (int i = 0; i < 3; i++) {
            scanResults[i] = Util.createMockScanResult(i);
        }

        // For now we add directly to storage, but when we start calling
        // networkScoreManager.updateScores, we'll have to adjust this test.
        mProvider.addScoreForTest(GOOD_METERED_NETWORK);
        {
            ScanResult scanResult = new ScanResult();
            scanResult.level = 115;
            scanResult.SSID = GOOD_METERED_NETWORK_STRING_UNQUOTED;
            scanResult.wifiSsid = WifiSsid.createFromAsciiEncoded(
                    GOOD_METERED_NETWORK_STRING_UNQUOTED);
            scanResult.BSSID = GOOD_METERED_NETWORK.networkKey.wifiKey.bssid;
            scanResult.capabilities = "[ESS]";
            scanResult.timestamp = SystemClock.elapsedRealtime() * 1000;
            scanResults[3] = scanResult;
        }

        for (int i = 4; i < 6; i++) {
            scanResults[i] = Util.createMockScanResult(i);
        }

        RecommendationRequest request = new RecommendationRequest.Builder()
                .setScanResults(scanResults)
                .setNetworkCapabilities(new NetworkCapabilities().removeCapability(
                        NetworkCapabilities.NET_CAPABILITY_TRUSTED))
                .build();

        RecommendationResult result = verifyAndCaptureResult(request);
        assertEquals(GOOD_METERED_NETWORK.networkKey.wifiKey.ssid,
                result.getWifiConfiguration().SSID);
    }

    @Test
    public void recommendation_noScans_returnsCurrentConfig() throws Exception {
        ScanResult[] scanResults = new ScanResult[0];

        WifiConfiguration expectedConfig = new WifiConfiguration();
        expectedConfig.SSID = "ssid";
        expectedConfig.BSSID = "bssid";
        RecommendationRequest request = new RecommendationRequest.Builder()
                .setScanResults(scanResults)
                .setNetworkCapabilities(new NetworkCapabilities().removeCapability(
                        NetworkCapabilities.NET_CAPABILITY_TRUSTED))
                .setCurrentRecommendedWifiConfig(expectedConfig)
                .build();

        RecommendationResult result = verifyAndCaptureResult(request);
        assertEquals(expectedConfig, result.getWifiConfiguration());
    }

    @Test
    public void recommendation_noScans_noCurrentConfig_returnsEmpty() throws Exception {
        ScanResult[] scanResults = new ScanResult[0];

        RecommendationRequest request = new RecommendationRequest.Builder()
                .setScanResults(scanResults)
                .setNetworkCapabilities(new NetworkCapabilities().removeCapability(
                        NetworkCapabilities.NET_CAPABILITY_TRUSTED))
                .build();

        RecommendationResult result = verifyAndCaptureResult(request);
        assertNull(result.getWifiConfiguration());
    }

    @Test
    public void scoreNetworks() throws Exception {
        NetworkKey[] keys =
                new NetworkKey[]{GOOD_METERED_NETWORK.networkKey, GOOD_CAPTIVE_NETWORK.networkKey};
        mProvider.onRequestScores(keys);

        verify(mNetworkScoreManager).updateScores(Mockito.any());
    }

    @Test
    public void scoreNetworks_empty() throws Exception {
        NetworkKey[] keys = new NetworkKey[]{};
        mProvider.onRequestScores(keys);

        verify(mNetworkScoreManager, times(0)).updateScores(Mockito.any());
    }

    @Test
    public void dumpAddScores() {
        String[] args = {"addScore", GOOD_METERED_NETWORK_STRING};
        mProvider.dump(null /* fd */, new PrintWriter(new StringWriter()), args);

        ScoredNetwork[] scoredNetworks = verifyAndCaptureScoredNetworks();
        assertEquals(1, scoredNetworks.length);
        ScoredNetwork score = scoredNetworks[0];

        assertEquals(GOOD_METERED_NETWORK.networkKey.wifiKey.ssid, score.networkKey.wifiKey.ssid);
        assertEquals(GOOD_METERED_NETWORK.networkKey.wifiKey.bssid, score.networkKey.wifiKey.bssid);

        assertEquals(GOOD_METERED_NETWORK.meteredHint, score.meteredHint);
        assertEquals(
                GOOD_METERED_NETWORK.attributes.getBoolean(ScoredNetwork.EXTRA_HAS_CAPTIVE_PORTAL),
                score.attributes.getBoolean(ScoredNetwork.EXTRA_HAS_CAPTIVE_PORTAL));

        assertEquals(GOOD_METERED_NETWORK_CURVE.start, score.rssiCurve.start);
        assertEquals(GOOD_METERED_NETWORK_CURVE.bucketWidth, score.rssiCurve.bucketWidth);
        assertArrayEquals(GOOD_METERED_NETWORK_CURVE.rssiBuckets, score.rssiCurve.rssiBuckets);
    }

    @Test
    public void dumpAddScores_goodCaptivePortal() {
        String[] args = {"addScore", GOOD_CAPTIVE_NETWORK_STRING};
        mProvider.dump(null /* fd */, new PrintWriter(new StringWriter()), args);

        ScoredNetwork[] scoredNetworks = verifyAndCaptureScoredNetworks();
        assertEquals(1, scoredNetworks.length);
        ScoredNetwork score = scoredNetworks[0];

        assertEquals(GOOD_CAPTIVE_NETWORK.networkKey.wifiKey.ssid, score.networkKey.wifiKey.ssid);
        assertEquals(GOOD_CAPTIVE_NETWORK.networkKey.wifiKey.bssid, score.networkKey.wifiKey.bssid);

        assertEquals(GOOD_CAPTIVE_NETWORK.meteredHint, score.meteredHint);

        assertEquals(
                GOOD_CAPTIVE_NETWORK.attributes.getBoolean(ScoredNetwork.EXTRA_HAS_CAPTIVE_PORTAL),
                score.attributes.getBoolean(ScoredNetwork.EXTRA_HAS_CAPTIVE_PORTAL));
        assertEquals(GOOD_CAPTIVE_NETWORK_CURVE.start, score.rssiCurve.start);
        assertEquals(GOOD_CAPTIVE_NETWORK_CURVE.bucketWidth, score.rssiCurve.bucketWidth);
        assertArrayEquals(GOOD_CAPTIVE_NETWORK_CURVE.rssiBuckets,
                score.rssiCurve.rssiBuckets);
    }

    private RecommendationResult verifyAndCaptureResult(
            RecommendationRequest request) {
        mProvider.doOnRequestRecommendation(request, mCallback);

        ArgumentCaptor<RecommendationResult> resultCaptor =
                ArgumentCaptor.forClass(RecommendationResult.class);
        verify(mCallback).onResult(resultCaptor.capture());

        return resultCaptor.getValue();
    }

    private ScoredNetwork[] verifyAndCaptureScoredNetworks() {
        ArgumentCaptor<ScoredNetwork[]> resultCaptor = ArgumentCaptor.forClass(
                ScoredNetwork[].class);
        verify(mNetworkScoreManager).updateScores(resultCaptor.capture());
        return resultCaptor.getValue();
    }
}
