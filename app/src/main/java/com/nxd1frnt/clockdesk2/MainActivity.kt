package com.nxd1frnt.clockdesk2

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.*

class MainActivity : AppCompatActivity() {
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var settingsButton: Button
    private lateinit var debugButton: Button
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
    private val editModeTimeoutRunnable = object : Runnable {
        override fun run() {
            if (isEditMode && !isDemoMode) {
                exitEditMode()
            }
        }
    }

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
        settingsButton = findViewById(R.id.settings_button)
        debugButton = findViewById(R.id.demo_button)
        mainLayout = findViewById(R.id.main_layout)
        bottomSheet = findViewById(R.id.bottom_sheet)
        bottomSheetBehavior = from(bottomSheet).apply {
            state = STATE_HIDDEN
        }

        settingsButton.alpha = 0f
        settingsButton.visibility = View.VISIBLE
        debugButton.alpha = 0f
        debugButton.visibility = View.VISIBLE

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
            { time, sunrise, sunset ->
                if (isDemoMode) {
                    // existing debug callback
                }
            },
            { simulatedTime -> // Add this callback
                gradientManager.updateSimulatedTime(simulatedTime)
            }
        )

        fontManager.loadFont()

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



        // Debug mode toggle
        debugButton.setOnClickListener {
            isDemoMode = !isDemoMode
            clockManager.toggleDebugMode(isDemoMode)
            gradientManager.toggleDebugMode(isDemoMode)
//            Toast.makeText(
//                this,
//                if (isDebugMode) R.string.debug_mode_enabled else R.string.debug_mode_disabled,
//                Toast.LENGTH_SHORT
//            ).show()
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


    private fun setupBottomSheet() {
        val fontRecyclerView = bottomSheet.findViewById<RecyclerView>(R.id.font_recycler_view)
        fontRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun showCustomizationBottomSheet(isTimeText: Boolean) {
        val title = bottomSheet.findViewById<TextView>(R.id.customization_title)
        val sizeSeekBar = bottomSheet.findViewById<SeekBar>(R.id.size_seekbar)
        val sizeValue = bottomSheet.findViewById<TextView>(R.id.size_value)
        val transparencySeekBar = bottomSheet.findViewById<SeekBar>(R.id.transparency_seekbar)
        val transparencyPreview = bottomSheet.findViewById<View>(R.id.transparency_preview)
        val fontRecyclerView = bottomSheet.findViewById<RecyclerView>(R.id.font_recycler_view)
        val applyButton = bottomSheet.findViewById<TextView>(R.id.apply_button)
        val cancelButton = bottomSheet.findViewById<TextView>(R.id.cancel_button)
        val nightShiftSwitch = bottomSheet.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.night_shift_switch)        // Set title
        title.text = if (isTimeText) getString(R.string.customize_time) else getString(R.string.customize_date)
        Log.d("MainActivity", "Showing bottom sheet for ${if (isTimeText) "timeText" else "dateText"}")
        nightShiftSwitch.isChecked = fontManager.isNightShiftEnabled()
        // Highlight focused text
        focusedTextView = if (isTimeText) timeText else dateText
        highlightFocusedText(true)

        // Font carousel
        val fontAdapter = FontAdapter(fontManager.getFonts()) { fontId ->
            if (isTimeText) {
                fontManager.setTimeFont(fontId)
                Log.d("MainActivity", "Font selected for timeText: $fontId")
            } else {
                fontManager.setDateFont(fontId)
                Log.d("MainActivity", "Font selected for dateText: $fontId")
            }
        }
        fontRecyclerView.adapter = fontAdapter

        // Font size slider
        val currentSize = if (isTimeText) fontManager.getTimeSize() else fontManager.getDateSize()
        sizeSeekBar.max = 155
        sizeSeekBar.progress = (currentSize - 20).toInt()
        sizeValue.text = "${currentSize}sp"
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 20
                sizeValue.text = "${size}sp"
                if (isTimeText) {
                    fontManager.setTimeSize(size.toFloat())
                    Log.d("MainActivity", "Size set for timeText: $size")
                } else {
                    fontManager.setDateSize(size.toFloat())
                    Log.d("MainActivity", "Size set for dateText: $size")
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Color slider
        val currentAlpha = if (isTimeText) fontManager.getTimeAlpha() else fontManager.getDateAlpha()
        transparencySeekBar.max = 100
        transparencySeekBar.progress = (currentAlpha * 100).toInt()
        transparencyPreview.alpha = currentAlpha
        transparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alpha = progress / 100f
                transparencyPreview.alpha = alpha
                if (isTimeText) {
                    fontManager.setTimeAlpha(alpha)
                    Log.d("MainActivity", "Alpha set for timeText: $alpha")
                } else {
                    fontManager.setDateAlpha(alpha)
                    Log.d("MainActivity", "Alpha set for dateText: $alpha")
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

        // Buttons
        applyButton.setOnClickListener {
            fontManager.saveSettings()
            hideBottomSheet()
            Log.d("MainActivity", "Applied settings for ${if (isTimeText) "timeText" else "dateText"}")
        }
        cancelButton.setOnClickListener {
            fontManager.loadFont()
            hideBottomSheet()
            Log.d("MainActivity", "Cancelled settings for ${if (isTimeText) "timeText" else "dateText"}")
        }

        // Show bottom sheet
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun hideBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
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
        gradientManager.startUpdates()
    }

    private fun stopUpdates() {
        clockManager.stopUpdates()
        gradientManager.stopUpdates()
        handler.removeCallbacks(editModeTimeoutRunnable)
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        if (isEditMode) {
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
        timeText.background = null
        dateText.background = null
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