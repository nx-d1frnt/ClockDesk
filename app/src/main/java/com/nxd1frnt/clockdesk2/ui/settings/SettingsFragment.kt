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
        val cityPref = findPreference<androidx.preference.Preference>("location_city_name")
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

        cityPref?.setOnPreferenceClickListener {
            com.nxd1frnt.clockdesk2.ui.dialog.CitySearchDialog.show(requireContext()) { selected ->
                prefs.edit()
                    .putString("location_mode", "city")
                    .putString("location_city_name", selected.name)
                    .putString("resolved_latitude", selected.latitude.toString())
                    .putString("resolved_longitude", selected.longitude.toString())
                    .putString("resolved_city_display_name", selected.displayName)
                    .apply()

                cityPref.summary = selected.displayName
                toast(getString(R.string.city_resolved_format, selected.displayName, selected.latitude, selected.longitude))
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
    }
}

class DisplaySettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_display, rootKey)

        val modePref = findPreference<androidx.preference.ListPreference>("brightness_mode")
        val minBrightPref = findPreference<androidx.preference.SeekBarPreference>("smart_night_min_brightness")
        val luxPref = findPreference<androidx.preference.SeekBarPreference>("smart_night_lux_threshold")

        fun updateStates(mode: String?) {
            val isSmartNight = mode == "smart_night"
            minBrightPref?.isEnabled = isSmartNight
            luxPref?.isEnabled = isSmartNight
        }

        updateStates(modePref?.value)
        modePref?.setOnPreferenceChangeListener { _, newValue ->
            updateStates(newValue as? String)
            true
        }
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
            val includeDevices = preferenceManager.sharedPreferences?.getBoolean("backup_connected_devices", true) ?: true
            val success = SettingsBackupManager.exportSettings(requireContext(), it, includeDevices)
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

class UpdatesSettingsFragment : PreferenceFragmentCompat() {

