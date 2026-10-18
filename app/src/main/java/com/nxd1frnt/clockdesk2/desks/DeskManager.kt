package com.nxd1frnt.clockdesk2.desks

import android.content.Context
import android.content.SharedPreferences
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.Logger
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class DeskManager(private val context: Context) {
    private val TAG = "DeskManager"
    private val prefs: SharedPreferences =
        context.getSharedPreferences("DesksConfig", Context.MODE_PRIVATE)

    private val desks = mutableListOf<DeskItem>()

    var onDesksChanged: (() -> Unit)? = null

    init {
        loadDesks()
    }

    @Synchronized
    fun getDesks(): List<DeskItem> {
        return desks.sortedBy { it.order }.toList()
    }

    @Synchronized
    fun getDeskById(id: String): DeskItem? {
        return desks.firstOrNull { it.id == id }
    }

    @Synchronized
    fun addDesk(
        type: DeskType,
        customTitle: String? = null,
        customUrl: String? = null
    ): DeskItem {
        val title = customTitle ?: context.getString(type.titleResId)
        val id = "desk_${type.typeId}_${UUID.randomUUID().toString().take(8)}"
        val maxOrder = desks.maxOfOrNull { it.order } ?: -1
        val widgetKey = if (type == DeskType.WIDGETS && desks.none { it.type == DeskType.WIDGETS }) {
            "active_widgets_json"
        } else {
            "active_widgets_${id}"
        }

        val newDesk = DeskItem(
            id = id,
            type = type,
            title = title,
            order = maxOrder + 1,
            customUrl = customUrl ?: if (type == DeskType.WEB_KIOSK) "https://www.google.com" else "",
            autoRefreshMinutes = 0,
            widgetLayoutKey = widgetKey
        )

        desks.add(newDesk)
        saveDesks()
        onDesksChanged?.invoke()
        return newDesk
    }

    @Synchronized
    fun removeDesk(id: String): Boolean {
        if (desks.size <= 1) {
            // Do not delete the last remaining desk
            return false
        }
        val removed = desks.removeAll { it.id == id }
        if (removed) {
            // Re-normalize order
            desks.sortBy { it.order }
            desks.forEachIndexed { index, desk -> desk.order = index }
            saveDesks()
            onDesksChanged?.invoke()
        }
        return removed
    }

    @Synchronized
    fun reorderDesks(reorderedDesks: List<DeskItem>) {
        desks.clear()
        reorderedDesks.forEachIndexed { index, desk ->
            desk.order = index
            desks.add(desk)
        }
        saveDesks()
        onDesksChanged?.invoke()
    }

    @Synchronized
    fun updateDesk(updatedDesk: DeskItem) {
        val index = desks.indexOfFirst { it.id == updatedDesk.id }
        if (index != -1) {
            desks[index] = updatedDesk
            saveDesks()
            onDesksChanged?.invoke()
        }
    }

    @Synchronized
    private fun loadDesks() {
        desks.clear()
        val jsonString = prefs.getString("desks_list_json", null)
        if (!jsonString.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val typeId = obj.getString("type")
                    val type = DeskType.fromTypeId(typeId)
                    val title = obj.optString("title", context.getString(type.titleResId))
                    val order = obj.optInt("order", i)
                    val customUrl = obj.optString("customUrl", "")
                    val autoRefreshMinutes = obj.optInt("autoRefreshMinutes", 0)
                    val widgetLayoutKey = obj.optString("widgetLayoutKey", "active_widgets_json")

                    desks.add(
                        DeskItem(
                            id = id,
                            type = type,
                            title = title,
                            order = order,
                            customUrl = customUrl,
                            autoRefreshMinutes = autoRefreshMinutes,
                            widgetLayoutKey = widgetLayoutKey
                        )
                    )
                }
            } catch (e: Exception) {
                Logger.e(TAG) { "Failed to parse desks_list_json: ${e.message}" }
            }
        }

        if (desks.isEmpty()) {
            // Default setup: primary widget desk
            val defaultDesk = DeskItem(
                id = "desk_widgets_main",
                type = DeskType.WIDGETS,
                title = context.getString(R.string.desk_type_widgets),
                order = 0,
                widgetLayoutKey = "active_widgets_json"
            )
            desks.add(defaultDesk)
            saveDesks()
        } else {
            desks.sortBy { it.order }
        }
    }

    @Synchronized
    private fun saveDesks() {
        try {
            val array = JSONArray()
            for (desk in desks) {
                val obj = JSONObject().apply {
                    put("id", desk.id)
                    put("type", desk.type.typeId)
                    put("title", desk.title)
                    put("order", desk.order)
                    put("customUrl", desk.customUrl)
                    put("autoRefreshMinutes", desk.autoRefreshMinutes)
                    put("widgetLayoutKey", desk.widgetLayoutKey)
                }
                array.put(obj)
            }
            prefs.edit().putString("desks_list_json", array.toString()).apply()
            Logger.d(TAG) { "Saved ${desks.size} desks" }
        } catch (e: Exception) {
            Logger.e(TAG) { "Failed to save desks: ${e.message}" }
        }
    }
}
