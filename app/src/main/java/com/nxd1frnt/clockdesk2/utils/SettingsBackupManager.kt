package com.nxd1frnt.clockdesk2.utils

import android.content.Context
import android.net.Uri
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import org.json.JSONArray
import org.json.JSONObject

object SettingsBackupManager {

    private val PREF_FILES = listOf(
        BackgroundManager.PREFS_NAME,
        "WidgetPositions",
        "update_prefs",
        "ClockDeskPrefs"
    )

    fun exportSettings(context: Context, uri: Uri): Boolean {
        return try {
            val root = JSONObject()

            for (prefFile in PREF_FILES) {
                val prefs = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)
                val prefJson = JSONObject()

                for ((key, value) in prefs.all) {
                    val item = JSONObject()
                    // Явно сохраняем тип данных, чтобы избежать ClassCastException при импорте
                    when (value) {
                        is Boolean -> item.put("type", "boolean").put("value", value)
                        is Int -> item.put("type", "int").put("value", value)
                        is Long -> item.put("type", "long").put("value", value)
                        is Float -> item.put("type", "float").put("value", value.toDouble())
                        is String -> item.put("type", "string").put("value", value)
                        is Set<*> -> {
                            val array = JSONArray()
                            value.forEach { array.put(it) }
                            item.put("type", "set").put("value", array)
                        }
                    }
                    prefJson.put(key, item)
                }
                root.put(prefFile, prefJson)
            }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                // Указываем кодировку явно для надежности
                outputStream.write(root.toString(4).toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            Logger.e("SettingsBackupManager") { "Export failed: ${e.message}" }
            false
        }
    }

    fun importSettings(context: Context, uri: Uri): Boolean {
        return try {
            // Идиоматичное чтение потока в Kotlin (без циклов while)
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                it.readText()
            } ?: return false

            val root = JSONObject(content)

            for (prefFile in PREF_FILES) {
                if (!root.has(prefFile)) continue

                val prefJson = root.getJSONObject(prefFile)
                val prefs = context.getSharedPreferences(prefFile, Context.MODE_PRIVATE)
                val editor = prefs.edit()

                editor.clear() // Очищаем старые данные перед восстановлением

                val keys = prefJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val item = prefJson.optJSONObject(key) ?: continue

                    val type = item.optString("type")
                    when (type) {
                        "boolean" -> editor.putBoolean(key, item.getBoolean("value"))
                        "int" -> editor.putInt(key, item.getInt("value"))
                        "long" -> editor.putLong(key, item.getLong("value"))
                        "float" -> editor.putFloat(key, item.getDouble("value").toFloat())
                        "string" -> editor.putString(key, item.getString("value"))
                        "set" -> {
                            val array = item.getJSONArray("value")
                            val set = mutableSetOf<String>()
                            for (i in 0 until array.length()) {
                                set.add(array.getString(i))
                            }
                            editor.putStringSet(key, set)
                        }
                    }
                }
                // Используем commit() вместо apply()
                // Гарантируем синхронную запись на диск до того, как метод вернет true
                // Это важно, так как после восстановления UI часто перезапускается
                editor.commit()
            }
            true
        } catch (e: Exception) {
            Logger.e("SettingsBackupManager") { "Import failed: ${e.message}" }
            false
        }
    }
}