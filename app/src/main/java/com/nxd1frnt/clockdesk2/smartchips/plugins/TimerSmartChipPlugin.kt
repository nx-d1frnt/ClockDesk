package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.desks.DeskType
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import com.nxd1frnt.clockdesk2.timer.TimerManager
import com.nxd1frnt.clockdesk2.timer.TimerState
import com.nxd1frnt.clockdesk2.ui.MainActivity

class TimerSmartChipPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_timer_chip"

    private var stateChangeListener: (() -> Unit)? = null
    private var lastReportedSecond: Long = -1L

    private val timerListener = object : TimerManager.TimerListener {
        override fun onTimerTick(remainingMillis: Long, totalDurationMillis: Long) {
            val currentSecond = (remainingMillis + 999) / 1000
            if (currentSecond != lastReportedSecond) {
                lastReportedSecond = currentSecond
                stateChangeListener?.invoke()
            }
        }

        override fun onTimerStateChanged(state: TimerState) {
            lastReportedSecond = -1L
            stateChangeListener?.invoke()
        }
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        lastReportedSecond = -1L
        TimerManager.addListener(timerListener)
    }

    override fun stopListening() {
        TimerManager.removeListener(timerListener)
    }

    override fun createView(context: Context): View {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
        view.setOnClickListener {
            if (TimerManager.state == TimerState.FINISHED) {
                TimerManager.stopAlarm()
            }
            MainActivity.openDeskType(DeskType.TIMER)
        }
        return view
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        val state = TimerManager.state
        if (state == TimerState.IDLE) {
            return false
        }

        if (iconView != null && iconView.tag != R.drawable.ic_timer) {
            iconView.setImageResource(R.drawable.ic_timer)
            iconView.tag = R.drawable.ic_timer
        }

        val text = when (state) {
            TimerState.FINISHED -> context.getString(R.string.timer_time_up)
            TimerState.PAUSED -> "${TimerManager.formatTime(TimerManager.remainingMillis)} ⏸"
            TimerState.RUNNING -> TimerManager.formatTime(TimerManager.remainingMillis)
            TimerState.IDLE -> ""
        }

        if (textView != null) {
            textView.animate().cancel()
            textView.alpha = 1f
            if (textView.text.toString() != text) {
                textView.text = text
            }
            if (!textView.isSelected) {
                textView.isSelected = true
            }
        }

        return true
    }
}
