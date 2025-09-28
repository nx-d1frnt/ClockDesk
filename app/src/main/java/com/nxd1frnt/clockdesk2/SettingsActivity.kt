package com.nxd1frnt.clockdesk2

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {
    private lateinit var latitudeEditText: TextInputEditText
    private lateinit var longitudeEditText: TextInputEditText
    private lateinit var manualCoordinatesCheckBox: MaterialSwitch
    private lateinit var saveButton: Button
    private lateinit var chooseBackgroundButton: Button
    private lateinit var clearBackgroundButton: Button

    private val PICK_IMAGE_REQUEST = 200

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
        chooseBackgroundButton = findViewById(R.id.choose_background_button)
        clearBackgroundButton = findViewById(R.id.clear_background_button)

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

        // Choose background button -> open document picker for images
        chooseBackgroundButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // Clear saved background
        clearBackgroundButton.setOnClickListener {
            val savedUriStr = prefs.getString("background_uri", null)
            if (savedUriStr != null) {
                try {
                    val uri = Uri.parse(savedUriStr)
                    // release persistable permission if we previously took it
                    try {
                        contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // ignore if not permitted
                    }
                } catch (e: Exception) {
                    // ignore
                }
                // Remove the current background selection but keep list of saved backgrounds
                prefs.edit().remove("background_uri").apply()
                Toast.makeText(this, getString(R.string.clear_background_toast), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.no_background_to_clear), Toast.LENGTH_SHORT).show()
            }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            // Persist permission
            try {
                // Persist read permission for the chosen URI.
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // ignore if cannot persist
            }
            // Save URI string to prefs as the current background and add to saved list
            val prefs = getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString("background_uri", uri.toString()).apply()
            // also add to the set of available backgrounds
            val existing = prefs.getStringSet("background_uris", emptySet())?.toMutableSet() ?: mutableSetOf()
            existing.add(uri.toString())
            prefs.edit().putStringSet("background_uris", existing).apply()
            Toast.makeText(this, getString(R.string.background_saved_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEditTextState() {
        val enabled = manualCoordinatesCheckBox.isChecked
        latitudeEditText.isEnabled = enabled
        longitudeEditText.isEnabled = enabled
    }

}