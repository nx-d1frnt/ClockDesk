package com.nxd1frnt.clockdesk2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import com.nxd1frnt.clockdesk2.background.GradientManager
import com.nxd1frnt.clockdesk2.daytimegetter.SunriseAPI
import com.nxd1frnt.clockdesk2.smartchips.SmartChipManager
import com.nxd1frnt.clockdesk2.weathergetter.OpenMeteoAPI
import com.nxd1frnt.clockdesk2.ui.view.WeatherView
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceView
import com.nxd1frnt.clockdesk2.BurnInProtectionManager

class ClockDeskDreamService : DreamService() {

    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var lastfmLayout: LinearLayout
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var weatherView: WeatherView
    private lateinit var smartChipContainer: ConstraintLayout

    // Managers
    private lateinit var clockManager: ClockManager
    private lateinit var fontManager: FontManager
    private lateinit var weatherGetter: OpenMeteoAPI
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var gradientManager: GradientManager
    private lateinit var smartChipManager: SmartChipManager
    private lateinit var burnInProtectionManager: BurnInProtectionManager

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var locationManager: LocationManager

    private lateinit var themedContext: Context

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        try {
            isInteractive = false
            isFullscreen = true

            window?.attributes = window?.attributes?.apply {

                screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            locationManager = LocationManager(this, 101)

            themedContext = ContextThemeWrapper(this, R.style.Theme_ClockDesk)

            val view = View.inflate(themedContext, R.layout.activity_main, null)
            setContentView(view)

            initViews()
            hideUIControls()

            initManagers()

        } catch (e: Exception) {
            android.util.Log.e("ClockDeskDream", "CRASH in onAttachedToWindow", e)
            val errorView = TextView(this).apply {
                text = "ClockDesk Error: ${e.message}"
                setTextColor(android.graphics.Color.RED)
                textSize = 24f
                gravity = android.view.Gravity.CENTER
            }
            setContentView(errorView)
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()

        if (!::locationManager.isInitialized || !::clockManager.isInitialized) {
            android.util.Log.e("ClockDeskDream", "Managers not initialized. Skipping updates.")
            return
        }

        locationManager.loadCoordinates { lat, lon ->
            if (::weatherGetter.isInitialized) weatherGetter.startUpdates(lat, lon)
            if (::gradientManager.isInitialized) gradientManager.startUpdates()
        }

        if (::clockManager.isInitialized) clockManager.startUpdates()
        if (::smartChipManager.isInitialized) smartChipManager.startUpdates()
        if (::burnInProtectionManager.isInitialized) burnInProtectionManager.start()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()

        if (::clockManager.isInitialized) clockManager.stopUpdates()
        if (::gradientManager.isInitialized) gradientManager.stopUpdates()
        if (::weatherGetter.isInitialized) weatherGetter.stopUpdates()
        if (::smartChipManager.isInitialized) smartChipManager.stopUpdates()
        if (::burnInProtectionManager.isInitialized) burnInProtectionManager.stop()
    }

    private fun initViews() {
        timeText = findViewById(R.id.time_text)
        dateText = findViewById(R.id.date_text)
        weatherText = findViewById(R.id.weather_text)
        weatherIcon = findViewById(R.id.weather_icon)
        lastfmLayout = findViewById(R.id.lastfm_layout)
        backgroundLayout = findViewById(R.id.background_layout)
        weatherView = findViewById(R.id.weatherView)
        smartChipContainer = findViewById(R.id.smart_chip_container)
    }

    private fun hideUIControls() {
        findViewById<View>(R.id.settings_button)?.visibility = View.GONE
        findViewById<View>(R.id.demo_button)?.visibility = View.GONE
        findViewById<View>(R.id.background_button)?.visibility = View.GONE
        findViewById<View>(R.id.background_customization_fab)?.visibility = View.GONE

        findViewById<View>(R.id.tutorial_overlay_root)?.visibility = View.GONE
    }

    private fun initManagers() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val enableLogging = prefs.getBoolean("additional_logging", false)

        backgroundManager = BackgroundManager(this)
        locationManager = LocationManager(this, 101)

        val dayTimeGetter = SunriseAPI(this, locationManager)

        // Weather
        weatherGetter = OpenMeteoAPI(this, locationManager) {
            if (weatherGetter.temperature != null) {
                weatherText.text = "${weatherGetter.temperature}°C"
            }
        }

        // Gradient
        gradientManager = GradientManager(backgroundLayout, dayTimeGetter, locationManager, handler)

        // Font
        fontManager = FontManager(
            themedContext, timeText, dateText, lastfmLayout,
            findViewById(R.id.now_playing_text), findViewById(R.id.lastfm_icon),
            weatherText, weatherIcon, smartChipContainer, enableLogging
        )
        fontManager.loadFont()

        // Clock
        clockManager = ClockManager(
            timeText, dateText, handler, fontManager, dayTimeGetter, locationManager,
            { _, _, _ -> }, // Sun times callback
            { _ -> gradientManager.updateSimulatedTime(clockManager.getCurrentTime()) },
            enableLogging
        )

        // Smart Chips
        smartChipManager = SmartChipManager(themedContext, smartChipContainer, prefs, fontManager)

        burnInProtectionManager = BurnInProtectionManager(
            listOf(timeText, dateText, lastfmLayout, smartChipContainer)
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        smartChipManager.destroy()
    }
}