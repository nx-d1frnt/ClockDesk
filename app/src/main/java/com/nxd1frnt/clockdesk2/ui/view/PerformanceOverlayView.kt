package com.nxd1frnt.clockdesk2.ui.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.nxd1frnt.clockdesk2.utils.PerformanceTracker

class PerformanceOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val fpsValText: TextView
    private val renderValText: TextView
    private val gpuValText: TextView
    private val cpuValText: TextView

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        
        // Premium glassmorphism layout padding and margins
        val density = resources.displayMetrics.density
        val paddingPx = (12 * density).toInt()
        setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

        // Glassmorphic background: Semi-transparent dark slate with a subtle white border
        val backgroundDrawable = GradientDrawable().apply {
            setColor(Color.parseColor("#E612151D")) // 90% opacity dark slate
            cornerRadius = 12 * density
            setStroke((1 * density).toInt(), Color.parseColor("#26FFFFFF")) // 15% opacity white border
        }
        background = backgroundDrawable

        // Add layout elements
        fpsValText = createMetricRow("FPS", "0")
        renderValText = createMetricRow("Render", "0.0 ms")
        gpuValText = createMetricRow("Est. GPU", "0%")
        cpuValText = createMetricRow("App CPU", "0%")
    }

    private fun createMetricRow(label: String, initialVal: String): TextView {
        val density = resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = (3 * density).toInt()
            layoutParams = lp
        }

        val labelText = TextView(context).apply {
            text = "$label: "
            setTextColor(Color.parseColor("#99FFFFFF")) // Dimmed label text
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        }

        val valueText = TextView(context).apply {
            text = initialVal
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.create("sans-serif-bold", android.graphics.Typeface.NORMAL)
        }

        row.addView(labelText)
        row.addView(valueText)
        addView(row)
        
        return valueText
    }

    /**
     * Updates the overlay with new metrics.
     */
    fun updateMetrics(metrics: PerformanceTracker.Metrics) {
        post {
            // Update FPS
            fpsValText.text = "${metrics.fps}"
            fpsValText.setTextColor(getFpsColor(metrics.fps))

            // Update Render Time
            renderValText.text = String.format("%.2f ms", metrics.renderTimeMs)
            renderValText.setTextColor(getRenderTimeColor(metrics.renderTimeMs))

            // Update GPU Load
            gpuValText.text = String.format("%.0f%%", metrics.gpuLoadPercent)
            gpuValText.setTextColor(getPercentColor(metrics.gpuLoadPercent))

            // Update CPU Load
            cpuValText.text = String.format("%.1f%%", metrics.cpuLoadPercent)
            cpuValText.setTextColor(getPercentColor(metrics.cpuLoadPercent))
        }
    }

    private fun getFpsColor(fps: Int): Int {
        return when {
            fps >= 55 -> Color.parseColor("#FF4CAF50") // Green
            fps >= 30 -> Color.parseColor("#FFFFC107") // Yellow
            else -> Color.parseColor("#FFF44336")      // Red
        }
    }

    private fun getRenderTimeColor(ms: Float): Int {
        return when {
            ms <= 6f -> Color.parseColor("#FF8BC34A")  // Light Green (excellent)
            ms <= 12f -> Color.parseColor("#FF4CAF50") // Green (great)
            ms <= 20f -> Color.parseColor("#FFFFC107") // Yellow (heavy load)
            else -> Color.parseColor("#FFF44336")      // Red (extreme bottleneck)
        }
    }

    private fun getPercentColor(pct: Float): Int {
        return when {
            pct <= 30f -> Color.parseColor("#FF4CAF50") // Green
            pct <= 75f -> Color.parseColor("#FFFFC107") // Yellow
            else -> Color.parseColor("#FFF44336")      // Red
        }
    }
}
