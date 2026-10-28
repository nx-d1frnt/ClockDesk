package com.nxd1frnt.clockdesk2.ui

import com.nxd1frnt.clockdesk2.utils.PowerSavePolicy
import com.nxd1frnt.clockdesk2.utils.UpdateManager
import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.animation.TimeInterpolator
import android.view.WindowManager
import com.nxd1frnt.clockdesk2.utils.MotionUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import com.nxd1frnt.clockdesk2.desks.DeskManager
import com.nxd1frnt.clockdesk2.desks.DeskAppearancePreferences
import com.nxd1frnt.clockdesk2.desks.ui.DesksPagerAdapter
import com.nxd1frnt.clockdesk2.desks.ui.DeskPageIndicatorView
import com.nxd1frnt.clockdesk2.desks.ui.DeskOverviewManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.sidesheet.SideSheetBehavior
import com.google.android.material.sidesheet.SideSheetCallback
import com.nxd1frnt.clockdesk2.R
import com.nxd1frnt.clockdesk2.background.BackgroundCropController
import com.nxd1frnt.clockdesk2.background.BackgroundManager
import com.nxd1frnt.clockdesk2.background.BlurTransformation
import com.nxd1frnt.clockdesk2.background.GradientManager
import com.nxd1frnt.clockdesk2.daytimegetter.DayTimeGetter
import com.nxd1frnt.clockdesk2.daytimegetter.SunriseAPI
import com.nxd1frnt.clockdesk2.music.MusicPluginManager
import com.nxd1frnt.clockdesk2.music.MusicTrack
import com.nxd1frnt.clockdesk2.music.PluginState
import com.nxd1frnt.clockdesk2.network.GlideApp
import com.nxd1frnt.clockdesk2.smartchips.SmartChipManager
import com.nxd1frnt.clockdesk2.smartchips.plugins.BackgroundProgressPlugin
import com.nxd1frnt.clockdesk2.ui.settings.BackgroundSheetManager
import com.nxd1frnt.clockdesk2.ui.settings.SettingsActivity
import com.nxd1frnt.clockdesk2.ui.touch.EditModeTouchGuard
import com.nxd1frnt.clockdesk2.ui.view.DynamicBackgroundView
import com.nxd1frnt.clockdesk2.ui.view.PerformanceOverlayView
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceView
import com.nxd1frnt.clockdesk2.utils.BurnInProtectionManager
import com.nxd1frnt.clockdesk2.utils.ClockManager
import com.nxd1frnt.clockdesk2.utils.ColorExtractor
import com.nxd1frnt.clockdesk2.utils.EntranceAnimationManager
import com.nxd1frnt.clockdesk2.utils.FontManager
import com.nxd1frnt.clockdesk2.utils.ForegroundStateDispatcher
import com.nxd1frnt.clockdesk2.utils.LocationManager
import com.nxd1frnt.clockdesk2.utils.Logger
import com.nxd1frnt.clockdesk2.utils.PowerSaveObserver
import com.nxd1frnt.clockdesk2.utils.PowerStateManager
import com.nxd1frnt.clockdesk2.utils.SmartPixelManager
import com.nxd1frnt.clockdesk2.utils.UiMonetThemeManager
import com.nxd1frnt.clockdesk2.utils.calculateWeatherIntensity
import com.nxd1frnt.clockdesk2.utils.getWeatherMatrix
import com.nxd1frnt.clockdesk2.weathergetter.OpenMeteoAPI
import com.nxd1frnt.clockdesk2.weathergetter.WeatherGetter
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetManager
import com.nxd1frnt.clockdesk2.widgets.DesktopWidgetType
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetController
import com.nxd1frnt.clockdesk2.widgets.base.DesktopWidgetHost
import com.nxd1frnt.clockdesk2.widgets.impl.MediaDesktopWidget
import java.util.Calendar
import java.util.Date

class MainActivity : AppCompatActivity(), PowerSaveObserver, DesktopWidgetHost {
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var weatherText: TextView
    private lateinit var weatherIcon: ImageView
    private lateinit var weatherLayout: LinearLayout
    private lateinit var lastfmLayout: FrameLayout
    private lateinit var lastfmIcon: ImageView
    private lateinit var nowPlayingTextView: TextView
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var backgroundImageView: ImageView
    private lateinit var dynamicBackgroundView: DynamicBackgroundView
    private lateinit var turbulenceOverlay: TurbulenceView
    private lateinit var performanceOverlay: PerformanceOverlayView
    private lateinit var settingsButton: Button
    private lateinit var debugButton: Button
    private lateinit var backgroundCustomizationTab: FloatingActionButton
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var editModeBlurLayer: ImageView
    private lateinit var editModeActionBar: MaterialCardView
    private lateinit var btnAddWidget: Button
    private lateinit var btnResetLayout: Button
    private lateinit var btnDoneEdit: Button
    private lateinit var emptyDeskPrompt: TextView

    // UI Elements
    private lateinit var sideSheet: LinearLayout
    private lateinit var sideSheetBehavior: SideSheetBehavior<LinearLayout>
    private lateinit var backgroundBottomSheet: View

    private lateinit var tutorialLayout: ConstraintLayout
    private lateinit var tutorialFinger: ImageView
    private lateinit var tutorialText: TextView
    private lateinit var smartPixelManager: SmartPixelManager
    private lateinit var smartPixelOverlay: View
    private lateinit var customizationSheetManager: CustomizationSheetManager
    private lateinit var widgetGallerySideSheetManager: WidgetGallerySideSheetManager
    private lateinit var backgroundSheetManager: BackgroundSheetManager
    private lateinit var tutorialManager: TutorialManager

    // Core Managers
    private lateinit var desktopWidgetManager: DesktopWidgetManager
    private lateinit var clockManager: ClockManager
    private lateinit var gradientManager: GradientManager
    override lateinit var fontManager: FontManager
    private lateinit var locationManager: LocationManager
    private lateinit var weatherGetter: WeatherGetter
    private lateinit var dayTimeGetter: DayTimeGetter
    private lateinit var backgroundManager: BackgroundManager
    private lateinit var cropController: BackgroundCropController

    private lateinit var smartChipManager: SmartChipManager
    private lateinit var chipContainer: ConstraintLayout
    private lateinit var smartChipScrollView: ScrollView
    private lateinit var widgetMover: WidgetMover
    private lateinit var deskNotificationManager: com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationManager
    private lateinit var deskCallOverlay: com.nxd1frnt.clockdesk2.connect.ui.DeskCallOverlay
    private var deskConnectDeviceListener: com.nxd1frnt.clockdesk2.connect.DeskConnectManager.DeviceListener? = null
    private lateinit var burnInProtectionManager: BurnInProtectionManager
    private lateinit var powerStateManager: PowerStateManager
    private lateinit var sensorManager: SensorManager

