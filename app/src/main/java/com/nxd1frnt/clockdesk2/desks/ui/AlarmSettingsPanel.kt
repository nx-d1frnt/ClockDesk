package com.nxd1frnt.clockdesk2.desks.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.alarms.AlarmScheduler
import com.nxd1frnt.clockdesk2.alarms.AlarmSoundPlayer
import com.nxd1frnt.clockdesk2.alarms.DeskAlarm
import com.nxd1frnt.clockdesk2.alarms.SunriseTone
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

/** An unsaved settings draft. Previews use their own player and dialog window. */
internal class AlarmSettingsPanel(private val activity: Activity, initial: DeskAlarm) : LinearLayout(activity) {
    var draft = initial
        private set
    private val palette = UiMonetThemeManager.getPalette(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val sound = AlarmSoundPlayer(activity)
    private var childDialog: AlertDialog? = null
    private var previewDialog: AlertDialog? = null
    private val stopAudio = Runnable { stopPreviews() }
    private lateinit var audioPreview: MaterialButton
    private lateinit var audioError: TextView
    private val dp = resources.displayMetrics.density

    init {
        orientation = VERTICAL

        val wakeupCard = sectionCard(R.string.alarm_wakeup_settings, topSpace = 12)
        val soundOptions = column()
        toggle(wakeupCard, R.string.alarm_sound_only, "alarm_sound", initial.soundEnabled) {
            draft = draft.copy(soundEnabled = it)
            soundOptions.visibility = if (it) VISIBLE else GONE
            stopPreviews()
        }
        wakeupCard.addView(soundOptions)
        soundOptions.visibility = if (initial.soundEnabled) VISIBLE else GONE

        val soundRow = row()
        val melody = outlinedButton(soundRow, R.string.alarm_melody, "alarm_melody", LayoutParams(0, -2, 1f))
        fun updateMelody() {
            val title = draft.soundUri?.let { uri -> runCatching {
                activity.contentResolver.query(Uri.parse(uri), arrayOf(android.provider.MediaStore.Audio.Media.TITLE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull() } ?: activity.getString(R.string.alarm_default_melody)
            melody.text = activity.getString(R.string.alarm_melody_title, title)
        }
        updateMelody()
        melody.setOnClickListener { chooseMelody { updateMelody() } }

        audioPreview = tonalButton(soundRow, R.string.alarm_preview_sound, "alarm_preview_sound",
            LayoutParams(-2, -2).apply { marginStart = (8 * dp).toInt() })
        soundOptions.addView(soundRow, LayoutParams(-1, -2).apply { topMargin = (4 * dp).toInt() })

        audioError = text(activity.getString(R.string.alarm_sound_unavailable), 12f, palette.error).apply {
            visibility = GONE
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }
        soundOptions.addView(audioError)

        audioPreview.setOnClickListener {
            audioError.visibility = GONE
            if (audioPreview.isSelected) stopPreviews() else {
                stopPreviews()
                audioPreview.isSelected = true
                audioPreview.setText(R.string.alarm_stop_preview)
                sound.play(draft) {
                    audioPreview.isSelected = false
                    audioPreview.setText(R.string.alarm_preview_sound)
                    audioError.visibility = VISIBLE
                }
                handler.postDelayed(stopAudio, 8_000L)
            }
        }
        slider(soundOptions, R.string.alarm_volume, "alarm_volume", 0, 100, initial.volumePercent) {
            draft = draft.copy(volumePercent = it); stopPreviews()
        }
        soundOptions.addView(text(activity.getString(R.string.alarm_volume_summary), 12f, palette.onSurfaceVariant))
        slider(soundOptions, R.string.alarm_fade_in, "alarm_fade", 0, 120, initial.fadeInSeconds) {
            draft = draft.copy(fadeInSeconds = it); stopPreviews()
        }
        toggle(wakeupCard, R.string.alarm_vibration, "alarm_vibration", initial.vibrationEnabled) {
            draft = draft.copy(vibrationEnabled = it)
        }
        slider(wakeupCard, R.string.alarm_snooze_interval, "alarm_snooze_interval", 1, 30, initial.snoozeMinutes) {
            draft = draft.copy(snoozeMinutes = it)
        }

        val sunriseCard = sectionCard(R.string.alarm_sunrise_settings, topSpace = 12)
        val sunriseOptions = column()
        toggle(sunriseCard, R.string.alarm_sunrise_option, "alarm_sunrise", initial.sunriseEnabled) {
            draft = draft.copy(sunriseEnabled = it)
            sunriseOptions.visibility = if (it) VISIBLE else GONE
            stopPreviews()
        }
        sunriseCard.addView(text(activity.getString(R.string.alarm_sunrise_summary), 12f, palette.onSurfaceVariant))
        sunriseCard.addView(sunriseOptions)
        sunriseOptions.visibility = if (initial.sunriseEnabled) VISIBLE else GONE
        slider(sunriseOptions, R.string.alarm_sunrise_duration_value, "alarm_sunrise_duration", 1, 60, initial.sunriseDurationMinutes) {
            draft = draft.copy(sunriseDurationMinutes = it)
        }
        slider(sunriseOptions, R.string.alarm_sunrise_brightness, "alarm_sunrise_brightness", 5, 100, initial.sunriseBrightnessPercent) {
            draft = draft.copy(sunriseBrightnessPercent = it)
        }
        val sunriseRow = row()
        val tone = outlinedButton(sunriseRow, R.string.alarm_sunrise_tone, "alarm_sunrise_tone", LayoutParams(0, -2, 1f))
        fun toneTitle() { tone.text = activity.getString(R.string.alarm_tone_title, toneNames()[draft.sunriseTone.ordinal]) }
        toneTitle()
        tone.setOnClickListener {
            childDialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.alarm_sunrise_tone)
                .setSingleChoiceItems(toneNames(), draft.sunriseTone.ordinal) { dialog, which ->
                    draft = draft.copy(sunriseTone = SunriseTone.entries[which])
                    toneTitle()
                    dialog.dismiss()
                }.setNegativeButton(R.string.cancel, null).show().also { UiMonetThemeManager.styleAlertDialog(it, palette) }
        }
        tonalButton(sunriseRow, R.string.alarm_preview_sunrise, "alarm_preview_sunrise",
            LayoutParams(-2, -2).apply { marginStart = (8 * dp).toInt() })
            .setOnClickListener { previewSunrise() }
        sunriseOptions.addView(sunriseRow, LayoutParams(-1, -2).apply { topMargin = (6 * dp).toInt() })
    }

    fun stopPreviews() {
        handler.removeCallbacksAndMessages(null)
        sound.stop()
        if (::audioPreview.isInitialized) { audioPreview.isSelected = false; audioPreview.setText(R.string.alarm_preview_sound) }
        previewDialog?.dismiss()
        previewDialog = null
    }

    fun dispose() { stopPreviews(); childDialog?.dismiss(); childDialog = null }

    private fun toneNames() = arrayOf(activity.getString(R.string.alarm_tone_warm),
        activity.getString(R.string.alarm_tone_amber), activity.getString(R.string.alarm_tone_daylight))

    private fun chooseMelody(updated: () -> Unit) {
        stopPreviews()
        val titles = mutableListOf(activity.getString(R.string.alarm_default_melody))
        val uris = mutableListOf<String?>(null)
        runCatching {
            val manager = RingtoneManager(activity).apply { setType(RingtoneManager.TYPE_ALARM) }
            manager.cursor.use { cursor ->
                while (cursor.moveToNext()) {
                    titles.add(cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX))
                    uris.add(manager.getRingtoneUri(cursor.position).toString())
                }
            }
        }
        childDialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.alarm_melody)
            .setSingleChoiceItems(titles.toTypedArray(), uris.indexOf(draft.soundUri)) { dialog, which ->
                draft = draft.copy(soundUri = uris[which])
                updated()
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show().also { UiMonetThemeManager.styleAlertDialog(it, palette) }
    }

    private fun previewSunrise() {
        stopPreviews()
        val snapshot = draft
        val light = TextView(activity).apply {
            text = activity.getString(R.string.alarm_sunrise_preview_summary)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(24, 48, 24, 48)
            minHeight = (150 * dp).toInt()
        }
        val dialog = MaterialAlertDialogBuilder(activity).setTitle(R.string.alarm_preview_sunrise)
            .setView(light).setNegativeButton(R.string.alarm_stop_preview, null).create()
        previewDialog = dialog
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null); previewDialog = null }
        dialog.show()
        UiMonetThemeManager.styleAlertDialog(dialog, palette)
        val start = SystemClock.elapsedRealtime()
        val animate = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                val p = ((SystemClock.elapsedRealtime() - start) / 10_000f).coerceIn(0f, 1f)
                val bgColor = AlarmScheduler.sunriseOverlayColor(p, snapshot.sunriseTone)
                light.setBackgroundColor(bgColor)
                light.setTextColor(if (ColorUtils.calculateLuminance(bgColor) > 0.45) Color.BLACK else Color.WHITE)
                dialog.window?.let { window ->
                    window.attributes = window.attributes.apply { screenBrightness = AlarmScheduler.sunriseBrightness(p, snapshot) }
                }
                if (p < 1f) handler.postDelayed(this, 50L) else handler.postDelayed({ dialog.dismiss() }, 1500L)
            }
        }
        handler.post(animate)
    }

    private fun column() = LinearLayout(activity).apply { orientation = VERTICAL }
    private fun row() = LinearLayout(activity).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun text(value: String, size: Float = 14f, color: Int = palette.onSurface) = TextView(activity).apply {
        text = value; textSize = size; setTextColor(color)
    }
    private fun sectionCard(label: Int, topSpace: Int = 12): LinearLayout {
        val card = MaterialCardView(activity).apply {
            radius = 20f * dp
            cardElevation = 0f
            strokeWidth = (1 * dp).toInt()
            strokeColor = palette.outlineVariant
            setCardBackgroundColor(palette.surfaceContainer)
        }
        val inner = column().apply {
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }
        inner.addView(text(activity.getString(label), 14f, palette.primary).apply {
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LayoutParams(-1, -2).apply { bottomMargin = (4 * dp).toInt() })
        card.addView(inner, LayoutParams(-1, -2))
        addView(card, LayoutParams(-1, -2).apply { topMargin = (topSpace * dp).toInt() })
        return inner
    }
    private fun toggle(parent: LinearLayout, label: Int, key: String, checked: Boolean, changed: (Boolean) -> Unit) {
        parent.addView(MaterialSwitch(activity).apply {
            tag = key; text = activity.getString(label); isChecked = checked; minHeight = (48 * dp).toInt()
            UiMonetThemeManager.styleMaterialSwitch(this, palette)
            setOnCheckedChangeListener { _, value -> changed(value) }
        }, LayoutParams(-1, -2))
    }
    private fun slider(parent: LinearLayout, label: Int, key: String, min: Int, max: Int, initial: Int, changed: (Int) -> Unit) {
        val clamped = initial.coerceIn(min, max)
        val valueLabel = text(activity.getString(label, clamped))
        parent.addView(valueLabel, LayoutParams(-1, -2).apply { topMargin = (8 * dp).toInt() })
        parent.addView(Slider(activity).apply {
            tag = key
            valueFrom = min.toFloat()
            valueTo = max.toFloat()
            stepSize = 1f
            value = clamped.toFloat()
            labelBehavior = LabelFormatter.LABEL_FLOATING
            contentDescription = valueLabel.text
            UiMonetThemeManager.styleSlider(this, palette)
            addOnChangeListener { _, v, _ ->
                val intVal = v.toInt()
                valueLabel.text = activity.getString(label, intVal)
                contentDescription = valueLabel.text
                changed(intVal)
            }
        }, LayoutParams(-1, -2))
    }
    private fun outlinedButton(parent: LinearLayout, label: Int, key: String, lp: LayoutParams) =
        MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            tag = key; setText(label); minHeight = (48 * dp).toInt()
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setTextColor(palette.primary)
            strokeColor = ColorStateList.valueOf(palette.outlineVariant)
            parent.addView(this, lp)
        }
    private fun tonalButton(parent: LinearLayout, label: Int, key: String, lp: LayoutParams) =
        MaterialButton(activity, null, com.google.android.material.R.attr.materialButtonTonalStyle).apply {
            tag = key; setText(label); minHeight = (48 * dp).toInt()
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            backgroundTintList = ColorStateList.valueOf(palette.secondaryContainer)
            setTextColor(palette.onSecondaryContainer)
            parent.addView(this, lp)
        }
}
