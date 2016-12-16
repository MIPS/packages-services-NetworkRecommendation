package com.android.networkrecommendation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.ScoredNetwork;
import android.support.test.runner.AndroidJUnit4;

import com.android.networkrecommendation.DefaultNetworkRecommendationService.DefaultNetworkRecommendationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the recommendation provider directly, to test internals of the provider rather than the
 * service's API.
 */
@RunWith(AndroidJUnit4.class)
public class DefaultNetworkRecommendationProviderTest {

    private static final String GOOD_PAID_NETWORK = "\"Paid Network\",aa:bb:cc:dd:ee:ff" +
            "|-150,10,-128,-128,-128,-128,-128,-128,-128,-128,29,29,29,29,-128|1|0";
    private static final String GOOD_CAPTIVE_PORTAL_NETWORK = "\"My Network\",ff:ee:dd:cc:bb:aa" +
            "|-160,18,-128,-128,-128,-128,-128,-128,31,31,31,-128|0|1";

    @Test
    public void parseManualScores_goodPaid() {
        ScoredNetwork score =
                DefaultNetworkRecommendationProvider.parseScore(GOOD_PAID_NETWORK);

        assertEquals("\"Paid Network\"", score.networkKey.wifiKey.ssid);
        assertEquals("aa:bb:cc:dd:ee:ff", score.networkKey.wifiKey.bssid);

        assertTrue(score.meteredHint);
        assertFalse(score.attributes.getBoolean(ScoredNetwork.EXTRA_HAS_CAPTIVE_PORTAL));

        assertEquals(-150, score.rssiCurve.start);
        assertEquals(10, score.rssiCurve.bucketWidth);
        assertArrayEquals(
                new byte[]{
                        -128, -128, -128, -128, -128, -128, -128, -128, 29, 29, 29, 29, -128},
                score.rssiCurve.rssiBuckets);
    }

    @Test
    public void parseManualScores_goodCaptivePortal() {
        ScoredNetwork score =
                DefaultNetworkRecommendationProvider.parseScore(GOOD_CAPTIVE_PORTAL_NETWORK);

        assertEquals("\"My Network\"", score.networkKey.wifiKey.ssid);
        assertEquals("ff:ee:dd:cc:bb:aa", score.networkKey.wifiKey.bssid);

        assertFalse(score.meteredHint);
        assertTrue(score.attributes.getBoolean(ScoredNetwork.EXTRA_HAS_CAPTIVE_PORTAL));

        assertEquals(-160, score.rssiCurve.start);
        assertEquals(18, score.rssiCurve.bucketWidth);
        assertArrayEquals(
                new byte[]{-128, -128, -128, -128, -128, -128, 31, 31, 31, -128},
                score.rssiCurve.rssiBuckets);
    }
}
