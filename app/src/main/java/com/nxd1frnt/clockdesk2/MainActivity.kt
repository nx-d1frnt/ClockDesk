package com.nxd1frnt.clockdesk2

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import android.graphics.PorterDuff
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.daytimegetter.SunriseAPI
import com.nxd1frnt.clockdesk2.musicgetter.LastFmAPI
import com.nxd1frnt.clockdesk2.musicgetter.MusicGetter
import com.nxd1frnt.clockdesk2.smartchips.SmartChipManager
import com.nxd1frnt.clockdesk2.weathergetter.OpenMeteoAPI
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter

class MainActivity : AppCompatActivity() {
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherLayout: LinearLayout
    private lateinit var lastfmLayout: LinearLayout
    private lateinit var lastfmIcon: ImageView
    private lateinit var nowPlayingTextView: TextView
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var backgroundImageView: ImageView
    private lateinit var weatherView: WeatherView
    private lateinit var settingsButton: Button
    private lateinit var debugButton: Button
    private lateinit var backgroundButton: Button
    private lateinit var backgroundCustomizationTab: FloatingActionButton
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var editModeBlurLayer: ImageView
    private lateinit var bottomSheet: LinearLayout
    private lateinit var backgroundBottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var tutorialLayout: ConstraintLayout
    private lateinit var tutorialFinger: ImageView
    private lateinit var tutorialText: TextView

    // Bottom Sheet UI elements (Customization)
    private lateinit var bsTitle: TextView
    private lateinit var bsSizeSeekBar: SeekBar
    private lateinit var bsSizeValue: TextView
    private lateinit var bsTransparencySeekBar: SeekBar
    private lateinit var bsTransparencyPreview: View
    private lateinit var bsFontRecyclerView: RecyclerView
    private lateinit var bsApplyButton: Button
    private lateinit var bsCancelButton: Button
    private lateinit var bsNightShiftSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var bsTimeFormatGroup: RadioGroup
    private lateinit var bsDateFormatGroup: RadioGroup
    private lateinit var bsTimeFormatLabel: TextView
    private lateinit var bsDateFormatLabel: TextView
    private lateinit var bsFreeModeSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var bsGridSnapSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var bsTextGravityGroup: MaterialButtonToggleGroup
    private lateinit var bsHorizontalAlignGroup: MaterialButtonToggleGroup
    private lateinit var bsVerticalAlignGroup: MaterialButtonToggleGroup
    private lateinit var bsMoveUpBtn: Button
    private lateinit var bsMoveDownBtn: Button

    // Background Bottom Sheet UI elements
    private lateinit var bgRecycler: RecyclerView
    private lateinit var bgBlurSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var bgBlurSeek: SeekBar
    private lateinit var bgDimToggleGroup: MaterialButtonToggleGroup
    private lateinit var bgDimSeek: SeekBar
    private lateinit var bgClearBtn: Button
    private lateinit var bgApplyBtn: Button
    private lateinit var bgWeatherSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var bgManualWeatherSwitch: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var bgManualWeatherScroll: View
    private lateinit var bgWeatherToggleGroup: MaterialButtonToggleGroup
    private lateinit var bgIntensityContainer: View
    private lateinit var bgIntensitySeek: SeekBar

     // Flag to prevent UI updates from triggering listener logic
    private var isUpdatingBackgroundUi = false
    private lateinit var clockManager: ClockManager
    private lateinit var gradientManager: GradientManager
    private lateinit var fontManager: FontManager
    private lateinit var locationManager: LocationManager
    private lateinit var weatherGetter: WeatherGetter
    private lateinit var dayTimeGetter: DayTimeGetter
    private lateinit var musicGetter: MusicGetter
    private lateinit var widgetMover: WidgetMover
    private lateinit var burnInProtectionManager: BurnInProtectionManager
    private var isAdvancedGraphicsEnabled = false
    private var lastTrackInfo: String? = null
    private var wasMusicBackgroundApplied = false
    private var isEditMode = false
    private var isDemoMode = false
    private var isTutorialRunning = false
    private var isNightShiftEnabled = false
    private var focusedView: View? = null
    private val editModeTimeout = 10000L // 10 seconds
    private val animationDuration = 300L // 300ms
    private val handler = Handler(Looper.getMainLooper())
    private val permissionRequestCode = 100
    private val PICK_BG_REQUEST = 300
    private var enableAdditionalLogging = false
    private var isPowerSavingMode = false
    private var isAutoPowerSavingActive = false // Tracks if auto-mode did it
    private var batteryLevelReceiver: BatteryLevelReceiver? = null
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private val minPowerSaveBrightness = 0.01f
    private lateinit var smartChipManager: SmartChipManager
    private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                // If we are NOT in power-saving mode, do nothing.
                // Let the system handle auto-brightness.
                if (!isPowerSavingMode) {
                    return
                }

                val lux = event.values[0]
                val layoutParams = window.attributes

                // We are in power-saving mode, so we apply custom logic:
                // We map the lux value to a brightness level, but cap it
                // to a low value (e.g., 40%) to still save power.

                val newBrightness = when {
                    lux <= 20 -> minPowerSaveBrightness      // Very dark, use minimum
                    lux <= 500 -> 0.1f       // Dim room
                    lux <= 5000 -> 0.25f      // Bright room
                    else -> 0.4f             // Very bright (e.g., outdoors)
                }

                // Only update if the brightness actually needs to change
                if (layoutParams.screenBrightness != newBrightness) {
                    layoutParams.screenBrightness = newBrightness
                    window.attributes = layoutParams
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Not needed for this
        }
    }

    private val editModeTimeoutRunnable = Runnable {
        if (isEditMode && !isDemoMode) {
            exitEditMode()
        }
    }
    private var isBottomSheetInitializing = false
    private var hasCustomImageBackground = false
    private lateinit var backgroundBottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private var previewBackgroundUri: String? = null
    private var backgroundsAdapter: BackgroundsAdapter? = null
    private lateinit var backgroundProgressOverlay: View
    private lateinit var backgroundProgressText: TextView
    private lateinit var backgroundManager: BackgroundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full-screen and keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        setContentView(R.layout.activity_main)

        timeText = findViewById(R.id.time_text)
        dateText = findViewById(R.id.date_text)
        weatherText = findViewById(R.id.weather_text)
        weatherIcon = findViewById(R.id.weather_icon)
        weatherLayout = findViewById(R.id.weather_layout)
        lastfmLayout = findViewById(R.id.lastfm_layout)
        lastfmIcon = findViewById(R.id.lastfm_icon)
        nowPlayingTextView = findViewById(R.id.now_playing_text)
        backgroundLayout = findViewById(R.id.background_layout)
        backgroundImageView = findViewById(R.id.background_image_view)
        weatherView = findViewById(R.id.weatherView)
        backgroundProgressOverlay = findViewById(R.id.background_progress_overlay)
        backgroundProgressText = findViewById(R.id.background_progress_text)
        settingsButton = findViewById(R.id.settings_button)
        debugButton = findViewById(R.id.demo_button)
        backgroundButton = findViewById(R.id.background_button)
        backgroundCustomizationTab = findViewById(R.id.background_customization_fab)
        mainLayout = findViewById(R.id.main_layout)

        bottomSheet = findViewById(R.id.bottom_sheet)
        backgroundBottomSheet = findViewById<LinearLayout>(R.id.background_bottom_sheet)
        // main bottom sheet behavior
        bottomSheetBehavior = from(bottomSheet).apply {
            state = STATE_HIDDEN
        }
        tutorialLayout = findViewById(R.id.tutorial_overlay_root)
        tutorialFinger = findViewById(R.id.tutorial_finger_icon)
        tutorialText = findViewById(R.id.tutorial_text)
        widgetMover = WidgetMover(this, listOf(timeText, dateText, lastfmLayout), mainLayout)
        widgetMover.restoreOrderAndPositions()
        widgetMover.setOnInteractionListener { isInteracting ->
            if (isInteracting) {
                // User is holding/dragging: Cancel the timeout
                handler.removeCallbacks(editModeTimeoutRunnable)
            } else {
                // User released: Restart the timeout (if still in edit mode)
                if (isEditMode) {
                    resetEditModeTimeout()
                }
            }
        }
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        enableAdditionalLogging = prefs.getBoolean("additional_logging", false)
        editModeBlurLayer = findViewById(R.id.edit_mode_blur_layer)
        editModeBlurLayer.setColorFilter(Color.parseColor("#C5000000"), PorterDuff.Mode.SRC_OVER)
        isAdvancedGraphicsEnabled = prefs.getBoolean("advanced_graphics", false)

        backgroundBottomSheetBehavior = from(backgroundBottomSheet).apply {
            state = STATE_HIDDEN
        }

        // Initially hide edit-only controls (alpha 0)
        settingsButton.alpha = 0f
        settingsButton.visibility = View.GONE
        debugButton.alpha = 0f
        debugButton.visibility = View.GONE
