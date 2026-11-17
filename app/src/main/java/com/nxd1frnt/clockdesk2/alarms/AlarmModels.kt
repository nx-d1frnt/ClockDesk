package com.nxd1frnt.clockdesk2.alarms

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.UUID

enum class SunriseTone { WARM, AMBER, DAYLIGHT }

data class DeskAlarm(
    val id: String = UUID.randomUUID().toString(),
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val daysOfWeek: Set<Int> = emptySet(),
    val label: String = "",
    val sunriseEnabled: Boolean = false,
    val sunriseDurationMinutes: Int = 15,
    val soundEnabled: Boolean = true,
    val snoozeUntilMillis: Long? = null,
    val skipTriggerBeforeMillis: Long = 0L,
    val scheduledTriggerMillis: Long? = null,
    val soundUri: String? = null,
    val volumePercent: Int = 100,
    val fadeInSeconds: Int = 0,
    val vibrationEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
    val sunriseBrightnessPercent: Int = 100,
    val sunriseTone: SunriseTone = SunriseTone.WARM,
    val sunriseDismissedUntilMillis: Long = 0L
)

object AlarmScheduler {

    /**
     * Returns the persisted pending occurrence (including a late delivery), or calculates
     * the next local-time occurrence. Returns null if the alarm is disabled.
     */
    fun nextTriggerTimeMillis(alarm: DeskAlarm, nowMillis: Long): Long? {
        if (!alarm.enabled) return null
        alarm.scheduledTriggerMillis?.let { return it }
        val snooze = alarm.snoozeUntilMillis
        if (snooze != null && snooze > alarm.skipTriggerBeforeMillis) {
            return snooze
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = maxOf(nowMillis, alarm.skipTriggerBeforeMillis)
            set(Calendar.HOUR_OF_DAY, alarm.hour.coerceIn(0, 23))
            set(Calendar.MINUTE, alarm.minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (alarm.daysOfWeek.isEmpty()) {
            // Keep the current trigger minute valid for 60s so evaluateAlarms() can fire it at :00..:59.
            if (cal.timeInMillis + 60_000L <= nowMillis || cal.timeInMillis < alarm.skipTriggerBeforeMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        for (offset in 0..7) {
            val candidate = (cal.clone() as Calendar).apply {
                if (offset > 0) add(Calendar.DAY_OF_YEAR, offset)
            }
            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            val t = candidate.timeInMillis
            if (dayOfWeek in alarm.daysOfWeek && t + 60_000L > nowMillis && t >= alarm.skipTriggerBeforeMillis) {
                return t
            }
        }
        return null
    }

    /**
     * Returns sunrise progress in [0f, 1f] for [alarm] at [nowMillis].
     * Returns 0f when sunrise is disabled or outside the pre-alarm window.
     */
    fun sunriseProgressFor(alarm: DeskAlarm, nowMillis: Long): Float {
        if (!alarm.enabled || !alarm.sunriseEnabled || alarm.sunriseDurationMinutes <= 0 || alarm.snoozeUntilMillis != null) return 0f
        val triggerMillis = nextTriggerTimeMillis(alarm, nowMillis) ?: return 0f
        if (triggerMillis <= alarm.sunriseDismissedUntilMillis) return 0f
        if (nowMillis >= triggerMillis + 60_000L) return 0f
        val durationMillis = alarm.sunriseDurationMinutes * 60_000L
        val startMillis = triggerMillis - durationMillis
        if (nowMillis < startMillis) return 0f
        return ((nowMillis - startMillis).toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Maps sunrise progress [0f..1f] to a warm sunrise ARGB overlay color:
     * transparent -> deep ember -> warm orange -> bright morning sunlight.
     */
    fun sunriseOverlayColor(progress: Float, tone: SunriseTone = SunriseTone.WARM): Int {
        val p = progress.coerceIn(0f, 1f)
        if (p <= 0f) return Color.TRANSPARENT
        if (tone != SunriseTone.WARM) {
            val start = if (tone == SunriseTone.AMBER) Color.rgb(255, 80, 0) else Color.rgb(255, 190, 120)
            val end = if (tone == SunriseTone.AMBER) Color.rgb(255, 185, 65) else Color.rgb(255, 250, 235)
            val color = lerpColor(start, end, p)
            return Color.argb((235 * p).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        }
        return when {
            p < 0.35f -> {
                val t = p / 0.35f
                lerpColor(Color.argb(0, 120, 25, 0), Color.argb(115, 255, 68, 10), t)
            }
            p < 0.70f -> {
                val t = (p - 0.35f) / 0.35f
                lerpColor(Color.argb(115, 255, 68, 10), Color.argb(185, 255, 145, 45), t)
            }
            else -> {
                val t = (p - 0.70f) / 0.30f
                lerpColor(Color.argb(185, 255, 145, 45), Color.argb(235, 255, 232, 165), t)
            }
        }
    }

    fun sunriseBrightness(progress: Float, alarm: DeskAlarm): Float {
        val maximum = alarm.sunriseBrightnessPercent.coerceIn(5, 100) / 100f
        return 0.05f + (maximum - 0.05f) * progress.coerceIn(0f, 1f)
    }

    fun playbackVolume(alarm: DeskAlarm, elapsedMillis: Long): Float {
        val maximum = alarm.volumePercent.coerceIn(0, 100) / 100f
        if (alarm.fadeInSeconds <= 0) return maximum
        val fraction = (elapsedMillis.toFloat() / (alarm.fadeInSeconds.coerceAtMost(120) * 1000f)).coerceIn(0f, 1f)
        return maximum * (0.05f + 0.95f * fraction)
    }

    private fun lerpColor(start: Int, end: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        val a = (Color.alpha(start) + (Color.alpha(end) - Color.alpha(start)) * f).toInt()
        val r = (Color.red(start) + (Color.red(end) - Color.red(start)) * f).toInt()
        val g = (Color.green(start) + (Color.green(end) - Color.green(start)) * f).toInt()
        val b = (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * f).toInt()
        return Color.argb(a, r, g, b)
    }

    fun toJson(alarms: List<DeskAlarm>): String {
        val array = JSONArray()
        for (alarm in alarms) {
            val days = JSONArray()
            alarm.daysOfWeek.sorted().forEach { days.put(it) }
            array.put(JSONObject().apply {
                put("id", alarm.id)
                put("hour", alarm.hour)
                put("minute", alarm.minute)
                put("enabled", alarm.enabled)
                put("daysOfWeek", days)
                put("label", alarm.label)
                put("sunriseEnabled", alarm.sunriseEnabled)
                put("sunriseDurationMinutes", alarm.sunriseDurationMinutes)
                put("soundEnabled", alarm.soundEnabled)
                alarm.soundUri?.let { put("soundUri", it) }
                put("volumePercent", alarm.volumePercent)
                put("fadeInSeconds", alarm.fadeInSeconds)
                put("vibrationEnabled", alarm.vibrationEnabled)
                put("snoozeMinutes", alarm.snoozeMinutes)
                put("sunriseBrightnessPercent", alarm.sunriseBrightnessPercent)
                put("sunriseTone", alarm.sunriseTone.name)
                put("sunriseDismissedUntilMillis", alarm.sunriseDismissedUntilMillis)
                alarm.snoozeUntilMillis?.let { put("snoozeUntilMillis", it) }
                put("skipTriggerBeforeMillis", alarm.skipTriggerBeforeMillis)
                alarm.scheduledTriggerMillis?.let { put("scheduledTriggerMillis", it) }
            })
        }
        return array.toString()
    }

    fun fromJson(json: String?): List<DeskAlarm> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val daysArray = obj.optJSONArray("daysOfWeek")
                    val days = mutableSetOf<Int>()
                    if (daysArray != null) {
                        for (j in 0 until daysArray.length()) {
                            days.add(daysArray.getInt(j))
                        }
                    }
                    add(
                        DeskAlarm(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            hour = obj.optInt("hour", 7).coerceIn(0, 23),
                            minute = obj.optInt("minute", 0).coerceIn(0, 59),
                            enabled = obj.optBoolean("enabled", true),
                            daysOfWeek = days,
                            label = obj.optString("label", ""),
                            sunriseEnabled = obj.optBoolean("sunriseEnabled", false),
                            sunriseDurationMinutes = obj.optInt("sunriseDurationMinutes", 15).coerceIn(1, 60),
                            soundEnabled = obj.optBoolean("soundEnabled", true),
                            soundUri = obj.optString("soundUri").takeIf { it.isNotBlank() && it != "null" },
                            volumePercent = obj.optInt("volumePercent", 100).coerceIn(0, 100),
                            fadeInSeconds = obj.optInt("fadeInSeconds", 0).coerceIn(0, 120),
                            vibrationEnabled = obj.optBoolean("vibrationEnabled", true),
                            snoozeMinutes = obj.optInt("snoozeMinutes", 5).coerceIn(1, 30),
                            sunriseBrightnessPercent = obj.optInt("sunriseBrightnessPercent", 100).coerceIn(5, 100),
                            sunriseTone = SunriseTone.entries.firstOrNull { it.name == obj.optString("sunriseTone") } ?: SunriseTone.WARM,
                            sunriseDismissedUntilMillis = obj.optLong("sunriseDismissedUntilMillis", 0L),
                            snoozeUntilMillis = if (obj.has("snoozeUntilMillis") && !obj.isNull("snoozeUntilMillis")) {
                                obj.optLong("snoozeUntilMillis")
                            } else null,
                            skipTriggerBeforeMillis = obj.optLong("skipTriggerBeforeMillis", 0L),
                            scheduledTriggerMillis = if (obj.has("scheduledTriggerMillis") && !obj.isNull("scheduledTriggerMillis")) {
                                obj.optLong("scheduledTriggerMillis")
                            } else null
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
