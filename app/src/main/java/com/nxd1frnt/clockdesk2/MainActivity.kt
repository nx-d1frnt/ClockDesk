package com.nxd1frnt.clockdesk2

import android.app.Activity
import android.content.Intent
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var backgroundImageView: ImageView
    private lateinit var settingsButton: Button
    private lateinit var debugButton: Button
    private lateinit var backgroundButton: Button
    private lateinit var mainLayout: ConstraintLayout
    private lateinit var bottomSheet: LinearLayout
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var clockManager: ClockManager
    private lateinit var gradientManager: GradientManager
    private lateinit var fontManager: FontManager
    private lateinit var locationManager: LocationManager
    private lateinit var sunTimeApi: SunTimeApi
    private var isEditMode = false
    private var isDemoMode = false
    private var isNightShiftEnabled = false
    private var focusedTextView: TextView? = null
    private val editModeTimeout = 10000L // 10 seconds
    private val animationDuration = 300L // 300ms
    private val handler = Handler(Looper.getMainLooper())
    private val permissionRequestCode = 100
    private val PICK_BG_REQUEST = 300
    private val editModeTimeoutRunnable = object : Runnable {
        override fun run() {
            if (isEditMode && !isDemoMode) {
                exitEditMode()
            }
        }
    }
    private var isBottomSheetInitializing = false
    private var hasCustomImageBackground = false
    private lateinit var backgroundBottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private var previewBackgroundUri: String? = null
    private var backgroundsAdapter: BackgroundsAdapter? = null
    private lateinit var backgroundProgressOverlay: View
    private lateinit var backgroundProgressText: TextView

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
        backgroundLayout = findViewById(R.id.background_layout)
        backgroundImageView = findViewById(R.id.background_image_view)
        backgroundProgressOverlay = findViewById(R.id.background_progress_overlay)
        backgroundProgressText = findViewById(R.id.background_progress_text)
        settingsButton = findViewById(R.id.settings_button)
        debugButton = findViewById(R.id.demo_button)
        backgroundButton = findViewById(R.id.background_button)
        mainLayout = findViewById(R.id.main_layout)
        bottomSheet = findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = from(bottomSheet).apply {
            state = STATE_HIDDEN
        }

        // Background bottom sheet setup
        val backgroundBottomSheet = findViewById<LinearLayout>(R.id.background_bottom_sheet)
        backgroundBottomSheetBehavior = from(backgroundBottomSheet).apply {
            state = STATE_HIDDEN
        }

        // Initially hide edit-only controls (alpha 0)
        settingsButton.alpha = 0f
        settingsButton.visibility = View.GONE
        debugButton.alpha = 0f
        debugButton.visibility = View.GONE
        backgroundButton.alpha = 0f
        backgroundButton.visibility = View.GONE

        fontManager = FontManager(this, timeText, dateText)
        locationManager = LocationManager(this, permissionRequestCode)
        sunTimeApi = SunTimeApi(this, locationManager)
        gradientManager = GradientManager(backgroundLayout, sunTimeApi, locationManager, handler)
        clockManager = ClockManager(
            timeText,
            dateText,
            handler,
            fontManager,
            sunTimeApi,
            locationManager,
            { _, _, _ ->
                if (isDemoMode) {
                    Log.d("MainActivity", "debug sun times callback (demo mode)")
                }
            },
            { simulatedTime -> // Add this callback
                gradientManager.updateSimulatedTime(simulatedTime)
            }
        )

        fontManager.loadFont()

        // Load any saved custom background image from prefs (if set)
        loadSavedBackground()

        // Long tap for edit mode
        mainLayout.setOnLongClickListener {
            toggleEditMode()
            true
        }

        setupBottomSheet()
        timeText.setOnClickListener {
            if (isEditMode) {
                showCustomizationBottomSheet(true)
                if (!isDemoMode) {
                    handler.removeCallbacks(editModeTimeoutRunnable)
                    handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
                }
            }
        }
        dateText.setOnClickListener {
            if (isEditMode) {
                showCustomizationBottomSheet(false)
                if (!isDemoMode) {
                    handler.removeCallbacks(editModeTimeoutRunnable)
                    handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
                }
            }
        }

        // Background button behaves like settings: exit edit mode and open backgrounds sheet
        backgroundButton.setOnClickListener {
            showBackgroundBottomSheet()
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
                val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
                val uriStr = prefs.getString("background_uri", null)
                uriStr?.let {
                    try {
                        val uri = Uri.parse(it)
                        applyImageBackground(uri, prefs.getInt("background_blur_intensity", 0))
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

        // Load coordinates and sun times
        locationManager.loadCoordinates { lat, lon ->
            sunTimeApi.fetchSunTimes(lat, lon) {
                gradientManager.updateGradient()
            }
        }

        // Start updates
        startUpdates()
    }

    // Handle add-image result from background bottom sheet
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_BG_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                // Persist read permission for the chosen URI so the app can access it later.
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // ignore if cannot persist
            }
            val uriStr = uri.toString()
            val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
            val existing = prefs.getStringSet("background_uris", emptySet())?.toMutableSet() ?: mutableSetOf()
            existing.add(uriStr)
            prefs.edit().putStringSet("background_uris", existing).apply()
            // update adapter list (include default and add)
            val items = mutableListOf<String>()
            items.add("__DEFAULT_GRADIENT__")
            items.addAll(existing)
            items.add("__ADD__")
            backgroundsAdapter?.updateItems(items)
            // preview and set preview selection
            previewBackgroundUri = uriStr
            applyImageBackground(uri, prefs.getInt("background_blur_intensity", 0))
        }
    }

    // Load saved background URI from preferences and apply it if present
    private fun loadSavedBackground() {
        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        val uriStr = prefs.getString("background_uri", null)
        val blurIntensity = prefs.getInt("background_blur_intensity", 0)
        if (uriStr != null) {
            try {
                val uri = Uri.parse(uriStr)
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {

                }
                applyImageBackground(uri, blurIntensity)
                hasCustomImageBackground = true
                Log.d("MainActivity", "Loaded custom background: $uriStr (blurIntensity=$blurIntensity)")
            } catch (e: Exception) {
                prefs.edit().remove("background_uri").apply()
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
    private fun applyImageBackground(uri: Uri, blurIntensity: Int = 0) {
        try {
            // Stop gradient updates immediately to conserve CPU/battery when previewing/applying an image
            gradientManager.stopUpdates()

            // Show a progress overlay while loading/processing (especially when blurIntensity>0)
            val loadingMessage = if (blurIntensity > 0) getString(R.string.blur_applying_message) else getString(R.string.loading_background_message)
            setBackgroundProgressVisible(true, loadingMessage)

            // On modern Android, prefer RenderEffect for high-quality blur on the ImageView layer
            val usePlatformBlur = blurIntensity > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S

            val req = if (usePlatformBlur) {
                // don't pre-blur bitmaps; let the view's RenderEffect handle it
                RequestOptions().transform(CenterCrop())
            } else if (blurIntensity > 0) {
                // older devices: apply our BlurTransformation on the bitmap
                RequestOptions().transform(CenterCrop(), BlurTransformation(blurIntensity))
            } else {
                RequestOptions().transform(CenterCrop())
            }

            Glide.with(this)
                .load(uri)
                .apply(req)
                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(resource: android.graphics.drawable.Drawable, transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?) {
                        // Hide progress overlay (work is done)
                        setBackgroundProgressVisible(false)
                        backgroundImageView.setImageDrawable(resource)
                        // Apply platform blur if available
                        if (usePlatformBlur) {
                            try {
                                val radiusPx = blurIntensity.coerceAtLeast(1).toFloat()
                                val renderEffect = android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP)
                                backgroundImageView.setRenderEffect(renderEffect)
                            } catch (e: Throwable) {
                                // if RenderEffect fails for any reason, we already set a transformed drawable via Glide fallback
                            }
                        } else {
                            // Ensure any previous RenderEffect is cleared on modern devices when blur==0
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                try { backgroundImageView.setRenderEffect(null) } catch (_: Throwable) {}
                            }
                        }

                        // Make sure ImageView is visible over the gradient
                        backgroundImageView.visibility = View.VISIBLE
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        // Hide any overlay when load cleared
                        setBackgroundProgressVisible(false)
                    }

                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                        super.onLoadFailed(errorDrawable)
                        setBackgroundProgressVisible(false)
                    }
                })
        } catch (e: Exception) {
            setBackgroundProgressVisible(false)
            Log.w("MainActivity", "applyImageBackground(glide) failed: ${e.message}")
            Toast.makeText(this, getString(R.string.failed_to_load_background), Toast.LENGTH_SHORT).show()
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
        val blurSwitch = sheet.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.background_blur_switch)
        val blurSeek = sheet.findViewById<SeekBar>(R.id.blur_intensity_seekbar)
        val clearBtn = sheet.findViewById<Button>(R.id.clear_background_button_bs)
        val applyBtn = sheet.findViewById<Button>(R.id.apply_background_button)

        val prefs = getSharedPreferences("ClockDeskPrefs", MODE_PRIVATE)
        // Build items: default gradient, saved URIs, add-item
        val saved = prefs.getStringSet("background_uris", emptySet())?.toMutableSet() ?: mutableSetOf()
        val items = mutableListOf<String>().apply {
            add("__DEFAULT_GRADIENT__")
            addAll(saved)
            add("__ADD__")
        }

        if (backgroundsAdapter == null) {
            backgroundsAdapter = BackgroundsAdapter(this, items) { id ->
                when (id) {
                    "__ADD__" -> {
                        // Launch picker
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                        startActivityForResult(intent, PICK_BG_REQUEST)
                    }
                    "__DEFAULT_GRADIENT__" -> {
                        // preview default gradient: hide imageView, resume gradient updates, clear any renderEffect
                        previewBackgroundUri = "__DEFAULT_GRADIENT__"
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                try { backgroundImageView.setRenderEffect(null) } catch (_: Throwable) {}
                            }
                        } catch (_: Exception) {}
                        backgroundImageView.setImageDrawable(null)
                        backgroundImageView.visibility = View.GONE
                        gradientManager.startUpdates()
                    }
                    else -> {
                        // preview chosen saved image -> stop gradient updates right away and show progress while applying
                        try {
                            val uri = Uri.parse(id)
                            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            val intensity = prefs.getInt("background_blur_intensity", 0)
                            // Stop gradient updates immediately for preview
                            gradientManager.stopUpdates()
                            applyImageBackground(uri, intensity)
                            previewBackgroundUri = id
                        } catch (e: Exception) {
                            Log.w("MainActivity", "preview background failed: ${e.message}")
                        }
                    }
                 }
             }
            recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            recycler.adapter = backgroundsAdapter
        } else {
            // Update existing adapter items
            backgroundsAdapter?.updateItems(items)
        }
        // ensure the first two items (default/add) are visible
        recycler.scrollToPosition(0)

        // initialize UI states (default no blur)
        blurSeek.progress = prefs.getInt("background_blur_intensity", 0)
        blurSwitch.isChecked = blurSeek.progress > 0

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

        clearBtn.setOnClickListener {
            // Clear persisted background selection
            prefs.edit().remove("background_uri").apply()
            previewBackgroundUri = null
            hasCustomImageBackground = false
            // hide image view and show gradient
            backgroundImageView.setImageDrawable(null)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                try { backgroundImageView.setRenderEffect(null) } catch (_: Throwable) {}
            }
            backgroundImageView.visibility = View.GONE
            gradientManager.startUpdates()
            backgroundBottomSheetBehavior.state = STATE_HIDDEN
        }

        applyBtn.setOnClickListener {
            // Persist the currently previewed background (if any)
            val intensity = blurSeek.progress
            prefs.edit().putInt("background_blur_intensity", intensity).apply()

            when (previewBackgroundUri) {
                "__DEFAULT_GRADIENT__" -> {
                    // user chose the default gradient: remove any persisted custom uri
                    prefs.edit().remove("background_uri").apply()
                    hasCustomImageBackground = false
                    backgroundImageView.visibility = View.GONE
                    gradientManager.startUpdates()
                }
                null -> {
                    // nothing new previewed; keep existing selection
                    val existing = prefs.getString("background_uri", null)
                    existing?.let {
                        try {
                            applyImageBackground(Uri.parse(it), intensity)
                            hasCustomImageBackground = true
                        } catch (e: Exception) {
                            Log.w("MainActivity", "apply on applyBtn failed: ${e.message}")
                        }
                    }
                }
                else -> {
                    // persist selected image uri and apply it
                    prefs.edit().putString("background_uri", previewBackgroundUri).apply()
                    try {
                        applyImageBackground(Uri.parse(previewBackgroundUri), intensity)
                        hasCustomImageBackground = true
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
        fontRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        // Let the parent NestedScrollView handle scrolling gestures
        fontRecyclerView.isNestedScrollingEnabled = false
        // Ensure bottom sheet behavior is configured for nested scrolling
        bottomSheetBehavior.peekHeight = 0
        bottomSheetBehavior.isHideable = true
        // Allow dragging the bottom sheet (ensures NestedScrollView can pass drag gestures)
        bottomSheetBehavior.isDraggable = true
    }

    private fun showCustomizationBottomSheet(isTimeText: Boolean) {
        isBottomSheetInitializing = true
        val targetIsTime = isTimeText

        // Mark current target on the bottom sheet so listeners can always read the active target
        bottomSheet.setTag(R.id.customization_title, if (targetIsTime) "time" else "date")

        val title = bottomSheet.findViewById<TextView>(R.id.customization_title)
        val sizeSeekBar = bottomSheet.findViewById<SeekBar>(R.id.size_seekbar)
        val sizeValue = bottomSheet.findViewById<TextView>(R.id.size_value)
        val transparencySeekBar = bottomSheet.findViewById<SeekBar>(R.id.transparency_seekbar)
        val transparencyPreview = bottomSheet.findViewById<View>(R.id.transparency_preview)
        val fontRecyclerView = bottomSheet.findViewById<RecyclerView>(R.id.font_recycler_view)
        val applyButton = bottomSheet.findViewById<Button>(R.id.apply_button)
        val cancelButton = bottomSheet.findViewById<Button>(R.id.cancel_button)
        val alignmentGroup = bottomSheet.findViewById<RadioGroup>(R.id.alignment_radio_group)
        val nightShiftSwitch = bottomSheet.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.night_shift_switch)
        val timeFormatGroup = bottomSheet.findViewById<RadioGroup>(R.id.time_format_radio_group)
        val dateFormatGroup = bottomSheet.findViewById<RadioGroup>(R.id.date_format_radio_group)

        // Set title
        title.text = if (targetIsTime) getString(R.string.customize_time) else getString(R.string.customize_date)
        Log.d("MainActivity", "Showing bottom sheet for ${if (targetIsTime) "timeText" else "dateText"} (tag=${bottomSheet.getTag(R.id.customization_title)})")
        nightShiftSwitch.isChecked = fontManager.isNightShiftEnabled()
        // Highlight focused text
        focusedTextView = if (targetIsTime) timeText else dateText
        highlightFocusedText(true)

        // Font carousel
        val fontAdapter = FontAdapter(fontManager.getFonts()) { fontId ->
            val active = bottomSheet.getTag(R.id.customization_title) as? String
            val isActiveTime = (active == "time")
            if (isActiveTime) {
                fontManager.setTimeFont(fontId)
                Log.d("MainActivity", "Font selected for timeText: $fontId")
            } else {
                fontManager.setDateFont(fontId)
                Log.d("MainActivity", "Font selected for dateText: $fontId")
            }
        }
        fontRecyclerView.adapter = fontAdapter

        // --- Ensure old listeners won't react to our programmatic changes ---
        sizeSeekBar.setOnSeekBarChangeListener(null)
        transparencySeekBar.setOnSeekBarChangeListener(null)
        alignmentGroup.setOnCheckedChangeListener(null)
        timeFormatGroup.setOnCheckedChangeListener(null)
        dateFormatGroup.setOnCheckedChangeListener(null)

        // Font size slider - initialize programmatically
        val currentSize = if (targetIsTime) fontManager.getTimeSize() else fontManager.getDateSize()
        sizeSeekBar.max = 155
        sizeSeekBar.progress = (currentSize - 20).toInt()
        sizeValue.text = "${currentSize}sp"

        // Attach size listener (read active target from bottomSheet tag)
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (isBottomSheetInitializing) return
                val size = progress + 20
                sizeValue.text = "${size}sp"
                val active = bottomSheet.getTag(R.id.customization_title) as? String
                val isActiveTime = (active == "time")
                if (isActiveTime) {
                    fontManager.setTimeSize(size.toFloat())
                    Log.d("MainActivity", "Size set for timeText: $size (active=$active)")
                } else {
                    fontManager.setDateSize(size.toFloat())
                    Log.d("MainActivity", "Size set for dateText: $size (active=$active)")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Color slider
        val currentAlpha = if (targetIsTime) fontManager.getTimeAlpha() else fontManager.getDateAlpha()
        transparencySeekBar.max = 100
        transparencySeekBar.progress = (currentAlpha * 100).toInt()
        transparencyPreview.alpha = currentAlpha
        transparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                if (isBottomSheetInitializing) return
                val alpha = progress / 100f
                transparencyPreview.alpha = alpha
                val active = bottomSheet.getTag(R.id.customization_title) as? String
                val isActiveTime = (active == "time")
                if (isActiveTime) {
                    fontManager.setTimeAlpha(alpha)
                    Log.d("MainActivity", "Alpha set for timeText: $alpha (active=$active)")
                } else {
                    fontManager.setDateAlpha(alpha)
                    Log.d("MainActivity", "Alpha set for dateText: $alpha (active=$active)")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        nightShiftSwitch.isChecked = fontManager.isNightShiftEnabled()
        nightShiftSwitch.setOnCheckedChangeListener { _, isChecked ->
            isNightShiftEnabled = isChecked
            fontManager.setNightShiftEnabled(isChecked)
        }

        // Initialize alignment radio buttons to current alignment (avoid triggering listener by clearing it first)
        val currentAlignment = if (targetIsTime) fontManager.getTimeAlignment() else fontManager.getDateAlignment()
        when (currentAlignment) {
            View.TEXT_ALIGNMENT_VIEW_START, View.TEXT_ALIGNMENT_TEXT_START -> alignmentGroup.check(R.id.left_radio_button)
            View.TEXT_ALIGNMENT_CENTER -> alignmentGroup.check(R.id.center_radio_button)
            View.TEXT_ALIGNMENT_VIEW_END, View.TEXT_ALIGNMENT_TEXT_END -> alignmentGroup.check(R.id.right_radio_button)
            else -> alignmentGroup.check(R.id.left_radio_button)
        }

        // Initialize time format radio buttons from FontManager
        val currentTimePattern = fontManager.getTimeFormatPattern()
        if (currentTimePattern.contains("H")) {
            timeFormatGroup.check(R.id.time_24_radio)
        } else {
            timeFormatGroup.check(R.id.time_12_radio)
        }

        // Initialize date format radio buttons from FontManager
        val currentDatePattern = fontManager.getDateFormatPattern()
        when (currentDatePattern) {
            getString(R.string.date_format_short) -> dateFormatGroup.check(R.id.date_format_1)
            getString(R.string.date_format_medium) -> dateFormatGroup.check(R.id.date_format_2)
            getString(R.string.date_format_long) -> dateFormatGroup.check(R.id.date_format_3)
            else -> {
                // try matching known patterns
                when (currentDatePattern) {
                    "MMM dd" -> dateFormatGroup.check(R.id.date_format_1)
                    "EEE, MMM dd" -> dateFormatGroup.check(R.id.date_format_2)
                    "EEEE, MMMM dd, yyyy" -> dateFormatGroup.check(R.id.date_format_3)
                    else -> dateFormatGroup.check(R.id.date_format_2)
                }
            }
        }

        // Now attach listener so only user interactions trigger alignment updates
        alignmentGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isBottomSheetInitializing) return@setOnCheckedChangeListener
            val checkedView = alignmentGroup.findViewById<View>(checkedId)
            if (checkedView == null || !checkedView.isPressed) return@setOnCheckedChangeListener
            val alignment = when (checkedId) {
                R.id.left_radio_button -> View.TEXT_ALIGNMENT_VIEW_START
                R.id.center_radio_button -> View.TEXT_ALIGNMENT_CENTER
                R.id.right_radio_button -> View.TEXT_ALIGNMENT_VIEW_END
                else -> View.TEXT_ALIGNMENT_VIEW_START
            }
            val active = bottomSheet.getTag(R.id.customization_title) as? String
            val isActiveTime = (active == "time")
            if (isActiveTime) {
                fontManager.setTimeAlignment(alignment)
                Log.d("MainActivity", "Alignment set for timeText: $alignment (active=$active)")
            } else {
                fontManager.setDateAlignment(alignment)
                Log.d("MainActivity", "Alignment set for dateText: $alignment (active=$active)")
            }
        }

        // Time format listener
        timeFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isBottomSheetInitializing) return@setOnCheckedChangeListener
            val pattern = when (checkedId) {
                R.id.time_24_radio -> "HH:mm"
                R.id.time_12_radio -> "hh:mm a"
                else -> "HH:mm"
            }
            fontManager.setTimeFormatPattern(pattern)
            // Update displayed time immediately
            try {
                val now = clockManager.getCurrentTime()
                timeText.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to format time immediately: ${e.message}")
            }
        }

        // Date format listener
        dateFormatGroup.setOnCheckedChangeListener { _, checkedId ->
            if (isBottomSheetInitializing) return@setOnCheckedChangeListener
            val pattern = when (checkedId) {
                R.id.date_format_1 -> "MMM dd"
                R.id.date_format_2 -> "EEE, MMM dd"
                R.id.date_format_3 -> "EEEE, MMMM dd, yyyy"
                else -> "EEE, MMM dd"
            }
            fontManager.setDateFormatPattern(pattern)
            // Update displayed date immediately
            try {
                val now = clockManager.getCurrentTime()
                dateText.text = SimpleDateFormat(pattern, Locale.getDefault()).format(now)
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed to format date immediately: ${e.message}")
            }
        }

        // Now attach other listeners (size/color) already set above

        // Buttons
        applyButton.setOnClickListener {
            fontManager.saveSettings()
            hideBottomSheet()
            Log.d("MainActivity", "Applied settings for ${if (targetIsTime) "timeText" else "dateText"}")
        }
        cancelButton.setOnClickListener {
            fontManager.loadFont()
            hideBottomSheet()
            Log.d("MainActivity", "Cancelled settings for ${if (targetIsTime) "timeText" else "dateText"}")
        }

        // Show bottom sheet
        isBottomSheetInitializing = false
        bottomSheetBehavior.state = STATE_EXPANDED
    }

    private fun hideBottomSheet() {
        bottomSheetBehavior.state = STATE_HIDDEN
        highlightFocusedText(false)
        focusedTextView = null
    }

    private fun highlightFocusedText(isHighlighted: Boolean) {
        focusedTextView?.let { textView ->
            if (isHighlighted) {
                textView.animate()
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(animationDuration)
                    .start()
                textView.setBackgroundResource(R.drawable.editable_border)
            } else {
                textView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(animationDuration)
                    .start()
                if (!isEditMode) textView.background = null
            }
        }
    }