//        backgroundButton.alpha = 0f
//        backgroundButton.visibility = View.GONE
        backgroundCustomizationTab.alpha = 0f
        backgroundCustomizationTab.visibility = View.GONE

        fontManager = FontManager(
            this,
            timeText,
            dateText,
            weatherText,
            weatherIcon,
            nowPlayingTextView,
            lastfmIcon,
            lastfmLayout,
            enableAdditionalLogging
        )
        backgroundManager = BackgroundManager(this)
        locationManager = LocationManager(this, permissionRequestCode)
        dayTimeGetter = SunriseAPI(this, locationManager)
        weatherGetter = OpenMeteoAPI(this, locationManager) {
            runOnUiThread {
                if (weatherGetter.temperature != null) {
                    weatherText.text = "${weatherGetter.temperature}°C"
                }

                val code = weatherGetter.weatherCode ?: 0
                val wind = weatherGetter.windSpeed ?: 0.0
                val isNight = !(weatherGetter.isDay ?: true)
                val precip = weatherGetter.precipitation
                val clouds = weatherGetter.cloudCover
                val vis = weatherGetter.visibility

                if (backgroundManager.isWeatherEffectsEnabled() && !backgroundManager.isManualWeatherEnabled()) {
                    weatherView.updateFromOpenMeteoSmart(
                        code, wind, isNight,
                        precip, clouds, vis
                    )
                }

                if (hasCustomImageBackground) {
                    updateBackgroundFilters()
                }
            }
        }
        musicGetter = LastFmAPI(this, musicCallback, backgroundManager)
        gradientManager = GradientManager(backgroundLayout, dayTimeGetter, locationManager, handler)
        clockManager = ClockManager(
            timeText,
            dateText,
            handler,
            fontManager,
            dayTimeGetter,
            locationManager,
            { _, _, _ ->
                if (isDemoMode) {
                    Log.d("MainActivity", "debug sun times callback (demo mode)")
                }
            },
            { currentTime -> // onTimeChanged callback (called in both real and demo)
                // Update gradient manager's simulated time handling
                try {
                    gradientManager.updateSimulatedTime(currentTime)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to update gradient simulated time: ${e.message}")
                }

                // If a custom image background is visible and dim mode is dynamic, recompute/apply dimming
                try {
                    if (backgroundImageView.visibility == View.VISIBLE) {
                        val mode = backgroundManager.getDimMode()
                        if (mode == BackgroundManager.DIM_MODE_DYNAMIC) {
//                            setBackgroundDimming(mode, backgroundManager.getDimIntensity())
                            updateBackgroundFilters()
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to update dynamic dimming: ${e.message}")
                }
            },
            enableAdditionalLogging
        )

        val chipContainer = findViewById<ConstraintLayout>(R.id.smart_chip_container)
        smartChipManager = SmartChipManager(
            this,
            chipContainer,
            prefs
        )
        burnInProtectionManager = BurnInProtectionManager(listOf(timeText, dateText, lastfmLayout, chipContainer))
        burnInProtectionManager.start()
        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Check if the key that changed is one of our smart chip toggles
            val chipKeys = setOf("show_battery_alert") +
                    smartChipManager.externalPlugins.map { it.preferenceKey }

            if (chipKeys.contains(key)) {
                smartChipManager.onPreferencesChanged()
            }

            when (key) {
                "automatic_battery_saver_mode", "battery_saver_trigger" -> {
                    // If auto-saver settings change, re-check chip status
                    smartChipManager.onPreferencesChanged()
                }
                "battery_saver_mode" -> {
                    // This is handled by SettingsFragment, but we also update the chip
                    smartChipManager.onPreferencesChanged()
                }
            }
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        fontManager.loadFont()

        // Load any saved custom background image from prefs (if set)
        loadSavedBackground()


        // Ensure dim is applied if image already set
        val dimModeInit = backgroundManager.getDimMode()
        backgroundManager.getDimIntensity()
        if (dimModeInit != BackgroundManager.DIM_MODE_OFF) {
//            setBackgroundDimming(
//                dimModeInit,
//                dimIntensity
//            )
            updateBackgroundFilters()
        }
        checkForFirstLaunchAnimation()
        // Long tap for edit mode
        mainLayout.setOnLongClickListener {
            toggleEditMode()
            true
        }

        setupBottomSheet()
        initCustomizationControls()
        initBackgroundControls()

        timeText.setOnClickListener {
            if (isEditMode) {
                showCustomizationBottomSheet(it) // pass the clicked view for focus
                resetEditModeTimeout()
            }
        }
        dateText.setOnClickListener {
            if (isEditMode) {
                showCustomizationBottomSheet(it)
                resetEditModeTimeout()
            }
        }
        lastfmLayout.setOnClickListener {
            if (isEditMode) {
                showCustomizationBottomSheet(it)
                resetEditModeTimeout()
            }
        }

        //  backgroundButton.setOnClickListener {
        //     showBackgroundBottomSheet()
        //  }

        backgroundCustomizationTab.setOnClickListener {
            showBackgroundBottomSheet()
            //highlightImageView(true)
            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        }

        // Debug mode toggle
        debugButton.setOnClickListener {
            isDemoMode = !isDemoMode
            clockManager.toggleDebugMode(isDemoMode)
            gradientManager.toggleDebugMode(isDemoMode)

            // If the user has set a custom image background, make sure it remains visible.
            if (hasCustomImageBackground) {
                // Stop any gradient updates that the demo toggle might have started
                gradientManager.stopUpdates()
                // Re-apply the saved image so it overwrites any gradient that may have been set
                val uriStr = backgroundManager.getSavedBackgroundUri()
                uriStr?.let {
                    try {
                        val uri = Uri.parse(it)
                        applyImageBackground(uri, backgroundManager.getBlurIntensity())
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Failed to reapply custom background: ${e.message}")
                    }
                }
            }

            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            exitEditMode() // Exit edit mode when opening settings
        }


        // Start updates
        restoreSavedWeatherState()
        startUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        smartChipManager.destroy()
        weatherGetter.stopUpdates()
    }

    private val musicCallback: (()->Unit) = callback@{
       if (isEditMode) {
       // if edit mode is active, do nothing
           return@callback
       } else {
           if (musicGetter.enabled) {
               if (musicGetter.currentTrack != lastTrackInfo) {
                   lastfmLayout.visibility = View.VISIBLE
                   lastfmLayout.animate().alpha(0f).setDuration(400)
                       .setListener(object : AnimatorListenerAdapter() {
                           override fun onAnimationEnd(animation: Animator) {
                               if (!isEditMode) {
                                   nowPlayingTextView.text = musicGetter.currentTrack
                                   nowPlayingTextView.isSelected = true
                                   lastfmLayout.animate().alpha(1f).setDuration(400).setListener(null)
                                       .start()
                               }
                           }
                       })
                   lastTrackInfo = musicGetter.currentTrack

                   // flag to track if we applied music background for this track
                   var isMusicBgAppliedThisTrack = false

                   if (musicGetter.currentAlbumArtUrl != null) {
                       Log.d(
                           "MainActivity",
                           "Applying album art background: ${musicGetter.currentAlbumArtUrl}"
                       )
                       applyImageBackground(
                           Uri.parse(musicGetter.currentAlbumArtUrl),
                           backgroundManager.getBlurIntensity()
                       )
                       wasMusicBackgroundApplied =
                           true // flag to indicate that music is controlling the background
                       isMusicBgAppliedThisTrack = true // flag for this track

                   } else if (musicGetter.userPreselectedBackgroundUri != null) {
                       Log.d(
                           "MainActivity",
                           "Applying user preselected background: ${musicGetter.userPreselectedBackgroundUri}"
                       )
                       applyImageBackground(
                           Uri.parse(musicGetter.userPreselectedBackgroundUri),
                           backgroundManager.getBlurIntensity()
                       )
                       wasMusicBackgroundApplied = true
                       isMusicBgAppliedThisTrack = true
                   }
                   // If no new art for this track, and music had applied background before, restore user background
                   if (!isMusicBgAppliedThisTrack && wasMusicBackgroundApplied) {
                       Log.d(
                           "MainActivity",
                           "Restoring user background (track changed, no new art)"
                       )
                       restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                       wasMusicBackgroundApplied = false // reset flag
                   }
               }
           } else {
               Log.d("MusicCallback", "Hiding lastfmLayout with animation")
               lastfmLayout.animate().alpha(0f).setDuration(400)
                   .setListener(object : AnimatorListenerAdapter() {
                       override fun onAnimationEnd(animation: Animator) {
                           Log.d("MusicCallback", "Animation end: isEditMode=$isEditMode")
                           if (!isEditMode) {
                               lastfmLayout.visibility = View.GONE
                           }
                       }
                   })

               // If music background was applied, restore user background
               if (wasMusicBackgroundApplied) {
                   restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                   Log.d(
                       "MainActivity",
                       "Restoring user background after music background disabled, bgUri=${backgroundManager.getSavedBackgroundUri()}"
                   )
                   wasMusicBackgroundApplied = false // reset flag
               }
           }
       }
    }


    private fun applyHeavyBlurToLayer(view: ImageView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurRadius = 45f
            view.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP
                )
            )
        }
        view.setColorFilter(Color.parseColor("#C5000000"), PorterDuff.Mode.SRC_OVER)
    }
    private fun checkForFirstLaunchAnimation() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)
        enableAdditionalLogging = prefs.getBoolean("additional_logging", false)

        if (isFirstLaunch) {
            startTutorialAnimation()
        } else {
          checkLocationPermissionsAndLoadData()
        }
    }

    private fun togglePowerSavingMode(enable: Boolean) {
        if (enable == isPowerSavingMode) return // Already in the correct state

        isPowerSavingMode = enable

        if (enable) {
            // --- ENABLE POWER SAVING ---

            // Stop frequent updates
            musicGetter.setPowerSavingMode(true)
            gradientManager.stopUpdates()
            clockManager.setPowerSavingMode(true)
            weatherGetter.setPowerSavingMode(true)

            // Set screen brightness to low
            val layoutParams = window.attributes
            layoutParams.screenBrightness = minPowerSaveBrightness
            window.attributes = layoutParams

        } else {
            // --- DISABLE POWER SAVING ---
            musicGetter.setPowerSavingMode(false)
            gradientManager.startUpdates()
            clockManager.setPowerSavingMode(false)
            weatherGetter.setPowerSavingMode(false)

            // Restore brightness
            val layoutParams = window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE // System default
            window.attributes = layoutParams
        }
    }

    private fun checkLocationPermissionsAndLoadData() {
        // check location permissions
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasCoarse || hasFine) {
            // permissions granted
            loadCoordinatesAndFetchData()
        } else {
            // show rationale dialog
            showLocationRationaleDialog()
        }
    }

    private fun restoreSavedWeatherState() {
        val isEnabled = backgroundManager.isWeatherEffectsEnabled()

        if (!isEnabled) {
            weatherView.visibility = View.GONE
            return
        }

        weatherView.visibility = View.VISIBLE
        val isNight = !dayTimeGetter.isDay()

        if (backgroundManager.isManualWeatherEnabled()) {
            val typeOrdinal = backgroundManager.getManualWeatherType()
            val type = WeatherView.WeatherType.values().getOrElse(typeOrdinal) { WeatherView.WeatherType.CLEAR }

            val intensity = backgroundManager.getManualWeatherIntensity() / 100f

            weatherView.forceWeather(type, intensity, 5.0f, isNight)
        } else {
          // we let the weatherGetter updates handle it
        }
    }
    private fun showLocationRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.location_permission_title))
            .setMessage(getString(R.string.location_permission_message))
            .setPositiveButton(getString(R.string.location_permission_grant)) { _, _ ->
                // user chose to grant permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    permissionRequestCode
                )
            }
            .setNegativeButton(getString(R.string.location_permission_manual)) { _, _ ->
                // user chose to enter location manually
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNeutralButton(getString(android.R.string.cancel)) { dialog, _ ->
                // user cancelled
                dialog.dismiss()
            }
            .show()
    }

    private fun loadCoordinatesAndFetchData() {
        locationManager.loadCoordinates { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (!hasCustomImageBackground) gradientManager.updateGradient()
                if (isNightShiftEnabled) {
                    fontManager.applyNightShiftTransition(
                        clockManager.getCurrentTime(),
                        dayTimeGetter,
                        isNightShiftEnabled
                    )
                }
                // recalculate dimming if needed
                if (backgroundImageView.visibility == View.VISIBLE) {
//                    setBackgroundDimming(backgroundManager.getDimMode(), backgroundManager.getDimIntensity())
                    updateBackgroundFilters()
                }


            }
            weatherGetter.startUpdates(lat, lon)
        }
    }

    private fun startTutorialAnimation() {
        isTutorialRunning = true // flag to prevent permissions check interference during tutorial
        tutorialLayout.visibility = View.VISIBLE
        tutorialFinger.translationX = resources.displayMetrics.widthPixels.toFloat()
        tutorialFinger.translationY = -200f
        tutorialText.text = getString(R.string.tutorial_text_1)

        // --- Part 1: Demonstrate Entering Edit Mode ---
        tutorialLayout.animate().alpha(1f).setDuration(500).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                tutorialText.animate().alpha(1f).setDuration(800).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        tutorialText.animate().alpha(0f).setDuration(800).setStartDelay(1000)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator) {
                                    tutorialText.text = getString(R.string.tutorial_text_2)
                                    tutorialText.animate().alpha(1f).setDuration(500).setListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator) {
                                            tutorialFinger.animate()
                                                .alpha(1f)
                                                .translationX(mainLayout.width / 2f - tutorialFinger.width / 2f)
                                                .translationY(mainLayout.height / 2f - tutorialFinger.height / 2f)
                                                .setDuration(1200)
                                                .setListener(object : AnimatorListenerAdapter() {
                                                    override fun onAnimationEnd(animation: Animator) {
                                                        tutorialFinger.animate()
                                                            .scaleX(0.8f).scaleY(0.8f)
                                                            .setDuration(200)
                                                            .setStartDelay(300)
                                                            .setListener(object : AnimatorListenerAdapter() {
                                                                override fun onAnimationEnd(animation: Animator) {
                                                                    tutorialFinger.animate()
                                                                        .scaleX(0.8f).scaleY(0.8f)
                                                                        .setDuration(800)
                                                                        .setListener(object : AnimatorListenerAdapter() {
                                                                            override fun onAnimationEnd(animation: Animator) {
                                                                                toggleEditMode()
                                                                                tutorialText.text = getString(R.string.tutorial_text_3)
                                                                                val targetX = timeText.x + (timeText.width / 2f) - (tutorialFinger.width / 2f)
                                                                                val targetY = timeText.y + (timeText.height / 2f) - (tutorialFinger.height / 2f)

                                                                                tutorialFinger.animate()
                                                                                    .scaleX(1f).scaleY(1f)
                                                                                    .x(targetX).y(targetY)
                                                                                    .setDuration(1000)
                                                                                    .setStartDelay(800)
                                                                                    .setListener(object : AnimatorListenerAdapter() {
                                                                                        override fun onAnimationEnd(animation: Animator) {
                                                                                            tutorialFinger.animate()
                                                                                                .scaleX(0.8f).scaleY(0.8f)
                                                                                                .setDuration(150)
                                                                                                .setListener(object : AnimatorListenerAdapter() {
                                                                                                    override fun onAnimationEnd(animation: Animator) {
                                                                                                        showCustomizationBottomSheet(timeText)
                                                                                                        tutorialFinger.animate()
                                                                                                            .scaleX(1f).scaleY(1f)
                                                                                                            .setDuration(150)
                                                                                                            .setStartDelay(200)
                                                                                                            .setListener(object : AnimatorListenerAdapter() {
                                                                                                                override fun onAnimationEnd(animation: Animator) {
                                                                                                                    tutorialText.text = getString(R.string.tutorial_text_4)
                                                                                                                    tutorialFinger.animate()
                                                                                                                        .alpha(0f)
                                                                                                                        .y(targetY - 200)
                                                                                                                        .setDuration(500)
                                                                                                                        .start()
                                                                                                                    tutorialLayout.setOnClickListener {
                                                                                                                        hideBottomSheet()
                                                                                                                        tutorialLayout.animate().alpha(0f).setDuration(300)
                                                                                                                            .setListener(object : AnimatorListenerAdapter() {
                                                                                                                                override fun onAnimationEnd(animation: Animator) {
                                                                                                                                    tutorialLayout.visibility = View.GONE
                                                                                                                                    val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                                                                                                                                    prefs.edit().putBoolean("isFirstLaunch", false).apply()
                                                                                                                                    isTutorialRunning = false
                                                                                                                                    checkLocationPermissionsAndLoadData()
                                                                                                                                }
                                                                                                                            })
                                                                                                                    }
                                                                                                                }
                                                                                                            })
                                                                                                    }
                                                                                                })
                                                                                        }
                                                                                    })
                                                                            }
                                                                        })
                                                                }
                                                            })
                                                    }
                                                })
                                        }
                                    })
                                }
                            })
                    }
                })
            }
        })
    }

    // Handle add-image result from background bottom sheet
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_BG_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                // Persist read permission for the chosen URI so the app can access it later.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (e: Exception) {
                // ignore if cannot persist
            }
            val uriStr = uri.toString()
            // Persist the uri using BackgroundManager
            backgroundManager.addSavedUri(uriStr)
            // update adapter list (include default and add)
            val items = mutableListOf<String>().apply {
                add("__DEFAULT_GRADIENT__")
                addAll(backgroundManager.getSavedUriSet())
                add("__ADD__")
            }
            backgroundsAdapter?.updateItems(items)
            // preview and set preview selection
            previewBackgroundUri = uriStr
            applyImageBackground(uri, backgroundManager.getBlurIntensity())
        }
    }

    // Load saved background URI from preferences and apply it if present
    private fun loadSavedBackground() {
        val uriStr = backgroundManager.getSavedBackgroundUri()
        val blurIntensity = backgroundManager.getBlurIntensity()
        if (uriStr != null) {
            try {
                val uri = Uri.parse(uriStr)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                } catch (e: Exception) {

                }
                applyImageBackground(uri, blurIntensity)
                hasCustomImageBackground = true
                Log.d(
                    "MainActivity",
                    "Loaded custom background: $uriStr (blurIntensity=$blurIntensity)"
                )
                // Apply saved dimming settings
//                setBackgroundDimming(
//                    backgroundManager.getDimMode(),
//                    backgroundManager.getDimIntensity()
//                )
                updateBackgroundFilters()
            } catch (e: Exception) {
                backgroundManager.setSavedBackgroundUri(null)
                hasCustomImageBackground = false
                Log.w("MainActivity", "Failed to load saved background: ${e.message}")
            }
        } else {
            // ensure image view is hidden and gradient updates run
            backgroundImageView.visibility = View.GONE
            hasCustomImageBackground = false
            backgroundProgressOverlay.visibility = View.GONE
            gradientManager.startUpdates()
            updateBackgroundFilters()
        }
    }

    // Apply image background using Glide into the ImageView; blurIntensity > 0 enables blur with that radius
    fun applyImageBackground(uri: Uri, blurIntensity: Int = 0) {
        try {
            val targetMode = backgroundManager.getDimMode()
            val targetIntensity = backgroundManager.getDimIntensity()

            val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)

            val targetZoom = calculateZoom(effectiveIntensity)
            // Stop gradient updates immediately to conserve CPU/battery when previewing/applying an image
            gradientManager.stopUpdates()
            //Paint the background black while loading new image
            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.BLACK, Color.BLACK)
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                // Use the modern method on API 16+
                backgroundLayout.background = gradientDrawable
            } else {
                // Use the older, deprecated method on API < 16
                @Suppress("DEPRECATION")
                backgroundLayout.setBackgroundDrawable(gradientDrawable)
            }
            backgroundImageView.visibility = View.VISIBLE
            //if (backgroundManager.getDimMode() == BackgroundManager.DIM_MODE_DYNAMIC) {
                backgroundImageView.scaleX = targetZoom
                backgroundImageView.scaleY = targetZoom

                backgroundImageView.animate()
                    .scaleX(targetZoom + 0.4f)
                    .scaleY(targetZoom + 0.4f)
                    .alpha(0f)
                    .setDuration(700)
                    .setListener(null)
                    .start()
            //}
            //stop updating dimming while loading new image

            // Show a progress overlay while loading/processing the image from URI
            val loadingMessage =
                if (blurIntensity > 0) getString(R.string.blur_applying_message) else getString(R.string.loading_background_message)
            setBackgroundProgressVisible(true, loadingMessage)

            // On modern Android, prefer RenderEffect for high-quality blur on the ImageView layer
            val usePlatformBlur =
                blurIntensity > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            // Determine a reasonable target size to avoid loading very large bitmaps into memory.
            val metrics = resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels
            // Limit to a sensible cap (e.g. 1080p) to prevent massive bitmaps on very high-res photos
            val maxDim = 1080
            val targetW = minOf(screenW, maxDim)
            val targetH = minOf(screenH, maxDim)
            // Build Glide request with transformations
            val req = RequestOptions()
                .transform(CenterCrop()) // always center-crop
                .override(targetW, targetH) // target size
                .downsample(DownsampleStrategy.CENTER_INSIDE) // downsample if image is larger than target
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC) // cache strategy

            val finalReq = if (usePlatformBlur) {
                // don't pre-blur bitmaps; let the view's RenderEffect handle it
                req
            } else if (blurIntensity > 0) {
                // older devices: apply our BlurTransformation on the bitmap (Glide will run this off-main-thread)
                req.transform(CenterCrop(), BlurTransformation(blurIntensity))
            } else {
                req
            }

            GlideApp.with(this)
                .load(uri) // load from URI
                .apply(finalReq) // apply options
                .into(object :
                    com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        // Hide progress overlay (work is done)
                        setBackgroundProgressVisible(false)
                        backgroundImageView.setImageDrawable(resource) // set the loaded image
                        if (fontManager.isDynamicColorEnabled()) {
                            val bitmap = (resource as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                fontManager.updateDynamicColors(bitmap) {
                                    // Colors are ready, force a color recalculation
                                    fontManager.applyNightShiftTransition(
                                        clockManager.getCurrentTime(),
                                        dayTimeGetter,
                                        fontManager.isNightShiftEnabled()
                                    )
                                }
                            }
                        }
                        // Apply platform blur if available
                        if (usePlatformBlur) {
                            try {
                                val radiusPx = blurIntensity.coerceAtLeast(1).toFloat()
                                val renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                    radiusPx,
                                    radiusPx,
                                    android.graphics.Shader.TileMode.CLAMP
                                )
                                backgroundImageView.setRenderEffect(renderEffect)
                            } catch (e: Throwable) {
                                // if RenderEffect fails for any reason, we already set a transformed drawable via Glide fallback
                            }
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    backgroundImageView.setRenderEffect(null)
                                } catch (_: Throwable) {
                                }
                            }
                        }

                        backgroundImageView.visibility = View.VISIBLE

                        val targetMode = backgroundManager.getDimMode()
                        val targetIntensity = backgroundManager.getDimIntensity()

                        val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)

                        val targetZoom = calculateZoom(effectiveIntensity)
                            backgroundImageView.scaleX = targetZoom + 0.4f
                            backgroundImageView.scaleY = targetZoom + 0.4f
                            backgroundImageView.animate()
                                .scaleX(targetZoom)
                                .scaleY(targetZoom)
                                .alpha(1.0f)
                                .setDuration(700)
                                .setListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
