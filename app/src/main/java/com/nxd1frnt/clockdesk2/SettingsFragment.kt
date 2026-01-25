package com.nxd1frnt.clockdesk2

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.nxd1frnt.clockdesk2.smartchips.ChipPluginContract
import com.nxd1frnt.clockdesk2.utils.Logger
import org.xmlpull.v1.XmlPullParser

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferenceManager = preferenceManager
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.preferences, rootKey)
        // disable album art background option if android version < 4.4
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
            val albumArtBackgroundPref =
                findPreference<androidx.preference.SwitchPreference>("lastfm_albumart_background")
            albumArtBackgroundPref?.isEnabled = false
            albumArtBackgroundPref?.isChecked = false
            albumArtBackgroundPref?.summary = "Sadly not supported on your Android version."
        }
        val smartChipCategory = findPreference<PreferenceCategory>("smart_chips_category")

        if (smartChipCategory == null) {
            Logger.e("SettingsFragment"){"Could not find 'smart_chips_category' in preferences.xml"}
            return // If category doesn't exist, stop
        }

        // 3. Discover external plugins and add them to the category
        discoverAndAddExternalPlugins(smartChipCategory)
    }
    private fun discoverAndAddExternalPlugins(category: PreferenceCategory) {
        val pm = requireContext().packageManager
        val queryIntent = Intent(ChipPluginContract.ACTION_QUERY_PLUGINS)

        // Query the package manager for all broadcast receivers that match our action
        val receivers = pm.queryBroadcastReceivers(queryIntent, PackageManager.GET_META_DATA)

        Logger.d("SettingsFragment"){"Found ${receivers.size} potential plugins."}

        for (resolveInfo in receivers) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val metaData = activityInfo.metaData ?: continue
            val packageName = activityInfo.packageName

            // Check if the receiver has the required meta-data
            if (metaData.containsKey(ChipPluginContract.META_DATA_PLUGIN_INFO)) {
                val resId = metaData.getInt(ChipPluginContract.META_DATA_PLUGIN_INFO)
                try {
                    // Get resources from the *external plugin's* package
                    val pluginRes = pm.getResourcesForApplication(packageName)
                    val parser = pluginRes.getXml(resId)

                    var prefKey: String? = null
                    var dispName: String? = null

                    // Parse the plugin_info.xml
                    while (parser.next() != XmlPullParser.END_DOCUMENT) {
                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "smart-chip-plugin") {
                            prefKey = parser.getAttributeValue(null, "preferenceKey")
                            dispName = parser.getAttributeValue(null, "displayName")
                            break // Found what we need
                        }
                    }

                    if (prefKey != null && dispName != null) {
                        // 4. Create a new SwitchPreference for this plugin
                        val pluginPref = SwitchPreferenceCompat(requireContext()).apply {
                            key = prefKey
                            title = dispName
                            summary = "External Plugin"
                            setDefaultValue(false) // Default to off
                        }

                        // 5. Add the new preference to our category
                        category.addPreference(pluginPref)
                        Logger.d("SettingsFragment"){"Added plugin to settings: $dispName ($prefKey)"}
                    }
                } catch (e: Exception) {
                    Logger.e("SettingsFragment"){"Failed to parse plugin info from $packageName: ${e.message}"}
                }
            }
        }
    }
}