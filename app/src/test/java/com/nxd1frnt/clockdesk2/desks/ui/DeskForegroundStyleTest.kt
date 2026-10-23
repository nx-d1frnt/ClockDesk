package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.nxd1frnt.clockdesk2.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeskForegroundStyleTest {
    @Test fun choosesContrastFromWallpaperAndDimming() {
        assertEquals(Color.WHITE, DeskForegroundStyle.contrastingColor(intArrayOf(Color.BLACK), ColorMatrix()))
        assertEquals(Color.BLACK, DeskForegroundStyle.contrastingColor(intArrayOf(Color.WHITE), ColorMatrix()))
        val dim = ColorMatrix().apply { setScale(0.15f, 0.15f, 0.15f, 1f) }
        assertEquals(Color.WHITE, DeskForegroundStyle.contrastingColor(intArrayOf(Color.WHITE), dim))
    }

    @Test fun samplesEntireImageIncludingSmallBitmaps() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(0, 0, Color.BLACK)
        val samples = DeskForegroundStyle.sample(bitmap)
        assertEquals(16, samples.count { it == Color.BLACK })
        assertEquals(48, samples.count { it == Color.WHITE })
        bitmap.recycle()
    }

    @Test fun wallpaperTextChangesButCardsBadgesAndFilledButtonsKeepTheirColors() {
        val context = RuntimeEnvironment.getApplication()
        context.setTheme(R.style.Theme_ClockDesk)
        DeskForegroundStyle.update(context, Color.WHITE)
        val root = FrameLayout(context)
        val title = TextView(context).apply { setTextColor(Color.BLACK) }
        val cardText = TextView(context).apply { setTextColor(Color.RED) }
        val card = MaterialCardView(context).apply { addView(cardText) }
        val badge = TextView(context).apply {
            setBackgroundColor(Color.BLUE)
            setTextColor(Color.YELLOW)
        }
        val button = MaterialButton(context).apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.BLUE)
            setTextColor(Color.GREEN)
        }
        val outlined = MaterialButton(context).apply {
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setTextColor(Color.BLACK)
            strokeWidth = 1
        }
        listOf(title, card, badge, button, outlined).forEach { root.addView(it) }
        DeskForegroundStyle.apply(root)
        assertEquals(Color.WHITE, title.currentTextColor)
        assertEquals(Color.WHITE, outlined.currentTextColor)
        assertEquals(Color.RED, cardText.currentTextColor)
        assertEquals(Color.YELLOW, badge.currentTextColor)
        assertEquals(Color.GREEN, button.currentTextColor)
        DeskForegroundStyle.update(context, Color.BLACK)
        DeskForegroundStyle.apply(root)
        assertEquals(Color.BLACK, title.currentTextColor)
        val newLabel = TextView(context)
        DeskForegroundStyle.applyText(newLabel)
        assertEquals(Color.BLACK, newLabel.currentTextColor)
    }
}