//                                        setBackgroundDimming(
//                                            targetMode,
//                                            targetIntensity
//                                        )
                                        updateBackgroundFilters()
                                    }
                                }).start()
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        // Hide any overlay when load cleared
                        setBackgroundProgressVisible(false)

                    }

                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        setBackgroundProgressVisible(false)
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.failed_to_load_background),
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.w("MainActivity", "Glide failed to load background image from URI: $uri")
                        try {
                            restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                        } catch (e: Exception) {
                            TODO("Not yet implemented")
                            Log.w("MainActivity", "restoreUserBackground failed: ${e.message}")
                        }
                    }

                    override fun onLoadStarted(placeholder: android.graphics.drawable.Drawable?) {
                        super.onLoadStarted(placeholder)
                        // Show progress overlay when load starts
                        setBackgroundProgressVisible(true, getString(R.string.loading_background_message))
                    }

                })
            if (isAdvancedGraphicsEnabled) {
                editModeBlurLayer.visibility = View.VISIBLE
                val ambientReq = RequestOptions()
                    .transform(FitCenter())
                    .override(200, 400)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                val finalAmbientReq = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    ambientReq.transform(FitCenter(), BlurTransformation(35)) // Heavy blur
                } else {
                    ambientReq
                }

                GlideApp.with(this)
                    .load(uri)
                    .apply(finalAmbientReq)
                    .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                        ) {
                            editModeBlurLayer.setImageDrawable(resource)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                applyHeavyBlurToLayer(editModeBlurLayer)
                            } else {
                                editModeBlurLayer.setColorFilter(Color.parseColor("#C5000000"), PorterDuff.Mode.SRC_OVER)
                            }
                        }
                        override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                            editModeBlurLayer.setImageDrawable(null)
                        }
                    })
            } else {
                editModeBlurLayer.setImageDrawable(null)
                editModeBlurLayer.visibility = View.GONE
            }
        } catch (e: Exception) {
            setBackgroundProgressVisible(false)
            Log.w("MainActivity", "applyImageBackground(glide) failed: ${e.message}")
            Toast.makeText(this, getString(R.string.failed_to_load_background), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun getEffectiveDimIntensity(mode: Int, userIntensity: Int): Int {
        val intensity = if (mode == BackgroundManager.DIM_MODE_DYNAMIC) {
            try {
                backgroundManager.computeEffectiveDimIntensity(
                    clockManager.getCurrentTime(),
                    dayTimeGetter
                )
            } catch (e: Exception) {
                userIntensity.coerceIn(0, 50)
            }
        } else {
            userIntensity.coerceIn(0, 50)
        }
        return intensity.coerceIn(0, 50)
    }

    private fun calculateZoom(effectiveIntensity: Int): Float {
        return 1.0f + (effectiveIntensity.coerceIn(0, 50) / 50f) * 0.2f
    }

//    private fun setBackgroundDimming(mode: Int, intensity: Int) {
//        runOnUiThread {
//            try {
//                // mode: BackgroundManager.DIM_MODE_OFF/CONTINUOUS/DYNAMIC
//                if (mode == BackgroundManager.DIM_MODE_OFF || intensity <= 0) {
//                    backgroundImageView.clearColorFilter()
//                    backgroundImageView.alpha = 1.0f
//                    return@runOnUiThread
//                }
//
//                // Determine effective intensity. For dynamic mode, compute based on current time + sun times.
//                val effectiveIntensity = getEffectiveDimIntensity(mode, intensity)
//
//                // 2. Calculate parameters
//                val zoom = calculateZoom(effectiveIntensity)
//                val maxAlpha = 0.8f
//                val alpha = (effectiveIntensity / 50f) * maxAlpha
//
//                backgroundImageView.scaleX = zoom
//                backgroundImageView.scaleY = zoom
//                val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
//                val overlayColor = Color.argb(alphaInt, 0, 0, 0)
//                // Use SRC_OVER to darken the image
//                backgroundImageView.setColorFilter(overlayColor, PorterDuff.Mode.SRC_OVER)
//            } catch (e: Exception) {
//                Log.w("MainActivity", "setBackgroundDimming failed: ${e.message}")
//            }
//        }
//    }

    private fun updateBackgroundFilters() {
        if (backgroundImageView.visibility != View.VISIBLE) return

        val dimMode = backgroundManager.getDimMode()
        val dimIntensity = backgroundManager.getDimIntensity()
        val effectiveDim = getEffectiveDimIntensity(dimMode, dimIntensity)

        val maxDarkness = 0.8f
        val dimFactor = (effectiveDim / 50f) * maxDarkness
        val brightness = 1.0f - dimFactor

        val isWeatherEnabled = backgroundManager.isWeatherEffectsEnabled()
        val isNight = !dayTimeGetter.isDay()
        val combinedMatrix = ColorMatrix()

        if (isWeatherEnabled) {
            val isManual = backgroundManager.isManualWeatherEnabled()
            var wmoCode = 0
            var rawIntensity = 0f

            if (isManual) {
                val typeOrdinal = backgroundManager.getManualWeatherType()
                val type = WeatherView.WeatherType.values()[typeOrdinal]
                wmoCode = when (type) {
                    WeatherView.WeatherType.CLEAR -> 0
                    WeatherView.WeatherType.CLOUDY -> 3
                    WeatherView.WeatherType.FOG -> 45
                    WeatherView.WeatherType.RAIN -> 63
                    WeatherView.WeatherType.SNOW -> 73
                    WeatherView.WeatherType.THUNDERSTORM -> 95
                    else -> 0
                }
                rawIntensity = backgroundManager.getManualWeatherIntensity() / 100f
            } else {
                wmoCode = weatherGetter.weatherCode ?: 0
                rawIntensity = calculateWeatherIntensity(
                    wmoCode,
                    weatherGetter.windSpeed,
                    weatherGetter.precipitation,
                    weatherGetter.cloudCover,
                    weatherGetter.visibility
                )
            }

            val visualIntensity = rawIntensity * 0.2f

            val weatherMatrix = getWeatherMatrix(wmoCode, isNight, visualIntensity)
            combinedMatrix.postConcat(weatherMatrix)
        }

        val dimMatrix = ColorMatrix()
        dimMatrix.setScale(brightness, brightness, brightness, 1f)
        combinedMatrix.postConcat(dimMatrix)

        backgroundImageView.colorFilter = ColorMatrixColorFilter(combinedMatrix)

        val zoom = calculateZoom(effectiveDim)
        backgroundImageView.scaleX = zoom
        backgroundImageView.scaleY = zoom
    }


    private fun setBackgroundProgressVisible(visible: Boolean, message: String? = null) {
        runOnUiThread {
            if (visible) {
                backgroundProgressOverlay.visibility = View.VISIBLE
                backgroundProgressOverlay.alpha = 0f
                backgroundProgressOverlay.scaleX = 1.1f
                backgroundProgressOverlay.scaleY = 1.1f
                backgroundProgressOverlay.animate()
                    .alpha(0.55f)
                    .setDuration(300)
                    .setListener(null)
                    .start()
                backgroundProgressOverlay.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(300)
                    .setListener(null)
                    .start()
            } else {
                backgroundProgressOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            backgroundProgressOverlay.visibility = View.GONE
                        }
                    })
                    .start()
                backgroundProgressOverlay.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(300)
                    .setListener(null)
                    .start()
            }
            backgroundProgressText.isSelected = true // enable marquee
            if (message != null) backgroundProgressText.text = message
        }
    }

    // Show the dedicated backgrounds bottom sheet
    private fun showBackgroundBottomSheet() {
        isUpdatingBackgroundUi = true

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val bottomSheetDp = 380f
        val bottomSheetPx = bottomSheetDp * metrics.density
        val targetScale = 0.60f
        val translationX = (screenW * (1f - targetScale) / 2f) - bottomSheetPx

        mainLayout.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationX(translationX)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()

        val items = mutableListOf<String>().apply {
            add("__DEFAULT_GRADIENT__")
            addAll(backgroundManager.getSavedUriSet())
            add("__ADD__")
        }
        backgroundsAdapter?.updateItems(items)

        val savedUri = backgroundManager.getSavedBackgroundUri()
        val currentSelectionId = savedUri ?: "__DEFAULT_GRADIENT__"
        backgroundsAdapter?.selectedId = currentSelectionId
        bgRecycler.scrollToPosition(0)

        val blurInt = backgroundManager.getBlurIntensity()
        bgBlurSeek.progress = blurInt
        bgBlurSwitch.isChecked = blurInt > 0

        val dimMode = backgroundManager.getDimMode()
        val dimIntensity = backgroundManager.getDimIntensity()

        when (dimMode) {
            BackgroundManager.DIM_MODE_OFF -> bgDimToggleGroup.check(R.id.off_button)
            BackgroundManager.DIM_MODE_CONTINUOUS -> bgDimToggleGroup.check(R.id.continuous_button)
            BackgroundManager.DIM_MODE_DYNAMIC -> bgDimToggleGroup.check(R.id.dynamic_button)
            else -> bgDimToggleGroup.check(R.id.off_button)
        }
        bgDimSeek.progress = dimIntensity

        val isWeatherEnabled = backgroundManager.isWeatherEffectsEnabled()
        val isManual = backgroundManager.isManualWeatherEnabled()

        bgWeatherSwitch.isChecked = isWeatherEnabled
        bgManualWeatherSwitch.isChecked = isManual
        bgManualWeatherSwitch.isEnabled = isWeatherEnabled

        bgManualWeatherScroll.visibility = if (isManual && isWeatherEnabled) View.VISIBLE else View.GONE
        bgIntensityContainer.visibility = if (isManual && isWeatherEnabled) View.VISIBLE else View.GONE

        bgIntensitySeek.progress = backgroundManager.getManualWeatherIntensity()

        val savedType = backgroundManager.getManualWeatherType()
        val buttonId = when (savedType) {
            WeatherView.WeatherType.RAIN.ordinal -> R.id.btn_weather_rain
            WeatherView.WeatherType.SNOW.ordinal -> R.id.btn_weather_snow
            WeatherView.WeatherType.FOG.ordinal -> R.id.btn_weather_fog
            WeatherView.WeatherType.THUNDERSTORM.ordinal -> R.id.btn_weather_thunder
            WeatherView.WeatherType.CLOUDY.ordinal -> R.id.btn_weather_cloudy
            WeatherView.WeatherType.CLEAR.ordinal -> R.id.btn_weather_clear
            else -> R.id.btn_weather_rain
        }
        bgWeatherToggleGroup.check(buttonId)

        isUpdatingBackgroundUi = false

        backgroundBottomSheetBehavior.state = STATE_EXPANDED
    }

    private fun applyWeatherState(previewManual: Boolean? = null) {
        val isEnabled = findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.weather_effect_switch)?.isChecked
            ?: backgroundManager.isWeatherEffectsEnabled()

        if (!isEnabled) {
            weatherView.visibility = View.GONE
            return
        }
        weatherView.visibility = View.VISIBLE

        val isNight = !dayTimeGetter.isDay()


        val isManual = previewManual ?: backgroundManager.isManualWeatherEnabled()

        if (isManual) {

            val group = findViewById<MaterialButtonToggleGroup>(R.id.weather_type_toggle_group)
            val typeOrdinal = if (group != null && group.checkedButtonId != View.NO_ID) {
                when (group.checkedButtonId) {
                    R.id.btn_weather_clear -> WeatherView.WeatherType.CLEAR.ordinal
                    R.id.btn_weather_cloudy -> WeatherView.WeatherType.CLOUDY.ordinal
                    R.id.btn_weather_rain -> WeatherView.WeatherType.RAIN.ordinal
                    R.id.btn_weather_snow -> WeatherView.WeatherType.SNOW.ordinal
                    R.id.btn_weather_fog -> WeatherView.WeatherType.FOG.ordinal
                    R.id.btn_weather_thunder -> WeatherView.WeatherType.THUNDERSTORM.ordinal
                    else -> backgroundManager.getManualWeatherType()
                }
            } else {
                backgroundManager.getManualWeatherType()
            }

            val type = WeatherView.WeatherType.values()[typeOrdinal]
            val progress = backgroundManager.getManualWeatherIntensity()
            val floatIntensity = progress / 100f

            weatherView.forceWeather(type, floatIntensity, 5.0f, isNight)

            if (hasCustomImageBackground) {
                when(type) {
                    WeatherView.WeatherType.CLEAR -> 0
                    WeatherView.WeatherType.CLOUDY -> 3
                    WeatherView.WeatherType.RAIN -> 63
                    WeatherView.WeatherType.SNOW -> 73
                    WeatherView.WeatherType.FOG -> 45
                    WeatherView.WeatherType.THUNDERSTORM -> 95
                    else -> 0
                }
//                backgroundImageView.applyWeatherFilter(fakeWmo, !dayTimeGetter.isDay())
            updateBackgroundFilters()
            }

        } else {
            val code = weatherGetter.weatherCode ?: 0
            val wind = weatherGetter.windSpeed ?: 0.0
            weatherView.updateFromOpenMeteoSmart(
                code, wind, isNight,
                precipitation = weatherGetter.precipitation,
                cloudCover = weatherGetter.cloudCover,
                visibility = weatherGetter.visibility
            )
            if (hasCustomImageBackground) {
//                backgroundImageView.applyWeatherFilter(code, !dayTimeGetter.isDay())
                updateBackgroundFilters()
            }
        }
    }

    private fun setupBottomSheet() {
        val fontRecyclerView = bottomSheet.findViewById<RecyclerView>(R.id.font_recycler_view)
        fontRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        // Let the parent NestedScrollView handle scrolling gestures
        fontRecyclerView.isNestedScrollingEnabled = false
        // Ensure bottom sheet behavior is configured for nested scrolling
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.isHideable = true
        // Allow dragging the bottom sheet (ensures NestedScrollView can pass drag gestures)
        bottomSheetBehavior.isDraggable = true
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == STATE_HIDDEN) {
                    //highlightImageView(false)
                    when (newState) {
                        STATE_DRAGGING,
                        STATE_SETTLING,
                        STATE_EXPANDED,
                        STATE_HALF_EXPANDED -> {
                            stopHideUiTimer()
                        }

                        STATE_COLLAPSED,
                        STATE_HIDDEN -> {
                            resetEditModeTimeout()
                            hideBottomSheet()
                        }
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        backgroundBottomSheetBehavior.peekHeight = 0
        backgroundBottomSheetBehavior.isHideable = true
        backgroundBottomSheetBehavior.isDraggable = true
        backgroundBottomSheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    STATE_DRAGGING,
                    STATE_SETTLING,
                    STATE_EXPANDED,
                    STATE_HALF_EXPANDED -> {
                        stopHideUiTimer()
                    }

                    STATE_COLLAPSED,
                    STATE_HIDDEN -> {
                        mainLayout.animate()
                            .scaleX(0.90f)
                            .scaleY(0.90f)
                            .translationX(0f)
                            .setDuration(animationDuration)
                            .setInterpolator(OvershootInterpolator())
                            .start()

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            backgroundCustomizationTab.visibility = View.VISIBLE
                            backgroundCustomizationTab.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .start()
                        } else {
                            backgroundCustomizationTab.visibility = View.VISIBLE
                        }

                        resetEditModeTimeout()
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
            bottomSheet.bringToFront()
            backgroundBottomSheet.bringToFront()
    }

    private fun showCustomizationBottomSheet(viewToCustomize: View) {
        isBottomSheetInitializing = true
        focusedView = viewToCustomize

        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels.toFloat()
        val bottomSheetDp = 380f
        val bottomSheetPx = bottomSheetDp * metrics.density
        val targetScale = 0.60f
        val translationX = (screenW * (1f - targetScale) / 2f) - bottomSheetPx

        mainLayout.animate()
            .scaleX(targetScale)
            .scaleY(targetScale)
            .translationX(translationX)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()

        handler.removeCallbacks(editModeTimeoutRunnable)


        val currentSize: Float
        val currentAlpha: Float

        when (viewToCustomize.id) {
            R.id.time_text -> {
                bsTitle.text = getString(R.string.customize_time)
                bsTimeFormatGroup.visibility = View.VISIBLE
                bsTimeFormatLabel.visibility = View.VISIBLE
                bsDateFormatGroup.visibility = View.GONE
                bsDateFormatLabel.visibility = View.GONE


                bsTimeFormatGroup.check(
                    if (fontManager.getTimeFormatPattern().contains("H")) R.id.time_24_radio else R.id.time_12_radio
                )

                currentSize = fontManager.getTimeSize()
                currentAlpha = fontManager.getTimeAlpha()
            }
            R.id.date_text -> {
                bsTitle.text = getString(R.string.customize_date)
                bsTimeFormatGroup.visibility = View.GONE
                bsTimeFormatLabel.visibility = View.GONE
                bsDateFormatGroup.visibility = View.VISIBLE
                bsDateFormatLabel.visibility = View.VISIBLE

                val pattern = fontManager.getDateFormatPattern()
                val id = when (pattern) {
                    "MMM dd" -> R.id.date_format_1
                    "EEE, MMM dd" -> R.id.date_format_2
                    "EEEE, MMMM dd, yyyy" -> R.id.date_format_3
                    else -> R.id.date_format_2
                }
                bsDateFormatGroup.check(id)

                currentSize = fontManager.getDateSize()
                currentAlpha = fontManager.getDateAlpha()
            }
            R.id.lastfm_layout -> {
                bsTitle.text = getString(R.string.customize_now_playing)
                bsTimeFormatGroup.visibility = View.GONE
                bsDateFormatGroup.visibility = View.GONE
                bsTimeFormatLabel.visibility = View.GONE
                bsDateFormatLabel.visibility = View.GONE

                currentSize = fontManager.getLastfmSize()
                currentAlpha = fontManager.getLastfmAlpha()
            }
            else -> {
                hideBottomSheet()
                return
            }
        }

        highlightFocusedView(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            backgroundCustomizationTab.animate().alpha(0f).setDuration(200).withEndAction {
                backgroundCustomizationTab.visibility = View.GONE
            }.start()
        } else {
            backgroundCustomizationTab.visibility = View.GONE
        }

        val maxvalue = (metrics.widthPixels / metrics.density * 0.3).toInt()
        bsSizeSeekBar.max = maxvalue - 20
        bsSizeSeekBar.progress = (currentSize - 20).toInt()
        bsSizeValue.text = getString(R.string.size_value_format, currentSize.toInt())

        bsTransparencySeekBar.max = 100
        bsTransparencySeekBar.progress = (currentAlpha * 100).toInt()
        bsTransparencyPreview.alpha = currentAlpha

        bsNightShiftSwitch.isChecked = fontManager.isNightShiftEnabled()
        bsFreeModeSwitch.isChecked = widgetMover.isFreeMovementEnabled()
        bsGridSnapSwitch.isChecked = widgetMover.isGridSnapEnabled()


        val savedGravity = widgetMover.getInternalGravity(viewToCustomize)
        val gravityBtnId = when (savedGravity) {
            widgetMover.GRAVITY_START -> R.id.gravity_left_button
            widgetMover.GRAVITY_CENTER -> R.id.gravity_center_button
            widgetMover.GRAVITY_END -> R.id.gravity_right_button
            else -> R.id.gravity_center_button
        }
        if (bsTextGravityGroup.checkedButtonId != gravityBtnId) {
            bsTextGravityGroup.check(gravityBtnId)
        }

        val savedValign = widgetMover.getAlignmentOnlyV(viewToCustomize)
        val vAlignBtnId = when (savedValign) {
            widgetMover.ALIGN_V_TOP -> R.id.align_top_button
            widgetMover.ALIGN_V_CENTER -> R.id.align_center_vertical_button
            widgetMover.ALIGN_V_BOTTOM -> R.id.align_bottom_button
            else -> View.NO_ID
        }
        if (bsVerticalAlignGroup.checkedButtonId != vAlignBtnId) {
            bsVerticalAlignGroup.check(vAlignBtnId)
        }

        val savedHalign = widgetMover.getAlignmentOnlyH(viewToCustomize)
        val hAlignBtnId = when (savedHalign) {
            widgetMover.ALIGN_H_LEFT -> R.id.left_button
            widgetMover.ALIGN_H_CENTER -> R.id.center_button
            widgetMover.ALIGN_H_RIGHT -> R.id.right_button
            else -> View.NO_ID
        }
        if (bsHorizontalAlignGroup.checkedButtonId != hAlignBtnId) {
            bsHorizontalAlignGroup.check(hAlignBtnId)
        }

        isBottomSheetInitializing = false
        bottomSheetBehavior.state = STATE_EXPANDED
    }

    private fun hideBottomSheet() {
        bottomSheetBehavior.state = STATE_HIDDEN
        highlightFocusedView(false) // Use the new unified function
        resetEditModeTimeout()
        mainLayout.animate()
            .scaleX(0.90f)
            .scaleY(0.90f)
            .translationX(0f)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()
        focusedView = null
        // Restore background customization button with animation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            backgroundCustomizationTab.visibility = View.VISIBLE
            backgroundCustomizationTab.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else {
            backgroundCustomizationTab.visibility = View.VISIBLE
        }
    }

    private fun hideBackgroundBottomSheet() {
        backgroundBottomSheetBehavior.state = STATE_HIDDEN
        resetEditModeTimeout()
        mainLayout.animate()
            .scaleX(0.90f)
            .scaleY(0.90f)
            .translationX(0f)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()
        // Restore background customization button with animation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            backgroundCustomizationTab.visibility = View.VISIBLE
            backgroundCustomizationTab.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else {
            backgroundCustomizationTab.visibility = View.VISIBLE
        }
    }

    private fun highlightFocusedView(isHighlighted: Boolean) {
        focusedView?.let { view ->
            val scale = if (isHighlighted) 1.05f else 1.0f

            view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(animationDuration)
                .start()

            if (isHighlighted) {
                view.setBackgroundResource(R.drawable.editable_border)
            } else {
                // Only remove the background if we are not in edit mode anymore.
                // This prevents the border from disappearing when switching focus.
                if (!isEditMode) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        view.background = null
                    } else {
                        @Suppress("DEPRECATION")
                        view.setBackgroundDrawable(null)
                    }
                }
            }
        }
    }

