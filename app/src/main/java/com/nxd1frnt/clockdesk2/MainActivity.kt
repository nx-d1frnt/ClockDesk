package com.nxd1frnt.clockdesk2

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
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
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.daytimegetter.SunriseAPI
import com.nxd1frnt.clockdesk2.musicgetter.LastFmAPI
import com.nxd1frnt.clockdesk2.musicgetter.MusicGetter
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
    private lateinit var settingsButton: Button
    private lateinit var debugButton: Button
    private lateinit var backgroundButton: Button
    private lateinit var backgroundCustomizationTab: FloatingActionButton
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var tutorialLayout: ConstraintLayout
    private lateinit var tutorialFinger: ImageView
    private lateinit var tutorialText: TextView
    private lateinit var clockManager: ClockManager
    private lateinit var gradientManager: GradientManager
    private lateinit var fontManager: FontManager
    private lateinit var locationManager: LocationManager
    private lateinit var weatherGetter: WeatherGetter
    private lateinit var dayTimeGetter: DayTimeGetter
    private lateinit var musicGetter: MusicGetter
    private lateinit var rssTickerText: TextView
    private var rssTicker: RssTicker? = null
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
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN or android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        rssTickerText = findViewById(R.id.rss_ticker_text)
        nowPlayingTextView = findViewById(R.id.now_playing_text)
        backgroundLayout = findViewById(R.id.background_layout)
        backgroundImageView = findViewById(R.id.background_image_view)
        backgroundProgressOverlay = findViewById(R.id.background_progress_overlay)
        backgroundProgressText = findViewById(R.id.background_progress_text)
        settingsButton = findViewById(R.id.settings_button)
        debugButton = findViewById(R.id.demo_button)
        backgroundButton = findViewById(R.id.background_button)
        backgroundCustomizationTab = findViewById(R.id.background_customization_fab)
        mainLayout = findViewById(R.id.main_layout)
        bottomSheet = findViewById(R.id.bottom_sheet)
        // main bottom sheet behavior
        bottomSheetBehavior = from(bottomSheet).apply {
            state = STATE_HIDDEN
        }
        tutorialLayout = findViewById(R.id.tutorial_overlay_root)
        tutorialFinger = findViewById(R.id.tutorial_finger_icon)
        tutorialText = findViewById(R.id.tutorial_text)

        // background-specific bottom sheet (used for picking backgrounds)
        val backgroundBottomSheet = findViewById<LinearLayout>(R.id.background_bottom_sheet)
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
            lastfmLayout
        )
        backgroundManager = BackgroundManager(this)
        locationManager = LocationManager(this, permissionRequestCode)
        dayTimeGetter = SunriseAPI(this, locationManager)
        weatherGetter = OpenMeteoAPI(this, locationManager)
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
                            setBackgroundDimming(mode, backgroundManager.getDimIntensity())
                        }
                    }
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed to update dynamic dimming: ${e.message}")
                }
            }
        )

        fontManager.loadFont()

        // Load any saved custom background image from prefs (if set)
        loadSavedBackground()

        // Ensure dim is applied if image already set
        val dimModeInit = backgroundManager.getDimMode()
        val dimIntensity = backgroundManager.getDimIntensity()
        if (dimModeInit != BackgroundManager.DIM_MODE_OFF) {
            setBackgroundDimming(
                dimModeInit,
                dimIntensity
            )
        }
        checkForFirstLaunchAnimation()
        // Long tap for edit mode
        mainLayout.setOnLongClickListener {
            toggleEditMode()
            true
        }

        setupBottomSheet()
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
            highlightImageView(true)
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

        checkLocationPermissionsAndLoadData()

        // Start updates
        startUpdates()
    }

    private val musicCallback: (()->Unit) = {
        if (musicGetter.enabled) {
            if (musicGetter.currentTrack != lastTrackInfo) {
                lastfmLayout.visibility = View.VISIBLE
                lastfmLayout.animate().alpha(0f).setDuration(400).setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        nowPlayingTextView.text = musicGetter.currentTrack
                        nowPlayingTextView.isSelected = true
                        lastfmLayout.animate().alpha(1f).setDuration(400).setListener(null).start()
                    }
                })
                lastTrackInfo = musicGetter.currentTrack

                // flag to track if we applied music background for this track
                var isMusicBgAppliedThisTrack = false

                if (musicGetter.currentAlbumArtUrl != null) {
                    Log.d("MainActivity", "Applying album art background: ${musicGetter.currentAlbumArtUrl}")
                    applyImageBackground(Uri.parse(musicGetter.currentAlbumArtUrl), backgroundManager.getBlurIntensity())
                    wasMusicBackgroundApplied = true // flag to indicate that music is controlling the background
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
                    Log.d("MainActivity", "Restoring user background (track changed, no new art)")
                    restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                    wasMusicBackgroundApplied = false // reset flag
                }
            }
        } else {
            // nothing playing, hide layout
            lastfmLayout.animate().alpha(0f).setDuration(400).setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    lastfmLayout.visibility = View.GONE
                }
            })

            // If music background was applied, restore user background
            if (wasMusicBackgroundApplied) {
                restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                Log.d("MainActivity", "Restoring user background after music background disabled, bgUri=${backgroundManager.getSavedBackgroundUri()}")
                wasMusicBackgroundApplied = false // Сбрасываем флаг
            }
        }
    }
    private fun updateRssTickerFromPrefs() {
        // Stop any existing ticker
        rssTicker?.stop()
        rssTicker = null
        rssTickerText.visibility = View.GONE

        // Get preferences
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val rssEnabled = prefs.getBoolean("enable_rss_ticker", true)

        if (rssEnabled) {
            val rssUrl = prefs.getString("rss_feed_url", "http://www.npr.org/rss/rss.php?id=1001")
            if (!rssUrl.isNullOrBlank()) {
                rssTickerText.visibility = View.VISIBLE
                // Create a new ticker with the potentially updated URL
                rssTicker = RssTicker(rssTickerText, rssUrl)
            }
        }
    }

    private fun checkForFirstLaunchAnimation() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)

        if (isFirstLaunch) {
            startTutorialAnimation()
        } else {
          checkLocationPermissionsAndLoadData()
        }
    }

    private fun checkLocationPermissionsAndLoadData() {
        // Проверяем, есть ли уже разрешение
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasCoarse || hasFine) {
            // 1. Разрешение есть. Загружаем данные как обычно.
            loadCoordinatesAndFetchData()
        } else {
            // 2. Разрешения нет. Показываем наш диалог с объяснением.
            showLocationRationaleDialog()
        }
    }

    private fun showLocationRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.location_permission_title)) // (Добавьте это в strings.xml)
            .setMessage(getString(R.string.location_permission_message)) // (Добавьте это в strings.xml)
            .setPositiveButton(getString(R.string.location_permission_grant)) { _, _ ->
                // user chose to grant permission
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    permissionRequestCode // Это 100, как вы указали в MainActivity
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
                // Пересчитываем динамическое затемнение на случай, если время восхода/заката изменилось
                if (backgroundImageView.visibility == View.VISIBLE) {
                    setBackgroundDimming(backgroundManager.getDimMode(), backgroundManager.getDimIntensity())
                }
            }
        }
    }

    private fun startTutorialAnimation() {
        // --- 1. Initial Setup ---
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
                                                                                tutorialText.text = getString(R.string.tutorial_text_3) // ИСПРАВЛЕНО
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
                                                                                                                    tutorialText.text = getString(R.string.tutorial_text_4) // ИСПРАВЛЕНО
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
        if (requestCode == PICK_BG_REQUEST && resultCode == Activity.RESULT_OK) {
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
                setBackgroundDimming(
                    backgroundManager.getDimMode(),
                    backgroundManager.getDimIntensity()
                )
            } catch (e: Exception) {
                backgroundManager.setSavedBackgroundUri(null)
                hasCustomImageBackground = false
                Log.w("MainActivity", "Failed to load saved background: ${e.message}")
            }
        } else {
            // ensure image view is hidden and gradient updates run
            backgroundImageView.visibility = View.GONE
            hasCustomImageBackground = false
            gradientManager.startUpdates()
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

            // Show a progress overlay while loading/processing (especially when blurIntensity>0)
            val loadingMessage =
                if (blurIntensity > 0) getString(R.string.blur_applying_message) else getString(R.string.loading_background_message)
            setBackgroundProgressVisible(true, loadingMessage)

            // On modern Android, prefer RenderEffect for high-quality blur on the ImageView layer
            val usePlatformBlur =
                blurIntensity > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

            // Determine a reasonable target size to avoid loading very large bitmaps into memory.
            val metrics = resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels
            // Limit to a sensible cap (e.g. 1080p) to prevent massive bitmaps on very high-res photos
            val maxDim = 1080
            val targetW = minOf(screenW, maxDim)
            val targetH = minOf(screenH, maxDim)

            val req = RequestOptions()
                .transform(CenterCrop())
                .override(targetW, targetH)
                .downsample(DownsampleStrategy.CENTER_INSIDE)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)

            val finalReq = if (usePlatformBlur) {
                // don't pre-blur bitmaps; let the view's RenderEffect handle it
                req
            } else if (blurIntensity > 0) {
                // older devices: apply our BlurTransformation on the bitmap (Glide will run this off-main-thread)
                req.transform(CenterCrop(), BlurTransformation(blurIntensity))
            } else {
                req
            }

            Glide.with(this)
                .load(uri)
                .apply(finalReq)
                .into(object :
                    com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        // Hide progress overlay (work is done)
                        setBackgroundProgressVisible(false)
                        backgroundImageView.setImageDrawable(resource)
                        //editmodebgImageView.setImageDrawable(resource)
                        //editmodebgImageView.setColorFilter(Color.argb(180, 0, 0, 0), PorterDuff.Mode.SRC_OVER)
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
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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
                                        setBackgroundDimming(
                                            targetMode,
                                            targetIntensity
                                        )
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
                        try {
                            restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                        } catch (e: Exception) {
                            TODO("Not yet implemented")
                            Log.w("MainActivity", "restoreUserBackground failed: ${e.message}")
                        }
                    }
                })
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
                // Динамический режим: рассчитать по времени
                backgroundManager.computeEffectiveDimIntensity(
                    clockManager.getCurrentTime(),
                    dayTimeGetter
                )
            } catch (e: Exception) {
                // В случае сбоя используем статическое значение
                userIntensity.coerceIn(0, 50)
            }
        } else {
            // Статический режим: просто используем значение пользователя
            userIntensity.coerceIn(0, 50)
        }
        // Финальная гарантия, что значение в нужных пределах
        return intensity.coerceIn(0, 50)
    }

    private fun calculateZoom(effectiveIntensity: Int): Float {
        return 1.0f + (effectiveIntensity.coerceIn(0, 50) / 50f) * 0.2f
    }

    private fun setBackgroundDimming(mode: Int, intensity: Int) {
        runOnUiThread {
            try {
                // mode: BackgroundManager.DIM_MODE_OFF/CONTINUOUS/DYNAMIC
                if (mode == BackgroundManager.DIM_MODE_OFF || intensity <= 0) {
                    backgroundImageView.clearColorFilter()
                    backgroundImageView.alpha = 1.0f
                    return@runOnUiThread
                }

                // Determine effective intensity. For dynamic mode, compute based on current time + sun times.
                val effectiveIntensity = getEffectiveDimIntensity(mode, intensity)

                // 2. Рассчитываем зум и альфу
                val zoom = calculateZoom(effectiveIntensity) // <-- Используем хелпер
                val maxAlpha = 0.8f
                val alpha = (effectiveIntensity / 50f) * maxAlpha

                // 3. Применяем
                backgroundImageView.scaleX = zoom
                backgroundImageView.scaleY = zoom
                val alphaInt = (alpha * 255).toInt().coerceIn(0, 255)
                val overlayColor = Color.argb(alphaInt, 0, 0, 0)
                // Use SRC_OVER to darken the image
                backgroundImageView.setColorFilter(overlayColor, PorterDuff.Mode.SRC_OVER)
            } catch (e: Exception) {
                Log.w("MainActivity", "setBackgroundDimming failed: ${e.message}")
            }
        }
    }

    private fun setBackgroundProgressVisible(visible: Boolean, message: String? = null) {
        runOnUiThread {
            backgroundProgressOverlay.visibility = if (visible) View.VISIBLE else View.GONE
            if (message != null) backgroundProgressText.text = message
        }
    }

    // Show the dedicated backgrounds bottom sheet
    private fun showBackgroundBottomSheet() {
        val sheet = findViewById<LinearLayout>(R.id.background_bottom_sheet)
        val recycler = sheet.findViewById<RecyclerView>(R.id.background_recycler_view)
        val blurSwitch =
            sheet.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.background_blur_switch)
        val blurSeek = sheet.findViewById<SeekBar>(R.id.blur_intensity_seekbar)
        //val dimSwitch = sheet.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.background_dim_switch)
        val dimtogglegroup =
            sheet.findViewById<MaterialButtonToggleGroup>(R.id.dimming_toggle_group)
        val dimSeek = sheet.findViewById<SeekBar>(R.id.dimming_intensity_seekbar)
        val clearBtn = sheet.findViewById<Button>(R.id.clear_background_button_bs)
        val applyBtn = sheet.findViewById<Button>(R.id.apply_background_button)

        val prefs = backgroundManager // alias for readability
        // Build items: default gradient, saved URIs, add-item
        val items = mutableListOf<String>().apply {
            add("__DEFAULT_GRADIENT__")
            addAll(prefs.getSavedUriSet())
            add("__ADD__")
        }

        if (backgroundsAdapter == null) {
            backgroundsAdapter = BackgroundsAdapter(this, items) { id ->
                when (id) {
                    "__ADD__" -> {
                        // Launch picker
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
                            Toast.makeText(this, "", Toast.LENGTH_SHORT).show()
                        }
                    }

                    "__DEFAULT_GRADIENT__" -> {
                        // preview default gradient: hide imageView, resume gradient updates, clear any renderEffect
                        previewBackgroundUri = "__DEFAULT_GRADIENT__"
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                try {
                                    backgroundImageView.setRenderEffect(null)
                                } catch (_: Throwable) {
                                }
                            }
                        } catch (_: Exception) {
                        }
                        backgroundImageView.setImageDrawable(null)
                        backgroundImageView.visibility = View.GONE
                        gradientManager.startUpdates()
                    }

                    else -> {
                        // preview chosen saved image -> stop gradient updates right away and show progress while applying
                        try {
                            val uri = Uri.parse(id)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            }
                            val intensity = prefs.getBlurIntensity()
                            // Stop gradient updates immediately for preview
                            gradientManager.stopUpdates()
                            applyImageBackground(uri, intensity)
                            previewBackgroundUri = id
                        } catch (e: Exception) {

                        }
                    }
                }
            }
            recycler.layoutManager =
                LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            recycler.isNestedScrollingEnabled = false
            recycler.adapter = backgroundsAdapter
        } else {
            // Update existing adapter items
            backgroundsAdapter?.updateItems(items)
        }
        // ensure the first two items (default/add) are visible
        recycler.scrollToPosition(0)

        // initialize UI states (default no blur)
        blurSeek.progress = prefs.getBlurIntensity()
        blurSwitch.isChecked = blurSeek.progress > 0

        // initialize dimming UI from saved global prefs
        val dimMode = prefs.getDimMode()
        val dimIntensity = prefs.getDimIntensity()
        when (dimMode) {
            BackgroundManager.DIM_MODE_OFF -> dimtogglegroup.check(R.id.off_button)
            BackgroundManager.DIM_MODE_CONTINUOUS -> dimtogglegroup.check(R.id.continuous_button)
            BackgroundManager.DIM_MODE_DYNAMIC -> dimtogglegroup.check(R.id.dynamic_button)
            else -> dimtogglegroup.check(R.id.off_button)
        }
        dimSeek.progress = dimIntensity

        blurSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                blurSeek.progress = 0
            } else if (blurSeek.progress == 0) {
                blurSeek.progress = 25
            }
        }

        blurSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                blurSwitch.isChecked = progress > 0
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

