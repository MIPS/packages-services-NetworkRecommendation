package com.android.networkrecommendation.shadows;

import android.annotation.ColorInt;
import android.graphics.Bitmap;

import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowBitmap;

@Implements(Bitmap.class)
public class BitmapGetPixelsShadow extends ShadowBitmap {
    public void getPixels(@ColorInt int[] pixels, int offset, int stride,
            int x, int y, int width, int height) {
    }
}
