package com.nxd1frnt.clockdesk2.desks.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.app.Activity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.snackbar.Snackbar
import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.alarms.DeskAlarm
import com.nxd1frnt.clockdesk2.alarms.DeskAlarmManager
import com.nxd1frnt.clockdesk2.alarms.SystemAlarmScheduler
import com.nxd1frnt.clockdesk2.utils.UiMonetPalette
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AlarmsDeskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val txtTitle: TextView
    private val btnAdd: MaterialButton
    private val cardSummary: MaterialCardView
    private val cardList: MaterialCardView
    private val imgSummaryIcon: ImageView
    private val txtNextLabel: TextView
    private val txtNextTime: TextView
    private val txtNextSub: TextView
    private val btnSkip: MaterialButton
    private val btnPermissions: MaterialButton
    private val btnEditNext: MaterialButton
    private val btnStopLight: MaterialButton
    private var editorDialog: AlertDialog? = null
    private val refreshSummary = object : Runnable {
        override fun run() {
            updateSummary()
            postDelayed(this, 15_000L)
        }
    }
    private val rvAlarms: RecyclerView
    private val txtEmpty: TextView

    private val adapter = AlarmsAdapter()
    private var currentPalette: UiMonetPalette = UiMonetThemeManager.getPalette(context)

    private val alarmListener = object : DeskAlarmManager.AlarmListener {
        override fun onAlarmsChanged(alarms: List<DeskAlarm>) {
            post {
                adapter.submit(alarms)
                txtEmpty.visibility = if (alarms.isEmpty()) View.VISIBLE else View.GONE
                updateSummary()
            }
        }

        override fun onSunriseChanged(progress: Float, alarm: DeskAlarm?) {
            post { updateSummary() }
        }

        override fun onAlarmRinging(alarm: DeskAlarm?) {
            post { updateSummary() }
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.desk_alarms_layout, this, true)

        txtTitle = findViewById(R.id.txt_alarms_title)
        btnAdd = findViewById(R.id.btn_alarm_add)
        cardSummary = findViewById(R.id.card_alarm_summary)
        cardList = findViewById(R.id.card_alarms_list)
        imgSummaryIcon = findViewById(R.id.img_alarm_summary_icon)
        txtNextLabel = findViewById(R.id.txt_alarm_next_label)
        txtNextTime = findViewById(R.id.txt_alarm_next_time)
        txtNextSub = findViewById(R.id.txt_alarm_next_sub)
        btnSkip = findViewById(R.id.btn_alarm_skip_next)
        btnPermissions = findViewById(R.id.btn_alarm_permissions)
        btnEditNext = findViewById(R.id.btn_alarm_edit_next)
        btnStopLight = findViewById(R.id.btn_alarm_stop_light)
        rvAlarms = findViewById(R.id.rv_alarms)
        txtEmpty = findViewById(R.id.txt_alarms_empty)

        rvAlarms.layoutManager = LinearLayoutManager(context)
        rvAlarms.adapter = adapter

        btnAdd.setOnClickListener { showAlarmEditor(null) }
        btnSkip.setOnClickListener {
            DeskAlarmManager.getNextAlarm()?.let { (before, _) ->
                DeskAlarmManager.skipNext(before.id)
                val after = DeskAlarmManager.getAlarms().find { it.id == before.id } ?: return@let
                Snackbar.make(this, if (before.snoozeUntilMillis != null) R.string.alarm_snooze_cancelled else R.string.alarm_skipped,
                    Snackbar.LENGTH_LONG).setAction(R.string.alarm_undo) {
                    DeskAlarmManager.restoreSkippedOccurrence(before, after)
                }.show()
            }
        }
        btnPermissions.setOnClickListener { requestAlarmAccess() }
        btnEditNext.setOnClickListener { DeskAlarmManager.getNextAlarm()?.let { showAlarmEditor(it.first) } }
        btnStopLight.setOnClickListener { DeskAlarmManager.dismissSunrise() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        DeskAlarmManager.addListener(alarmListener)
        refreshMonetTheme()
        post(refreshSummary)
    }

    override fun onDetachedFromWindow() {
        DeskAlarmManager.removeListener(alarmListener)
        removeCallbacks(refreshSummary)
        editorDialog?.dismiss()
        super.onDetachedFromWindow()
    }

    fun dispose() {
        DeskAlarmManager.removeListener(alarmListener)
        removeCallbacks(refreshSummary)
        editorDialog?.dismiss()
    }

    fun refreshMonetTheme(palette: UiMonetPalette = UiMonetThemeManager.getPalette(context)) {
        currentPalette = palette
        UiMonetThemeManager.applyLiveToViewHierarchy(this, palette)
        txtTitle.setTextColor(palette.onSurface)
        btnAdd.backgroundTintList = ColorStateList.valueOf(palette.primaryContainer)
        btnAdd.setTextColor(palette.onPrimaryContainer)
        btnAdd.iconTint = ColorStateList.valueOf(palette.onPrimaryContainer)

        cardSummary.setCardBackgroundColor(palette.surfaceContainer)
        cardSummary.strokeColor = palette.outlineVariant
        cardList.setCardBackgroundColor(palette.surfaceContainer)
        cardList.strokeColor = palette.outlineVariant

        imgSummaryIcon.imageTintList = ColorStateList.valueOf(palette.primary)
        txtNextLabel.setTextColor(palette.onSurfaceVariant)
        txtNextTime.setTextColor(palette.onSurface)
        txtNextSub.setTextColor(palette.onSurfaceVariant)
        txtEmpty.setTextColor(palette.onSurfaceVariant)

        listOf(btnSkip, btnPermissions, btnEditNext, btnStopLight).forEach {
            it.setTextColor(palette.primary)
            it.strokeColor = ColorStateList.valueOf(palette.outlineVariant)
        }
        adapter.notifyDataSetChanged()
        DeskForegroundStyle.apply(this)
    }

    private fun updateSummary() {
        val now = System.currentTimeMillis()
        val sunriseProgress = DeskAlarmManager.sunriseProgress
        val next = DeskAlarmManager.getNextAlarm(now)
        val sunriseActive = sunriseProgress > 0f && DeskAlarmManager.ringingAlarm == null
        imgSummaryIcon.setImageResource(if (sunriseActive) R.drawable.ic_sun_clock_outline else R.drawable.ic_alarm)
        btnSkip.visibility = if (next != null && DeskAlarmManager.ringingAlarm == null) View.VISIBLE else View.GONE
        btnEditNext.visibility = btnSkip.visibility
        btnStopLight.visibility = if (DeskAlarmManager.isSunriseActive) View.VISIBLE else View.GONE
        val kind = context.getString(if (next?.first?.snoozeUntilMillis != null) R.string.alarm_snoozed else R.string.alarm_next_label)
        txtNextLabel.text = next?.first?.label?.takeIf { it.isNotBlank() }?.let { "$it · $kind" } ?: kind
        if (next != null) {
            val (alarm, trigger) = next
            val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
            txtNextTime.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(trigger))
            val durationMinutes = ((trigger - now + 59_999L) / 60_000L).coerceAtLeast(1L)
            val duration = if (durationMinutes >= 60) {
                context.getString(R.string.alarm_duration_hours_minutes, durationMinutes / 60, durationMinutes % 60)
            } else context.getString(R.string.alarm_duration_minutes, durationMinutes)
            val today = Calendar.getInstance().apply { timeInMillis = now }
            val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val day = Calendar.getInstance().apply { timeInMillis = trigger }
            fun sameDay(other: Calendar) = day.get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
                day.get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
            val date = when {
                sameDay(today) -> context.getString(R.string.alarm_today)
                sameDay(tomorrow) -> context.getString(R.string.alarm_tomorrow)
                else -> DateFormat.getMediumDateFormat(context).format(Date(trigger))
            }
            val details = "$date · ${context.getString(R.string.alarm_in_duration, duration)}"
            txtNextSub.text = if (sunriseActive) {
                "$details\n${context.getString(R.string.alarm_sunrise_active, (sunriseProgress * 100).toInt().coerceIn(1, 100))}"
            } else details
            btnSkip.setText(if (alarm.snoozeUntilMillis != null) R.string.alarm_cancel_snooze else R.string.alarm_skip_next)
        } else {
            txtNextTime.text = "--:--"
            txtNextSub.setText(R.string.alarm_none_upcoming)
        }
        val access = missingAlarmAccess()
        btnPermissions.visibility = if (access == null) View.GONE else View.VISIBLE
        if (access != null) btnPermissions.setText(access)
    }

    private fun missingAlarmAccess(): Int? = when {
        !SystemAlarmScheduler.canSchedule(context) -> R.string.alarm_allow_exact
        !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() -> R.string.alarm_allow_notifications
        Build.VERSION.SDK_INT >= 34 && !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() -> R.string.alarm_allow_fullscreen
        else -> null
    }

    private fun requestAlarmAccess() {
        val activity = context as? Activity ?: return
        val packageUri = Uri.parse("package:${context.packageName}")
        val intent = when (missingAlarmAccess()) {
            R.string.alarm_allow_exact -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
            R.string.alarm_allow_notifications -> {
                if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !activity.getPreferences(Context.MODE_PRIVATE).getBoolean("alarm_notification_requested", false)) {
                    activity.getPreferences(Context.MODE_PRIVATE).edit().putBoolean("alarm_notification_requested", true).apply()
                    activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2401)
                    return
                }
                if (Build.VERSION.SDK_INT >= 26) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
            }
            R.string.alarm_allow_fullscreen -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
            else -> return
        }
        if (intent.resolveActivity(context.packageManager) != null) activity.startActivity(intent)
    }

    private fun formatAlarmTime(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(cal.timeInMillis))
    }

    private fun formatDays(days: Set<Int>): String {
        if (days.isEmpty()) return context.getString(R.string.alarm_once)
        val weekdays = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY
        )
        val weekends = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        if (days.size == 7) return context.getString(R.string.alarm_daily)
        if (days == weekdays) return context.getString(R.string.alarm_weekdays)
        if (days == weekends) return context.getString(R.string.alarm_weekends)

        val symbols = DateFormatSymbols.getInstance().shortWeekdays
        val order = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        return order.filter { it in days }.joinToString(" ") { symbols[it] }
    }

    private fun showAlarmEditor(existing: DeskAlarm?) {
        val activity = context as? Activity ?: return
        val palette = UiMonetThemeManager.getPalette(activity)
        val density = resources.displayMetrics.density
        val pad = (16 * density).toInt()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (8 * density).toInt(), pad, (8 * density).toInt())
        }

        var selectedHour = existing?.hour ?: 7
        var selectedMinute = existing?.minute ?: 0
        var timePickerDialog: android.app.TimePickerDialog? = null
        var materialTimePicker: MaterialTimePicker? = null
        val timeButton = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            tag = "alarm_time"
            text = formatAlarmTime(selectedHour, selectedMinute)
            textSize = 32f
            minHeight = (64 * density).toInt()
            cornerRadius = (20 * density).toInt()
            backgroundTintList = ColorStateList.valueOf(palette.primaryContainer)
            setTextColor(palette.onPrimaryContainer)
            ResourcesCompat.getFont(activity, R.font.googlesansflex)?.let { typeface = it }
            contentDescription = activity.getString(R.string.alarm_change_time)
        }
        timeButton.setOnClickListener {
            materialTimePicker?.dismissAllowingStateLoss()
            timePickerDialog?.dismiss()
            if (activity is FragmentActivity) {
                val picker = MaterialTimePicker.Builder()
                    .setTimeFormat(if (DateFormat.is24HourFormat(activity)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                    .setHour(selectedHour)
                    .setMinute(selectedMinute)
                    .setTitleText(R.string.alarm_change_time)
                    .build()
                picker.addOnPositiveButtonClickListener {
                    selectedHour = picker.hour
                    selectedMinute = picker.minute
                    timeButton.text = formatAlarmTime(selectedHour, selectedMinute)
                }
                materialTimePicker = picker
                picker.show(activity.supportFragmentManager, "alarm_time_picker")
            } else {
                timePickerDialog = android.app.TimePickerDialog(activity, { _, hour, minute ->
                    selectedHour = hour
                    selectedMinute = minute
                    timeButton.text = formatAlarmTime(hour, minute)
                }, selectedHour, selectedMinute, DateFormat.is24HourFormat(activity)).also { it.show() }
            }
        }
        root.addView(timeButton, LinearLayout.LayoutParams(-1, -2))

        // Label input
        val tilLabel = TextInputLayout(activity).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = activity.getString(R.string.alarm_label_hint)
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }
        val edtLabel = TextInputEditText(tilLabel.context).apply {
            setText(existing?.label.orEmpty())
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        tilLabel.addView(edtLabel, LinearLayout.LayoutParams(-1, -2))
        UiMonetThemeManager.styleTextInputLayout(tilLabel, palette)
        root.addView(tilLabel, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (12 * density).toInt()
        })

        // Day-of-week segmented toggle group
        val selectedDays = (existing?.daysOfWeek ?: emptySet()).toMutableSet()
        val daysGroup = MaterialButtonToggleGroup(activity).apply {
            isSingleSelection = false
            isSelectionRequired = false
        }
        val dayOrder = listOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
            Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
        )
        val symbols = DateFormatSymbols.getInstance()
        val shortNames = symbols.shortWeekdays
        val fullNames = symbols.weekdays
        val dayByButtonId = mutableMapOf<Int, Int>()
        dayOrder.forEach { day ->
            val btnId = View.generateViewId()
            dayByButtonId[btnId] = day
            val btn = MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                id = btnId
                isCheckable = true
                isChecked = day in selectedDays
                text = shortNames[day].take(2)
                contentDescription = fullNames[day]
                textSize = 12f
                minimumWidth = 0
                minWidth = 0
                setPadding(0, 0, 0, 0)
            }
            daysGroup.addView(btn, LinearLayout.LayoutParams(0, (48 * density).toInt(), 1f))
        }
        UiMonetThemeManager.styleButtonToggleGroup(daysGroup, palette)
        daysGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            val day = dayByButtonId[checkedId] ?: return@addOnButtonCheckedListener
            if (isChecked) selectedDays.add(day) else selectedDays.remove(day)
            UiMonetThemeManager.styleButtonToggleGroup(daysGroup, palette)
        }
        root.addView(daysGroup, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (12 * density).toInt()
        })

        val panel = AlarmSettingsPanel(activity, existing ?: DeskAlarm(hour = 7, minute = 0))
        root.addView(panel, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(activity).apply { addView(root) }
        editorDialog?.dismiss()
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(if (existing == null) R.string.alarm_add else R.string.alarm_edit)
            .setView(scroll)
            .setPositiveButton(R.string.apply) { _, _ ->
                DeskAlarmManager.addOrUpdateAlarm(panel.draft.copy(
                    hour = selectedHour, minute = selectedMinute,
                    daysOfWeek = selectedDays.toSet(),
                    label = edtLabel.text?.toString()?.trim().orEmpty()
                ))
            }.setNegativeButton(R.string.cancel, null)
        if (existing != null) builder.setNeutralButton(R.string.alarm_delete) { _, _ -> DeskAlarmManager.deleteAlarm(existing.id) }
        val dialog = builder.create()
        editorDialog = dialog
        val owner = activity as? LifecycleOwner
        val lifecycle = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                panel.stopPreviews()
                materialTimePicker?.dismissAllowingStateLoss()
                timePickerDialog?.dismiss()
            }
            override fun onDestroy(owner: LifecycleOwner) { dialog.dismiss() }
        }
        val ringing = object : DeskAlarmManager.AlarmListener {
            override fun onAlarmRinging(alarm: DeskAlarm?) { if (alarm != null) dialog.dismiss() }
        }
        dialog.setOnDismissListener {
            materialTimePicker?.dismissAllowingStateLoss()
            timePickerDialog?.dismiss()
            panel.dispose()
            owner?.lifecycle?.removeObserver(lifecycle)
            DeskAlarmManager.removeListener(ringing)
            if (editorDialog === dialog) editorDialog = null
        }
        dialog.show()
        UiMonetThemeManager.styleAlertDialog(dialog, palette)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(palette.error)
        owner?.lifecycle?.addObserver(lifecycle)
        DeskAlarmManager.addListener(ringing)
    }

    private inner class AlarmsAdapter : RecyclerView.Adapter<AlarmsAdapter.AlarmVH>() {
        private val items = mutableListOf<DeskAlarm>()

        fun submit(newItems: List<DeskAlarm>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmVH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desk_alarm, parent, false)
            return AlarmVH(view)
        }

        override fun onBindViewHolder(holder: AlarmVH, position: Int) {
            holder.bind(items[position])
        }

        inner class AlarmVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val card: MaterialCardView = itemView.findViewById(R.id.card_alarm_item)
            private val txtTime: TextView = itemView.findViewById(R.id.txt_item_alarm_time)
            private val imgSunrise: ImageView = itemView.findViewById(R.id.img_item_sunrise)
            private val txtSunrise: TextView = itemView.findViewById(R.id.txt_item_sunrise)
            private val txtMeta: TextView = itemView.findViewById(R.id.txt_item_alarm_meta)
            private val switchEnabled: MaterialSwitch = itemView.findViewById(R.id.switch_item_alarm)

            fun bind(alarm: DeskAlarm) {
                card.setCardBackgroundColor(currentPalette.surfaceContainerHigh)
                card.strokeColor = currentPalette.outlineVariant
                txtTime.setTextColor(if (alarm.enabled) currentPalette.onSurface else currentPalette.onSurfaceVariant)
                txtMeta.setTextColor(currentPalette.onSurfaceVariant)
                imgSunrise.imageTintList = ColorStateList.valueOf(currentPalette.primary)
                txtSunrise.setTextColor(currentPalette.primary)

                txtTime.text = if (alarm.snoozeUntilMillis != null) {
                    DateFormat.getTimeFormat(context).format(Date(alarm.snoozeUntilMillis))
                } else formatAlarmTime(alarm.hour, alarm.minute)

                if (alarm.sunriseEnabled) {
                    imgSunrise.visibility = View.VISIBLE
                    txtSunrise.visibility = View.VISIBLE
                    txtSunrise.text = context.getString(R.string.alarm_sunrise_minutes, alarm.sunriseDurationMinutes)
                } else {
                    imgSunrise.visibility = View.GONE
                    txtSunrise.visibility = View.GONE
                }

                val daysText = if (alarm.snoozeUntilMillis != null) context.getString(R.string.alarm_snoozed) else formatDays(alarm.daysOfWeek)
                txtMeta.text = if (alarm.label.isNotBlank()) "${alarm.label} · $daysText" else daysText

                switchEnabled.setOnCheckedChangeListener(null)
                switchEnabled.isChecked = alarm.enabled
                switchEnabled.setOnCheckedChangeListener { _, checked ->
                    DeskAlarmManager.setAlarmEnabled(alarm.id, checked)
                }

                card.setOnClickListener { showAlarmEditor(alarm) }
            }
        }
    }
}
