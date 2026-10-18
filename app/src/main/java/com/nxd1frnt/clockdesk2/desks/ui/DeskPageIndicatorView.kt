package com.nxd1frnt.clockdesk2.desks.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import kotlin.math.abs
import kotlin.math.roundToInt

class DeskPageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private var pageCount = 0
    private var currentPage = 0
    private var scrollPosition = 0f
    private var isFadingOut = false
    var onPageClicked: ((Int) -> Unit)? = null

    private val fadeOutRunnable = Runnable {
        isFadingOut = true
        animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction {
                visibility = View.INVISIBLE
                isFadingOut = false
            }
            .start()
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        alpha = 0f
        visibility = View.INVISIBLE
    }

    fun refreshMonetTheme() {
        updateDots()
    }

    fun setPageCount(count: Int, selectedPage: Int = 0) {
        pageCount = count.coerceAtLeast(0)
        currentPage = selectedPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scrollPosition = currentPage.toFloat()
        rebuildDots()
        showTemporarily()
    }

    fun setCurrentPage(page: Int) {
        val selectedPage = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        if (selectedPage != currentPage) {
            currentPage = selectedPage
            showTemporarily()
        }
        setScrollPosition(selectedPage, 0f)
    }

    fun setScrollPosition(position: Int, offset: Float) {
        val progress = (position + offset).coerceIn(0f, (pageCount - 1).coerceAtLeast(0).toFloat())
        if (progress == scrollPosition) return
        scrollPosition = progress
        updateDots()
    }

    fun showTemporarily(durationMs: Long = 2500L) {
        handler.removeCallbacks(fadeOutRunnable)
        if (pageCount <= 1) {
            animate().cancel()
            isFadingOut = false
            visibility = View.INVISIBLE
            alpha = 0f
            return
        }
        if (visibility != View.VISIBLE || isFadingOut) {
            animate().cancel()
            isFadingOut = false
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(200).withEndAction(null).start()
        }
        handler.postDelayed(fadeOutRunnable, durationMs)
    }

    private fun rebuildDots() {
        removeAllViews()
        if (pageCount <= 1) return

        val palette = UiMonetThemeManager.getPalette(context)
        val initialColor = ColorUtils.setAlphaComponent(palette.onSurface, 0x60)
        val density = resources.displayMetrics.density
        val marginPx = (4 * density).toInt()

        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                val heightPx = (7 * density).roundToInt()
                layoutParams = LayoutParams(heightPx, heightPx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = heightPx / 2f
                    setColor(initialColor)
                }
                setOnClickListener {
                    onPageClicked?.invoke(i)
                }
            }
            addView(dot)
        }
        updateDots()
    }

    private fun updateDots() {
        val palette = UiMonetThemeManager.getPalette(context)
        val activeColor = palette.primary
        val inactiveColor = ColorUtils.setAlphaComponent(palette.onSurface, 0x60)
        val density = resources.displayMetrics.density
        val inactiveWidth = (7 * density).roundToInt()
        val extraWidth = (18 * density).roundToInt() - inactiveWidth
        val fromPage = scrollPosition.toInt()
        val nextExtraWidth = (extraWidth * (scrollPosition - fromPage)).roundToInt()
        for (i in 0 until childCount) {
            val dot = getChildAt(i)
            val selection = (1f - abs(i - scrollPosition)).coerceIn(0f, 1f)
            // Share the extra width so the whole indicator stays centered even
            // when pixel rounding would otherwise make both dots wider.
            val widthPx = inactiveWidth + when (i) {
                fromPage -> extraWidth - nextExtraWidth
                fromPage + 1 -> nextExtraWidth
                else -> 0
            }
            if (dot.layoutParams.width != widthPx) {
                dot.layoutParams = dot.layoutParams.apply { width = widthPx }
            }
            val color = ColorUtils.blendARGB(inactiveColor, activeColor, selection)
            (dot.background as? GradientDrawable)?.setColor(color)
            dot.alpha = 1f
        }
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(fadeOutRunnable)
        animate().cancel()
        isFadingOut = false
        visibility = View.INVISIBLE
        alpha = 0f
        super.onDetachedFromWindow()
    }
}
