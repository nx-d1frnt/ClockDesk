package com.nxd1frnt.clockdesk2.desks.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.nxd1frnt.clockdesk2.R

class DeskPageIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val handler = Handler(Looper.getMainLooper())
    private var pageCount = 0
    private var currentPage = 0
    var onPageClicked: ((Int) -> Unit)? = null

    private val fadeOutRunnable = Runnable {
        animate()
            .alpha(0f)
            .setDuration(400)
            .withEndAction { visibility = View.INVISIBLE }
            .start()
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        alpha = 0f
        visibility = View.INVISIBLE
    }

    fun setPageCount(count: Int, selectedPage: Int = 0) {
        pageCount = count
        currentPage = selectedPage.coerceIn(0, (count - 1).coerceAtLeast(0))
        rebuildDots()
        showTemporarily()
    }

    fun setCurrentPage(page: Int) {
        if (page != currentPage) {
            currentPage = page
            updateDots()
            showTemporarily()
        }
    }

    fun showTemporarily(durationMs: Long = 2500L) {
        handler.removeCallbacks(fadeOutRunnable)
        if (pageCount <= 1) {
            visibility = View.INVISIBLE
            alpha = 0f
            return
        }
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(200).start()
        handler.postDelayed(fadeOutRunnable, durationMs)
    }

    private fun rebuildDots() {
        removeAllViews()
        if (pageCount <= 1) return

        val density = resources.displayMetrics.density
        val marginPx = (4 * density).toInt()

        for (i in 0 until pageCount) {
            val dot = View(context).apply {
                val isSelected = (i == currentPage)
                val widthPx = if (isSelected) (18 * density).toInt() else (7 * density).toInt()
                val heightPx = (7 * density).toInt()
                layoutParams = LayoutParams(widthPx, heightPx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }
                background = ContextCompat.getDrawable(
                    context,
                    if (isSelected) R.drawable.onboarding_dot_active else R.drawable.onboarding_dot_inactive
                )
                setOnClickListener {
                    onPageClicked?.invoke(i)
                }
            }
            addView(dot)
        }
    }

    private fun updateDots() {
        val density = resources.displayMetrics.density
        for (i in 0 until childCount) {
            val dot = getChildAt(i)
            val isSelected = (i == currentPage)
            val widthPx = if (isSelected) (18 * density).toInt() else (7 * density).toInt()
            val heightPx = (7 * density).toInt()
            dot.layoutParams = (dot.layoutParams as LayoutParams).apply {
                width = widthPx
                height = heightPx
            }
            dot.background = ContextCompat.getDrawable(
                context,
                if (isSelected) R.drawable.onboarding_dot_active else R.drawable.onboarding_dot_inactive
            )
        }
    }
}
