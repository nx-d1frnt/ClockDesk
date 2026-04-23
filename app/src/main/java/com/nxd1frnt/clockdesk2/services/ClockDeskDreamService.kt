package com.nxd1frnt.clockdesk2.services

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import com.nxd1frnt.clockdesk2.background.GradientManager
import com.nxd1frnt.clockdesk2.daytimegetter.SunriseAPI
import com.nxd1frnt.clockdesk2.music.MusicPluginManager
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.smartchips.SmartChipManager
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceView
import com.nxd1frnt.clockdesk2.ui.view.WeatherView
import com.nxd1frnt.clockdesk2.utils.BurnInProtectionManager
import com.nxd1frnt.clockdesk2.utils.ClockManager
import com.nxd1frnt.clockdesk2.utils.FontManager
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.weathergetter.OpenMeteoAPI

class ClockDeskDreamService : DreamService() {

    // region Views
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherLayout: LinearLayout
    private lateinit var lastfmLayout: LinearLayout
    private lateinit var lastfmIcon: ImageView
    private lateinit var nowPlayingText: TextView
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var backgroundImageView: ImageView
    private lateinit var turbulenceOverlay: TurbulenceView
    private lateinit var weatherView: WeatherView
    private lateinit var smartChipContainer: ConstraintLayout
    // endregion

    // region Managers
    private lateinit var clockManager: ClockManager
    private lateinit var fontManager: FontManager
    private lateinit var weatherGetter: OpenMeteoAPI
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var gradientManager: GradientManager
    private lateinit var smartChipManager: SmartChipManager
    private lateinit var burnInProtectionManager: BurnInProtectionManager
    private lateinit var locationManager: LocationManager
    private lateinit var dayTimeGetter: SunriseAPI
    private var musicManager: MusicPluginManager? = null
    // endregion

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var themedContext: Context

