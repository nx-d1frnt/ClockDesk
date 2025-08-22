package com.nxd1frnt.clockdesk2

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.util.Log
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.android.volley.toolbox.ImageLoader
import com.android.volley.toolbox.ImageRequest
import java.util.UUID

class BackgroundManager(
    private val context: Context,
    private val backgroundLayout: LinearLayout,
    private val gradientManager: GradientManager
) {
    private var currentBackground: Background? = null
    private val backgrounds = mutableListOf<Background>()

//    init {
//        // Initialize with dynamic gradient and sample images
//        backgrounds.add(
//            Background(
//                id = "gradient_dynamic",
//                type = BackgroundType.GRADIENT,
//                previewDrawable = createGradientPreview(),
//                data = "dynamic_gradient"
//            )
//        )
//        // Add sample images (replace with actual resource IDs or gallery access)
//        backgrounds.add(
//            Background(
//                id = "image_1",
//                type = BackgroundType.IMAGE,
//                previewDrawable = ContextCompat.getDrawable(context, R.drawable.sample_image_1),
//                data = "res://com.nxd1frnt.clockdesk2/drawable/sample_image_1"
//            )
//        )
//        backgrounds.add(
//            Background(
//                id = "image_2",
//                type = BackgroundType.IMAGE,
//                previewDrawable = ContextCompat.getDrawable(context, R.drawable.sample_image_2),
//                data = "res://com.nxd1frnt.clockdesk2/drawable/sample_image_2"
//            )
//        )
//        loadSavedBackground()
//    }
//
//    fun getBackgrounds(): List<Background> = backgrounds
//
//    fun applyBackground(background: Background) {
//        currentBackground = background
//        when (background.type) {
//            BackgroundType.GRADIENT -> {
//                gradientManager.startUpdates() // Resume gradient updates
//                backgroundLayout.background = gradientManager.getCurrentGradient()
//            }
//            BackgroundType.IMAGE -> {
//                gradientManager.stopUpdates() // Stop gradient updates
//                val imageUri = Uri.parse(background.data as String)
//                ImageLoader(context).enqueue(
//                    ImageRequest.Builder(context)
//                        .data(imageUri)
//                        .target { drawable ->
//                            backgroundLayout.background = drawable
//                        }
//                        .build()
//                )
//            }
//        }
//        saveBackground()
//        Log.d("BackgroundManager", "Applied background: ${background.id}")
//    }
//
//    fun addImageBackground(uri: Uri) {
//        val id = UUID.randomUUID().toString()
//        val background = Background(
//            id = id,
//            type = BackgroundType.IMAGE,
//            previewDrawable = null, // Generate preview if needed
//            data = uri.toString()
//        )
//        backgrounds.add(background)
//        applyBackground(background)
//        Log.d("BackgroundManager", "Added image background: $id")
//    }
//
//    private fun loadSavedBackground() {
//        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
//        val savedId = prefs.getString("background_id", "gradient_dynamic")
//        currentBackground = backgrounds.find { it.id == savedId }
//        if (currentBackground != null) {
//            applyBackground(currentBackground!!)
//        } else {
//            applyBackground(backgrounds[0]) // Default to gradient
//        }
//    }
//
//    private fun saveBackground() {
//        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
//        prefs.edit().putString("background_id", currentBackground?.id).apply()
//    }
//
//    private fun createGradientPreview(): GradientDrawable {
//        val gradientDrawable = GradientDrawable(
//            GradientDrawable.Orientation.TOP_BOTTOM,
//            intArrayOf(0xFF1E90FF.toInt(), 0xFFB0E0E6.toInt()) // Sample midday gradient
//        )
//        gradientDrawable.setSize(100, 100) // Small size for preview
//        return gradientDrawable
//    }
//
//    fun getCurrentGradient(): GradientDrawable? {
//        return if (currentBackground?.type == BackgroundType.GRADIENT) {
//            gradientManager.getCurrentGradient()
//        } else {
//            null
//        }
//    }
}