    private val mainTimerListener = object : com.nxd1frnt.clockdesk2.timer.TimerManager.TimerListener {
        override fun onTimerTick(remainingMillis: Long, totalDurationMillis: Long) {}
        override fun onTimerStateChanged(state: com.nxd1frnt.clockdesk2.timer.TimerState) {
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (state == com.nxd1frnt.clockdesk2.timer.TimerState.FINISHED) {
                    if (::deskNotificationManager.isInitialized) {
                        deskNotificationManager.showTimerExpiredNotification(
                            title = getString(R.string.timer_time_up),
                            text = getString(R.string.timer_dismiss_to_stop)
                        )
                    }
                } else {
                    if (::deskNotificationManager.isInitialized) {
                        deskNotificationManager.dismissTimerNotification()
                    }
                }
            }
        }
    }

    // Desks Feature
    private lateinit var deskManager: DeskManager
    private lateinit var deskAppearancePreferences: DeskAppearancePreferences
    private var restoredDeskPages: Bundle? = null
    private lateinit var desksViewPager: ViewPager2
    private lateinit var deskPageIndicator: DeskPageIndicatorView
    private lateinit var deskOverviewManager: DeskOverviewManager
    private lateinit var desksPagerAdapter: DesksPagerAdapter
    private lateinit var primaryWidgetsContainer: ConstraintLayout
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var deskGestureDetector: GestureDetector
    private val editModeTouchGuard = EditModeTouchGuard()
    private lateinit var btnManageDesks: MaterialButton
    private var selectedDeskId: String? = null

    // State Variables
    private var musicManager: MusicPluginManager? = null
    private var currentMusicState: PluginState = PluginState.Idle
    private val musicStateUpdates = ForegroundStateDispatcher<PluginState> { applyMusicStateUpdate(it) }
    private var lastTrackInfo: String? = null
    private var wasMusicBackgroundApplied = false
    private var currentAppliedArtworkSource: Any? = null
    private var isUpdatingBackgroundUi = false
    override var isEditMode: Boolean = false
    override val hostContext: Context get() = this
    override fun getRestTranslationX(view: View): Float = if (::widgetMover.isInitialized) widgetMover.getRestTranslationX(view) else 0f
    override fun getRestTranslationY(view: View): Float = if (::widgetMover.isInitialized) widgetMover.getRestTranslationY(view) else 0f
    override fun onMusicArtworkChanged(artworkSource: Any?) {}
    override fun onWidgetClicked(widget: DesktopWidgetController) {
        widget.rootView?.let {
            ensureCustomizationSheetManager().showForView(it)
            resetEditModeTimeout()
        }
    }
    override fun getPreferences(): SharedPreferences = if (::deskAppearancePreferences.isInitialized) {
        deskAppearancePreferences
    } else getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
    private var isCropModeActive = false
    private var isScaleAnimating = false
    private var isLaunchingFilePicker = false
    var isLaunchingExternalActivity = false
    private var isDemoMode = false
    private var isTutorialRunning = false
    private var isNightShiftEnabled = false
    private var isPowerSavingMode = false
    private var isAutoPowerSavingActive = false
    private var isBottomSheetInitializing = false
    private var hasCustomImageBackground = false
    private var previewBackgroundUri: String? = null
    private var lastBackgroundSource: Any? = null
    private var lastBlurIntensity: Int? = null
    private data class BackgroundLoad(
        val model: Any,
        val blurIntensity: Int,
        val skipAnimation: Boolean,
        val onComplete: (() -> Unit)?
    )
    private val backgroundLoads = ForegroundStateDispatcher<BackgroundLoad> {
        performBackgroundLoad(it.model, it.blurIntensity, it.skipAnimation, it.onComplete)
    }
    private val backgroundProviderListener: (com.nxd1frnt.clockdesk2.background.provider.IBackgroundProvider, com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload) -> Unit = { provider, payload ->
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            val activeId = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).getActiveProviderId()
            if (activeId == provider.id || activeId.endsWith("/${provider.id}") || provider.id.endsWith("/$activeId")) {
                // The provider has already cached this payload for restoring after music stops.
                if (isMusicBackgroundActive()) return@runOnUiThread
                Logger.i("BackgroundFlow") { "BackgroundProvider updated: ${provider.id}, applying payload $payload" }
                applyBackgroundPayload(payload)
            }
        }
    }
    private var backgroundRequestId = 0L
    private var isBackgroundLoading = false
    private var activeBackgroundTarget: CustomTarget<Bitmap>? = null
    private var activeEditBackgroundTarget: CustomTarget<Drawable>? = null
    private var isAdvancedGraphicsEnabled = false
    private var isGraphicsTransitionsEnabled = true
    private var isGraphicsTurbulenceEnabled = true
    private var isGraphicsTurbulenceContinuousMusicEnabled = false
    private var isGraphicsEditBlurEnabled = true
    private var graphicsRenderScale = 100
    private var graphicsWeatherScale = 40
    private var enableAdditionalLogging = false
    private var lastIsNight: Boolean? = null
    private var lastFilterDimFactor: Float = -1f
    private var lastFilterWmoCode: Int = -1
    private var lastFilterNightFactor: Float = -1f
    private var lastFilterIsNight: Boolean? = null
    private var lastFilterIsWeatherEnabled: Boolean? = null
    private var lastFilterVisualIntensity: Float = -1f
    private var lastFilterIsEditMode: Boolean? = null

    private var focusedView: View? = null
    private val editModeTimeout = 10000L // 10 seconds
    private val animationDuration = 300L // 300ms
    private val handler = Handler(Looper.getMainLooper())
    private val permissionRequestCode = 100
    private val PICK_BG_REQUEST = 300
    private val PICK_FONT_REQUEST = 400

    private var isResumed = false
    private enum class NightDimState { NORMAL, DIMMED }
    private var currentNightDimState = NightDimState.NORMAL
    private var pendingNightDimState: NightDimState? = null
    private val dimDebounceHandler = Handler(Looper.getMainLooper())
    private var lastSeenLux: Float? = null
    private val dimDebounceRunnable = Runnable {
        pendingNightDimState?.let { targetState ->
            currentNightDimState = targetState
            applyNightDimMode(targetState)
            pendingNightDimState = null
        }
    }

    private var lightSensor: Sensor? = null
    private lateinit var preferenceChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    private var pendingRestoreRunnable: Runnable? = null
    private var pendingBackgroundRestoreRunnable: Runnable? = null
    private lateinit var entranceAnimationManager: EntranceAnimationManager
    private var isEntranceLayoutReady = false
    private var isBackgroundReady = false
    private var entranceAnimationPlayed = false
    private var startupSystemsInitialized = false
    private val ENTRANCE_ANIMATION_TIMEOUT = 2500L
    private val MIN_LOADER_DURATION = 200L
    private var activityStartTime = 0L
    private var showMediaIcon = false

    private val entranceAnimationRunnable = Runnable {
        checkAndPlayEntranceAnimation(force = true)
    }
    private val playEntranceAnimationRunnable = Runnable {
        if (isResumed && !isDestroyed && !isFinishing) {
            entranceAnimationManager.play { completeStartup() }
        }
    }

    private val editModeTimeoutRunnable = Runnable {
        if (isAnySheetShowing()) {
            return@Runnable
        }

        if (isEditMode && !isDemoMode) {
            exitEditMode()
        }
    }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                val lux = event.values[0]
                lastSeenLux = lux

                if (PowerSavePolicy.read(
                        getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE), isPowerSavingMode
                    ).brightnessOverride != null) return

                // Smart Night Dimming logic
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                val brightnessMode = prefs.getString("brightness_mode", "system") ?: "system"
                if (brightnessMode == "smart_night") {
                    val threshold = prefs.getInt("smart_night_lux_threshold", 5)
                    val hysteresis = 3 // 3 lux hysteresis

                    val candidateState = when (currentNightDimState) {
                        NightDimState.NORMAL -> {
                            if (lux < threshold) NightDimState.DIMMED else NightDimState.NORMAL
                        }
                        NightDimState.DIMMED -> {
                            if (lux >= threshold + hysteresis) NightDimState.NORMAL else NightDimState.DIMMED
                        }
                    }

                    if (candidateState != currentNightDimState) {
                        if (candidateState != pendingNightDimState) {
                            dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
                            pendingNightDimState = candidateState
                            dimDebounceHandler.postDelayed(dimDebounceRunnable, 2500L) // 2.5 seconds debounce
                        }
                    } else {
                        // Candidate matches current state; cancel any pending transition to a different state
                        if (pendingNightDimState != null) {
                            dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
                            pendingNightDimState = null
                        }
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun checkAndPlayEntranceAnimation(force: Boolean = false) {
        if (entranceAnimationPlayed) return
        if (force || (isEntranceLayoutReady && isBackgroundReady)) {
            entranceAnimationPlayed = true
            handler.removeCallbacks(entranceAnimationRunnable)
            scheduleEntranceAnimation()
        }
    }

    private fun scheduleEntranceAnimation() {
        mainLayout.removeCallbacks(playEntranceAnimationRunnable)
        val delay = maxOf(0L, MIN_LOADER_DURATION - (SystemClock.uptimeMillis() - activityStartTime))
        mainLayout.postDelayed(playEntranceAnimationRunnable, delay)
    }

    private fun completeStartup() {
        if (startupSystemsInitialized || !isResumed || isDestroyed || isFinishing) return
        startupSystemsInitialized = true
        setupMusicSystem()
        setupDeskConnectSystem()
        loadFogTextures()
        UpdateManager.checkAndShowPostUpdateChangelog(this)
    }

    private fun finishEntranceAnimation() {
        if (!::entranceAnimationManager.isInitialized) return
        entranceAnimationPlayed = true
        handler.removeCallbacks(entranceAnimationRunnable)
        mainLayout.removeCallbacks(playEntranceAnimationRunnable)
        entranceAnimationManager.finish()
        completeStartup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        activityStartTime = SystemClock.uptimeMillis()
        UiMonetThemeManager.applyToActivity(this)
        super.onCreate(savedInstanceState)
        selectedDeskId = savedInstanceState?.getString("selected_desk_id")
        restoredDeskPages = savedInstanceState?.getBundle("desk_pages")

        setupWindowFlags()
        setContentView(R.layout.activity_main)

        initViews()

        sideSheetBehavior = SideSheetBehavior.from(sideSheet).apply {
            state = SideSheetBehavior.STATE_HIDDEN
        }

        handler.postDelayed(entranceAnimationRunnable, ENTRANCE_ANIMATION_TIMEOUT)

        initCoreManagers()
        setupEntranceAnimation()
        initUIManagers()
        setupListeners()
        applyLiveMonetTheme()
        instance = this

        val skipAnimation = savedInstanceState != null
        loadSavedBackground(skipAnimation)
        checkForFirstLaunchAnimation()
        restoreSavedWeatherState()
        startUpdates()
        com.nxd1frnt.clockdesk2.timer.TimerManager.addListener(mainTimerListener)
        handleTimerIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleTimerIntent(intent)
    }

    private fun handleTimerIntent(intent: Intent?) {
        if (intent?.getStringExtra("open_desk") == com.nxd1frnt.clockdesk2.desks.DeskType.TIMER.name) {
            mainLayout.post {
                openDeskType(com.nxd1frnt.clockdesk2.desks.DeskType.TIMER)
            }
        }
    }

    private fun applyLiveMonetTheme() {
        UiMonetThemeManager.applyLiveToMainActivity(this)
        if (::dynamicBackgroundView.isInitialized && ::fontManager.isInitialized) {
            dynamicBackgroundView.updateTurbulenceLiveConfig(color = fontManager.getDynamicScheme().primary)
        }
        if (::customizationSheetManager.isInitialized) {
            customizationSheetManager.refreshMonetTheme()
        }
        if (::widgetGallerySideSheetManager.isInitialized) {
            widgetGallerySideSheetManager.refreshMonetTheme()
        }
        if (::backgroundSheetManager.isInitialized) {
            backgroundSheetManager.refreshMonetTheme()
        }
        if (::deskPageIndicator.isInitialized) {
            deskPageIndicator.refreshMonetTheme()
        }
        if (::deskOverviewManager.isInitialized) {
            deskOverviewManager.refreshMonetTheme()
        }
        if (::desksPagerAdapter.isInitialized) {
            desksPagerAdapter.refreshMonetTheme()
        }
    }

    private fun setupEntranceAnimation() {
        val themeColor = fontManager.getDynamicScheme().primary
        val isWidgetDesk = deskManager.getDeskById(selectedDeskId.orEmpty())?.type ==
            com.nxd1frnt.clockdesk2.desks.DeskType.WIDGETS
        entranceAnimationManager = EntranceAnimationManager(
            rootView = mainLayout,
            widgets = if (isWidgetDesk) desktopWidgetManager.getViews() else listOf(desksViewPager),
            turbulenceOverlay = turbulenceOverlay,
            isTurbulenceEnabled = isAdvancedGraphicsEnabled && isGraphicsTurbulenceEnabled,
            dynamicBackgroundView = dynamicBackgroundView,
            targetTranslationYProvider = { view -> if (view === desksViewPager) 0f else widgetMover.getRestTranslationY(view) },
            themeColor = themeColor
        )
        entranceAnimationManager.prepareViews()
        if (!isWidgetDesk) {
            desksViewPager.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(view: View, left: Int, top: Int, right: Int, bottom: Int,
                    oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                    if (view.width > 0 && view.height > 0) {
                        view.removeOnLayoutChangeListener(this)
                        isEntranceLayoutReady = true
                        checkAndPlayEntranceAnimation()
                    }
                }
            })
        }
    }

    private fun setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private fun initViews() {
        primaryWidgetsContainer = layoutInflater.inflate(R.layout.layout_desk_widgets, null, false) as ConstraintLayout

        timeText = primaryWidgetsContainer.findViewById(R.id.time_text)
        dateText = primaryWidgetsContainer.findViewById(R.id.date_text)
        weatherText = primaryWidgetsContainer.findViewById(R.id.weather_text)
        weatherIcon = primaryWidgetsContainer.findViewById(R.id.weather_icon)
        weatherLayout = primaryWidgetsContainer.findViewById(R.id.weather_layout)
        lastfmLayout = primaryWidgetsContainer.findViewById(R.id.lastfm_layout)
        lastfmIcon = primaryWidgetsContainer.findViewById(R.id.lastfm_icon)
        nowPlayingTextView = primaryWidgetsContainer.findViewById(R.id.now_playing_text)
        backgroundCustomizationTab = primaryWidgetsContainer.findViewById(R.id.background_customization_fab)
        chipContainer = primaryWidgetsContainer.findViewById(R.id.smart_chip_container)
        smartChipScrollView = primaryWidgetsContainer.findViewById(R.id.smart_chip_scrollview)
        emptyDeskPrompt = primaryWidgetsContainer.findViewById(R.id.empty_desk_prompt)

        desksViewPager = findViewById(R.id.desks_view_pager)
        deskPageIndicator = findViewById(R.id.desk_page_indicator)
        btnManageDesks = findViewById<MaterialButton>(R.id.btn_manage_desks)

        backgroundLayout = findViewById(R.id.background_layout)
        backgroundImageView = findViewById(R.id.background_image_view)
        dynamicBackgroundView = findViewById(R.id.dynamic_background_view)
        dynamicBackgroundView.onBackgroundForegroundChanged = { color ->
            if (com.nxd1frnt.clockdesk2.desks.ui.DeskForegroundStyle.update(this, color) &&
                ::desksPagerAdapter.isInitialized) {
                desksPagerAdapter.refreshMonetTheme()
            }
        }
        turbulenceOverlay = findViewById(R.id.turbulence_overlay)
        performanceOverlay = findViewById(R.id.performance_overlay)
        settingsButton = findViewById(R.id.settings_button)
        debugButton = findViewById(R.id.demo_button)
        mainLayout = findViewById(R.id.main_layout)
        sideSheet = findViewById(R.id.side_sheet)
        backgroundBottomSheet = findViewById(R.id.background_bottom_sheet)
        backgroundBottomSheet.visibility = View.GONE

        val widgetGallerySheet = findViewById<LinearLayout>(R.id.widget_gallery_side_sheet)
        SideSheetBehavior.from(widgetGallerySheet).state = SideSheetBehavior.STATE_HIDDEN

        tutorialLayout = findViewById(R.id.tutorial_overlay_root)
        tutorialFinger = findViewById(R.id.tutorial_finger_icon)
        tutorialText = findViewById(R.id.tutorial_text)
        smartPixelOverlay = findViewById(R.id.smart_pixel_overlay)
        editModeBlurLayer = findViewById(R.id.edit_mode_blur_layer)
        editModeActionBar = findViewById(R.id.edit_mode_action_bar)
        btnAddWidget = findViewById(R.id.btn_add_widget)
        btnResetLayout = findViewById(R.id.btn_reset_layout)
        btnDoneEdit = findViewById(R.id.btn_done_edit)
        androidx.core.widget.ImageViewCompat.setImageTintList(editModeBlurLayer, null)
        backgroundCustomizationTab.alpha = 0f
        backgroundCustomizationTab.visibility = View.GONE
        editModeActionBar.visibility = View.GONE
        emptyDeskPrompt.visibility = View.GONE
    }

    private fun initCoreManagers() {
        deskManager = DeskManager(this)
        selectedDeskId = selectedDeskId?.takeIf { deskManager.getDeskById(it) != null }
            ?: deskManager.getInitialDeskId()
        val initialWidgetLayoutKey = deskManager.getDeskById(selectedDeskId!!)
            ?.takeIf { it.type == com.nxd1frnt.clockdesk2.desks.DeskType.WIDGETS }?.widgetLayoutKey
            ?: deskManager.getDesks().firstOrNull { it.type == com.nxd1frnt.clockdesk2.desks.DeskType.WIDGETS }
                ?.widgetLayoutKey ?: DesktopWidgetManager.DEFAULT_LAYOUT_KEY
        deskAppearancePreferences = DeskAppearancePreferences(this).apply { switchLayout(initialWidgetLayoutKey) }
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        enableAdditionalLogging = prefs.getBoolean("additional_logging", false)
        Logger.isLoggingEnabled = enableAdditionalLogging
        showMediaIcon = deskAppearancePreferences.getBoolean("show_media_icon", false)
        isAdvancedGraphicsEnabled = prefs.getBoolean("advanced_graphics", false)
        isGraphicsTransitionsEnabled = prefs.getBoolean("graphics_enable_transitions", true)
        isGraphicsTurbulenceEnabled = prefs.getBoolean("graphics_enable_turbulence", true)
        isGraphicsTurbulenceContinuousMusicEnabled = prefs.getBoolean("graphics_turbulence_music_continuous", false)
        isGraphicsEditBlurEnabled = prefs.getBoolean("graphics_enable_edit_blur", true)
        graphicsRenderScale = prefs.getInt("graphics_render_scale", 100)
        graphicsWeatherScale = prefs.getInt("graphics_weather_scale", 40)

        backgroundManager = BackgroundManager(this)
        locationManager = LocationManager(this, permissionRequestCode)
        dayTimeGetter = SunriseAPI(this, locationManager)

        weatherGetter = OpenMeteoAPI(this, locationManager) {
            runOnUiThread {
                val code = weatherGetter.weatherCode ?: 0
                val wind = weatherGetter.windSpeed ?: 0.0
                val isNight = !dayTimeGetter.isDay()
                val precip = weatherGetter.precipitation
                val clouds = weatherGetter.cloudCover
                val vis = weatherGetter.visibility

                if (backgroundManager.isWeatherEffectsEnabled() && !backgroundManager.isManualWeatherEnabled()) {
                    val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                    val windUnit = prefs.getString("wind_speed_unit", "kmh") ?: "kmh"
                    val precipUnit = prefs.getString("precipitation_unit", "mm") ?: "mm"
                    dynamicBackgroundView.updateFromOpenMeteoSmart(
                        code, wind, isNight,
                        precip, clouds, vis,
                        windUnit, precipUnit
                    )
                }

                if (hasCustomImageBackground) {
                    updateBackgroundFilters()
                }
            }
        }

        gradientManager = GradientManager(
            dynamicBackgroundView,
            dayTimeGetter,
            locationManager,
            handler,
            isCustomBackgroundActive = {
                !isDynamicGradientActive()
            }
        )

        fontManager = FontManager(
            this,
            timeText,
            dateText,
            lastfmLayout,
            nowPlayingTextView,
            lastfmIcon,
            weatherText,
            weatherIcon,
            chipContainer,
            enableAdditionalLogging
        )
        fontManager.appearancePreferences = deskAppearancePreferences
        fontManager.onDynamicSchemeChanged = {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    applyLiveMonetTheme()
                }
            }
        }

        smartChipManager = SmartChipManager(
            this,
            chipContainer,
            deskAppearancePreferences,
            fontManager
        )
        lifecycle.addObserver(smartChipManager)

        clockManager = ClockManager(
            timeText,
            dateText,
            handler,
            fontManager,
            dayTimeGetter,
            locationManager,
            { _, _, _ ->
                if (isDemoMode) {
                    Logger.d("MainActivity") { "debug sun times callback (demo mode)" }
                }
            },
            { currentTime ->
                try {
                    gradientManager.updateSimulatedTime(currentTime)
                } catch (e: Exception) {
                    Logger.w("MainActivity") { "Failed to update gradient simulated time: ${e.message}" }
                }

                if (isDemoMode) {
                    restoreSavedWeatherState(currentTime)
                } else {
                    val isNight = !dayTimeGetter.isDay()
                    if (lastIsNight == null || lastIsNight != isNight) {
                        lastIsNight = isNight
                        if (::backgroundSheetManager.isInitialized && backgroundSheetManager.isShowing) {
                            backgroundSheetManager.applyWeatherPreview()
                        } else {
                            restoreSavedWeatherState()
                        }
                        updateSensorRegistration()
                        if (!isNight) {
                            applyNightDimMode(NightDimState.NORMAL)
                        }
                    }
                }

                try {
                    if (dynamicBackgroundView.visibility == View.VISIBLE) {
                        val mode = backgroundManager.getDimMode()
                        if (mode == BackgroundManager.Companion.DIM_MODE_DYNAMIC || backgroundManager.isNightShiftEnabled()) {
                            updateBackgroundFilters()
                        }
                    }
                } catch (e: Exception) {
                    Logger.w("MainActivity") { "Failed to update dynamic dimming: ${e.message}" }
                }
            },
            prefs,
            enableAdditionalLogging
        )

        desktopWidgetManager = DesktopWidgetManager(
            this, primaryWidgetsContainer, host = this, layoutKey = initialWidgetLayoutKey
        )
        desktopWidgetManager.bindExistingViews(
            timeView = timeText,
            dateView = dateText,
            mediaView = lastfmLayout,
            chipsView = chipContainer,
            weatherView = weatherLayout
        )
        setupMediaWidgetCallbacks()

        val activeViews = desktopWidgetManager.getViews()
        val movableViews = activeViews.filter { it !== smartChipScrollView && it !== chipContainer }

        val burnInShiftDp = prefs.getInt("burn_in_shift_distance_dp", 10).toFloat()
        val burnInShiftPx = (burnInShiftDp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        burnInProtectionManager = BurnInProtectionManager(activeViews, maxShiftPx = burnInShiftPx)
        burnInProtectionManager.positionPreferencesName = DesktopWidgetManager.positionPreferencesName(initialWidgetLayoutKey)

        widgetMover = WidgetMover(this, movableViews, primaryWidgetsContainer,
            positionPreferencesName = DesktopWidgetManager.positionPreferencesName(initialWidgetLayoutKey))
        widgetMover.burnInProtectionManager = burnInProtectionManager

        desktopWidgetManager.onWidgetsChanged = {
            fontManager.loadFont()
            desktopWidgetManager.bindExistingViews(
                timeView = timeText,
                dateView = dateText,
                mediaView = lastfmLayout,
                chipsView = chipContainer,
                weatherView = weatherLayout
            )
            setupMediaWidgetCallbacks()
            desktopWidgetManager.getController<MediaDesktopWidget>()?.apply {
                updateMediaStyle(fontManager.getMediaStyle())
                handleMusicStateUpdate(currentMusicState)
            }
            clockManager.refreshForDeskSwitch(reschedule = isResumed)
            val currentActiveViews = desktopWidgetManager.getViews()
            widgetMover.setViews(currentActiveViews.filter { it !== smartChipScrollView && it !== chipContainer })
            burnInProtectionManager.updateViews(currentActiveViews)
            updateEmptyDeskPromptVisibility()
        }

        widgetMover.onInitialLayoutComplete = {
            isEntranceLayoutReady = true
            checkAndPlayEntranceAnimation()
        }

        setupDesksSystem()

        smartPixelManager = SmartPixelManager(this, smartPixelOverlay, timeoutMs = 10000L)
        if (prefs.getBoolean("smart_pixels_enabled", false)) smartPixelManager.start()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        powerStateManager = PowerStateManager.getInstance(this)
        powerStateManager.registerObserver(this)
        powerStateManager.registerObserver(clockManager)
        powerStateManager.registerObserver(weatherGetter)

        fontManager.loadFont()
        desktopWidgetManager.getController<MediaDesktopWidget>()?.updateMediaStyle(fontManager.getMediaStyle())
        setupPreferencesListener(deskAppearancePreferences)
        com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).addListener(backgroundProviderListener)
    }

    private fun setupDesksSystem() {
        desksPagerAdapter = DesksPagerAdapter(deskManager, primaryWidgetsContainer) { desk ->
            if (desktopWidgetManager.layoutKey != desk.widgetLayoutKey) {
                if (isEditMode) exitEditMode()
                val positionsName = DesktopWidgetManager.positionPreferencesName(desk.widgetLayoutKey)
                deskAppearancePreferences.switchLayout(desk.widgetLayoutKey)
                showMediaIcon = deskAppearancePreferences.getBoolean("show_media_icon", false)
                burnInProtectionManager.positionPreferencesName = positionsName
                widgetMover.setPositionPreferences(positionsName)
                desktopWidgetManager.switchLayout(desk.widgetLayoutKey)
                smartChipManager.onPreferencesChanged()
            }
        }
        val initialPosition = deskManager.getDesks().indexOfFirst { it.id == selectedDeskId }.coerceAtLeast(0)
        desksPagerAdapter.restoreState(restoredDeskPages)
        restoredDeskPages = null
        desksPagerAdapter.selectDesk(initialPosition)
        desksViewPager.isSaveEnabled = false
        desksViewPager.adapter = desksPagerAdapter
        desksViewPager.setCurrentItem(initialPosition, false)
        deskManager.recordSelectedDesk(deskManager.getDesks()[initialPosition].id)
        desksViewPager.offscreenPageLimit = 1
        deskPageIndicator.setPageCount(deskManager.getDesks().size, desksViewPager.currentItem)
        deskPageIndicator.onPageClicked = { pos -> desksViewPager.setCurrentItem(pos, true) }

        var pendingDeskPosition: Int? = null
        var scrollState = ViewPager2.SCROLL_STATE_IDLE

        desksViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                deskPageIndicator.setScrollPosition(position, positionOffset)
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (deskManager.getDesks().getOrNull(position)?.id != selectedDeskId &&
                    ::entranceAnimationManager.isInitialized && !entranceAnimationManager.isComplete) {
                    finishEntranceAnimation()
                }
                if (isEditMode) exitEditMode()
                deskPageIndicator.showTemporarily()
                selectedDeskId = deskManager.getDesks().getOrNull(position)?.id
                selectedDeskId?.let { deskManager.recordSelectedDesk(it) }

                if (scrollState == ViewPager2.SCROLL_STATE_IDLE) {
                    desksPagerAdapter.selectDesk(position)
                } else {
                    pendingDeskPosition = position
                }
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                scrollState = state
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    deskPageIndicator.showTemporarily()
                } else if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    deskPageIndicator.setCurrentPage(desksViewPager.currentItem)
                    val targetPos = pendingDeskPosition ?: desksViewPager.currentItem
                    pendingDeskPosition = null
                    desksPagerAdapter.selectDesk(targetPos)
                }
            }
        })

        deskManager.onDesksChanged = {
            val updatedList = deskManager.getDesks()
            val selectedPosition = updatedList.indexOfFirst { it.id == selectedDeskId }
                .takeIf { it >= 0 } ?: desksViewPager.currentItem.coerceIn(0, updatedList.lastIndex)
            if (updatedList[selectedPosition].id != selectedDeskId) finishEntranceAnimation()
            desksPagerAdapter.updateDesks(updatedList)
            desksViewPager.setCurrentItem(selectedPosition, false)
            selectedDeskId = updatedList[selectedPosition].id
            deskManager.recordSelectedDesk(updatedList[selectedPosition].id)
            desksPagerAdapter.selectDesk(selectedPosition)
            deskPageIndicator.setPageCount(updatedList.size, desksViewPager.currentItem)
            if (::deskOverviewManager.isInitialized) {
                deskOverviewManager.refreshList()
            }
        }

        val overviewOverlay = findViewById<View>(R.id.desk_overview_overlay)
        deskOverviewManager = DeskOverviewManager(this, overviewOverlay, deskManager,
            desksPagerAdapter.previews, { selectedDeskId }, { desksPagerAdapter.capturePreviews() }) { targetIndex ->
            desksViewPager.setCurrentItem(targetIndex, true)
        }

        btnManageDesks.setOnClickListener {
            deskOverviewManager.show()
        }

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (detector.scaleFactor < 0.82f && !isEditMode && !deskOverviewManager.isVisible) {
                    deskOverviewManager.show()
                    return true
                }
                return false
            }
        })

        deskGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                if (!isEditMode && !deskOverviewManager.isVisible) {
                    mainLayout.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    enterEditMode()
                }
            }
        })
    }

    private fun setupDeskConnectSystem() {
        if (::deskNotificationManager.isInitialized) return
        val deskConnectManager = com.nxd1frnt.clockdesk2.connect.DeskConnectManager.getInstance(this)
        val notificationContainer = findViewById<android.view.ViewGroup>(R.id.desk_notification_container)
        val callContainer = findViewById<android.view.ViewGroup>(R.id.desk_call_container)
        deskNotificationManager = com.nxd1frnt.clockdesk2.connect.ui.DeskNotificationManager(this, notificationContainer, deskConnectManager)
        deskCallOverlay = com.nxd1frnt.clockdesk2.connect.ui.DeskCallOverlay(this, callContainer, deskConnectManager)

        if (com.nxd1frnt.clockdesk2.timer.TimerManager.state == com.nxd1frnt.clockdesk2.timer.TimerState.FINISHED) {
            deskNotificationManager.showTimerExpiredNotification(
                title = getString(R.string.timer_time_up),
                text = getString(R.string.timer_dismiss_to_stop)
            )
        }

        val listener = object : com.nxd1frnt.clockdesk2.connect.DeskConnectManager.DeviceListener {
            override fun onDeviceDiscovered(device: com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice) {}
            override fun onDeviceConnected(device: com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice) {}
            override fun onDeviceDisconnected(device: com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice) {}
            override fun onPairingRequested(device: com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice, verificationKey: String) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(UiMonetThemeManager.wrapContext(this@MainActivity))
                            .setTitle(R.string.deskconnect_pairing_request_title)
                            .setMessage(getString(R.string.deskconnect_pairing_request_message, device.getDisplayName(), verificationKey))
                            .setPositiveButton(R.string.deskconnect_accept) { _, _ ->
                                deskConnectManager.acceptPair(device)
                            }
                            .setNegativeButton(R.string.deskconnect_reject) { _, _ ->
                                deskConnectManager.rejectPair(device)
                            }
                            .setCancelable(false)
                            .show()
                        UiMonetThemeManager.styleAlertDialog(dialog)
                    }
                }
            }
            override fun onPairingStateChanged(device: com.nxd1frnt.clockdesk2.connect.model.DeskConnectDevice, isPaired: Boolean) {}
        }
        deskConnectDeviceListener = listener
        deskConnectManager.deviceListeners.add(listener)
    }

    private fun loadFogTextures() {
        lifecycleScope.launch(Dispatchers.IO) {
            val fogBitmap = BitmapFactory.decodeResource(resources, R.drawable.fog)
            val cloudsBitmap = BitmapFactory.decodeResource(resources, R.drawable.clouds)
            withContext(Dispatchers.Main) {
                if (!isDestroyed && !isFinishing && ::dynamicBackgroundView.isInitialized) {
                    dynamicBackgroundView.setFogTextures(fogBitmap, cloudsBitmap)
                    val targetScale = if (isAdvancedGraphicsEnabled) graphicsRenderScale / 100f else 0.5f
                    dynamicBackgroundView.setRenderScale(targetScale)
                    val targetWeatherScale = if (isAdvancedGraphicsEnabled) graphicsWeatherScale / 100f else 0.4f
                    dynamicBackgroundView.weatherResolutionScale = targetWeatherScale
                }
            }
        }
    }

    private fun isAnySheetShowing(): Boolean {
        return (::sideSheetBehavior.isInitialized && sideSheetBehavior.state != SideSheetBehavior.STATE_HIDDEN) ||
               (::widgetGallerySideSheetManager.isInitialized && widgetGallerySideSheetManager.isShowing) ||
               (::backgroundSheetManager.isInitialized && backgroundSheetManager.isShowing) ||
               (::cropController.isInitialized && cropController.isActive())
    }

    private fun showDock(animate: Boolean = true) {
        if (!isEditMode || isCropModeActive) return
        if (isAnySheetShowing()) return

        if (editModeActionBar.visibility == View.VISIBLE &&
            editModeActionBar.alpha >= 0.99f &&
            editModeActionBar.translationY == 0f &&
            editModeActionBar.scaleX >= 0.99f) {
            return
        }

        editModeActionBar.animate().cancel()
        val dockSlideOffset = dpToPx(24f)
        if (animate) {
            editModeActionBar.visibility = View.VISIBLE
            editModeActionBar.alpha = 0f
            editModeActionBar.translationY = dockSlideOffset
            editModeActionBar.scaleX = 0.92f
            editModeActionBar.scaleY = 0.92f
            editModeActionBar.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(animationDuration)
                .setInterpolator(MotionUtils.EXPRESSIVE_SPRING)
                .start()
        } else {
            editModeActionBar.visibility = View.VISIBLE
            editModeActionBar.alpha = 1f
            editModeActionBar.translationY = 0f
            editModeActionBar.scaleX = 1f
            editModeActionBar.scaleY = 1f
        }
    }

    private fun hideDock(animate: Boolean = true) {
        editModeActionBar.animate().cancel()
        val dockSlideOffset = dpToPx(24f)
        if (animate && editModeActionBar.visibility == View.VISIBLE) {
            editModeActionBar.animate()
                .alpha(0f)
                .translationY(dockSlideOffset)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(animationDuration)
                .setInterpolator(MotionUtils.EMPHASIZED_ACCELERATE)
                .withEndAction {
                    if (!isEditMode || isAnySheetShowing() || isCropModeActive) {
                        editModeActionBar.visibility = View.GONE
                    }
                }
                .start()
        } else {
            editModeActionBar.visibility = View.GONE
            editModeActionBar.alpha = 0f
            editModeActionBar.translationY = dockSlideOffset
        }
    }

    private fun handleSheetStateChanged(isHidden: Boolean) {
        if (isHidden) {
            resetEditModeTimeout()
            if (isEditMode && !isAnySheetShowing() && !isCropModeActive) {
                if (editModeActionBar.visibility != View.VISIBLE) {
                    showDock(animate = true)
                }
            }
        } else {
            stopHideUiTimer()
        }
    }

    private fun ensureCustomizationSheetManager(): CustomizationSheetManager {
        if (!::customizationSheetManager.isInitialized) {
            setupSideSheet()
            customizationSheetManager = CustomizationSheetManager(
                sideSheetView = sideSheet,
                mainLayout = mainLayout,
                backgroundCustomizationTab = backgroundCustomizationTab,
                dockView = editModeActionBar,
                fontManager = fontManager,
                widgetMover = widgetMover,
                clockManager = clockManager,
                dayTimeGetter = dayTimeGetter,
                widgetManager = desktopWidgetManager,
                onAddFontRequested = {
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf"))
                        }
                    } else {
                        Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                    }
                    try {
                        isLaunchingFilePicker = true
                        startActivityForResult(intent, PICK_FONT_REQUEST)
                    } catch (e: ActivityNotFoundException) { /* Handle error */ }
                },
                onSheetStateChanged = { isHidden ->
                    handleSheetStateChanged(isHidden)
                }
            )
            customizationSheetManager.onRemoveWidgetRequested = { viewToRemove ->
                removeWidget(viewToRemove)
            }
        }
        return customizationSheetManager
    }

    private fun ensureWidgetGallerySideSheetManager(): WidgetGallerySideSheetManager {
        if (!::widgetGallerySideSheetManager.isInitialized) {
            widgetGallerySideSheetManager = WidgetGallerySideSheetManager(
                sideSheetView = findViewById(R.id.widget_gallery_side_sheet),
                mainLayout = mainLayout,
                backgroundCustomizationTab = backgroundCustomizationTab,
                dockView = editModeActionBar,
                widgetManager = desktopWidgetManager,
                onWidgetAdded = { type ->
                    onWidgetAdded(type)
                },
                onSheetStateChanged = { isHidden ->
                    handleSheetStateChanged(isHidden)
                }
            )
        }
        return widgetGallerySideSheetManager
    }

    private fun ensureCropController(): BackgroundCropController {
        if (!::cropController.isInitialized) {
            val overlayView = findViewById<View>(R.id.crop_overlay)
            cropController = BackgroundCropController(
                dynamicBackgroundView = dynamicBackgroundView,
                overlayRoot = overlayView,
                backgroundManager = backgroundManager,
                widgetViewsProvider = {
                    val views = desktopWidgetManager.getViews().toMutableList()
                    if (emptyDeskPrompt.visibility == View.VISIBLE) {
                        views.add(emptyDeskPrompt)
                    }
                    views
                },
                onApply = {
                    isCropModeActive = false
                    if (dynamicBackgroundView.visibility == View.VISIBLE) {
                        updateBackgroundFilters()
                    }
                    ensureBackgroundSheetManager().show()
                    resetEditModeTimeout()
                },
                onCancel = {
                    isCropModeActive = false
                    ensureBackgroundSheetManager().show()
                    resetEditModeTimeout()
                }
            )
        }
        return cropController
    }

    private fun ensureBackgroundSheetManager(): BackgroundSheetManager {
        if (!::backgroundSheetManager.isInitialized) {
            backgroundSheetManager = BackgroundSheetManager(
                floatingMenuView = backgroundBottomSheet,
                mainLayout = mainLayout,
                backgroundCustomizationTab = backgroundCustomizationTab,
                dockView = editModeActionBar,
                backgroundManager = backgroundManager,
                dayTimeGetter = dayTimeGetter,
                weatherGetter = weatherGetter,
                weatherView = dynamicBackgroundView,
                isMusicBackgroundApplied = { wasMusicBackgroundApplied },
                onAddBackgroundRequested = {
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
                        isLaunchingFilePicker = true
                        startActivityForResult(intent, PICK_BG_REQUEST)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_SHORT).show()
                    }
                },
                onPreviewImage = { uri, blur ->
                    applyImageBackground(uri, blur)
                },
                onPreviewBlur = { blur ->
                    previewBlur(blur)
                },
                onPreviewGradient = {
                    previewGradient()
                },
                onPreviewSolidColor = { color ->
                    applySolidColorBackground(color)
                },
                onPreviewMusicAlbumArt = { enabled, blur ->
                    previewMusicAlbumArt(enabled, blur)
                },
                onRestoreOriginalState = {
                    restoreInitialBackgroundState()
                },
                onUpdateFilters = { previewMode, previewIntensity, previewMin, previewMax, previewNightShift, previewZoom ->
                    if (dynamicBackgroundView.visibility == View.VISIBLE) {
                        updateBackgroundFilters(previewMode, previewIntensity, previewMin, previewMax, previewNightShift, previewZoom)
                    }
                },
                onApplyCompleted = { previewUri, blur ->
                    applyBackgroundSettingsFromSheet(previewUri, blur)
                },
                onClearBackground = {
                    backgroundManager.setSavedBackgroundUri(null)
                    setCustomBackground(false)
                    backgroundImageView.setImageDrawable(null)
                    fontManager.clearDynamicColors()
                    fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, true)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try { backgroundImageView.setRenderEffect(null) } catch (_: Throwable) {}
                    }
                    backgroundImageView.visibility = View.GONE
                    dynamicBackgroundView.visibility = View.VISIBLE
                    backgroundManager.clearDim()
                    gradientManager.startUpdates()
                    lastBackgroundSource = null
                    lastBlurIntensity = null
                    restoreSavedWeatherState()
                    updateBackgroundFilters()
                },
                onSheetStateChanged = { isHidden ->
                    handleSheetStateChanged(isHidden)
                },
                onCropRequested = {
                    isCropModeActive = true
                    hideDock(animate = true)
                    backgroundSheetManager.hide()
                    ensureCropController().enter()
                    stopHideUiTimer()
                }
            )
        }
        return backgroundSheetManager
    }

    private fun initTutorialManager(prefs: SharedPreferences) {
        if (!::tutorialManager.isInitialized) {
            tutorialManager = TutorialManager(
                tutorialLayout = tutorialLayout,
                tutorialFinger = tutorialFinger,
                tutorialText = tutorialText,
                mainLayout = mainLayout,
                timeText = timeText,
                prefs = prefs,
                toggleEditModeAction = { toggleEditMode() },
                showCustomizationAction = { view -> ensureCustomizationSheetManager().showForView(view) },
                hideBottomSheetAction = {
                    if (::customizationSheetManager.isInitialized) customizationSheetManager.hide()
                },
                requestLocationPermissionAction = {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                        permissionRequestCode
                    )
                },
                onTutorialFinished = { checkLocationPermissionsAndLoadData() }
            )
        }
    }

    private fun initUIManagers() {
        sideSheet.bringToFront()
        backgroundBottomSheet.bringToFront()
    }

    private fun setupListeners() {
        mainLayout.setOnLongClickListener { enterEditMode(); true }
        if (::primaryWidgetsContainer.isInitialized) {
            primaryWidgetsContainer.setOnLongClickListener { enterEditMode(); true }
        }

        widgetMover.setOnInteractionListener { isInteracting ->
            if (isInteracting) {
                handler.removeCallbacks(editModeTimeoutRunnable)
            } else {
                if (isEditMode) {
                    resetEditModeTimeout()
                }
            }
        }

        timeText.setOnClickListener {
            if (isEditMode) {
                ensureCustomizationSheetManager().showForView(it)
                resetEditModeTimeout()
            }
        }
        dateText.setOnClickListener {
            if (isEditMode) {
                ensureCustomizationSheetManager().showForView(it)
                resetEditModeTimeout()
            }
        }
        lastfmLayout.setOnClickListener {
            if (isEditMode) {
                ensureCustomizationSheetManager().showForView(it)
                resetEditModeTimeout()
            }
        }
        chipContainer.setOnClickListener {
            if (isEditMode) {
                ensureCustomizationSheetManager().showForView(it)
                resetEditModeTimeout()
            }
        }
        smartChipScrollView.setOnClickListener {
            if (isEditMode) {
                ensureCustomizationSheetManager().showForView(chipContainer)
                resetEditModeTimeout()
            }
        }
        weatherLayout.setOnClickListener {
            if (isEditMode) {
                ensureCustomizationSheetManager().showForView(it)
                resetEditModeTimeout()
            } else {
                com.nxd1frnt.clockdesk2.smartchips.ui.WeatherDialog.show(this)
            }
        }

        backgroundCustomizationTab.setOnClickListener {
            ensureBackgroundSheetManager().show()
            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        }

        debugButton.setOnClickListener {
            isDemoMode = !isDemoMode
            clockManager.toggleDebugMode(isDemoMode)
            gradientManager.toggleDebugMode(isDemoMode)

            if (hasCustomImageBackground) {
                gradientManager.stopUpdates()
                val uriStr = backgroundManager.getSavedBackgroundUri()
                uriStr?.let {
                    try {
                        val uri = Uri.parse(it)
                        applyImageBackground(uri, backgroundManager.getBlurIntensity())
                    } catch (e: Exception) {
                        Logger.w("MainActivity"){"Failed to reapply custom background: ${e.message}"}
                    }
                }
            }

            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        }

//        debugButton.setOnLongClickListener {
//            throw RuntimeException("Test crash triggered by user long-pressing the Demo button.")
//        }

        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            exitEditMode()
        }

        btnAddWidget.setOnClickListener {
            if (::customizationSheetManager.isInitialized && customizationSheetManager.isShowing) {
                customizationSheetManager.hide()
            }
            ensureWidgetGallerySideSheetManager().show()
            stopHideUiTimer()
        }

        btnResetLayout.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(UiMonetThemeManager.wrapContext(this))
                .setTitle(R.string.confirm_reset_layout_title)
                .setMessage(R.string.confirm_reset_layout_msg)
                .setPositiveButton(R.string.reset_layout) { _, _ ->
                    desktopWidgetManager.resetToDefaults()
                    Toast.makeText(this, R.string.reset_layout, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            UiMonetThemeManager.styleAlertDialog(dialog)
            resetEditModeTimeout()
        }

        btnDoneEdit.setOnClickListener {
            exitEditMode()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (::deskOverviewManager.isInitialized && deskOverviewManager.isVisible) {
                    deskOverviewManager.hide()
                    return
                }

                if (::cropController.isInitialized && isCropModeActive) {
                    cropController.cancelAndExit()
                    return
                }

                if (::backgroundSheetManager.isInitialized && backgroundSheetManager.isShowing) {
                    backgroundSheetManager.cancelAndHide()
                    return
                }

                if (::widgetGallerySideSheetManager.isInitialized && widgetGallerySideSheetManager.isShowing) {
                    widgetGallerySideSheetManager.hide()
                    return
                }

                if (::customizationSheetManager.isInitialized && sideSheetBehavior.state != SideSheetBehavior.STATE_HIDDEN) {
                    fontManager.loadFont()
                    widgetMover.restoreOrderAndPositions()
                    customizationSheetManager.hide()
                    return
                }

                if (::tutorialManager.isInitialized && tutorialManager.handleBackPressed()) {
                    return
                }

                if (isEditMode) {
                    exitEditMode()
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun setupPreferencesListener(prefs: SharedPreferences) {
        preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (::smartChipManager.isInitialized && smartChipManager.isChipRelatedPreference(key)) {
                smartChipManager.onPreferencesChanged()
            }

            when (key) {
                "ui_monet_from_background" -> runOnUiThread {
                    applyLiveMonetTheme()
                }
                "show_performance_overlay" -> runOnUiThread {
                    val showOverlay = prefs.getBoolean("show_performance_overlay", false)
                    togglePerformanceOverlay(showOverlay)
                }
                "additional_logging" -> {
                    enableAdditionalLogging = prefs.getBoolean("additional_logging", false)
                    Logger.isLoggingEnabled = enableAdditionalLogging
                }
                "advanced_graphics" -> runOnUiThread {
                    isAdvancedGraphicsEnabled = prefs.getBoolean("advanced_graphics", false)
                    if (::dynamicBackgroundView.isInitialized) {
                        val targetScale = if (isAdvancedGraphicsEnabled) graphicsRenderScale / 100f else 0.5f
                        dynamicBackgroundView.setRenderScale(targetScale)
                        val targetWeatherScale = if (isAdvancedGraphicsEnabled) graphicsWeatherScale / 100f else 0.4f
                        dynamicBackgroundView.weatherResolutionScale = targetWeatherScale
                    }
                    checkAndApplyTurbulenceState(prefs)
                }
                "graphics_enable_transitions" -> {
                    isGraphicsTransitionsEnabled = prefs.getBoolean("graphics_enable_transitions", true)
                }
                "graphics_transition_style" -> runOnUiThread {
                    if (::dynamicBackgroundView.isInitialized) {
                        dynamicBackgroundView.reloadTransitionPreferences()
                    }
                }
                "graphics_enable_edit_blur" -> {
                    isGraphicsEditBlurEnabled = prefs.getBoolean("graphics_enable_edit_blur", true)
                    if (!isGraphicsEditBlurEnabled) {
                        editModeBlurLayer.setImageDrawable(null)
                        editModeBlurLayer.visibility = View.GONE
                    } else if (isEditMode && hasCustomImageBackground) {
                        editModeBlurLayer.visibility = View.VISIBLE
                        editModeBlurLayer.alpha = 1.0f
                    }
                    updateBackgroundFilters(force = true)
                }
                "graphics_enable_turbulence", "graphics_turbulence_music_continuous" -> runOnUiThread {
                    checkAndApplyTurbulenceState(prefs)
                }
                "graphics_turbulence_grid" -> runOnUiThread {
                    val gridScale = prefs.getInt("graphics_turbulence_grid", 10) / 10f
                    if (::dynamicBackgroundView.isInitialized) {
                        dynamicBackgroundView.updateTurbulenceLiveConfig(gridScale = gridScale)
                    }
                }
                "graphics_turbulence_speed" -> runOnUiThread {
                    val speedScale = prefs.getInt("graphics_turbulence_speed", 10) / 10f
                    if (::dynamicBackgroundView.isInitialized) {
                        dynamicBackgroundView.updateTurbulenceLiveConfig(speedScale = speedScale)
                    }
                }
                "graphics_render_scale" -> runOnUiThread {
                    graphicsRenderScale = prefs.getInt("graphics_render_scale", 100)
                    if (::dynamicBackgroundView.isInitialized) {
                        val targetScale = if (isAdvancedGraphicsEnabled) graphicsRenderScale / 100f else 0.5f
                        dynamicBackgroundView.setRenderScale(targetScale)
                    }
                }
                "graphics_weather_scale" -> runOnUiThread {
                    graphicsWeatherScale = prefs.getInt("graphics_weather_scale", 40)
                    if (::dynamicBackgroundView.isInitialized) {
                        val targetWeatherScale = if (isAdvancedGraphicsEnabled) graphicsWeatherScale / 100f else 0.4f
                        dynamicBackgroundView.weatherResolutionScale = targetWeatherScale
                    }
                }
                "lastfm_albumart_background" -> runOnUiThread { handleMusicStateUpdate(currentMusicState) }
                "show_media_icon" -> runOnUiThread {
                    showMediaIcon = prefs.getBoolean("show_media_icon", false)
                    if (!showMediaIcon) {
                        lastfmIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.music_note))
                    } else {
                        updateSourceIcon(MusicTrack(
                            title = "",
                            artist = "",
                            album = "",
                            sourcePackageName = (currentMusicState as? PluginState.Playing)?.track?.sourcePackageName,
                            sourceIconBitmap = (currentMusicState as? PluginState.Playing)?.track?.sourceIconBitmap
                        ))
                    }
                    handleMusicStateUpdate(currentMusicState)
                }
                "brightness_mode" -> runOnUiThread {
                    updateSensorRegistration()
                    val mode = prefs.getString("brightness_mode", "system") ?: "system"
                    if (mode == "system") {
                        cancelPendingDimTransition()
                        currentNightDimState = NightDimState.NORMAL
                        applyNightDimMode(NightDimState.NORMAL)
                    } else {
                        reEvaluateSmartNightDimming()
                    }
                }
                "smart_night_min_brightness" -> runOnUiThread {
                    if (!isPowerSavingMode && prefs.getString("brightness_mode", "system") == "smart_night" && currentNightDimState == NightDimState.DIMMED) {
                        val minBrightPct = prefs.getInt("smart_night_min_brightness", 5) / 100f
                        applyBrightnessOverride(minBrightPct.coerceIn(0.01f, 1.0f))
                    }
                }
                "smart_night_lux_threshold" -> runOnUiThread {
                    if (!isPowerSavingMode && prefs.getString("brightness_mode", "system") == "smart_night") {
                        reEvaluateSmartNightDimming()
                    }
                }
                "burn_in_protection" -> runOnUiThread {
                    if (prefs.getBoolean("burn_in_protection", false)) {
                        burnInProtectionManager.start()
                    } else {
                        burnInProtectionManager.stop()
                    }
                }
                "burn_in_shift_distance_dp" -> runOnUiThread {
                    val shiftDp = prefs.getInt("burn_in_shift_distance_dp", 10).toFloat()
                    burnInProtectionManager.setShiftDistanceDp(this, shiftDp)
                }
                "smart_pixels_enabled" -> runOnUiThread {
                    val enableSmartPixels = prefs.getBoolean("power_saver_enable_smart_pixels", true)
                    val shouldStart = prefs.getBoolean("smart_pixels_enabled", false) || (isPowerSavingMode && enableSmartPixels)
                    if (shouldStart) smartPixelManager.start() else smartPixelManager.stop()
                }
                "smart_pixels_intensity" -> runOnUiThread {
                    val intensity = prefs.getInt("smart_pixels_intensity", 50)
                    smartPixelManager.setIntensity(intensity)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    private fun checkAndApplyTurbulenceState(prefs: SharedPreferences) {
        isAdvancedGraphicsEnabled = prefs.getBoolean("advanced_graphics", false)
        isGraphicsTurbulenceEnabled = prefs.getBoolean("graphics_enable_turbulence", true)
        isGraphicsTurbulenceContinuousMusicEnabled = prefs.getBoolean("graphics_turbulence_music_continuous", false)

        if (::dynamicBackgroundView.isInitialized) {
            val shouldPlayContinuous = isAdvancedGraphicsEnabled &&
                    isGraphicsTurbulenceEnabled &&
                    isGraphicsTurbulenceContinuousMusicEnabled &&
                    currentMusicState is PluginState.Playing

            if (shouldPlayContinuous) {
                if (!dynamicBackgroundView.isTurbulencePlaying) {
                    val noiseColor = fontManager.getDynamicScheme().primary
                    dynamicBackgroundView.playTurbulence(noiseColor, continuous = true)
                } else {
                    dynamicBackgroundView.updateTurbulenceLiveConfig(
                        continuous = true, color = fontManager.getDynamicScheme().primary)
                }
            } else {
                if (!isAdvancedGraphicsEnabled || !isGraphicsTurbulenceEnabled || !isGraphicsTurbulenceContinuousMusicEnabled) {
                    dynamicBackgroundView.finishTurbulence(750L)
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev != null && editModeTouchGuard.shouldConsume(ev)) return true
        if (::smartPixelManager.isInitialized) {
            smartPixelManager.onUserInteraction()
        }
        if (ev != null) {
            if (::deskPageIndicator.isInitialized && (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE)) {
                deskPageIndicator.showTemporarily()
            }
            if (::deskOverviewManager.isInitialized && deskOverviewManager.isVisible) {
                return super.dispatchTouchEvent(ev)
            }
            if (::scaleGestureDetector.isInitialized && !isEditMode) {
                scaleGestureDetector.onTouchEvent(ev)
            }
            if (::deskGestureDetector.isInitialized) {
                deskGestureDetector.onTouchEvent(ev)
            }
        }
        if (ev != null && ::smartChipManager.isInitialized && smartChipManager.isStackExpanded) {
            if (smartChipManager.handleExpandedStackTouch(ev)) {
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }


    private fun setCustomBackground(hasCustom: Boolean) {
        hasCustomImageBackground = hasCustom
        if (::backgroundSheetManager.isInitialized) {
            backgroundSheetManager.updateCropButtonVisibility(hasCustom)
        }
        if (!hasCustom) {
            lastBackgroundSource = null
            lastBlurIntensity = null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::desksPagerAdapter.isInitialized) outState.putBundle("desk_pages", desksPagerAdapter.saveState())
        outState.putString("selected_desk_id", selectedDeskId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::entranceAnimationManager.isInitialized) entranceAnimationManager.destroy()
        if (::mainLayout.isInitialized) mainLayout.removeCallbacks(playEntranceAnimationRunnable)
        if (::desksPagerAdapter.isInitialized) desksPagerAdapter.destroy()
        musicStateUpdates.pause()
        musicStateUpdates.clear()
        backgroundLoads.pause()
        backgroundLoads.clear()
        cancelBackgroundLoads()
        super.onDestroy()
        weatherGetter.stopUpdates()
        musicManager?.destroy()
        if (::deskNotificationManager.isInitialized) deskNotificationManager.destroy()
        if (::deskCallOverlay.isInitialized) deskCallOverlay.destroy()
        deskConnectDeviceListener?.let {
            com.nxd1frnt.clockdesk2.connect.DeskConnectManager.getInstance(this).deviceListeners.remove(it)
            deskConnectDeviceListener = null
        }
        if (::powerStateManager.isInitialized) {
            powerStateManager.unregisterObserver(this)
            if (::clockManager.isInitialized) {
                powerStateManager.unregisterObserver(clockManager)
            }
            powerStateManager.unregisterObserver(weatherGetter)
        }
        if (::clockManager.isInitialized) {
            clockManager.stopUpdates()
        }
        pendingRestoreRunnable?.let { handler.removeCallbacks(it) }
        cancelPendingDimTransition()
        dimDebounceHandler.removeCallbacksAndMessages(null)
        handler.removeCallbacksAndMessages(null)
        if (::backgroundSheetManager.isInitialized) backgroundSheetManager.onDestroy()
        com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).removeListener(backgroundProviderListener)
        deskAppearancePreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        sensorManager.unregisterListener(sensorEventListener)
        com.nxd1frnt.clockdesk2.timer.TimerManager.removeListener(mainTimerListener)
        if (instance === this) instance = null
    }

    private fun setupMusicSystem() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        musicManager = MusicPluginManager(this, prefs) { state ->
            runOnUiThread {
                handleMusicStateUpdate(state)
            }
        }
        setupMediaWidgetCallbacks()
    }

    private fun setupMediaWidgetCallbacks() {
        if (::desktopWidgetManager.isInitialized) {
            desktopWidgetManager.getController<com.nxd1frnt.clockdesk2.widgets.impl.MediaDesktopWidget>()?.apply {
                onPlayPauseAction = { musicManager?.togglePlayPause() }
                onNextAction = { musicManager?.next() }
                onPrevAction = { musicManager?.previous() }
                onTimeoutAction = { performMusicIdleState() }
            }
        }
    }

    private fun updateSourceIcon(track: MusicTrack) {
        if (!showMediaIcon) {
            lastfmIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.music_note))
            val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
            lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
            lastfmIcon.visibility = View.VISIBLE
            return
        }

        val duration = 300L
//        lastfmIcon.animate()
//            .alpha(0f)
//            .setDuration(duration)
//            .withEndAction {
//                // if (isDestroyed || isFinishing) return@withEndAction

                var iconApplied = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    lastfmIcon.clipToOutline = false
                }

                if (track.sourceIconResId != null) {
                    lastfmIcon.setImageDrawable(ContextCompat.getDrawable(this, track.sourceIconResId))

                    val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
                    lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        lastfmIcon.imageAlpha = 255
                    }
                    iconApplied = true
                } else if (track.sourceIconBitmap != null) {
                    lastfmIcon.setImageBitmap(track.sourceIconBitmap)

                    val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
                    lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        lastfmIcon.imageAlpha = 255
                    }
                    iconApplied = true
                } else if (!track.sourcePackageName.isNullOrEmpty()) {
                    try {
                        val icon = packageManager.getApplicationIcon(track.sourcePackageName)
                        var monochromeDrawable: Drawable? = null

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && icon is android.graphics.drawable.AdaptiveIconDrawable) {
                            monochromeDrawable = icon.monochrome
                        }

                        if (monochromeDrawable != null) {
                            lastfmIcon.setImageDrawable(monochromeDrawable)

                            val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
                            lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                lastfmIcon.imageAlpha = 255
                            }
                        } else {
                            lastfmIcon.setImageDrawable(icon)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                lastfmIcon.outlineProvider = object : ViewOutlineProvider() {
                                    override fun getOutline(view: View, outline: Outline) {
                                        outline.setOval(0, 0, view.width, view.height)
                                    }
                                }
                                lastfmIcon.clipToOutline = true
                            }

                            val matrix = ColorMatrix().apply { setSaturation(0f) }
                            lastfmIcon.colorFilter = ColorMatrixColorFilter(matrix)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                lastfmIcon.imageAlpha = 200
                            }
                        }
                        iconApplied = true
                    } catch (e: PackageManager.NameNotFoundException) {
                        Logger.w("MainActivity") { "Couldn't find icon for package: ${track.sourcePackageName}" }
                    }
                }

                if (!iconApplied) {
                    val tintColor = fontManager.getFinalColorForView(R.id.lastfm_layout)
                    lastfmIcon.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
                    lastfmIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.music_note))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        lastfmIcon.imageAlpha = 255
                    }
                }

