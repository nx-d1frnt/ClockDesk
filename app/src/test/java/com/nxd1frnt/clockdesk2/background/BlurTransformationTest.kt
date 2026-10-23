package com.nxd1frnt.clockdesk2.background

import android.app.Application
import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPoolAdapter
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BlurTransformationTest {
    private fun blur(width: Int, height: Int, sampling: Int = 1): Bitmap {
        val context = RuntimeEnvironment.getApplication()
        val input = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val resource = BitmapResource.obtain(input, BitmapPoolAdapter())!!
        return BlurTransformation(context, 100, sampling)
            .transform(context, resource, 270, 180).get()
    }

    @Test fun smallAndLargeCoversUseTheSameBlurScale() {
        for (size in listOf(32, 128, 512, 2048)) {
            val result = blur(size, size)
            assertEquals(180, result.width)
            assertEquals(180, result.height)
        }
    }

    @Test fun normalizationPreservesPortraitAndLandscapeAspectRatios() {
        val landscape = blur(1200, 600)
        assertEquals(270, landscape.width)
        assertEquals(135, landscape.height)
        val portrait = blur(60, 120)
        assertEquals(90, portrait.width)
        assertEquals(180, portrait.height)
    }

    @Test fun samplingKeepsThinImagesAtLeastOnePixelWide() {
        val result = blur(1, 1000, sampling = 3)
        assertEquals(1, result.width)
        assertEquals(60, result.height)
    }

    @Test fun disabledBlurPreservesOriginalBitmap() {
        val context = RuntimeEnvironment.getApplication()
        val input = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val resource = BitmapResource.obtain(input, BitmapPoolAdapter())!!
        assertSame(input, BlurTransformation(context, 0)
            .transform(context, resource, 270, 180).get())
    }
}
