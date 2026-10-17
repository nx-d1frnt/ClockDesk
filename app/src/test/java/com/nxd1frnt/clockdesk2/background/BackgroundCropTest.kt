package com.nxd1frnt.clockdesk2.background

import com.nxd1frnt.clockdesk2.ui.view.DynamicBackgroundView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundCropTest {

    @Test
    fun test9to16CropToTopAndSwitchTo16to9StaysInBounds() {
        val screenW = 1920
        val screenH = 1080

        // 1. 9:16 portrait image (1080x1920) on 16:9 landscape screen (1920x1080)
        // User crops to top: dragging downwards increases curOffsetY
        val offsetTopY = 1166.66f
        val cropPortrait = DynamicBackgroundView.computeCenterCrop(
            imgW = 1080,
            imgH = 1920,
            vw = screenW,
            vh = screenH,
            isGradient = false,
            scale = 1f,
            offsetX = 0f,
            offsetY = offsetTopY,
            unscaledViewWidth = screenW.toFloat(),
            unscaledViewHeight = screenH.toFloat()
        )

        val portraitScaleX = cropPortrait[0]
        val portraitScaleY = cropPortrait[1]
        val portraitDx = cropPortrait[2]
        val portraitDy = cropPortrait[3]

        assertTrue("Portrait Dx should be >= 0", portraitDx >= 0f)
        assertTrue("Portrait Dy should be >= 0", portraitDy >= 0f)
        assertTrue("Portrait U coords should be <= 1.0", portraitDx + portraitScaleX <= 1.0001f)
        assertTrue("Portrait V coords should be <= 1.0", portraitDy + portraitScaleY <= 1.0001f)
        // With offset to top, top edge should touch or be close to 0.0
        assertTrue("Portrait cropped to top should have Dy near 0", portraitDy in 0f..0.01f)

        // 2. Now switch to 16:9 image (1920x1080).
        // Even if old/stale offset (1166.66f) is passed, it MUST NOT go out of bounds.
        val cropLandscape = DynamicBackgroundView.computeCenterCrop(
            imgW = 1920,
            imgH = 1080,
            vw = screenW,
            vh = screenH,
            isGradient = false,
            scale = 1f,
            offsetX = 0f,
            offsetY = offsetTopY,
            unscaledViewWidth = screenW.toFloat(),
            unscaledViewHeight = screenH.toFloat()
        )

        val landscapeScaleX = cropLandscape[0]
        val landscapeScaleY = cropLandscape[1]
        val landscapeDx = cropLandscape[2]
        val landscapeDy = cropLandscape[3]

        assertEquals(1f, landscapeScaleX, 0.001f)
        assertEquals(1f, landscapeScaleY, 0.001f)
        // Crucial: Dy was previously -1.08 (out of bounds). Now must be clamped to 0.0!
        assertEquals("Landscape Dy must be clamped to 0.0", 0f, landscapeDy, 0.0001f)
        assertEquals("Landscape Dx must be 0.0", 0f, landscapeDx, 0.0001f)
        assertTrue("Landscape U coords within bounds", landscapeDx >= 0f && landscapeDx + landscapeScaleX <= 1.0001f)
        assertTrue("Landscape V coords within bounds", landscapeDy >= 0f && landscapeDy + landscapeScaleY <= 1.0001f)

        // 3. Now switch back to 9:16 image (1080x1920) with the preserved offset.
        // The clamped position from the 16:9 image MUST NOT persist - it should return to the original top crop!
        val cropPortraitAgain = DynamicBackgroundView.computeCenterCrop(
            imgW = 1080,
            imgH = 1920,
            vw = screenW,
            vh = screenH,
            isGradient = false,
            scale = 1f,
            offsetX = 0f,
            offsetY = offsetTopY,
            unscaledViewWidth = screenW.toFloat(),
            unscaledViewHeight = screenH.toFloat()
        )

        assertEquals("Returning to portrait must restore original Dy", portraitDy, cropPortraitAgain[3], 0.0001f)
        assertEquals("Returning to portrait must restore original Dx", portraitDx, cropPortraitAgain[2], 0.0001f)
        assertEquals("Returning to portrait must restore original ScaleY", portraitScaleY, cropPortraitAgain[1], 0.0001f)
        assertEquals("Returning to portrait must restore original ScaleX", portraitScaleX, cropPortraitAgain[0], 0.0001f)
    }

    @Test
    fun testExcessiveOffsetsAlwaysClampedAcrossAspectRatios() {
        val screenW = 1920
        val screenH = 1080
        val testImages = listOf(
            Pair(1080, 1920), // 9:16
            Pair(1920, 1080), // 16:9
            Pair(1440, 1080), // 4:3
            Pair(1080, 1080), // 1:1
            Pair(2560, 1080), // 21:9
            Pair(1080, 2400)  // 9:20
        )

        val testOffsets = listOf(
            Pair(10000f, 10000f),
            Pair(-10000f, -10000f),
            Pair(5000f, -5000f),
            Pair(-5000f, 5000f),
            Pair(0f, 0f)
        )

        for ((imgW, imgH) in testImages) {
            for ((offX, offY) in testOffsets) {
                val crop = DynamicBackgroundView.computeCenterCrop(
                    imgW = imgW,
                    imgH = imgH,
                    vw = screenW,
                    vh = screenH,
                    isGradient = false,
                    scale = 1f,
                    offsetX = offX,
                    offsetY = offY,
                    unscaledViewWidth = screenW.toFloat(),
                    unscaledViewHeight = screenH.toFloat()
                )

                val sX = crop[0]
                val sY = crop[1]
                val dx = crop[2]
                val dy = crop[3]

                assertTrue("Image ${imgW}x${imgH} with offset ($offX, $offY) has dx >= 0", dx >= 0f)
                assertTrue("Image ${imgW}x${imgH} with offset ($offX, $offY) has dy >= 0", dy >= 0f)
                assertTrue("Image ${imgW}x${imgH} with offset ($offX, $offY) has dx + sX <= 1.0", dx + sX <= 1.0001f)
                assertTrue("Image ${imgW}x${imgH} with offset ($offX, $offY) has dy + sY <= 1.0", dy + sY <= 1.0001f)
            }
        }
    }

    @Test
    fun testZoomedScaleCropBounds() {
        val screenW = 1920
        val screenH = 1080

        // 16:9 image zoomed 2x
        val cropZoomed = DynamicBackgroundView.computeCenterCrop(
            imgW = 1920,
            imgH = 1080,
            vw = screenW,
            vh = screenH,
            isGradient = false,
            scale = 2f,
            offsetX = 500f,
            offsetY = -300f,
            unscaledViewWidth = screenW.toFloat(),
            unscaledViewHeight = screenH.toFloat()
        )

        val sX = cropZoomed[0]
        val sY = cropZoomed[1]
        val dx = cropZoomed[2]
        val dy = cropZoomed[3]

        assertEquals(0.5f, sX, 0.001f)
        assertEquals(0.5f, sY, 0.001f)
        assertTrue("dx must be in [0, 0.5]", dx in 0f..0.5001f)
        assertTrue("dy must be in [0, 0.5]", dy in 0f..0.5001f)
        assertTrue("dx + sX <= 1.0", dx + sX <= 1.0001f)
        assertTrue("dy + sY <= 1.0", dy + sY <= 1.0001f)
    }
}
