package com.nxd1frnt.clockdesk2.desks

import android.content.Context
import android.content.SharedPreferences
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetManager

/** Widget appearance belongs to a desk; application and background settings stay shared. */
class DeskAppearancePreferences(private val context: Context) : SharedPreferences {
    companion object {
        private val widgetSuffixes = listOf(
            "FontID", "FontIndex", "Size", "Alpha", "VariationAxes", "Weight", "Width", "Roundness",
            "Color", "UseDynamicColor", "DynamicRole", "BgColor", "UseDynamicBgColor", "DynamicBgRole",
            "isNightShiftEnabled", "MaxWidth"
        )
        private val widgetKeys = listOf("time", "date", "lastfm", "chips", "weather")
            .flatMap { prefix -> widgetSuffixes.map { prefix + it } }.toSet() + setOf(
                "timeFormatPattern", "dateFormatPattern", "clockStyle", "dateStyle", "mediaStyle",
                "show_media_icon", "clock_show_seconds", "clock_digit_animations", "clock_digit_animation_style", "smart_chips_stack_overflow", "smart_chip_order"
            )

        fun profile(context: Context, layoutKey: String): SharedPreferences {
            val global = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
            if (layoutKey == DesktopWidgetManager.DEFAULT_LAYOUT_KEY) return global
            val profile = context.getSharedPreferences("DeskAppearance_$layoutKey", Context.MODE_PRIVATE)
            if (!profile.getBoolean("profile_initialized", false)) {
                val editor = profile.edit()
                global.all.filterKeys { it in widgetKeys }.forEach { (key, value) -> putValue(editor, key, value) }
                editor.putBoolean("profile_initialized", true).apply()
            }
            return profile
        }

        fun copyProfile(context: Context, sourceKey: String, destinationKey: String) {
            val source = profile(context, sourceKey)
            val editor = profile(context, destinationKey).edit().clear()
            source.all.filterKeys { it in widgetKeys }.forEach { (key, value) -> putValue(editor, key, value) }
            editor.putBoolean("profile_initialized", true).apply()
        }

        internal fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
            when (value) {
                null -> editor.remove(key)
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
    }

    private val global = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
    private var appearance = global
    var layoutKey: String = DesktopWidgetManager.DEFAULT_LAYOUT_KEY
        private set
    private val listeners = mutableMapOf<SharedPreferences.OnSharedPreferenceChangeListener,
        Pair<SharedPreferences.OnSharedPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener>>()

    fun switchLayout(key: String) {
        if (layoutKey == key) return
        listeners.values.forEach { (_, local) -> if (appearance !== global) appearance.unregisterOnSharedPreferenceChangeListener(local) }
        layoutKey = key
        appearance = profile(context, key)
        listeners.values.forEach { (_, local) -> if (appearance !== global) appearance.registerOnSharedPreferenceChangeListener(local) }
    }

    private fun storage(key: String?) = if (key in widgetKeys) appearance else global
    override fun contains(key: String?) = storage(key).contains(key)
    override fun getString(key: String?, defValue: String?) = storage(key).getString(key, defValue)
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = storage(key).getStringSet(key, defValues)
    override fun getInt(key: String?, defValue: Int) = storage(key).getInt(key, defValue)
    override fun getLong(key: String?, defValue: Long) = storage(key).getLong(key, defValue)
    override fun getFloat(key: String?, defValue: Float) = storage(key).getFloat(key, defValue)
    override fun getBoolean(key: String?, defValue: Boolean) = storage(key).getBoolean(key, defValue)
    override fun getAll(): MutableMap<String, *> = (global.all.filterKeys { it !in widgetKeys } +
        appearance.all.filterKeys { it in widgetKeys }).toMutableMap()

    override fun edit(): SharedPreferences.Editor {
        // Capture the profile so an editor cannot write into a subsequently selected desk.
        val localEditor = appearance.edit()
        val globalEditor = if (appearance === global) localEditor else global.edit()
        fun editor(key: String?) = if (key in widgetKeys) localEditor else globalEditor
        return object : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?) = apply { editor(key).putString(key, value) }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { editor(key).putStringSet(key, values) }
            override fun putInt(key: String?, value: Int) = apply { editor(key).putInt(key, value) }
            override fun putLong(key: String?, value: Long) = apply { editor(key).putLong(key, value) }
            override fun putFloat(key: String?, value: Float) = apply { editor(key).putFloat(key, value) }
            override fun putBoolean(key: String?, value: Boolean) = apply { editor(key).putBoolean(key, value) }
            override fun remove(key: String?) = apply { editor(key).remove(key) }
            override fun clear() = apply { widgetKeys.forEach { localEditor.remove(it) } }
            override fun apply() { if (globalEditor !== localEditor) globalEditor.apply(); localEditor.apply() }
            override fun commit(): Boolean {
                val globalSaved = if (globalEditor !== localEditor) globalEditor.commit() else true
                return localEditor.commit() && globalSaved
            }
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        if (listeners.containsKey(listener)) return
        val globalListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (appearance === global || key !in widgetKeys) listener.onSharedPreferenceChanged(this, key)
        }
        val localListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in widgetKeys) listener.onSharedPreferenceChanged(this, key)
        }
        listeners[listener] = globalListener to localListener
        global.registerOnSharedPreferenceChangeListener(globalListener)
        if (appearance !== global) appearance.registerOnSharedPreferenceChangeListener(localListener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        val (globalListener, localListener) = listeners.remove(listener) ?: return
        global.unregisterOnSharedPreferenceChangeListener(globalListener)
        if (appearance !== global) appearance.unregisterOnSharedPreferenceChangeListener(localListener)
    }
}
