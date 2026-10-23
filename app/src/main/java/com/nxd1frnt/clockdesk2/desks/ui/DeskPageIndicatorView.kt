package com.nxd1frnt.clockdesk2.desks.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.ColorUtils
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import kotlin.math.abs
import kotlin.math.roundToInt

class DeskPageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private var pageCount = 0
    private var currentPage = 0
    private var scrollPosition = 0f
    private val density = resources.displayMetrics.density
    private val dotHeight = (7 * density).roundToInt()
    private val extraWidth = (18 * density).roundToInt() - dotHeight
    private val dotMargin = (4 * density).toInt()
    private var palette = UiMonetThemeManager.getPalette(context)
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
        alpha = 0f
        visibility = View.INVISIBLE
    }

    fun refreshMonetTheme() {
        palette = UiMonetThemeManager.getPalette(context)
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

        val initialColor = ColorUtils.setAlphaComponent(palette.onSurface, 0x60)

        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                layoutParams = LayoutParams(dotHeight + extraWidth, dotHeight)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dotHeight / 2f
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentWidth = if (childCount > 0) childCount * (dotHeight + 2 * dotMargin) + extraWidth else 0
        setMeasuredDimension(
            resolveSize(contentWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize((if (childCount > 0) dotHeight else 0) + paddingTop + paddingBottom, heightMeasureSpec)
        )
        for (i in 0 until childCount) {
            getChildAt(i).measure(
                MeasureSpec.makeMeasureSpec(dotHeight + extraWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dotHeight, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        updateDots()
    }

    private fun updateDots() {
        val activeColor = palette.primary
        val inactiveColor = ColorUtils.setAlphaComponent(palette.onSurface, 0x60)
        val inactiveWidth = dotHeight
        val fromPage = scrollPosition.toInt()
        val nextExtraWidth = (extraWidth * (scrollPosition - fromPage)).roundToInt()
        val contentWidth = childCount * (dotHeight + 2 * dotMargin) + extraWidth
        var start = paddingLeft + (width - paddingLeft - paddingRight - contentWidth) / 2
        val top = paddingTop + (height - paddingTop - paddingBottom - dotHeight) / 2
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
            // The total width is constant. Move the child bounds directly so a swipe
            // never requests another measure/layout pass through the activity root.
            val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) {
                width - start - dotMargin - widthPx
            } else start + dotMargin
            dot.layout(left, top, left + widthPx, top + dotHeight)
            start += widthPx + 2 * dotMargin
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
