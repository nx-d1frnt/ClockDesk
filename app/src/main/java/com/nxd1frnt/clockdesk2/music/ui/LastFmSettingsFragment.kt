package com.nxd1frnt.clockdesk2.music.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.nxd1frnt.clockdesk2.R

class LastFmSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"

        setPreferencesFromResource(R.xml.prefs_lastfm, rootKey)
    }
}