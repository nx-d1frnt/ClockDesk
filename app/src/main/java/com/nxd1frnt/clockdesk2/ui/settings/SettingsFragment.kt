package com.nxd1frnt.clockdesk2.ui.settings

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nxd1frnt.clockdesk2.BuildConfig
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.ui.MusicSourcesFragment
import com.nxd1frnt.clockdesk2.ui.dialog.CitySearchDialog
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem
import com.nxd1frnt.clockdesk2.utils.SettingsBackupManager
import com.nxd1frnt.clockdesk2.utils.UpdateManager
import java.util.Date

class GeneralSettingsFragment : BaseM3SettingsFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Auto-migration from legacy useManualCoordinates setting
        if (!prefs.contains("location_mode")) {
            val legacyManual = prefs.getBoolean("useManualCoordinates", false)
            val mode = if (legacyManual) "coords" else "auto"
            prefs.edit().putString("location_mode", mode).apply()
        }
    }

    override fun buildSettings(): List<SettingsItem> = settings {
        val currentMode = prefs.getString("location_mode", "auto") ?: "auto"

        category(getString(R.string.sun_sunrise_api_settings)) {
            choice(
                key = "location_mode",
                title = getString(R.string.location_mode_title),
                summary = getString(R.string.location_mode_summary),
                iconRes = R.drawable.ic_location,
                entries = resources.getStringArray(R.array.location_mode_entries),
                entryValues = resources.getStringArray(R.array.location_mode_values),
                defaultValue = "auto"
            )

            val resolvedName = prefs.getString("resolved_city_display_name", "")
            val savedCity = prefs.getString("location_city_name", "")
            val citySummary = when {
                !resolvedName.isNullOrBlank() -> resolvedName
                !savedCity.isNullOrBlank() -> savedCity
                else -> getString(R.string.location_city_name_title)
            }

            action(
                key = "location_city_name",
                title = getString(R.string.location_city_name_title),
                summary = citySummary,
                showChevron = true,
                isVisible = { prefs.getString("location_mode", "auto") == "city" }
            ) {
                CitySearchDialog.show(requireContext()) { selected ->
                    prefs.edit()
                        .putString("location_mode", "city")
                        .putString("location_city_name", selected.name)
                        .putString("resolved_latitude", selected.latitude.toString())
                        .putString("resolved_longitude", selected.longitude.toString())
                        .putString("resolved_city_display_name", selected.displayName)
                        .apply()

                    Toast.makeText(
                        context,
                        getString(R.string.city_resolved_format, selected.displayName, selected.latitude, selected.longitude),
                        Toast.LENGTH_LONG
                    ).show()
                    rebuildSettings()
                }
            }

            textEdit(
                key = "latitude",
                title = getString(R.string.latitude_label),
                dialogTitle = getString(R.string.enter_latitude),
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED,
                isVisible = { prefs.getString("location_mode", "auto") == "coords" }
            )

            textEdit(
                key = "longitude",
                title = getString(R.string.longitude),
                dialogTitle = getString(R.string.enter_longitude),
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED,
                isVisible = { prefs.getString("location_mode", "auto") == "coords" }
            )
        }

        category(getString(R.string.weather_settings_category_title)) {
            choice(
                key = "weather_refresh_interval",
                title = getString(R.string.weather_refresh_interval_title),
                summary = getString(R.string.weather_refresh_interval_summary),
                entries = resources.getStringArray(R.array.weather_refresh_entries),
                entryValues = resources.getStringArray(R.array.weather_refresh_values),
                defaultValue = "30"
            )
        }

        category(getString(R.string.pref_units_category_title)) {
            choice(
                key = "temperature_unit",
                title = getString(R.string.pref_temperature_unit_title),
                entries = resources.getStringArray(R.array.temperature_unit_entries),
                entryValues = resources.getStringArray(R.array.temperature_unit_values),
                defaultValue = "celsius"
            )

            choice(
                key = "wind_speed_unit",
                title = getString(R.string.pref_wind_speed_unit_title),
                entries = resources.getStringArray(R.array.wind_speed_unit_entries),
                entryValues = resources.getStringArray(R.array.wind_speed_unit_values),
                defaultValue = "kmh"
            )

            choice(
                key = "precipitation_unit",
                title = getString(R.string.pref_precipitation_unit_title),
                entries = resources.getStringArray(R.array.precipitation_unit_entries),
                entryValues = resources.getStringArray(R.array.precipitation_unit_values),
                defaultValue = "mm"
            )
        }
    }
}

