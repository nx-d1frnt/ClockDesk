package com.nxd1frnt.clockdesk2.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import kotlin.math.floor
import com.nxd1frnt.clockdesk2.ui.view.TurbulenceNoiseView as NativeTurbulenceView

class TurbulenceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var nativeController: TurbulenceNoiseController? = null
    private var nativeView: NativeTurbulenceView? = null

    private var finishRunnable: Runnable? = null
    private var readyRunnable: Runnable? = null

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

        finishRunnable?.let { removeCallbacks(it) }
        readyRunnable?.let { removeCallbacks(it) }

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
                luminosityMultiplier = 0.6f
            )

            nativeController?.play(TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE, config)
            readyRunnable = Runnable { onReadyCallback() }
            postDelayed(readyRunnable, 400)

            finishRunnable = Runnable { nativeController?.finish() }
            postDelayed(finishRunnable, 5000)
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

                finishRunnable = Runnable {
                    turbulence.finish { visibility = View.GONE }
                }
                postDelayed(finishRunnable, 5000)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        nativeController?.finish()
        legacyTurbulenceView?.cleanUp()
    }

    // -------------------------------------------------------------------------
    // Legacy implementation for API < 33
    // -------------------------------------------------------------------------

    private class LegacySimplexNoiseView(context: Context) : View(context) {

        // Scale the noise bitmap well below screen size: the upscaled blur looks
        // intentional and dramatically reduces per-frame CPU work.
        private val scaleFactor = 0.12f

        private var noiseBitmap: Bitmap? = null

        // ARGB_8888 → RGB_565: halves memory & setPixels bandwidth for a colored overlay.
        // Alpha is communicated via paint.alpha instead, so no per-pixel alpha needed.
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        private var config: NoiseConfig? = null
        private var animator: ValueAnimator? = null

        // Noise state – driven by animator elapsed time, not wall-clock or fixed step.
        private var noiseBaseX = 0f
        private var noiseBaseY = 0f
        private var noiseBaseZ = 0f
        private var opacity = 0f

        // Background thread for noise generation so the main thread is never blocked.
        private val noiseThread = HandlerThread("LegacyNoiseGen").also { it.start() }
        private val noiseHandler = Handler(noiseThread.looper)

        // Guards against queuing multiple generation tasks when the bg thread can't keep up.
        // Frames are dropped rather than queued – this eliminates the blink/stutter caused
        // by a backlog of stale frames being swapped in rapid succession.
        @Volatile private var isGenerating = false

        // Double-buffer: noiseHandler writes to backBitmap, main thread reads frontBitmap.
        // ARGB_8888 is required: per-pixel alpha encodes the noise luma mask for transparency.
        private var frontBitmap: Bitmap? = null
        private var backBitmap: Bitmap? = null

        // Reusable pixel buffer – allocated once per bitmap size, never inside the loop.
        private var pixelBuffer: IntArray = IntArray(0)

        data class NoiseConfig(
            val gridCount: Float = 1.2f,
            val luminosityMultiplier: Float = 1f,
            val noiseMoveSpeedX: Float = 0f,
            val noiseMoveSpeedY: Float = 0f,
            val noiseMoveSpeedZ: Float = 0.3f,
            val color: Int = Color.WHITE,
            val screenColor: Int = Color.BLACK,
            val maxDuration: Long = 30000,
            val easeInDuration: Long = 750,
            val easeOutDuration: Long = 750,
            val lumaMatteBlendFactor: Float = 1f,
            val lumaMatteOverallBrightness: Float = 0f,
            val shouldInverseNoiseLuminosity: Boolean = false,
            val octaves: Int = 1
        )

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                val scaledW = (w * scaleFactor).toInt().coerceAtLeast(1)
                val scaledH = (h * scaleFactor).toInt().coerceAtLeast(1)

                frontBitmap?.recycle()
                backBitmap?.recycle()
                frontBitmap = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
                backBitmap  = Bitmap.createBitmap(scaledW, scaledH, Bitmap.Config.ARGB_8888)
                noiseBitmap = frontBitmap

                pixelBuffer = IntArray(scaledW * scaledH)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // Always read from frontBitmap (the last completed frame from the bg thread).
            val bitmap = frontBitmap ?: return
            if (opacity <= 0f) return

            paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
            val scaleX = width.toFloat() / bitmap.width
            val scaleY = height.toFloat() / bitmap.height

            canvas.save()
            canvas.scale(scaleX, scaleY)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            canvas.restore()
        }

        // --- Public animation API --------------------------------------------

        fun playEaseIn(config: NoiseConfig, onEnd: (() -> Unit)? = null) {
            this.config = config
            resetNoiseOffsets()
            // Capture base at phase start so elapsedSec drives offsets from here.
            val baseX = noiseBaseX; val baseY = noiseBaseY; val baseZ = noiseBaseZ

            startAnimator(config.easeInDuration, 0f, 1f) { progress, elapsedSec ->
                opacity = progress * config.luminosityMultiplier
                // Update noiseBase* so the next phase (playMain) inherits the correct
                // position seamlessly when its elapsedSec resets to 0.
                noiseBaseX = baseX + elapsedSec * config.noiseMoveSpeedX
                noiseBaseY = baseY + elapsedSec * config.noiseMoveSpeedY
                noiseBaseZ = baseZ + elapsedSec * config.noiseMoveSpeedZ
                scheduleNoiseFrame(0f)  // offsets already in noiseBase*
            }?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
            })
        }

        fun playMain(config: NoiseConfig) {
            this.config = config
            // Capture where ease-in left off; elapsedSec resets to 0 for this phase.
            val baseX = noiseBaseX; val baseY = noiseBaseY; val baseZ = noiseBaseZ

            startAnimator(config.maxDuration, 0f, 1f) { _, elapsedSec ->
                opacity = config.luminosityMultiplier
                noiseBaseX = baseX + elapsedSec * config.noiseMoveSpeedX
                noiseBaseY = baseY + elapsedSec * config.noiseMoveSpeedY
                noiseBaseZ = baseZ + elapsedSec * config.noiseMoveSpeedZ
                scheduleNoiseFrame(0f)  // offsets already in noiseBase*
            }
        }

        fun finish(onEnd: (() -> Unit)? = null) {
            val currentConfig = config ?: return   // Nothing to finish – guard null safely.
            val startOpacity = opacity
            val baseX = noiseBaseX; val baseY = noiseBaseY; val baseZ = noiseBaseZ

            startAnimator(currentConfig.easeOutDuration, 0f, 1f) { progress, elapsedSec ->
                opacity = startOpacity * (1f - progress)
                noiseBaseX = baseX + elapsedSec * currentConfig.noiseMoveSpeedX
                noiseBaseY = baseY + elapsedSec * currentConfig.noiseMoveSpeedY
                noiseBaseZ = baseZ + elapsedSec * currentConfig.noiseMoveSpeedZ
                scheduleNoiseFrame(0f)  // offsets already in noiseBase*
            }?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    visibility = GONE
                    onEnd?.invoke()
                }
            })
        }

        fun cleanUp() {
            animator?.cancel()
            noiseHandler.removeCallbacksAndMessages(null)
            noiseThread.quitSafely()
            isGenerating = false
            frontBitmap?.recycle(); frontBitmap = null
            backBitmap?.recycle();  backBitmap  = null
            noiseBitmap = null
        }

        // --- Internals -------------------------------------------------------

        private fun resetNoiseOffsets() {
            noiseBaseX = Math.random().toFloat() * 100f
            noiseBaseY = Math.random().toFloat() * 100f
            noiseBaseZ = Math.random().toFloat() * 100f
        }

        /**
         * Posts noise generation to the background thread.
         * The main thread is never blocked; it just draws the last ready frame.
         *
         * If a generation task is already running, the current frame is dropped.
         * This prevents a queue of stale frames from building up and being flushed
         * all at once, which was the cause of the visible blinking.
         */
        private fun scheduleNoiseFrame(elapsedSec: Float) {
            if (isGenerating) return   // Drop frame – bg thread still busy.

            val cfg = config ?: return
            // Capture backBitmap reference on the main thread before posting.
            val back = backBitmap ?: return

            // Each animation phase sets noiseBase* at its start and passes elapsedSec
            // from its own animator, so offset = base + time * speed is always relative
            // to the beginning of that phase. No cross-phase accumulation occurs.
            val ox = noiseBaseX + elapsedSec * cfg.noiseMoveSpeedX
            val oy = noiseBaseY + elapsedSec * cfg.noiseMoveSpeedY
            val oz = noiseBaseZ + elapsedSec * cfg.noiseMoveSpeedZ

            isGenerating = true
            noiseHandler.post {
                generateNoiseTo(back, cfg, ox, oy, oz)
                // Swap on the main thread, then clear the flag so the next frame
                // only begins after the freshly written bitmap is safely in front.
                post {
                    val tmp = frontBitmap
                    frontBitmap = back
                    backBitmap  = tmp
                    isGenerating = false
                    invalidate()
                }
            }
        }

        /**
         * All per-pixel noise math runs off the main thread.
         * Uses the pre-allocated [pixelBuffer] – no allocations inside the loop.
         */
        private fun generateNoiseTo(
            bitmap: Bitmap,
            cfg: NoiseConfig,
            ox: Float, oy: Float, oz: Float
        ) {
            val w = bitmap.width
            val h = bitmap.height
            if (w == 0 || h == 0) return

            val buf = pixelBuffer
            if (buf.size < w * h) return  // Size mismatch during resize – skip frame.

            val aspectRatio = width.toFloat() / height.toFloat().coerceAtLeast(0.001f)

            val colorR = Color.red(cfg.color)
            val colorG = Color.green(cfg.color)
            val colorB = Color.blue(cfg.color)
            val screenR = Color.red(cfg.screenColor)
            val screenG = Color.green(cfg.screenColor)
            val screenB = Color.blue(cfg.screenColor)

            val invW = aspectRatio / w
            val invH = 1f / h
            val gridCount = cfg.gridCount
            val blendFactor = cfg.lumaMatteBlendFactor
            val brightness = cfg.lumaMatteOverallBrightness
            val octaves = cfg.octaves
            val inverse = cfg.shouldInverseNoiseLuminosity

            var idx = 0
            for (y in 0 until h) {
                val uvY = y * invH
                for (x in 0 until w) {
                    val uvX = x * invW

                    val nx = (uvX + ox) * gridCount
                    val ny = (uvY + oy) * gridCount
                    val nz = oz * gridCount

                    var noise = simplexNoise3D(nx, ny, nz, octaves)
                    if (inverse) noise = -noise

                    var luma = (noise * 0.5f + 0.5f) * blendFactor + brightness
                    if (luma < 0f) luma = 0f else if (luma > 1f) luma = 1f

                    val r = (screenR + (colorR - screenR) * luma + 0.5f).toInt()
                    val g = (screenG + (colorG - screenG) * luma + 0.5f).toInt()
                    val b = (screenB + (colorB - screenB) * luma + 0.5f).toInt()

                    // Premultiplied alpha: alpha = luma, RGB premultiplied accordingly.
                    val a = (luma * 255 + 0.5f).toInt()
                    buf[idx++] = Color.argb(a, r, g, b)
                }
            }

            bitmap.setPixels(buf, 0, w, 0, 0, w, h)
        }

        private fun startAnimator(
            duration: Long,
            startVal: Float,
            endVal: Float,
            onUpdate: (progress: Float, elapsedSec: Float) -> Unit
        ): ValueAnimator? {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(startVal, endVal).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val progress = anim.animatedValue as Float
                    // Use animator's own elapsed time – tied to the animation clock,
                    // not wall-clock, so it's correct even after pauses/frame drops.
                    val elapsedSec = anim.currentPlayTime * 0.001f
                    onUpdate(progress, elapsedSec)
                }
                start()
            }
            return animator
        }

        // --- Simplex noise math ----------------------------------------------

        private fun simplexNoise3D(x: Float, y: Float, z: Float, octaves: Int): Float {
            if (octaves == 1) return simplexNoise3DBase(x, y, z)

            var total = 0f
            var frequency = 1f
            var amplitude = 1f
            var maxValue = 0f
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
            val x0 = xin - (i - t)
            val y0 = yin - (j - t)
            val z0 = zin - (k - t)

            val i1: Int; val j1: Int; val k1: Int
            val i2: Int; val j2: Int; val k2: Int
            if (x0 >= y0) {
                if (y0 >= z0)      { i1=1;j1=0;k1=0;i2=1;j2=1;k2=0 }
                else if (x0 >= z0) { i1=1;j1=0;k1=0;i2=1;j2=0;k2=1 }
                else               { i1=0;j1=0;k1=1;i2=1;j2=0;k2=1 }
            } else {
                if (y0 < z0)       { i1=0;j1=0;k1=1;i2=0;j2=1;k2=1 }
                else if (x0 < z0)  { i1=0;j1=1;k1=0;i2=0;j2=1;k2=1 }
                else               { i1=0;j1=1;k1=0;i2=1;j2=1;k2=0 }
            }

            val x1 = x0 - i1 + G3; val y1 = y0 - j1 + G3; val z1 = z0 - k1 + G3
            val x2 = x0 - i2 + 2f*G3; val y2 = y0 - j2 + 2f*G3; val z2 = z0 - k2 + 2f*G3
            val x3 = x0 - 1f + 3f*G3; val y3 = y0 - 1f + 3f*G3; val z3 = z0 - 1f + 3f*G3

            var n0 = 0f; var n1 = 0f; var n2 = 0f; var n3 = 0f

            var t0 = 0.6f - x0*x0 - y0*y0 - z0*z0
            if (t0 > 0f) { t0 *= t0; n0 = t0*t0 * dotGrad(hash(i,j,k), x0,y0,z0) }

            var t1 = 0.6f - x1*x1 - y1*y1 - z1*z1
            if (t1 > 0f) { t1 *= t1; n1 = t1*t1 * dotGrad(hash(i+i1,j+j1,k+k1), x1,y1,z1) }

            var t2 = 0.6f - x2*x2 - y2*y2 - z2*z2
            if (t2 > 0f) { t2 *= t2; n2 = t2*t2 * dotGrad(hash(i+i2,j+j2,k+k2), x2,y2,z2) }

            var t3 = 0.6f - x3*x3 - y3*y3 - z3*z3
            if (t3 > 0f) { t3 *= t3; n3 = t3*t3 * dotGrad(hash(i+1,j+1,k+1), x3,y3,z3) }

            return 32f * (n0 + n1 + n2 + n3)
        }

        private fun hash(i: Int, j: Int, k: Int): Int {
            var h = i * 1671731 + j * 10139267 + k * 374761393
            h = h xor (h shr 16)
            return h and 0xFF
        }

        /**
         * Inlined dot product with gradient vector – eliminates the FloatArray
         * allocation that the original [grad3] caused on every noise sample.
         */
        private fun dotGrad(hash: Int, x: Float, y: Float, z: Float): Float {
            return when (hash and 15) {
                0  ->  x + y
                1  -> -x + y
                2  ->  x - y
                3  -> -x - y
                4  ->  x + z
                5  -> -x + z
                6  ->  x - z
                7  -> -x - z
                8  ->  y + z
                9  -> -y + z
                10 ->  y - z
                11 -> -y - z
                else -> x + y   // 12-15: wrap, matches original
            }
        }
    }
}