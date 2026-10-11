package com.nxd1frnt.clockdesk2.smartchips.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager

class NotificationChipSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        UiMonetThemeManager.applyToActivity(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_sub_settings)
        UiMonetThemeManager.applyToSettingsActivity(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val collapsingToolbar = findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        collapsingToolbar.title = getString(R.string.notification_chip_settings_title)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        toolbar.setNavigationOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, NotificationChipSettingsFragment())
                .commit()
        }
    }
}
