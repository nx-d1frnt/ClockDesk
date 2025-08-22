package com.nxd1frnt.clockdesk2

import android.content.Context import android.os.Bundle import android.view.View import android.view.WindowManager import android.widget.Button import android.widget.CheckBox import android.widget.EditText import android.widget.Toast import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : AppCompatActivity() {
    private lateinit var latitudeEditText: TextInputEditText
    private lateinit var longitudeEditText: TextInputEditText
    private lateinit var manualCoordinatesCheckBox: MaterialSwitch
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable full-screen mode and keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )

        setContentView(R.layout.activity_settings)

        // Initialize UI
        latitudeEditText = findViewById(R.id.latitude_edit)
        longitudeEditText = findViewById(R.id.longitude_edit)
        manualCoordinatesCheckBox = findViewById(R.id.manual_coordinates_checkbox)
        saveButton = findViewById(R.id.save_button)

        // Load saved preferences
        val prefs = getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val useManual = prefs.getBoolean("useManualCoordinates", false)
        val savedLatitude = prefs.getFloat("latitude", 40.7128f) // Default: New York
        val savedLongitude = prefs.getFloat("longitude", -74.0060f)

        manualCoordinatesCheckBox.isChecked = useManual
        latitudeEditText.setText(savedLatitude.toString())
        longitudeEditText.setText(savedLongitude.toString())
        updateEditTextState()

        // Enable/disable EditText based on CheckBox
        manualCoordinatesCheckBox.setOnCheckedChangeListener { _, isChecked ->
            updateEditTextState()
        }

        // Save settings and finish
        saveButton.setOnClickListener {
            if (manualCoordinatesCheckBox.isChecked) {
                val latitudeStr = latitudeEditText.text.toString()
                val longitudeStr = longitudeEditText.text.toString()
                try {
                    val latitude = latitudeStr.toDouble()
                    val longitude = longitudeStr.toDouble()
                    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                        Toast.makeText(this, getString(R.string.invalid_coordinates), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    with(prefs.edit()) {
                        putBoolean("useManualCoordinates", true)
                        putFloat("latitude", latitude.toFloat())
                        putFloat("longitude", longitude.toFloat())
                        apply()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, getString(R.string.invalid_coordinates), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            } else {
                with(prefs.edit()) {
                    putBoolean("useManualCoordinates", false)
                    apply()
                }
            }
            finish()
        }
    }

    private fun updateEditTextState() {
        val enabled = manualCoordinatesCheckBox.isChecked
        latitudeEditText.isEnabled = enabled
        longitudeEditText.isEnabled = enabled
    }

}