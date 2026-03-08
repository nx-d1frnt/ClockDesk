package com.nxd1frnt.clockdesk2.smartchips.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.shared.ui.DraggablePluginAdapter
import com.nxd1frnt.clockdesk2.shared.ui.PluginUiModel
import com.nxd1frnt.clockdesk2.smartchips.ChipPluginContract
import com.nxd1frnt.clockdesk2.utils.Logger
import org.xmlpull.v1.XmlPullParser
import java.util.Collections

class SmartChipsPluginsFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DraggablePluginAdapter

    private data class ChipDef(
        val id: String,
        val packageName: String,
        val name: String,
        val desc: String,
        val isInternal: Boolean,
        val iconDrawable: android.graphics.drawable.Drawable? = null,
        val settingsActivityClassName: String? = null
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(context)
            clipToPadding = false
            setPadding(0, 16, 0, 16)
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val availableChips = loadAvailableChips()

        val savedOrderString = prefs.getString(
            "smart_chip_order",
            "system_bg_progress,show_battery_alert,show_updates"
        ) ?: ""
        val savedOrderList = savedOrderString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        Logger.d("SmartChipsFragment") { "Saved order: $savedOrderList" }
        val finalIdList = ArrayList<String>()


        for (id in savedOrderList) {
            if (availableChips.containsKey(id)) {
                finalIdList.add(id)
            }
        }

        //New plugins go to the bottom of the list
        for (id in availableChips.keys) {
            if (!finalIdList.contains(id)) {
                finalIdList.add(id)
            }
        }

        val items = finalIdList.mapNotNull { id ->
            val info = availableChips[id] ?: return@mapNotNull null
            val isEnabled = prefs.getBoolean(id, false)

            //Detect if plugin is external
            val isExternalPlugin = !info.isInternal

            PluginUiModel(
                id = info.id,
                name = info.name,
                // If plugin description is empty or null, use a default string
                desc = info.desc.ifEmpty { getString(R.string.external_plugin_desc) },
                settingsActivityClassName = info.settingsActivityClassName,
                isEnabled = isEnabled,
                iconDrawable = info.iconDrawable, // Передаем иконку, полученную через PackageManager
                isExternal = isExternalPlugin     // Передаем флаг для адаптера
            )
        }.toMutableList()

        //Save new plugin order
        val newOrderString = items.joinToString(",") { it.id }
        if (newOrderString != savedOrderString) {
            prefs.edit().putString("smart_chip_order", newOrderString).apply()
        }

        setupAdapterAndGestures(items, prefs)
    }

    private fun setupAdapterAndGestures(items: MutableList<PluginUiModel>, prefs: android.content.SharedPreferences) {
        adapter = DraggablePluginAdapter(
            items = items,
            onClick = { pluginId -> // pluginId = preferenceKey
                // Launch the settings activity for the plugin
                val info = loadAvailableChips()[pluginId] // Get chip info by pluginId

                val settingsClass = info?.settingsActivityClassName
                if (info != null && settingsClass != null) {
                    try {
                        // Launch the settings activity
                        val fullClassName = if (settingsClass.startsWith(".")) {
                            info.packageName + settingsClass
                        } else {
                            settingsClass
                        }

                        val intent = Intent().apply {
                            component = ComponentName(info.packageName, fullClassName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Logger.e("SmartChipsFragment") { "Error launching settings for ${info.packageName}: ${e.message}" }
                        // Show toast if settings activity is not found
                        android.widget.Toast.makeText(context, R.string.error_opening_settings, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSwitchChanged = { pluginId, isEnabled ->
                prefs.edit().putBoolean(pluginId, isEnabled).apply()
            }
        )
        recyclerView.adapter = adapter

        // Dragging functionality
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = source.adapterPosition
                val toPos = target.adapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false

                Collections.swap(items, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)

                // Save new order
                val newOrder = items.joinToString(",") { it.id }
                prefs.edit().putString("smart_chip_order", newOrder).apply()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

    }

    private fun loadAvailableChips(): Map<String, ChipDef> {
        val map = mutableMapOf<String, ChipDef>()

        // Internal chips registration
        map["show_updates"] = ChipDef(
            id = "show_updates",
            packageName = requireContext().packageName,
            name = getString(R.string.show_updates_chip),
            desc = getString(R.string.show_updates_chip_summary),
            isInternal = true,
            iconDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.update)
        )
        map["show_battery_alert"] = ChipDef(
            id = "show_battery_alert",
            packageName = requireContext().packageName,
            name = getString(R.string.show_battery_alert_chip),
            desc = getString(R.string.show_battery_alert_chip_summary),
            isInternal = true,
            iconDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_battery_alert)
        )
        map["system_bg_progress"] = ChipDef(
            id = "system_bg_progress",
            packageName = requireContext().packageName,
            name = getString(R.string.show_background_progress_chip),
            desc = getString(R.string.show_background_progress_chip_summary),
            isInternal = true,
            iconDrawable = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.image_refresh)
        )

        // External plugin discovery via BroadcastReceivers
        val pm = requireContext().packageManager
        val queryIntent = Intent(ChipPluginContract.ACTION_QUERY_PLUGINS)
        val receivers = pm.queryBroadcastReceivers(queryIntent, PackageManager.GET_META_DATA)

        for (resolveInfo in receivers) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val metaData = activityInfo.metaData ?: continue
            val packageName = activityInfo.packageName

            if (metaData.containsKey(ChipPluginContract.META_DATA_PLUGIN_INFO)) {
                val resId = metaData.getInt(ChipPluginContract.META_DATA_PLUGIN_INFO)
                try {
                    val pluginRes = pm.getResourcesForApplication(packageName)
                    val parser = pluginRes.getXml(resId)

                    var prefKey: String? = null
                    var dispName: String? = null
                    var description = getString(R.string.external_plugin_desc)
                    var iconDrawable: android.graphics.drawable.Drawable? = null
                    var settingsActivity: String? = null

                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "smart-chip-plugin") {

                            for (i in 0 until parser.attributeCount) {
                                val attrName = parser.getAttributeName(i)
                                val resId = parser.getAttributeResourceValue(i, 0)
                                val rawValue = parser.getAttributeValue(i)

                                when (attrName) {
                                    "preferenceKey" -> prefKey = rawValue
                                    "settingsActivity" -> settingsActivity = rawValue
                                    "displayName" -> {
                                        dispName = if (resId != 0) {
                                            try { pluginRes.getString(resId) } catch (e: Exception) { rawValue }
                                        } else if (rawValue?.startsWith("@") == true) {
                                            try { pluginRes.getString(rawValue.substring(1).toInt()) } catch (e: Exception) { rawValue }
                                        } else {
                                            rawValue
                                        }
                                    }
                                    "description" -> {
                                        val parsedDesc = if (resId != 0) {
                                            try { pluginRes.getString(resId) } catch (e: Exception) { rawValue }
                                        } else if (rawValue?.startsWith("@") == true) {
                                            try { pluginRes.getString(rawValue.substring(1).toInt()) } catch (e: Exception) { rawValue }
                                        } else {
                                            rawValue
                                        }
                                        if (!parsedDesc.isNullOrBlank()) {
                                            description = parsedDesc
                                        }
                                    }
                                    "icon" -> {
                                        if (resId != 0) {
                                            try {
                                                iconDrawable = androidx.core.content.res.ResourcesCompat.getDrawable(pluginRes, resId, null)
                                            } catch (e: Exception) {
                                                Logger.w("SmartChipsFragment") { "Could not load icon for $packageName" }
                                            }
                                        }
                                    }
                                }
                            }
                            break
                        }
                    }

                    if (prefKey != null && dispName != null) {
                        map[prefKey] = ChipDef(
                            id = prefKey,
                            packageName = packageName,
                            name = dispName,
                            desc = description,
                            isInternal = false,
                            iconDrawable = iconDrawable ?: resolveInfo.loadIcon(pm), // If icon is not found, use default icon
                            settingsActivityClassName = settingsActivity // Pass a settings activity class name
                        )
                    }
                } catch (e: Exception) {
                    Logger.e("SmartChipsFragment") { "Failed to parse plugin info from $packageName" }
                }
            }
        }
        Logger.d("SmartChipsFragment") { "Available chips: $map" }
        return map
    }
}