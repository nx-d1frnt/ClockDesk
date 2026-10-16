package com.nxd1frnt.clockdesk2.music.ui

import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.ui.settings.components.BaseM3SettingsFragment
import com.nxd1frnt.clockdesk2.ui.settings.components.SettingsItem

class LastFmSettingsFragment : BaseM3SettingsFragment() {

    override fun buildSettings(): List<SettingsItem> = settings {
        category("Authorization") {
            textEdit(
                key = "lastfm_username",
                title = getString(R.string.last_fm_username),
                dialogTitle = getString(R.string.enter_last_fm_username)
            )

            textEdit(
                key = "lastfm_api_key",
                title = getString(R.string.last_fm_api_key),
                dialogTitle = getString(R.string.enter_last_fm_api_key)
            )
        }

        category("Behavior") {
            slider(
                key = "last_fm_refresh_interval",
                title = getString(R.string.last_fm_refresh_interval),
                summary = getString(R.string.last_fm_refresh_interval_summary),
                min = 5f,
                max = 60f,
                step = 1f,
                defaultValue = 30f,
                valueFormatter = { "${it.toInt()} min" }
            )

            switch(
                key = "last_fm_dynamic_refresh_rate",
                title = getString(R.string.dynamic_refresh_threshold),
                summary = getString(R.string.last_fm_dynamic_refresh_rate_summary),
                defaultValue = false
            )

            slider(
                key = "dynamic_refresh_threshold",
                title = getString(R.string.dynamic_refresh_threshold),
                summary = getString(R.string.dynamic_refresh_threshold_summary),
                min = 1f,
                max = 10f,
                step = 1f,
                defaultValue = 5f,
                isEnabled = { prefs.getBoolean("last_fm_dynamic_refresh_rate", false) }
            )
        }
    }
}