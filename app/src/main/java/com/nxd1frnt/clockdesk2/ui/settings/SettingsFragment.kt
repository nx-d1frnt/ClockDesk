package com.nxd1frnt.clockdesk2.ui.settings

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

class SettingsFragment : PreferenceFragmentCompat() {

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
        val preferenceManager = preferenceManager
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        // disable album art background option if android version < 4.4
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            val albumArtBackgroundPref =
                findPreference<SwitchPreferenceCompat>("lastfm_albumart_background")
            albumArtBackgroundPref?.isEnabled = false
            albumArtBackgroundPref?.isChecked = false
            albumArtBackgroundPref?.summary = getString(R.string.feature_not_supported)
        }

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