//    private fun showCustomizationDialog(isTimeText: Boolean) {
//        val dialog = CustomizationDialog(fontManager, isTimeText) {
//            // No additional action needed; fontManager applies changes
//        }
//        dialog.show(supportFragmentManager, "CustomizationDialog")
//    }

    private fun startUpdates() {
        clockManager.startUpdates()
        // Only start gradient updates if there is no custom image background
        if (!hasCustomImageBackground) {
            gradientManager.startUpdates()
        }
    }

    private fun stopUpdates() {
        clockManager.stopUpdates()
        gradientManager.stopUpdates()
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        if (isEditMode) {
            settingsButton.visibility = View.VISIBLE
            debugButton.visibility = View.VISIBLE
            backgroundButton.visibility = View.VISIBLE
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
            backgroundButton.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            timeText.setBackgroundResource(R.drawable.editable_border)
            dateText.setBackgroundResource(R.drawable.editable_border)
            //Toast.makeText(this, R.string.edit_mode_enabled, Toast.LENGTH_SHORT).show()
            if (!isDemoMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        } else {
            exitEditMode()
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
        backgroundButton.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        timeText.background = null
        dateText.background = null
        settingsButton.visibility = View.GONE
        debugButton.visibility = View.GONE
        backgroundButton.visibility = View.GONE
        handler.removeCallbacks(editModeTimeoutRunnable)
        //Toast.makeText(this, R.string.edit_mode_disabled, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        locationManager.loadCoordinates { lat, lon ->
            sunTimeApi.fetchSunTimes(lat, lon) {
                gradientManager.updateGradient()
                if (isNightShiftEnabled) {
                    fontManager.applyNightShiftTransition(
                        clockManager.getCurrentTime(),
                        sunTimeApi,
                        isNightShiftEnabled
                    )
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
        startUpdates()
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
            sunTimeApi.fetchSunTimes(lat, lon) {
                gradientManager.updateGradient()
            }
        }
    }
}