class MusicSettingsFragment : BaseM3SettingsFragment() {
    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.music_settings_title)) {
            action(
                title = getString(R.string.music_sources_title),
                summary = getString(R.string.music_sources_summary),
                iconRes = R.drawable.ic_music_icon,
                showChevron = true
            ) {
                openSubFragment(MusicSourcesFragment())
            }
        }
    }
}

class DisplaySettingsFragment : BaseM3SettingsFragment() {
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var currentLux: Float? = null
    private var hasLightSensor: Boolean = true

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                val lux = event.values.getOrNull(0) ?: return
                currentLux = lux
                updateLightLevel(lux)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sm = requireContext().getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        sensorManager = sm
        lightSensor = sm?.getDefaultSensor(Sensor.TYPE_LIGHT)
        hasLightSensor = (lightSensor != null)
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let { sensor ->
            sensorManager?.registerListener(sensorEventListener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(sensorEventListener)
        super.onPause()
    }

    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.appearance)) {
            switch(
                key = "ui_monet_from_background",
                title = getString(R.string.ui_monet_from_background_title),
                summary = getString(R.string.ui_monet_from_background_summary),
                iconRes = R.drawable.ic_palette_swatch,
                defaultValue = true
            ) {
                activity?.recreate()
            }
        }

        category(getString(R.string.display_settings_title)) {
            action(
                key = "open_screensaver_settings",
                title = getString(R.string.screensaver_settings_title),
                summary = getString(R.string.screensaver_settings_summary),
                iconRes = R.drawable.ic_desktop,
                showChevron = true
            ) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_DREAM_SETTINGS))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), R.string.screensaver_settings_not_found, Toast.LENGTH_SHORT).show()
                }
            }

            switch(
                key = "burn_in_protection",
                title = getString(R.string.burn_in_protection_title),
                summary = getString(R.string.burn_in_protection_summary),
                iconRes = R.drawable.ic_blur_on,
                defaultValue = false
            )

            burnInPreview(
                isVisible = { prefs.getBoolean("burn_in_protection", false) }
            )

            switch(
                key = "smart_pixels_enabled",
                title = getString(R.string.smart_pixels_title),
                summary = getString(R.string.smart_pixels_summary),
                defaultValue = false,
                iconRes = R.drawable.ic_checkerboard
            )

            smartPixelsPreview(
                isVisible = { prefs.getBoolean("smart_pixels_enabled", false) }
            )
        }

        category(getString(R.string.smart_night_brightness_category)) {
            choice(
                key = "brightness_mode",
                title = getString(R.string.brightness_mode_title),
                summary = getString(R.string.brightness_mode_summary),
                entries = resources.getStringArray(R.array.brightness_mode_entries),
                entryValues = resources.getStringArray(R.array.brightness_mode_values),
                defaultValue = "system"
            )

            slider(
                key = "smart_night_min_brightness",
                title = getString(R.string.smart_night_min_brightness_title),
                summary = getString(R.string.smart_night_min_brightness_summary),
                min = 1f,
                max = 100f,
                step = 1f,
                defaultValue = 5f,
                valueFormatter = { "${it.toInt()}%" },
                isEnabled = { prefs.getString("brightness_mode", "system") == "smart_night" }
            )

            slider(
                key = "smart_night_lux_threshold",
                title = getString(R.string.smart_night_lux_threshold_title),
                summary = getString(R.string.smart_night_lux_threshold_summary),
                min = 1f,
                max = 50f,
                step = 1f,
                defaultValue = 5f,
                valueFormatter = { "${it.toInt()} lx" },
                isEnabled = { prefs.getString("brightness_mode", "system") == "smart_night" }
            )

            lightLevel(
                title = getString(R.string.current_light_level_title),
                summary = getString(R.string.current_light_level_summary),
                iconRes = R.drawable.ic_light_mode,
                luxValue = currentLux,
                hasSensor = hasLightSensor
            )
        }
    }
}

