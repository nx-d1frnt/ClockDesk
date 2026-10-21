package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip

class BackgroundProgressPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "system_bg_progress"

    enum class Stage(val iconRes: Int, val defaultMessageRes: Int) {
        DOWNLOADING(R.drawable.image_refresh, R.string.stage_downloading),
        LOADING(R.drawable.baseline_add_photo_alternate_24, R.string.stage_loading),
        GENERATING_GRADIENT(R.drawable.ic_palette_swatch, R.string.stage_gradient),
        BLURRING(R.drawable.ic_blur, R.string.stage_blurring),
        EXTRACTING_COLORS(R.drawable.ic_palette_swatch, R.string.stage_colors),
        APPLYING_THEME(R.drawable.ic_auto_awesome, R.string.stage_theme),
        RENDERING(R.drawable.image_edit_outline, R.string.stage_rendering),
        COMPLETED(R.drawable.ic_check, R.string.stage_completed),
        FAILED(R.drawable.ic_close, R.string.stage_failed),
        IDLE(0, 0)
    }

    private data class StageItem(
        val stage: Stage,
        val customMessage: String? = null
    )

    companion object {
        private const val SHOW_DELAY_MS = 300L
        private const val UPDATE_INTERVAL_MS = 300L
        private const val COMPLETED_DISPLAY_MS = 500L
        private const val FAILED_DISPLAY_MS = 2000L

        var currentStage: Stage = Stage.IDLE
            private set
        var customMessage: String? = null
            private set
        var onGlobalStateChanged: (() -> Unit)? = null

        private val mainHandler = Handler(Looper.getMainLooper())
        private var pendingStage: StageItem? = null
        private var stageStartTime = 0L
        private var advanceRunnable: Runnable? = null

        /**
         * Starts a fresh session, clearing any lingering stages from previous operations.
         */
        fun startSession() {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post { startSession() }
                return
            }
            clearPending()
        }

        /**
         * Coalesce bursts into the latest stage instead of replaying completed work.
         * Fast operations finish before the chip appears; terminal states bypass throttling.
         */
        fun postStage(stage: Stage, messageOverride: String? = null) {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post { postStage(stage, messageOverride) }
                return
            }

            if (stage == Stage.IDLE) {
                reset()
                return
            }

            val item = StageItem(stage, messageOverride)

            if (stage == Stage.FAILED || stage == Stage.COMPLETED) {
                if (currentStage == stage && customMessage == messageOverride && advanceRunnable != null) return
                val wasVisible = currentStage != Stage.IDLE
                clearPending()
                if (stage == Stage.COMPLETED && !wasVisible) return
                applyStage(item)
                scheduleReset(if (stage == Stage.FAILED) FAILED_DISPLAY_MS else COMPLETED_DISPLAY_MS)
                return
            }

            // A new operation must not inherit a previous result's hide callback.
            if (currentStage == Stage.COMPLETED || currentStage == Stage.FAILED) {
                clearPending()
                applyStage(item)
                return
            }

            if (currentStage == stage && customMessage == messageOverride) {
                clearPending()
                return
            }

            pendingStage = item
            scheduleAdvanceIfNeeded()
        }

        private fun scheduleAdvanceIfNeeded() {
            if (advanceRunnable != null) return

            val remaining = if (currentStage == Stage.IDLE) SHOW_DELAY_MS else {
                (UPDATE_INTERVAL_MS - (SystemClock.uptimeMillis() - stageStartTime)).coerceAtLeast(0L)
            }

            advanceRunnable = Runnable {
                advanceRunnable = null
                val next = pendingStage
                pendingStage = null
                if (next != null) applyStage(next)
            }
            mainHandler.postDelayed(advanceRunnable!!, remaining)
        }

        private fun applyStage(item: StageItem) {
            currentStage = item.stage
            customMessage = item.customMessage
            stageStartTime = SystemClock.uptimeMillis()
            onGlobalStateChanged?.invoke()
        }

        private fun scheduleReset(delayMs: Long) {
            advanceRunnable = Runnable { reset() }
            mainHandler.postDelayed(advanceRunnable!!, delayMs)
        }

        fun reset() {
            if (Looper.myLooper() != Looper.getMainLooper()) {
                mainHandler.post { reset() }
                return
            }
            clearPending()
            if (currentStage != Stage.IDLE) {
                currentStage = Stage.IDLE
                customMessage = null
                onGlobalStateChanged?.invoke()
            }
        }

        private fun clearPending() {
            advanceRunnable?.let { mainHandler.removeCallbacks(it) }
            advanceRunnable = null
            pendingStage = null
        }
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        onGlobalStateChanged = listener
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
        view.isClickable = false
        view.isFocusable = false
        return view
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        if (currentStage == Stage.IDLE) return false

        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        if (iconView != null && currentStage.iconRes != 0) {
            if (iconView.tag != currentStage.iconRes) {
                iconView.setImageResource(currentStage.iconRes)
                iconView.tag = currentStage.iconRes
            }
        }

        val text = customMessage ?: if (currentStage.defaultMessageRes != 0) {
            context.getString(currentStage.defaultMessageRes)
        } else ""

        if (textView != null) {
            // Keep updates synchronous and local: nested bounds transitions conflict
            // with the manager's stack appearance and layout animations.
            if (textView.text.toString() != text) textView.text = text
            textView.isSelected = true
        }

        return true
    }
}
