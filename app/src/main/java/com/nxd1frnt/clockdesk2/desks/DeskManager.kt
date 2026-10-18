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

    fun getStartDeskId(): String? = prefs.getString("start_desk_id", null)
        ?.takeIf { id -> desks.any { it.id == id } }

    fun getInitialDeskId(): String = getStartDeskId()
        ?: prefs.getString("last_selected_desk_id", null)?.takeIf { id -> desks.any { it.id == id } }
        ?: getDesks().first().id

    fun recordSelectedDesk(id: String) {
        if (getDeskById(id) != null) prefs.edit().putString("last_selected_desk_id", id).apply()
    }

    fun setStartDesk(id: String?) {
        if (id != null && getDeskById(id) == null) return
        prefs.edit().putString("start_desk_id", id).apply()
        onDesksChanged?.invoke()
    }

    fun renameDesk(id: String, title: String): Boolean {
        val desk = getDeskById(id) ?: return false
        val normalized = title.trim().take(80)
        if (normalized.isEmpty()) return false
        updateDesk(desk.copy(title = normalized))
        return true
    }

    @Synchronized
    fun duplicateDesk(id: String): DeskItem? {
        val source = getDeskById(id) ?: return null
        val newId = "desk_${source.type.typeId}_${UUID.randomUUID().toString().take(8)}"
        val newDesk = source.copy(
            id = newId,
            title = context.getString(R.string.desk_copy_title, source.title),
            order = (desks.maxOfOrNull { it.order } ?: -1) + 1,
            widgetLayoutKey = "active_widgets_$newId"
        )
        if (source.type == DeskType.WIDGETS) {
            val layouts = context.getSharedPreferences("DesktopLayout", Context.MODE_PRIVATE)
            val layout = layouts.getString(source.widgetLayoutKey, null) ?: "[]"
            layouts.edit().putString(newDesk.widgetLayoutKey, layout).apply()
            val positions = context.getSharedPreferences(
                com.nxd1frnt.clockdesk2.widgets.DesktopWidgetManager.positionPreferencesName(source.widgetLayoutKey), Context.MODE_PRIVATE
            )
            val copiedPositions = context.getSharedPreferences(
                com.nxd1frnt.clockdesk2.widgets.DesktopWidgetManager.positionPreferencesName(newDesk.widgetLayoutKey), Context.MODE_PRIVATE
            ).edit()
            positions.all.forEach { (key, value) -> DeskAppearancePreferences.putValue(copiedPositions, key, value) }
            copiedPositions.apply()
            DeskAppearancePreferences.copyProfile(context, source.widgetLayoutKey, newDesk.widgetLayoutKey)
        }
        desks.add(newDesk)
        saveDesks()
        onDesksChanged?.invoke()
        return newDesk
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
        val widgetKey = "active_widgets_${id}"

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
            if (prefs.getString("start_desk_id", null) == id) prefs.edit().remove("start_desk_id").apply()
            if (prefs.getString("last_selected_desk_id", null) == id) {
                prefs.edit().putString("last_selected_desk_id", desks.first().id).apply()
            }
            saveDesks()
            onDesksChanged?.invoke()
        }
        return removed
    }

    @Synchronized
    fun reorderDesks(reorderedDesks: List<DeskItem>) {
        if (reorderedDesks.size != desks.size || reorderedDesks.map { it.id }.toSet() != desks.map { it.id }.toSet()) return
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
                    val fallbackWidgetKey = if (id == "desk_widgets_main") {
                        "active_widgets_json"
                    } else {
                        "active_widgets_$id"
                    }
                    val widgetLayoutKey = obj.optString("widgetLayoutKey", fallbackWidgetKey)
                        .ifBlank { fallbackWidgetKey }

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
            // Older configurations could assign the legacy key to several desks.
            // Keep the original layout on its owner and isolate the other desks.
            val widgetDesks = desks.filter { it.type == DeskType.WIDGETS }
                .sortedBy { if (it.id == "desk_widgets_main") 0 else 1 }
            val usedKeys = mutableSetOf<String>()
            var keysChanged = false
            widgetDesks.forEach { desk ->
                if (!usedKeys.add(desk.widgetLayoutKey)) {
                    desk.widgetLayoutKey = "active_widgets_${desk.id}"
                    usedKeys.add(desk.widgetLayoutKey)
                    keysChanged = true
                }
            }
            if (keysChanged) saveDesks()
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