class BatterySettingsFragment : BaseM3SettingsFragment() {
    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.battery_saver_settings_title)) {
            batteryStatus(
                onToggleManual = {
                    rebuildSettings()
                }
            )
        }

        category(getString(R.string.power_saver_trigger_rules_category)) {
            switch(
                key = "power_saver_sync_system",
                title = getString(R.string.power_saver_sync_system_title),
                summary = getString(R.string.power_saver_sync_system_summary),
                defaultValue = true
            )

            switch(
                key = "automatic_battery_saver_mode",
                title = getString(R.string.automatic_battery_saver_mode_title),
                summary = getString(R.string.automatic_battery_saver_mode_summary),
                defaultValue = false
            )

            slider(
                key = "battery_saver_trigger",
                title = getString(R.string.battery_saver_trigger_title),
                summary = getString(R.string.battery_saver_trigger_summary),
                min = 5f,
                max = 90f,
                step = 1f,
                defaultValue = 15f,
                valueFormatter = { "${it.toInt()}%" },
                isEnabled = { prefs.getBoolean("automatic_battery_saver_mode", false) }
            )
        }

        category(getString(R.string.power_saver_display_category)) {
            switch(
                key = "power_saver_lock_brightness",
                title = getString(R.string.power_saver_lock_brightness_title),
                summary = getString(R.string.power_saver_lock_brightness_summary),
                defaultValue = true
            )

            slider(
                key = "power_saver_brightness_level",
                title = getString(R.string.power_saver_brightness_level_title),
                summary = getString(R.string.power_saver_brightness_level_summary),
                min = 1f,
                max = 100f,
                step = 1f,
                defaultValue = 1f,
                valueFormatter = { "${it.toInt()}%" },
                isEnabled = { prefs.getBoolean("power_saver_lock_brightness", true) }
            )

            switch(
                key = "power_saver_dim_background",
                title = getString(R.string.power_saver_dim_background_title),
                summary = getString(R.string.power_saver_dim_background_summary),
                defaultValue = true
            )

            slider(
                key = "power_saver_dim_level",
                title = getString(R.string.power_saver_dim_level_title),
                summary = getString(R.string.power_saver_dim_level_summary),
                min = 0f,
                max = 100f,
                step = 1f,
                defaultValue = 85f,
                valueFormatter = { "${it.toInt()}%" },
                isEnabled = { prefs.getBoolean("power_saver_dim_background", true) }
            )

            switch(
                key = "power_saver_enable_smart_pixels",
                title = getString(R.string.power_saver_enable_smart_pixels_title),
                summary = getString(R.string.power_saver_enable_smart_pixels_summary),
                defaultValue = true
            )
        }

        category(getString(R.string.power_saver_visual_effects_category)) {
            switch(
                key = "power_saver_disable_animations",
                title = getString(R.string.power_saver_disable_animations_title),
                summary = getString(R.string.power_saver_disable_animations_summary),
                defaultValue = true
            )

            switch(
                key = "power_saver_limit_fps",
                title = getString(R.string.power_saver_limit_fps_title),
                summary = getString(R.string.power_saver_limit_fps_summary),
                defaultValue = true
            )

            slider(
                key = "power_saver_fps_limit_value",
                title = getString(R.string.power_saver_fps_limit_value_title),
                summary = getString(R.string.power_saver_fps_limit_value_summary),
                min = 15f,
                max = 60f,
                step = 1f,
                defaultValue = 30f,
                valueFormatter = { "${it.toInt()} fps" },
                isEnabled = { prefs.getBoolean("power_saver_limit_fps", true) }
            )
        }

        category(getString(R.string.power_saver_background_activity_category)) {
            switch(
                key = "power_saver_limit_clock",
                title = getString(R.string.power_saver_limit_clock_title),
                summary = getString(R.string.power_saver_limit_clock_summary),
                defaultValue = true
            )

            switch(
                key = "power_saver_disable_weather",
                title = getString(R.string.power_saver_disable_weather_title),
                summary = getString(R.string.power_saver_disable_weather_summary),
                defaultValue = true
            )

            switch(
                key = "power_saver_disable_light_sensor",
                title = getString(R.string.power_saver_disable_light_sensor_title),
                summary = getString(R.string.power_saver_disable_light_sensor_summary),
                defaultValue = true
            )
        }
    }
}

