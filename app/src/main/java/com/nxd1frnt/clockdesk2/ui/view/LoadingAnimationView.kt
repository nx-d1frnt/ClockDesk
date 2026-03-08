package com.nxd1frnt.clockdesk2.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.nxd1frnt.clockdesk2.ui.view.shader.CircularRevealShader
import com.nxd1frnt.clockdesk2.ui.view.shader.CompositeLoadingShader
import com.nxd1frnt.clockdesk2.ui.view.shader.ShaderUtilLibrary
import com.nxd1frnt.clockdesk2.ui.view.shader.SparkleShader
import kotlin.math.floor
import kotlin.math.max

/**
 * A self-contained loading + reveal animation view.
 *
 * - On API 33+ uses GPU [RuntimeShader]-based effects (sparkles, color turbulence,
 *   circular / fade reveal) identical to the original AOSP [LoadingAnimation].
 * - On API < 33 falls back to a CPU simplex-noise canvas animation that conveys
 *   the same visual intent without requiring AGSL support.
 *
 * Usage:
 * ```kotlin
 * loadingAnimationView.playLoadingAnimation()
 * // later, when content is ready:
 * loadingAnimationView.playRevealAnimation()
 * ```
 */
class LoadingAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    enum class RevealType { CIRCULAR, FADE }

    var revealType: RevealType = RevealType.CIRCULAR
    var timeOutDuration: Long? = null

    /**
     * The view to blur behind this overlay, resolved automatically as the sibling
     * directly below this view in the parent [ViewGroup].
     *
     * You can override it manually if needed:
     *   loadingAnimationView.targetView = myImageView
     *
     * Set to null to disable the behind-blur entirely.
     * Only has effect on API 33+.
     */
    var targetView: View? = null
        set(value) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                field?.setRenderEffect(null) // clear leftover blur on old target
            }
            field = value
        }

    /** Resolves [targetView], auto-detecting the sibling behind us if not set manually. */
    private fun resolvedTargetView(): View? {
        targetView?.let { return it }
        val p = parent as? ViewGroup ?: return null
        val myIndex = p.indexOfChild(this)
        return if (myIndex > 0) p.getChildAt(myIndex - 1) else null
    }

    // -------------------------------------------------------------------------
    // Native (API 33+) state
    // -------------------------------------------------------------------------

    private var nativeController: NativeLoadingController? = null

    // -------------------------------------------------------------------------
    // Legacy (< API 33) state
    // -------------------------------------------------------------------------

    private var legacyView: LegacyLoadingView? = null

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) elevation = 10f

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setupNative()
        } else {
            setupLegacy()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun setupNative() {
        // The RuntimeShader reads this view's pixels as `in_background`.
        // Without a filled child the shader samples transparent pixels and
        // produces nothing visible.
        val bgFill = View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
        }
        addView(bgFill)
        nativeController = NativeLoadingController(this)
    }

    private fun setupLegacy() {
        legacyView = LegacyLoadingView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }
        addView(legacyView)
    }

    // -------------------------------------------------------------------------
    // Public API – mirrors LoadingAnimation
    // -------------------------------------------------------------------------

    fun playLoadingAnimation(seed: Long? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nativeController?.playLoadingAnimation(seed)
        } else {
            playLegacyLoadingAnimation()
        }
    }

    fun playRevealAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nativeController?.playRevealAnimation()
        } else {
            playLegacyRevealAnimation()
        }
    }

    fun setupRevealAnimation(seed: Long? = null, revealTransitionProgress: Float? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nativeController?.setupRevealAnimation(seed, revealTransitionProgress)
        } else {
            playLegacyLoadingAnimation(seed) // best-effort for legacy
        }
    }

    /** Update accent colors for the shader effects (native only; legacy uses fixed white). */
    fun updateColors(accentColor: Int, backgroundColor: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Keep the fill view background in sync so the shader always
            // samples the right base colour as in_background.
            getChildAt(0)?.setBackgroundColor(backgroundColor)
            nativeController?.updateColors(accentColor, backgroundColor)
        }
        // Legacy: pass colors into the config used by the next playLoadingAnimation call.
        // If already animating, update the running config immediately.
        legacyView?.updateColors(accentColor, backgroundColor)
    }

    fun cancel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nativeController?.cancel()
        } else {
            legacyView?.cleanUp()
        }
    }

    fun end() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nativeController?.end()
        } else {
            legacyView?.finish(onEnd = { visibility = View.GONE })
        }
    }

    fun getElapsedTime(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            nativeController?.getElapsedTime() ?: 0L
        else 0L

    fun getTransitionProgress(): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            nativeController?.getTransitionProgress() ?: 0f
        else 0f

    // -------------------------------------------------------------------------
    // Legacy helpers
    // -------------------------------------------------------------------------

    private fun playLegacyLoadingAnimation(seed: Long? = null) {
        val view = legacyView ?: return
        post {
            if (width == 0 || height == 0) return@post
            visibility = View.VISIBLE
            bringToFront()
            view.visibility = View.VISIBLE

            val config = LegacyConfig(color = Color.WHITE)
            view.applyTargetBlur(1f)
            view.playEaseIn(config) {
                view.playMain(config)
                if (timeOutDuration != null) {
                    postDelayed({ playLegacyRevealAnimation() }, timeOutDuration!!)
                }
            }
        }
    }

    private fun playLegacyRevealAnimation() {
        val view = legacyView ?: return
        view.finish {
            visibility = View.GONE
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Auto-resolve the sibling behind us if no explicit targetView was set.
        // Done here (not in init) because the parent hierarchy isn't available yet in init.
        if (targetView == null) {
            targetView = resolvedTargetView()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nativeController?.cancel()
            resolvedTargetView()?.setRenderEffect(null) // always clean up on detach
        } else {
            legacyView?.cleanUp()
        }
    }

    // =========================================================================
    // NativeLoadingController  (API 33+)
    // =========================================================================

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private inner class NativeLoadingController(private val view: View) {

        private val pixelDensity = view.resources.displayMetrics.density

        private val loadingShader = CompositeLoadingShader()
        private val colorNoiseShader = TurbulenceNoiseShaderCompat().apply {
            setPixelDensity(pixelDensity)
            setGridCount(NOISE_SIZE)
            setOpacity(1f)
            setInverseNoiseLuminosity(inverse = true)
            setColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
        }
        private val sparkleShader = SparkleShader().apply {
            setPixelDensity(pixelDensity)
            setGridCount(NOISE_SIZE)
        }
        private val revealShader = CircularRevealShader()

        // Do NOT set blur to 0 — causes a crash.
        private var blurRadius = MIN_BLUR_PX
        private var lastBuiltBlurRadius = -1f        // cache: only rebuild when radius changes
        private var lastBuiltTargetBlurRadius = -1f    // same cache for the target view blur
        private var elapsedTime = 0L
        private var lastFrameTimeNs = 0L             // for Choreographer delta calculation
        private var transitionProgress = 0f

        private var fadeInAnimator: ValueAnimator? = null
        private var revealAnimator: ValueAnimator? = null
        private var animationState = AnimationState.IDLE
        private var choreographerRunning = false

        private var blurEffect = buildBlurEffect()

        // Choreographer callback — replaces TimeAnimator.
        // Throttled to TICK_INTERVAL_MS so uniforms update at ~30fps regardless
        // of display refresh rate, keeping the main thread and GPU load low.
        private var tickAccumulator = 0L
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!choreographerRunning) return

                val deltaNs = if (lastFrameTimeNs == 0L) 0L
                else frameTimeNanos - lastFrameTimeNs
                lastFrameTimeNs = frameTimeNanos

                tickAccumulator += deltaNs / 1_000_000L  // ns → ms
                if (tickAccumulator >= TICK_INTERVAL_MS) {
                    flushUniforms(elapsedTime, tickAccumulator)
                    tickAccumulator = 0L
                }

                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        // -- Colors --
        private var accentColor = Color.WHITE
        private var bgColor = Color.BLACK

        fun updateColors(accent: Int, bg: Int) {
            accentColor = accent
            bgColor = bg
            colorNoiseShader.setColor(accent)
            colorNoiseShader.setBackgroundColor(bg)
            sparkleShader.setColor(accent)
            loadingShader.setScreenColor(bg)
        }

        // -- Playback --

        fun playLoadingAnimation(seed: Long?) {
            if (animationState == AnimationState.FADE_IN_PLAYING ||
                animationState == AnimationState.FADE_IN_PLAYED) return

            if (animationState == AnimationState.REVEAL_PLAYING) revealAnimator?.cancel()
            animationState = AnimationState.FADE_IN_PLAYING
            view.visibility = View.VISIBLE
            elapsedTime = seed ?: (0L..10000L).random()

            cancelAllAnimators()

            fadeInAnimator = ValueAnimator.ofFloat(transitionProgress, 1f).apply {
                duration = FADE_IN_DURATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    transitionProgress = it.animatedValue as Float
                    loadingShader.setAlpha(transitionProgress)
                    blurRadius = maxOf(MAX_BLUR_PX * transitionProgress, MIN_BLUR_PX)
                    updateTargetBlur(transitionProgress)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        animationState = AnimationState.FADE_IN_PLAYED
                    }
                })
                start()
            }

            startChoreographer()
        }

        fun playRevealAnimation() {
            when (revealType) {
                RevealType.CIRCULAR -> playCircularReveal()
                RevealType.FADE -> playFadeReveal()
            }
        }

        fun setupRevealAnimation(seed: Long?, revealTransitionProgress: Float?) {
            cancel()
            view.visibility = View.VISIBLE
            elapsedTime = seed ?: (0L..10000L).random()
            transitionProgress = revealTransitionProgress ?: 1f
            blurRadius = maxOf(MAX_BLUR_PX * transitionProgress, MIN_BLUR_PX)
            blurEffect = buildBlurEffect()
            lastBuiltBlurRadius = blurRadius
            animationState = AnimationState.FADE_IN_PLAYED
            loadingShader.setAlpha(transitionProgress)
            startChoreographer()
        }

        fun cancel() {
            stopChoreographer()
            cancelAllAnimators()
        }

        fun end() {
            fadeInAnimator?.apply { removeAllListeners(); removeAllUpdateListeners(); end() }
            stopChoreographer()
            revealAnimator?.apply { removeAllListeners(); removeAllUpdateListeners(); end() }
            when (revealType) {
                RevealType.CIRCULAR -> resetCircularReveal()
                RevealType.FADE -> resetFadeReveal()
            }
        }

        fun getElapsedTime() = elapsedTime
        fun getTransitionProgress() = transitionProgress

        // -- Private --

        private fun startChoreographer() {
            if (choreographerRunning) return
            choreographerRunning = true
            lastFrameTimeNs = 0L
            tickAccumulator = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        private fun stopChoreographer() {
            choreographerRunning = false
            Choreographer.getInstance().removeFrameCallback(frameCallback)
        }

        private fun playCircularReveal() {
            if (animationState == AnimationState.REVEAL_PLAYING ||
                animationState == AnimationState.REVEAL_PLAYED ||
                animationState == AnimationState.FADE_OUT_PLAYING ||
                animationState == AnimationState.FADE_OUT_PLAYED) return

            if (animationState == AnimationState.FADE_IN_PLAYING) {
                fadeInAnimator?.removeAllListeners()
                fadeInAnimator?.removeAllUpdateListeners()
                fadeInAnimator?.cancel()
            }
            animationState = AnimationState.REVEAL_PLAYING
            view.visibility = View.VISIBLE
            revealShader.setCenter(view.width * 0.5f, view.height * 0.5f)
            revealAnimator?.cancel()
            revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = REVEAL_DURATION_MS
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val progress = it.animatedValue as Float
                    revealShader.setRadius(progress * max(view.width, view.height) * 2f)
                    val blurAmount = (1f - progress) *
                            (MAX_REVEAL_BLUR_AMOUNT - MIN_REVEAL_BLUR_AMOUNT) +
                            MIN_REVEAL_BLUR_AMOUNT
                    revealShader.setBlur(blurAmount)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { resetCircularReveal() }
                })
                start()
            }
        }

        private fun resetCircularReveal() {
            animationState = AnimationState.REVEAL_PLAYED
            view.setRenderEffect(null)
            view.visibility = View.INVISIBLE
            stopChoreographer()
            clearTargetBlur()
            blurRadius = MIN_BLUR_PX
            transitionProgress = 0f
        }

        private fun playFadeReveal() {
            if (animationState == AnimationState.REVEAL_PLAYING ||
                animationState == AnimationState.REVEAL_PLAYED ||
                animationState == AnimationState.FADE_OUT_PLAYING ||
                animationState == AnimationState.FADE_OUT_PLAYED) return

            if (animationState == AnimationState.FADE_IN_PLAYING) {
                fadeInAnimator?.removeAllListeners()
                fadeInAnimator?.removeAllUpdateListeners()
                fadeInAnimator?.cancel()
            }
            animationState = AnimationState.FADE_OUT_PLAYING
            view.visibility = View.VISIBLE
            fadeInAnimator = ValueAnimator.ofFloat(transitionProgress, 0f).apply {
                duration = FADE_OUT_DURATION_MS
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    transitionProgress = it.animatedValue as Float
                    loadingShader.setAlpha(transitionProgress)
                    blurRadius = maxOf(MAX_BLUR_PX * transitionProgress, MIN_BLUR_PX)
                    updateTargetBlur(transitionProgress)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        resetFadeReveal()
                    }
                })
                start()
            }
        }

        private fun resetFadeReveal() {
            animationState = AnimationState.FADE_OUT_PLAYED
            view.setRenderEffect(null)
            stopChoreographer()
            clearTargetBlur()
            blurRadius = MIN_BLUR_PX
            transitionProgress = 0f
        }

        private fun flushUniforms(totalTime: Long, deltaTime: Long) {
            // Guard: skip if the view hasn't been laid out yet
            val w = view.width.toFloat()
            val h = view.height.toFloat()
            if (w == 0f || h == 0f) return

            elapsedTime += deltaTime
            val time = elapsedTime / 1000f

            colorNoiseShader.apply {
                setSize(w, h)
                setNoiseMove(time * NOISE_SPEED, 0f, time * NOISE_SPEED)
            }
            sparkleShader.apply {
                setSize(w, h)
                setNoiseMove(time * NOISE_SPEED, 0f, time * NOISE_SPEED)
                setTime(time)
            }
            loadingShader.apply {
                setSparkle(sparkleShader)
                setColorTurbulenceMask(colorNoiseShader)
            }

            // Only rebuild RenderEffect objects when their inputs actually change.
            // Rebuilding every frame triggers a render-pipeline re-setup each time.
            val shaderEffect = RenderEffect.createRuntimeShaderEffect(loadingShader, "in_background")
            if (blurRadius != lastBuiltBlurRadius) {
                blurEffect = buildBlurEffect()
                lastBuiltBlurRadius = blurRadius
            }

            // Timeout
            if (timeOutDuration != null &&
                totalTime > timeOutDuration!! &&
                animationState == AnimationState.FADE_IN_PLAYED) {
                playRevealAnimation()
            }

            if (animationState == AnimationState.REVEAL_PLAYING) {
                // Unblur the target in sync with the reveal circle expanding
                val revealProgress = if (view.width > 0)
                    (revealAnimator?.animatedFraction ?: 0f) else 0f
                updateTargetBlur(1f - revealProgress)
                view.setRenderEffect(
                    RenderEffect.createChainEffect(
                        RenderEffect.createRuntimeShaderEffect(revealShader, "in_src"),
                        RenderEffect.createChainEffect(shaderEffect, blurEffect)
                    )
                )
            } else {
                view.setRenderEffect(RenderEffect.createChainEffect(shaderEffect, blurEffect))
            }
        }

        /**
         * Applies a blur [RenderEffect] to [targetView] scaled by [amount] (0 = sharp, 1 = max blur).
         * Uses the same cached-radius pattern as the shimmer blur to avoid per-frame rebuilds.
         */
        private fun updateTargetBlur(amount: Float) {
            val target = resolvedTargetView() ?: return
            val targetBlurPx = (TARGET_BLUR_MAX_PX * amount.coerceIn(0f, 1f)) * pixelDensity
            if (targetBlurPx == lastBuiltTargetBlurRadius) return
            lastBuiltTargetBlurRadius = targetBlurPx
            if (targetBlurPx <= MIN_BLUR_PX * pixelDensity) {
                target.setRenderEffect(null)
            } else {
                target.setRenderEffect(
                    RenderEffect.createBlurEffect(targetBlurPx, targetBlurPx, Shader.TileMode.MIRROR)
                )
            }
        }

        private fun clearTargetBlur() {
            resolvedTargetView()?.setRenderEffect(null)
            lastBuiltTargetBlurRadius = -1f
        }

        private fun buildBlurEffect() = RenderEffect.createBlurEffect(
            blurRadius * pixelDensity,
            blurRadius * pixelDensity,
            Shader.TileMode.MIRROR
        )

        private fun cancelAllAnimators() {
            fadeInAnimator?.removeAllListeners(); fadeInAnimator?.removeAllUpdateListeners(); fadeInAnimator?.cancel()
            revealAnimator?.removeAllListeners(); revealAnimator?.removeAllUpdateListeners(); revealAnimator?.cancel()
        }
    }

    // =========================================================================
    // TurbulenceNoiseShaderCompat
    //
    // A minimal RuntimeShader that replicates the TurbulenceNoiseShader used by
    // the original LoadingAnimation for the color-turbulence noise mask.
    // Replaces the SystemUI dependency on API 33+.
    // =========================================================================

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private class TurbulenceNoiseShaderCompat :
        android.graphics.RuntimeShader(TURBULENCE_SHADER) {

        companion object {
            // language=AGSL
            private const val UNIFORMS = """
                uniform vec2 in_size;
                uniform vec3 in_noiseMove;
                uniform float in_gridNum;
                uniform float in_aspectRatio;
                uniform half in_opacity;
                layout(color) uniform vec4 in_color;
                layout(color) uniform vec4 in_backgroundColor;
                uniform half in_pixelDensity;
                uniform int in_inverseLuminosity;
            """

            private const val MAIN_SHADER = """
                vec4 main(vec2 p) {
                    vec2 uv = p / in_size.xy;
                    uv.x *= in_aspectRatio;
                    vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                    half n = simplex3d(noiseP) * 0.5 + 0.5;
                    half luma = (in_inverseLuminosity == 1) ? (1.0 - n) : n;
                    vec3 color = mix(in_backgroundColor.rgb, in_color.rgb, luma);
                    return vec4(color * in_opacity, in_opacity);
                }
            """

            private val TURBULENCE_SHADER =
                UNIFORMS + ShaderUtilLibrary.SHADER_LIB + MAIN_SHADER
        }

        fun setSize(width: Float, height: Float) {
            setFloatUniform("in_size", width, height)
            setFloatUniform("in_aspectRatio", width / java.lang.Float.max(height, 0.001f))
        }

        fun setNoiseMove(x: Float, y: Float, z: Float) {
            setFloatUniform("in_noiseMove", x, y, z)
        }

        fun setGridCount(gridNumber: Float) {
            setFloatUniform("in_gridNum", gridNumber)
        }

        fun setOpacity(opacity: Float) {
            setFloatUniform("in_opacity", opacity)
        }

        fun setColor(color: Int) {
            setColorUniform("in_color", color)
        }

        fun setBackgroundColor(color: Int) {
            setColorUniform("in_backgroundColor", color)
        }

        fun setPixelDensity(density: Float) {
            setFloatUniform("in_pixelDensity", density)
        }

        fun setInverseNoiseLuminosity(inverse: Boolean) {
            setIntUniform("in_inverseLuminosity", if (inverse) 1 else 0)
        }
    }

    // =========================================================================
    // LegacyLoadingView  (API < 33)
    //
    // Improvements over the original:
    // - Bitmap generation moved to a background HandlerThread — main thread
    //   only calls invalidate() when a new frame is ready
    // - Throttled to LEGACY_FPS (20fps) — more than enough for a slow shimmer
    // - All three noise axes advance, not just Z
    // - Accepts accent/background colors from updateColors()
    // - Target-view blur simulated with a fast box-blur on a scaled Bitmap
    // - Reveal is a proper fade-out, not an instant hide
    // =========================================================================

    private inner class LegacyLoadingView(context: Context) : View(context) {

        // Render at 12% of screen size then scale up — keeps the pixel loop tiny
        private val scaleFactor = 0.10f

        // Double-buffered: worker writes into backBitmap, main thread draws frontBitmap
        private var frontBitmap: Bitmap? = null
        private var backBitmap: Bitmap? = null
        private val bitmapLock = Any()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Background thread for noise generation
        private val workerThread = HandlerThread("LegacyNoiseWorker").also { it.start() }
        private val workerHandler = Handler(workerThread.looper)
        private val mainHandler = Handler(Looper.getMainLooper())

        private var config: LegacyConfig? = null
        private var animator: ValueAnimator? = null

        private var noiseOffsetX = 0f
        private var noiseOffsetY = 0f
        private var noiseOffsetZ = 0f
        private var opacity = 0f

        // Throttle: only generate a new frame every FRAME_INTERVAL_MS on the worker
        private var lastFrameMs = 0L
        private val frameIntervalMs = 1000L / LEGACY_FPS

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0) {
                val bw = (w * scaleFactor).toInt().coerceAtLeast(1)
                val bh = (h * scaleFactor).toInt().coerceAtLeast(1)
                synchronized(bitmapLock) {
                    frontBitmap?.recycle()
                    backBitmap?.recycle()
                    frontBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    backBitmap  = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            synchronized(bitmapLock) { frontBitmap }?.let { bmp ->
                if (opacity > 0f) {
                    paint.alpha = (opacity * 255).toInt().coerceIn(0, 255)
                    canvas.save()
                    canvas.scale(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
                    canvas.drawBitmap(bmp, 0f, 0f, paint)
                    canvas.restore()
                }
            }
        }

        fun playEaseIn(config: LegacyConfig, onEnd: (() -> Unit)? = null) {
            this.config = config
            noiseOffsetX = Math.random().toFloat() * 100f
            noiseOffsetY = Math.random().toFloat() * 100f
            noiseOffsetZ = Math.random().toFloat() * 100f
            startAnimator(config.easeInDuration, 0f, 1f) { progress ->
                opacity = progress * config.luminosityMultiplier
                scheduleNoiseFrame()
            }?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd?.invoke() }
            })
        }

        fun playMain(config: LegacyConfig) {
            this.config = config
            startAnimator(config.maxDuration, 1f, 1f) { _ ->
                opacity = config.luminosityMultiplier
                scheduleNoiseFrame()
            }
        }

        fun finish(onEnd: (() -> Unit)? = null) {
            val cfg = config ?: LegacyConfig()
            val startAlpha = if (cfg.luminosityMultiplier > 0f)
                opacity / cfg.luminosityMultiplier else 0f
            startAnimator(cfg.easeOutDuration, startAlpha, 0f) { progress ->
                opacity = progress * cfg.luminosityMultiplier
                // Unblur the target in sync with our fade-out
                resolvedTargetView()?.alpha = 1f  // alpha trick: fade target in as we fade out
                updateLegacyTargetBlur(progress)
                scheduleNoiseFrame()
            }?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    clearLegacyTargetBlur()
                    visibility = View.GONE
                    onEnd?.invoke()
                }
            })
        }

        fun updateColors(color: Int, screenColor: Int) {
            config = config?.copy(color = color, screenColor = screenColor)
                ?: LegacyConfig(color = color, screenColor = screenColor)
        }

        fun cleanUp() {
            animator?.cancel()
            workerHandler.removeCallbacksAndMessages(null)
            synchronized(bitmapLock) {
                frontBitmap?.recycle(); frontBitmap = null
                backBitmap?.recycle();  backBitmap  = null
            }
            workerThread.quitSafely()
        }

        // -- Blur helpers for the target view (software path) --

        fun applyTargetBlur(amount: Float) {
            updateLegacyTargetBlur(amount)
        }

        private fun updateLegacyTargetBlur(amount: Float) {
            val target = resolvedTargetView() ?: return
            // On legacy we simulate blur by reducing alpha of an overlay.
            // True software blur is too expensive; a semi-transparent scrim
            // over the image is a visually acceptable approximation.
            target.alpha = 1f - (amount.coerceIn(0f, 1f) * LEGACY_TARGET_BLUR_ALPHA)
        }

        private fun clearLegacyTargetBlur() {
            resolvedTargetView()?.alpha = 1f
        }

        // -- Internal --

        private fun scheduleNoiseFrame() {
            workerHandler.post {
                val now = System.currentTimeMillis()
                if (now - lastFrameMs < frameIntervalMs) return@post
                lastFrameMs = now
                generateNoiseIntoBack()
                swapBuffers()
                mainHandler.post { invalidate() }
            }
        }

        private fun swapBuffers() {
            synchronized(bitmapLock) {
                val tmp = frontBitmap
                frontBitmap = backBitmap
                backBitmap = tmp
            }
        }

        private fun startAnimator(
            duration: Long, startVal: Float, endVal: Float,
            onUpdate: (Float) -> Unit
        ): ValueAnimator? {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(startVal, endVal).apply {
                this.duration = duration
                interpolator = LinearInterpolator()
                addUpdateListener { onUpdate(it.animatedValue as Float) }
                start()
            }
            return animator
        }

        private fun generateNoiseIntoBack() {
            val bitmap = synchronized(bitmapLock) { backBitmap } ?: return
            val cfg = config ?: return
            val w = bitmap.width; val h = bitmap.height
            if (w == 0 || h == 0) return

            // Advance all three axes for proper 3D cloud motion
            val step = frameIntervalMs / 1000f
            noiseOffsetX += cfg.noiseMoveSpeedX * step
            noiseOffsetY += cfg.noiseMoveSpeedY * step
            noiseOffsetZ += cfg.noiseMoveSpeedZ * step

            val pixels = IntArray(w * h)
            val ar = width.toFloat() / height.toFloat().coerceAtLeast(1f)
            val cr = Color.red(cfg.color);       val cg = Color.green(cfg.color);       val cb = Color.blue(cfg.color)
            val sr = Color.red(cfg.screenColor); val sg = Color.green(cfg.screenColor); val sb = Color.blue(cfg.screenColor)

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val uvX = (x.toFloat() / w) * ar
                    val uvY = y.toFloat() / h
                    val noise = simplexNoise3D(
                        (uvX + noiseOffsetX) * cfg.gridCount,
                        (uvY + noiseOffsetY) * cfg.gridCount,
                        noiseOffsetZ * cfg.gridCount
                    )
                    val luma = (1f - (noise * 0.5f + 0.5f)).coerceIn(0f, 1f)
                    val blend = (1.75f * luma - 1.3f).coerceAtLeast(0f)

                    val r = (sr + (cr - sr) * blend).toInt().coerceIn(0, 255)
                    val g = (sg + (cg - sg) * blend).toInt().coerceIn(0, 255)
                    val b = (sb + (cb - sb) * blend).toInt().coerceIn(0, 255)
                    val a = (blend * 255 * opacity).toInt().coerceIn(0, 255)
                    pixels[y * w + x] = Color.argb(a, r, g, b)
                }
            }
            bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        }

        // ---- Simplex 3D noise ----

        private fun simplexNoise3D(xin: Float, yin: Float, zin: Float): Float {
            val F3 = 1f / 3f; val G3 = 1f / 6f
            val s = (xin + yin + zin) * F3
            val i = floor(xin + s).toInt(); val j = floor(yin + s).toInt(); val k = floor(zin + s).toInt()
            val t = (i + j + k) * G3
            val x0 = xin - (i - t); val y0 = yin - (j - t); val z0 = zin - (k - t)

            val i1: Int; val j1: Int; val k1: Int; val i2: Int; val j2: Int; val k2: Int
            if (x0 >= y0) {
                if (y0 >= z0)      { i1=1;j1=0;k1=0;i2=1;j2=1;k2=0 }
                else if (x0 >= z0) { i1=1;j1=0;k1=0;i2=1;j2=0;k2=1 }
                else               { i1=0;j1=0;k1=1;i2=1;j2=0;k2=1 }
            } else {
                if (y0 < z0)       { i1=0;j1=0;k1=1;i2=0;j2=1;k2=1 }
                else if (x0 < z0)  { i1=0;j1=1;k1=0;i2=0;j2=1;k2=1 }
                else               { i1=0;j1=1;k1=0;i2=1;j2=1;k2=0 }
            }

            val x1=x0-i1+G3; val y1=y0-j1+G3; val z1=z0-k1+G3
            val x2=x0-i2+2f*G3; val y2=y0-j2+2f*G3; val z2=z0-k2+2f*G3
            val x3=x0-1f+3f*G3; val y3=y0-1f+3f*G3; val z3=z0-1f+3f*G3

            var n0=0f; var n1=0f; var n2=0f; var n3=0f
            var t0=0.6f-x0*x0-y0*y0-z0*z0
            if (t0>0) { val g=grad3(hash(i,j,k)); t0*=t0; n0=t0*t0*(g[0]*x0+g[1]*y0+g[2]*z0) }
            var t1=0.6f-x1*x1-y1*y1-z1*z1
            if (t1>0) { val g=grad3(hash(i+i1,j+j1,k+k1)); t1*=t1; n1=t1*t1*(g[0]*x1+g[1]*y1+g[2]*z1) }
            var t2=0.6f-x2*x2-y2*y2-z2*z2
            if (t2>0) { val g=grad3(hash(i+i2,j+j2,k+k2)); t2*=t2; n2=t2*t2*(g[0]*x2+g[1]*y2+g[2]*z2) }
            var t3=0.6f-x3*x3-y3*y3-z3*z3
            if (t3>0) { val g=grad3(hash(i+1,j+1,k+1)); t3*=t3; n3=t3*t3*(g[0]*x3+g[1]*y3+g[2]*z3) }

            return 32f * (n0+n1+n2+n3)
        }

        private fun hash(i: Int, j: Int, k: Int): Int {
            var h = i * 1671731 + j * 10139267 + k * 374761393
            h = h xor (h shr 16); return h and 0xFF
        }

        private fun grad3(hash: Int): FloatArray = when (hash and 15) {
            0  -> floatArrayOf(1f,  1f,  0f); 1  -> floatArrayOf(-1f, 1f,  0f)
            2  -> floatArrayOf(1f, -1f,  0f); 3  -> floatArrayOf(-1f,-1f,  0f)
            4  -> floatArrayOf(1f,  0f,  1f); 5  -> floatArrayOf(-1f, 0f,  1f)
            6  -> floatArrayOf(1f,  0f, -1f); 7  -> floatArrayOf(-1f, 0f, -1f)
            8  -> floatArrayOf(0f,  1f,  1f); 9  -> floatArrayOf( 0f,-1f,  1f)
            10 -> floatArrayOf(0f,  1f, -1f); 11 -> floatArrayOf( 0f,-1f, -1f)
            else -> floatArrayOf(1f, 1f, 0f)
        }
    }

    // =========================================================================
    // Shared animation state (hoisted out of inner class — enums are not
    // permitted inside inner / local classes in Kotlin on Android)
    // =========================================================================

    // Hoisted from LegacyLoadingView — data classes are prohibited inside inner classes
    private data class LegacyConfig(
        val color: Int = Color.WHITE,
        val screenColor: Int = Color.BLACK,
        val gridCount: Float = 1.7f,
        val noiseMoveSpeedX: Float = 0.04f,
        val noiseMoveSpeedY: Float = 0.02f,
        val noiseMoveSpeedZ: Float = 0.2f,
        val luminosityMultiplier: Float = 1f,
        val easeInDuration: Long = 1100L,
        val easeOutDuration: Long = 1500L,
        val maxDuration: Long = 30_000L
    )

    private enum class AnimationState {
        IDLE, FADE_IN_PLAYING, FADE_IN_PLAYED,
        FADE_OUT_PLAYING, FADE_OUT_PLAYED,
        REVEAL_PLAYING, REVEAL_PLAYED
    }

    // =========================================================================
    // Constants
    // =========================================================================

    companion object {
        private const val NOISE_SPEED            = 0.2f
        private const val NOISE_SIZE             = 1.7f
        private const val MAX_BLUR_PX            = 40f   // was 80 — halved; blur cost scales with radius
        private const val MIN_BLUR_PX            = 1f
        private const val FADE_IN_DURATION_MS    = 1100L
        private const val FADE_OUT_DURATION_MS   = 1500L
        const val TIME_OUT_DURATION_MS           = 10_000L
        private const val REVEAL_DURATION_MS     = 3600L
        private const val MIN_REVEAL_BLUR_AMOUNT = 1f
        private const val MAX_REVEAL_BLUR_AMOUNT = 2.5f
        // Uniform updates are throttled to this interval. The GPU renders at full
        // refresh rate — only the noise time value updates at ~30fps, which is
        // imperceptible on a slow-moving cloud/shimmer effect.
        private const val TICK_INTERVAL_MS       = 33L   // ~30fps
        private const val TARGET_BLUR_MAX_PX         = 25f   // max blur radius applied to the target view
        private const val LEGACY_FPS                  = 20L   // noise frame rate on API < 33
        private const val LEGACY_TARGET_BLUR_ALPHA    = 0.55f // how much to dim the target on legacy (simulates blur)
    }
}