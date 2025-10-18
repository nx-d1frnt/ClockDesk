package com.nxd1frnt.clockdesk2

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferenceManager = preferenceManager
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.preferences, rootKey)
        // disable album art background option if android version < 4.4
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) {
            val albumArtBackgroundPref = findPreference<androidx.preference.SwitchPreference>("lastfm_albumart_background")
            albumArtBackgroundPref?.isEnabled = false
            albumArtBackgroundPref?.isChecked = false
            albumArtBackgroundPref?.summary = "Sadly not supported on your Android version."
        }
    }

}