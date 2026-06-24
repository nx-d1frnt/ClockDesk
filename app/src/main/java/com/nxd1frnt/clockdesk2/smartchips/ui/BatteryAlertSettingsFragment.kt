package com.nxd1frnt.clockdesk2.smartchips.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.nxd1frnt.clockdesk2.R

class BatteryAlertSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_battery_alert, rootKey)
    }
}
