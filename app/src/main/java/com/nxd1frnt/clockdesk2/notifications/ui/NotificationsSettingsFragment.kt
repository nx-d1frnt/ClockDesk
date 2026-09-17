package com.nxd1frnt.clockdesk2.notifications.ui

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.smartchips.ui.NotificationChipSettingsActivity

class NotificationsSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = "ClockDeskPrefs"
        setPreferencesFromResource(R.xml.pref_notifications, rootKey)

        findPreference<Preference>("pref_key_notification_chip_settings")?.setOnPreferenceClickListener {
            val intent = Intent(requireContext(), NotificationChipSettingsActivity::class.java)
            startActivity(intent)
            true
        }

        findPreference<Preference>("pref_key_open_notification_shade")?.setOnPreferenceClickListener {
            NotificationShadeBottomSheet.show(requireContext())
            true
        }
    }
}
