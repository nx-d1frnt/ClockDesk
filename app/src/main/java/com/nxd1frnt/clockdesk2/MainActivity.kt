package com.nxd1frnt.clockdesk2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject
import java.io.BufferedReader
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var settingsButton: Button
    private lateinit var debugButton : Button
    private lateinit var mainLayout: ConstraintLayout
    private val handler = Handler(Looper.getMainLooper())
    private var sunriseTime: Date? = null
    private var sunsetTime: Date? = null
    private var dawnTime: Date? = null
    private var duskTime: Date? = null
    private var solarNoonTime: Date? = null
    private var latitude: Double = 40.7128 // Fallback: New York
    private var longitude: Double = -74.0060
    private val permissionRequestCode = 1
    private var isEditMode = false
    private var isDebugMode = false
    private val editModeTimeout = 10000L // 10 seconds
    private val animationDuration = 400L
    private val debugCycleInterval = 300L
    private val fonts = listOf(R.font.anton_regular, R.font.inflatevf, R.font.kanit_regular, R.font.sigmar_regular, R.font.monomakh_regular, R.font.orbitron_regular, R.font.dancingscript_regular, R.font.grapenuts_regular, R.font.madimione_regular, R.font.montserrat_regular, R.font.pressstart2p_regular, R.font.shafarik_regular)
    private var currentFontIndex = 0
    private var simulatedTime: Calendar = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
    private val clockUpdateRunnable = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, if (isDebugMode) debugCycleInterval else 1000)
            Log.d("ClockUpdate", "Clock updated at ${System.currentTimeMillis()}")
        }
    }

    private val gradientUpdateRunnable = object : Runnable {
        override fun run() {
            updateGradientBackground()
            handler.postDelayed(this, if (isDebugMode) debugCycleInterval else 60000)
            Log.d("GradientUpdate", "Gradient updated at ${System.currentTimeMillis()}")
        }
    }

    private val editModeTimeoutRunnable = object : Runnable {
        override fun run() {
            if (isEditMode && !isDebugMode) {
                exitEditMode()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full-screen mode and keep screen on
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

        // Initialize UI
        timeText = findViewById(R.id.time_text)
        dateText = findViewById(R.id.date_text)
        backgroundLayout = findViewById(R.id.background_layout)
        settingsButton = findViewById(R.id.settings_button)
        debugButton = findViewById(R.id.debug_button)
        mainLayout = findViewById(R.id.main_layout)

        // Initially hide buttons
        settingsButton.alpha = 0f
        settingsButton.visibility = View.VISIBLE
        debugButton.alpha = 0f
        debugButton.visibility = View.VISIBLE

        // Load saved font
        val prefs = getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        currentFontIndex = prefs.getInt("fontIndex", 0)
        applyFont()

        // Long tap to toggle edit mode
        mainLayout.setOnLongClickListener {
            toggleEditMode()
            true
        }

        // Font switching in edit mode
        val fontClickListener = View.OnClickListener {
            if (isEditMode) {
                currentFontIndex = (currentFontIndex + 1) % fonts.size
                applyFont()
                with(prefs.edit()) {
                    putInt("fontIndex", currentFontIndex)
                    apply()
                }
                Toast.makeText(this@MainActivity, R.string.font_changed, Toast.LENGTH_SHORT).show()
                // Reset timeout
                if (!isDebugMode) {
                    handler.removeCallbacks(editModeTimeoutRunnable)
                    handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
                }
            }
        }
        timeText.setOnClickListener(fontClickListener)
        dateText.setOnClickListener(fontClickListener)

        // Debug mode toggle
        debugButton.setOnClickListener {
            toggleDebugMode()
            // Reset timeout
            if (!isDebugMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        }

        // Load saved coordinates
        if (prefs.getBoolean("useManualCoordinates", false)) {
            latitude = prefs.getFloat("latitude", 40.7128f).toDouble()
            longitude = prefs.getFloat("longitude", -74.0060f).toDouble()
            fetchSunTimes()
        } else {
            // Request location permissions
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), permissionRequestCode)
            } else {
                fetchLocationAndSunTimes()
            }
        }

        // Start updates
        startUpdates()
    }

    private fun startUpdates() {
        handler.removeCallbacks(clockUpdateRunnable)
        handler.removeCallbacks(gradientUpdateRunnable)
        handler.post(clockUpdateRunnable)
        handler.post(gradientUpdateRunnable)
        Log.d("MainActivity", "Updates started")
    }

    private fun stopUpdates() {
        handler.removeCallbacks(clockUpdateRunnable)
        handler.removeCallbacks(gradientUpdateRunnable)
        handler.removeCallbacks(editModeTimeoutRunnable)
        Log.d("MainActivity", "Updates stopped")
    }

    private fun applyFont() {
        val typeface = ResourcesCompat.getFont(this, fonts[currentFontIndex])
        timeText.typeface = typeface
        dateText.typeface = typeface
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        if (isEditMode) {
            // Zoom out main layout and fade in buttons
            mainLayout.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(animationDuration)
                .start()
            settingsButton.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            debugButton.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            // Show borders
            timeText.setBackgroundResource(R.drawable.editable_border)
            dateText.setBackgroundResource(R.drawable.editable_border)
            Toast.makeText(this, R.string.edit_mode_enabled, Toast.LENGTH_SHORT).show()
            // Start timeout if not in debug mode
            if (!isDebugMode) {
                handler.removeCallbacks(editModeTimeoutRunnable)
                handler.postDelayed(editModeTimeoutRunnable, editModeTimeout)
            }
        } else {
            exitEditMode()
        }
    }

    private fun exitEditMode() {
        isEditMode = false
        // Zoom in main layout and fade out buttons
        mainLayout.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(animationDuration)
            .start()
        settingsButton.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        debugButton.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        // Remove borders
        timeText.background = null
        dateText.background = null
        handler.removeCallbacks(editModeTimeoutRunnable)
        Toast.makeText(this, R.string.edit_mode_disabled, Toast.LENGTH_SHORT).show()
    }

    private fun toggleDebugMode() {
        isDebugMode = !isDebugMode
        if (isDebugMode) {
            // Reset simulated time to midnight
            simulatedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            // Restart updates for faster cycle
            startUpdates()
            Toast.makeText(this, R.string.debug_mode_enabled, Toast.LENGTH_SHORT).show()
        } else {
            // Restart updates for normal mode
            startUpdates()
            Toast.makeText(this, R.string.debug_mode_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload coordinates
        val prefs = getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("useManualCoordinates", false)) {
            latitude = prefs.getFloat("latitude", 40.7128f).toDouble()
            longitude = prefs.getFloat("longitude", -74.0060f).toDouble()
            fetchSunTimes()
        } else {
            fetchLocationAndSunTimes()
        }
        // Ensure edit mode and debug mode are off
        if (isEditMode) exitEditMode()
        if (isDebugMode) toggleDebugMode()
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
        if (requestCode == permissionRequestCode && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndSunTimes()
        } else {
            fetchSunTimes()
        }
    }

    private fun fetchLocationAndSunTimes() {
        val prefs = getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("useManualCoordinates", false)) {
            latitude = prefs.getFloat("latitude", 40.7128f).toDouble()
            longitude = prefs.getFloat("longitude", -74.0060f).toDouble()
            fetchSunTimes()
            return
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            fetchSunTimes()
            return
        }
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                latitude = location.latitude
                longitude = location.longitude
            }
            fetchSunTimes()
        } catch (e: SecurityException) {
            fetchSunTimes()
        }
    }

    private fun fetchSunTimes() {
        val url = "https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=today&formatted=0"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                parseSunTimes(response)
                updateGradientBackground()
                Log.d("SunTimes", "Fetched: sunrise=$sunriseTime, sunset=$sunsetTime, dawn=$dawnTime, dusk=$duskTime")
            },
            {
                setFallbackTimes()
                updateGradientBackground()
                Log.d("SunTimes", "Used fallback times")
            }
        )
        Volley.newRequestQueue(this).add(request)
    }

    private fun parseSunTimes(response: JSONObject) {
        val results = response.getJSONObject("results")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault())
        fun normalizeTime(time: String): String = time.replace("Z", "+0000")
        sunriseTime = dateFormat.parse(normalizeTime(results.getString("sunrise")))
        sunsetTime = dateFormat.parse(normalizeTime(results.getString("sunset")))
        solarNoonTime = dateFormat.parse(normalizeTime(results.getString("solar_noon")))
        dawnTime = dateFormat.parse(normalizeTime(results.getString("civil_twilight_begin")))
        duskTime = dateFormat.parse(normalizeTime(results.getString("civil_twilight_end")))
    }

    private fun setFallbackTimes() {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        sunriseTime = dateFormat.parse("06:00")?.apply {
            time = today.timeInMillis + (6 * 60 * 60 * 1000)
        } ?: Date()
        sunsetTime = dateFormat.parse("18:00")?.apply {
            time = today.timeInMillis + (18 * 60 * 60 * 1000)
        } ?: Date()
        dawnTime = dateFormat.parse("05:30")?.apply {
            time = today.timeInMillis + (5 * 60 + 30) * 60 * 1000
        } ?: Date()
        solarNoonTime = dateFormat.parse("12:00")?.apply {
            time = today.timeInMillis + (12 * 60 * 60 * 1000)
        } ?: Date()
        duskTime = dateFormat.parse("18:30")?.apply {
            time = today.timeInMillis + (18 * 60 + 30) * 60 * 1000
        } ?: Date()
    }

    private fun updateClock() {
        val currentTime = if (isDebugMode) {
            // Increment simulated time by 30 minutes
            simulatedTime.add(Calendar.MINUTE, 30)
            if (simulatedTime.get(Calendar.HOUR_OF_DAY) >= 24) {
                simulatedTime.set(Calendar.HOUR_OF_DAY, 0)
                simulatedTime.set(Calendar.MINUTE, 0)
            }
            // Show debug info
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(simulatedTime.time)
            val sunriseStr = sunriseTime?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "N/A"
            val sunsetStr = sunsetTime?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it) } ?: "N/A"
            Toast.makeText(this, "Time: $timeStr, Sunrise: $sunriseStr, Sunset: $sunsetStr", Toast.LENGTH_SHORT).show()
            Log.d("DebugMode", "Simulated time: $timeStr")
            simulatedTime.time
        } else {
            Calendar.getInstance().time
        }
        timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
        dateText.text = SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(currentTime)
    }

    private fun updateGradientBackground() {
        val currentTime = if (isDebugMode) simulatedTime.time else Calendar.getInstance().time
        val (topColor, bottomColor) = getSkyGradientColors(currentTime)
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        )
        backgroundLayout.background = gradientDrawable
        Log.d("GradientUpdate", "Colors: top=$topColor, bottom=$bottomColor")
    }

    private fun getSkyGradientColors(currentTime: Date): Pair<Int, Int> {
        val sunrise = sunriseTime ?: setFallbackTimes().let { sunriseTime!! }
        val sunset = sunsetTime ?: setFallbackTimes().let { sunsetTime!! }
        val dawn = dawnTime ?: setFallbackTimes().let { dawnTime!! }
        val solarNoon = solarNoonTime ?: setFallbackTimes().let { solarNoonTime!! }
        val dusk = duskTime ?: setFallbackTimes().let { duskTime!! }

        // Normalize all times to today's date
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentCal = Calendar.getInstance().apply { time = currentTime }
        currentCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        currentCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        currentCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val dawnCal = Calendar.getInstance().apply { time = dawn }
        dawnCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        dawnCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        dawnCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val sunriseCal = Calendar.getInstance().apply { time = sunrise }
        sunriseCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        sunriseCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        sunriseCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val solarNoonCal = Calendar.getInstance().apply { time = solarNoon }
        solarNoonCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        solarNoonCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        solarNoonCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val sunsetCal = Calendar.getInstance().apply { time = sunset }
        sunsetCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        sunsetCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        sunsetCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val duskCal = Calendar.getInstance().apply { time = dusk }
        duskCal.set(Calendar.YEAR, today.get(Calendar.YEAR))
        duskCal.set(Calendar.MONTH, today.get(Calendar.MONTH))
        duskCal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))

        val postSunsetCal = Calendar.getInstance().apply { time = sunsetCal.time; add(Calendar.MINUTE, 30) }
        val fullNightCal = Calendar.getInstance().apply { time = postSunsetCal.time; add(Calendar.MINUTE, 40) }

        return when {
            currentCal.time.before(dawnCal.time) -> {
                Log.d("Gradient", "Night phase at ${currentCal.time}")
                Pair(0xFF08090D.toInt(), 0xFF161B1F.toInt()) // Night
            }
            currentCal.time.before(sunriseCal.time) -> {
                val factor = (currentCal.time.time - dawnCal.time.time).toFloat() / (sunriseCal.time.time - dawnCal.time.time)
                Log.d("Gradient", "Dawn to sunrise at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF08090D.toInt(), 0xFF504787.toInt(), factor),
                    interpolateColor(0xFF161B1F.toInt(), 0xFFFE8A34.toInt(), factor)
                )
            }
            currentCal.time.before(solarNoonCal.time) -> {
                val factor = (currentCal.time.time - sunriseCal.time.time).toFloat() / (solarNoonCal.time.time - sunriseCal.time.time)
                Log.d("Gradient", "Sunrise to solar noon at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF504787.toInt(), 0xFF1E90FF.toInt(), factor),
                    interpolateColor(0xFFFE8A34.toInt(), 0xFFB0E0E6.toInt(), factor)
                )
            }
            currentCal.time.before(sunsetCal.time) -> {
                Log.d("Gradient", "Midday phase at ${currentCal.time}")
                Pair(0xFF1E90FF.toInt(), 0xFFB0E0E6.toInt()) // Midday
            }
            currentCal.time.before(duskCal.time) -> {
                val factor = (currentCal.time.time - sunsetCal.time.time).toFloat() / (duskCal.time.time - sunsetCal.time.time)
                Log.d("Gradient", "Sunset to dusk at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF1E90FF.toInt(), 0xFF393854.toInt(), factor),
                    interpolateColor(0xFFB0E0E6.toInt(), 0xFFF97D3D.toInt(), factor)
                )
            }
            currentCal.time.before(postSunsetCal.time) -> {
                val factor = (currentCal.time.time - duskCal.time.time).toFloat() / (postSunsetCal.time.time - duskCal.time.time)
                Log.d("Gradient", "Dusk to post-sunset at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF393854.toInt(), 0xFF52565F.toInt(), factor),
                    interpolateColor(0xFFF97D3D.toInt(), 0xFFF4794D.toInt(), factor)
                )
            }
            currentCal.time.before(fullNightCal.time) -> {
                val factor = (currentCal.time.time - postSunsetCal.time.time).toFloat() / (fullNightCal.time.time - postSunsetCal.time.time)
                Log.d("Gradient", "Post-sunset to full night at ${currentCal.time}, factor=$factor")
                Pair(
                    interpolateColor(0xFF52565F.toInt(), 0xFF08090D.toInt(), factor),
                    interpolateColor(0xFFF4794D.toInt(), 0xFF161B1F.toInt(), factor)
                )
            }
            else -> {
                Log.d("Gradient", "Full night phase at ${currentCal.time}")
                Pair(0xFF08090D.toInt(), 0xFF161B1F.toInt()) // Full Night
            }
        }
    }

    private fun interpolateColor(color1: Int, color2: Int, factor: Float): Int {
        val clampedFactor = factor.coerceIn(0f, 1f)
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val a1 = Color.alpha(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        val a2 = Color.alpha(color2)
        return Color.argb(
            (a1 + (a2 - a1) * clampedFactor).toInt(),
            (r1 + (r2 - r1) * clampedFactor).toInt(),
            (g1 + (g2 - g1) * clampedFactor).toInt(),
            (b1 + (b2 - b1) * clampedFactor).toInt()
        )
    }

}