//        dimSwitch.setOnCheckedChangeListener { _, isChecked ->
//            if (!isChecked) {
//                dimSeek.progress = 0
//            } else if (dimSeek.progress == 0) {
//                dimSeek.progress = 25
//            }
//            // preview change immediately if an image is visible
//            if (backgroundImageView.visibility == View.VISIBLE) {
//                setBackgroundDimming(isChecked, dimSeek.progress)
//            }
//        }
        dimtogglegroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) {
                // Prevent unchecking all buttons: revert to previous state
                if (group.checkedButtonId == View.NO_ID) {
                    group.check(checkedId)
                }
                return@addOnButtonCheckedListener
            }
            // Map the checked button to a dim mode
            val previewMode = when (checkedId) {
                R.id.off_button -> BackgroundManager.DIM_MODE_OFF
                R.id.continuous_button -> BackgroundManager.DIM_MODE_CONTINUOUS
                R.id.dynamic_button -> BackgroundManager.DIM_MODE_DYNAMIC
                else -> BackgroundManager.DIM_MODE_OFF
            }
            // If user selected off, clear seek progress preview
            if (previewMode == BackgroundManager.DIM_MODE_OFF) dimSeek.progress = 0

            // preview change immediately if an image is visible
            if (backgroundImageView.visibility == View.VISIBLE) {
                setBackgroundDimming(previewMode, dimSeek.progress)
            }
        }

        dimSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                // preview change using currently selected toggle mode
                val checkedId = dimtogglegroup.checkedButtonId
                val previewMode = when (checkedId) {
                    R.id.off_button -> BackgroundManager.DIM_MODE_OFF
                    R.id.continuous_button -> BackgroundManager.DIM_MODE_CONTINUOUS
                    R.id.dynamic_button -> BackgroundManager.DIM_MODE_DYNAMIC
                    else -> BackgroundManager.DIM_MODE_OFF
                }
                if (backgroundImageView.visibility == View.VISIBLE) {
                    setBackgroundDimming(previewMode, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        clearBtn.setOnClickListener {
            // Clear persisted background selection
            backgroundManager.setSavedBackgroundUri(null)
            previewBackgroundUri = null
            hasCustomImageBackground = false
            // hide image view and show gradient
            backgroundImageView.setImageDrawable(null)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                try {
                    backgroundImageView.setRenderEffect(null)
                } catch (_: Throwable) {
                }
            }
            backgroundImageView.visibility = View.GONE
            // clear dim prefs when switching to gradient
            backgroundManager.clearDim()
            gradientManager.startUpdates()
            backgroundBottomSheetBehavior.state = STATE_HIDDEN
        }

        applyBtn.setOnClickListener {
            // Persist the currently previewed background (if any)
            val intensity = blurSeek.progress
            backgroundManager.setBlurIntensity(intensity)
            // Persist dim mode/intensity using toggle group
            val checkedId = dimtogglegroup.checkedButtonId
            val modeToSave = when (checkedId) {
                R.id.off_button -> BackgroundManager.DIM_MODE_OFF
                R.id.continuous_button -> BackgroundManager.DIM_MODE_CONTINUOUS
                R.id.dynamic_button -> BackgroundManager.DIM_MODE_DYNAMIC
                else -> BackgroundManager.DIM_MODE_OFF
            }
            backgroundManager.setDimMode(modeToSave)
            backgroundManager.setDimIntensity(dimSeek.progress)

            when (previewBackgroundUri) {
                "__DEFAULT_GRADIENT__" -> {
                    // user chose the default gradient: remove any persisted custom uri
                    backgroundManager.setSavedBackgroundUri(null)
                    hasCustomImageBackground = false
                    backgroundImageView.visibility = View.GONE
                    gradientManager.startUpdates()
                }

                null -> {
                    // nothing new previewed; keep existing selection
                    val existing = backgroundManager.getSavedBackgroundUri()
                    existing?.let {
                        try {
                            applyImageBackground(Uri.parse(it), intensity)
                            hasCustomImageBackground = true
                            // Ensure dim applied
                            setBackgroundDimming(
                                backgroundManager.getDimMode(),
                                backgroundManager.getDimIntensity()
                            )
                        } catch (e: Exception) {
                            Log.w("MainActivity", "apply on applyBtn failed: ${e.message}")
                        }
                    }
                }

                else -> {
                    // persist selected image uri and apply it
                    backgroundManager.setSavedBackgroundUri(previewBackgroundUri)
                    try {
                        applyImageBackground(Uri.parse(previewBackgroundUri), intensity)
                        hasCustomImageBackground = true
                        // Ensure dim applied after persist
                        setBackgroundDimming(
                            backgroundManager.getDimMode(),
                            backgroundManager.getDimIntensity()
                        )
                    } catch (e: Exception) {
                        Log.w("MainActivity", "apply on applyBtn failed: ${e.message}")
                    }
                }
            }

            previewBackgroundUri = null
            backgroundBottomSheetBehavior.state = STATE_HIDDEN
        }

        backgroundBottomSheetBehavior.state = STATE_EXPANDED
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
                    hideBottomSheet()
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        backgroundBottomSheetBehavior.peekHeight = 0
        backgroundBottomSheetBehavior.isHideable = true
        backgroundBottomSheetBehavior.isDraggable = true
        backgroundBottomSheetBehavior.addBottomSheetCallback(object : BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == STATE_HIDDEN) {
                    highlightImageView(false)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    private fun showCustomizationBottomSheet(viewToCustomize: View) {
        isBottomSheetInitializing = true

        // Set the currently focused view for the listeners to use
        focusedView = viewToCustomize

        // Find all UI elements within the bottom sheet
        val title = bottomSheet.findViewById<TextView>(R.id.customization_title)
        val sizeSeekBar = bottomSheet.findViewById<SeekBar>(R.id.size_seekbar)
        val sizeValue = bottomSheet.findViewById<TextView>(R.id.size_value)
        val transparencySeekBar = bottomSheet.findViewById<SeekBar>(R.id.transparency_seekbar)
        val transparencyPreview = bottomSheet.findViewById<View>(R.id.transparency_preview)
        val fontRecyclerView = bottomSheet.findViewById<RecyclerView>(R.id.font_recycler_view)
        val applyButton = bottomSheet.findViewById<Button>(R.id.apply_button)
        val cancelButton = bottomSheet.findViewById<Button>(R.id.cancel_button)
        val alignmentGroup =
            bottomSheet.findViewById<MaterialButtonToggleGroup>(R.id.alignment_toggle_group)
        val nightShiftSwitch =
            bottomSheet.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.night_shift_switch)
        val timeFormatGroup = bottomSheet.findViewById<RadioGroup>(R.id.time_format_radio_group)
        val dateFormatGroup = bottomSheet.findViewById<RadioGroup>(R.id.date_format_radio_group)
        val timeformatlabel = bottomSheet.findViewById<TextView>(R.id.time_format_label)
        val dateformatlabel = bottomSheet.findViewById<TextView>(R.id.date_format_label)

        // --- Centralized Logic to get initial values based on the focused view ---
        val currentSize: Float
        val currentAlpha: Float
        val currentAlignment: Int

        when (viewToCustomize.id) {
            R.id.time_text -> {
                title.text = getString(R.string.customize_time)
                timeFormatGroup.visibility = View.VISIBLE
                timeformatlabel.visibility = View.VISIBLE
                dateFormatGroup.visibility = View.GONE
                dateformatlabel.visibility = View.GONE

                currentSize = fontManager.getTimeSize()
                currentAlpha = fontManager.getTimeAlpha()
                currentAlignment = fontManager.getTimeAlignment()
            }

            R.id.date_text -> {
                title.text = getString(R.string.customize_date)
                timeFormatGroup.visibility = View.GONE
                timeformatlabel.visibility = View.GONE
                dateFormatGroup.visibility = View.VISIBLE
                dateformatlabel.visibility = View.VISIBLE

                currentSize = fontManager.getDateSize()
                currentAlpha = fontManager.getDateAlpha()
                currentAlignment = fontManager.getDateAlignment()
            }

            R.id.lastfm_layout -> {
                title.text = getString(R.string.customize_now_playing)
                timeFormatGroup.visibility = View.GONE
                dateFormatGroup.visibility = View.GONE
                timeformatlabel.visibility = View.GONE
                dateformatlabel.visibility = View.GONE

                currentSize = fontManager.getLastfmSize()
                currentAlpha = fontManager.getLastfmAlpha()
                currentAlignment = fontManager.getLastfmAlignment()
            }

            else -> {
                // If an unknown view is passed, do nothing and hide the sheet
                hideBottomSheet()
                return
            }
        }

        // Highlight the newly focused view
        highlightFocusedView(true)

        // Animate background customization button out
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            backgroundCustomizationTab.animate().alpha(0f).setDuration(200).withEndAction {
                backgroundCustomizationTab.visibility = View.GONE
            }.start()
        } else {
            backgroundCustomizationTab.visibility = View.GONE
        }

        // --- Clear all listeners to prevent them from firing during initialization ---
        sizeSeekBar.setOnSeekBarChangeListener(null)
        transparencySeekBar.setOnSeekBarChangeListener(null)
        alignmentGroup.clearOnButtonCheckedListeners()
        timeFormatGroup.setOnCheckedChangeListener(null)
        dateFormatGroup.setOnCheckedChangeListener(null)

        // --- Initialize UI controls with the fetched values ---
        sizeSeekBar.max = 155
        sizeSeekBar.progress = (currentSize - 20).toInt()
        sizeValue.text = "${currentSize.toInt()}sp"

        transparencySeekBar.max = 100
        transparencySeekBar.progress = (currentAlpha * 100).toInt()
        transparencyPreview.alpha = currentAlpha

        nightShiftSwitch.isChecked = fontManager.isNightShiftEnabled()

        when (currentAlignment) {
            View.TEXT_ALIGNMENT_VIEW_START, View.TEXT_ALIGNMENT_TEXT_START -> alignmentGroup.check(R.id.left_button)
            View.TEXT_ALIGNMENT_CENTER -> alignmentGroup.check(R.id.center_button)
            View.TEXT_ALIGNMENT_VIEW_END, View.TEXT_ALIGNMENT_TEXT_END -> alignmentGroup.check(R.id.right_button)
            else -> alignmentGroup.check(R.id.left_button)
        }

        // Initialize format radio buttons
        timeFormatGroup.check(
            if (fontManager.getTimeFormatPattern()
                    .contains("H")
            ) R.id.time_24_radio else R.id.time_12_radio
        )
        when (fontManager.getDateFormatPattern()) {
            "MMM dd" -> dateFormatGroup.check(R.id.date_format_1)
            "EEE, MMM dd" -> dateFormatGroup.check(R.id.date_format_2)
            "EEEE, MMMM dd, yyyy" -> dateFormatGroup.check(R.id.date_format_3)
            else -> dateFormatGroup.check(R.id.date_format_2)
        }

        // --- Setup Listeners (now they will only respond to user actions) ---

        val fontAdapter = FontAdapter(fontManager.getFonts()) { fontId ->
            when (focusedView?.id) {
                R.id.time_text -> fontManager.setTimeFont(fontId)
                R.id.date_text -> fontManager.setDateFont(fontId)
                R.id.lastfm_layout -> fontManager.setLastfmFont(fontId)
            }
        }
        fontRecyclerView.adapter = fontAdapter

        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val size = (progress + 20).toFloat()
                sizeValue.text = "${size.toInt()}sp"
                when (focusedView?.id) {
                    R.id.time_text -> fontManager.setTimeSize(size)
                    R.id.date_text -> fontManager.setDateSize(size)
                    R.id.lastfm_layout -> fontManager.setLastfmSize(size)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        transparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val alpha = progress / 100f
                transparencyPreview.alpha = alpha
                when (focusedView?.id) {
                    R.id.time_text -> fontManager.setTimeAlpha(alpha)
                    R.id.date_text -> fontManager.setDateAlpha(alpha)
                    R.id.lastfm_layout -> fontManager.setLastfmAlpha(alpha)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        nightShiftSwitch.setOnCheckedChangeListener { _, isChecked ->
            isNightShiftEnabled = isChecked
            fontManager.setNightShiftEnabled(isChecked)
        }

        alignmentGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || !findViewById<View>(checkedId).isPressed) return@addOnButtonCheckedListener
            val alignment = when (checkedId) {
                R.id.left_button -> View.TEXT_ALIGNMENT_VIEW_START
                R.id.center_button -> View.TEXT_ALIGNMENT_CENTER
                R.id.right_button -> View.TEXT_ALIGNMENT_VIEW_END
                else -> View.TEXT_ALIGNMENT_VIEW_START
            }
            when (focusedView?.id) {
                R.id.time_text -> fontManager.setTimeAlignment(alignment)
                R.id.date_text -> fontManager.setDateAlignment(alignment)
                R.id.lastfm_layout -> fontManager.setLastfmAlignment(alignment)
            }
        }

        timeFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            val pattern = if (checkedId == R.id.time_24_radio) "HH:mm" else "hh:mm a"
            fontManager.setTimeFormatPattern(pattern)
            clockManager.updateTimeText() // Update display immediately
        }

        dateFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            val pattern = when (checkedId) {
                R.id.date_format_1 -> "MMM dd"
                R.id.date_format_2 -> "EEE, MMM dd"
                R.id.date_format_3 -> "EEEE, MMMM dd, yyyy"
                else -> "EEE, MMM dd"
            }
            fontManager.setDateFormatPattern(pattern)
            clockManager.updateDateText() // Update display immediately
        }

        applyButton.setOnClickListener {
            fontManager.saveSettings()
            hideBottomSheet()
        }

        cancelButton.setOnClickListener {
            fontManager.loadFont() // Revert any temporary changes
            hideBottomSheet()
        }

        // --- Show the bottom sheet ---
        isBottomSheetInitializing = false
        bottomSheetBehavior.state = STATE_EXPANDED
    }

    private fun hideBottomSheet() {
        bottomSheetBehavior.state = STATE_HIDDEN
        highlightFocusedView(false) // Use the new unified function
        focusedView = null
        // Restore background customization button with animation
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            backgroundCustomizationTab.visibility = View.VISIBLE
            backgroundCustomizationTab.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else {
            backgroundCustomizationTab.visibility = View.GONE
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

    private fun highlightImageView(isHighlighted: Boolean) {
        if (isHighlighted) {
            val targetMode = backgroundManager.getDimMode()
            val targetIntensity = backgroundManager.getDimIntensity()
            val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)
            val targetScale = calculateZoom(effectiveIntensity)
            backgroundImageView.animate()
                .scaleX(targetScale + 0.4f)
                .scaleY(targetScale + 0.4f)
                .setDuration(animationDuration)
                .start()
            //backgroundImageView.setBackgroundResource(R.drawable.editable_border)
        } else {
            val targetMode = backgroundManager.getDimMode()
            val targetIntensity = backgroundManager.getDimIntensity()
            val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)
            val targetScale = calculateZoom(effectiveIntensity)
            backgroundImageView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(animationDuration)
                .start()
            // if (!isEditMode) backgroundImageView.background = null
        }
    }

    private fun startUpdates() {
        clockManager.startUpdates()
        musicGetter.startUpdates()
        rssTicker?.start()
        // Only start gradient updates if there is no custom image background
        if (!hasCustomImageBackground) {
            gradientManager.startUpdates()
        }
    }

    private fun stopUpdates() {
        clockManager.stopUpdates()
        rssTicker?.stop()
        musicGetter.stopUpdates()
        gradientManager.stopUpdates()
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
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


    private fun exitEditMode() {
        isEditMode = false
        mainLayout.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(animationDuration)
            .setInterpolator(OvershootInterpolator())
            .start()
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
        settingsButton.visibility = View.GONE
        debugButton.visibility = View.GONE
       // backgroundButton.visibility = View.GONE
        backgroundCustomizationTab.visibility = View.GONE
        handler.removeCallbacks(editModeTimeoutRunnable)
        //Toast.makeText(this, R.string.edit_mode_disabled, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
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
                    setBackgroundDimming(backgroundManager.getDimMode(), backgroundManager.getDimIntensity())
                }
            }
        }
        if (isEditMode) exitEditMode()
        if (isDemoMode) {
            isDemoMode = false
            clockManager.toggleDebugMode(false)
            gradientManager.toggleDebugMode(false)
        }
        // reload background in case user changed it in Settings
        loadSavedBackground()

        // Create/re-create the RSS ticker from preferences
        updateRssTickerFromPrefs()

        // Start all updates (including the new RSS ticker)
        startUpdates()
    }

    private fun restoreGradientBackground() {
        backgroundImageView.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                backgroundImageView.setRenderEffect(null)
            } catch (_: Throwable) {}
        }
        backgroundImageView.clearColorFilter() // Сбрасываем затемнение
        hasCustomImageBackground = false
        gradientManager.startUpdates() // Запускаем обновления градиента
    }
    fun restoreUserBackground(savedUriStr: String?) {
        if (savedUriStr != null) {
            try {
                val uri = Uri.parse(savedUriStr)
                val blur = backgroundManager.getBlurIntensity() // Берем текущие настройки блюра
                applyImageBackground(uri, blur) // Просто применяем его заново
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
}