//    private fun highlightImageView(isHighlighted: Boolean) {
//        if (isHighlighted) {
//            val targetMode = backgroundManager.getDimMode()
//            val targetIntensity = backgroundManager.getDimIntensity()
//            val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)
//            val targetScale = calculateZoom(effectiveIntensity)
//            backgroundImageView.animate()
//                .scaleX(targetScale + 0.4f)
//                .scaleY(targetScale + 0.4f)
//                .setDuration(animationDuration)
//                .start()
//            //backgroundImageView.setBackgroundResource(R.drawable.editable_border)
//        } else {
//            val targetMode = backgroundManager.getDimMode()
//            val targetIntensity = backgroundManager.getDimIntensity()
//            val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)
//            val targetScale = calculateZoom(effectiveIntensity)
//            backgroundImageView.animate()
//                .scaleX(targetScale)
//                .scaleY(targetScale)
//                .setDuration(animationDuration)
//                .start()
//            // if (!isEditMode) backgroundImageView.background = null
//        }
//    }

    private fun startUpdates() {
        clockManager.startUpdates()
        musicGetter.startUpdates()
        // Only start gradient updates if there is no custom image background
        if (!hasCustomImageBackground) {
            gradientManager.startUpdates()
        }
        smartChipManager.startUpdates()
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val enableBurnInProtection = prefs.getBoolean("burn_in_protection", false)
        if (enableBurnInProtection) {
            burnInProtectionManager?.start()
        } else {
            burnInProtectionManager?.stop()
        }
    }

    private fun stopUpdates() {
        clockManager.stopUpdates()
        musicGetter.stopUpdates()
        gradientManager.stopUpdates()
        smartChipManager.stopUpdates()
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun animateCornerRadius(view: View, fromRadius: Float, toRadius: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val animator = android.animation.ValueAnimator.ofFloat(fromRadius, toRadius)
            animator.duration = animationDuration-100L

            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Float

                view.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, value)
                    }
                }
                view.clipToOutline = value > 0f
            }
            animator.start()
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
       // musicGetter.stopUpdates()
        //widgetMover.resetLayoutConstraints()
        widgetMover.setEditMode(isEditMode)
        val targetRadius = dpToPx(36f)
        if (isEditMode) {
            settingsButton.visibility = View.VISIBLE
            debugButton.visibility = View.VISIBLE
            // backgroundButton.visibility = View.VISIBLE
            backgroundCustomizationTab.visibility = View.VISIBLE
            mainLayout.animate()
                .scaleX(0.90f)
                .scaleY(0.90f)
                .setDuration(animationDuration)
                .setInterpolator(OvershootInterpolator())
                .start()
            animateCornerRadius(mainLayout, 0f, targetRadius)
            if (isAdvancedGraphicsEnabled && hasCustomImageBackground) {
                editModeBlurLayer.visibility = View.VISIBLE
                editModeBlurLayer.alpha = 1.0f
            }
            settingsButton.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            debugButton.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            //backgroundButton.animate()
            //    .alpha(1f)
            //     .setDuration(animationDuration)
            //    .start()
            backgroundCustomizationTab.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()

            timeText.setBackgroundResource(R.drawable.editable_border)
            dateText.setBackgroundResource(R.drawable.editable_border)
            lastfmLayout.setBackgroundResource(R.drawable.editable_border)
            lastfmLayout.animate().cancel()
            lastfmLayout.clearAnimation()
            lastfmLayout.post {
                lastfmLayout.visibility = View.VISIBLE
                lastfmLayout.alpha = 1f
                lastfmLayout.setBackgroundResource(R.drawable.editable_border)
               // Log.d("EditMode", "lastfmLayout set via post(): visibility=${lastfmLayout.visibility}, alpha=${lastfmLayout.alpha}")
            }
            lastfmLayout.alpha = 1f
            lastfmLayout.visibility = View.VISIBLE
            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        } else {
            exitEditMode()
        }
    }

    private fun resetEditModeTimeout() {
        if (!isDemoMode) {
            handler.removeCallbacks(editModeTimeoutRunnable)
            handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
        }
    }

    private fun stopHideUiTimer() {
        handler.removeCallbacks(editModeTimeoutRunnable)
    }


    private fun exitEditMode() {
        isEditMode = false
        mainLayout.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()
        val startRadius = dpToPx(36f)
        animateCornerRadius(mainLayout, startRadius, 0f)
        if (editModeBlurLayer.visibility == View.VISIBLE) {
            editModeBlurLayer.animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isEditMode) {
                            editModeBlurLayer.visibility = View.GONE
                        }
                    }
                })
                .start()
        }
        settingsButton.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        debugButton.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        //backgroundButton.animate()
        //    .alpha(0f)
         //   .setDuration(animationDuration)
         //   .start()
        backgroundCustomizationTab.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            timeText.background = null
        } else
            timeText.setBackgroundDrawable(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            dateText.background = null
        } else
            dateText.setBackgroundDrawable(null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            lastfmLayout.background = null
        } else
            lastfmLayout.setBackgroundDrawable(null)

        if (!musicGetter.enabled || musicGetter.currentTrack.isNullOrEmpty()) {
            lastfmLayout.visibility = View.GONE
        }
       // musicGetter.startUpdates()
        settingsButton.visibility = View.GONE
        debugButton.visibility = View.GONE
       // backgroundButton.visibility = View.GONE
        backgroundCustomizationTab.visibility = View.GONE
        handler.removeCallbacks(editModeTimeoutRunnable)
        //Toast.makeText(this, R.string.edit_mode_disabled, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        locationManager.loadCoordinates { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (!hasCustomImageBackground) gradientManager.updateGradient()
                if (isNightShiftEnabled) {
                    fontManager.applyNightShiftTransition(
                        clockManager.getCurrentTime(),
                        dayTimeGetter,
                        isNightShiftEnabled
                    )
                }
                // Recompute dynamic dimming on resume in case sun times changed
                if (backgroundImageView.visibility == View.VISIBLE) {
//                    setBackgroundDimming(backgroundManager.getDimMode(), backgroundManager.getDimIntensity())
                    updateBackgroundFilters()
                }
             }
         }
         if (isEditMode) exitEditMode()
         if (isDemoMode) {
            isDemoMode = false
            clockManager.toggleDebugMode(false)
            gradientManager.toggleDebugMode(false)
         }

         val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
         enableAdditionalLogging = prefs.getBoolean("additional_logging", false)
        isAdvancedGraphicsEnabled = prefs.getBoolean("advanced_graphics", false)
         clockManager.setAdditionalLogging(enableAdditionalLogging)
         fontManager.setAdditionalLogging(enableAdditionalLogging)
        if (batteryLevelReceiver == null) {
            batteryLevelReceiver = BatteryLevelReceiver()
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(batteryLevelReceiver, filter)
        }
        lightSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        smartChipManager.updateAllChips()
         startUpdates()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun restoreGradientBackground() {
        backgroundImageView.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                backgroundImageView.setRenderEffect(null)
            } catch (_: Throwable) {}
        }
