package com.nxd1frnt.clockdesk2.ui.settings

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.music.ui.LastFmSettingsFragment
import com.nxd1frnt.clockdesk2.music.ui.MusicSourcesFragment
import com.nxd1frnt.clockdesk2.smartchips.ui.SmartChipsPluginsFragment

class SettingsActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var collapsingToolbar: CollapsingToolbarLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply dynamic colors for Material You
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        
        // Enable Edge-to-Edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContentView(R.layout.activity_settings)
        toolbar = findViewById(R.id.toolbar)
        collapsingToolbar = findViewById(R.id.collapsingToolbar)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Keep landscape for desk clock consistency
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        setupToolbar()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportFragmentManager.addOnBackStackChangedListener {
            val currentFragment = supportFragmentManager.findFragmentById(R.id.settings_container)

            val newTitle = when (currentFragment) {
                is MusicSourcesFragment -> getString(R.string.music_sources_title)
                is LastFmSettingsFragment -> getString(R.string.lastfm_plugin_name)
                is SmartChipsPluginsFragment -> getString(R.string.smart_chips_plugins_title)
                else -> getString(R.string.settings_title)
            }
            collapsingToolbar.title = newTitle
        }

        toolbar.setNavigationOnClickListener {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else {
                finish()
            }
        }
    }
}
