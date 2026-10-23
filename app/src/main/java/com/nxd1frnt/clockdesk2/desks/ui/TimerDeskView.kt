package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.timer.TimerManager
import com.nxd1frnt.clockdesk2.timer.TimerState
import com.nxd1frnt.clockdesk2.utils.UiMonetPalette
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class TimerDeskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val txtTitle: TextView
    private val txtDigits: TextView
    private val txtStatus: TextView
    private val cardDisplay: MaterialCardView
    private val cardControls: MaterialCardView
    private val progressCircular: CircularProgressIndicator

    private val btnCustom: MaterialButton
    private val btnSilent: MaterialButton
    private val btnReset: MaterialButton
    private val btnPrimary: MaterialButton
    private val btnAdd1m: MaterialButton
    private val btnAdd5m: MaterialButton

    private val chip1m: MaterialButton
    private val chip3m: MaterialButton
    private val chip5m: MaterialButton
    private val chip10m: MaterialButton
    private val chip15m: MaterialButton
    private val chip30m: MaterialButton
    private val chip1h: MaterialButton

    private val timerListener = object : TimerManager.TimerListener {
        override fun onTimerTick(remainingMillis: Long, totalDurationMillis: Long) {
            post { updateTick(remainingMillis, totalDurationMillis) }
        }

        override fun onTimerStateChanged(state: TimerState) {
            post { updateState(state) }
        }

        override fun onTimerSilentChanged(isSilent: Boolean) {
            post { updateSilent(isSilent) }
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_timer_layout, this, true)

        txtTitle = findViewById(R.id.txt_timer_title)
        txtDigits = findViewById(R.id.txt_timer_digits)
        txtStatus = findViewById(R.id.txt_timer_status)
        cardDisplay = findViewById(R.id.card_timer_display)
        cardControls = findViewById(R.id.card_timer_controls)
        progressCircular = findViewById(R.id.progress_timer_circular)

        btnCustom = findViewById(R.id.btn_timer_custom)
        btnSilent = findViewById(R.id.btn_timer_silent)
        btnReset = findViewById(R.id.btn_timer_reset)
        btnPrimary = findViewById(R.id.btn_timer_primary)
        btnAdd1m = findViewById(R.id.btn_timer_add_1m)
        btnAdd5m = findViewById(R.id.btn_timer_add_5m)

        btnSilent.setOnClickListener {
            TimerManager.setSilent(!TimerManager.isSilent)
        }

        chip1m = findViewById(R.id.chip_preset_1m)
        chip3m = findViewById(R.id.chip_preset_3m)
        chip5m = findViewById(R.id.chip_preset_5m)
        chip10m = findViewById(R.id.chip_preset_10m)
        chip15m = findViewById(R.id.chip_preset_15m)
        chip30m = findViewById(R.id.chip_preset_30m)
        chip1h = findViewById(R.id.chip_preset_1h)

        chip1m.setOnClickListener { TimerManager.setDuration(60_000L) }
        chip3m.setOnClickListener { TimerManager.setDuration(180_000L) }
        chip5m.setOnClickListener { TimerManager.setDuration(300_000L) }
        chip10m.setOnClickListener { TimerManager.setDuration(600_000L) }
        chip15m.setOnClickListener { TimerManager.setDuration(900_000L) }
        chip30m.setOnClickListener { TimerManager.setDuration(1800_000L) }
        chip1h.setOnClickListener { TimerManager.setDuration(3600_000L) }

        btnCustom.setOnClickListener { showCustomTimeDialog() }

        btnReset.setOnClickListener {
            TimerManager.reset()
        }

        btnPrimary.setOnClickListener {
            when (TimerManager.state) {
                TimerState.IDLE -> TimerManager.start()
                TimerState.RUNNING -> TimerManager.pause()
                TimerState.PAUSED -> TimerManager.resume()
                TimerState.FINISHED -> {
                    TimerManager.stopAlarm()
                    TimerManager.reset()
                }
            }
        }

        btnAdd1m.setOnClickListener { TimerManager.addTime(60_000L) }
        btnAdd5m.setOnClickListener { TimerManager.addTime(300_000L) }
    }

    private fun updateSilent(isSilent: Boolean) {
        val palette = UiMonetThemeManager.getPalette(context)
        if (isSilent) {
            btnSilent.setText(R.string.timer_silent)
            btnSilent.setIconResource(R.drawable.ic_notifications_off)
            btnSilent.backgroundTintList = ColorStateList.valueOf(palette.secondaryContainer)
            btnSilent.setTextColor(palette.onSecondaryContainer)
            btnSilent.iconTint = ColorStateList.valueOf(palette.onSecondaryContainer)
            btnSilent.strokeColor = ColorStateList.valueOf(palette.secondary)
        } else {
            btnSilent.setText(R.string.timer_sound)
            btnSilent.setIconResource(R.drawable.ic_notifications)
            btnSilent.backgroundTintList = ColorStateList.valueOf(palette.surfaceContainer)
            btnSilent.setTextColor(palette.onSurface)
            btnSilent.iconTint = ColorStateList.valueOf(palette.primary)
            btnSilent.strokeColor = ColorStateList.valueOf(palette.outlineVariant)
        }
    }

    fun refreshMonetTheme(palette: UiMonetPalette = UiMonetThemeManager.getPalette(context)) {
        UiMonetThemeManager.applyLiveToViewHierarchy(this, palette)
        txtTitle.setTextColor(palette.onSurface)
        txtDigits.setTextColor(palette.onSurface)
        txtStatus.setTextColor(palette.onSurfaceVariant)
        cardDisplay.setCardBackgroundColor(palette.surfaceContainer)
        cardDisplay.strokeColor = palette.outlineVariant
        cardControls.setCardBackgroundColor(palette.surfaceContainer)
        cardControls.strokeColor = palette.outlineVariant
        progressCircular.trackColor = palette.surfaceContainerHighest
        updateSilent(TimerManager.isSilent)
        updateState(TimerManager.state)
        DeskForegroundStyle.apply(this)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        TimerManager.addListener(timerListener)
        refreshMonetTheme()
        updateTick(TimerManager.remainingMillis, TimerManager.totalDurationMillis)
        updateSilent(TimerManager.isSilent)
        updateState(TimerManager.state)
    }

    override fun onDetachedFromWindow() {
        TimerManager.removeListener(timerListener)
        super.onDetachedFromWindow()
    }

    fun dispose() {
        TimerManager.removeListener(timerListener)
    }

    private fun updateTick(remainingMillis: Long, totalDurationMillis: Long) {
        txtDigits.text = TimerManager.formatTime(remainingMillis)
        val progress = if (totalDurationMillis > 0L) {
            ((remainingMillis.toDouble() / totalDurationMillis.toDouble()) * 1000).toInt().coerceIn(0, 1000)
        } else 1000
        progressCircular.progress = progress
    }

    private fun updateState(state: TimerState) {
        val palette = UiMonetThemeManager.getPalette(context)
        when (state) {
            TimerState.IDLE -> {
                txtStatus.text = TimerManager.formatTime(TimerManager.totalDurationMillis)
                btnPrimary.setText(R.string.timer_start)
                btnPrimary.setIconResource(R.drawable.ic_play_arrow)
                btnPrimary.backgroundTintList = ColorStateList.valueOf(palette.primary)
                btnPrimary.setTextColor(palette.onPrimary)
                btnPrimary.iconTint = ColorStateList.valueOf(palette.onPrimary)
                progressCircular.setIndicatorColor(palette.primary)
            }
            TimerState.RUNNING -> {
                txtStatus.text = TimerManager.formatTime(TimerManager.totalDurationMillis)
                btnPrimary.setText(R.string.timer_pause)
                btnPrimary.setIconResource(R.drawable.ic_pause)
                btnPrimary.backgroundTintList = ColorStateList.valueOf(palette.secondaryContainer)
                btnPrimary.setTextColor(palette.onSecondaryContainer)
                btnPrimary.iconTint = ColorStateList.valueOf(palette.onSecondaryContainer)
                progressCircular.setIndicatorColor(palette.primary)
            }
            TimerState.PAUSED -> {
                txtStatus.text = "${TimerManager.formatTime(TimerManager.totalDurationMillis)} (${context.getString(R.string.timer_pause)})"
                btnPrimary.setText(R.string.timer_resume)
                btnPrimary.setIconResource(R.drawable.ic_play_arrow)
                btnPrimary.backgroundTintList = ColorStateList.valueOf(palette.primary)
                btnPrimary.setTextColor(palette.onPrimary)
                btnPrimary.iconTint = ColorStateList.valueOf(palette.onPrimary)
                progressCircular.setIndicatorColor(palette.primary)
            }
            TimerState.FINISHED -> {
                txtDigits.text = "00:00"
                txtStatus.text = context.getString(R.string.timer_time_up)
                btnPrimary.setText(R.string.timer_stop_alarm)
                btnPrimary.setIconResource(R.drawable.ic_alarm)
                btnPrimary.backgroundTintList = ColorStateList.valueOf(palette.error)
                btnPrimary.setTextColor(palette.onError)
                btnPrimary.iconTint = ColorStateList.valueOf(palette.onError)
                progressCircular.setIndicatorColor(palette.error)
            }
        }
    }

    private fun showCustomTimeDialog() {
        val activity = context as? Activity ?: return
        val palette = UiMonetThemeManager.getPalette(activity)

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        val curTotal = TimerManager.totalDurationMillis / 1000L
        val initH = (curTotal / 3600).toInt()
        val initM = ((curTotal % 3600) / 60).toInt()
        val initS = (curTotal % 60).toInt()

        fun createPicker(max: Int, cur: Int, label: String): NumberPicker {
            return NumberPicker(activity).apply {
                minValue = 0
                maxValue = max
                value = cur
                wrapSelectorWheel = true
            }
        }

        val npH = createPicker(23, initH, activity.getString(R.string.timer_hours))
        val npM = createPicker(59, initM, activity.getString(R.string.timer_minutes))
        val npS = createPicker(59, initS, activity.getString(R.string.timer_seconds))

        fun createColumn(picker: NumberPicker, label: String): LinearLayout {
            return LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val padH = (12 * resources.displayMetrics.density).toInt()
                setPadding(padH, 0, padH, 0)
                addView(picker)
                addView(TextView(activity).apply {
                    text = label
                    setTextColor(palette.onSurfaceVariant)
                    textSize = 12f
                    gravity = Gravity.CENTER
                })
            }
        }

        layout.addView(createColumn(npH, activity.getString(R.string.timer_hours)))
        layout.addView(createColumn(npM, activity.getString(R.string.timer_minutes)))
        layout.addView(createColumn(npS, activity.getString(R.string.timer_seconds)))

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.timer_custom_title)
            .setView(layout)
            .setPositiveButton(R.string.apply) { _, _ ->
                val totalSec = npH.value * 3600L + npM.value * 60L + npS.value
                val totalMs = totalSec * 1000L
                if (totalMs > 0) {
                    TimerManager.setDuration(totalMs)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()
        UiMonetThemeManager.styleAlertDialog(dialog, palette)
    }
}