//        backgroundImageView.clearColorFilter()
        hasCustomImageBackground = false
        editModeBlurLayer.setImageDrawable(null)
        editModeBlurLayer.visibility = View.GONE
        gradientManager.startUpdates()
    }

    private fun initCustomizationControls() {
        bsTitle = bottomSheet.findViewById(R.id.customization_title)
        bsSizeSeekBar = bottomSheet.findViewById(R.id.size_seekbar)
        bsSizeValue = bottomSheet.findViewById(R.id.size_value)
        bsTransparencySeekBar = bottomSheet.findViewById(R.id.transparency_seekbar)
        bsTransparencyPreview = bottomSheet.findViewById(R.id.transparency_preview)
        bsFontRecyclerView = bottomSheet.findViewById(R.id.font_recycler_view)
        bsApplyButton = bottomSheet.findViewById(R.id.apply_button)
        bsCancelButton = bottomSheet.findViewById(R.id.cancel_button)
        bsNightShiftSwitch = bottomSheet.findViewById(R.id.night_shift_switch)
        bsTimeFormatGroup = bottomSheet.findViewById(R.id.time_format_radio_group)
        bsDateFormatGroup = bottomSheet.findViewById(R.id.date_format_radio_group)
        bsTimeFormatLabel = bottomSheet.findViewById(R.id.time_format_label)
        bsDateFormatLabel = bottomSheet.findViewById(R.id.date_format_label)
        bsFreeModeSwitch = bottomSheet.findViewById(R.id.free_mode_switch)
        bsGridSnapSwitch = bottomSheet.findViewById(R.id.grid_snap_switch)
        bsTextGravityGroup = bottomSheet.findViewById(R.id.text_gravity_toggle_group)
        bsHorizontalAlignGroup = bottomSheet.findViewById(R.id.alignment_toggle_group)
        bsVerticalAlignGroup = bottomSheet.findViewById(R.id.vertical_alignment_group)
        bsMoveUpBtn = bottomSheet.findViewById(R.id.move_up_button)
        bsMoveDownBtn = bottomSheet.findViewById(R.id.move_down_button)


        bsSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || focusedView == null) return
                val size = (progress + 20).toFloat()
                bsSizeValue.text = getString(R.string.size_value_format, size.toInt())

                when (focusedView?.id) {
                    R.id.time_text -> fontManager.setTimeSize(size)
                    R.id.date_text -> fontManager.setDateSize(size)
                    R.id.lastfm_layout -> fontManager.setLastfmSize(size)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bsTransparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || focusedView == null) return
                val alpha = progress / 100f
                bsTransparencyPreview.alpha = alpha

                when (focusedView?.id) {
                    R.id.time_text -> fontManager.setTimeAlpha(alpha)
                    R.id.date_text -> fontManager.setDateAlpha(alpha)
                    R.id.lastfm_layout -> fontManager.setLastfmAlpha(alpha)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bsNightShiftSwitch.setOnCheckedChangeListener { _, isChecked ->
            isNightShiftEnabled = isChecked
            fontManager.setNightShiftEnabled(isChecked)
        }

        bsFreeModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            widgetMover.setFreeMovementEnabled(isChecked)
        }

        bsGridSnapSwitch.setOnCheckedChangeListener { _, isChecked ->
            widgetMover.setGridSnapEnabled(isChecked)
        }

        bsTextGravityGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.gravity_left_button -> widgetMover.GRAVITY_START
                R.id.gravity_center_button -> widgetMover.GRAVITY_CENTER
                R.id.gravity_right_button -> widgetMover.GRAVITY_END
                else -> widgetMover.GRAVITY_CENTER
            }
            widgetMover.setTextGravity(focusedView!!, mode)
        }

        bsHorizontalAlignGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.left_button -> 0
                R.id.center_button -> 1
                R.id.right_button -> 2
                else -> 0
            }
            widgetMover.alignViewHorizontal(focusedView!!, mode)
        }

        bsVerticalAlignGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || focusedView == null) return@addOnButtonCheckedListener
            val mode = when (checkedId) {
                R.id.align_top_button -> 0
                R.id.align_center_vertical_button -> 1
                R.id.align_bottom_button -> 2
                else -> 0
            }
            widgetMover.alignViewVertical(focusedView!!, mode)
        }

        bsMoveUpBtn.setOnClickListener {
            focusedView?.let { widgetMover.moveWidgetOrder(it, true) }
        }

        bsMoveDownBtn.setOnClickListener {
            focusedView?.let { widgetMover.moveWidgetOrder(it, false) }
        }

        bsTimeFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (focusedView?.id == R.id.time_text) {
                val pattern = if (checkedId == R.id.time_24_radio) "HH:mm" else "hh:mm a"
                fontManager.setTimeFormatPattern(pattern)
                clockManager.updateTimeText()
            }
        }

        bsDateFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (focusedView?.id == R.id.date_text) {
                val pattern = when (checkedId) {
                    R.id.date_format_1 -> "MMM dd"
                    R.id.date_format_2 -> "EEE, MMM dd"
                    R.id.date_format_3 -> "EEEE, MMMM dd, yyyy"
                    else -> "EEE, MMM dd"
                }
                fontManager.setDateFormatPattern(pattern)
                clockManager.updateDateText()
            }
        }

        bsApplyButton.setOnClickListener {
            fontManager.saveSettings()
            hideBottomSheet()
        }

        bsCancelButton.setOnClickListener {
            fontManager.loadFont()
            widgetMover.restoreOrderAndPositions()
            hideBottomSheet()
        }

        val fontAdapter = FontAdapter(fontManager.getFonts()) { fontId ->
            when (focusedView?.id) {
                R.id.time_text -> fontManager.setTimeFont(fontId)
                R.id.date_text -> fontManager.setDateFont(fontId)
                R.id.lastfm_layout -> fontManager.setLastfmFont(fontId)
            }
        }
        bsFontRecyclerView.adapter = fontAdapter
    }

    private fun initBackgroundControls() {
        val sheet = findViewById<LinearLayout>(R.id.background_bottom_sheet)
        bgRecycler = sheet.findViewById(R.id.background_recycler_view)
        bgBlurSwitch = sheet.findViewById(R.id.background_blur_switch)
        bgBlurSeek = sheet.findViewById(R.id.blur_intensity_seekbar)
        bgDimToggleGroup = sheet.findViewById(R.id.dimming_toggle_group)
        bgDimSeek = sheet.findViewById(R.id.dimming_intensity_seekbar)
        bgClearBtn = sheet.findViewById(R.id.clear_background_button_bs)
        bgApplyBtn = sheet.findViewById(R.id.apply_background_button)
        bgWeatherSwitch = sheet.findViewById(R.id.weather_effect_switch)
        bgManualWeatherSwitch = sheet.findViewById(R.id.manual_weather_switch)
        bgManualWeatherScroll = sheet.findViewById(R.id.manual_weather_scroll)
        bgWeatherToggleGroup = sheet.findViewById(R.id.weather_type_toggle_group)
        bgIntensityContainer = sheet.findViewById(R.id.weather_intensity_container)
        bgIntensitySeek = sheet.findViewById(R.id.weather_intensity_seekbar)

        bgRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        bgRecycler.isNestedScrollingEnabled = false

        backgroundsAdapter = BackgroundsAdapter(this, mutableListOf()) { id ->
            when (id) {
                "__ADD__" -> {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                    } else {
                        Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                    }
                    try {
                        startActivityForResult(intent, PICK_BG_REQUEST)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_SHORT).show()
                    }
                }
                "__DEFAULT_GRADIENT__" -> {
                    previewBackgroundUri = "__DEFAULT_GRADIENT__"
                    fontManager.clearDynamicColors()
                    fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, fontManager.isNightShiftEnabled())

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try { backgroundImageView.setRenderEffect(null) } catch (_: Throwable) {}
                    }
                    backgroundImageView.setImageDrawable(null)
                    backgroundImageView.visibility = View.GONE
                    gradientManager.startUpdates()
                }
                else -> {
                    try {
                        val uri = Uri.parse(id)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            try {
                                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: Exception) { /* ignore */ }
                        }
                        val intensity = bgBlurSeek.progress
                        gradientManager.stopUpdates()
                        applyImageBackground(uri, intensity)
                        previewBackgroundUri = id
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Error selecting background: $id", e)
                    }
                }
            }
        }
        bgRecycler.adapter = backgroundsAdapter


        bgBlurSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            if (!isChecked) {
                bgBlurSeek.progress = 0
            } else if (bgBlurSeek.progress == 0) {
                bgBlurSeek.progress = 25
            }
        }

        bgBlurSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                bgBlurSwitch.isChecked = progress > 0
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })


        bgDimToggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked || isUpdatingBackgroundUi) {
                if (group.checkedButtonId == View.NO_ID && !isUpdatingBackgroundUi) group.check(checkedId) // Prevent uncheck all
                return@addOnButtonCheckedListener
            }

            val previewMode = when (checkedId) {
                R.id.off_button -> BackgroundManager.DIM_MODE_OFF
                R.id.continuous_button -> BackgroundManager.DIM_MODE_CONTINUOUS
                R.id.dynamic_button -> BackgroundManager.DIM_MODE_DYNAMIC
                else -> BackgroundManager.DIM_MODE_OFF
            }
            if (previewMode == BackgroundManager.DIM_MODE_OFF) bgDimSeek.progress = 0
            if (backgroundImageView.visibility == View.VISIBLE) updateBackgroundFilters()
        }

        bgDimSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (backgroundImageView.visibility == View.VISIBLE) updateBackgroundFilters()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bgIntensitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                updateManualWeatherPreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        bgWeatherToggleGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked && !isUpdatingBackgroundUi) {
                updateManualWeatherPreview()
            }
        }

        bgWeatherSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener

            bgManualWeatherSwitch.isEnabled = isChecked
            bgManualWeatherScroll.visibility = if (isChecked && bgManualWeatherSwitch.isChecked) View.VISIBLE else View.GONE
            weatherView.visibility = if (isChecked) View.VISIBLE else View.GONE
            bgIntensityContainer.visibility = if (isChecked) View.VISIBLE else View.GONE

            if (!isChecked) {
                val isNight = !dayTimeGetter.isDay()
                weatherView.updateFromOpenMeteoSmart(
                    0, 0.0, isNight,
                    precipitation = null,
                    cloudCover = null,
                    visibility = null
                )
                updateBackgroundFilters()
            } else {
                applyWeatherState()
            }
        }

        bgManualWeatherSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingBackgroundUi) return@setOnCheckedChangeListener
            bgManualWeatherScroll.visibility = if (isChecked) View.VISIBLE else View.GONE
            applyWeatherState(previewManual = isChecked)
        }


        // --- BUTTONS ---
        bgClearBtn.setOnClickListener {
            backgroundManager.setSavedBackgroundUri(null)
            previewBackgroundUri = null
            hasCustomImageBackground = false

            backgroundImageView.setImageDrawable(null)
            fontManager.clearDynamicColors()
            fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, fontManager.isNightShiftEnabled())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { backgroundImageView.setRenderEffect(null) } catch (_: Throwable) {}
            }
            backgroundImageView.visibility = View.GONE
            backgroundManager.clearDim()
            gradientManager.startUpdates()
            backgroundBottomSheetBehavior.state = STATE_HIDDEN
        }

        bgApplyBtn.setOnClickListener {
            applyBackgroundSettings()
        }
    }

    private fun updateManualWeatherPreview() {
        val floatIntensity = bgIntensitySeek.progress / 100f
        val checkedId = bgWeatherToggleGroup.checkedButtonId
        val type = when (checkedId) {
            R.id.btn_weather_clear -> WeatherView.WeatherType.CLEAR
            R.id.btn_weather_cloudy -> WeatherView.WeatherType.CLOUDY
            R.id.btn_weather_rain -> WeatherView.WeatherType.RAIN
            R.id.btn_weather_snow -> WeatherView.WeatherType.SNOW
            R.id.btn_weather_fog -> WeatherView.WeatherType.FOG
            R.id.btn_weather_thunder -> WeatherView.WeatherType.THUNDERSTORM
            else -> WeatherView.WeatherType.RAIN
        }
        val isNight = !dayTimeGetter.isDay()
        weatherView.forceWeather(type, floatIntensity, 5.0f, isNight)
        updateBackgroundFilters()
    }

    private fun applyBackgroundSettings() {
        val intensity = bgBlurSeek.progress
        backgroundManager.setBlurIntensity(intensity)

        val checkedId = bgDimToggleGroup.checkedButtonId
        val modeToSave = when (checkedId) {
            R.id.off_button -> BackgroundManager.DIM_MODE_OFF
            R.id.continuous_button -> BackgroundManager.DIM_MODE_CONTINUOUS
            R.id.dynamic_button -> BackgroundManager.DIM_MODE_DYNAMIC
            else -> BackgroundManager.DIM_MODE_OFF
        }
        backgroundManager.setDimMode(modeToSave)
        backgroundManager.setDimIntensity(bgDimSeek.progress)

        backgroundManager.setWeatherEffectsEnabled(bgWeatherSwitch.isChecked)
        backgroundManager.setManualWeatherEnabled(bgManualWeatherSwitch.isChecked)

        val selectedBtn = bgWeatherToggleGroup.checkedButtonId
        val typeToSave = when (selectedBtn) {
            R.id.btn_weather_clear -> WeatherView.WeatherType.CLEAR.ordinal
            R.id.btn_weather_cloudy -> WeatherView.WeatherType.CLOUDY.ordinal
            R.id.btn_weather_rain -> WeatherView.WeatherType.RAIN.ordinal
            R.id.btn_weather_snow -> WeatherView.WeatherType.SNOW.ordinal
            R.id.btn_weather_fog -> WeatherView.WeatherType.FOG.ordinal
            R.id.btn_weather_thunder -> WeatherView.WeatherType.THUNDERSTORM.ordinal
            else -> WeatherView.WeatherType.RAIN.ordinal
        }
        backgroundManager.setManualWeatherIntensity(bgIntensitySeek.progress)
        backgroundManager.setManualWeatherType(typeToSave)

        applyWeatherState()

        if (wasMusicBackgroundApplied) {

            when (previewBackgroundUri) {
                "__DEFAULT_GRADIENT__" -> {
                    backgroundManager.setSavedBackgroundUri(null)
                    Log.d("MainActivity", "Saved: user wants default gradient (will apply after music stops)")
                }
                null -> {
                    Log.d("MainActivity", "No new background selected, keeping current settings")
                }
                else -> {
                    backgroundManager.setSavedBackgroundUri(previewBackgroundUri)
                    Log.d("MainActivity", "Saved new background URI: $previewBackgroundUri")
                }
            }

            Toast.makeText(
                this,
                getString(R.string.settings_saved_music_active),
                Toast.LENGTH_LONG
            ).show()

            previewBackgroundUri = null
            hideBackgroundBottomSheet()

            return
        }

        when (previewBackgroundUri) {
            "__DEFAULT_GRADIENT__" -> {
                backgroundManager.setSavedBackgroundUri(null)
                hasCustomImageBackground = false
                fontManager.clearDynamicColors()
                fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, fontManager.isNightShiftEnabled())
                backgroundImageView.visibility = View.GONE
                gradientManager.startUpdates()
            }
            null -> {
                val existing = backgroundManager.getSavedBackgroundUri()
                existing?.let {
                    try {
                        applyImageBackground(Uri.parse(it), intensity)
                        hasCustomImageBackground = true
                        updateBackgroundFilters()
                    } catch (e: Exception) {
                        Log.w("MainActivity", "Failed to re-apply existing background", e)
                    }
                }
            }
            else -> {
                backgroundManager.setSavedBackgroundUri(previewBackgroundUri)
                try {
                    applyImageBackground(Uri.parse(previewBackgroundUri!!), intensity)
                    hasCustomImageBackground = true
                    updateBackgroundFilters()
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to apply new background", e)
                }
            }
        }

        previewBackgroundUri = null
        hideBackgroundBottomSheet()
    }
    fun restoreUserBackground(savedUriStr: String?) {
        if (savedUriStr != null) {
            try {
                val uri = Uri.parse(savedUriStr)
                val blur = backgroundManager.getBlurIntensity()
                applyImageBackground(uri, blur)
                hasCustomImageBackground = true
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to restore user background", e)
                restoreGradientBackground()
            }
        } else {
            restoreGradientBackground()
        }
    }

    override fun onPause() {
        super.onPause()
        batteryLevelReceiver?.let {
            unregisterReceiver(it)
            batteryLevelReceiver = null
        }
        burnInProtectionManager?.stop()
        getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        sensorManager.unregisterListener(sensorEventListener)
        stopUpdates()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationManager.onRequestPermissionsResult(requestCode, grantResults) { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (!hasCustomImageBackground) gradientManager.updateGradient()
            }
        }
    }
    private inner class BatteryLevelReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                val isAutoPowerSaveEnabled = prefs.getBoolean("automatic_battery_saver_mode", true)

                if (!isAutoPowerSaveEnabled) {
                    // If the user disabled the feature, do nothing.
                    // If the mode was on, you might want to disable it.
                    if (isAutoPowerSavingActive) {
                        togglePowerSavingMode(false)
                    }
                    return
                }

                // Get battery status
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                // Get battery level
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val batteryPct = (level.toFloat() / scale.toFloat() * 100).toInt()

                // Get user's threshold
                val threshold = prefs.getInt("battery_saver_trigger", 15)

                // --- Logic ---
                if (batteryPct <= threshold && !isCharging) {
                    // Battery is low and not charging: Turn ON power saving
                    if (!isPowerSavingMode) {
                        Log.d("BatteryReceiver", "Battery low ($batteryPct%), enabling power-saving mode.")
                        togglePowerSavingMode(true)
                        isAutoPowerSavingActive = true // Flag that this was an automatic change
                    }
                } else {
                    // Battery is OK or charging: Turn OFF power saving (if we turned it on)
                    if (isPowerSavingMode && isAutoPowerSavingActive) {
                        Log.d("BatteryReceiver", "Battery OK ($batteryPct%) or charging, disabling auto power-saving mode.")
                        togglePowerSavingMode(false)
                        isAutoPowerSavingActive = false // Clear the flag
                    }
                }
            }
        }
    }
}