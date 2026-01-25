package com.nxd1frnt.clockdesk2.utils

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.google.android.material.color.utilities.QuantizerCelebi
import com.google.android.material.color.utilities.Score

object ColorExtractor {

    /**
     * Извлекает доминирующий "Seed" цвет из Bitmap в стиле Material You.
     * Работает асинхронно, чтобы не фризить UI.
     */
    @SuppressLint("RestrictedApi")
    fun extractColor(bitmap: Bitmap, onColorReady: (Int) -> Unit) {
        val handler = Handler(Looper.getMainLooper())

        Thread {
            try {
                val scaledBitmap = if (bitmap.width > 128 || bitmap.height > 128) {
                    Bitmap.createScaledBitmap(bitmap, 128, 128, false)
                } else {
                    bitmap
                }

                val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
                scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)

                val colors = QuantizerCelebi.quantize(pixels, 128)

                val ranked = Score.score(colors)

                val seedColor = if (ranked.isNotEmpty()) ranked[0] else null

                handler.post {
                    if (seedColor != null) {
                        onColorReady(seedColor)
                    } else {
                        onColorReady(android.graphics.Color.LTGRAY)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                handler.post { onColorReady(android.graphics.Color.LTGRAY) }
            }
        }.start()
    }
}