//                lastfmIcon.animate()
//                    .alpha(1f)
//                    .setDuration(duration)
//                    .start()
            //}.start()
    }

    private fun areArtworkSourcesEqual(source1: Any?, source2: Any?): Boolean {
        if (source1 === source2) return true
        if (source1 == null || source2 == null) return false

        if (source1 is String && source2 is String) {
            return source1 == source2
        }
        if (source1 is Uri && source2 is Uri) {
            return source1 == source2
        }

        if (source1 is Bitmap && source2 is Bitmap) {
            if (source1.isRecycled || source2.isRecycled) return false
            if (source1.width != source2.width || source1.height != source2.height) return false
            return runCatching { source1.sameAs(source2) }.getOrDefault(false)
        }

        return false
    }

    private fun handleMusicStateUpdate(state: PluginState) {
        if (isDestroyed || isFinishing) return
        currentMusicState = state
        musicStateUpdates.submit(state)
    }

    private fun applyMusicStateUpdate(state: PluginState) {
        val isMediaActive = ::desktopWidgetManager.isInitialized && desktopWidgetManager.isWidgetTypeActive(DesktopWidgetType.MEDIA)

        if (state is PluginState.Playing) {
            val track = state.track
            val trackInfoText = "${track.artist} - ${track.title}"
            val isTextDifferent = trackInfoText != lastTrackInfo
            lastTrackInfo = trackInfoText

            val validBitmap = track.artworkBitmap != null && !track.artworkBitmap.isRecycled
            val newArtSource: Any? = if (validBitmap) track.artworkBitmap else if (!track.artworkUrl.isNullOrEmpty()) track.artworkUrl else null
            val isArtChanged = !areArtworkSourcesEqual(newArtSource, currentAppliedArtworkSource)

            Logger.d("MainActivity") {
                "handleMusicStateUpdate: '${track.artist} - ${track.title}', isTextDiff=$isTextDifferent, isArtChanged=$isArtChanged, wasBgApplied=$wasMusicBackgroundApplied, hasBitmap=$validBitmap"
            }

            if (isTextDifferent || isArtChanged || (!wasMusicBackgroundApplied && newArtSource != null)) {
                handleBackgroundUpdate(track)
            } else if (isAdvancedGraphicsEnabled && isGraphicsTurbulenceEnabled && isGraphicsTurbulenceContinuousMusicEnabled) {
                if (!dynamicBackgroundView.isTurbulencePlaying) {
                    val noiseColor = fontManager.getDynamicScheme().primary
                    dynamicBackgroundView.playTurbulence(noiseColor, continuous = true)
                }
            }
        } else if (state is PluginState.Paused) {
            val track = state.track
            lastTrackInfo = "${track.artist} - ${track.title}"

            pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
            pendingBackgroundRestoreRunnable = null

            if (wasMusicBackgroundApplied) {
                restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                wasMusicBackgroundApplied = false
                currentAppliedArtworkSource = null
            }

            if (isGraphicsTurbulenceContinuousMusicEnabled || dynamicBackgroundView.isTurbulencePlaying) {
                dynamicBackgroundView.finishTurbulence(750L)
            }
        } else {
            lastTrackInfo = null
        }

        if (isMediaActive) {
            if (state is PluginState.Playing || state is PluginState.Paused) {
                pendingRestoreRunnable?.let { handler.removeCallbacks(it) }
                pendingRestoreRunnable = null
            }
            desktopWidgetManager.getController<MediaDesktopWidget>()?.handleMusicStateUpdate(state)
        } else {
            lastfmLayout.animate().cancel()
            lastfmLayout.clearAnimation()
            lastfmLayout.visibility = View.GONE
            lastfmLayout.alpha = 0f
        }

        if (state is PluginState.Idle || state is PluginState.Disabled) {
            if (pendingRestoreRunnable == null) {
                val runnable = Runnable {
                    performMusicIdleState()
                    pendingRestoreRunnable = null
                }
                pendingRestoreRunnable = runnable
                handler.postDelayed(runnable, 800) // 800ms delay
            }
        }
    }

    private fun performMusicIdleState() {
        if (!isResumed) return
        pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
        pendingBackgroundRestoreRunnable = null

        desktopWidgetManager.getController<MediaDesktopWidget>()?.performIdleTransition()

        if (wasMusicBackgroundApplied) {
            restoreUserBackground(backgroundManager.getSavedBackgroundUri())
            wasMusicBackgroundApplied = false
            currentAppliedArtworkSource = null
        }

        if (isGraphicsTurbulenceContinuousMusicEnabled || dynamicBackgroundView.isTurbulencePlaying) {
            dynamicBackgroundView.finishTurbulence(1000L)
        }
    }

    private fun handleBackgroundUpdate(track: MusicTrack) {
        val blurIntensity = backgroundManager.getBlurIntensity()
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val musicBgEnabled = prefs.getBoolean("lastfm_albumart_background", true)

        if (!musicBgEnabled) {
            pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
            pendingBackgroundRestoreRunnable = null
            if (wasMusicBackgroundApplied) {
                restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                wasMusicBackgroundApplied = false
                currentAppliedArtworkSource = null
            }
            return
        }

        val validBitmap = track.artworkBitmap != null && !track.artworkBitmap.isRecycled
        if (validBitmap) {
            pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
            pendingBackgroundRestoreRunnable = null

            if (!wasMusicBackgroundApplied || currentAppliedArtworkSource !== track.artworkBitmap) {
                Logger.d("MainActivity") { "Applying bitmap album art background" }
                applyBitmapBackground(track.artworkBitmap!!, blurIntensity)
                wasMusicBackgroundApplied = true
                currentAppliedArtworkSource = track.artworkBitmap
            }
        } else if (!track.artworkUrl.isNullOrEmpty()) {
            pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
            pendingBackgroundRestoreRunnable = null

            if (!wasMusicBackgroundApplied || currentAppliedArtworkSource != track.artworkUrl) {
                Logger.d("MainActivity") { "Applying URL album art background: ${track.artworkUrl}" }
                applyImageBackground(Uri.parse(track.artworkUrl), blurIntensity)
                wasMusicBackgroundApplied = true
                currentAppliedArtworkSource = track.artworkUrl
            }
        } else {
            if (wasMusicBackgroundApplied && pendingBackgroundRestoreRunnable == null) {
                Logger.d("MainActivity") { "No artwork on track, scheduling background restore in 4000ms" }
                val runnable = Runnable {
                    pendingBackgroundRestoreRunnable = null
                    if (wasMusicBackgroundApplied && currentMusicState is PluginState.Playing) {
                        val currentTrack = (currentMusicState as PluginState.Playing).track
                        val isStillMissing = currentTrack.artworkBitmap == null && currentTrack.artworkUrl.isNullOrEmpty()
                        if (isStillMissing) {
                            Logger.d("MainActivity") { "Restoring user default background after 4000ms grace period" }
                            restoreUserBackground(backgroundManager.getSavedBackgroundUri())
                            wasMusicBackgroundApplied = false
                            currentAppliedArtworkSource = null
                        }
                    }
                }
                pendingBackgroundRestoreRunnable = runnable
                handler.postDelayed(runnable, 4000)
            }
        }
    }




    private fun checkForFirstLaunchAnimation() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)
        enableAdditionalLogging = prefs.getBoolean("additional_logging", false)

        if (isFirstLaunch) {
            initTutorialManager(prefs)
            tutorialManager.start()
        }
    }


    private fun checkLocationPermissionsAndLoadData() {
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasCoarse || hasFine) {
            loadCoordinatesAndFetchData()
        } else {
            val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            val rationaleShown = prefs.getBoolean("location_permission_rationale_shown", false)
            if (!rationaleShown) {
                showLocationRationaleDialog()
            } else {
                loadCoordinatesAndFetchData()
                showManualLocationOptionDialog()
            }
        }
    }

    private fun restoreSavedWeatherState(simulatedTime: Date? = null) {
        val isEnabled = backgroundManager.isWeatherEffectsEnabled()
        val targetTime = simulatedTime ?: Calendar.getInstance().time
        val dayFactor = dayTimeGetter.getDayFactor(targetTime)
        val isNight = dayFactor < 0.1f
        lastIsNight = isNight

        if (!isEnabled) {
            dynamicBackgroundView.forceWeather(DynamicBackgroundView.WeatherType.NONE, 0f, 0f, isNight, dayFactor)
            return
        }

        if (backgroundManager.isManualWeatherEnabled()) {
            val typeOrdinal = backgroundManager.getManualWeatherType()
            val type = DynamicBackgroundView.WeatherType.values().getOrElse(typeOrdinal) { DynamicBackgroundView.WeatherType.CLEAR }
            val intensity = backgroundManager.getManualWeatherIntensity() / 100f
            dynamicBackgroundView.forceWeather(type, intensity, intensity * 1.5f, isNight, dayFactor)
        } else {
            val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            val code = weatherGetter.weatherCode ?: 0
            val wind = weatherGetter.windSpeed ?: 0.0
            val precip = weatherGetter.precipitation
            val clouds = weatherGetter.cloudCover
            val vis = weatherGetter.visibility
            val windUnit = prefs.getString("wind_speed_unit", "kmh") ?: "kmh"
            val precipUnit = prefs.getString("precipitation_unit", "mm") ?: "mm"
            dynamicBackgroundView.updateFromOpenMeteoSmart(
                code, wind, isNight,
                precip, clouds, vis,
                windUnit, precipUnit, dayFactor
            )
        }
    }

    private fun showLocationRationaleDialog() {
        val dialog = MaterialAlertDialogBuilder(UiMonetThemeManager.wrapContext(this))
            .setTitle(getString(R.string.location_permission_title))
            .setMessage(getString(R.string.location_permission_message))
            .setPositiveButton(getString(R.string.location_permission_grant)) { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    permissionRequestCode
                )
            }
            .setNegativeButton(getString(R.string.location_permission_manual)) { _, _ ->
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNeutralButton(getString(android.R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
                loadCoordinatesAndFetchData()
                showManualLocationOptionDialog()
            }
            .setOnCancelListener {
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
                loadCoordinatesAndFetchData()
                showManualLocationOptionDialog()
            }
            .show()
        UiMonetThemeManager.styleAlertDialog(dialog)
    }

    private fun showManualLocationOptionDialog() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val promptShown = prefs.getBoolean("manual_location_prompt_shown", false)
        if (promptShown) return

        prefs.edit().putBoolean("manual_location_prompt_shown", true).apply()

        val dialog = MaterialAlertDialogBuilder(UiMonetThemeManager.wrapContext(this))
            .setTitle(getString(R.string.location_permission_title))
            .setMessage(getString(R.string.location_search_city_prompt))
            .setPositiveButton(getString(android.R.string.yes)) { _, _ ->
                showCitySearchDialog()
            }
            .setNegativeButton(getString(android.R.string.no)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        UiMonetThemeManager.styleAlertDialog(dialog)
    }

    private fun showCitySearchDialog() {
        com.nxd1frnt.clockdesk2.ui.dialog.CitySearchDialog.show(this) { selected ->
            val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            prefs.edit()
                .putString("location_mode", "city")
                .putString("location_city_name", selected.name)
                .putString("resolved_latitude", selected.latitude.toString())
                .putString("resolved_longitude", selected.longitude.toString())
                .putString("resolved_city_display_name", selected.displayName)
                .apply()

            Toast.makeText(this, getString(R.string.city_resolved_format, selected.displayName, selected.latitude, selected.longitude), Toast.LENGTH_LONG).show()
            loadCoordinatesAndFetchData()
        }
    }

    private fun loadCoordinatesAndFetchData() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val mode = prefs.getString("location_mode", "auto") ?: "auto"
        val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (mode == "auto" && !hasCoarse && !hasFine) {
            weatherGetter.stopUpdates()
//            runOnUiThread {
//                weatherText.text = getString(R.string.no_location_tap_to_set)
//                weatherIcon.setImageResource(R.drawable.ic_weather_unknown)
//                weatherLayout.visibility = View.VISIBLE
//            }
            // Load coordinates for daytime calculations anyway (using fallback)
            locationManager.loadCoordinates { lat, lon ->
                dayTimeGetter.fetch(lat, lon) {
                    if (isDynamicGradientActive()) gradientManager.updateGradient()
                    if (isNightShiftEnabled) {
                        fontManager.applyNightShiftTransition(
                            clockManager.getCurrentTime(),
                            dayTimeGetter,
                            isNightShiftEnabled
                        )
                    }
                    if (dynamicBackgroundView.visibility == View.VISIBLE) {
                        updateBackgroundFilters()
                    }
                }
            }
            return
        }

        locationManager.loadCoordinates { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (isDynamicGradientActive()) gradientManager.updateGradient()
                if (isNightShiftEnabled) {
                    fontManager.applyNightShiftTransition(
                        clockManager.getCurrentTime(),
                        dayTimeGetter,
                        isNightShiftEnabled
                    )
                }
                if (dynamicBackgroundView.visibility == View.VISIBLE) {
                    updateBackgroundFilters()
                }
            }
            if (isResumed) weatherGetter.startUpdates(lat, lon)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FONT_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val newIndex = fontManager.addCustomFont(uri)
                ensureCustomizationSheetManager().onFontAdded(newIndex)
            }
        }
        if (requestCode == PICK_BG_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (e: Exception) {
                // ignore
            }
            val uriStr = uri.toString()
            backgroundManager.addSavedUri(uriStr)
            ensureBackgroundSheetManager().onImageAdded(uriStr)
        }
    }

    private fun loadSavedBackground(skipAnimation: Boolean = false) {
        val activeProviderId = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).getActiveProviderId()
        Logger.i("BackgroundFlow") { "loadSavedBackground: activeProviderId=$activeProviderId, skipAnimation=$skipAnimation" }
        if (activeProviderId == com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID) {
            val color = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).solidColorProvider.getColor(this)
            applySolidColorBackground(color, skipAnimation) {
                isBackgroundReady = true
                checkAndPlayEntranceAnimation()
            }
            return
        }

        if (activeProviderId.startsWith("external:")) {
            val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
            val provider = bpm.getExternalProviders().firstOrNull { it.id == activeProviderId }
            val payload = provider?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(activeProviderId)
            Logger.i("BackgroundFlow") { "loadSavedBackground: external provider=$activeProviderId, payload=$payload" }
            applyBackgroundPayload(payload, skipAnimation) {
                isBackgroundReady = true
                checkAndPlayEntranceAnimation()
            }
            if (provider != null) {
                bpm.requestDataFromPlugin(provider)
            } else {
                bpm.requestDataFromPluginId(activeProviderId)
            }
            return
        }

        val uriStr = backgroundManager.getSavedBackgroundUri()
        val blurIntensity = backgroundManager.getBlurIntensity()
        if (uriStr != null && activeProviderId != com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID) {
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
                applyImageBackground(uri, blurIntensity, skipAnimation) {
                    isBackgroundReady = true
                    checkAndPlayEntranceAnimation()
                }
                setCustomBackground(true)
                Logger.i("BackgroundFlow") { "Loaded custom background: $uriStr (blurIntensity=$blurIntensity)" }
                updateBackgroundFilters()
            } catch (e: Exception) {
                backgroundManager.setSavedBackgroundUri(null)
                setCustomBackground(false)
                if (!wasMusicBackgroundApplied) {
                    fontManager.clearDynamicColors()
                }
                Logger.w("BackgroundFlow") { "Failed to load saved background: ${e.message}" }

                isBackgroundReady = true
                checkAndPlayEntranceAnimation()
            }
        } else {
            Logger.i("BackgroundFlow") { "No saved custom background, reverting to gradient" }
            backgroundImageView.visibility = View.GONE
            setCustomBackground(false)
            if (!wasMusicBackgroundApplied) {
                fontManager.clearDynamicColors()
            }
            val duration = if (!skipAnimation && isAdvancedGraphicsEnabled && isGraphicsTransitionsEnabled) 1800L else 0L
            gradientManager.updateGradient(duration)
            gradientManager.startUpdates()
            updateBackgroundFilters()

            isBackgroundReady = true
            checkAndPlayEntranceAnimation()
        }
    }

    fun applyBackgroundPayload(
        payload: com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload,
        skipAnimation: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        Logger.i("BackgroundFlow") { "applyBackgroundPayload: $payload, skipAnimation=$skipAnimation" }
        when (payload) {
            is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.SolidColor -> {
                applySolidColorBackground(payload.color, skipAnimation, onComplete)
            }
            is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.ImageUri -> {
                setCustomBackground(true)
                val blur = backgroundManager.getBlurIntensity()
                applyImageBackground(Uri.parse(payload.uriString), blur, skipAnimation, onComplete)
            }
            is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.ImageBitmap -> {
                setCustomBackground(true)
                val blur = backgroundManager.getBlurIntensity()
                applyBitmapBackground(payload.bitmap, blur, skipAnimation, onComplete)
            }
            is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.Gradient -> {
                previewGradient()
                onComplete?.invoke()
            }
        }
    }

    fun applySolidColorBackground(color: Int, skipAnimation: Boolean = false, onComplete: (() -> Unit)? = null) {
        Logger.i("BackgroundFlow") { "applySolidColorBackground: color=#${String.format("%06X", 0xFFFFFF and color)}, skipAnimation=$skipAnimation" }
        backgroundLoads.clear()
        cancelBackgroundLoads()
        isBackgroundLoading = false
        lastBackgroundSource = "solid_color:$color"
        lastBlurIntensity = null
        gradientManager.stopUpdates()
        setCustomBackground(true)
        fontManager.setDynamicScheme(color)
        fontManager.setDynamicColorFromSeed(fontManager.getDynamicScheme().secondary)
        fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, true)

        if (isAdvancedGraphicsEnabled && isGraphicsEditBlurEnabled) {
            editModeBlurLayer.setImageDrawable(ColorDrawable(color))
            if (isEditMode) {
                editModeBlurLayer.visibility = View.VISIBLE
                editModeBlurLayer.alpha = 1.0f
            }
        } else {
            editModeBlurLayer.setImageDrawable(null)
            editModeBlurLayer.visibility = View.GONE
        }

        backgroundImageView.setImageDrawable(null)
        backgroundImageView.visibility = View.GONE
        dynamicBackgroundView.visibility = View.VISIBLE
        dynamicBackgroundView.reloadTransitionPreferences()
        dynamicBackgroundView.setTransitionColorSeed(fontManager.getDynamicScheme().primary)
        val duration = if (skipAnimation || !isAdvancedGraphicsEnabled || !isGraphicsTransitionsEnabled) 0L else 1200L
        dynamicBackgroundView.transitionToSolidColor(color, duration, force = true)
        updateBackgroundFilters(force = true)
        onComplete?.invoke()
    }

    fun applyImageBackground(uri: Uri, blurIntensity: Int = 0, skipAnimation: Boolean = false, onComplete: (() -> Unit)? = null) {
        Logger.i("BackgroundFlow") { "applyImageBackground: uri=$uri, blur=$blurIntensity, skipAnimation=$skipAnimation" }
        loadBackgroundInternal(uri, blurIntensity, skipAnimation, onComplete)
    }

    fun applyBitmapBackground(bitmap: Bitmap, blurIntensity: Int = 0, skipAnimation: Boolean = false, onComplete: (() -> Unit)? = null) {
        Logger.i("BackgroundFlow") { "applyBitmapBackground: bitmap=${bitmap.width}x${bitmap.height}, blur=$blurIntensity, skipAnimation=$skipAnimation" }
        loadBackgroundInternal(bitmap, blurIntensity, skipAnimation, onComplete)
    }

    private fun loadBackgroundInternal(model: Any, blurIntensity: Int, skipAnimation: Boolean = false, onComplete: (() -> Unit)? = null) {
        if (isFinishing || isDestroyed) return
        backgroundLoads.submit(BackgroundLoad(model, blurIntensity, skipAnimation, onComplete))
    }

    private fun cancelBackgroundLoads(
        clearEditBackground: Boolean = true,
        keepCompositeLoading: Boolean = false
    ) {
        backgroundRequestId++
        if (!keepCompositeLoading && ::dynamicBackgroundView.isInitialized) {
            dynamicBackgroundView.cancelCompositeLoading()
        }
        val bgTarget = activeBackgroundTarget
        activeBackgroundTarget = null
        val editTarget = if (clearEditBackground) activeEditBackgroundTarget else null
        if (clearEditBackground) {
            activeEditBackgroundTarget = null
        }
        if (bgTarget == null && editTarget == null) return

        val isActivityDestroyed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed
        try {
            val requestManager = if (isActivityDestroyed) {
                GlideApp.with(applicationContext)
            } else {
                GlideApp.with(this)
            }
            bgTarget?.let { requestManager.clear(it) }
            editTarget?.let { requestManager.clear(it) }
        } catch (e: IllegalArgumentException) {
            try {
                val appRequestManager = GlideApp.with(applicationContext)
                bgTarget?.let { appRequestManager.clear(it) }
                editTarget?.let { appRequestManager.clear(it) }
            } catch (ignored: Exception) {
            }
        } catch (e: Exception) {
            Logger.w("MainActivity") { "Failed to clear background targets: ${e.message}" }
        }
    }

    private fun isBackgroundRequestCurrent(requestId: Long): Boolean =
        requestId == backgroundRequestId && isResumed && !isFinishing && !isDestroyed

    private fun performBackgroundLoad(model: Any, blurIntensity: Int, skipAnimation: Boolean, onComplete: (() -> Unit)?) {
        if (isFinishing) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed) return
        
        Logger.i("BackgroundFlow") { "performBackgroundLoad: model=$model, blur=$blurIntensity, lastSource=$lastBackgroundSource, lastBlur=$lastBlurIntensity" }
        if (areArtworkSourcesEqual(lastBackgroundSource, model) && lastBlurIntensity == blurIntensity) {
            Logger.i("BackgroundFlow") { "performBackgroundLoad: source and blur unchanged, skipping reload" }
            onComplete?.invoke()
            return
        }
        
        val isSourceChanged = !areArtworkSourcesEqual(lastBackgroundSource, model)

        val animAllowed = isAdvancedGraphicsEnabled && isGraphicsTransitionsEnabled && isSourceChanged && !skipAnimation
        cancelBackgroundLoads(keepCompositeLoading = animAllowed)
        val requestId = backgroundRequestId
        isBackgroundLoading = true
        setCustomBackground(true)
        
        lastBackgroundSource = model
        lastBlurIntensity = blurIntensity
        
        try {
            val targetMode = backgroundManager.getDimMode()
            val targetIntensity = backgroundManager.getDimIntensity()
            val effectiveIntensity = getEffectiveDimIntensity(targetMode, targetIntensity)
            val targetZoom = calculateZoom(effectiveIntensity)

            gradientManager.stopUpdates()

            val gradientDrawable = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0F141A"), Color.parseColor("#171E28"))
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                backgroundLayout.background = gradientDrawable
            } else {
                @Suppress("DEPRECATION")
                backgroundLayout.setBackgroundDrawable(gradientDrawable)
            }

            if (animAllowed) {
                dynamicBackgroundView.reloadTransitionPreferences()
                dynamicBackgroundView.setTransitionColorSeed(fontManager.getDynamicScheme().primary)
                dynamicBackgroundView.beginCompositeLoading()
                isScaleAnimating = true
                dynamicBackgroundView.setBackgroundZoom(targetZoom + 0.2f, 400L)
            } else {
                isScaleAnimating = false
                dynamicBackgroundView.setBackgroundZoom(targetZoom + 0.2f)
            }

            if (dynamicBackgroundView.visibility != View.VISIBLE) {
                dynamicBackgroundView.alpha = 0f
                dynamicBackgroundView.visibility = View.VISIBLE
                dynamicBackgroundView.animate().alpha(1f).setDuration(700).start()
            }

            val metrics = resources.displayMetrics
            val maxDim = 1080

            val blurScaleFactor = if (blurIntensity <= 0) 1.0f else {
                val normalized = blurIntensity.coerceIn(0, 100) / 100f
                1.0f - (normalized * 0.75f)
            }

            val targetW = (minOf(metrics.widthPixels, maxDim) * blurScaleFactor).toInt().coerceAtLeast(64)
            val targetH = (minOf(metrics.heightPixels, maxDim) * blurScaleFactor).toInt().coerceAtLeast(64)

            var req = RequestOptions()
                .override(targetW, targetH)
                .downsample(DownsampleStrategy.CENTER_INSIDE)
                .diskCacheStrategy(if (model is Bitmap) DiskCacheStrategy.NONE else DiskCacheStrategy.AUTOMATIC)
                .skipMemoryCache(model is Bitmap)

            if (blurIntensity > 0) {
                req = req.transform(
                    BlurTransformation(this, blurIntensity, 1) {
                        if (isBackgroundRequestCurrent(requestId)) {
                            val blurMsg = getString(R.string.stage_blurring_detail, blurIntensity)
                            updateBackgroundProgress(BackgroundProgressPlugin.Stage.BLURRING, blurMsg)
                        }
                    }
                )
            }

            val requestManager = GlideApp.with(this)
            val mainTarget = object : CustomTarget<Bitmap>() {
                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap>?
                ) {
                    if (!isBackgroundRequestCurrent(requestId)) return
                    // The renderer and color extractor outlive Glide's target. Own their bitmap
                    // so clearing a superseded request cannot recycle an image they still use.
                    val bitmap = resource.copy(Bitmap.Config.ARGB_8888, false)
                    if (bitmap == null) {
                        onLoadFailed(null)
                        return
                    }
                    activeBackgroundTarget = null
                    // Glide forbids clearing the request from inside its own target callback.
                    handler.post { requestManager.clear(this) }
                    if (isSourceChanged) {
                        updateBackgroundProgress(BackgroundProgressPlugin.Stage.EXTRACTING_COLORS)

                        Thread {
                            ColorExtractor.extractColor(bitmap) { seedColor ->
                                handler.post {
                                    if (!isBackgroundRequestCurrent(requestId)) {
                                        bitmap.recycle()
                                        return@post
                                    }
                                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.APPLYING_THEME)

                                    fontManager.setDynamicScheme(seedColor)
                                    fontManager.setDynamicColorFromSeed(fontManager.getDynamicScheme().secondary)

                                    val noiseColor = fontManager.getDynamicScheme().primary
                                    dynamicBackgroundView.reloadTransitionPreferences()
                                    dynamicBackgroundView.setTransitionColorSeed(noiseColor)

                                    val isContinuous = isGraphicsTurbulenceContinuousMusicEnabled && currentMusicState is PluginState.Playing
                                    val useSelfContainedTransition = isGraphicsTransitionsEnabled &&
                                        dynamicBackgroundView.transitionType != DynamicBackgroundView.TransitionType.LIQUID_CHROMATIC
                                    if (isAdvancedGraphicsEnabled && isGraphicsTurbulenceEnabled && isSourceChanged && !skipAnimation && (!useSelfContainedTransition || isContinuous)) {
                                        if (useSelfContainedTransition && isContinuous && dynamicBackgroundView.isTurbulencePlaying) {
                                            dynamicBackgroundView.updateTurbulenceLiveConfig(
                                                continuous = true, color = noiseColor)
                                        } else {
                                            dynamicBackgroundView.playTurbulence(noiseColor, continuous = isContinuous)
                                        }
                                    }

                                    val isBitmap = model is Bitmap
                                    val bgOffsetX = if (isBitmap) 0f else backgroundManager.getBgOffsetX()
                                    val bgOffsetY = if (isBitmap) 0f else backgroundManager.getBgOffsetY()
                                    val bgScale   = if (isBitmap) 1f else backgroundManager.getBgScale()

                                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.RENDERING)

                                    // 1. Запускаем кинематографичный GLSL-переход (Crossfade)
                                    val duration = if (skipAnimation || !isAdvancedGraphicsEnabled || !isGraphicsTransitionsEnabled) 0L else 2000L
                                    Logger.i("BackgroundFlow") { "onResourceReady: starting crossfade transitionTo bitmap (${bitmap.width}x${bitmap.height}) duration=$duration, scale=$bgScale" }
                                    dynamicBackgroundView.transitionTo(bitmap, duration, bgScale, bgOffsetX, bgOffsetY)

                                    val currentTargetMode = backgroundManager.getDimMode()
                                    val currentTargetIntensity = backgroundManager.getDimIntensity()
                                    val currentEffectiveIntensity = getEffectiveDimIntensity(currentTargetMode, currentTargetIntensity)
                                    val finalZoom = calculateZoom(currentEffectiveIntensity)

                                    fontManager.applyNightShiftTransition(
                                        clockManager.getCurrentTime(),
                                        dayTimeGetter,
                                        true
                                    )

                                    isBackgroundLoading = false

                                    updateBackgroundFilters()

                                    if (isAdvancedGraphicsEnabled && isGraphicsTransitionsEnabled && !skipAnimation) {
                                        isScaleAnimating = true
                                        dynamicBackgroundView.setBackgroundZoom(finalZoom, 1200L) {
                                            if (isBackgroundRequestCurrent(requestId)) {
                                                isScaleAnimating = false
                                                updateBackgroundProgress(BackgroundProgressPlugin.Stage.COMPLETED)
                                            }
                                        }
                                    } else {
                                        dynamicBackgroundView.setBackgroundZoom(finalZoom)
                                        isScaleAnimating = false
                                        updateBackgroundProgress(BackgroundProgressPlugin.Stage.COMPLETED)
                                    }
                                    onComplete?.invoke()
                                }
                            }
                        }.start()
                    } else {
                        val isBitmap = model is Bitmap
                        val bgOffsetX = if (isBitmap) 0f else backgroundManager.getBgOffsetX()
                        val bgOffsetY = if (isBitmap) 0f else backgroundManager.getBgOffsetY()
                        val bgScale   = if (isBitmap) 1f else backgroundManager.getBgScale()

                        updateBackgroundProgress(BackgroundProgressPlugin.Stage.RENDERING)

                        val duration = if (skipAnimation || !isAdvancedGraphicsEnabled || !isGraphicsTransitionsEnabled) 0L else 300L
                        Logger.i("BackgroundFlow") { "onResourceReady: quick transitionTo bitmap (${bitmap.width}x${bitmap.height}) duration=$duration" }
                        dynamicBackgroundView.transitionTo(bitmap, duration, bgScale, bgOffsetX, bgOffsetY)

                        updateBackgroundFilters()
                        updateBackgroundProgress(BackgroundProgressPlugin.Stage.COMPLETED)
                        isBackgroundLoading = false
                        onComplete?.invoke()
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                    // No borrowed Glide bitmap is retained after onResourceReady.
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    if (!isBackgroundRequestCurrent(requestId)) return
                    super.onLoadFailed(errorDrawable)
                    activeBackgroundTarget = null
                    handler.post { requestManager.clear(this) }
                    isBackgroundLoading = false
                    dynamicBackgroundView.cancelCompositeLoading()
                    lastBackgroundSource = null
                    lastBlurIntensity = null
                    updateBackgroundProgress(BackgroundProgressPlugin.Stage.FAILED, getString(R.string.stage_failed))
                    Logger.w("BackgroundFlow") { "Glide failed to load background: $model" }
                    isScaleAnimating = false

                    onComplete?.invoke()

                    handler.post {
                        if (!isBackgroundRequestCurrent(requestId)) return@post
                        try {
                            val savedUri = backgroundManager.getSavedBackgroundUri()
                            if (model.toString() != savedUri) {
                                restoreUserBackground(savedUri)
                            }
                        } catch (e: Exception) {
                            Logger.e("MainActivity"){"Failed to restore user background: ${e.message}"}
                        }
                    }
                }

                override fun onLoadStarted(placeholder: Drawable?) {
                    if (!isBackgroundRequestCurrent(requestId)) return
                    super.onLoadStarted(placeholder)
                    val initialStage = when {
                        model is Uri && (model.scheme == "http" || model.scheme == "https") -> BackgroundProgressPlugin.Stage.DOWNLOADING
                        model is String && (model.startsWith("http://") || model.startsWith("https://")) -> BackgroundProgressPlugin.Stage.DOWNLOADING
                        else -> BackgroundProgressPlugin.Stage.LOADING
                    }
                    updateBackgroundProgress(initialStage)
                }
            }

            activeBackgroundTarget = mainTarget
            GlideApp.with(this)
                .asBitmap()
                .load(model)
                .apply(req)
                .into(mainTarget)

            if (isAdvancedGraphicsEnabled) {
                applyEditModeBlurLayer(model)
            } else {
                editModeBlurLayer.setImageDrawable(null)
                editModeBlurLayer.visibility = View.GONE
            }

        } catch (e: Exception) {
            if (!isBackgroundRequestCurrent(requestId)) return
            isBackgroundLoading = false
            updateBackgroundProgress(BackgroundProgressPlugin.Stage.FAILED, getString(R.string.stage_failed))
            dynamicBackgroundView.cancelCompositeLoading()
            Logger.e("MainActivity"){"loadBackgroundInternal failed: ${e.message}"}
            onComplete?.invoke()
        }
    }

    private fun applyEditModeBlurLayer(model: Any) {
        if (isEditMode) {
            editModeBlurLayer.visibility = View.VISIBLE
            editModeBlurLayer.alpha = 1.0f
        }
        val ambientReq = RequestOptions()
            .transform(FitCenter(), BlurTransformation(this, 20, 3))
            .override(200, 400)
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

        val editTarget = object : CustomTarget<Drawable>() {
            override fun onResourceReady(
                resource: Drawable,
                transition: Transition<in Drawable>?
            ) {
                if (activeEditBackgroundTarget !== this || isDestroyed || isFinishing) return
                editModeBlurLayer.setImageDrawable(resource)
                if (isEditMode) {
                    editModeBlurLayer.visibility = View.VISIBLE
                    editModeBlurLayer.alpha = 1.0f
                }
                updateBackgroundFilters(force = true)
            }
            override fun onLoadCleared(placeholder: Drawable?) {
                editModeBlurLayer.setImageDrawable(null)
            }
        }

        activeEditBackgroundTarget = editTarget
        GlideApp.with(this)
            .load(model)
            .apply(ambientReq)
            .into(editTarget)
    }

    private fun getEffectiveDimIntensity(
        mode: Int,
        userIntensity: Int,
        previewDimMin: Int? = null,
        previewDimMax: Int? = null
    ): Int {
        val intensity = when (mode) {
            BackgroundManager.Companion.DIM_MODE_OFF -> 0
            BackgroundManager.Companion.DIM_MODE_DYNAMIC -> {
                try {
                    backgroundManager.computeEffectiveDimIntensity(
                        clockManager.getCurrentTime(),
                        dayTimeGetter,
                        overrideMode = mode,
                        overrideMin = previewDimMin,
                        overrideMax = previewDimMax
                    )
                } catch (e: Exception) {
                    (previewDimMax ?: backgroundManager.getDimMaxIntensity()).coerceIn(0, 50)
                }
            }
            else -> userIntensity.coerceIn(0, 50) // CONTINUOUS
        }
        return intensity.coerceIn(0, 50)
    }

    private fun calculateZoom(effectiveIntensity: Int, previewZoom: Boolean? = null): Float {
        val zoomEnabled = previewZoom ?: backgroundManager.getZoomEnabled()
        if (!zoomEnabled) {
            return 1.0f
        }
        return 1.0f + (effectiveIntensity.coerceIn(0, 50) / 50f) * 0.2f
    }

    private fun updateBackgroundFilters(
        previewDimMode: Int? = null,
        previewDimIntensity: Int? = null,
        previewDimMinIntensity: Int? = null,
        previewDimMaxIntensity: Int? = null,
        previewNightShift: Boolean? = null,
        previewZoom: Boolean? = null,
        force: Boolean = false
    ) {
        if (dynamicBackgroundView.visibility != View.VISIBLE) return

        val dimMode = previewDimMode ?: backgroundManager.getDimMode()
        // For continuous mode preview, use the single-slider value (previewDimIntensity).
        // For dynamic mode preview, use max (previewDimMaxIntensity) so the helper can
        // interpolate between min and max correctly.
        // Fall back to saved prefs when no preview overrides are given.
        val dimIntensity = when {
            previewDimMode == BackgroundManager.Companion.DIM_MODE_CONTINUOUS && previewDimIntensity != null -> previewDimIntensity
            previewDimMode == BackgroundManager.Companion.DIM_MODE_DYNAMIC && previewDimMaxIntensity != null -> previewDimMaxIntensity
            else -> backgroundManager.getDimIntensity()
        }
        val effectiveDim = getEffectiveDimIntensity(
            dimMode,
            dimIntensity,
            previewDimMinIntensity,
            previewDimMaxIntensity
        )

        val maxDarkness = 0.8f
        val dimFactor = (effectiveDim / 50f) * maxDarkness

        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val dimBackground = prefs.getBoolean("power_saver_dim_background", true)
        val finalDimFactor = if (isPowerSavingMode && dimBackground) {
            val dimLevel = prefs.getInt("power_saver_dim_level", 85) / 100f
            dimLevel
        } else {
            dimFactor
        }
        val brightness = 1.0f - finalDimFactor

        val isWeatherEnabled = backgroundManager.isWeatherEffectsEnabled()
        val isNight = !dayTimeGetter.isDay()
        var wmoCode = 0
        var rawIntensity = 0f

        if (isWeatherEnabled) {
            val isManual = backgroundManager.isManualWeatherEnabled()
            if (isManual) {
                val typeOrdinal = backgroundManager.getManualWeatherType()
                val type = DynamicBackgroundView.WeatherType.values()[typeOrdinal]
                wmoCode = when (type) {
                    DynamicBackgroundView.WeatherType.CLEAR -> 0
                    DynamicBackgroundView.WeatherType.CLOUDY -> 3
                    DynamicBackgroundView.WeatherType.FOG -> 45
                    DynamicBackgroundView.WeatherType.RAIN -> 63
                    DynamicBackgroundView.WeatherType.SNOW -> 73
                    DynamicBackgroundView.WeatherType.THUNDERSTORM -> 95
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
        }

        val visualIntensity = rawIntensity * 0.2f

        // Night Shift Factor for Background
        val nightShiftEnabled = previewNightShift ?: backgroundManager.isNightShiftEnabled()
        val nightFactor = if (nightShiftEnabled) backgroundManager.computeNightShiftFactor(clockManager.getCurrentTime(), dayTimeGetter) else 0f

        val isPreview = previewDimMode != null || previewDimIntensity != null ||
            previewDimMinIntensity != null || previewDimMaxIntensity != null ||
            previewNightShift != null || previewZoom != null

        if (!force && !isPreview &&
            kotlin.math.abs(lastFilterDimFactor - finalDimFactor) < 0.003f &&
            kotlin.math.abs(lastFilterNightFactor - nightFactor) < 0.005f &&
            lastFilterWmoCode == wmoCode &&
            lastFilterIsNight == isNight &&
            lastFilterIsWeatherEnabled == isWeatherEnabled &&
            kotlin.math.abs(lastFilterVisualIntensity - visualIntensity) < 0.005f &&
            lastFilterIsEditMode == isEditMode
        ) {
            return
        }

        lastFilterDimFactor = finalDimFactor
        lastFilterNightFactor = nightFactor
        lastFilterWmoCode = wmoCode
        lastFilterIsNight = isNight
        lastFilterIsWeatherEnabled = isWeatherEnabled
        lastFilterVisualIntensity = visualIntensity
        lastFilterIsEditMode = isEditMode

        val baseWeatherMatrix = ColorMatrix()
        if (isWeatherEnabled) {
            val weatherMatrix = getWeatherMatrix(wmoCode, isNight, visualIntensity)
            baseWeatherMatrix.postConcat(weatherMatrix)
        }

        if (nightFactor > 0f) {
            val nightShiftMatrix = ColorMatrix()
            val rScale = 1.0f
            val gScale = 1.0f - (0.55f * nightFactor)
            val bScale = 1.0f - (0.80f * nightFactor)
            nightShiftMatrix.setScale(rScale, gScale, bScale, 1f)
            baseWeatherMatrix.postConcat(nightShiftMatrix)
        }

        val combinedMatrix = ColorMatrix(baseWeatherMatrix)
        val dimMatrix = ColorMatrix()
        dimMatrix.setScale(brightness, brightness, brightness, 1f)
        combinedMatrix.postConcat(dimMatrix)

        dynamicBackgroundView.setColorFilter(combinedMatrix)

        if (isEditMode && isAdvancedGraphicsEnabled && isGraphicsEditBlurEnabled && hasCustomImageBackground) {
            val editModeMatrix = ColorMatrix(baseWeatherMatrix)
            val editModeDim = ColorMatrix()
            val blendedBrightness = 1.0f - (finalDimFactor * 0.3f)
            val finalEditScale = 0.35f * blendedBrightness
            editModeDim.setScale(finalEditScale, finalEditScale, finalEditScale, 1.0f)
            editModeMatrix.postConcat(editModeDim)
            editModeBlurLayer.colorFilter = android.graphics.ColorMatrixColorFilter(editModeMatrix)
        } else {
            editModeBlurLayer.clearColorFilter()
        }

        if (!isScaleAnimating) {
            val zoom = calculateZoom(effectiveDim, previewZoom)
            dynamicBackgroundView.setBackgroundZoom(zoom)
        }
    }

    private fun updateBackgroundProgress(stage: BackgroundProgressPlugin.Stage, messageOverride: String? = null) {
        BackgroundProgressPlugin.postStage(stage, messageOverride)
    }

    private fun setupSideSheet() {
        val fontRecyclerView = sideSheet.findViewById<RecyclerView>(R.id.font_recycler_view)
        fontRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fontRecyclerView.isNestedScrollingEnabled = false
        sideSheetBehavior.addCallback(object : SideSheetCallback() {
            override fun onStateChanged(sheet: View, newState: Int) {
                if (newState == SideSheetBehavior.STATE_HIDDEN) {
                    resetEditModeTimeout()
                    if (::customizationSheetManager.isInitialized) {
                        customizationSheetManager.hide()
                    }
                } else {
                    stopHideUiTimer()
                }
            }

            override fun onSlide(sheet: View, slideOffset: Float) {}
        })
        sideSheet.bringToFront()
    }

    private fun showDeleteConfirmationDialog(title: String, message: String, onConfirm: () -> Unit) {
        val dialog = MaterialAlertDialogBuilder(UiMonetThemeManager.wrapContext(this))
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        UiMonetThemeManager.styleAlertDialog(dialog)
    }

    private fun startUpdates() {
        clockManager.startUpdates()
        if (isDynamicGradientActive()) gradientManager.startUpdates()
        if (getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE).getBoolean("burn_in_protection", false)) burnInProtectionManager.start()
        else burnInProtectionManager.stop()
    }

    private fun stopUpdates() {
        clockManager.stopUpdates()
        gradientManager.stopUpdates()
        weatherGetter.stopUpdates()
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun animateCornerRadius(
        view: View,
        fromRadius: Float,
        toRadius: Float,
        duration: Long = animationDuration,
        interpolator: TimeInterpolator = MotionUtils.EMPHASIZED
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val animator = ValueAnimator.ofFloat(fromRadius, toRadius)
            animator.duration = duration
            animator.interpolator = interpolator

            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Float

                view.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
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

    private fun enterEditMode() {
        if (isEditMode) return
        if (::deskManager.isInitialized && ::desksViewPager.isInitialized) {
            val currentDesk = deskManager.getDesks().getOrNull(desksViewPager.currentItem)
            if (currentDesk != null && currentDesk.type != com.nxd1frnt.clockdesk2.desks.DeskType.WIDGETS) {
                return
            }
        }
        editModeTouchGuard.cancelCurrentGesture { cancel ->
            if (::deskGestureDetector.isInitialized) deskGestureDetector.onTouchEvent(cancel)
            if (::scaleGestureDetector.isInitialized) scaleGestureDetector.onTouchEvent(cancel)
            if (::smartChipManager.isInitialized && smartChipManager.isStackExpanded) {
                smartChipManager.handleExpandedStackTouch(cancel)
            }
            super.dispatchTouchEvent(cancel)
        }
        isEditMode = true
        smartChipManager.setEditMode(true) { clickedView ->
            ensureCustomizationSheetManager().showForView(clickedView)
            resetEditModeTimeout()
        }
        widgetMover.setEditMode(true)
        val targetRadius = dpToPx(36f)

        if (::desksViewPager.isInitialized) {
            desksViewPager.post {
                if (isEditMode) {
                    desksViewPager.isUserInputEnabled = false
                }
            }
        }

        backgroundCustomizationTab.visibility = View.VISIBLE
        backgroundCustomizationTab.alpha = 0f
        backgroundCustomizationTab.scaleX = 0.8f
        backgroundCustomizationTab.scaleY = 0.8f
        backgroundCustomizationTab.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(animationDuration)
            .setInterpolator(MotionUtils.EMPHASIZED_DECELERATE)
            .start()

        mainLayout.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(animationDuration)
            .setInterpolator(MotionUtils.EMPHASIZED)
            .start()
        animateCornerRadius(mainLayout, 0f, targetRadius, animationDuration, MotionUtils.EMPHASIZED)

        if (isAdvancedGraphicsEnabled && isGraphicsEditBlurEnabled && hasCustomImageBackground) {
            editModeBlurLayer.animate().cancel()
            editModeBlurLayer.visibility = View.VISIBLE
            editModeBlurLayer.alpha = 1.0f
            updateBackgroundFilters(force = true)
        }

        showDock(animate = true)

        updateEmptyDeskPromptVisibility()

        desktopWidgetManager.setEditMode(true)
        if (!isDemoMode) {
            handler.removeCallbacks(editModeTimeoutRunnable)
            handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
        }
    }

    private fun toggleEditMode() {
        if (isEditMode) {
            exitEditMode()
        } else {
            enterEditMode()
        }
    }

    private fun resetEditModeTimeout() {
        if (!isDemoMode && !isCropModeActive) {
            handler.removeCallbacks(editModeTimeoutRunnable)
            handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
        }
    }

    private fun stopHideUiTimer() {
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun exitEditMode() {
        if (!isEditMode) return
        isEditMode = false
        if (::desksViewPager.isInitialized) {
            desksViewPager.isUserInputEnabled = true
        }
        //updateBackgroundFilters(force = true)
        smartChipManager.setEditMode(false) { }
        widgetMover.setEditMode(false)
        val startRadius = dpToPx(36f)

        mainLayout.animate()
            .scaleX(1f)
            .scaleY(1f)
            .translationX(0f)
            .translationY(0f)
            .setDuration(animationDuration)
            .setInterpolator(MotionUtils.EMPHASIZED)
            .start()
        animateCornerRadius(mainLayout, startRadius, 0f, animationDuration, MotionUtils.EMPHASIZED)

        if (editModeBlurLayer.visibility == View.VISIBLE) {
            editModeBlurLayer.animate().cancel()
            editModeBlurLayer.animate()
                .alpha(0f)
                .setDuration(animationDuration)
                .setInterpolator(MotionUtils.EMPHASIZED_ACCELERATE)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (!isEditMode) {
                            editModeBlurLayer.visibility = View.GONE
                        }
                    }
                })
                .start()
        }

        backgroundCustomizationTab.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(animationDuration)
            .setInterpolator(MotionUtils.EMPHASIZED_ACCELERATE)
            .withEndAction {
                if (!isEditMode) {
                    backgroundCustomizationTab.visibility = View.GONE
                }
            }
            .start()

        hideDock(animate = true)

        if (::cropController.isInitialized && isCropModeActive) {
            cropController.cancelAndExit()
        }

        if (::customizationSheetManager.isInitialized && customizationSheetManager.isShowing) {
            customizationSheetManager.hide()
        }

        if (::backgroundSheetManager.isInitialized && backgroundSheetManager.isShowing) {
            backgroundSheetManager.cancelAndHide()
        }

        emptyDeskPrompt.visibility = View.GONE
        desktopWidgetManager.setEditMode(false)

        chipContainer.background = null
        smartChipScrollView.background = null

        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun updateEmptyDeskPromptVisibility() {
        val hasVisibleWidgets = desktopWidgetManager.getActiveInstances().isNotEmpty()
        emptyDeskPrompt.visibility = if (isEditMode && !hasVisibleWidgets) View.VISIBLE else View.GONE
    }

    private fun removeWidget(view: View) {
        val instance = desktopWidgetManager.getInstanceForView(view)
        if (instance != null) {
            desktopWidgetManager.removeWidget(instance)
            view.animate().cancel()
            view.clearAnimation()
            view.visibility = View.GONE
            view.alpha = 0f
            widgetMover.removeView(view)
            burnInProtectionManager.updateViews(desktopWidgetManager.getViews())
            updateEmptyDeskPromptVisibility()
            Toast.makeText(this, R.string.remove_widget, Toast.LENGTH_SHORT).show()
        }
    }

    private fun onWidgetAdded(type: DesktopWidgetType) {
        val view = when (type) {
            DesktopWidgetType.TIME -> timeText
            DesktopWidgetType.DATE -> dateText
            DesktopWidgetType.MEDIA -> lastfmLayout
            DesktopWidgetType.SMART_CHIPS -> chipContainer
            DesktopWidgetType.WEATHER -> weatherLayout
        }
        view.visibility = View.VISIBLE
        if (isEditMode) {
            view.setBackgroundResource(R.drawable.editable_border)
        }
        if (type == DesktopWidgetType.MEDIA) {
            lastfmLayout.alpha = 1f
            if (nowPlayingTextView.text.isNullOrEmpty()) {
                nowPlayingTextView.text = getString(R.string.now_playing_placeholder)
            }
        }
        desktopWidgetManager.bindExistingViews(timeText, dateText, lastfmLayout, chipContainer, weatherLayout)
        val activeViews = desktopWidgetManager.getViews()
        widgetMover.setViews(activeViews.filter { it !== smartChipScrollView && it !== chipContainer })
        burnInProtectionManager.updateViews(activeViews)
        fontManager.loadFont()
        updateEmptyDeskPromptVisibility()
    }

    override fun onResume() {
        super.onResume()
        if (::desksPagerAdapter.isInitialized) desksPagerAdapter.setHostResumed(true)
        isResumed = true
        desktopWidgetManager.getControllers().forEach { it.onResume() }
        if (::entranceAnimationManager.isInitialized) {
            if (entranceAnimationManager.isComplete) completeStartup()
            else if (entranceAnimationPlayed && !entranceAnimationManager.hasStarted) scheduleEntranceAnimation()
        }
        updateSensorRegistration()
        if (::tutorialManager.isInitialized) {
            tutorialManager.checkAndUpdatePermissionState()
        }
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val disableAnimations = prefs.getBoolean("power_saver_disable_animations", true)
        if (::dynamicBackgroundView.isInitialized) {
            dynamicBackgroundView.onResume()
            dynamicBackgroundView.areAnimationsPaused = isPowerSavingMode && disableAnimations
        }
        setupWindowFlags()
        onPowerSaveModeChanged(powerStateManager.isPowerSaveEnabled())
        locationManager.loadCoordinates { lat, lon ->
            dayTimeGetter.fetch(lat, lon) {
                if (isDynamicGradientActive()) gradientManager.updateGradient()
                if (isNightShiftEnabled) fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, isNightShiftEnabled)
                if (dynamicBackgroundView.visibility == View.VISIBLE) updateBackgroundFilters()
                updateSensorRegistration()
                if (dayTimeGetter.isDay()) {
                    applyNightDimMode(NightDimState.NORMAL)
                } else {
                    reEvaluateSmartNightDimming()
                }
            }
            if (isResumed && ::weatherGetter.isInitialized) {
                weatherGetter.startUpdates(lat, lon)
            }
        }
        if (isEditMode && !isLaunchingFilePicker && !isLaunchingExternalActivity && !isAnySheetShowing()) {
            exitEditMode()
        }
        isLaunchingFilePicker = false
        isLaunchingExternalActivity = false
        if (isDemoMode) {
            isDemoMode = false
            clockManager.toggleDebugMode(false)
            gradientManager.toggleDebugMode(false)
        }

        enableAdditionalLogging = prefs.getBoolean("additional_logging", false)
        isAdvancedGraphicsEnabled = prefs.getBoolean("advanced_graphics", false)
        isGraphicsTransitionsEnabled = prefs.getBoolean("graphics_enable_transitions", true)
        isGraphicsTurbulenceEnabled = prefs.getBoolean("graphics_enable_turbulence", true)
        isGraphicsTurbulenceContinuousMusicEnabled = prefs.getBoolean("graphics_turbulence_music_continuous", false)
        isGraphicsEditBlurEnabled = prefs.getBoolean("graphics_enable_edit_blur", true)
        graphicsRenderScale = prefs.getInt("graphics_render_scale", 100)
        graphicsWeatherScale = prefs.getInt("graphics_weather_scale", 40)
        if (::dynamicBackgroundView.isInitialized) {
            dynamicBackgroundView.reloadTransitionPreferences()
            val targetScale = if (isAdvancedGraphicsEnabled) graphicsRenderScale / 100f else 0.5f
            dynamicBackgroundView.setRenderScale(targetScale)
            val targetWeatherScale = if (isAdvancedGraphicsEnabled) graphicsWeatherScale / 100f else 0.4f
            dynamicBackgroundView.weatherResolutionScale = targetWeatherScale
        }

        val enableSmartPixels = prefs.getBoolean("power_saver_enable_smart_pixels", true)
        val shouldStartSmartPixels = prefs.getBoolean("smart_pixels_enabled", false) || (isPowerSavingMode && enableSmartPixels)
        if (shouldStartSmartPixels) smartPixelManager.start() else smartPixelManager.stop()
        deskAppearancePreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        smartChipManager.updateAllChips()
        val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
        val activeProviderId = bpm.getActiveProviderId()
        if (activeProviderId.startsWith("external:") && startupSystemsInitialized) {
            val plugin = bpm.getExternalProviders().firstOrNull { it.id == activeProviderId }
            val payload = plugin?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(activeProviderId)
            if (!isMusicBackgroundActive() && payload !is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.Gradient) {
                applyBackgroundPayload(payload, skipAnimation = true)
            }
            if (plugin != null) {
                bpm.requestDataFromPlugin(plugin)
            } else {
                bpm.requestDataFromPluginId(activeProviderId)
            }
        }
        startUpdates()
        val showPerformance = prefs.getBoolean("show_performance_overlay", false)
        togglePerformanceOverlay(showPerformance)
        // Choose the current music background before resuming any interrupted image load.
        musicStateUpdates.resume()
        backgroundLoads.resume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun isMusicBackgroundActive(): Boolean =
        wasMusicBackgroundApplied && backgroundManager.isMusicAlbumArtEnabled()

    fun isDynamicGradientActive(): Boolean {
        if (wasMusicBackgroundApplied) return false
        if (::backgroundSheetManager.isInitialized && backgroundSheetManager.isShowing) {
            val preview = backgroundSheetManager.previewBackgroundUri
            if (preview != null) {
                return preview == "__DEFAULT_GRADIENT__" ||
                        preview == com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID
            }
        }
        val activeId = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).getActiveProviderId()
        if (activeId != com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID) {
            return false
        }
        return !hasCustomImageBackground
    }

    private fun previewBlur(blur: Int) {
        val previewUri = if (::backgroundSheetManager.isInitialized) backgroundSheetManager.previewBackgroundUri else null
        if (previewUri != null) {
            if (previewUri == "__DEFAULT_GRADIENT__" ||
                previewUri == com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID ||
                previewUri == com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID) {
                return
            }
            if (previewUri.startsWith("external:")) {
                val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                val provider = bpm.getExternalProviders().firstOrNull { it.id == previewUri }
                val payload = provider?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(previewUri)
                when (payload) {
                    is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.ImageUri -> {
                        applyImageBackground(Uri.parse(payload.uriString), blur, skipAnimation = true)
                    }
                    is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.ImageBitmap -> {
                        applyBitmapBackground(payload.bitmap, blur, skipAnimation = true)
                    }
                    else -> return
                }
                return
            }
            applyImageBackground(Uri.parse(previewUri), blur, skipAnimation = true)
        } else if (wasMusicBackgroundApplied && currentAppliedArtworkSource != null) {
            when (val art = currentAppliedArtworkSource) {
                is Bitmap -> applyBitmapBackground(art, blur, skipAnimation = true)
                is String -> applyImageBackground(Uri.parse(art), blur, skipAnimation = true)
                is Uri -> applyImageBackground(art, blur, skipAnimation = true)
            }
        } else {
            val activeProviderId = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).getActiveProviderId()
            if (activeProviderId == com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID ||
                activeProviderId == com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID) {
                return
            } else if (activeProviderId.startsWith("external:")) {
                val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                val provider = bpm.getExternalProviders().firstOrNull { it.id == activeProviderId }
                val payload = provider?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(activeProviderId)
                when (payload) {
                    is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.ImageUri -> {
                        applyImageBackground(Uri.parse(payload.uriString), blur, skipAnimation = true)
                    }
                    is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.ImageBitmap -> {
                        applyBitmapBackground(payload.bitmap, blur, skipAnimation = true)
                    }
                    else -> return
                }
            } else {
                val savedUri = backgroundManager.getSavedBackgroundUri()
                if (savedUri != null && savedUri != "__DEFAULT_GRADIENT__") {
                    applyImageBackground(Uri.parse(savedUri), blur, skipAnimation = true)
                }
            }
        }
    }

    private fun previewGradient() {
        Logger.i("BackgroundFlow") { "previewGradient() called" }
        backgroundLoads.clear()
        cancelBackgroundLoads()
        isBackgroundLoading = false
        lastBackgroundSource = "gradient"
        lastBlurIntensity = null
        backgroundImageView.setImageDrawable(null)
        backgroundImageView.visibility = View.GONE
        dynamicBackgroundView.visibility = View.VISIBLE

        BackgroundProgressPlugin.startSession()
        updateBackgroundProgress(BackgroundProgressPlugin.Stage.GENERATING_GRADIENT)
        val (topColor, bottomColor) = gradientManager.getSkyGradientColors(Calendar.getInstance().time)
        dynamicBackgroundView.transitionToGradient(topColor, bottomColor, 300L, force = true)
        fontManager.clearDynamicColors()
        setCustomBackground(false)
        editModeBlurLayer.setImageDrawable(null)
        editModeBlurLayer.visibility = View.GONE
        gradientManager.startUpdates()
        updateBackgroundFilters(force = true)
        updateBackgroundProgress(BackgroundProgressPlugin.Stage.COMPLETED, getString(R.string.stage_gradient_applied))
    }

    private fun previewMusicAlbumArt(enabled: Boolean, blur: Int) {
        if (enabled && wasMusicBackgroundApplied && currentAppliedArtworkSource != null) {
            when (val art = currentAppliedArtworkSource) {
                is Bitmap -> applyBitmapBackground(art, blur, skipAnimation = true)
                is String -> applyImageBackground(Uri.parse(art), blur, skipAnimation = true)
                is Uri -> applyImageBackground(art, blur, skipAnimation = true)
            }
        } else {
            val previewUri = backgroundSheetManager.previewBackgroundUri ?: backgroundManager.getSavedBackgroundUri()
            if (previewUri == "__DEFAULT_GRADIENT__" || 
                previewUri == null || 
                previewUri == com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID) {
                previewGradient()
            } else if (previewUri == com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID) {
                val color = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).solidColorProvider.getColor(this)
                applySolidColorBackground(color, skipAnimation = true)
            } else if (previewUri.startsWith("external:")) {
                val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                val provider = bpm.getExternalProviders().firstOrNull { it.id == previewUri }
                val payload = provider?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(previewUri)
                applyBackgroundPayload(payload, skipAnimation = true)
            } else {
                applyImageBackground(Uri.parse(previewUri), blur, skipAnimation = true)
            }
        }
    }

    private fun restoreInitialBackgroundState() {
        val blurIntensity = backgroundManager.getBlurIntensity()
        Logger.i("BackgroundFlow") { "restoreInitialBackgroundState() called. blurIntensity=$blurIntensity" }
        if (backgroundManager.isMusicAlbumArtEnabled() && wasMusicBackgroundApplied && currentAppliedArtworkSource != null) {
            when (val art = currentAppliedArtworkSource) {
                is Bitmap -> applyBitmapBackground(art, blurIntensity, skipAnimation = true)
                is String -> applyImageBackground(Uri.parse(art), blurIntensity, skipAnimation = true)
                is Uri -> applyImageBackground(art, blurIntensity, skipAnimation = true)
            }
            setCustomBackground(true)
        } else {
            val activeProviderId = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).getActiveProviderId()
            Logger.i("BackgroundFlow") { "restoreInitialBackgroundState: activeProviderId=$activeProviderId" }
            if (activeProviderId == com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID) {
                val color = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).solidColorProvider.getColor(this)
                applySolidColorBackground(color, skipAnimation = true)
                setCustomBackground(true)
            } else if (activeProviderId.startsWith("external:")) {
                val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                val provider = bpm.getExternalProviders().firstOrNull { it.id == activeProviderId }
                val payload = provider?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(activeProviderId)
                applyBackgroundPayload(payload, skipAnimation = true)
            } else {
                val savedUri = backgroundManager.getSavedBackgroundUri()
                if (savedUri != null && savedUri != "__DEFAULT_GRADIENT__" && activeProviderId != com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID) {
                    applyImageBackground(Uri.parse(savedUri), blurIntensity, skipAnimation = true)
                    setCustomBackground(true)
                } else {
                    restoreGradientBackground()
                }
            }
        }
        restoreSavedWeatherState()
        updateBackgroundFilters()
    }

    private fun applyBackgroundSettingsFromSheet(previewUri: String?, blur: Int) {
        Logger.i("BackgroundFlow") { "applyBackgroundSettingsFromSheet: previewUri=$previewUri, blur=$blur" }
        val albumArtEnabled = backgroundManager.isMusicAlbumArtEnabled()
        if (albumArtEnabled && wasMusicBackgroundApplied) {
            when (previewUri) {
                "__DEFAULT_GRADIENT__",
                com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID -> {
                    backgroundManager.setSavedBackgroundUri(null)
                    com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                        .setActiveProviderId(com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID, notify = false)
                }
                com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID -> {
                    backgroundManager.setSavedBackgroundUri(null)
                    com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                        .setActiveProviderId(com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID, notify = false)
                }
                null -> { }
                else -> {
                    if (previewUri.startsWith("external:")) {
                        backgroundManager.setSavedBackgroundUri(null)
                        com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                            .setActiveProviderId(previewUri, notify = false)
                    } else {
                        backgroundManager.setSavedBackgroundUri(previewUri)
                        com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                            .setActiveProviderId(com.nxd1frnt.clockdesk2.background.provider.ImageBackgroundProvider.ID, notify = false)
                    }
                }
            }
            if (currentAppliedArtworkSource != null) {
                when (val art = currentAppliedArtworkSource) {
                    is Bitmap -> applyBitmapBackground(art, blur, skipAnimation = true)
                    is String -> applyImageBackground(Uri.parse(art), blur, skipAnimation = true)
                    is Uri -> applyImageBackground(art, blur, skipAnimation = true)
                }
            }
            restoreSavedWeatherState()
            updateBackgroundFilters()
            Toast.makeText(this, getString(R.string.settings_saved_music_active), Toast.LENGTH_LONG).show()
            return
        }

        if (!albumArtEnabled) {
            wasMusicBackgroundApplied = false
            currentAppliedArtworkSource = null
        }

        when (previewUri) {
            com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID -> {
                Logger.i("BackgroundFlow") { "applyBackgroundSettingsFromSheet -> SolidColorBackgroundProvider" }
                backgroundManager.setSavedBackgroundUri(null)
                com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                    .setActiveProviderId(com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID, notify = false)
                val color = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).solidColorProvider.getColor(this)
                applySolidColorBackground(color, skipAnimation = false)
            }
            "__DEFAULT_GRADIENT__",
            com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID -> {
                Logger.i("BackgroundFlow") { "applyBackgroundSettingsFromSheet -> DynamicGradientBackgroundProvider" }
                backgroundManager.setSavedBackgroundUri(null)
                com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                    .setActiveProviderId(com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID, notify = false)
                BackgroundProgressPlugin.startSession()
                updateBackgroundProgress(BackgroundProgressPlugin.Stage.GENERATING_GRADIENT)
                setCustomBackground(false)
                fontManager.clearDynamicColors()
                fontManager.applyNightShiftTransition(clockManager.getCurrentTime(), dayTimeGetter, true)
                backgroundLoads.clear()
                cancelBackgroundLoads()
                isBackgroundLoading = false
                lastBackgroundSource = "gradient"
                lastBlurIntensity = null
                dynamicBackgroundView.visibility = View.VISIBLE
                backgroundImageView.setImageDrawable(null)
                backgroundImageView.visibility = View.GONE
                backgroundManager.clearDim()
                val (topColor, bottomColor) = gradientManager.getSkyGradientColors(Calendar.getInstance().time)
                dynamicBackgroundView.transitionToGradient(topColor, bottomColor, 300L, force = true)
                gradientManager.startUpdates()
                updateBackgroundProgress(BackgroundProgressPlugin.Stage.COMPLETED, getString(R.string.stage_gradient_applied))
            }
            null -> {
                val activeProviderId = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).getActiveProviderId()
                Logger.i("BackgroundFlow") { "applyBackgroundSettingsFromSheet -> null previewUri, activeProviderId=$activeProviderId" }
                if (activeProviderId == com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID) {
                    val color = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this).solidColorProvider.getColor(this)
                    applySolidColorBackground(color, skipAnimation = true)
                } else if (activeProviderId.startsWith("external:")) {
                    val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                    val provider = bpm.getExternalProviders().firstOrNull { it.id == activeProviderId }
                    val payload = provider?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(activeProviderId)
                    applyBackgroundPayload(payload, skipAnimation = true)
                } else {
                    val existing = backgroundManager.getSavedBackgroundUri()
                    if (existing != null) {
                        try {
                            applyImageBackground(Uri.parse(existing), blur, skipAnimation = true)
                            setCustomBackground(true)
                        } catch (e: Exception) {
                            Logger.w("BackgroundFlow") { "Failed to re-apply existing background: ${e.message}" }
                        }
                    } else {
                        restoreGradientBackground()
                    }
                }
            }
            else -> {
                Logger.i("BackgroundFlow") { "applyBackgroundSettingsFromSheet -> external or image: $previewUri" }
                if (previewUri.startsWith("external:")) {
                    backgroundManager.setSavedBackgroundUri(null)
                    val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                    bpm.setActiveProviderId(previewUri, notify = false)
                    val provider = bpm.getExternalProviders().firstOrNull { it.id == previewUri }
                    val payload = provider?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(previewUri)
                    if (payload !is com.nxd1frnt.clockdesk2.background.provider.BackgroundPayload.Gradient) {
                        applyBackgroundPayload(payload, skipAnimation = false)
                    }
                    if (provider != null) {
                        bpm.requestDataFromPlugin(provider)
                    } else {
                        bpm.requestDataFromPluginId(previewUri)
                    }
                } else {
                    backgroundManager.setSavedBackgroundUri(previewUri)
                    com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
                        .setActiveProviderId(com.nxd1frnt.clockdesk2.background.provider.ImageBackgroundProvider.ID, notify = false)
                    try {
                        applyImageBackground(Uri.parse(previewUri), blur, skipAnimation = true)
                        setCustomBackground(true)
                    } catch (e: Exception) {
                        Logger.w("BackgroundFlow") { "Failed to apply new background: ${e.message}" }
                    }
                }
            }
        }
        restoreSavedWeatherState()
        updateBackgroundFilters()
    }

    private fun restoreGradientBackground() {
        Logger.i("BackgroundFlow") { "restoreGradientBackground() called" }
        backgroundLoads.clear()
        cancelBackgroundLoads()
        isBackgroundLoading = false
        lastBackgroundSource = "gradient"
        lastBlurIntensity = null
        currentAppliedArtworkSource = null
        wasMusicBackgroundApplied = false
        BackgroundProgressPlugin.startSession()
        updateBackgroundProgress(BackgroundProgressPlugin.Stage.GENERATING_GRADIENT)
        dynamicBackgroundView.visibility = View.VISIBLE
        backgroundImageView.setImageDrawable(null)
        backgroundImageView.visibility = View.GONE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                backgroundImageView.setRenderEffect(null)
            } catch (_: Throwable) {}
        }
        fontManager.clearDynamicColors()
        setCustomBackground(false)
        editModeBlurLayer.setImageDrawable(null)
        editModeBlurLayer.visibility = View.GONE
        val (topColor, bottomColor) = gradientManager.getSkyGradientColors(Calendar.getInstance().time)
        dynamicBackgroundView.transitionToGradient(topColor, bottomColor, 300L, force = true)
        gradientManager.startUpdates()
        updateBackgroundProgress(BackgroundProgressPlugin.Stage.COMPLETED, getString(R.string.stage_gradient_applied))
    }

    fun restoreUserBackground(savedUriStr: String?) {
        lastBackgroundSource = null
        currentAppliedArtworkSource = null
        wasMusicBackgroundApplied = false
        val bpm = com.nxd1frnt.clockdesk2.background.provider.BackgroundProviderManager.getInstance(this)
        val activeProviderId = bpm.getActiveProviderId()
        if (activeProviderId == com.nxd1frnt.clockdesk2.background.provider.SolidColorBackgroundProvider.ID) {
            val color = bpm.solidColorProvider.getColor(this)
            applySolidColorBackground(color, skipAnimation = false)
            return
        }
        if (activeProviderId.startsWith("external:")) {
            val provider = bpm.getExternalProviders().firstOrNull { it.id == activeProviderId }
            val payload = provider?.getCurrentPayload(this) ?: bpm.getSavedPayloadForId(activeProviderId)
            applyBackgroundPayload(payload, skipAnimation = false)
            if (provider != null) {
                bpm.requestDataFromPlugin(provider)
            } else {
                bpm.requestDataFromPluginId(activeProviderId)
            }
            return
        }
        val effectiveUri = savedUriStr ?: backgroundManager.getSavedBackgroundUri()
        if (effectiveUri != null && effectiveUri != "__DEFAULT_GRADIENT__" &&
            activeProviderId != com.nxd1frnt.clockdesk2.background.provider.DynamicGradientBackgroundProvider.ID) {
            try {
                val uri = Uri.parse(effectiveUri)
                val blur = backgroundManager.getBlurIntensity()
                applyImageBackground(uri, blur) {
                    setCustomBackground(true)
                }
                if (::backgroundSheetManager.isInitialized) {
                    backgroundSheetManager.updateCropButtonVisibility(true)
                }
            } catch (e: Exception) {
                Logger.e("MainActivity") { "Failed to restore user background: ${e.message}" }
                restoreGradientBackground()
            }
        } else {
            restoreGradientBackground()
        }
    }

    override fun onPause() {
        isResumed = false
        desktopWidgetManager.getControllers().forEach { it.onPause() }
        mainLayout.removeCallbacks(playEntranceAnimationRunnable)
        if (::entranceAnimationManager.isInitialized && entranceAnimationManager.hasStarted) {
            entranceAnimationManager.finish()
        }
        if (::desksPagerAdapter.isInitialized) {
            desksPagerAdapter.capturePreviews()
            desksPagerAdapter.setHostResumed(false)
        }
        super.onPause()
        musicStateUpdates.pause(replayLatest = true)
        backgroundLoads.pause(replayLatest = isBackgroundLoading)
        if (isBackgroundLoading) {
            lastBackgroundSource = null
            lastBlurIntensity = null
        }
        isBackgroundLoading = false
        cancelBackgroundLoads(clearEditBackground = false)
        pendingRestoreRunnable?.let { handler.removeCallbacks(it) }
        pendingRestoreRunnable = null
        pendingBackgroundRestoreRunnable?.let { handler.removeCallbacks(it) }
        pendingBackgroundRestoreRunnable = null
        updateSensorRegistration()
        if (::dynamicBackgroundView.isInitialized) {
            dynamicBackgroundView.animate().setListener(null).cancel()
            dynamicBackgroundView.alpha = 1f
            isScaleAnimating = false
            dynamicBackgroundView.onPause()
        }
        burnInProtectionManager.stop()
        smartPixelManager.stop()
        stopUpdates()
        stopPerformanceUpdates()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (::tutorialManager.isInitialized) {
            tutorialManager.checkAndUpdatePermissionState()
        }
        if (requestCode == permissionRequestCode) {
            loadCoordinatesAndFetchData()
            val hasCoarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasCoarse && !hasFine) {
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                prefs.edit().putBoolean("location_permission_rationale_shown", true).apply()
                showManualLocationOptionDialog()
            }
        }
    }

    override fun onPowerSaveModeChanged(isEnabled: Boolean) {
        isPowerSavingMode = isEnabled
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val policy = PowerSavePolicy.read(prefs, isEnabled)
        if (::dynamicBackgroundView.isInitialized) {
            dynamicBackgroundView.areAnimationsPaused = policy.pauseAnimations
            dynamicBackgroundView.maxFps = policy.maxFps
        }
        updateSensorRegistration()
        if (isResumed) {
            val brightness = policy.brightnessOverride
            if (brightness != null) applyBrightnessOverride(brightness)
            else if (prefs.getString("brightness_mode", "system") == "smart_night")
                applyNightDimMode(currentNightDimState)
            else applyBrightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            if (policy.enableSmartPixels) smartPixelManager.start() else smartPixelManager.stop()
        }
        if (::dynamicBackgroundView.isInitialized && dynamicBackgroundView.visibility == View.VISIBLE) {
            updateBackgroundFilters()
        }
    }

    private var performanceRunnable: Runnable? = null
    private val performanceUpdateInterval = 1000L // 1 second

    private fun togglePerformanceOverlay(enabled: Boolean) {
        if (!::performanceOverlay.isInitialized) return
        dynamicBackgroundView.performanceTracker.isEnabled = enabled
        if (enabled) {
            performanceOverlay.visibility = View.VISIBLE
            startPerformanceUpdates()
        } else {
            performanceOverlay.visibility = View.GONE
            stopPerformanceUpdates()
        }
    }

    private fun startPerformanceUpdates() {
        if (performanceRunnable != null) return
        performanceRunnable = object : Runnable {
            override fun run() {
                if (dynamicBackgroundView.performanceTracker.isEnabled) {
                    val metrics = dynamicBackgroundView.performanceTracker.updateMetrics()
                    performanceOverlay.updateMetrics(metrics)
                    handler.postDelayed(this, performanceUpdateInterval)
                }
            }
        }
        handler.post(performanceRunnable!!)
    }

    private fun stopPerformanceUpdates() {
        performanceRunnable?.let {
            handler.removeCallbacks(it)
            performanceRunnable = null
        }
    }

    private fun applyBrightnessOverride(brightnessValue: Float) {
        val layoutParams = window.attributes
        if (layoutParams.screenBrightness != brightnessValue) {
            layoutParams.screenBrightness = brightnessValue
            window.attributes = layoutParams
        }
    }

    private fun applyNightDimMode(state: NightDimState) {
        if (PowerSavePolicy.read(
                getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE), isPowerSavingMode
            ).brightnessOverride != null) return

        if (dayTimeGetter.isDay()) {
            applyBrightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
            return
        }

        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        if (state == NightDimState.DIMMED) {
            val minBrightPct = prefs.getInt("smart_night_min_brightness", 5) / 100f
            applyBrightnessOverride(minBrightPct.coerceIn(0.01f, 1.0f))
        } else {
            applyBrightnessOverride(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }

    private fun updateSensorRegistration() {
        if (!::sensorManager.isInitialized) return
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val brightnessMode = prefs.getString("brightness_mode", "system") ?: "system"
        val policy = PowerSavePolicy.read(prefs, isPowerSavingMode)
        val shouldRegister = isResumed && !policy.disableLightSensor &&
            brightnessMode == "smart_night" && !dayTimeGetter.isDay()

        if (shouldRegister) {
            lightSensor?.let {
                sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } else {
            sensorManager.unregisterListener(sensorEventListener)
            cancelPendingDimTransition()
        }
    }

    private fun cancelPendingDimTransition() {
        dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
        pendingNightDimState = null
    }

    private fun reEvaluateSmartNightDimming() {
        val lux = lastSeenLux ?: return
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val threshold = prefs.getInt("smart_night_lux_threshold", 5)
        val hysteresis = 3

        val candidateState = when (currentNightDimState) {
            NightDimState.NORMAL -> {
                if (lux < threshold) NightDimState.DIMMED else NightDimState.NORMAL
            }
            NightDimState.DIMMED -> {
                if (lux >= threshold + hysteresis) NightDimState.NORMAL else NightDimState.DIMMED
            }
        }

        if (candidateState != currentNightDimState) {
            if (candidateState != pendingNightDimState) {
                dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
                pendingNightDimState = candidateState
                dimDebounceHandler.postDelayed(dimDebounceRunnable, 2500L)
            }
        } else {
            if (pendingNightDimState != null) {
                dimDebounceHandler.removeCallbacks(dimDebounceRunnable)
                pendingNightDimState = null
            }
        }
    }

    fun switchToDeskType(type: com.nxd1frnt.clockdesk2.desks.DeskType): Boolean {
        if (!::deskManager.isInitialized || !::desksViewPager.isInitialized) return false
        val desks = deskManager.getDesks()
        var targetIndex = desks.indexOfFirst { it.type == type }
        if (targetIndex < 0) {
            val newDesk = deskManager.addDesk(type)
            targetIndex = deskManager.getDesks().indexOfFirst { it.id == newDesk.id }
        }
        if (targetIndex >= 0) {
            desksViewPager.setCurrentItem(targetIndex, true)
            return true
        }
        return false
    }

    companion object {
        private var instanceRef: java.lang.ref.WeakReference<MainActivity>? = null

        var instance: MainActivity?
            get() = instanceRef?.get()
            set(value) {
                instanceRef = if (value != null) java.lang.ref.WeakReference(value) else null
            }

        fun openDeskType(type: com.nxd1frnt.clockdesk2.desks.DeskType): Boolean {
            return instance?.switchToDeskType(type) ?: false
        }
    }
}