class PerformanceSettingsFragment : BaseM3SettingsFragment() {
    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.graphics_visual_effects_category)) {
            switch(
                key = "advanced_graphics",
                title = getString(R.string.enable_advanced_graphics),
                summary = getString(R.string.enable_advanced_graphics_summary),
                iconRes = R.drawable.ic_speedometer,
                defaultValue = false
            )

            val advGraphics = prefs.getBoolean("advanced_graphics", false)

            switch(
                key = "graphics_enable_transitions",
                title = getString(R.string.graphics_enable_transitions_title),
                summary = getString(R.string.graphics_enable_transitions_summary),
                defaultValue = true,
                isEnabled = { advGraphics }
            )

            val transitionsEnabled = advGraphics && prefs.getBoolean("graphics_enable_transitions", true)

            choice(
                key = "graphics_transition_style",
                title = getString(R.string.graphics_transition_style_title),
                summary = getString(R.string.graphics_transition_style_summary),
                entries = resources.getStringArray(R.array.graphics_transition_style_entries),
                entryValues = resources.getStringArray(R.array.graphics_transition_style_values),
                defaultValue = "liquid_chromatic",
                isEnabled = { transitionsEnabled }
            )

            switch(
                key = "graphics_enable_turbulence",
                title = getString(R.string.graphics_enable_turbulence_title),
                summary = getString(R.string.graphics_enable_turbulence_summary),
                defaultValue = true,
                isEnabled = { advGraphics }
            )

            val turbulence = advGraphics && prefs.getBoolean("graphics_enable_turbulence", true)

            turbulencePreview(
                isVisible = { turbulence }
            )

            slider(
                key = "graphics_turbulence_grid",
                title = getString(R.string.graphics_turbulence_grid_title),
                summary = getString(R.string.graphics_turbulence_grid_summary),
                min = 5f,
                max = 50f,
                step = 1f,
                defaultValue = 10f,
                isEnabled = { turbulence }
            )

            slider(
                key = "graphics_turbulence_speed",
                title = getString(R.string.graphics_turbulence_speed_title),
                summary = getString(R.string.graphics_turbulence_speed_summary),
                min = 1f,
                max = 30f,
                step = 1f,
                defaultValue = 10f,
                isEnabled = { turbulence }
            )

            switch(
                key = "graphics_turbulence_music_continuous",
                title = getString(R.string.graphics_turbulence_music_continuous_title),
                summary = getString(R.string.graphics_turbulence_music_continuous_summary),
                defaultValue = false,
                isEnabled = { turbulence }
            )

            switch(
                key = "graphics_enable_edit_blur",
                title = getString(R.string.graphics_enable_edit_blur_title),
                summary = getString(R.string.graphics_enable_edit_blur_summary),
                defaultValue = true,
                isEnabled = { advGraphics }
            )
        }

        category(getString(R.string.graphics_optimization_category)) {
            slider(
                key = "graphics_render_scale",
                title = getString(R.string.graphics_render_scale_title),
                summary = getString(R.string.graphics_render_scale_summary),
                min = 10f,
                max = 100f,
                step = 1f,
                defaultValue = 100f,
                valueFormatter = { "${it.toInt()}%" }
            )

            slider(
                key = "graphics_weather_scale",
                title = getString(R.string.graphics_weather_scale_title),
                summary = getString(R.string.graphics_weather_scale_summary),
                min = 10f,
                max = 100f,
                step = 1f,
                defaultValue = 40f,
                valueFormatter = { "${it.toInt()}%" }
            )
        }

        category(getString(R.string.performance_diagnostics_category)) {
            switch(
                key = "show_performance_overlay",
                title = getString(R.string.show_performance_overlay_title),
                summary = getString(R.string.show_performance_overlay_summary),
                defaultValue = false
            )

            switch(
                key = "additional_logging",
                title = getString(R.string.enable_additional_logging),
                summary = getString(R.string.enable_additional_logging_summary),
                defaultValue = false
            )
        }
    }
}

