package com.nxd1frnt.clockdesk2.daytimegetter

import android.content.Context

/** One day's times for the latest location, kept separate from user settings. */
internal class SunTimesCache(context: Context) {
    private val prefs = context.getSharedPreferences("SunTimesCache", Context.MODE_PRIVATE)
    private val fields = listOf("sunrise", "sunset", "dawn", "dusk", "solar_noon")

    init {
        val settings = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val legacyKeys = settings.all.keys.filter { key ->
            fields.any { key.startsWith("${it}_sun_times_") }
        }
        if (legacyKeys.isNotEmpty()) {
            val editor = settings.edit()
            legacyKeys.forEach { editor.remove(it) }
            editor.apply()
        }
    }

    fun read(key: String): List<Long>? {
        if (prefs.getString("key", null) != key) return null
        return fields.map { prefs.getLong(it, 0L) }.takeIf { times -> times.all { it > 0L } }
    }

    fun write(key: String, times: List<Long>) {
        require(times.size == fields.size && times.all { it > 0L })
        val editor = prefs.edit().clear().putString("key", key)
        fields.zip(times).forEach { (field, time) -> editor.putLong(field, time) }
        editor.apply()
    }
}
