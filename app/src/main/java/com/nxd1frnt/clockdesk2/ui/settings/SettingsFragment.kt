package com.nxd1frnt.clockdesk2.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.SettingsBackupManager

class GeneralSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_general, rootKey)

        val prefs = requireContext().getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

        // Auto-migration from legacy useManualCoordinates setting
        if (!prefs.contains("location_mode")) {
            val legacyManual = prefs.getBoolean("useManualCoordinates", false)
            val mode = if (legacyManual) "coords" else "auto"
            prefs.edit().putString("location_mode", mode).apply()
        }

        val locationModePref = findPreference<androidx.preference.ListPreference>("location_mode")
        val cityPref = findPreference<androidx.preference.EditTextPreference>("location_city_name")
        val latPref = findPreference<androidx.preference.Preference>("latitude")
        val lonPref = findPreference<androidx.preference.Preference>("longitude")

        fun updateVisibility(mode: String?) {
            cityPref?.isVisible = (mode == "city")
            latPref?.isVisible = (mode == "coords")
            lonPref?.isVisible = (mode == "coords")
        }

        // Initialize visibility
        updateVisibility(locationModePref?.value)

        // Set summary display for city name based on already resolved details
        val resolvedName = prefs.getString("resolved_city_display_name", "")
        val savedCity = prefs.getString("location_city_name", "")
        if (!resolvedName.isNullOrBlank()) {
            cityPref?.summary = resolvedName
        } else if (!savedCity.isNullOrBlank()) {
            cityPref?.summary = savedCity
        }

        locationModePref?.setOnPreferenceChangeListener { _, newValue ->
            val newMode = newValue as? String
            updateVisibility(newMode)
            true
        }

        cityPref?.setOnPreferenceChangeListener { _, newValue ->
            val newCity = newValue as? String
            if (!newCity.isNullOrBlank()) {
                com.nxd1frnt.clockdesk2.utils.GeocodingHelper.geocodeCity(requireContext(), newCity,
                    onSuccess = { lat, lon, resolvedCity ->
                        prefs.edit()
                            .putString("resolved_latitude", lat.toString())
                            .putString("resolved_longitude", lon.toString())
                            .putString("resolved_city_display_name", resolvedCity)
                            .apply()
                        activity?.runOnUiThread {
                            cityPref.summary = resolvedCity
                            toast(getString(R.string.city_resolved_format, resolvedCity, lat, lon))
                        }
                    },
                    onError = { error ->
                        activity?.runOnUiThread {
                            toast(getString(R.string.city_resolve_error_format, newCity))
                        }
                    }
                )
            }
            true
        }
    }

    private fun toast(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }
}

class MusicSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_music, rootKey)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            val albumArtBackgroundPref =
                findPreference<SwitchPreferenceCompat>("lastfm_albumart_background")
            albumArtBackgroundPref?.isEnabled = false
            albumArtBackgroundPref?.isChecked = false
            albumArtBackgroundPref?.summary = getString(R.string.feature_not_supported)
        }
    }
}

class DisplaySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_display, rootKey)
    }
}

class BatterySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_battery, rootKey)
    }
}

class PerformanceSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_performance, rootKey)
    }
}

class BackupSettingsFragment : PreferenceFragmentCompat() {

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val success = SettingsBackupManager.exportSettings(requireContext(), it)
            if (success) {
                Toast.makeText(requireContext(), R.string.export_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.export_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            confirmRestore(it)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_backup, rootKey)

        findPreference<Preference>("export_settings")?.setOnPreferenceClickListener {
            exportLauncher.launch("clockdesk_settings.json")
            true
        }

        findPreference<Preference>("import_settings")?.setOnPreferenceClickListener {
            importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
            true
        }
    }

    private fun confirmRestore(uri: android.net.Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.confirm_restore_title)
            .setMessage(R.string.confirm_restore_message)
            .setPositiveButton(R.string.apply) { _, _ ->
                val success = SettingsBackupManager.importSettings(requireContext(), uri)
                if (success) {
                    Toast.makeText(requireContext(), R.string.import_success, Toast.LENGTH_LONG).show()
                    restartApp()
                } else {
                    Toast.makeText(requireContext(), R.string.import_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun restartApp() {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