class BackupSettingsFragment : BaseM3SettingsFragment() {

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            val includeDevices = prefs.getBoolean("backup_connected_devices", true)
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

    override fun buildSettings(): List<SettingsItem> = settings {
        category(getString(R.string.backup_restore_title)) {
            switch(
                key = "backup_connected_devices",
                title = getString(R.string.backup_connected_devices_title),
                summary = getString(R.string.backup_connected_devices_summary),
                iconRes = R.drawable.ic_backup_restore,
                defaultValue = true
            )

            action(
                key = "export_settings",
                title = getString(R.string.export_settings),
                showChevron = true
            ) {
                exportLauncher.launch("clockdesk_settings.json")
            }

            action(
                key = "import_settings",
                title = getString(R.string.import_settings),
                showChevron = true
            ) {
                importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
            }
        }
    }

    private fun confirmRestore(uri: android.net.Uri) {
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ClockDesk_Dialog_Theme)
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
        com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager.styleAlertDialog(dialog)
    }

    private fun restartApp() {
        val intent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}

class UpdatesSettingsFragment : BaseM3SettingsFragment() {

    private var notesExpanded = false
    private val updateListener: () -> Unit = {
        activity?.runOnUiThread { if (view != null) rebuildSettings() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isNightlyBuild = BuildConfig.VERSION_NAME.contains("nightly", ignoreCase = true)
        if (!prefs.contains(UpdateManager.KEY_UPDATE_NIGHTLY_CHANNEL)) {
            prefs.edit().putBoolean(UpdateManager.KEY_UPDATE_NIGHTLY_CHANNEL, isNightlyBuild).apply()
        }
    }

    override fun onResume() {
        super.onResume()
        UpdateManager.addStateListener(updateListener)
        rebuildSettings()
    }

    override fun onPause() {
        super.onPause()
        UpdateManager.removeStateListener(updateListener)
    }

    override fun buildSettings(): List<SettingsItem> = settings {
        val lastCheck = UpdateManager.getLastSuccessfulCheckTime(requireContext())
        val lastCheckStr = if (lastCheck > 0L) {
            val dateStr = android.text.format.DateFormat.getMediumDateFormat(requireContext()).format(Date(lastCheck))
            val timeStr = android.text.format.DateFormat.getTimeFormat(requireContext()).format(Date(lastCheck))
            getString(R.string.update_last_success, "$dateStr $timeStr")
        } else {
            getString(R.string.update_no_success)
        }

        val isUpdateAvail = UpdateManager.isUpdateAvailable
        val downloadState = UpdateManager.downloadState
        val downloading = downloadState == UpdateManager.DownloadState.DOWNLOADING
        val checking = UpdateManager.isChecking
        val checkFailed = UpdateManager.lastError != null
        val ready = isUpdateAvail && downloadState == UpdateManager.DownloadState.READY_TO_INSTALL
        val downloadFailed = isUpdateAvail && downloadState == UpdateManager.DownloadState.FAILED

        val statusText = when {
            downloading -> getString(R.string.downloading_update_title)
            checking -> getString(R.string.update_status_checking)
            checkFailed -> getString(R.string.update_status_error)
            ready -> getString(R.string.update_ready_to_install)
            downloadFailed -> getString(R.string.update_download_failed_title)
            isUpdateAvail -> getString(R.string.update_status_available, UpdateManager.latestVersion.orEmpty())
            lastCheck > 0L && UpdateManager.latestVersion != null -> getString(R.string.update_status_up_to_date)
            else -> getString(R.string.update_status_unknown)
        }

        val installedChannel = getString(if (BuildConfig.VERSION_NAME.contains("nightly", true))
            R.string.update_channel_nightly else R.string.update_channel_stable)
        val details = buildList {
            add(getString(R.string.update_installed_build, BuildConfig.VERSION_NAME, installedChannel))
            add(lastCheckStr)
            if (checkFailed) add(getString(R.string.update_check_error_help))
            if (downloadFailed) add(getString(R.string.update_download_error_help))
            if (ready) add(getString(R.string.update_install_help))
        }.joinToString("\n")
        val actionTitle = getString(when {
            checking -> R.string.update_status_checking
            downloading -> R.string.downloading_update_title
            checkFailed -> R.string.update_retry_check
            ready -> R.string.install_update_action
            downloadFailed -> R.string.update_retry_download
            isUpdateAvail && UpdateManager.apkDownloadUrl == null -> R.string.update_view_release
            isUpdateAvail -> R.string.download_and_install_title
            else -> R.string.check_for_updates_title
        })
        val progressText = if (!downloading) null else if (UpdateManager.totalBytes > 0) {
            getString(R.string.downloading_update_progress, UpdateManager.downloadProgress,
                UpdateManager.formatBytes(UpdateManager.downloadedBytes),
                UpdateManager.formatBytes(UpdateManager.totalBytes))
        } else getString(R.string.update_download_unknown_size, UpdateManager.formatBytes(UpdateManager.downloadedBytes))

        updateStatus(SettingsItem.UpdateStatus(
            title = statusText,
            details = details,
            actionTitle = actionTitle,
            actionEnabled = !checking && !downloading,
            downloading = downloading,
            progress = if (UpdateManager.totalBytes > 0) UpdateManager.downloadProgress else null,
            progressText = progressText,
            cancelTitle = getString(R.string.cancel),
            onAction = {
                if (checkFailed || !isUpdateAvail) UpdateManager.checkForUpdates(requireContext(), force = true)
                else UpdateManager.downloadAndInstall(requireContext())
            },
            onCancel = { UpdateManager.cancelDownload(requireContext()) }
        ))

        val notes = UpdateManager.releaseNotes?.trim().orEmpty()
        category(getString(R.string.update_whats_new)) {
            action(
                title = UpdateManager.latestVersion.orEmpty(),
                summary = if (notesExpanded) notes else notes.lineSequence().take(3).joinToString("\n").take(220),
                statusText = getString(if (notesExpanded) R.string.update_show_less else R.string.update_show_more),
                isVisible = { isUpdateAvail && notes.isNotEmpty() && !checking && !checkFailed }
            ) {
                notesExpanded = !notesExpanded
                rebuildSettings()
            }
            action(
                title = getString(R.string.changelog_history_title),
                summary = getString(R.string.changelog_history_summary),
                showChevron = true
            ) {
                openSubFragment(ChangelogHistoryFragment())
            }
            action(
                title = getString(R.string.update_view_release),
                showChevron = true,
                isVisible = { !checking }
            ) { UpdateManager.openReleasePage(requireContext()) }
        }

        category(getString(R.string.update_channel_category_title)) {
            val nightly = UpdateManager.isNightlyChannel(requireContext())
            action(
                title = getString(R.string.update_channel_title),
                summary = getString(if (nightly) R.string.update_channel_nightly_description else R.string.update_channel_stable_description),
                statusText = getString(if (nightly) R.string.update_channel_nightly else R.string.update_channel_stable),
                showChevron = true,
                isEnabled = { !checking && !downloading }
            ) {
                val dialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.update_channel_title)
                    .setSingleChoiceItems(arrayOf(
                        getString(R.string.update_channel_stable_option),
                        getString(R.string.update_channel_nightly_option)
                    ), if (nightly) 1 else 0) { dialog, which ->
                        dialog.dismiss()
                        if ((which == 1) != nightly) {
                            prefs.edit().putBoolean(UpdateManager.KEY_UPDATE_NIGHTLY_CHANNEL, which == 1).apply()
                            notesExpanded = false
                            UpdateManager.checkForUpdates(requireContext(), force = true)
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager.styleAlertDialog(dialog)
            }
        }

        category(getString(R.string.notifications_settings_title)) {
            switch(
                key = "show_updates",
                title = getString(R.string.show_updates_chip),
                summary = getString(R.string.show_updates_chip_summary),
                defaultValue = true
            )
        }
    }
}
