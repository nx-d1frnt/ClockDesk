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
import com.nxd1frnt.clockdesk2.smartchips.setTextWithFade

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
        private const val MIN_STAGE_DISPLAY_MS = 500L
        private const val COMPLETED_DISPLAY_MS = 850L
        private const val FAILED_DISPLAY_MS = 2000L

        var currentStage: Stage = Stage.IDLE
            private set
        var customMessage: String? = null
            private set
        var onGlobalStateChanged: (() -> Unit)? = null

        private val mainHandler = Handler(Looper.getMainLooper())
        private val stageQueue = ArrayDeque<StageItem>()
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
         * Post a progress stage to be displayed.
         * Guarantees each stage has a minimum display time to ensure user readability
         * without any skipped or prematurely aborted animations.
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

            if (stage == Stage.FAILED) {
                clearPending()
                applyStage(item)
                advanceRunnable = Runnable { reset() }
                mainHandler.postDelayed(advanceRunnable!!, FAILED_DISPLAY_MS)
                return
            }

            if (currentStage == Stage.IDLE) {
                applyStage(item)
                return
            }

            if (currentStage == stage && customMessage == messageOverride && stageQueue.isEmpty()) {
                return
            }

            val elapsed = SystemClock.uptimeMillis() - stageStartTime
            if (elapsed >= MIN_STAGE_DISPLAY_MS && stageQueue.isEmpty()) {
                applyStage(item)
            } else {
                val lastInQueue = stageQueue.lastOrNull()
                if (lastInQueue?.stage != stage || lastInQueue.customMessage != messageOverride) {
                    stageQueue.add(item)
                    scheduleAdvanceIfNeeded()
                }
            }
        }

        private fun scheduleAdvanceIfNeeded() {
            if (advanceRunnable != null) return

            val elapsed = SystemClock.uptimeMillis() - stageStartTime
            val remaining = (MIN_STAGE_DISPLAY_MS - elapsed).coerceAtLeast(0L)

            advanceRunnable = Runnable {
                advanceRunnable = null
                if (stageQueue.isNotEmpty()) {
                    val next = stageQueue.removeFirst()
                    applyStage(next)
                    if (next.stage == Stage.COMPLETED) {
                        advanceRunnable = Runnable { reset() }
                        mainHandler.postDelayed(advanceRunnable!!, COMPLETED_DISPLAY_MS)
                    } else if (stageQueue.isNotEmpty()) {
                        scheduleAdvanceIfNeeded()
                    }
                } else if (currentStage == Stage.COMPLETED) {
                    reset()
                }
            }
            mainHandler.postDelayed(advanceRunnable!!, remaining)
        }

        private fun applyStage(item: StageItem) {
            currentStage = item.stage
            customMessage = item.customMessage
            stageStartTime = SystemClock.uptimeMillis()
            onGlobalStateChanged?.invoke()

            if (item.stage == Stage.COMPLETED && stageQueue.isEmpty() && advanceRunnable == null) {
                advanceRunnable = Runnable { reset() }
                mainHandler.postDelayed(advanceRunnable!!, COMPLETED_DISPLAY_MS)
            }
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
            stageQueue.clear()
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
            val currentTag = iconView.tag as? Int
            if (currentTag == null) {
                iconView.setImageResource(currentStage.iconRes)
                iconView.tag = currentStage.iconRes
            } else if (currentTag != currentStage.iconRes) {
                iconView.tag = currentStage.iconRes
                iconView.animate()
                    .alpha(0f)
                    .scaleX(0.7f)
                    .scaleY(0.7f)
                    .setDuration(120)
                    .withEndAction {
                        iconView.setImageResource(currentStage.iconRes)
                        iconView.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .start()
                    }
                    .start()
            }
        }

        val text = customMessage ?: if (currentStage.defaultMessageRes != 0) {
            context.getString(currentStage.defaultMessageRes)
        } else ""

        if (textView != null) {
            textView.setTextWithFade(text)
        }

        return true
    }
}