    /** Mirrors MainActivity.hasCustomImageBackground — controls gradient vs image path */
    private var hasCustomImageBackground = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        try {
            isInteractive = false
            isFullscreen = true

            // Force landscape. In some OEM builds window.attributes is more reliable
            // than requestedOrientation inside a DreamService, so we set both.
            var requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.attributes = window?.attributes?.apply {
                screenOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            themedContext = ContextThemeWrapper(this, R.style.Theme_ClockDesk)

            val view = View.inflate(themedContext, R.layout.activity_main, null)
            setContentView(view)

            initViews()
            hideUIControls()
            initManagers()

        } catch (e: Exception) {
            Log.e("ClockDeskDream", "CRASH in onAttachedToWindow", e)
            setContentView(TextView(this).apply {
                text = "ClockDesk Error: ${e.message}"
                setTextColor(Color.RED)
                textSize = 24f
                gravity = Gravity.CENTER
            })
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()

        if (!::locationManager.isInitialized || !::clockManager.isInitialized) {
            Log.e("ClockDeskDream", "Managers not initialized. Skipping updates.")
            return
        }

        // Mirrors MainActivity.onResume → locationManager.loadCoordinates block
        locationManager.loadCoordinates { lat, lon ->
            // 1. Fetch sun times so gradient and night-shift are correct
            dayTimeGetter.fetch(lat, lon) {
                if (!hasCustomImageBackground) {
                    gradientManager.updateGradient()
                }
            }
            // 2. Weather
            if (::weatherGetter.isInitialized) weatherGetter.startUpdates(lat, lon)
            // 3. Gradient (only if no user photo)
            if (!hasCustomImageBackground && ::gradientManager.isInitialized) {
                gradientManager.startUpdates()
            }
        }

        if (::clockManager.isInitialized) clockManager.startUpdates()

        // Burn-in — honour the user preference just like MainActivity.startUpdates()
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val enableBurnIn = prefs.getBoolean("burn_in_protection", false)
        if (enableBurnIn && ::burnInProtectionManager.isInitialized) {
            burnInProtectionManager.start()
        }

        // Music
        val musicPrefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        musicManager = MusicPluginManager(this, musicPrefs) { state ->
            onMusicStateChanged(state)
        }

        // Restore user background (photo or gradient)
        restoreBackground()
        // Restore weather overlay state
        restoreWeatherView()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()

        if (::clockManager.isInitialized) clockManager.stopUpdates()
        if (::gradientManager.isInitialized) gradientManager.stopUpdates()
        if (::weatherGetter.isInitialized) weatherGetter.stopUpdates()
        if (::burnInProtectionManager.isInitialized) burnInProtectionManager.stop()
        musicManager?.destroy()
        musicManager = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    // region Init helpers

    private fun initViews() {
        timeText = findViewById(R.id.time_text)
        dateText = findViewById(R.id.date_text)
        weatherText = findViewById(R.id.weather_text)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherLayout = findViewById(R.id.weather_layout)
        lastfmLayout = findViewById(R.id.lastfm_layout)
        lastfmIcon = findViewById(R.id.lastfm_icon)
        nowPlayingText = findViewById(R.id.now_playing_text)
        backgroundLayout = findViewById(R.id.background_layout)
        backgroundImageView = findViewById(R.id.background_image_view)
        turbulenceOverlay = findViewById(R.id.turbulence_overlay)
        weatherView = findViewById(R.id.weatherView)
        smartChipContainer = findViewById(R.id.smart_chip_container)
    }

    private fun hideUIControls() {
        listOf(
            R.id.settings_button,
            R.id.demo_button,
            R.id.background_button,
            R.id.background_customization_fab,
            R.id.tutorial_overlay_root,
            R.id.side_sheet_container,
        ).forEach { id -> findViewById<View>(id)?.visibility = View.GONE }
    }

    private fun initManagers() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val enableLogging = prefs.getBoolean("additional_logging", false)

        backgroundManager = BackgroundManager(this)
        locationManager = LocationManager(this, 101)
        dayTimeGetter = SunriseAPI(this, locationManager)

        // Weather — full callback matching MainActivity
        weatherGetter = OpenMeteoAPI(this, locationManager) {
            if (weatherGetter.temperature != null) {
                weatherText.text = "${weatherGetter.temperature}°C"
            }

            val code = weatherGetter.weatherCode ?: 0
            val wind = weatherGetter.windSpeed ?: 0.0
            val isNight = !(weatherGetter.isDay ?: true)
            val precip = weatherGetter.precipitation
            val clouds = weatherGetter.cloudCover
            val vis = weatherGetter.visibility

            if (backgroundManager.isWeatherEffectsEnabled() &&
                !backgroundManager.isManualWeatherEnabled()
            ) {
                weatherView.updateFromOpenMeteoSmart(
                    wmoCode = code,
                    windSpeedKmh = wind,
                    night = isNight,
                    precipitation = precip,
                    cloudCover = clouds,
                    visibility = vis
                )
            }

            // Show/hide the weather text+icon strip like MainActivity
            weatherLayout.visibility =
                if (weatherGetter.temperature != null) View.VISIBLE else View.GONE
        }

        // Gradient
        gradientManager = GradientManager(backgroundLayout, dayTimeGetter, locationManager, handler)

        // Font — identical argument list to MainActivity
        fontManager = FontManager(
            themedContext, timeText, dateText, lastfmLayout,
            nowPlayingText, lastfmIcon,
            weatherText, weatherIcon, smartChipContainer, enableLogging
        )
        fontManager.loadFont()

        // Clock
        clockManager = ClockManager(
            timeText, dateText, handler, fontManager, dayTimeGetter, locationManager,
            { _, _, _ -> }, // sun-times callback — no-op in dream
            { _ -> gradientManager.updateSimulatedTime(clockManager.getCurrentTime()) },
            enableLogging
        )

        // Smart Chips
        smartChipManager = SmartChipManager(themedContext, smartChipContainer, prefs, fontManager)

        // Burn-in (object created here; start/stop driven by prefs in lifecycle methods)
        burnInProtectionManager = BurnInProtectionManager(
            listOf(timeText, dateText, lastfmLayout, smartChipContainer)
        )
    }

    // endregion

    // region Background restore

    /**
     * Mirrors the pattern in MainActivity.onResume / restoreUserBackground:
     * - if user saved a photo URI → show it
     * - otherwise keep the gradient
     */
    private fun restoreBackground() {
        val uriStr = backgroundManager.getSavedBackgroundUri()
        if (!uriStr.isNullOrEmpty()) {
            val uri = runCatching { Uri.parse(uriStr) }.getOrNull()
            if (uri != null) {
                hasCustomImageBackground = true
                gradientManager.stopUpdates()
                applyImageBackground(uri, backgroundManager.getBlurIntensity())
                return
            }
        }
        // No user photo → gradient path
        hasCustomImageBackground = false
        backgroundImageView.visibility = View.GONE
    }

    /**
     * Simplified applyImageBackground for Dream (no Glide transition animation,
     * no LoadingAnimationView, but does apply blur via RenderEffect on API 31+).
     */
    private fun applyImageBackground(uri: Uri, blurIntensity: Int) {
        // Use Glide the same way MainActivity does (reuse GlideApp if available,
        // otherwise fall back to standard Glide). Only the loading part is simplified —
        // no cross-fade animation needed while the screen is static.
        try {
            com.nxd1frnt.clockdesk2.network.GlideApp.with(this)
                .load(uri)
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        backgroundImageView.setImageDrawable(resource)
                        backgroundImageView.visibility = View.VISIBLE

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                            && blurIntensity > 0
                        ) {
                            val radius = blurIntensity.toFloat().coerceIn(1f, 25f)
                            backgroundImageView.setRenderEffect(
                                android.graphics.RenderEffect.createBlurEffect(
                                    radius, radius,
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                            )
                        } else {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                backgroundImageView.setRenderEffect(null)
                            }
                        }
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        backgroundImageView.visibility = View.GONE
                    }
                })
        } catch (e: Exception) {
            Log.e("ClockDeskDream", "Failed to load background image", e)
            hasCustomImageBackground = false
            backgroundImageView.visibility = View.GONE
        }
    }

    // endregion

    // region Weather overlay restore

    /**
     * Mirrors MainActivity's weather-view setup logic so the WeatherView
     * state is consistent with user preferences on dream start.
     */
    private fun restoreWeatherView() {
        val isEnabled = backgroundManager.isWeatherEffectsEnabled()
        if (!isEnabled) {
            weatherView.visibility = View.GONE
            return
        }
        weatherView.visibility = View.VISIBLE
        val isNight = !dayTimeGetter.isDay()

        if (backgroundManager.isManualWeatherEnabled()) {
            val typeOrdinal = backgroundManager.getManualWeatherType()
            val type = WeatherView.WeatherType.values()
                .getOrElse(typeOrdinal) { WeatherView.WeatherType.CLEAR }
            val intensity = backgroundManager.getManualWeatherIntensity() / 100f
            weatherView.forceWeather(type, intensity, 5.0f, isNight)
        }
        // Automatic weather will be set once the first weatherGetter callback fires.
    }

    // endregion

    // region Music

    private fun onMusicStateChanged(state: PluginState) {
        when (state) {
            is PluginState.Playing -> {
                lastfmLayout.visibility = View.VISIBLE
                nowPlayingText.text = state.track?.let { "${it.artist} — ${it.title}" }
                    ?: ""
            }
            else -> {
                lastfmLayout.visibility = View.GONE
            }
        }
    }

    // endregion
}