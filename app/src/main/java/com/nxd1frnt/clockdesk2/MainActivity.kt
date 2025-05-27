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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var backgroundLayout: LinearLayout
    private lateinit var settingsButton: Button
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
    private val editModeTimeout = 10000L // 10 seconds
    private val animationDuration = 400L
    private val fonts = listOf(R.font.anton_regular, R.font.kanit_regular, R.font.sigmar_regular, R.font.monomakh_regular, R.font.orbitron_regular, R.font.dancingscript_regular, R.font.grapenuts_regular, R.font.madimione_regular, R.font.montserrat_regular, R.font.pressstart2p_regular, R.font.shafarik_regular)
    private var currentFontIndex = 0

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
        mainLayout = findViewById(R.id.main_layout)

        settingsButton.alpha = 0f
        settingsButton.visibility = View.VISIBLE // Keep visible for animation

        val prefs = getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        currentFontIndex = prefs.getInt("fontIndex", 0)
        applyFont()

        // Long tap to toggle edit mode
        mainLayout.setOnLongClickListener {
            toggleEditMode()
            true
        }

        val fontClickListener = View.OnClickListener {
            if (isEditMode) {
                currentFontIndex = (currentFontIndex + 1) % fonts.size
                applyFont()
                with(prefs.edit()) {
                    putInt("fontIndex", currentFontIndex)
                    apply()
                }
                Toast.makeText(this, R.string.font_changed, Toast.LENGTH_SHORT).show()
            }
        }
        timeText.setOnClickListener(fontClickListener)
        dateText.setOnClickListener(fontClickListener)
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

        // Start clock updates
        updateClock()
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateClock()
                handler.postDelayed(this, 1000) // Update every second
            }
        }, 1000)

        // Start gradient updates
        updateGradientBackground()
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateGradientBackground()
                handler.postDelayed(this, 60000) // Update every minute
            }
        }, 60000)

        // Open settings
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            exitEditMode() // Exit edit mode when opening settings
        }
    }

    private fun applyFont() {
        val typeface = ResourcesCompat.getFont(this, fonts[currentFontIndex])
        timeText.typeface = typeface
        dateText.typeface = typeface
    }

    private fun toggleEditMode() {
        isEditMode = !isEditMode
        if (isEditMode) {
            // Zoom out main layout and fade in settings button
            mainLayout.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(animationDuration)
                .setInterpolator(OvershootInterpolator())
                .start()
            settingsButton.animate()
                .alpha(1f)
                .setDuration(animationDuration)
                .start()
            // Show borders
            timeText.setBackgroundResource(R.drawable.editable_border)
            timeText.animate().alpha(if (isEditMode) 1f else 0f).setDuration(animationDuration).start()
            dateText.setBackgroundResource(R.drawable.editable_border)
            dateText.animate().alpha(if (isEditMode) 1f else 0f).setDuration(animationDuration).start()
            Toast.makeText(this, R.string.edit_mode_enabled, Toast.LENGTH_SHORT).show()
            // Start timeout
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                if (isEditMode) {
                    exitEditMode()
                }
            }, editModeTimeout)
        } else {
            exitEditMode()
        }
    }

    private fun exitEditMode() {
        isEditMode = false
        // Zoom in main layout and fade out settings button
        mainLayout.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(animationDuration)
            .start()
        settingsButton.animate()
            .alpha(0f)
            .setDuration(animationDuration)
            .start()
        // Remove borders
        timeText.background = null
        dateText.background = null
        handler.removeCallbacksAndMessages(null)
        Toast.makeText(this, R.string.edit_mode_disabled, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // Reload coordinates in case they changed
        val prefs = getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("useManualCoordinates", false)) {
            latitude = prefs.getFloat("latitude", 40.7128f).toDouble()
            longitude = prefs.getFloat("longitude", -74.0060f).toDouble()
            fetchSunTimes()
        } else {
            fetchLocationAndSunTimes()
        }
        // Ensure edit mode is off on resume
        if (isEditMode) {
            exitEditMode()
        }
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
            // Use fallback coordinates and fetch sun times
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
            fetchSunTimes() // Fallback if permission denied
            return
        }
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            val location: Location? = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                latitude = location.latitude
                longitude = location.longitude
            }
            fetchSunTimes()
        } catch (e: SecurityException) {
            fetchSunTimes() // Fallback if location access fails
        }
    }

    private fun fetchSunTimes() {
        val url = "https://api.sunrise-sunset.org/json?lat=$latitude&lng=$longitude&date=today&formatted=0"
        val request = JsonObjectRequest(
            Request.Method.GET, url, null,
            { response ->
                parseSunTimes(response)
                updateGradientBackground()
            },
            {
                // Fallback to hardcoded times on error
                setFallbackTimes()
                updateGradientBackground()
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
        sunriseTime = dateFormat.parse("06:00") ?: Date()
        sunsetTime = dateFormat.parse("18:00") ?: Date()
        dawnTime = dateFormat.parse("05:30") ?: Date()
        solarNoonTime = dateFormat.parse("12:00") ?: Date()
        duskTime = dateFormat.parse("17:30") ?: Date()
    }

    private fun updateClock() {
        val currentTime = Calendar.getInstance().time
        timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(currentTime)
        dateText.text = SimpleDateFormat("EEE, MMM dd", Locale.getDefault()).format(currentTime)
    }

    private fun updateGradientBackground() {
        val currentTime = Calendar.getInstance().time
        val (topColor, bottomColor) = getSkyGradientColors(currentTime)
        val gradientDrawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        )
        backgroundLayout.background = gradientDrawable
    }

    private fun getSkyGradientColors(currentTime: Date): Pair<Int, Int> {
        // Use API-fetched or fallback times
        val sunrise = sunriseTime ?: setFallbackTimes().let { sunriseTime!! }
        val sunset = sunsetTime ?: setFallbackTimes().let { sunsetTime!! }
        val dawn = dawnTime ?: setFallbackTimes().let { dawnTime!! }
        val solarNoon = solarNoonTime ?: setFallbackTimes().let { solarNoonTime!! }
        val dusk = duskTime ?: setFallbackTimes().let { duskTime!! }
        val postSunset = Date(sunset.time + 30 * 60 * 1000) // 30 minutes after sunset
        val fullNight = Date(postSunset.time + 40 * 60 * 1000) // 40 minutes after postSunset

        return when {
            currentTime.before(dawn) -> {
                Pair(0xFF08090D.toInt(), 0xFF161B1F.toInt()) // Night
            }
            currentTime.before(sunrise) -> {
                val factor = (currentTime.time - dawn.time).toFloat() / (sunrise.time - dawn.time)
                Pair(
                    interpolateColor(0xFF08090D.toInt(), 0xFF504787.toInt(), factor),
                    interpolateColor(0xFF161B1F.toInt(), 0xFFFE8A34.toInt(), factor)
                )
            }
            currentTime.before(solarNoon) -> {
                val factor = (currentTime.time - sunrise.time).toFloat() / (solarNoon.time - sunrise.time)
                Pair(
                    interpolateColor(0xFF504787.toInt(), 0xFF1E90FF.toInt(), factor),
                    interpolateColor(0xFFFE8A34.toInt(), 0xFFB0E0E6.toInt(), factor)
                )
            }
            currentTime.before(dusk) -> {
                Pair(0xFF1E90FF.toInt(), 0xFFB0E0E6.toInt()) // Midday
            }
            currentTime.before(sunset) -> {
                val factor = (currentTime.time - dusk.time).toFloat() / (sunset.time - dusk.time)
                Pair(
                    interpolateColor(0xFF1E90FF.toInt(), 0xFF393854.toInt(), factor),
                    interpolateColor(0xFFB0E0E6.toInt(), 0xFFF97D3D.toInt(), factor)
                )
            }
            currentTime.before(postSunset) -> {
                val factor = (currentTime.time - sunset.time).toFloat() / (postSunset.time - sunset.time)
                Pair(
                    interpolateColor(0xFF393854.toInt(), 0xFF52565F.toInt(), factor),
                    interpolateColor(0xFFF97D3D.toInt(), 0xFFF4794D.toInt(), factor)
                )
            }
            currentTime.before(fullNight) -> {
                val factor = (currentTime.time - postSunset.time).toFloat() / (fullNight.time - postSunset.time)
                Pair(
                    interpolateColor(0xFF52565F.toInt(), 0xFF08090D.toInt(), factor),
                    interpolateColor(0xFFF4794D.toInt(), 0xFF161B1F.toInt(), factor)
                )
            }
            else -> {
                Pair(0xFF08090D.toInt(), 0xFF161B1F.toInt()) // Full Night
            }
        }
    }

    private fun interpolateColor(color1: Int, color2: Int, factor: Float): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        val a1 = Color.alpha(color1)
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        val a2 = Color.alpha(color2)
        return Color.argb(
            (a1 + (a2 - a1) * factor).toInt(),
            (r1 + (r2 - r1) * factor).toInt(),
            (g1 + (g2 - g1) * factor).toInt(),
            (b1 + (b2 - b1) * factor).toInt()
        )
    }

}