    private var currentVersionPref: Preference? = null
    private var statusPref: Preference? = null
    private var checkUpdatesPref: Preference? = null
    private var installUpdatePref: Preference? = null
    private var nightlyChannelPref: SwitchPreferenceCompat? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_updates, rootKey)

        currentVersionPref = findPreference("update_current_version")
        statusPref = findPreference("update_status")
        checkUpdatesPref = findPreference("check_updates_now")
        installUpdatePref = findPreference("install_update_now")
        nightlyChannelPref = findPreference("update_nightly_channel")

        val isNightlyBuild = com.nxd1frnt.clockdesk2.BuildConfig.VERSION_NAME.contains("nightly", ignoreCase = true)
        val prefs = requireContext().getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)

        // Ensure default reflects current build if not set
        if (!prefs.contains(com.nxd1frnt.clockdesk2.utils.UpdateManager.KEY_UPDATE_NIGHTLY_CHANNEL)) {
            nightlyChannelPref?.isChecked = isNightlyBuild
            prefs.edit().putBoolean(com.nxd1frnt.clockdesk2.utils.UpdateManager.KEY_UPDATE_NIGHTLY_CHANNEL, isNightlyBuild).apply()
        }

        currentVersionPref?.summary = com.nxd1frnt.clockdesk2.BuildConfig.VERSION_NAME

        nightlyChannelPref?.setOnPreferenceChangeListener { _, newValue ->
            val isNightly = newValue as? Boolean ?: false
            prefs.edit().putBoolean(com.nxd1frnt.clockdesk2.utils.UpdateManager.KEY_UPDATE_NIGHTLY_CHANNEL, isNightly).apply()
            com.nxd1frnt.clockdesk2.utils.UpdateManager.checkForUpdates(requireContext(), force = true)
            true
        }

        checkUpdatesPref?.setOnPreferenceClickListener {
            com.nxd1frnt.clockdesk2.utils.UpdateManager.checkForUpdates(requireContext(), force = true)
            true
        }

        installUpdatePref?.setOnPreferenceClickListener {
            com.nxd1frnt.clockdesk2.utils.UpdateManager.downloadAndInstall(requireContext())
            true
        }
    }

    override fun onResume() {
        super.onResume()
        com.nxd1frnt.clockdesk2.utils.UpdateManager.onUpdateStateChanged = {
            activity?.runOnUiThread {
                updateUI()
            }
        }
        updateUI()
    }

    override fun onPause() {
        super.onPause()
        com.nxd1frnt.clockdesk2.utils.UpdateManager.onUpdateStateChanged = null
    }

    private fun updateUI() {
        val ctx = context ?: return
        val lastCheck = com.nxd1frnt.clockdesk2.utils.UpdateManager.getLastCheckTime(ctx)

        if (lastCheck > 0L) {
            val dateStr = android.text.format.DateFormat.getMediumDateFormat(ctx).format(java.util.Date(lastCheck))
            val timeStr = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(lastCheck))
            checkUpdatesPref?.summary = getString(R.string.check_for_updates_last_checked, "$dateStr $timeStr")
        } else {
            checkUpdatesPref?.summary = getString(R.string.check_for_updates_never)
        }

        val isUpdateAvail = com.nxd1frnt.clockdesk2.utils.UpdateManager.isUpdateAvailable
        val downloadState = com.nxd1frnt.clockdesk2.utils.UpdateManager.downloadState

        if (isUpdateAvail) {
            installUpdatePref?.isVisible = true
            when (downloadState) {
                com.nxd1frnt.clockdesk2.utils.UpdateManager.DownloadState.DOWNLOADING -> {
                    val percent = com.nxd1frnt.clockdesk2.utils.UpdateManager.downloadProgress
                    val downloadedStr = com.nxd1frnt.clockdesk2.utils.UpdateManager.formatBytes(com.nxd1frnt.clockdesk2.utils.UpdateManager.downloadedBytes)
                    val totalStr = com.nxd1frnt.clockdesk2.utils.UpdateManager.formatBytes(com.nxd1frnt.clockdesk2.utils.UpdateManager.totalBytes)
                    val progressText = getString(R.string.downloading_update_progress, percent, downloadedStr, totalStr)

                    statusPref?.summary = progressText
                    installUpdatePref?.title = getString(R.string.downloading_update_title)
                    installUpdatePref?.summary = "$percent%"
                    installUpdatePref?.isEnabled = false
                }
                com.nxd1frnt.clockdesk2.utils.UpdateManager.DownloadState.READY_TO_INSTALL -> {
                    statusPref?.summary = getString(R.string.update_ready_to_install)
                    installUpdatePref?.title = getString(R.string.install_update_action)
                    installUpdatePref?.summary = com.nxd1frnt.clockdesk2.utils.UpdateManager.apkFileName
                    installUpdatePref?.isEnabled = true
                }
                com.nxd1frnt.clockdesk2.utils.UpdateManager.DownloadState.FAILED -> {
                    statusPref?.summary = getString(R.string.download_failed)
                    installUpdatePref?.title = getString(R.string.download_and_install_title)
                    installUpdatePref?.summary = getString(R.string.download_failed)
                    installUpdatePref?.isEnabled = true
                }
                com.nxd1frnt.clockdesk2.utils.UpdateManager.DownloadState.IDLE -> {
                    val version = com.nxd1frnt.clockdesk2.utils.UpdateManager.latestVersion ?: ""
                    statusPref?.summary = getString(R.string.update_status_available, version)
                    installUpdatePref?.title = getString(R.string.download_and_install_title)
                    installUpdatePref?.summary = getString(R.string.download_and_install_summary)
                    installUpdatePref?.isEnabled = true
                }
            }
        } else {
            installUpdatePref?.isVisible = false
            when {
                com.nxd1frnt.clockdesk2.utils.UpdateManager.isChecking -> {
                    statusPref?.summary = getString(R.string.update_status_checking)
                }
                com.nxd1frnt.clockdesk2.utils.UpdateManager.lastError != null -> {
                    statusPref?.summary = getString(R.string.update_status_error)
                }
                lastCheck > 0L -> {
                    statusPref?.summary = getString(R.string.update_status_up_to_date)
                }
                else -> {
                    statusPref?.summary = getString(R.string.check_for_updates_never)
                }
            }
        }
    }
}

