package com.nxd1frnt.clockdesk2.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceNoiseAnimationConfig
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceNoiseController
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceNoiseView as NativeTurbulenceView
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceNoiseShader
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class TurbulenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var nativeController: TurbulenceNoiseController? = null
    private var nativeView: NativeTurbulenceView? = null

    // Legacy компоненты
    private var legacyTurbulenceView: LegacySimplexNoiseView? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            elevation = 10f
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setupNativeImplementation()
        } else {
            setupLegacyImplementation()
        }
    }

    private fun setupNativeImplementation() {
        nativeView = NativeTurbulenceView(context, null).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }
        addView(nativeView)
        nativeController = TurbulenceNoiseController(nativeView!!)
    }

    private fun setupLegacyImplementation() {
        legacyTurbulenceView = LegacySimplexNoiseView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }
        addView(legacyTurbulenceView)
    }

    fun playAnimation(color: Int? = null, onReadyCallback: () -> Unit) {
        val targetColor = color ?: Color.WHITE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            playNative(targetColor, onReadyCallback)
        } else {
            playLegacy(targetColor, onReadyCallback)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun playNative(color: Int, onReadyCallback: () -> Unit) {
        post {
            if (width == 0 || height == 0) {
                onReadyCallback()
                return@post
            }
            visibility = View.VISIBLE
            bringToFront()

            val config = TurbulenceNoiseAnimationConfig(
                width = width.toFloat(),
                height = height.toFloat(),
                color = color,
                pixelDensity = resources.displayMetrics.density,
                gridCount = 1.0f,
                noiseMoveSpeedZ = 0.45f,
                luminosityMultiplier = 1.0f
            )

            nativeController?.play(TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE, config)
            postDelayed({ onReadyCallback() }, 400)
            postDelayed({ nativeController?.finish() }, 5000)
        }
    }

    private fun playLegacy(color: Int, onReadyCallback: () -> Unit) {
        val turbulence = legacyTurbulenceView ?: return

        post {
            if (width == 0 || height == 0) {
                onReadyCallback()
                return@post
            }

            visibility = View.VISIBLE
            bringToFront()
            turbulence.visibility = View.VISIBLE

            val config = LegacySimplexNoiseView.NoiseConfig(
                color = color,
                gridCount = 1.0f,
                noiseMoveSpeedZ = 0.45f,
                luminosityMultiplier = 1.0f,
                octaves = 1
            )

            turbulence.playEaseIn(config) {
                onReadyCallback()
                turbulence.playMain(config)
                postDelayed({
                    turbulence.finish { visibility = View.GONE }
                }, 5000)
            }
        }
    }


    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        nativeController?.finish()
        legacyTurbulenceView?.cleanUp()
    }

    private class LegacySimplexNoiseView(context: Context) : View(context) {

        private val scaleFactor = 0.12f

        private var noiseBitmap: Bitmap? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) // Filter flag для сглаживания

        private var config: NoiseConfig? = null
        private var animator: ValueAnimator? = null

        // Noise state
        private var noiseOffsetX = 0f
        private var noiseOffsetY = 0f
        private var noiseOffsetZ = 0f
        private var opacity = 0f

        data class NoiseConfig(
            val gridCount: Float = 1.2f, // Number of noise grids across the screen
            val luminosityMultiplier: Float = 1f, // Multiplier for overall brightness
            val noiseMoveSpeedX: Float = 0f, // Speed of noise movement in X direction
            val noiseMoveSpeedY: Float = 0f, // Speed of noise movement in Y direction
            val noiseMoveSpeedZ: Float = 0.3f, // Speed of noise movement in Z direction
            val color: Int = Color.WHITE, // Color of the noise
            val screenColor: Int = Color.BLACK, // Background color
            val maxDuration: Long = 30000, // Duration of main animation
            val easeInDuration: Long = 750, // Duration of ease-in animation
            val easeOutDuration: Long = 750, // Duration of ease-out animation
            val lumaMatteBlendFactor: Float = 1f, // Contrast factor for noise luminance
            val lumaMatteOverallBrightness: Float = 0f, // Overall brightness offset for noise luminance
            val shouldInverseNoiseLuminosity: Boolean = false, // Whether to invert the noise luminance
            val octaves: Int = 1 // Number of noise octaves for detail
        )

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                noiseBitmap?.recycle()
                val scaledW = (w * scaleFactor).toInt().coerceAtLeast(1)
                val scaledH = (h * scaleFactor).toInt().coerceAtLeast(1)
                noiseBitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            noiseBitmap?.let { bitmap ->
                if (opacity > 0f) {
                    paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                    val scaleX = width.toFloat() / bitmap.width
                    val scaleY = height.toFloat() / bitmap.height

                    canvas.save()
                    canvas.scale(scaleX, scaleY)
                    canvas.drawBitmap(bitmap, 0f, 0f, paint)
                    canvas.restore()
                }
            }
        }

        fun playEaseIn(config: NoiseConfig, onEnd: (() -> Unit)? = null) {
            this.config = config
            noiseOffsetX = Math.random().toFloat() * 100f
            noiseOffsetY = Math.random().toFloat() * 100f
            noiseOffsetZ = Math.random().toFloat() * 100f

            startAnimator(config.easeInDuration, 0f, 1f) { progress, time ->
                opacity = progress * config.luminosityMultiplier
                updateNoiseAndDraw(time)
            }?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.invoke()
                }
            })
        }

        fun playMain(config: NoiseConfig) {
            this.config = config
            startAnimator(config.maxDuration, 1f, 1f) { _, time ->
                opacity = config.luminosityMultiplier
                updateNoiseAndDraw(time)
            }
        }

        fun finish(onEnd: (() -> Unit)? = null) {
            val currentConfig = config ?: NoiseConfig()

            val startAlpha = opacity / currentConfig.luminosityMultiplier

            startAnimator(currentConfig.easeOutDuration, startAlpha, 0f) { progress, time ->
                opacity = progress * currentConfig.luminosityMultiplier
                updateNoiseAndDraw(time)
            }?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = View.GONE
                    onEnd?.invoke()
                }
            })
        }

        fun cleanUp() {
            animator?.cancel()
            noiseBitmap?.recycle()
            noiseBitmap = null
        }

        private fun startAnimator(
            duration: Long,
            startVal: Float,
            endVal: Float,
            onUpdate: (Float, Float) -> Unit
        ): ValueAnimator? {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(startVal, endVal).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val progress = anim.animatedValue as Float
                    val timeInSec = System.currentTimeMillis() / 1000f
                    onUpdate(progress, timeInSec)
                }
                start()
            }
            return animator
        }

        private fun updateNoiseAndDraw(timeInSec: Float) {
            val cfg = config ?: return

            noiseOffsetX += cfg.noiseMoveSpeedX * 0.016f // ~60fps step
            noiseOffsetY += cfg.noiseMoveSpeedY * 0.016f
            noiseOffsetZ += cfg.noiseMoveSpeedZ * 0.016f

            generateNoise()
            invalidate()
        }

        private fun generateNoise() {
            val bitmap = noiseBitmap ?: return
            val cfg = config ?: return

            val w = bitmap.width
            val h = bitmap.height
            if (w == 0 || h == 0) return

            val pixels = IntArray(w * h)

            val aspectRatio = width.toFloat() / height.toFloat()

            val colorRed = Color.red(cfg.color)
            val colorGreen = Color.green(cfg.color)
            val colorBlue = Color.blue(cfg.color)

            val screenR = Color.red(cfg.screenColor)
            val screenG = Color.green(cfg.screenColor)
            val screenB = Color.blue(cfg.screenColor)

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val uvX = (x.toFloat() / w) * aspectRatio
                    val uvY = y.toFloat() / h

                    val noiseX = (uvX + noiseOffsetX) * cfg.gridCount
                    val noiseY = (uvY + noiseOffsetY) * cfg.gridCount
                    val noiseZ = noiseOffsetZ * cfg.gridCount

                    var noise = simplexNoise3D(noiseX, noiseY, noiseZ, cfg.octaves)

                    if (cfg.shouldInverseNoiseLuminosity) {
                        noise = -noise
                    }

                    // Map [-1, 1] to [0, 1]
                    var luma = noise * 0.5f + 0.5f

                    // Contrast/Brightness
                    luma = (luma * cfg.lumaMatteBlendFactor + cfg.lumaMatteOverallBrightness).coerceIn(0f, 1f)


                    val blendFactor = luma // * 0.6f

                    val finalR = (screenR + (colorRed - screenR) * blendFactor).toInt()
                    val finalG = (screenG + (colorGreen - screenG) * blendFactor).toInt()
                    val finalB = (screenB + (colorBlue - screenB) * blendFactor).toInt()

                    val pixelAlpha = (luma * 255).toInt()

                    pixels[y * w + x] = Color.argb(pixelAlpha, finalR, finalG, finalB)
                }
            }

            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        // --- Simplex Noise Math ---
        private fun simplexNoise3D(x: Float, y: Float, z: Float, octaves: Int): Float {
            var total = 0f
            var frequency = 1f
            var amplitude = 1f
            var maxValue = 0f
            // Avoid unnecessary calculations for single octave
            if (octaves == 1) {
                return simplexNoise3DBase(x, y, z)
            }

            for (i in 0 until octaves) {
                total += simplexNoise3DBase(x * frequency, y * frequency, z * frequency) * amplitude
                maxValue += amplitude
                amplitude *= 0.5f
                frequency *= 2f
            }

            return total / maxValue
        }

        private fun simplexNoise3DBase(xin: Float, yin: Float, zin: Float): Float {
            val F3 = 1f / 3f
            val G3 = 1f / 6f

            val s = (xin + yin + zin) * F3
            val i = floor(xin + s).toInt()
            val j = floor(yin + s).toInt()
            val k = floor(zin + s).toInt()

            val t = (i + j + k) * G3
            val X0 = i - t
            val Y0 = j - t
            val Z0 = k - t
            val x0 = xin - X0
            val y0 = yin - Y0
            val z0 = zin - Z0

            val i1: Int; val j1: Int; val k1: Int
            val i2: Int; val j2: Int; val k2: Int

            if (x0 >= y0) {
                if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0 }
                else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1 }
                else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1 }
            } else {
                if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1 }
                else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1 }
                else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0 }
            }

            val x1 = x0 - i1 + G3
            val y1 = y0 - j1 + G3
            val z1 = z0 - k1 + G3
            val x2 = x0 - i2 + 2f * G3
            val y2 = y0 - j2 + 2f * G3
            val z2 = z0 - k2 + 2f * G3
            val x3 = x0 - 1f + 3f * G3
            val y3 = y0 - 1f + 3f * G3
            val z3 = z0 - 1f + 3f * G3

            var n0 = 0f; var n1 = 0f; var n2 = 0f; var n3 = 0f

            var t0 = 0.6f - x0 * x0 - y0 * y0 - z0 * z0
            if (t0 > 0) {
                val grad = grad3(hash(i, j, k))
                t0 *= t0
                n0 = t0 * t0 * (grad[0] * x0 + grad[1] * y0 + grad[2] * z0)
            }

            var t1 = 0.6f - x1 * x1 - y1 * y1 - z1 * z1
            if (t1 > 0) {
                val grad = grad3(hash(i + i1, j + j1, k + k1))
                t1 *= t1
                n1 = t1 * t1 * (grad[0] * x1 + grad[1] * y1 + grad[2] * z1)
            }

            var t2 = 0.6f - x2 * x2 - y2 * y2 - z2 * z2
            if (t2 > 0) {
                val grad = grad3(hash(i + i2, j + j2, k + k2))
                t2 *= t2
                n2 = t2 * t2 * (grad[0] * x2 + grad[1] * y2 + grad[2] * z2)
            }

            var t3 = 0.6f - x3 * x3 - y3 * y3 - z3 * z3
            if (t3 > 0) {
                val grad = grad3(hash(i + 1, j + 1, k + 1))
                t3 *= t3
                n3 = t3 * t3 * (grad[0] * x3 + grad[1] * y3 + grad[2] * z3)
            }

            return 32f * (n0 + n1 + n2 + n3)
        }

        private fun hash(i: Int, j: Int, k: Int): Int {
            var h = i * 1671731 + j * 10139267 + k * 374761393
            h = h xor (h shr 16)
            h = h and 0xFF
            return h
        }

        private fun grad3(hash: Int): FloatArray {
            val h = hash and 15
            return when (h) {
                0 -> floatArrayOf(1f, 1f, 0f); 1 -> floatArrayOf(-1f, 1f, 0f)
                2 -> floatArrayOf(1f, -1f, 0f); 3 -> floatArrayOf(-1f, -1f, 0f)
                4 -> floatArrayOf(1f, 0f, 1f); 5 -> floatArrayOf(-1f, 0f, 1f)
                6 -> floatArrayOf(1f, 0f, -1f); 7 -> floatArrayOf(-1f, 0f, -1f)
                8 -> floatArrayOf(0f, 1f, 1f); 9 -> floatArrayOf(0f, -1f, 1f)
                10 -> floatArrayOf(0f, 1f, -1f); 11 -> floatArrayOf(0f, -1f, -1f)
                else -> floatArrayOf(1f, 1f, 0f)
            }
        }
    }
}