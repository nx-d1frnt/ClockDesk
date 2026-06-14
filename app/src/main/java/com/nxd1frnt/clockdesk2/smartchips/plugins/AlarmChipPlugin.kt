package com.nxd1frnt.clockdesk2.smartchips.plugins

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ISmartChip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmChipPlugin(private val context: Context) : ISmartChip {

    override val preferenceKey: String = "show_alarm_chip"

    private var stateChangeListener: (() -> Unit)? = null
    private var isListening = false

    private val alarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) {
                stateChangeListener?.invoke()
            }
        }
    }

    override fun setOnStateChangeListener(listener: () -> Unit) {
        this.stateChangeListener = listener
    }

    override fun startListening() {
        if (isListening) return
        val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        context.registerReceiver(alarmReceiver, filter)
        isListening = true
    }

    override fun stopListening() {
        if (!isListening) return
        try {
            context.unregisterReceiver(alarmReceiver)
            isListening = false
        } catch (e: Exception) {}
    }

    override fun createView(context: Context): View {
        return LayoutInflater.from(context)
            .inflate(R.layout.smart_chip_layout, null, false)
    }

    override fun update(view: View, sharedPreferences: SharedPreferences): Boolean {
        val iconView = view.findViewById<ImageView>(R.id.chip_icon)
        val textView = view.findViewById<TextView>(R.id.chip_text)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nextAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            alarmManager?.nextAlarmClock
        } else null

        if (nextAlarm != null) {
            val triggerTime = nextAlarm.triggerTime
            val date = Date(triggerTime)

            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            val pattern = if (is24) "EEE HH:mm" else "EEE h:mm a"
            val format = SimpleDateFormat(pattern, Locale.getDefault())
            val timeString = format.format(date)

            iconView.setImageResource(R.drawable.ic_alarm)
            textView.text = timeString
            return true
        }

        return false
    }
}
