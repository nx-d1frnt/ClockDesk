/*
 * Copyright (C) 2022 The Android Open Source Project
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nxd1frnt.clockdesk2.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.drawable.GradientDrawable
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import com.nxd1frnt.clockdesk2.desks.ui.DeskForegroundStyle
import com.nxd1frnt.clockdesk2.utils.PerformanceTracker
import com.nxd1frnt.clockdesk2.utils.calculateWeatherIntensity
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Высокопроизводительный фоновый контейнер на базе OpenGL ES 2.0.
 * Объединяет кинематографичный переход (Liquid Chromatic Transition)
 * и систему симуляции погоды (WeatherGLView) в единый рендер-конвейер.
 */
class DynamicBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    enum class WeatherType { NONE, RAIN, SNOW, FOG, THUNDERSTORM, CLOUDY, CLEAR }
    enum class TransitionType { LIQUID_CHROMATIC, COMPOSITE_LOADING, SPARKLE_SWIPE }
    enum class TurbulencePhase {
        IDLE,
        EASE_IN,
        MAIN,
        TRANSITION_TO_CONTINUOUS,
        CONTINUOUS,
        EASE_OUT
    }

    var onBackgroundForegroundChanged: ((Int) -> Unit)? = null
    private var backgroundSamples = intArrayOf()
    private val foregroundColorMatrix = ColorMatrix()

    private fun updateBackgroundForeground() {
        onBackgroundForegroundChanged?.invoke(
            DeskForegroundStyle.contrastingColor(backgroundSamples, foregroundColorMatrix)
        )
    }

    val performanceTracker = PerformanceTracker()

    private val renderer: BackgroundRenderer
    private var transitionAnimator: ValueAnimator? = null
    @Volatile private var transitionVersion = 0L
    private var isPaused = false
    private var fadeAnimator: ValueAnimator? = null
    private var backgroundZoomAnimator: ValueAnimator? = null
    private var backgroundZoom = 1f
    private var lastTopColor: Int? = null
    private var lastBottomColor: Int? = null

    private data class BackgroundTransition(
        val bitmap: Bitmap, val durationMs: Long, val isGradient: Boolean,
        val scale: Float, val offsetX: Float, val offsetY: Float, val version: Long
    )
    @Volatile private var pendingSurfaceTransition: BackgroundTransition? = null
    @Volatile private var latestBackgroundTransition: BackgroundTransition? = null
    @Volatile private var currentBitmap: Bitmap? = null
    @Volatile private var fogBitmap: Bitmap? = null
    @Volatile private var cloudsBitmap: Bitmap? = null

    @Volatile var transitionType: TransitionType = TransitionType.LIQUID_CHROMATIC
        set(value) {
            if (field != value) {
                field = value
                queueEvent {
                    renderer.setTransitionType(value)
                    requestRender()
                }
            }
        }

    var weatherResolutionScale: Float = 0.4f
        set(value) {
            if (field != value) {
                field = value
                requestRender()
            }
        }

    @Volatile var maxFps: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestRender()
            }
        }

    var areAnimationsPaused: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                queueEvent {
                    renderer.areAnimationsPaused = value
                    requestRender()
                }
            }
        }

    val imageWidth: Int
        get() = currentBitmap?.width?.takeIf { it > 0 } ?: renderer.getImageWidth()

    val imageHeight: Int
        get() = currentBitmap?.height?.takeIf { it > 0 } ?: renderer.getImageHeight()

    init {
        // Настройка EGL для поддержки полупрозрачности и аппаратного сглаживания
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(false)

        renderer = BackgroundRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        reloadTransitionPreferences()
    }

    /**
     * Загружает выбранный стиль перехода фона из SharedPreferences.
     */
    fun reloadTransitionPreferences() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val style = prefs.getString("graphics_transition_style", "liquid_chromatic")
        val targetType = when (style) {
            "composite_loading" -> TransitionType.COMPOSITE_LOADING
            "sparkle_swipe" -> TransitionType.SPARKLE_SWIPE
            else -> TransitionType.LIQUID_CHROMATIC
        }
        transitionType = targetType
        queueEvent {
            renderer.setTransitionType(targetType)
        }
    }

    /**
     * Устанавливает акцентный и фоновый цвета для CompositeLoadingShader перехода.
     */
    fun setTransitionColors(accentColor: Int, screenColor: Int? = null) {
        queueEvent {
            renderer.setTransitionColors(accentColor, screenColor)
        }
    }

    /** Uses the same palette at tones 40 (accent1.s600) and 10 (accent1.s900) as AOSP. */
    @android.annotation.SuppressLint("RestrictedApi")
    fun setTransitionColorSeed(seedColor: Int) {
        val palette = com.google.android.material.color.utilities.TonalPalette.fromInt(seedColor)
        setTransitionColors(palette.tone(40), palette.tone(10))
    }

    /** Starts the loading effect on the current image before the next bitmap is available. */
    fun beginCompositeLoading() {
        if (isPaused || transitionType != TransitionType.COMPOSITE_LOADING) return
        ++transitionVersion
        pendingSurfaceTransition = null
        transitionAnimator?.cancel()
        queueEvent {
            renderer.beginCompositeLoading()
            requestRender()
        }
    }

    /** Clears a loading effect whose image request failed, was replaced, or was canceled. */
    fun cancelCompositeLoading() {
        queueEvent {
            if (renderer.cancelCompositeLoading()) requestRender()
        }
    }

    /**
     * Запускает высокохудожественный переход к новому фоновому изображению.
     */
    fun transitionTo(
        bitmap: Bitmap,
        durationMs: Long = 1800L,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        if (currentBitmap == bitmap) {
            return
        }
        transitionToInternal(bitmap, durationMs, false, scale, offsetX, offsetY)
    }

    private fun transitionToInternal(
        bitmap: Bitmap,
        durationMs: Long,
        isGradient: Boolean,
        scale: Float = 1f,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ) {
        backgroundSamples = DeskForegroundStyle.sample(bitmap)
        updateBackgroundForeground()
        reloadTransitionPreferences()
        if (!isGradient) {
            lastTopColor = null
            lastBottomColor = null
        }
        val version = ++transitionVersion
        transitionAnimator?.cancel()
        val request = BackgroundTransition(bitmap, durationMs, isGradient, scale, offsetX, offsetY, version)
        // Publish before currentBitmap: surface creation must not install this image instantly.
        latestBackgroundTransition = request
        pendingSurfaceTransition = request
        currentBitmap = bitmap
        queueEvent { applyBackgroundTransition(request) }
    }

    private fun applyBackgroundTransition(request: BackgroundTransition) {
        if (request !== pendingSurfaceTransition || request.version != transitionVersion ||
            !renderer.isSurfaceCreated) return
        pendingSurfaceTransition = null
        val (bitmap, durationMs, isGradient, scale, offsetX, offsetY, version) = request
        val shouldAnimate = renderer.setNextTexture(bitmap, durationMs <= 0, isGradient, scale, offsetX, offsetY)
        val startingProgress = renderer.getTransitionProgress()

        post {
            if (version != transitionVersion) return@post
            if (shouldAnimate && durationMs > 0 && !isPaused) {
                transitionAnimator?.cancel()
                val activeType = transitionType
                val effectiveDuration = if (activeType == TransitionType.COMPOSITE_LOADING && durationMs >= 300L) {
                    maxOf(durationMs, CompositeLoadingTimeline.TOTAL_DURATION_MS)
                } else {
                    durationMs
                }
                transitionAnimator = ValueAnimator.ofFloat(startingProgress, 1f).apply {
                    duration = (effectiveDuration * (1f - startingProgress)).toLong().coerceAtLeast(1L)
                    interpolator = if (activeType == TransitionType.COMPOSITE_LOADING) {
                        android.view.animation.LinearInterpolator()
                    } else {
                        AccelerateDecelerateInterpolator()
                    }
                    addUpdateListener { anim ->
                        val progress = anim.animatedValue as Float
                        renderer.setTransitionProgress(progress)
                        requestRender()
                    }
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            if (version != transitionVersion) return
                            queueEvent {
                                renderer.finishTransitionIfComplete()
                            }
                        }
                    })
                    start()
                }
            } else {
                if (shouldAnimate) {
                    queueEvent {
                        renderer.setTransitionProgress(1f)
                        renderer.finishTransitionIfComplete()
                        requestRender()
                    }
                } else {
                    requestRender()
                }
            }
        }
    }

    /**
     * Генерация градиента в Bitmap и плавный переход к нему на GPU.
     */
    fun transitionToGradient(topColor: Int, bottomColor: Int, durationMs: Long = 1800L, force: Boolean = false) {
        if (!force && lastTopColor == topColor && lastBottomColor == bottomColor) {
            com.nxd1frnt.clockdesk2.utils.Logger.d("BackgroundFlow") { "DynamicBackgroundView.transitionToGradient: colors unchanged, ignoring" }
            return
        }
        com.nxd1frnt.clockdesk2.utils.Logger.i("BackgroundFlow") { "DynamicBackgroundView.transitionToGradient: top=#${String.format("%06X", 0xFFFFFF and topColor)}, bottom=#${String.format("%06X", 0xFFFFFF and bottomColor)}, durationMs=$durationMs" }
        lastTopColor = topColor
        lastBottomColor = bottomColor
        setTransitionColorSeed(topColor)
        val bitmap = createGradientBitmap(topColor, bottomColor, 256, 256)
        transitionToInternal(bitmap, durationMs, true)
    }

    /**
     * Smooth transition to a uniform solid color on GPU.
     */
    fun transitionToSolidColor(color: Int, durationMs: Long = 1800L, force: Boolean = true) {
        com.nxd1frnt.clockdesk2.utils.Logger.i("BackgroundFlow") { "DynamicBackgroundView.transitionToSolidColor: color=#${String.format("%06X", 0xFFFFFF and color)}, durationMs=$durationMs, force=$force" }
        if (force) {
            lastTopColor = null
            lastBottomColor = null
        }
        transitionToGradient(color, color, durationMs, force)
    }

    private fun createGradientBitmap(topColor: Int, bottomColor: Int, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val drawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        )
        drawable.setBounds(0, 0, width, height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Плавная регулировка яркости и прозрачности на уровне фрагментного шейдера.
     */
    fun setGlobalAlpha(alpha: Float, durationMs: Long = 0L) {
        if (durationMs <= 0) {
            queueEvent {
                renderer.setGlobalAlpha(alpha)
                requestRender()
            }
        } else {
            post {
                fadeAnimator?.cancel()
                fadeAnimator = ValueAnimator.ofFloat(renderer.getGlobalAlpha(), alpha).apply {
                    duration = durationMs
                    addUpdateListener { anim ->
                        val value = anim.animatedValue as Float
                        queueEvent {
                            renderer.setGlobalAlpha(value)
                            requestRender()
                        }
                    }
                    start()
                }
            }
        }
    }

    /** Zooms bitmap UVs while turbulence, weather, and reveal geometry stay in view pixels. */
    fun setBackgroundZoom(zoom: Float, durationMs: Long = 0L, onEnd: (() -> Unit)? = null) {
        backgroundZoomAnimator?.cancel()
        val target = if (zoom.isFinite()) zoom.coerceAtLeast(1f) else 1f
        fun update(value: Float) {
            backgroundZoom = value
            queueEvent {
                renderer.setBackgroundZoom(value)
                requestRender()
            }
        }
        if (durationMs <= 0 || isPaused) {
            update(target)
            onEnd?.invoke()
            return
        }
        backgroundZoomAnimator = ValueAnimator.ofFloat(backgroundZoom, target).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { update(it.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var canceled = false
                override fun onAnimationCancel(animation: android.animation.Animator) { canceled = true }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!canceled) onEnd?.invoke()
                }
            })
            start()
        }
    }

    /**
     * Передача матрицы фильтров (Night Shift, диммирование, погода).
     */
    fun setColorFilter(matrix: ColorMatrix) {
        foregroundColorMatrix.set(matrix)
        updateBackgroundForeground()
        queueEvent {
            renderer.updateColorMatrix(matrix)
            requestRender()
        }
    }

    /**
     * Синхронизация сдвигов кропа.
     */
    fun setCropTransform(scale: Float, offsetX: Float, offsetY: Float) {
        queueEvent {
            renderer.setCropTransform(scale, offsetX, offsetY)
            requestRender()
        }
    }

    /**
     * Установка параметров погоды.
     */
    fun forceWeather(
        type: WeatherType,
        intensity: Float,
        wind: Float,
        isNight: Boolean,
        dayFactor: Float = if (!isNight) 1.0f else 0.0f
    ) {
        queueEvent {
            renderer.updateWeatherConfig(type, intensity, wind, isNight, dayFactor)
        }
    }

    /**
     * Установка погоды на основе метеоданных.
     */
    fun updateFromOpenMeteoSmart(
        wmoCode: Int, windSpeed: Double, night: Boolean,
        precipitation: Double?, cloudCover: Int?, visibility: Double?,
        windUnit: String = "kmh", precipUnit: String = "mm",
        dayFactor: Float = if (!night) 1.0f else 0.0f
    ) {
        val rawWind = windSpeed.toFloat()
        val windKmh = when (windUnit) {
            "mph" -> rawWind * 1.60934f
            "ms" -> rawWind * 3.6f
            else -> rawWind
        }
        val windFactor = (windKmh / 15f).coerceIn(-4f, 4f)
        val calculatedIntensity = calculateWeatherIntensity(
            wmoCode, windSpeed, precipitation, cloudCover, visibility, windUnit, precipUnit
        )

        val type = when (wmoCode) {
            0, 1 -> WeatherType.CLEAR   // clear sky, mainly clear
            2    -> WeatherType.CLEAR   // partly cloudy (gentle clouds, still feels clear)
            3    -> WeatherType.CLOUDY  // overcast
            45, 48 -> WeatherType.FOG
            51, 53, 55, 56, 57 -> WeatherType.RAIN
            61, 63, 65, 80, 81, 82 -> WeatherType.RAIN
            71, 73, 75, 77, 85, 86 -> WeatherType.SNOW
            95, 96, 99 -> WeatherType.THUNDERSTORM
            else -> WeatherType.NONE
        }

        queueEvent {
            renderer.updateWeatherConfig(type, calculatedIntensity, windFactor, night, dayFactor)
        }
    }

    /**
     * Загрузка текстур облаков/тумана.
     */
    fun setFogTextures(fog: Bitmap?, clouds: Bitmap?) {
        fogBitmap = fog
        cloudsBitmap = clouds
        queueEvent {
            renderer.loadFogTextures(fog, clouds)
        }
    }

    /**
     * Запуск OpenGL анимации турбулентности (3D Simplex noise).
     */
    fun playTurbulence(
        color: Int? = null,
        durationMs: Long = 5000L,
        easeInDurationMs: Long = 400L,
        easeOutDurationMs: Long = 750L,
        continuous: Boolean = false,
        onReadyCallback: (() -> Unit)? = null,
        onEndCallback: (() -> Unit)? = null
    ) {
        val targetColor = color ?: android.graphics.Color.parseColor("#5A7184")
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val gridScale = prefs.getInt("graphics_turbulence_grid", 10) / 10f
        val speedScale = prefs.getInt("graphics_turbulence_speed", 10) / 10f
        val isTurbulenceEnabled = prefs.getBoolean("graphics_enable_turbulence", true)

        if (!isTurbulenceEnabled) {
            onReadyCallback?.invoke()
            onEndCallback?.invoke()
            return
        }

        val config = TurbulenceNoiseAnimationConfig(
            color = targetColor,
            gridCount = 1.0f * gridScale,
            noiseMoveSpeedZ = 0.45f * speedScale,
            luminosityMultiplier = 0.6f,
            easeInDuration = easeInDurationMs.toFloat(),
            maxDuration = durationMs.toFloat(),
            easeOutDuration = easeOutDurationMs.toFloat()
        )
        playTurbulence(config, continuous, onReadyCallback, onEndCallback)
    }

    /**
     * Запуск OpenGL анимации турбулентности с кастомной конфигурацией.
     */
    fun playTurbulence(
        config: TurbulenceNoiseAnimationConfig,
        continuous: Boolean = false,
        onReadyCallback: (() -> Unit)? = null,
        onEndCallback: (() -> Unit)? = null
    ) {
        queueEvent {
            renderer.startTurbulence(config, continuous, onReadyCallback, onEndCallback)
            requestRender()
        }
    }

    /**
     * Плавное завершение текущей анимации турбулентности.
     */
    fun finishTurbulence(easeOutDurationMs: Long = 750L) {
        queueEvent {
            renderer.finishTurbulence(easeOutDurationMs)
            requestRender()
        }
    }

    /**
     * Немедленная отмена анимации турбулентности.
     */
    fun cancelTurbulence() {
        queueEvent {
            renderer.cancelTurbulence()
            requestRender()
        }
    }

    /**
     * Немедленное обновление параметров турбулентности на лету.
     */
    fun updateTurbulenceLiveConfig(
        gridScale: Float? = null,
        speedScale: Float? = null,
        continuous: Boolean? = null,
        color: Int? = null
    ) {
        queueEvent {
            renderer.updateTurbulenceLiveConfig(gridScale, speedScale, continuous, color)
        }
    }

    /**
     * Перезагрузка параметров турбулентности из SharedPreferences на лету.
     */
    fun reloadTurbulencePreferences() {
        val prefs = context.getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
        val gridScale = prefs.getInt("graphics_turbulence_grid", 10) / 10f
        val speedScale = prefs.getInt("graphics_turbulence_speed", 10) / 10f
        val continuous = prefs.getBoolean("graphics_turbulence_music_continuous", false)
        updateTurbulenceLiveConfig(gridScale, speedScale, continuous)
    }

    val isTurbulencePlaying: Boolean
        get() = renderer.isTurbulencePlaying()

    /** Used by periodic gradient refreshes so they cannot replace an active reveal. */
    val isBackgroundTransitionRunning: Boolean
        get() = pendingSurfaceTransition != null || renderer.hasBackgroundTransition()

    private var currentRenderScale: Float = 1.0f

    /**
     * Установка коэффициента масштабирования рендеринга (оптимизация).
     */
    fun setRenderScale(scale: Float) {
        val safeScale = scale.coerceIn(0.1f, 1.0f)
        if (currentRenderScale != safeScale) {
            currentRenderScale = safeScale
            applyRenderScale()
            requestRender()
        }
    }

    private fun applyRenderScale() {
        post {
            holder.setSizeFromLayout()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyRenderScale()
    }

    override fun onPause() {
        isPaused = true
        transitionAnimator?.end()
        transitionVersion++
        pendingSurfaceTransition = null
        queueEvent {
            renderer.cancelCompositeLoading()
            renderer.setTransitionProgress(1f)
            renderer.isSurfaceCreated = false
        }
        fadeAnimator?.cancel()
        backgroundZoomAnimator?.cancel()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
    }

    private class Particle {
        var x = 0f; var y = 0f
        var speedX = 0f; var speedY = 0f
        var size = 0f; var scaleX = 1f
        var r = 1f; var g = 1f; var b = 1f; var alpha = 1f; var baseAlpha = 1f
        var angle = 0f; var alphaSpeed = 0f
        var active = false

        fun reset(w: Int, h: Int, state: BackgroundRenderer.WeatherState) {
            val windOffset = abs(state.windFactor * h)
            scaleX = 1f; r = 1f; g = 1f; b = 1f

            when (state.type) {
                WeatherType.RAIN, WeatherType.THUNDERSTORM -> {
                    x = Random.nextFloat() * (w + windOffset * 2) - windOffset
                    y = -Random.nextFloat() * h * 0.5f
                    speedY = (30f + Random.nextFloat() * 15f) * (0.8f + state.intensity * 0.2f)
                    size = 20f + Random.nextFloat() * 30f
                    baseAlpha = 0.15f + Random.nextFloat() * 0.3f
                    alpha = baseAlpha
                    r = 0.8f; g = 0.9f
                }
                WeatherType.SNOW -> {
                    x = Random.nextFloat() * (w + windOffset * 2) - windOffset
                    y = -Random.nextFloat() * h * 0.5f
                    speedY = 2f + Random.nextFloat() * 4f
                    size = 8f + Random.nextFloat() * 12f
                    baseAlpha = 0.6f + Random.nextFloat() * 0.4f
                    alpha = baseAlpha
                    angle = Random.nextFloat() * 360f
                }
                WeatherType.FOG -> {
                    x = Random.nextFloat() * w; y = Random.nextFloat() * h
                    speedX = (Random.nextFloat() - 0.5f) * 0.1f + (state.windFactor * 0.02f)
                    speedY = (Random.nextFloat() - 0.5f) * 0.05f
                    size = 300f + Random.nextFloat() * 400f
                    scaleX = 1.2f + Random.nextFloat() * 0.8f
                    baseAlpha = 0.03f + Random.nextFloat() * 0.06f
                    alpha = baseAlpha
                }
                WeatherType.CLOUDY -> {
                    x = Random.nextFloat() * w
                    y = Random.nextFloat() * h * 0.7f
                    speedX = (state.windFactor * 0.08f) + (Random.nextFloat() - 0.5f) * 0.15f
                    speedY = (Random.nextFloat() - 0.5f) * 0.02f
                    size = 400f + Random.nextFloat() * 600f
                    scaleX = 1.3f + Random.nextFloat() * 1.0f
                    baseAlpha = 0.02f + Random.nextFloat() * 0.04f
                    alpha = baseAlpha
                }
                WeatherType.CLEAR -> {
                    if (state.isNight) {
                        x = Random.nextFloat() * w; y = Random.nextFloat() * h
                        speedX = 0f; speedY = 0f
                        size = 3f + Random.nextFloat() * 6f
                        baseAlpha = Random.nextFloat()
                        alpha = baseAlpha; angle = Random.nextFloat() * 360f
                        alphaSpeed = 0.02f + Random.nextFloat() * 0.03f
                    } else {
                        x = Random.nextFloat() * w; y = Random.nextFloat() * h
                        speedX = (Random.nextFloat() - 0.5f) * 0.4f
                        speedY = (Random.nextFloat() - 0.5f) * 0.4f
                        size = 40f + Random.nextFloat() * 120f
                        baseAlpha = 0.08f + Random.nextFloat() * 0.15f
                        alpha = baseAlpha;
                        angle = Random.nextFloat() * 360f
                        alphaSpeed = 0.01f + Random.nextFloat() * 0.02f
                        r = 1.0f; g = 0.95f; b = 0.8f
                    }
                }
                else -> {}
            }
            active = true
        }

        fun isVisible(w: Int, h: Int): Boolean {
            val margin = size * scaleX
            return x > -margin && x < w + margin && y > -margin && y < h + margin
        }
    }

    private inner class BackgroundRenderer : Renderer {
        private var program = 0
        private var backgroundZoom = 1f
        private var compositeLoadingProgram = 0
        private val sparkleSwipe = SparkleSwipeEffect(::createProgram)

        private var positionHandle = 0
        private var texCoordHandle = 0
        private var uvTransformCurrentHandle = 0
        private var uvTransformNextHandle = 0

        private var texCurrentHandle = 0
        private var texNextHandle = 0
        private var progressHandle = 0
        private var globalAlphaHandle = 0
        private var colorMatrixHandle = 0
        private var colorOffsetHandle = 0

        private var compPositionHandle = 0
        private var compTexCoordHandle = 0
        private var compUvTransformCurrentHandle = 0
        private var compTexCurrentHandle = 0
        private var compUvTransformNextHandle = 0
        private var compTexNextHandle = 0
        private var compProgressHandle = 0
        private var compRevealHandle = 0
        private var compBlurTextureHandle = 0
        private var compHasBlurHandle = 0
        private val compositeFadeInterpolator = android.view.animation.PathInterpolator(0f, 0f, 0f, 1f)
        private val compositeRevealInterpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
        private val compositeBlur = CompositeBackgroundBlur(::createProgram)
        private val compositeLoadingTimeline = CompositeLoadingTimeline()
        private var compGlobalAlphaHandle = 0
        private var compColorMatrixHandle = 0
        private var compColorOffsetHandle = 0
        private var compTimeHandle = 0
        private var compNoiseMoveHandle = 0
        private var compGridNumHandle = 0
        private var compScreenSizeHandle = 0
        private var compAspectRatioHandle = 0
        private var compPixelDensityHandle = 0
        private var compAccentColorHandle = 0
        private var compScreenColorHandle = 0

        @Volatile private var activeTransitionType = TransitionType.LIQUID_CHROMATIC
        private var transitionTimeSeed = 0f
        private var compositeStartTime = 0L
        private var compAccentR = 0.35f
        private var compAccentG = 0.44f
        private var compAccentB = 0.52f
        private var compAccentA = 1.0f
        private var compScreenR = 0.08f
        private var compScreenG = 0.11f
        private var compScreenB = 0.14f
        private var compScreenA = 1.0f

        private var screenProgram = 0
        private var screenPositionHandle = 0
        private var screenTexCoordHandle = 0
        private var screenTextureHandle = 0
        private var screenAlphaHandle = 0

        @Volatile var areAnimationsPaused: Boolean = false

        @Volatile private var textureCurrent = 0
        @Volatile var texCurrentW = 0
        @Volatile var texCurrentH = 0
        @Volatile private var isCurrentGradient = false

        @Volatile private var textureNext = 0
        @Volatile var texNextW = 0
        @Volatile var texNextH = 0
        @Volatile private var isNextGradient = false

        private var dummyTextureId = 0
        private var viewWidth = 0
        private var viewHeight = 0
        private var lastWeatherW = 0
        private var lastWeatherH = 0

        @Volatile var isSurfaceCreated = false

        @Volatile private var transitionProgress = 0f
        @Volatile private var globalAlpha = 1.0f

        @Volatile private var cropScaleCurrent = 1.0f
        @Volatile private var cropOffsetXCurrent = 0f
        @Volatile private var cropOffsetYCurrent = 0f

        @Volatile private var cropScaleNext = 1.0f
        @Volatile private var cropOffsetXNext = 0f
        @Volatile private var cropOffsetYNext = 0f

        private val glColorMatrix = FloatArray(16)
        private val glColorOffset = FloatArray(4)

        private val vertexData = floatArrayOf(
            -1f, -1f, 0f, 0f, 1f,
             1f, -1f, 0f, 1f, 1f,
            -1f,  1f, 0f, 0f, 0f,
             1f,  1f, 0f, 1f, 0f
        )
        private lateinit var vertexBuffer: FloatBuffer

        // --- Weather System Variables ---
        private val maxParticles = 800

        inner class WeatherState {
            var type = WeatherType.NONE
            var intensity = 1.0f
            var targetIntensity = 1.0f
            var windFactor = 0f
            var targetWindFactor = 0f
            var isNight = false
            var dayFactor = 0.5f
            var particles = Array(maxParticles) { Particle() }
        }

        private var currentState = WeatherState()
        private var previousState = WeatherState()

        private var isTransitioning = false
        private var transitionStartTime = 0L
        private val TRANSITION_DURATION_MS = 3500L
        private var lastFrameTime = 0L

        private val projectionMatrix = FloatArray(16)

        private var pointProgram = 0
        private var lineProgram = 0
        private var uPointGlobalAlphaLoc = 0
        private var uLineGlobalAlphaLoc = 0

        private lateinit var pointDataBuffer: FloatBuffer
        private lateinit var lineDataBuffer: FloatBuffer

        private val POINT_STRIDE = 7
        private val LINE_STRIDE = 6

        private var lightningAlpha = 0f
        private var nextLightningFrame = 0

        private var glassRainProgram = 0
        private var rainShowerProgram = 0
        private var snowProgram = 0
        private var fogProgram = 0
        private var sunProgram = 0

        private var uShowerTimeLoc = 0; private var uShowerResLoc = 0; private var uShowerTexLoc = 0
        private var uShowerHasTexLoc = 0; private var uShowerFgTexLoc = 0; private var uShowerHasFgTexLoc = 0
        private var uShowerIntensityLoc = 0; private var uShowerAspectLoc = 0; private var uShowerGlobalAlphaLoc = 0
        private var showerAPos = -1
        private var uShowerWindFactorLoc = 0
        private var uGlassWindFactorLoc = 0
        private val BASE_WIND_FACTOR = 2.0f

        private var uTimeLoc = 0; private var uResLoc = 0; private var uTexLoc = 0; private var uHasTexLoc = 0
        private var uGlassFgTexLoc = 0; private var uGlassHasFgTexLoc = 0; private var uLightningLoc = 0
        private var uIntensityLoc = 0; private var uGlassAspectLoc = 0; private var uGlassGlobalAlphaLoc = 0
        private var glassAPos = -1

        private var uSnowTimeLoc = 0; private var uSnowResLoc = 0; private var uSnowTexLoc = 0
        private var uSnowHasTexLoc = 0; private var uSnowFgTexLoc = 0; private var uSnowHasFgTexLoc = 0
        private var uSnowIntensityLoc = 0; private var uSnowAspectLoc = 0; private var uSnowGlobalAlphaLoc = 0
        private var snowAPos = -1

        private var uFogTimeLoc = 0; private var uFogResLoc = 0; private var uFogTexLoc = 0
        private var uFogHasTexLoc = 0; private var uFogFgTexLoc = 0; private var uFogHasFgTexLoc = 0
        private var uFogIntensityLoc = 0; private var uFogNoiseTexLoc = 0; private var uCloudsNoiseTexLoc = 0
        private var uHasFogTexLoc = 0; private var uFogGlobalAlphaLoc = 0
        private var uFogIsCloudyLoc = 0
        private var fogAPos = -1

        private var uSunTimeLoc = 0; private var uSunResLoc = 0; private var uSunTexLoc = 0
        private var uSunHasTexLoc = 0; private var uSunFgTexLoc = 0; private var uSunHasFgTexLoc = 0
        private var uSunIntensityLoc = 0; private var uSunAspectLoc = 0; private var uSunGlobalAlphaLoc = 0
        private var sunAPos = -1

        private var fogTextureId = -1
        private var cloudsTextureId = -1

        // --- Turbulence System Variables ---
        private var turbulenceProgram = 0
        private var turbAPos = -1
        private var uTurbGridNumLoc = 0
        private var uTurbNoiseMoveLoc = 0
        private var uTurbScreenSizeLoc = 0
        private var uTurbAspectRatioLoc = 0
        private var uTurbOpacityLoc = 0
        private var uTurbInverseLumaLoc = 0
        private var uTurbColorLoc = 0
        private var uTurbGlobalAlphaLoc = 0

        @Volatile private var turbulencePhase = TurbulencePhase.IDLE
        private val turbulenceVisibility = TurbulenceVisibility()
        private val turbulenceColor = TurbulenceColorTransition()
        private var turbColorR = 1f
        private var turbColorG = 1f
        private var turbColorB = 1f
        private var turbColorA = 1f
        private var turbGridCount = 1.0f
        private var turbSpeedX = 0f
        private var turbSpeedY = 0f
        private var turbSpeedZ = 0.45f
        private var turbLuminosity = 1.0f
        private var turbContinuousOpacity = 0.65f
        private var turbContinuousSpeedFactor = 0.35f
        private var turbTransitionDurationMs = 1500L
        private var turbEaseOutStartOpacity = 0f
        private var turbCurrentSpeedFactor = 1.0f
        private var turbIsContinuous = false
        private var turbInverseLuma = false
        private var turbEaseInDurationMs = 400L
        private var turbMainDurationMs = 5000L
        private var turbEaseOutDurationMs = 750L
        private var turbOffsetX = 0f
        private var turbOffsetY = 0f
        private var turbOffsetZ = 0f
        private var turbPhaseStartTime = 0L
        private var turbCurrentOpacity = 0f
        private var onTurbReadyCallback: (() -> Unit)? = null
        private var onTurbEndCallback: (() -> Unit)? = null
        private var turbReadyNotified = false

        private val startTime = System.currentTimeMillis()

        private val quadVertices = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val quadBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(quadVertices); position(0) }

        // --- Framebuffer Object (FBO) for refraction ---
        private var fboId = 0
        private var fboTexId = 0
        private var weatherFboId = 0
        private var weatherFboTexId = 0
        private var fboWidth = 0
        private var fboHeight = 0

        // --- Turbulence FBO (low-resolution offscreen render) ---
        private var turbFboId = 0
        private var turbFboTexId = 0
        private var turbFboW = 0
        private var turbFboH = 0

        init {
            updateColorMatrix(ColorMatrix())
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            isSurfaceCreated = false
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData)
            vertexBuffer.position(0)

            // Reset OpenGL handle properties since the old context is gone
            textureCurrent = 0
            textureNext = 0
            fogTextureId = -1
            cloudsTextureId = -1
            dummyTextureId = 0
            fboId = 0
            fboTexId = 0
            weatherFboId = 0
            weatherFboTexId = 0
            screenProgram = 0
            screenAlphaHandle = 0
            compositeLoadingProgram = 0
            compositeBlur.onContextCreated()
            compositeLoadingTimeline.cancel()
            turbulenceProgram = 0
            turbFboId = 0
            turbFboTexId = 0
            turbFboW = 0
            turbFboH = 0

            // --- Background Shader ---
            val vertexShader = """
                attribute vec4 a_Position;
                attribute vec2 a_TexCoord;
                
                uniform vec4 u_UvTransformCurrent;
                uniform vec4 u_UvTransformNext;
                
                varying vec2 v_TexCoordCurrent;
                varying vec2 v_TexCoordNext;
                varying vec2 v_ScreenUv;
                
                void main() {
                    gl_Position = a_Position;
                    v_TexCoordCurrent = a_TexCoord * u_UvTransformCurrent.xy + u_UvTransformCurrent.zw;
                    v_TexCoordNext = a_TexCoord * u_UvTransformNext.xy + u_UvTransformNext.zw;
                    v_ScreenUv = a_TexCoord;
                }
            """.trimIndent()

            val fragmentShader = """
                precision mediump float;
                
                varying vec2 v_TexCoordCurrent;
                varying vec2 v_TexCoordNext;
                varying vec2 v_ScreenUv;
                
                uniform sampler2D u_TextureCurrent;
                uniform sampler2D u_TextureNext;
                
                uniform float u_Progress;
                uniform float u_GlobalAlpha;
                
                uniform mat4 u_ColorMatrix;
                uniform vec4 u_ColorOffset;
                
                void main() {
                    float p = u_Progress;
                    
                    float zoomCurrent = mix(1.0, 1.05, p);
                    vec2 uvCurrent = (v_TexCoordCurrent - 0.5) / zoomCurrent + 0.5;
                    float zoomNext = mix(0.95, 1.0, p);
                    vec2 uvNext = (v_TexCoordNext - 0.5) / zoomNext + 0.5;
                    
                    float sweepAxis = (v_TexCoordNext.x + v_TexCoordNext.y) * 0.5;
                    float wave = sin(sweepAxis * 10.0 - p * 3.14) * cos(v_TexCoordNext.y * 6.0 + p * 1.57) * 0.08;
                    float waveFront = sweepAxis + wave;
                    
                    float threshold = (1.0 - p) * 1.6 - 0.3; 
                    float mixAlpha = smoothstep(threshold - 0.12, threshold + 0.12, waveFront);
                    
                    float distortStrength = mixAlpha * (1.0 - mixAlpha) * 0.035;
                    vec2 distortCurrent = vec2(sin(uvCurrent.y * 12.0), cos(uvCurrent.x * 12.0)) * distortStrength;
                    vec2 distortNext = vec2(cos(uvNext.y * 12.0), sin(uvNext.x * 12.0)) * distortStrength;
                    
                    vec2 refrCurrent = uvCurrent + distortCurrent;
                    vec2 refrNext = uvNext + distortNext;
                    
                    float chromaOffset = distortStrength * 1.4;
                    
                    vec4 colorCurrent = vec4(
                        texture2D(u_TextureCurrent, refrCurrent + vec2(chromaOffset, 0.0)).r,
                        texture2D(u_TextureCurrent, refrCurrent).g,
                        texture2D(u_TextureCurrent, refrCurrent - vec2(chromaOffset, 0.0)).b,
                        texture2D(u_TextureCurrent, refrCurrent).a
                    );
                    
                    vec4 colorNext = vec4(
                        texture2D(u_TextureNext, refrNext + vec2(chromaOffset, 0.0)).r,
                        texture2D(u_TextureNext, refrNext).g,
                        texture2D(u_TextureNext, refrNext - vec2(chromaOffset, 0.0)).b,
                        texture2D(u_TextureNext, refrNext).a
                    );
                    
                    vec4 blendedColor = mix(colorCurrent, colorNext, mixAlpha);
                    
                    float edgeGlow = smoothstep(0.0, 0.5, mixAlpha) - smoothstep(0.5, 1.0, mixAlpha);
                    vec3 warmFlare = vec3(1.0, 0.55, 0.32) * sin(p * 3.14) * edgeGlow * 0.35;
                    blendedColor.rgb += warmFlare;
                    
                    vec4 transformedColor = (u_ColorMatrix * blendedColor) + u_ColorOffset;
                    transformedColor.a *= u_GlobalAlpha;
                    
                    gl_FragColor = clamp(transformedColor, 0.0, 1.0);
                }
            """.trimIndent()

            program = createProgram(vertexShader, fragmentShader)
            GLES20.glUseProgram(program)

            positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
            texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
            uvTransformCurrentHandle = GLES20.glGetUniformLocation(program, "u_UvTransformCurrent")
            uvTransformNextHandle = GLES20.glGetUniformLocation(program, "u_UvTransformNext")

            texCurrentHandle = GLES20.glGetUniformLocation(program, "u_TextureCurrent")
            texNextHandle = GLES20.glGetUniformLocation(program, "u_TextureNext")
            progressHandle = GLES20.glGetUniformLocation(program, "u_Progress")
            globalAlphaHandle = GLES20.glGetUniformLocation(program, "u_GlobalAlpha")

            colorMatrixHandle = GLES20.glGetUniformLocation(program, "u_ColorMatrix")
            colorOffsetHandle = GLES20.glGetUniformLocation(program, "u_ColorOffset")

            // --- AOSP CompositeLoadingShader + SparkleShader + CircularRevealShader ---
            compositeLoadingProgram = createProgram(vertexShader, COMPOSITE_LOADING_FRAGMENT_SHADER)
            if (compositeLoadingProgram != 0) {
                compPositionHandle = GLES20.glGetAttribLocation(compositeLoadingProgram, "a_Position")
                compTexCoordHandle = GLES20.glGetAttribLocation(compositeLoadingProgram, "a_TexCoord")
                compUvTransformCurrentHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_UvTransformCurrent")
                compTexCurrentHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_TextureCurrent")
                compUvTransformNextHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_UvTransformNext")
                compTexNextHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_TextureNext")
                compProgressHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_Alpha")
                compRevealHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_Reveal")
                compBlurTextureHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_BlurredBackground")
                compHasBlurHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_HasBlur")
                compGlobalAlphaHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_GlobalAlpha")
                compColorMatrixHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_ColorMatrix")
                compColorOffsetHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_ColorOffset")
                compTimeHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_time")
                compNoiseMoveHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_noiseMove")
                compGridNumHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_gridNum")
                compScreenSizeHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_screenSize")
                compAspectRatioHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_aspectRatio")
                compPixelDensityHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_pixelDensity")
                compAccentColorHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_accentColor")
                compScreenColorHandle = GLES20.glGetUniformLocation(compositeLoadingProgram, "u_screenColor")
            }

            sparkleSwipe.onContextCreated(vertexShader)
            dummyTextureId = createDummyTexture()

            // --- Weather System Initialization ---
            pointDataBuffer = ByteBuffer.allocateDirect(maxParticles * POINT_STRIDE * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()
            lineDataBuffer = ByteBuffer.allocateDirect(maxParticles * 2 * LINE_STRIDE * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer()

            pointProgram = createProgram(POINT_VERTEX_SHADER, POINT_FRAGMENT_SHADER)
            uPointGlobalAlphaLoc = GLES20.glGetUniformLocation(pointProgram, "u_globalAlpha")

            lineProgram = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER)
            uLineGlobalAlphaLoc = GLES20.glGetUniformLocation(lineProgram, "u_globalAlpha")

            // --- GLASS RAIN ---
            glassRainProgram = createProgram(GLASS_RAIN_VERTEX_SHADER, GLASS_RAIN_FRAGMENT_SHADER)
            uTimeLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_time")
            uResLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_screenSize")
            uTexLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_texture")
            uHasTexLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_hasTexture")
            uGlassFgTexLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_foregroundTex")
            uGlassHasFgTexLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_hasForegroundTex")
            uIntensityLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_intensity")
            uLightningLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_lightningAlpha")
            uGlassAspectLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_screenAspectRatio")
            uGlassGlobalAlphaLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_globalAlpha")
            uGlassWindFactorLoc = GLES20.glGetUniformLocation(glassRainProgram, "u_windFactor")
            glassAPos = GLES20.glGetAttribLocation(glassRainProgram, "a_Position")

            // --- RAIN SHOWER ---
            rainShowerProgram = createProgram(RAIN_SHOWER_VERTEX_SHADER, RAIN_SHOWER_FRAGMENT_SHADER)
            uShowerTimeLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_time")
            uShowerResLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_screenSize")
            uShowerTexLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_texture")
            uShowerHasTexLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_hasTexture")
            uShowerFgTexLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_foregroundTex")
            uShowerHasFgTexLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_hasForegroundTex")
            uShowerIntensityLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_intensity")
            uShowerAspectLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_screenAspectRatio")
            uShowerGlobalAlphaLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_globalAlpha")
            uShowerWindFactorLoc = GLES20.glGetUniformLocation(rainShowerProgram, "u_windFactor")
            showerAPos = GLES20.glGetAttribLocation(rainShowerProgram, "a_Position")

            // --- SNOW ---
            snowProgram = createProgram(SNOW_VERTEX_SHADER, SNOW_FRAGMENT_SHADER)
            uSnowTimeLoc = GLES20.glGetUniformLocation(snowProgram, "u_time")
            uSnowResLoc = GLES20.glGetUniformLocation(snowProgram, "u_screenSize")
            uSnowTexLoc = GLES20.glGetUniformLocation(snowProgram, "u_texture")
            uSnowHasTexLoc = GLES20.glGetUniformLocation(snowProgram, "u_hasTexture")
            uSnowFgTexLoc = GLES20.glGetUniformLocation(snowProgram, "u_foregroundTex")
            uSnowHasFgTexLoc = GLES20.glGetUniformLocation(snowProgram, "u_hasForegroundTex")
            uSnowIntensityLoc = GLES20.glGetUniformLocation(snowProgram, "u_intensity")
            uSnowAspectLoc = GLES20.glGetUniformLocation(snowProgram, "u_screenAspectRatio")
            uSnowGlobalAlphaLoc = GLES20.glGetUniformLocation(snowProgram, "u_globalAlpha")
            snowAPos = GLES20.glGetAttribLocation(snowProgram, "a_Position")

            // --- FOG ---
            fogProgram = createProgram(FOG_VERTEX_SHADER, FOG_FRAGMENT_SHADER)
            uFogTimeLoc = GLES20.glGetUniformLocation(fogProgram, "u_time")
            uFogResLoc = GLES20.glGetUniformLocation(fogProgram, "u_screenSize")
            uFogTexLoc = GLES20.glGetUniformLocation(fogProgram, "u_texture")
            uFogHasTexLoc = GLES20.glGetUniformLocation(fogProgram, "u_hasTexture")
            uFogFgTexLoc = GLES20.glGetUniformLocation(fogProgram, "u_foregroundTex")
            uFogHasFgTexLoc = GLES20.glGetUniformLocation(fogProgram, "u_hasForegroundTex")
            uFogIntensityLoc = GLES20.glGetUniformLocation(fogProgram, "u_intensity")
            uFogNoiseTexLoc = GLES20.glGetUniformLocation(fogProgram, "u_fogTex")
            uCloudsNoiseTexLoc = GLES20.glGetUniformLocation(fogProgram, "u_cloudsTex")
            uHasFogTexLoc = GLES20.glGetUniformLocation(fogProgram, "u_hasFogTex")
            uFogGlobalAlphaLoc = GLES20.glGetUniformLocation(fogProgram, "u_globalAlpha")
            uFogIsCloudyLoc = GLES20.glGetUniformLocation(fogProgram, "u_isCloudy")
            fogAPos = GLES20.glGetAttribLocation(fogProgram, "a_Position")

            // --- SUN (CLEAR DAY) ---
            sunProgram = createProgram(SUN_VERTEX_SHADER, SUN_FRAGMENT_SHADER)
            uSunTimeLoc = GLES20.glGetUniformLocation(sunProgram, "u_time")
            uSunResLoc = GLES20.glGetUniformLocation(sunProgram, "u_screenSize")
            uSunTexLoc = GLES20.glGetUniformLocation(sunProgram, "u_texture")
            uSunHasTexLoc = GLES20.glGetUniformLocation(sunProgram, "u_hasTexture")
            uSunFgTexLoc = GLES20.glGetUniformLocation(sunProgram, "u_foregroundTex")
            uSunHasFgTexLoc = GLES20.glGetUniformLocation(sunProgram, "u_hasForegroundTex")
            uSunIntensityLoc = GLES20.glGetUniformLocation(sunProgram, "u_intensity")
            uSunAspectLoc = GLES20.glGetUniformLocation(sunProgram, "u_screenAspectRatio")
            uSunGlobalAlphaLoc = GLES20.glGetUniformLocation(sunProgram, "u_globalAlpha")
            sunAPos = GLES20.glGetAttribLocation(sunProgram, "a_Position")

            // --- Upscale Screen Shader ---
            val screenVertexShader = """
                attribute vec4 a_Position;
                attribute vec2 a_TexCoord;
                varying vec2 v_TexCoord;
                void main() {
                    gl_Position = a_Position;
                    v_TexCoord = vec2(a_TexCoord.x, 1.0 - a_TexCoord.y);
                }
            """.trimIndent()

            val screenFragmentShader = """
                precision mediump float;
                varying vec2 v_TexCoord;
                uniform sampler2D u_Texture;
                uniform float u_Alpha;
                void main() {
                    gl_FragColor = texture2D(u_Texture, v_TexCoord) * u_Alpha;
                }
            """.trimIndent()

            screenProgram = createProgram(screenVertexShader, screenFragmentShader)
            screenPositionHandle = GLES20.glGetAttribLocation(screenProgram, "a_Position")
            screenTexCoordHandle = GLES20.glGetAttribLocation(screenProgram, "a_TexCoord")
            screenTextureHandle = GLES20.glGetUniformLocation(screenProgram, "u_Texture")
            screenAlphaHandle = GLES20.glGetUniformLocation(screenProgram, "u_Alpha")

            // --- TURBULENCE ---
            turbulenceProgram = createProgram(TURBULENCE_VERTEX_SHADER, TURBULENCE_FRAGMENT_SHADER)
            turbAPos = GLES20.glGetAttribLocation(turbulenceProgram, "a_Position")
            uTurbGridNumLoc = GLES20.glGetUniformLocation(turbulenceProgram, "u_gridNum")
            uTurbNoiseMoveLoc = GLES20.glGetUniformLocation(turbulenceProgram, "u_noiseMove")
            uTurbScreenSizeLoc = GLES20.glGetUniformLocation(turbulenceProgram, "u_screenSize")
            uTurbAspectRatioLoc = GLES20.glGetUniformLocation(turbulenceProgram, "u_aspectRatio")
            uTurbOpacityLoc = GLES20.glGetUniformLocation(turbulenceProgram, "u_opacity")
            uTurbInverseLumaLoc = GLES20.glGetUniformLocation(turbulenceProgram, "u_inverseLuma")
            uTurbColorLoc = GLES20.glGetUniformLocation(turbulenceProgram, "u_color")
            uTurbGlobalAlphaLoc = GLES20.glGetUniformLocation(turbulenceProgram, "u_globalAlpha")

            isSurfaceCreated = true

            // A first image queued before EGL initialization still needs its transition.
            if (pendingSurfaceTransition != null) {
                queueEvent { pendingSurfaceTransition?.let { applyBackgroundTransition(it) } }
            } else currentBitmap?.let { bitmap ->
                val cached = latestBackgroundTransition
                setNextTexture(
                    bitmap,
                    instant = true,
                    isGradient = cached?.isGradient ?: isCurrentGradient,
                    scale = cached?.scale ?: cropScaleCurrent,
                    offsetX = cached?.offsetX ?: cropOffsetXCurrent,
                    offsetY = cached?.offsetY ?: cropOffsetYCurrent
                )
            }

            // Reload fog/clouds textures from cache if present
            if (fogBitmap != null || cloudsBitmap != null) {
                loadFogTextures(fogBitmap, cloudsBitmap)
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            viewWidth = width
            viewHeight = height
            Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
            requestRender()
        }

        private fun isSparkleSwipeActive(): Boolean =
            activeTransitionType == TransitionType.SPARKLE_SWIPE && textureNext != 0 && sparkleSwipe.isAvailable

        private fun isCompositeTransitionActive(): Boolean =
            activeTransitionType == TransitionType.COMPOSITE_LOADING &&
                textureCurrent != 0 && compositeLoadingProgram != 0 &&
                (textureNext != 0 || compositeLoadingTimeline.isWaitingForImage)

        private fun compositeFadeAlpha(): Float {
            val fadeProgress = (transitionProgress / CompositeLoadingTimeline.FADE_FRACTION)
                .coerceIn(0f, 1f)
            return CompositeLoadingTimeline.fadeAlpha(
                fadeProgress, compositeFadeInterpolator.getInterpolation(fadeProgress))
        }

        private fun drawBackgroundQuad() {
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            val useCompositeLoading = isCompositeTransitionActive()

            val cropCurrent = calculateCenterCrop(
                texCurrentW, texCurrentH, viewWidth, viewHeight,
                isCurrentGradient,
                cropScaleCurrent, cropOffsetXCurrent, cropOffsetYCurrent
            )
            val cropNext = if (textureNext == 0) cropCurrent else calculateCenterCrop(
                texNextW, texNextH, viewWidth, viewHeight,
                isNextGradient,
                cropScaleNext, cropOffsetXNext, cropOffsetYNext
            )

            val activeCurrent = if (textureCurrent != 0) textureCurrent else dummyTextureId
            val activeNext = if (textureNext != 0) textureNext else activeCurrent

            if (useCompositeLoading) {
                // LoadingAnimation: 1100 ms fade, then 3600 ms circular reveal. Stretch
                // both phases proportionally when the caller requests a longer transition.
                val fadeFraction = CompositeLoadingTimeline.FADE_FRACTION
                val alpha = compositeFadeAlpha()
                val reveal = compositeRevealInterpolator.getInterpolation(
                    ((transitionProgress - fadeFraction) / (1f - fadeFraction)).coerceIn(0f, 1f))
                val blurred = compositeBlur.render(activeCurrent, cropCurrent, viewWidth, viewHeight,
                    maxOf(80f * alpha, 1f) * resources.displayMetrics.density)
                GLES20.glUseProgram(compositeLoadingProgram)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurred)
                GLES20.glUniform1i(compBlurTextureHandle, 2)
                GLES20.glUniform1f(compHasBlurHandle, if (blurred != 0) 1f else 0f)

                vertexBuffer.position(0)
                GLES20.glVertexAttribPointer(compPositionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
                GLES20.glEnableVertexAttribArray(compPositionHandle)

                vertexBuffer.position(3)
                GLES20.glVertexAttribPointer(compTexCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
                GLES20.glEnableVertexAttribArray(compTexCoordHandle)

                GLES20.glUniform4fv(compUvTransformCurrentHandle, 1, cropCurrent, 0)
                GLES20.glUniform4fv(compUvTransformNextHandle, 1, cropNext, 0)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeCurrent)
                GLES20.glUniform1i(compTexCurrentHandle, 0)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeNext)
                GLES20.glUniform1i(compTexNextHandle, 1)

                GLES20.glUniform1f(compProgressHandle, alpha)
                GLES20.glUniform1f(compRevealHandle, reveal)
                GLES20.glUniform1f(compGlobalAlphaHandle, globalAlpha)
                GLES20.glUniformMatrix4fv(compColorMatrixHandle, 1, false, glColorMatrix, 0)
                GLES20.glUniform4fv(compColorOffsetHandle, 1, glColorOffset, 0)

                val time = transitionTimeSeed + (android.os.SystemClock.uptimeMillis() - compositeStartTime) / 1000f
                val noiseOffset = time * COMP_NOISE_SPEED
                // Evaluate masks in view pixels even when the weather pipeline renders to a
                // smaller FBO. This preserves AOSP's 0.8 dp sparkle cells and noise pattern.
                val effectiveDensity = resources.displayMetrics.density

                GLES20.glUniform1f(compTimeHandle, time)
                GLES20.glUniform3f(compNoiseMoveHandle, noiseOffset, 0f, noiseOffset)
                GLES20.glUniform1f(compGridNumHandle, COMP_NOISE_SIZE)
                GLES20.glUniform2f(compScreenSizeHandle, viewWidth.toFloat(), viewHeight.toFloat())
                GLES20.glUniform1f(compAspectRatioHandle, viewWidth.toFloat() / maxOf(viewHeight, 1))
                GLES20.glUniform1f(compPixelDensityHandle, effectiveDensity)
                GLES20.glUniform3f(compAccentColorHandle, compAccentR, compAccentG, compAccentB)
                GLES20.glUniform3f(compScreenColorHandle, compScreenR, compScreenG, compScreenB)
            } else if (isSparkleSwipeActive()) {
                sparkleSwipe.draw(vertexBuffer, activeCurrent, activeNext, cropCurrent, cropNext,
                    transitionProgress,
                    transitionTimeSeed + (android.os.SystemClock.uptimeMillis() - compositeStartTime) / 1000f,
                    viewWidth, viewHeight, resources.displayMetrics.density,
                    compAccentR, compAccentG, compAccentB, globalAlpha, glColorMatrix, glColorOffset)
                return
            } else {
                GLES20.glUseProgram(program)

                vertexBuffer.position(0)
                GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
                GLES20.glEnableVertexAttribArray(positionHandle)

                vertexBuffer.position(3)
                GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
                GLES20.glEnableVertexAttribArray(texCoordHandle)

                GLES20.glUniform4fv(uvTransformCurrentHandle, 1, cropCurrent, 0)
                GLES20.glUniform4fv(uvTransformNextHandle, 1, cropNext, 0)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeCurrent)
                GLES20.glUniform1i(texCurrentHandle, 0)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeNext)
                GLES20.glUniform1i(texNextHandle, 1)

                GLES20.glUniform1f(progressHandle, transitionProgress)
                GLES20.glUniform1f(globalAlphaHandle, globalAlpha)

                GLES20.glUniformMatrix4fv(colorMatrixHandle, 1, false, glColorMatrix, 0)
                GLES20.glUniform4fv(colorOffsetHandle, 1, glColorOffset, 0)
            }

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (program == 0) return

            val limit = maxFps
            if (limit > 0) {
                val elapsed = System.currentTimeMillis() - lastFrameTime
                val targetFrameTime = 1000L / limit
                if (elapsed < targetFrameTime) {
                    try {
                        Thread.sleep(targetFrameTime - elapsed)
                    } catch (e: InterruptedException) {
                        // ignore
                    }
                }
            }

            val startTime = if (performanceTracker.isEnabled) System.nanoTime() else 0L

            val now = System.currentTimeMillis()
            val dt = if (lastFrameTime == 0L) 0f else (now - lastFrameTime) / 1000f
            lastFrameTime = now

            var needsContinuousRender = false
            if (compositeLoadingTimeline.isWaitingForImage) {
                transitionProgress = compositeLoadingTimeline.progress(android.os.SystemClock.uptimeMillis())
                needsContinuousRender = true
            }
            if (currentState.type != WeatherType.NONE) {
                val diffIntens = currentState.targetIntensity - currentState.intensity
                val diffWind = currentState.targetWindFactor - currentState.windFactor

                if (Math.abs(diffIntens) > 0.005f || Math.abs(diffWind) > 0.005f) {
                    val lerpAlpha = (1.0f - Math.exp(-dt / 0.5)).toFloat()
                    currentState.intensity += diffIntens * lerpAlpha
                    currentState.windFactor += diffWind * lerpAlpha
                    needsContinuousRender = true
                } else {
                    currentState.intensity = currentState.targetIntensity
                    currentState.windFactor = currentState.targetWindFactor
                }
            }

            if (turbulencePhase != TurbulencePhase.IDLE) {
                needsContinuousRender = true
                val tint = turbulenceColor.colorAt(android.os.SystemClock.uptimeMillis())
                turbColorR = android.graphics.Color.red(tint) / 255f
                turbColorG = android.graphics.Color.green(tint) / 255f
                turbColorB = android.graphics.Color.blue(tint) / 255f
                turbColorA = android.graphics.Color.alpha(tint) / 255f

                val elapsed = now - turbPhaseStartTime
                when (turbulencePhase) {
                    TurbulencePhase.EASE_IN -> {
                        turbCurrentSpeedFactor = 1.0f
                        val progress = if (turbEaseInDurationMs > 0) (elapsed / turbEaseInDurationMs.toFloat()).coerceIn(0f, 1f) else 1f
                        turbCurrentOpacity = progress * turbLuminosity
                        if (!turbReadyNotified && (elapsed >= minOf(300L, turbEaseInDurationMs) || elapsed >= turbEaseInDurationMs)) {
                            turbReadyNotified = true
                            val cb = onTurbReadyCallback
                            if (cb != null) post { cb() }
                        }
                        if (elapsed >= turbEaseInDurationMs) {
                            turbulencePhase = TurbulencePhase.MAIN
                            turbPhaseStartTime = now
                        }
                    }
                    TurbulencePhase.MAIN -> {
                        turbCurrentSpeedFactor = 1.0f
                        turbCurrentOpacity = turbLuminosity
                        if (!turbReadyNotified) {
                            turbReadyNotified = true
                            val cb = onTurbReadyCallback
                            if (cb != null) post { cb() }
                        }
                        if (turbMainDurationMs > 0 && elapsed >= turbMainDurationMs) {
                            if (turbIsContinuous) {
                                turbulencePhase = TurbulencePhase.TRANSITION_TO_CONTINUOUS
                                turbPhaseStartTime = now
                            } else {
                                turbEaseOutStartOpacity = turbCurrentOpacity
                                turbulencePhase = TurbulencePhase.EASE_OUT
                                turbPhaseStartTime = now
                            }
                        }
                    }
                    TurbulencePhase.TRANSITION_TO_CONTINUOUS -> {
                        val progress = if (turbTransitionDurationMs > 0) (elapsed / turbTransitionDurationMs.toFloat()).coerceIn(0f, 1f) else 1f
                        turbCurrentOpacity = turbLuminosity + (turbContinuousOpacity - turbLuminosity) * progress
                        turbCurrentSpeedFactor = 1.0f + (turbContinuousSpeedFactor - 1.0f) * progress
                        if (elapsed >= turbTransitionDurationMs) {
                            turbulencePhase = TurbulencePhase.CONTINUOUS
                            turbCurrentOpacity = turbContinuousOpacity
                            turbCurrentSpeedFactor = turbContinuousSpeedFactor
                            turbPhaseStartTime = now
                        }
                    }
                    TurbulencePhase.CONTINUOUS -> {
                        turbCurrentOpacity = turbContinuousOpacity
                        turbCurrentSpeedFactor = turbContinuousSpeedFactor
                    }
                    TurbulencePhase.EASE_OUT -> {
                        val progress = if (turbEaseOutDurationMs > 0) (elapsed / turbEaseOutDurationMs.toFloat()).coerceIn(0f, 1f) else 1f
                        turbCurrentOpacity = (1f - progress) * turbEaseOutStartOpacity
                        if (elapsed >= turbEaseOutDurationMs) {
                            turbulencePhase = TurbulencePhase.IDLE
                            turbCurrentOpacity = 0f
                            deleteTurbFBO()
                            val cb = onTurbEndCallback
                            if (cb != null) post { cb() }
                        }
                    }
                    TurbulencePhase.IDLE -> {}
                }

                turbOffsetX += dt * turbSpeedX * turbCurrentSpeedFactor
                turbOffsetY += dt * turbSpeedY * turbCurrentSpeedFactor
                turbOffsetZ += dt * turbSpeedZ * turbCurrentSpeedFactor
            }

            val hasWeather = currentState.type != WeatherType.NONE || isTransitioning

            val finalRenderScale = if (hasWeather) minOf(currentRenderScale, weatherResolutionScale) else currentRenderScale
            val useFbo = finalRenderScale < 0.99f || hasWeather

            val targetFboWidth = (viewWidth * finalRenderScale).toInt().coerceAtLeast(64)
            val targetFboHeight = (viewHeight * finalRenderScale).toInt().coerceAtLeast(64)

            // 1. Draw Background to FBO 1 if useFbo is true
            if (useFbo) {
                if (fboId == 0 || fboWidth != targetFboWidth || fboHeight != targetFboHeight) {
                    createFBO(targetFboWidth, targetFboHeight)
                }
                if (fboId != 0) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
                    GLES20.glViewport(0, 0, targetFboWidth, targetFboHeight)
                } else {
                    // creation failed — fall back to direct full-res render
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                    GLES20.glViewport(0, 0, viewWidth, viewHeight)
                }
            } else {
                if (fboId != 0) deleteFBO()
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glViewport(0, 0, viewWidth, viewHeight)
            }

            // Keep this frame's overlay policy even if drawing commits the transition.
            val compositeInFrame = isCompositeTransitionActive()
            val sparkleInFrame = isSparkleSwipeActive()
            val turbulenceMask = if (compositeInFrame) {
                1f - compositeFadeAlpha()
            } else if (sparkleInFrame) {
                (1f - transitionProgress / 0.15f).coerceIn(0f, 1f)
            } else null
            val turbulenceHandoff = turbulenceVisibility.update(
                turbulenceMask, android.os.SystemClock.uptimeMillis())
            if ((compositeInFrame || sparkleInFrame) && turbulenceMask == 0f && !turbIsContinuous &&
                turbulencePhase != TurbulencePhase.IDLE) cancelTurbulence()
            drawBackgroundQuad()

            if (transitionProgress >= 1.0f && textureNext != 0) {
                commitTransition()
            }

            // 2. Render weather or upscale
            if (useFbo) {
                if (hasWeather) {
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, weatherFboId)
                    // Viewport is still targetFboWidth / targetFboHeight
                    renderWeather(fboTexId, targetFboWidth, targetFboHeight)

                    // Determine transition alpha for the FBO screen composition pass
                    val isTransitioningToOrFromNone = isTransitioning && 
                            (previousState.type == WeatherType.NONE || currentState.type == WeatherType.NONE)
                    
                    var screenAlpha = 1.0f
                    if (isTransitioningToOrFromNone) {
                        val elapsed = System.currentTimeMillis() - transitionStartTime
                        var progress = elapsed / TRANSITION_DURATION_MS.toFloat()
                        if (progress > 1.0f) progress = 1.0f
                        val easeProgress = progress * progress * (3f - 2f * progress)
                        screenAlpha = if (currentState.type != WeatherType.NONE) easeProgress else (1.0f - easeProgress)
                    }

                    // If transitioning to/from NONE, draw the clean full-res background directly to the screen first
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                    GLES20.glViewport(0, 0, viewWidth, viewHeight)

                    if (isTransitioningToOrFromNone) {
                        drawBackgroundQuad()
                    } else {
                        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                    }

                    // Upscale FBO composite texture on top of the screen with screenAlpha blending
                    GLES20.glUseProgram(screenProgram)

                    vertexBuffer.position(0)
                    GLES20.glVertexAttribPointer(screenPositionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
                    GLES20.glEnableVertexAttribArray(screenPositionHandle)

                    vertexBuffer.position(3)
                    GLES20.glVertexAttribPointer(screenTexCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
                    GLES20.glEnableVertexAttribArray(screenTexCoordHandle)

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, weatherFboTexId)
                    GLES20.glUniform1i(screenTextureHandle, 0)
                    GLES20.glUniform1f(screenAlphaHandle, screenAlpha)

                    // Enable blending and set correct blend mode for premultiplied FBO upscale pass
                    GLES20.glEnable(GLES20.GL_BLEND)
                    GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                } else {
                    // No weather, just upscale FBO 1 (background FBO) to the screen
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                    GLES20.glViewport(0, 0, viewWidth, viewHeight)

                    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                    GLES20.glUseProgram(screenProgram)

                    vertexBuffer.position(0)
                    GLES20.glVertexAttribPointer(screenPositionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
                    GLES20.glEnableVertexAttribArray(screenPositionHandle)

                    vertexBuffer.position(3)
                    GLES20.glVertexAttribPointer(screenTexCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
                    GLES20.glEnableVertexAttribArray(screenTexCoordHandle)

                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexId)
                    GLES20.glUniform1i(screenTextureHandle, 0)
                    GLES20.glUniform1f(screenAlphaHandle, 1.0f)

                    GLES20.glDisable(GLES20.GL_BLEND)

                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                }
            }

            // Keep existing turbulence visible until the composite has faded in.
            // Handoff reaches zero before the circle opens, leaving revealed pixels clean.
            if (turbCurrentOpacity > 0.001f && turbulenceHandoff > 0f) {
                drawTurbulence(viewWidth, viewHeight, turbulenceHandoff)
            }

            // Update renderMode based on active simulation, transition, or interpolation
            val isBgTransitioning = transitionProgress > 0f && transitionProgress < 1f && textureNext != 0
            val hasActiveAnimation = needsContinuousRender || isTransitioning || isBgTransitioning || currentState.type != WeatherType.NONE || turbulencePhase != TurbulencePhase.IDLE
            if (areAnimationsPaused) {
                renderMode = RENDERMODE_WHEN_DIRTY
            } else if (hasActiveAnimation) {
                renderMode = RENDERMODE_CONTINUOUSLY
            } else {
                renderMode = RENDERMODE_WHEN_DIRTY
            }

            if (performanceTracker.isEnabled) {
                //GLES20.glFinish()
                val endTime = System.nanoTime()
                performanceTracker.trackFrame(endTime - startTime)
            }
        }

        private fun renderWeather(backgroundTexId: Int, w: Int, h: Int) {
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            val dimensionsChanged = lastWeatherW != w || lastWeatherH != h
            if (dimensionsChanged) {
                lastWeatherW = w
                lastWeatherH = h
            }

            if (currentState.type == WeatherType.THUNDERSTORM || previousState.type == WeatherType.THUNDERSTORM) {
                updateLightning()
            }

            if (lightningAlpha > 0f) {
                GLES20.glClearColor(0.9f * lightningAlpha, 0.9f * lightningAlpha, 1f * lightningAlpha, lightningAlpha)
                lightningAlpha *= 0.88f
                if (lightningAlpha < 0.01f) lightningAlpha = 0f
            } else {
                GLES20.glClearColor(0f, 0f, 0f, 0f)
            }
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // Pre-fill FBO 2 with 100% background texture first
            drawClearBackground(1.0f, backgroundTexId, w, h)

            if (isTransitioning) {
                val elapsed = System.currentTimeMillis() - transitionStartTime
                var progress = elapsed / TRANSITION_DURATION_MS.toFloat()
                if (progress >= 1f) {
                    progress = 1f
                    isTransitioning = false
                    if (currentState.type == WeatherType.NONE) {
                        renderMode = RENDERMODE_WHEN_DIRTY
                    }
                }

                val easeProgress = progress * progress * (3f - 2f * progress)

                if (previousState.type != WeatherType.NONE) {
                    drawWeatherState(previousState, 1.0f - easeProgress, backgroundTexId, w, h, dimensionsChanged)
                }
                if (currentState.type != WeatherType.NONE) {
                    drawWeatherState(currentState, easeProgress, backgroundTexId, w, h, dimensionsChanged)
                }
            } else {
                if (currentState.type != WeatherType.NONE) {
                    drawWeatherState(currentState, 1.0f, backgroundTexId, w, h, dimensionsChanged)
                }
            }
        }

        private fun drawWeatherState(state: WeatherState, alpha: Float, backgroundTexId: Int, w: Int, h: Int, dimensionsChanged: Boolean) {
            if (alpha <= 0.001f) return

            when (state.type) {
                WeatherType.RAIN, WeatherType.THUNDERSTORM -> {
                    drawRainShower(state.intensity, state.windFactor, alpha, backgroundTexId, w, h)
                    updateAndDrawParticles(state, alpha, w, h, dimensionsChanged)
                    drawGlassRain(state.intensity, state.windFactor, alpha, backgroundTexId, w, h)
                }
                WeatherType.SNOW -> {
                    drawSnowLayer(state.intensity, alpha, backgroundTexId, w, h)
                    updateAndDrawParticles(state, alpha, w, h, dimensionsChanged)
                }
                WeatherType.FOG -> {
                    drawFogLayer(state.intensity, alpha, false, backgroundTexId, w, h, state.dayFactor)
                    updateAndDrawParticles(state, alpha, w, h, dimensionsChanged)
                }
                WeatherType.CLOUDY -> {
                    drawFogLayer(state.intensity, alpha, true, backgroundTexId, w, h, state.dayFactor)
                    updateAndDrawParticles(state, alpha, w, h, dimensionsChanged)
                }
                WeatherType.CLEAR -> {
                    val sunAlpha = alpha * state.dayFactor
                    val nightAlpha = alpha * (1.0f - state.dayFactor)

                    if (sunAlpha > 0.005f) {
                        drawSunLayer(state.intensity, sunAlpha, backgroundTexId, w, h)
                    }
                    if (nightAlpha > 0.005f) {
                        drawClearBackground(nightAlpha, backgroundTexId, w, h)
                    }
                    updateAndDrawParticles(state, alpha, w, h, dimensionsChanged)
                }
                else -> {}
            }
        }

        private fun bindBaseTextures(texLoc: Int, hasTexLoc: Int, fgTexLoc: Int, hasFgTexLoc: Int, backgroundTexId: Int) {
            if (backgroundTexId != -1) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, backgroundTexId)
                GLES20.glUniform1i(texLoc, 0)
                GLES20.glUniform1i(hasTexLoc, 1)
            } else {
                GLES20.glUniform1i(hasTexLoc, 0)
            }

            // Foreground is not used in dynamic background and is kept bound to dummy/0
            GLES20.glUniform1i(hasFgTexLoc, 0)
        }

        private fun drawClearBackground(alpha: Float, backgroundTexId: Int, w: Int, h: Int) {
            if (fogProgram == 0) return
            GLES20.glUseProgram(fogProgram)

            GLES20.glUniform1f(uFogTimeLoc, 0f)
            GLES20.glUniform2f(uFogResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform1f(uFogIntensityLoc, 0f)
            GLES20.glUniform1f(uFogGlobalAlphaLoc, alpha)
            GLES20.glUniform1f(uFogIsCloudyLoc, 0f)

            bindBaseTextures(uFogTexLoc, uFogHasTexLoc, uFogFgTexLoc, uFogHasFgTexLoc, backgroundTexId)
            GLES20.glUniform1i(uHasFogTexLoc, 0)

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(fogAPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glEnableVertexAttribArray(fogAPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(fogAPos)
        }

        private fun drawSunLayer(intensityMult: Float, alpha: Float, backgroundTexId: Int, w: Int, h: Int) {
            if (sunProgram == 0) return
            GLES20.glUseProgram(sunProgram)

            val time = (System.currentTimeMillis() - startTime) / 1000f
            GLES20.glUniform1f(uSunTimeLoc, time)
            GLES20.glUniform2f(uSunResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform1f(uSunIntensityLoc, (intensityMult * 0.8f).coerceIn(0f, 1f))
            GLES20.glUniform1f(uSunAspectLoc, w.toFloat() / h.toFloat())
            GLES20.glUniform1f(uSunGlobalAlphaLoc, alpha)

            bindBaseTextures(uSunTexLoc, uSunHasTexLoc, uSunFgTexLoc, uSunHasFgTexLoc, backgroundTexId)

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(sunAPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glEnableVertexAttribArray(sunAPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(sunAPos)
        }

        private fun drawFogLayer(intensityMult: Float, alpha: Float, isCloudy: Boolean, backgroundTexId: Int, w: Int, h: Int, dayFactor: Float = 0.5f) {
            if (fogProgram == 0) return
            GLES20.glUseProgram(fogProgram)

            val time = (System.currentTimeMillis() - startTime) / 1000f
            GLES20.glUniform1f(uFogTimeLoc, time)
            GLES20.glUniform2f(uFogResLoc, w.toFloat(), h.toFloat())
            val adjustedIntensity = intensityMult.coerceIn(0f, 1f) * (0.35f + 0.65f * dayFactor)
            GLES20.glUniform1f(uFogIntensityLoc, adjustedIntensity)
            GLES20.glUniform1f(uFogGlobalAlphaLoc, alpha)
            GLES20.glUniform1f(uFogIsCloudyLoc, if (isCloudy) 1.0f else 0.0f)

            bindBaseTextures(uFogTexLoc, uFogHasTexLoc, uFogFgTexLoc, uFogHasFgTexLoc, backgroundTexId)

            if (fogTextureId != -1 && cloudsTextureId != -1) {
                GLES20.glUniform1i(uHasFogTexLoc, 1)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fogTextureId)
                GLES20.glUniform1i(uFogNoiseTexLoc, 1)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cloudsTextureId)
                GLES20.glUniform1i(uCloudsNoiseTexLoc, 2)
            } else {
                GLES20.glUniform1i(uHasFogTexLoc, 0)
            }

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(fogAPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glEnableVertexAttribArray(fogAPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(fogAPos)
        }

        private fun drawSnowLayer(intensityMult: Float, alpha: Float, backgroundTexId: Int, w: Int, h: Int) {
            if (snowProgram == 0) return
            GLES20.glUseProgram(snowProgram)

            val time = (System.currentTimeMillis() - startTime) / 1000f
            GLES20.glUniform1f(uSnowTimeLoc, time)
            GLES20.glUniform2f(uSnowResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform1f(uSnowIntensityLoc, intensityMult.coerceIn(0f, 1f))
            GLES20.glUniform1f(uSnowAspectLoc, w.toFloat() / h.toFloat())
            GLES20.glUniform1f(uSnowGlobalAlphaLoc, alpha)

            bindBaseTextures(uSnowTexLoc, uSnowHasTexLoc, uSnowFgTexLoc, uSnowHasFgTexLoc, backgroundTexId)

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(snowAPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glEnableVertexAttribArray(snowAPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(snowAPos)
        }

        private fun drawGlassRain(intensityMult: Float, windFactor: Float, alpha: Float, backgroundTexId: Int, w: Int, h: Int) {
            if (glassRainProgram == 0) return
            GLES20.glUseProgram(glassRainProgram)

            val time = (System.currentTimeMillis() - startTime) / 1000f
            GLES20.glUniform1f(uTimeLoc, time)
            GLES20.glUniform1f(uGlassAspectLoc, w.toFloat() / h.toFloat())
            GLES20.glUniform1f(uIntensityLoc, intensityMult.coerceIn(0f, 1f))
            GLES20.glUniform2f(uResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform1f(uLightningLoc, lightningAlpha)
            GLES20.glUniform1f(uGlassGlobalAlphaLoc, alpha)
            GLES20.glUniform1f(uGlassWindFactorLoc, windFactor)

            bindBaseTextures(uTexLoc, uHasTexLoc, uGlassFgTexLoc, uGlassHasFgTexLoc, backgroundTexId)

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(glassAPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glEnableVertexAttribArray(glassAPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(glassAPos)
        }

        private fun drawRainShower(intensityMult: Float, windFactor: Float, alpha: Float, backgroundTexId: Int, w: Int, h: Int) {
            if (rainShowerProgram == 0) return
            GLES20.glUseProgram(rainShowerProgram)

            val time = (System.currentTimeMillis() - startTime) / 1000f
            GLES20.glUniform1f(uShowerTimeLoc, time)
            GLES20.glUniform2f(uShowerResLoc, w.toFloat(), h.toFloat())
            GLES20.glUniform1f(uShowerIntensityLoc, intensityMult.coerceIn(0f, 1f))
            GLES20.glUniform1f(uShowerAspectLoc, w.toFloat() / h.toFloat())
            GLES20.glUniform1f(uShowerGlobalAlphaLoc, alpha)
            GLES20.glUniform1f(uShowerWindFactorLoc, windFactor)

            bindBaseTextures(uShowerTexLoc, uShowerHasTexLoc, uShowerFgTexLoc, uShowerHasFgTexLoc, backgroundTexId)

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(showerAPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glEnableVertexAttribArray(showerAPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(showerAPos)
        }

        private fun updateAndDrawParticles(state: WeatherState, alpha: Float, w: Int, h: Int, dimensionsChanged: Boolean) {
            val isLineMode = state.type == WeatherType.RAIN || state.type == WeatherType.THUNDERSTORM
            val activeLimit = getActiveLimit(state)

            pointDataBuffer.position(0)
            lineDataBuffer.position(0)

            var drawCount = 0

            for (i in 0 until maxParticles) {
                val p = state.particles[i]
                if (i >= activeLimit) {
                    p.active = false
                    continue
                }

                if (dimensionsChanged) {
                    p.active = false
                }

                if (!p.active) p.reset(w, h, state)
                updateParticle(p, state, w, h)

                if (p.isVisible(w, h)) {
                    drawCount++
                    if (isLineMode) packLineData(p, state, alpha) else packPointData(p, state, alpha)
                }
            }

            if (drawCount > 0) {
                if (isLineMode) renderLines(drawCount, alpha, w, h) else renderPoints(drawCount, alpha, w, h)
            }
        }

        private fun updateParticle(p: Particle, state: WeatherState, w: Int, h: Int) {
            when (state.type) {
                WeatherType.RAIN, WeatherType.THUNDERSTORM -> {
                    val effectiveWind = state.windFactor + BASE_WIND_FACTOR
                    p.x += effectiveWind; p.y += p.speedY
                }
                WeatherType.SNOW -> {
                    p.x += state.windFactor; p.y += p.speedY; p.angle += 0.03f; p.x += sin(p.angle) * 1.5f
                }
                WeatherType.FOG, WeatherType.CLOUDY -> {
                    p.x += p.speedX * state.intensity; p.y += p.speedY * state.intensity
                    val margin = p.size * 2
                    if (p.x > w + margin) p.x = -margin
                    if (p.x < -margin) p.x = w + margin
                }
                WeatherType.CLEAR -> {
                    if (state.isNight) {
                        p.angle += p.alphaSpeed * state.intensity
                        val blink = (sin(p.angle) * 0.5f + 0.5f)
                        p.alpha = (0.2f + (blink * 0.8f)) * p.baseAlpha
                    } else {
                        p.angle += p.alphaSpeed * state.intensity
                        val blink = (sin(p.angle) * 0.5f + 0.5f)
                        p.alpha = (0.4f + (blink * 0.6f)) * p.baseAlpha

                        p.x += p.speedX * state.intensity
                        p.y += p.speedY * state.intensity
                        val margin = p.size
                        if (p.x > w + margin) p.x = -margin
                        if (p.x < -margin) p.x = w + margin
                        if (p.y > h + margin) p.y = -margin
                        if (p.y < -margin) p.y = h + margin
                    }
                }
                else -> {}
            }

            if (p.y > h + p.size) {
                p.reset(w, h, state)
            }
        }

        private fun packPointData(p: Particle, state: WeatherState, alpha: Float) {
            pointDataBuffer.put(p.x).put(p.y).put(p.size * p.scaleX * weatherResolutionScale)
            var finalAlpha = p.alpha * alpha
            if (state.type == WeatherType.CLEAR) {
                if (p.speedX == 0f && p.speedY == 0f) {
                    finalAlpha *= (1.0f - state.dayFactor).coerceIn(0f, 1f)
                } else {
                    finalAlpha *= state.dayFactor.coerceIn(0f, 1f)
                }
            } else if (state.type == WeatherType.FOG || state.type == WeatherType.CLOUDY) {
                finalAlpha *= (0.3f + state.intensity * 0.7f).coerceIn(0f, 1f)
            }
            pointDataBuffer.put(p.r).put(p.g).put(p.b).put(finalAlpha)
        }

        private fun packLineData(p: Particle, state: WeatherState, alpha: Float) {
            val scaledSize = p.size * weatherResolutionScale
            val tailY = p.y - scaledSize
            val effectiveWind = state.windFactor + BASE_WIND_FACTOR
            val horizontalOffset = if (p.speedY != 0f) (effectiveWind / p.speedY) * scaledSize * 0.8f else effectiveWind * 2f * weatherResolutionScale
            val tailX = p.x - horizontalOffset
            lineDataBuffer.put(p.x).put(p.y).put(p.r).put(p.g).put(p.b).put(p.alpha * alpha)
            lineDataBuffer.put(tailX).put(tailY).put(p.r).put(p.g).put(p.b).put(0f)
        }

        private fun renderPoints(count: Int, globalAlpha: Float, w: Int, h: Int) {
            GLES20.glUseProgram(pointProgram)
            pointDataBuffer.position(0)
            
            // Build dynamic ortho matrix for current FBO dimensions
            val weatherProj = FloatArray(16)
            Matrix.orthoM(weatherProj, 0, 0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f)
            
            val uMatrixLoc = GLES20.glGetUniformLocation(pointProgram, "u_Matrix")
            GLES20.glUniformMatrix4fv(uMatrixLoc, 1, false, weatherProj, 0)
            GLES20.glUniform1f(uPointGlobalAlphaLoc, globalAlpha)

            val aPosLoc = GLES20.glGetAttribLocation(pointProgram, "a_Position")
            val aSizeLoc = GLES20.glGetAttribLocation(pointProgram, "a_PointSize")
            val aColorLoc = GLES20.glGetAttribLocation(pointProgram, "a_Color")

            pointDataBuffer.position(0)
            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, POINT_STRIDE * 4, pointDataBuffer)
            GLES20.glEnableVertexAttribArray(aPosLoc)

            pointDataBuffer.position(2)
            GLES20.glVertexAttribPointer(aSizeLoc, 1, GLES20.GL_FLOAT, false, POINT_STRIDE * 4, pointDataBuffer)
            GLES20.glEnableVertexAttribArray(aSizeLoc)

            pointDataBuffer.position(3)
            GLES20.glVertexAttribPointer(aColorLoc, 4, GLES20.GL_FLOAT, false, POINT_STRIDE * 4, pointDataBuffer)
            GLES20.glEnableVertexAttribArray(aColorLoc)

            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)

            GLES20.glDisableVertexAttribArray(aPosLoc)
            GLES20.glDisableVertexAttribArray(aSizeLoc)
            GLES20.glDisableVertexAttribArray(aColorLoc)
        }

        private fun renderLines(count: Int, globalAlpha: Float, w: Int, h: Int) {
            GLES20.glUseProgram(lineProgram)
            lineDataBuffer.position(0)
            
            // Build dynamic ortho matrix for current FBO dimensions
            val weatherProj = FloatArray(16)
            Matrix.orthoM(weatherProj, 0, 0f, w.toFloat(), h.toFloat(), 0f, -1f, 1f)
            
            val uMatrixLoc = GLES20.glGetUniformLocation(lineProgram, "u_Matrix")
            GLES20.glUniformMatrix4fv(uMatrixLoc, 1, false, weatherProj, 0)
            GLES20.glUniform1f(uLineGlobalAlphaLoc, globalAlpha)

            GLES20.glLineWidth(2f * weatherResolutionScale)

            val aPosLoc = GLES20.glGetAttribLocation(lineProgram, "a_Position")
            val aColorLoc = GLES20.glGetAttribLocation(lineProgram, "a_Color")

            lineDataBuffer.position(0)
            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, LINE_STRIDE * 4, lineDataBuffer)
            GLES20.glEnableVertexAttribArray(aPosLoc)

            lineDataBuffer.position(2)
            GLES20.glVertexAttribPointer(aColorLoc, 4, GLES20.GL_FLOAT, false, LINE_STRIDE * 4, lineDataBuffer)
            GLES20.glEnableVertexAttribArray(aColorLoc)

            GLES20.glDrawArrays(GLES20.GL_LINES, 0, count * 2)

            GLES20.glDisableVertexAttribArray(aPosLoc)
            GLES20.glDisableVertexAttribArray(aColorLoc)
        }

        private fun updateLightning() {
            if (nextLightningFrame > 0) {
                nextLightningFrame--
            } else {
                lightningAlpha = 0.15f + Random.nextFloat() * 0.20f
                nextLightningFrame = 150 + Random.nextInt(400)
            }
        }

        private fun getActiveLimit(state: WeatherState): Int {
            return when (state.type) {
                WeatherType.FOG -> (15 + (25 * state.intensity)).toInt().coerceIn(10, 40)
                WeatherType.CLOUDY -> (15 + (25 * state.intensity)).toInt().coerceIn(10, 40)
                WeatherType.CLEAR -> if (state.isNight) (50 + (200 * state.intensity)).toInt().coerceIn(50, 300) else (10 + (20 * state.intensity)).toInt().coerceIn(10, 35)
                else -> (maxParticles * state.intensity).toInt().coerceIn(0, maxParticles)
            }
        }

        private fun createFBO(width: Int, height: Int) {
            deleteFBO()

            val framebuffers = IntArray(2)
            GLES20.glGenFramebuffers(2, framebuffers, 0)
            fboId = framebuffers[0]
            weatherFboId = framebuffers[1]

            val textures = IntArray(2)
            GLES20.glGenTextures(2, textures, 0)
            fboTexId = textures[0]
            weatherFboTexId = textures[1]

            // Setup FBO 1 (Background texture)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTexId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTexId, 0
            )

            var status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                deleteFBO()
                return
            }

            // Setup FBO 2 (Weather composite texture)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, weatherFboTexId)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, weatherFboId)
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, weatherFboTexId, 0
            )

            status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                deleteFBO()
                return
            }

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

            fboWidth = width
            fboHeight = height
        }

        private fun deleteFBO() {
            if (fboId != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
                fboId = 0
            }
            if (fboTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(fboTexId), 0)
                fboTexId = 0
            }
            if (weatherFboId != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(weatherFboId), 0)
                weatherFboId = 0
            }
            if (weatherFboTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(weatherFboTexId), 0)
                weatherFboTexId = 0
            }
            fboWidth = 0
            fboHeight = 0
        }

        fun updateWeatherConfig(
            type: WeatherType,
            intens: Float,
            wind: Float,
            night: Boolean,
            dayFactor: Float = if (!night) 1.0f else 0.0f
        ) {
            val targetDayFactor = dayFactor.coerceIn(0f, 1f)
            if (currentState.type == type && currentState.isNight == night && kotlin.math.abs(currentState.dayFactor - targetDayFactor) < 0.01f) {
                currentState.targetIntensity = intens
                currentState.targetWindFactor = wind
                currentState.dayFactor = targetDayFactor
                if (currentState.type != WeatherType.NONE) {
                    renderMode = RENDERMODE_CONTINUOUSLY
                }
                requestRender()
                return
            }

            previousState.type = currentState.type
            previousState.intensity = currentState.intensity
            previousState.targetIntensity = currentState.intensity
            previousState.windFactor = currentState.windFactor
            previousState.targetWindFactor = currentState.windFactor
            previousState.isNight = currentState.isNight
            previousState.dayFactor = currentState.dayFactor

            val tempParticles = previousState.particles
            previousState.particles = currentState.particles
            currentState.particles = tempParticles

            currentState.type = type
            currentState.intensity = intens
            currentState.targetIntensity = intens
            currentState.windFactor = wind
            currentState.targetWindFactor = wind
            currentState.isNight = night
            currentState.dayFactor = targetDayFactor

            currentState.particles.forEach { it.active = false }

            if (currentState.type == WeatherType.NONE && previousState.type == WeatherType.NONE) {
                isTransitioning = false
                renderMode = RENDERMODE_WHEN_DIRTY
            } else if (previousState.type != WeatherType.NONE || currentState.type != WeatherType.NONE) {
                isTransitioning = true
                transitionStartTime = System.currentTimeMillis()
                renderMode = RENDERMODE_CONTINUOUSLY
            } else {
                isTransitioning = false
                renderMode = RENDERMODE_WHEN_DIRTY
            }
            requestRender()
        }

        fun loadFogTextures(fog: Bitmap?, clouds: Bitmap?) {
            if (fogTextureId != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(fogTextureId), 0)
                fogTextureId = -1
            }
            if (cloudsTextureId != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(cloudsTextureId), 0)
                cloudsTextureId = -1
            }

            if (fog != null) {
                val texIds = IntArray(1)
                GLES20.glGenTextures(1, texIds, 0)
                fogTextureId = texIds[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fogTextureId)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, fog, 0)
            }

            if (clouds != null) {
                val texIds = IntArray(1)
                GLES20.glGenTextures(1, texIds, 0)
                cloudsTextureId = texIds[0]
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, cloudsTextureId)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, clouds, 0)
            }
            requestRender()
        }

        fun updateColorMatrix(matrix: ColorMatrix) {
            val src = matrix.array
            glColorMatrix[0] = src[0];  glColorMatrix[4] = src[1];  glColorMatrix[8]  = src[2];  glColorMatrix[12] = src[3]
            glColorMatrix[1] = src[5];  glColorMatrix[5] = src[6];  glColorMatrix[9]  = src[7];  glColorMatrix[13] = src[8]
            glColorMatrix[2] = src[10]; glColorMatrix[6] = src[11]; glColorMatrix[10] = src[12]; glColorMatrix[14] = src[13]
            glColorMatrix[3] = src[15]; glColorMatrix[7] = src[16]; glColorMatrix[11] = src[17]; glColorMatrix[15] = src[18]

            glColorOffset[0] = src[4] / 255f
            glColorOffset[1] = src[9] / 255f
            glColorOffset[2] = src[14] / 255f
            glColorOffset[3] = src[19] / 255f
        }

        private fun ensureInitialBackground() {
            if (textureCurrent != 0) return
            textureCurrent = createDummyTexture()
            texCurrentW = 1
            texCurrentH = 1
            isCurrentGradient = true
            cropScaleCurrent = 1f
            cropOffsetXCurrent = 0f
            cropOffsetYCurrent = 0f
        }

        fun beginCompositeLoading() {
            if (!isSurfaceCreated || activeTransitionType != TransitionType.COMPOSITE_LOADING ||
                compositeLoadingProgram == 0) return
            // A successor request must not briefly uncover the old image or reset its noise.
            if (compositeLoadingTimeline.isWaitingForImage) return
            val wasComposite = isCompositeTransitionActive()
            val carriedProgress = if (wasComposite) transitionProgress else 0f
            if (textureNext != 0) {
                if (transitionProgress >= 0.5f) commitTransition()
                else {
                    deleteTexture(textureNext)
                    textureNext = 0
                }
            }
            ensureInitialBackground()
            compositeBlur.release()
            val now = android.os.SystemClock.uptimeMillis()
            if (!wasComposite) {
                transitionTimeSeed = Random.nextFloat() * 10f
                compositeStartTime = now
            }
            compositeLoadingTimeline.begin(now, carriedProgress)
            transitionProgress = compositeLoadingTimeline.progress(now)
        }

        fun cancelCompositeLoading(): Boolean {
            if (!compositeLoadingTimeline.isWaitingForImage) return false
            compositeLoadingTimeline.cancel()
            transitionProgress = 0f
            compositeBlur.release()
            return true
        }

        fun hasBackgroundTransition(): Boolean =
            textureNext != 0 || compositeLoadingTimeline.isWaitingForImage

        fun getTransitionProgress(): Float = transitionProgress

        fun setNextTexture(
            bitmap: Bitmap,
            instant: Boolean = false,
            isGradient: Boolean = false,
            scale: Float = 1f,
            offsetX: Float = 0f,
            offsetY: Float = 0f
        ): Boolean {
            if (!isSurfaceCreated) {
                currentBitmap = bitmap
                return false
            }

            val continuingLoading = !instant &&
                activeTransitionType == TransitionType.COMPOSITE_LOADING &&
                compositeLoadingTimeline.isWaitingForImage
            val loadingProgress = if (continuingLoading) {
                compositeLoadingTimeline.imageReady(android.os.SystemClock.uptimeMillis())
            } else {
                compositeLoadingTimeline.cancel()
                0f
            }

            if (instant) {
                compositeBlur.release()
                if (textureCurrent != 0) deleteTexture(textureCurrent)
                textureCurrent = loadTexture(bitmap)
                texCurrentW = bitmap.width
                texCurrentH = bitmap.height
                isCurrentGradient = isGradient
                cropScaleCurrent = scale
                cropOffsetXCurrent = offsetX
                cropOffsetYCurrent = offsetY
                if (textureNext != 0) {
                    deleteTexture(textureNext)
                    textureNext = 0
                }
                isNextGradient = false
                transitionProgress = 0f
                return false
            }

            // A replacement upload may reuse the deleted pending texture's GL name.
            // Never let that name match a cached blur of a different bitmap.
            compositeBlur.release()
            if (textureNext != 0) {
                if (transitionProgress >= 0.5f || textureCurrent == 0) {
                    commitTransition()
                } else {
                    deleteTexture(textureNext)
                    textureNext = 0
                }
            }

            ensureInitialBackground()
            textureNext = loadTexture(bitmap)
            texNextW = bitmap.width
            texNextH = bitmap.height
            isNextGradient = isGradient
            cropScaleNext = scale
            cropOffsetXNext = offsetX
            cropOffsetYNext = offsetY
            transitionProgress = loadingProgress
            if (!continuingLoading) {
                transitionTimeSeed = Random.nextFloat() * 10f
                compositeStartTime = android.os.SystemClock.uptimeMillis()
            }

            return true
        }

        fun setTransitionProgress(progress: Float) {
            transitionProgress = progress
        }

        fun finishTransitionIfComplete() {
            if (transitionProgress >= 0.99f && textureNext != 0) {
                commitTransition()
            }
        }

        fun setTransitionType(type: TransitionType) {
            if (type != TransitionType.COMPOSITE_LOADING) cancelCompositeLoading()
            activeTransitionType = type
        }

        @android.annotation.SuppressLint("RestrictedApi")
        fun setTransitionColors(accentColor: Int, screenColor: Int? = null) {
            compAccentR = android.graphics.Color.red(accentColor) / 255f
            compAccentG = android.graphics.Color.green(accentColor) / 255f
            compAccentB = android.graphics.Color.blue(accentColor) / 255f
            compAccentA = 1f

            val resolvedScreenColor = screenColor ?: run {
                com.google.android.material.color.utilities.TonalPalette.fromInt(accentColor).tone(10)
            }
            compScreenR = android.graphics.Color.red(resolvedScreenColor) / 255f
            compScreenG = android.graphics.Color.green(resolvedScreenColor) / 255f
            compScreenB = android.graphics.Color.blue(resolvedScreenColor) / 255f
            compScreenA = 1f
        }

        fun setGlobalAlpha(alpha: Float) {
            globalAlpha = alpha
        }

        fun getGlobalAlpha(): Float = globalAlpha

        fun getImageWidth(): Int = if (textureNext != 0) texNextW else texCurrentW
        fun getImageHeight(): Int = if (textureNext != 0) texNextH else texCurrentH

        fun setCropTransform(scale: Float, offsetX: Float, offsetY: Float) {
            cropScaleCurrent = scale
            cropOffsetXCurrent = offsetX
            cropOffsetYCurrent = offsetY
        }

        private fun commitTransition() {
            compositeLoadingTimeline.cancel()
            if (textureCurrent != 0) deleteTexture(textureCurrent)

            textureCurrent = textureNext
            texCurrentW = texNextW
            texCurrentH = texNextH
            isCurrentGradient = isNextGradient
            cropScaleCurrent = cropScaleNext
            cropOffsetXCurrent = cropOffsetXNext
            cropOffsetYCurrent = cropOffsetYNext

            compositeBlur.release()
            textureNext = 0
            texNextW = 0
            texNextH = 0
            isNextGradient = false

            transitionProgress = 0f
            requestRender()
        }

        fun startTurbulence(
            config: TurbulenceNoiseAnimationConfig,
            continuous: Boolean = false,
            onReady: (() -> Unit)?,
            onEnd: (() -> Unit)?
        ) {
            val c = config.color
            // Music turbulence has its own color; preserve the transition's AOSP palette.
            turbulenceColor.setColor(c, android.os.SystemClock.uptimeMillis(),
                animate = turbulencePhase != TurbulencePhase.IDLE)
            turbGridCount = config.gridCount
            turbSpeedX = config.noiseMoveSpeedX
            turbSpeedY = config.noiseMoveSpeedY
            turbSpeedZ = config.noiseMoveSpeedZ
            turbLuminosity = config.luminosityMultiplier
            turbContinuousOpacity = 0.65f * turbLuminosity
            turbContinuousSpeedFactor = 0.35f
            turbTransitionDurationMs = 1500L
            turbInverseLuma = config.shouldInverseNoiseLuminosity
            turbEaseInDurationMs = config.easeInDuration.toLong()
            turbMainDurationMs = config.maxDuration.toLong()
            turbEaseOutDurationMs = config.easeOutDuration.toLong()
            turbIsContinuous = continuous

            if (turbulencePhase == TurbulencePhase.IDLE) {
                turbOffsetX = config.noiseOffsetX
                turbOffsetY = config.noiseOffsetY
                turbOffsetZ = config.noiseOffsetZ
                turbCurrentOpacity = 0f
            }

            turbPhaseStartTime = System.currentTimeMillis()
            turbulencePhase = TurbulencePhase.EASE_IN
            turbReadyNotified = false
            onTurbReadyCallback = onReady
            onTurbEndCallback = onEnd
        }

        fun finishTurbulence(easeOutDurationMs: Long = 750L) {
            if (turbulencePhase != TurbulencePhase.IDLE && turbulencePhase != TurbulencePhase.EASE_OUT) {
                turbEaseOutStartOpacity = turbCurrentOpacity
                turbEaseOutDurationMs = easeOutDurationMs
                turbPhaseStartTime = System.currentTimeMillis()
                turbulencePhase = TurbulencePhase.EASE_OUT
            }
        }

        fun cancelTurbulence() {
            turbulencePhase = TurbulencePhase.IDLE
            turbCurrentOpacity = 0f
            deleteTurbFBO()
            val endCb = onTurbEndCallback
            onTurbReadyCallback = null
            onTurbEndCallback = null
            if (endCb != null) {
                post { endCb() }
            }
        }

        fun updateTurbulenceLiveConfig(
            gridScale: Float? = null,
            speedScale: Float? = null,
            continuous: Boolean? = null,
            color: Int? = null
        ) {
            if (color != null) {
                turbulenceColor.setColor(color, android.os.SystemClock.uptimeMillis(),
                    animate = turbulencePhase != TurbulencePhase.IDLE)
            }
            if (gridScale != null) {
                turbGridCount = 1.0f * gridScale
            }
            if (speedScale != null) {
                turbSpeedZ = 0.45f * speedScale
            }
            if (continuous != null) {
                turbIsContinuous = continuous
                if (!continuous && (turbulencePhase == TurbulencePhase.CONTINUOUS || turbulencePhase == TurbulencePhase.TRANSITION_TO_CONTINUOUS)) {
                    finishTurbulence(750L)
                }
            }
            requestRender()
        }

        fun isTurbulencePlaying(): Boolean = turbulencePhase != TurbulencePhase.IDLE

        private fun drawTurbulence(w: Int, h: Int, opacityScale: Float = 1f) {
            if (turbulenceProgram == 0 || w <= 0 || h <= 0) return

            // Dynamically scale with user's Render Scale and Weather Scale settings
            val turbScale = minOf(currentRenderScale, weatherResolutionScale).coerceIn(0.1f, 1.0f)
            val tw = (w * turbScale).toInt().coerceAtLeast(64)
            val th = (h * turbScale).toInt().coerceAtLeast(64)

            // Create / resize turbulence FBO if needed
            if (turbFboId == 0 || turbFboW != tw || turbFboH != th) {
                deleteTurbFBO()
                val fbs = IntArray(1)
                GLES20.glGenFramebuffers(1, fbs, 0)
                turbFboId = fbs[0]

                val texs = IntArray(1)
                GLES20.glGenTextures(1, texs, 0)
                turbFboTexId = texs[0]

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, turbFboTexId)
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, tw, th, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
                )
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, turbFboId)
                GLES20.glFramebufferTexture2D(
                    GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, turbFboTexId, 0
                )

                if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                    deleteTurbFBO()
                    return
                }
                turbFboW = tw
                turbFboH = th
            }

            // --- Pass 1: render noise into low-res FBO ---
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, turbFboId)
            GLES20.glViewport(0, 0, tw, th)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glUseProgram(turbulenceProgram)

            // Pass the *full* screen aspect ratio so the noise pattern matches regardless of FBO size
            GLES20.glUniform1f(uTurbGridNumLoc, turbGridCount)
            GLES20.glUniform3f(uTurbNoiseMoveLoc, turbOffsetX, turbOffsetY, turbOffsetZ)
            GLES20.glUniform2f(uTurbScreenSizeLoc, tw.toFloat(), th.toFloat())
            GLES20.glUniform1f(uTurbAspectRatioLoc, w.toFloat() / maxOf(h.toFloat(), 1f))
            GLES20.glUniform1f(uTurbOpacityLoc, turbCurrentOpacity * opacityScale)
            GLES20.glUniform1f(uTurbInverseLumaLoc, if (turbInverseLuma) -1f else 1f)
            GLES20.glUniform4f(uTurbColorLoc, turbColorR, turbColorG, turbColorB, turbColorA)
            GLES20.glUniform1f(uTurbGlobalAlphaLoc, globalAlpha)

            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(turbAPos, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glEnableVertexAttribArray(turbAPos)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(turbAPos)

            // --- Pass 2: upscale FBO texture to screen with premultiplied alpha blend ---
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
            GLES20.glViewport(0, 0, w, h)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            GLES20.glUseProgram(screenProgram)

            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(screenPositionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
            GLES20.glEnableVertexAttribArray(screenPositionHandle)

            vertexBuffer.position(3)
            GLES20.glVertexAttribPointer(screenTexCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
            GLES20.glEnableVertexAttribArray(screenTexCoordHandle)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, turbFboTexId)
            GLES20.glUniform1i(screenTextureHandle, 0)
            GLES20.glUniform1f(screenAlphaHandle, 1.0f)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }

        private fun deleteTurbFBO() {
            if (turbFboId != 0) {
                GLES20.glDeleteFramebuffers(1, intArrayOf(turbFboId), 0)
                turbFboId = 0
            }
            if (turbFboTexId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(turbFboTexId), 0)
                turbFboTexId = 0
            }
            turbFboW = 0
            turbFboH = 0
        }

        fun setBackgroundZoom(zoom: Float) {
            backgroundZoom = zoom
        }

        private fun calculateCenterCrop(
            imgW: Int, imgH: Int, vw: Int, vh: Int,
            isGradient: Boolean,
            scale: Float, offsetX: Float, offsetY: Float
        ): FloatArray {
            val crop = computeCenterCrop(
                imgW, imgH, vw, vh,
                isGradient,
                scale, offsetX, offsetY,
                this@DynamicBackgroundView.width.toFloat(),
                this@DynamicBackgroundView.height.toFloat()
            )
            return applyBackgroundZoom(crop, backgroundZoom)
        }

        private fun loadTexture(bitmap: Bitmap): Int {
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            val id = textureIds[0]

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            return id
        }

        private fun createDummyTexture(): Int {
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            val id = textureIds[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

            val buffer = ByteBuffer.allocateDirect(4).put(byteArrayOf(15, 20, 26, 255.toByte()))
            buffer.position(0)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            return id
        }

        private fun deleteTexture(textureId: Int) {
            val textureIds = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textureIds, 0)
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            if (vertexShader == 0 || fragmentShader == 0) return 0

            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, vertexShader)
            GLES20.glAttachShader(p, fragmentShader)
            GLES20.glLinkProgram(p)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                GLES20.glDeleteProgram(p)
                return 0
            }
            return p
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                GLES20.glDeleteShader(shader)
                return 0
            }
            return shader
        }
    }

    companion object {
        /** Compose a centered screen zoom after the saved crop, without changing screen UVs. */
        internal fun applyBackgroundZoom(crop: FloatArray, zoom: Float): FloatArray {
            val factor = if (zoom.isFinite()) zoom.coerceAtLeast(1f) else 1f
            val width = crop[0] / factor
            val height = crop[1] / factor
            return floatArrayOf(width, height,
                crop[2] + (crop[0] - width) * 0.5f,
                crop[3] + (crop[1] - height) * 0.5f)
        }

        internal fun computeCenterCrop(
            imgW: Int, imgH: Int, vw: Int, vh: Int,
            isGradient: Boolean,
            scale: Float, offsetX: Float, offsetY: Float,
            unscaledViewWidth: Float = vw.toFloat(),
            unscaledViewHeight: Float = vh.toFloat()
        ): FloatArray {
            if (isGradient) {
                return floatArrayOf(1f, 1f, 0f, 0f)
            }
            if (imgW == 0 || imgH == 0 || vw == 0 || vh == 0) {
                return floatArrayOf(1f, 1f, 0f, 0f)
            }

            val viewAspect = vw.toFloat() / vh.toFloat()
            val imgAspect = imgW.toFloat() / imgH.toFloat()

            var scaleX = 1f
            var scaleY = 1f
            var dx = 0f
            var dy = 0f

            if (imgAspect > viewAspect) {
                scaleX = viewAspect / imgAspect
                dx = (1f - scaleX) / 2f
            } else {
                scaleY = imgAspect / viewAspect
                dy = (1f - scaleY) / 2f
            }

            val unscaledVw = if (unscaledViewWidth > 0f) unscaledViewWidth else vw.toFloat()
            val unscaledVh = if (unscaledViewHeight > 0f) unscaledViewHeight else vh.toFloat()

            val safeScale = scale.coerceAtLeast(1f)
            val finalScaleX = (scaleX / safeScale).coerceIn(0f, 1f)
            val finalScaleY = (scaleY / safeScale).coerceIn(0f, 1f)

            val maxDx = maxOf(0f, 1f - finalScaleX)
            val maxDy = maxOf(0f, 1f - finalScaleY)

            val rawDx = (dx - 0.5f) / safeScale + 0.5f - (offsetX / unscaledVw) * (scaleX / safeScale)
            val rawDy = (dy - 0.5f) / safeScale + 0.5f - (offsetY / unscaledVh) * (scaleY / safeScale)

            val finalDx = rawDx.coerceIn(0f, maxDx)
            val finalDy = rawDy.coerceIn(0f, maxDy)

            return floatArrayOf(finalScaleX, finalScaleY, finalDx, finalDy)
        }

        private const val POINT_VERTEX_SHADER = """
            uniform mat4 u_Matrix;
            attribute vec4 a_Position;
            attribute float a_PointSize;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                gl_Position = u_Matrix * a_Position;
                gl_PointSize = a_PointSize;
                v_Color = a_Color;
            }
        """

        private const val POINT_FRAGMENT_SHADER = """
            precision mediump float;
            uniform float u_globalAlpha;
            varying vec4 v_Color;
            void main() {
                vec2 coord = gl_PointCoord - vec2(0.5);
                float dist = length(coord) * 2.0;
                if (dist > 1.0) discard;
                
                float alphaMultiplier = 1.0 - dist;
                alphaMultiplier = alphaMultiplier * alphaMultiplier;
                
                float finalAlpha = v_Color.a * alphaMultiplier;
                
                gl_FragColor = vec4(v_Color.rgb * finalAlpha, finalAlpha) * u_globalAlpha;
            }
        """

        private const val LINE_VERTEX_SHADER = """
            uniform mat4 u_Matrix;
            attribute vec4 a_Position;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                gl_Position = u_Matrix * a_Position;
                v_Color = a_Color;
            }
        """

        private const val LINE_FRAGMENT_SHADER = """
            precision mediump float;
            uniform float u_globalAlpha;
            varying vec4 v_Color;
            void main() {
                gl_FragColor = vec4(v_Color.rgb * v_Color.a, v_Color.a) * u_globalAlpha;
            }
        """

        private const val GLSL_UTILS = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif

            const float PI  = 3.14159265358979;
            const float TAU = 6.28318530717959;

            float idGenerator(vec2 point) {
                vec2 p = fract(mod(point, 1024.0) * vec2(723.123, 236.209));
                p += dot(p, p + 17.1512);
                return fract(p.x * p.y);
            }

            float idGenerator(float value) {
                return idGenerator(vec2(value, 1.412));
            }

            float triangleNoise(vec2 n) {
                n  = fract(n * vec2(5.3987, 5.4421));
                n += dot(n.yx, n.xy + vec2(21.5351, 14.3137));
                float xy = n.x * n.y;
                return fract(xy * 95.4307) + fract(xy * 75.04961) - 1.0;
            }

            mat2 rotationMat(float angleRad) {
                float c = cos(angleRad);
                float s = sin(angleRad);
                return mat2(c, s, -s, c);
            }

            vec2 rotateAroundPoint(vec2 point, vec2 centerPoint, float angleRad) {
                return (point - centerPoint) * rotationMat(angleRad) + centerPoint;
            }

            float wiggle(float time, float wiggleSpeed) {
                return sin(wiggleSpeed * time + 0.5 * sin(wiggleSpeed * 5.0 * time)) * sin(wiggleSpeed * time) - 0.5;
            }

            float map(float value, float inMin, float inMax, float outMin, float outMax) {
                float v = clamp(value, inMin, inMax);
                float p = (v - inMin) / (inMax - inMin);
                return p * (outMax - outMin) + outMin;
            }

            float relativeLuminance(vec3 color) {
                return dot(vec3(0.2126, 0.7152, 0.0722), color);
            }

            vec3 imageRangeConversion(vec3 color, float rangeCompression, float blackLevel, float noise, float intensity) {
                color *= mix(1.0, rangeCompression + noise, intensity);
                color += blackLevel * intensity;
                return color;
            }
        """

        private const val GLSL_RAIN_CONSTANTS = """
            const vec3  highlightColor      = vec3(1.0);
            const vec3  contactShadowColor  = vec3(0.0);
            const vec3  dropTint            = vec3(1.0);
            const float dropTintIntensity   = 0.09;
            const float highlightIntensity  = 0.7;
            const float dropShadowIntensity = 0.5;
            uniform float u_windFactor;
        """

        private const val GLSL_SUN_EFFECT = """
            const vec2 sunCenter = vec2(0.57, -0.8); 
            const vec3 godRaysColor = vec3(1.0, 0.857, 0.71428);

            vec3 addFlareCircle(vec2 uv, vec2 sunPos, float distScale, float size, float chroAb, float definition) {
                float dR = distance(uv, distScale * (1.0 - chroAb) * sunPos) / (size * (1.0 - chroAb));
                float dG = distance(uv, distScale * 1.0 * sunPos) / (size);
                float dB = distance(uv, distScale * (1.0 + chroAb) * sunPos) / (size * (1.0 + chroAb));
                float wR = 1.0 - smoothstep(definition, 0.75, dR);
                float wG = 1.0 - smoothstep(definition, 0.75, dG);
                float wB = 1.0 - smoothstep(definition, 0.75, dB);
                return vec3(wR, wG, wB);
            }

            vec3 addFlareRing(vec2 uv, vec2 sunPos, float distScale, float size, float chroAb, float stroke) {
                float dR = distance(uv, distScale * (1.0 - chroAb) * sunPos) / (size * (1.0 - chroAb));
                float dG = distance(uv, distScale * 1.0 * sunPos) / (size);
                float dB = distance(uv, distScale * (1.0 + chroAb) * sunPos) / (size * (1.0 + chroAb));
                float wR = smoothstep(0.75 - stroke, 0.75, dR) - smoothstep(0.75, 0.75 + stroke, dR);
                float wG = smoothstep(0.75 - stroke, 0.75, dG) - smoothstep(0.75, 0.75 + stroke, dG);
                float wB = smoothstep(0.75 - stroke, 0.75, dB) - smoothstep(0.75, 0.75 + stroke, dB);
                return vec3(max(wR,0.0), max(wG,0.0), max(wB,0.0));
            }

            vec3 addFlareCircleSimple(vec2 uv, vec2 sunPos, float distScale, float size, float chroAb) {
                return addFlareCircle(uv, sunPos, distScale, size, chroAb, 0.25);
            }

            vec3 addFlareDistorted(vec2 uv, vec2 sunPos, float distScale, float size, float chroAb) {
                vec2 uvd = uv * length(uv);
                return addFlareCircle(uvd, sunPos, distScale, size, chroAb, 0.35);
            }

            vec3 addFlare(vec3 color, vec2 uv, vec2 sunPos, float intensity, float time) {
                vec3 ret = vec3(0.0);
                ret += vec3(0.7) * addFlareCircleSimple(uv, sunPos, -0.1, 0.1, 0.04);
                ret += vec3(0.64) * addFlareCircleSimple(uv, sunPos, 0.05, 0.035, 0.04);
                ret += vec3(0.5) * addFlareCircleSimple(uv, sunPos, -0.22, 0.18, 0.04);
                ret += vec3(0.34) * addFlareRing(uv, sunPos, -0.35, 0.4, 0.02, 0.16);
                ret += vec3(0.52) * addFlareDistorted(uv, sunPos, -0.4, 0.3, 0.08);
                ret += vec3(0.57) * addFlareDistorted(uv, sunPos, 0.4, 0.15, 0.06);
                return mix(color.rgb, vec3(1.0, 0.95, 0.88), intensity * ret);
            }

            float calculateRay(float angle, float time) {
                float rays = 17.5 + 8.0 * sin(3.0 * angle + time);
                rays += 4.0 * sin(12.0 * angle - 0.3 * time);
                rays += 4.0 * sin(25.0 * angle + 0.9252 * time);
                rays += -1.8 * cos(38.0 * angle - 0.114 * time);
                rays += 0.45 * cos(60.124 * angle + 0.251 * time);
                return rays;
            }

            float godRays(vec2 uv, vec2 center, float phase, float frequency, float time, float intensity) {
                uv -= center;
                float angle = atan(uv.y, uv.x);
                float sunGlow = 1.0 / (1.0 + 20.0 * length(uv));
                float rays = calculateRay(angle * frequency, phase + time);
                return intensity * sunGlow * (rays * 0.4 + 2.0 + 2.0 * length(uv));
            }

            vec3 addGodRays(vec3 background, vec2 fragCoord, vec2 uv, vec2 sunPos, float phase, float frequency, float timeSpeed, float time, float intensity) {
                float rays = godRays(uv, sunPos, phase, frequency, timeSpeed * time, intensity);
                rays -= triangleNoise(fragCoord.xy) * 0.025;
                rays = clamp(rays, 0.0, 1.0);
                vec3 raysColor = mix(godRaysColor, min(godRaysColor + 0.5, vec3(1.0)), smoothstep(0.15, 0.6, rays));
                return mix(background.rgb, raysColor, smoothstep(0.1, 1.0, rays));
            }

            float checkBrightnessGodRaysAtCenter(vec2 center, float phase, float frequency, float timeSpeed, float time) {
                float angle = atan(-center.y, -center.x);
                float rays = calculateRay(angle * frequency, phase + timeSpeed * time);
                return smoothstep(-0.75, 35.25, rays);
            }
        """

        private const val SUN_FRAGMENT_SHADER = GLSL_UTILS + GLSL_SUN_EFFECT + """
            uniform sampler2D u_texture;
            uniform int       u_hasTexture;
            uniform sampler2D u_foregroundTex;
            uniform int       u_hasForegroundTex;
            uniform float     u_time;
            uniform float     u_intensity;
            uniform float     u_globalAlpha;
            uniform vec2      u_screenSize;
            uniform float     u_screenAspectRatio;

            void main() {
                vec2 fragCoord = gl_FragCoord.xy;
                vec2 uv = fragCoord / u_screenSize;
                uv.y = 1.0 - uv.y;
                
                vec2 centeredUv = uv - vec2(0.5, 0.5);
                centeredUv.y /= u_screenAspectRatio;

                vec2 sunVariation = vec2(0.1 * sin(u_time * 0.3), 0.14 * cos(u_time * 0.5));
                sunVariation += 0.1 * (0.5 * sin(u_time * 0.456) + 0.5) * sunCenter / vec2(1.0, u_screenAspectRatio);
                vec2 sunPos = sunVariation + sunCenter / vec2(1.0, u_screenAspectRatio);

                vec2 texUv = vec2(gl_FragCoord.x / u_screenSize.x, gl_FragCoord.y / u_screenSize.y);
                vec4 baseColor = vec4(0.0);
                if (u_hasTexture == 1) {
                    baseColor = texture2D(u_texture, texUv);
                    baseColor.rgb *= baseColor.a;
                }
                if (u_hasForegroundTex == 1) {
                    vec4 fgColor = texture2D(u_foregroundTex, texUv);
                    fgColor.rgb *= fgColor.a;
                    baseColor = fgColor + baseColor * (1.0 - fgColor.a);
                }

                float brightnessSunray = checkBrightnessGodRaysAtCenter(sunPos, 10.0, 1.1, 0.9, u_time);
                brightnessSunray *= brightnessSunray;

                float noise = 0.025 * triangleNoise(fragCoord.xy + vec2(12.31, 1024.1241));
                baseColor.rgb = imageRangeConversion(baseColor.rgb, 0.95, 0.01, noise, u_intensity * 0.5);

                float lum = relativeLuminance(baseColor.rgb);
                vec3 highlightColor = vec3(0.41, 0.69, 0.856);
                float highlightThres = 0.66;
                float highlightBlend = 0.15 + brightnessSunray * 0.05; 
                vec3 shadowColor = vec3(0.756, 0.69, 0.31);
                float shadowThres = 0.33;
                float shadowBlend = 0.10 + brightnessSunray * 0.05; 

                float highlightsMask = smoothstep(highlightThres, 1.0, lum);
                float shadowsMask = 1.0 - smoothstep(0.0, shadowThres, lum);

                baseColor.rgb = mix(baseColor.rgb, shadowColor, u_intensity * shadowBlend * shadowsMask);
                baseColor.rgb = mix(baseColor.rgb, highlightColor, u_intensity * highlightBlend * highlightsMask);

                baseColor.rgb = addGodRays(baseColor.rgb, fragCoord.xy, centeredUv, sunPos, 10.0, 1.1, 0.9, u_time, u_intensity * 0.45);
                baseColor.rgb = addFlare(baseColor.rgb, centeredUv, sunPos, (0.2 + 0.4 * brightnessSunray) * u_intensity, u_time);

                gl_FragColor = baseColor * u_globalAlpha;
            }
        """

        private const val FOG_FRAGMENT_SHADER = GLSL_UTILS + """
            uniform sampler2D u_texture;
            uniform int       u_hasTexture;
            uniform sampler2D u_foregroundTex;
            uniform int       u_hasForegroundTex;
            uniform sampler2D u_fogTex;
            uniform sampler2D u_cloudsTex;
            uniform int       u_hasFogTex;
            uniform float     u_time;
            uniform float     u_intensity;
            uniform float     u_globalAlpha;
            uniform vec2      u_screenSize;
            uniform float     u_isCloudy;

            void main() {
                vec2 uv = gl_FragCoord.xy / u_screenSize;
                uv.y = 1.0 - uv.y;
                vec2 texUv = vec2(gl_FragCoord.x / u_screenSize.x, gl_FragCoord.y / u_screenSize.y);

                vec4 baseColor = vec4(0.0);
                if (u_hasTexture == 1) {
                    baseColor = texture2D(u_texture, texUv);
                    baseColor.rgb *= baseColor.a; 
                }

                float dynamicFogAlpha = 0.0;
                if (u_hasFogTex == 1) {
                    vec2 scaleMult = mix(vec2(1.0), vec2(0.6), u_isCloudy);
                    vec2 fogUv1 = (uv * 0.6 * scaleMult) + vec2(u_time * 0.0015, u_time * 0.0008);
                    vec2 fogUv2 = (uv * 0.4 * scaleMult) + vec2(-u_time * 0.0012, u_time * 0.0015);
                    vec3 noise1 = texture2D(u_fogTex, fogUv1).rgb;
                    vec3 noise2 = texture2D(u_cloudsTex, fogUv2).rgb;
                    float combinedNoise = (noise1.r + noise2.g) * 0.5;
                    
                    float smoothMin = mix(0.1, 0.3, u_isCloudy);
                    float smoothMax = mix(0.9, 0.7, u_isCloudy);
                    dynamicFogAlpha = smoothstep(smoothMin, smoothMax, combinedNoise) * 0.6 * u_intensity;
                } else {
                    float wave = sin(uv.x * 1.5 + u_time * 0.015) + sin(uv.y * 1.8 - u_time * 0.012);
                    wave = (wave / 2.0) * 0.5 + 0.5; 
                    wave = mix(wave, smoothstep(0.3, 0.7, wave), u_isCloudy);
                    dynamicFogAlpha = 0.5 * u_intensity * wave;
                }

                float heightMask = mix(1.0, smoothstep(0.1, 0.85, uv.y), u_isCloudy);

                float baseFogAlpha = mix(0.15, 0.05, u_isCloudy) * u_intensity;
                float farFogAlpha = clamp((baseFogAlpha + dynamicFogAlpha) * heightMask, 0.0, 1.0);
                
                vec3 layerColor = mix(vec3(0.95), vec3(0.90), u_isCloudy);
                
                vec4 farFogLayer = vec4(layerColor * farFogAlpha, farFogAlpha);
                baseColor = farFogLayer + baseColor * (1.0 - farFogLayer.a);

                if (u_hasForegroundTex == 1) {
                    vec4 fgColor = texture2D(u_foregroundTex, texUv);
                    fgColor.rgb *= fgColor.a;
                    baseColor = fgColor + baseColor * (1.0 - fgColor.a);
                }

                float nearFogAlpha = clamp(dynamicFogAlpha * 0.3 * heightMask, 0.0, 1.0);
                vec4 nearFogLayer = vec4(layerColor * nearFogAlpha, nearFogAlpha);
                
                gl_FragColor = (nearFogLayer + baseColor * (1.0 - nearFogLayer.a)) * u_globalAlpha;
            }
        """

        private const val GLSL_RAIN_SHOWER = """
            struct Rain { float dropMask; vec2 cellUv; };

            Rain generateRain(vec2 uv, float screenAspectRatio, float time, vec2 rainGridSize, float rainIntensity) {
                float cellAspectRatio = rainGridSize.x / rainGridSize.y;
                rainGridSize.y /= screenAspectRatio;
                
                float slant = -0.08 - u_windFactor * 0.012;
                uv.x += uv.y * slant;
                
                vec2 gridUv = uv * rainGridSize;
                gridUv.y = 1.0 - gridUv.y;
                
                gridUv += vec2(10.0); 
                
                float columnId = idGenerator(floor(gridUv.x));
                float colSpeed = 0.85 + 0.3 * idGenerator(floor(gridUv.x) + 42.1);
                
                float verticalGridPos = 0.4 * time * colSpeed;
                gridUv.y += verticalGridPos + columnId * 2.6;
                gridUv.y = mod(gridUv.y, 256.0);

                float cellId = idGenerator(floor(gridUv));
                vec2 cellUv = fract(gridUv) - 0.5;

                float intensity2 = idGenerator(floor(vec2(cellId * 8.16, 27.2)));
                if (intensity2 < 1.0 - rainIntensity) return Rain(0.0, cellUv);

                float scaleVariation  = 1.0 - 0.3 * cellId;

                float horizontalStart = 0.8 * (cellId - 0.5);
                float verticalStart = (idGenerator(floor(gridUv) + vec2(13.51, 71.39)) - 0.5) * 0.7;
                vec2 dropPos = cellUv;
                dropPos.y += verticalStart - 0.052;
                dropPos.x += horizontalStart;
                dropPos *= scaleVariation * vec2(14.2, 2.728);

                float dist = 1.0 - length(vec2(dropPos.x, dropPos.y - dropPos.x * dropPos.x));
                float dropMask = smoothstep(0.0, 0.80 + 3.0 * cellId, dist);
                
                return Rain(dropMask, cellUv);
            }
        """

        private const val GLSL_GLASS_RAIN = """
            struct GlassRain { vec2 drop; float dropMask; vec2 dropplets; float droppletsMask; float trailMask; vec2 cellUv; };

            GlassRain generateGlassRain(vec2 uv, float screenAspectRatio, float time, vec2 rainGridSize, float rainIntensity) {
                vec2 dropPos = vec2(0.0);
                float cellMainDropMask = 0.0, cellDroppletsMask = 0.0, cellTrailMask = 0.0;
                vec2 trailDropsPos = vec2(0.0);

                float cellAspectRatio = rainGridSize.x / rainGridSize.y;
                rainGridSize.y /= screenAspectRatio;
                
                float slant = -u_windFactor * 0.008;
                uv.x += uv.y * slant;
                
                vec2 gridUv = uv * rainGridSize;
                gridUv.y = 1.0 - gridUv.y;
                gridUv += vec2(10.0); 
                
                float columnId = idGenerator(floor(gridUv.x));
                float colSpeed = 0.85 + 0.3 * idGenerator(floor(gridUv.x) + 73.1);
                float verticalGridPos = (2.4 * time / 5.0) * colSpeed;
                gridUv.y += verticalGridPos + columnId * 3.4;
                gridUv.y = mod(gridUv.y, 128.0);

                float cellId = idGenerator(floor(gridUv));
                vec2 cellUv  = fract(gridUv) - 0.5;

                float wrappedTime = mod(time + cellId * 7.1203, 30.0);
                uv.y += cellId * 3.83027;
                float scaleVariation = 1.0 + 0.7 * cellId;

                if (cellId < 1.0 - rainIntensity) return GlassRain(dropPos, cellMainDropMask, trailDropsPos, cellDroppletsMask, cellTrailMask, cellUv);

                float verticalSpeed = TAU / 5.0;
                float verticalPosVariation = 0.45 * 0.63 * (-1.2 * sin(verticalSpeed * wrappedTime) - 0.5 * sin(2.0 * verticalSpeed * wrappedTime) - 0.3333 * sin(3.0 * verticalSpeed * wrappedTime));

                float wiggleSpeed = 6.0, wiggleAmp = 0.5, horizontalStartAmp = 0.5;
                float horizontalStart = (cellId - 0.5) * 2.0 * horizontalStartAmp / cellAspectRatio;
                float wiggleFactor = max(horizontalStartAmp - abs(horizontalStart), 0.15);
                float horizontalWiggle = wiggle(uv.y, wiggleSpeed);
                horizontalWiggle = horizontalStart + wiggleFactor * wiggleAmp * horizontalWiggle;

                float dropPosUncorrected = cellUv.x - horizontalWiggle;
                dropPos.x = dropPosUncorrected / cellAspectRatio;
                verticalPosVariation -= dropPosUncorrected * dropPosUncorrected / cellAspectRatio;
                dropPos.y = cellUv.y - verticalPosVariation;
                dropPos *= scaleVariation;
                
                cellMainDropMask = 1.0 - smoothstep(0.04, 0.06, length(dropPos));

                float rawTrailY = cellUv.y - mod(verticalGridPos, 25.0);
                float trailSubIndex = floor(rawTrailY * 5.0);
                float subDropId = idGenerator(vec2(cellId * 17.31 + trailSubIndex * 7.19, trailSubIndex));
                float subDropChance = smoothstep(0.55, 0.68, subDropId);

                float xJitter = (idGenerator(vec2(trailSubIndex * 31.7, cellId * 13.9)) - 0.5) * 0.022 / cellAspectRatio;
                float yJitter = (idGenerator(vec2(trailSubIndex * 47.3, cellId * 23.1)) - 0.5) * 0.05;

                trailDropsPos.x = (cellUv.x - horizontalWiggle) / cellAspectRatio + xJitter;
                trailDropsPos.y = (fract(rawTrailY * 5.0) - 0.5) / 5.0 + yJitter;
                trailDropsPos *= scaleVariation;
                
                float dropRadius = 0.012 + 0.012 * subDropId;
                cellDroppletsMask = (1.0 - smoothstep(dropRadius * 0.7, dropRadius, length(trailDropsPos))) * subDropChance;
                
                float verticalFading = 1.2 * (1.0 - smoothstep(verticalPosVariation, 0.5, cellUv.y));
                cellDroppletsMask *= verticalFading;
                cellDroppletsMask *= smoothstep(-0.06, 0.08, dropPos.y);

                cellTrailMask = smoothstep(-0.04, 0.04, dropPos.y) * verticalFading * (1.0 - smoothstep(0.02, 0.07, abs(dropPos.x)));
                cellDroppletsMask *= cellTrailMask;

                return GlassRain(dropPos, cellMainDropMask, trailDropsPos, cellDroppletsMask, cellTrailMask, cellUv);
            }

            vec3 generateStaticGlassRain(vec2 uv, float screenAspectRatio, float time, float intensity, vec2 screenSize) {
                vec2 gridSize = vec2(15.0, 15.0);
                gridSize.y /= screenAspectRatio;
                vec2 gridUv = uv * gridSize;
                gridUv.y = 1.0 - gridUv.y;
                gridUv += vec2(10.0); 
                
                float columnId = idGenerator(floor(gridUv.x));
                gridUv.y += columnId * 5.6;

                float cellId = idGenerator(floor(gridUv));
                if (cellId < 0.8) return vec3(0.0);

                vec2 cellUv = fract(gridUv) - 0.5;
                vec2 dropOffset = vec2(
                    idGenerator(floor(gridUv) + vec2(7.13, 13.91)) - 0.5,
                    idGenerator(floor(gridUv) + vec2(19.37, 41.73)) - 0.5
                ) * 0.7;
                vec2 centeredUv = cellUv - dropOffset;
                float delay = 3.5173, duration = 8.2;
                float t = mod(time + 100.0 * cellId, 3600.0);
                float circletime = floor(t / (duration + delay));
                float delayOffset = idGenerator(floor(gridUv) + vec2(mod(circletime, 100.0), 43.14 * cellId));
                float normalizedTime = map(mod(t, duration + delay) - delay * delayOffset, 0.0, duration, 0.0, 1.0);
                normalizedTime *= normalizedTime;

                vec2 pos = centeredUv * (1.5 - 0.5 * cellId + normalizedTime * 50.0);
                
                float mask = (1.0 - smoothstep(0.2, 0.3, length(pos))) * (1.0 - smoothstep(0.06, 0.2, normalizedTime)) * smoothstep(0.0, 0.45, intensity);

                return vec3(pos * 0.19, mask);
            }
        """

        private const val GLASS_RAIN_FRAGMENT_SHADER = GLSL_UTILS + GLSL_RAIN_CONSTANTS + GLSL_GLASS_RAIN + """
            uniform sampler2D u_texture;
            uniform int       u_hasTexture;
            uniform sampler2D u_foregroundTex;
            uniform int       u_hasForegroundTex;
            uniform float     u_time;
            uniform float     u_intensity;
            uniform float     u_globalAlpha;
            uniform vec2      u_screenSize;
            uniform float     u_screenAspectRatio;
            uniform float     u_lightningAlpha;

            void main() {
                vec2 fragCoord = gl_FragCoord.xy;
                vec2 uv = vec2(fragCoord.x / u_screenSize.x, 1.0 - fragCoord.y / u_screenSize.y);

                GlassRain small = generateGlassRain(uv, u_screenAspectRatio, u_time, vec2(4.0, 2.0), u_intensity);
                float dropMask = small.dropMask, droppletsMask = small.droppletsMask, trailMask = small.trailMask;
                vec2 dropUvMasked = small.drop * small.dropMask, droppletsUvMasked = small.dropplets * small.droppletsMask;

                GlassRain med = generateGlassRain(uv, u_screenAspectRatio, u_time * 1.267, vec2(3.5, 1.5), u_intensity);

                dropMask = max(med.dropMask, dropMask);
                droppletsMask = max(med.droppletsMask, droppletsMask);
                trailMask = max(med.trailMask, trailMask);
                dropUvMasked = mix(dropUvMasked, med.drop * med.dropMask, med.dropMask);
                droppletsUvMasked = mix(droppletsUvMasked, med.dropplets * med.droppletsMask, med.droppletsMask);

                vec3 staticRain = generateStaticGlassRain(uv, u_screenAspectRatio, u_time, u_intensity, u_screenSize);
                dropMask = max(dropMask, staticRain.z);
                dropUvMasked = mix(dropUvMasked, staticRain.xy * staticRain.z, staticRain.z);

                float distortionDrop = -0.1;
                vec2 uvDiffractionOffsets = distortionDrop * dropUvMasked;
                vec2 s = u_screenSize;
                s.y *= -1.0;

                vec2 texUv = vec2(gl_FragCoord.x / u_screenSize.x, gl_FragCoord.y / u_screenSize.y);
                
                vec4 baseColor = vec4(0.0);
                float dropStrength = max(dropMask, droppletsMask);
                
                if (dropStrength > 0.0) {
                    vec2 sampleUv = clamp(texUv + uvDiffractionOffsets * s / u_screenSize, 0.0, 1.0);
                    vec4 sampledColor = vec4(0.0);
                    
                    if (u_hasTexture == 1) {
                        sampledColor = texture2D(u_texture, sampleUv);
                        sampledColor.rgb *= sampledColor.a;
                    }
                    if (u_hasForegroundTex == 1) {
                        vec4 sampledFg = texture2D(u_foregroundTex, sampleUv);
                        sampledFg.rgb *= sampledFg.a;
                        sampledColor = sampledFg + sampledColor * (1.0 - sampledFg.a);
                    }

                    baseColor = sampledColor * dropStrength;
                }

                float tintAlpha = dropTintIntensity * smoothstep(0.7, 1.0, dropStrength);
                vec4 tintLayer = vec4(dropTint * tintAlpha, tintAlpha);
                baseColor = tintLayer + baseColor * (1.0 - tintLayer.a);

                float highlightFactor = smoothstep(0.05, 0.08, max(max(dropUvMasked.x, dropUvMasked.y) * 1.7, max(droppletsUvMasked.x, droppletsUvMasked.y) * 2.6));
                float hlAlpha = highlightIntensity * highlightFactor;
                vec4 hlLayer = vec4(highlightColor * hlAlpha, hlAlpha);
                baseColor = hlLayer + baseColor * (1.0 - hlLayer.a);

                float shadowFactor = smoothstep(0.055, 0.1, max(length(dropUvMasked * 1.7), length(droppletsUvMasked * 1.9)));
                float shAlpha = dropShadowIntensity * shadowFactor;
                vec4 shLayer = vec4(contactShadowColor * shAlpha, shAlpha);
                baseColor = shLayer + baseColor * (1.0 - shLayer.a);

                vec4 lgLayer = vec4(vec3(1.0) * u_lightningAlpha, u_lightningAlpha);
                baseColor = lgLayer + baseColor * (1.0 - lgLayer.a);

                gl_FragColor = baseColor * u_globalAlpha;
            }
        """

        private const val GLSL_SNOW = """
            const mat2 rot45 = mat2(
                0.7071067812, 0.7071067812,
                -0.7071067812, 0.7071067812
            );

            const float farthestSnowLayerWiggleSpeed = 5.8;
            const float closestSnowLayerWiggleSpeed = 2.6;

            float generateSnow(vec2 uv, float screenAspectRatio, float time, vec2 snowGridSize, float layerIndex, float minLayerIndex, float maxLayerIndex, float intensity) {
                float normalizedLayerIndex = map(layerIndex, minLayerIndex, maxLayerIndex, 0.0, 1.0);

                float depth = 0.65 + layerIndex * 0.41;
                float speedAdj = 1.0 + layerIndex * 0.15;
                float layerR = idGenerator(layerIndex);
                snowGridSize *= depth;
                time += layerR * 58.3;

                float cellAspectRatio = snowGridSize.x / snowGridSize.y;
                snowGridSize.y /= screenAspectRatio;

                uv.x += uv.y * (0.8 * layerR - 0.4);
                vec2 gridUv = uv * snowGridSize;
                gridUv.y = 1.0 - gridUv.y;
                gridUv += vec2(10.0); 

                float verticalGridPos = 0.4 * time / speedAdj;
                gridUv.y += verticalGridPos;

                float columnId = idGenerator(floor(gridUv.x));
                gridUv.y += columnId * 2.6 + time * 0.09 * (1.0 - columnId);

                float cellId = idGenerator(floor(gridUv));
                vec2 cellUv = fract(gridUv) - 0.5;
                cellUv.y *= -1.0;

                float cellIntensity = idGenerator(floor(vec2(cellId * 856.16, 272.2)));
                if (cellIntensity < 1.0 - intensity) return 0.0;

                float visibilityFactor = smoothstep(cellIntensity, max(cellIntensity - (0.02 + 0.18 * intensity), 0.0), 1.0 - intensity);
                float decreaseFactor = 2.0 + map(cellId, 0.0, 1.0, -0.1, 2.8) + 5.0 * (1.0 - visibilityFactor);

                float farLayerFadeOut = map(normalizedLayerIndex, 0.7, 1.0, 1.0, 0.4);
                float closeLayerFadeOut = map(normalizedLayerIndex, 0.0, 0.2, 0.6, 1.0);
                float opacityVariation = (1.0 - 0.9 * cellId) * visibilityFactor * closeLayerFadeOut * farLayerFadeOut;

                float wiggleSpeed = map(normalizedLayerIndex, 0.2, 0.7, closestSnowLayerWiggleSpeed, farthestSnowLayerWiggleSpeed);
                float wiggleAmp = 0.6 + 0.4 * smoothstep(0.5, 2.5, layerIndex);
                float horizontalStartAmp = 0.5;
                float horizontalWiggle = wiggle(uv.y - cellUv.y / snowGridSize.y + cellId * 2.1, wiggleSpeed * speedAdj);
                horizontalWiggle = horizontalStartAmp * wiggleAmp * horizontalWiggle;

                float snowFlakePosUncorrected = (cellUv.x - horizontalWiggle);
                vec2 snowFlakeShape = vec2(1.0, 1.2);
                vec2 snowFlakePos = vec2(snowFlakePosUncorrected / cellAspectRatio, cellUv.y);
                snowFlakePos -= vec2(0.0, uv.y - 0.5) * cellId;
                snowFlakePos *= snowFlakeShape * decreaseFactor;

                vec2 snowFlakeShapeVariation = vec2(0.055) * vec2((cellId * 2.0 - 1.0), (fract((cellId + 0.03521) * 34.21) * 2.0 - 1.0));
                vec2 snowFlakePosR = 1.016 * abs(rot45 * (snowFlakePos + snowFlakeShapeVariation));
                snowFlakePos = abs(snowFlakePos);

                float flakeMask = (1.0 - smoothstep(0.200 - 0.3 * opacityVariation, 0.3, snowFlakePos.x + snowFlakePos.y + snowFlakePosR.x + snowFlakePosR.y)) * opacityVariation;

                return flakeMask;
            }
        """

        private const val RAIN_SHOWER_VERTEX_SHADER = "attribute vec4 a_Position; void main() { gl_Position = a_Position; }"

        private const val RAIN_SHOWER_FRAGMENT_SHADER = GLSL_UTILS + GLSL_RAIN_CONSTANTS + GLSL_RAIN_SHOWER + """
            uniform sampler2D u_texture;
            uniform int       u_hasTexture;
            uniform sampler2D u_foregroundTex;
            uniform int       u_hasForegroundTex;
            uniform float     u_time;
            uniform float     u_intensity;
            uniform float     u_globalAlpha;
            uniform vec2      u_screenSize;
            uniform float     u_screenAspectRatio;

            const float rainVisibility = 0.65;

            void main() {
                vec2 fragCoord = gl_FragCoord.xy;
                vec2 uv = fragCoord / u_screenSize;
                uv.y = 1.0 - uv.y;
                vec2 texUv = vec2(gl_FragCoord.x / u_screenSize.x, gl_FragCoord.y / u_screenSize.y);

                vec4 baseColor = vec4(0.0);
                if (u_hasTexture == 1) {
                    baseColor = texture2D(u_texture, texUv);
                    baseColor.rgb *= baseColor.a; 
                }

                float farMask = 0.0;
                Rain rain1 = generateRain(uv, u_screenAspectRatio, u_time * 18.0, vec2(20.0, 2.0), u_intensity);
                farMask += rain1.dropMask * rainVisibility;
                farMask = clamp(farMask, 0.0, 1.0);
                vec4 farLayer = vec4(highlightColor * farMask, farMask);
                baseColor = farLayer + baseColor * (1.0 - farLayer.a);

                if (u_hasForegroundTex == 1) {
                    vec4 fgColor = texture2D(u_foregroundTex, texUv);
                    fgColor.rgb *= fgColor.a;
                    baseColor = fgColor + baseColor * (1.0 - fgColor.a);
                }

                float nearMask = 0.0;
                Rain rain2 = generateRain(uv, u_screenAspectRatio, u_time * 21.4, vec2(30.0, 4.0), u_intensity);
                nearMask += rain2.dropMask * rainVisibility * 0.8;
                nearMask = clamp(nearMask, 0.0, 1.0);
                vec4 nearLayer = vec4(highlightColor * nearMask, nearMask);
                
                gl_FragColor = (nearLayer + baseColor * (1.0 - nearLayer.a)) * u_globalAlpha;
            }
        """

        private const val SNOW_VERTEX_SHADER = "attribute vec4 a_Position; void main() { gl_Position = a_Position; }"

        private const val SNOW_FRAGMENT_SHADER = GLSL_UTILS + GLSL_SNOW + """
            uniform sampler2D u_texture;
            uniform int       u_hasTexture;
            uniform sampler2D u_foregroundTex;
            uniform int       u_hasForegroundTex;
            uniform float     u_time;
            uniform float     u_intensity;
            uniform float     u_globalAlpha;
            uniform vec2      u_screenSize;
            uniform float     u_screenAspectRatio;

            void main() {
                vec2 uv = gl_FragCoord.xy / u_screenSize;
                uv.y = 1.0 - uv.y;
                vec2 texUv = vec2(gl_FragCoord.x / u_screenSize.x, gl_FragCoord.y / u_screenSize.y);

                vec4 baseColor = vec4(0.0);
                if (u_hasTexture == 1) {
                    baseColor = texture2D(u_texture, texUv);
                    baseColor.rgb *= baseColor.a;
                }

                float farMask = 0.0;
                farMask += generateSnow(uv, u_screenAspectRatio, u_time, vec2(7.0, 1.5), 2.0, 0.0, 2.0, u_intensity);
                farMask += generateSnow(uv, u_screenAspectRatio, u_time, vec2(7.0, 1.5), 1.0, 0.0, 2.0, u_intensity);
                farMask = clamp(farMask, 0.0, 1.0);
                vec4 farLayer = vec4(vec3(1.0) * farMask, farMask);
                baseColor = farLayer + baseColor * (1.0 - farLayer.a);

                if (u_hasForegroundTex == 1) {
                    vec4 fgColor = texture2D(u_foregroundTex, texUv);
                    fgColor.rgb *= fgColor.a;
                    baseColor = fgColor + baseColor * (1.0 - fgColor.a);
                }

                float nearMask = generateSnow(uv, u_screenAspectRatio, u_time, vec2(7.0, 1.5), 0.0, 0.0, 2.0, u_intensity);
                nearMask = clamp(nearMask, 0.0, 1.0);
                vec4 nearLayer = vec4(vec3(1.0) * nearMask, nearMask);
                
                gl_FragColor = (nearLayer + baseColor * (1.0 - nearLayer.a)) * u_globalAlpha;
            }
        """

        private const val SUN_VERTEX_SHADER = "attribute vec4 a_Position; void main() { gl_Position = a_Position; }"
        private const val FOG_VERTEX_SHADER = "attribute vec4 a_Position; void main() { gl_Position = a_Position; }"
        private const val GLASS_RAIN_VERTEX_SHADER = "attribute vec4 a_Position; void main() { gl_Position = a_Position; }"

        private const val GLSL_SIMPLEX_NOISE = """
            vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
            vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
            vec4 permute(vec4 x) { return mod289(((x * 34.0) + 1.0) * x); }
            vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }

            float simplex3d(vec3 v) {
                const vec2 C = vec2(1.0 / 6.0, 1.0 / 3.0);
                const vec4 D = vec4(0.0, 0.5, 1.0, 2.0);

                vec3 i = floor(v + dot(v, C.yyy));
                vec3 x0 = v - i + dot(i, C.xxx);

                vec3 g = step(x0.yzx, x0.xyz);
                vec3 l = 1.0 - g;
                vec3 i1 = min(g.xyz, l.zxy);
                vec3 i2 = max(g.xyz, l.zxy);

                vec3 x1 = x0 - i1 + C.xxx;
                vec3 x2 = x0 - i2 + C.yyy;
                vec3 x3 = x0 - D.yyy;

                i = mod289(i);
                vec4 p = permute(permute(permute(
                            i.z + vec4(0.0, i1.z, i2.z, 1.0))
                          + i.y + vec4(0.0, i1.y, i2.y, 1.0))
                          + i.x + vec4(0.0, i1.x, i2.x, 1.0));

                float n_ = 0.142857142857;
                vec3 ns = n_ * D.wyz - D.xzx;

                vec4 j = p - 49.0 * floor(p * ns.z * ns.z);

                vec4 x_ = floor(j * ns.z);
                vec4 y_ = floor(j - 7.0 * x_);

                vec4 x = x_ * ns.x + ns.yyyy;
                vec4 y = y_ * ns.x + ns.yyyy;
                vec4 h = 1.0 - abs(x) - abs(y);

                vec4 b0 = vec4(x.xy, y.xy);
                vec4 b1 = vec4(x.zw, y.zw);

                vec4 s0 = floor(b0) * 2.0 + 1.0;
                vec4 s1 = floor(b1) * 2.0 + 1.0;
                vec4 sh = -step(h, vec4(0.0));

                vec4 a0 = b0.xzyw + s0.xzyw * sh.xxyy;
                vec4 a1 = b1.xzyw + s1.xzyw * sh.zzww;

                vec3 p0 = vec3(a0.xy, h.x);
                vec3 p1 = vec3(a0.zw, h.y);
                vec3 p2 = vec3(a1.xy, h.z);
                vec3 p3 = vec3(a1.zw, h.w);

                vec4 norm = taylorInvSqrt(vec4(dot(p0, p0), dot(p1, p1), dot(p2, p2), dot(p3, p3)));
                p0 *= norm.x;
                p1 *= norm.y;
                p2 *= norm.z;
                p3 *= norm.w;

                vec4 m = max(0.6 - vec4(dot(x0, x0), dot(x1, x1), dot(x2, x2), dot(x3, x3)), 0.0);
                m = m * m;
                return 42.0 * dot(m * m, vec4(dot(p0, x0), dot(p1, x1), dot(p2, x2), dot(p3, x3)));
            }
        """

        private const val TURBULENCE_VERTEX_SHADER = "attribute vec4 a_Position; void main() { gl_Position = a_Position; }"

        private const val TURBULENCE_FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif

        """ + GLSL_SIMPLEX_NOISE + """
            uniform float u_gridNum;
            uniform vec3  u_noiseMove;
            uniform vec2  u_screenSize;
            uniform float u_aspectRatio;
            uniform float u_opacity;
            uniform float u_inverseLuma;
            uniform vec4  u_color;
            uniform float u_globalAlpha;

            void main() {
                vec2 uv = gl_FragCoord.xy / u_screenSize;
                uv.y = 1.0 - uv.y;
                uv.x *= u_aspectRatio;

                vec3 noiseP = vec3(uv + u_noiseMove.xy, u_noiseMove.z) * u_gridNum;
                float noiseVal = simplex3d(noiseP);

                float noiseAlpha = noiseVal * 0.5 + 0.5;
                if (u_inverseLuma < 0.0) noiseAlpha = 1.0 - noiseAlpha;
                noiseAlpha = smoothstep(0.2, 0.8, noiseAlpha);

                float finalAlpha = noiseAlpha * u_opacity * u_color.a * u_globalAlpha;
                gl_FragColor = vec4(u_color.rgb * finalAlpha, finalAlpha);
            }
        """

        private const val COMP_NOISE_SPEED = 0.2f
        private const val COMP_NOISE_SIZE = 1.7f

        // AOSP ShaderUtilLibrary.kt (Apache-2.0), integer hash + simplex kernel.
        // Separate from the Ashima noise used by the weather and music effects.
        private const val GLSL_COMPOSITE_SIMPLEX = """
        int imod(int a, int b) {
            return a - (b * (a / b));
        }

        ivec3 imod(ivec3 a, int b) {
            return ivec3(imod(a.x, b), imod(a.y, b), imod(a.z, b));
        }

        // Integer based hash function with the return range of [-1, 1].
        vec3 hash(vec3 p) {
            ivec3 v = ivec3(p);
            v = v * 1671731 + 10139267;

            v.x += v.y * v.z;
            v.y += v.z * v.x;
            v.z += v.x * v.y;

            ivec3 v2 = v / 65536; // v >> 16
            v = imod((10 - imod((v + v2), 10)), 10); // v ^ v2

            v.x += v.y * v.z;
            v.y += v.z * v.x;
            v.z += v.x * v.y;

            // Use sin and cos to map the range to [-1, 1].
            return vec3(sin(float(v.x)), cos(float(v.y)), sin(float(v.z)));
        }

        // Skew factors (non-uniform).
        const float SKEW = 0.3333333;  // 1/3
        const float UNSKEW = 0.1666667;  // 1/6

        // Return range roughly [-1,1].
        // It's because the hash function (that returns a random gradient vector) returns
        // different magnitude of vectors. Noise doesn't have to be in the precise range thus
        // skipped normalize.
        float simplex3d(vec3 p) {
            // Skew the input coordinate, so that we get squashed cubical grid
            vec3 s = floor(p + (p.x + p.y + p.z) * SKEW);

            // Unskew back
            vec3 u = s - (s.x + s.y + s.z) * UNSKEW;

            // Unskewed coordinate that is relative to p, to compute the noise contribution
            // based on the distance.
            vec3 c0 = p - u;

            // We have six simplices (in this case tetrahedron, since we are in 3D) that we
            // could possibly in.
            // Here, we are finding the correct tetrahedron (simplex shape), and traverse its
            // four vertices (c0..3) when computing noise contribution.
            // The way we find them is by comparing c0's x,y,z values.
            // For example in 2D, we can find the triangle (simplex shape in 2D) that we are in
            // by comparing x and y values. i.e. x>y lower, x<y, upper triangle.
            // Same applies in 3D.
            //
            // Below indicates the offsets (or offset directions) when c0=(x0,y0,z0)
            // x0>y0>z0: (1,0,0), (1,1,0), (1,1,1)
            // x0>z0>y0: (1,0,0), (1,0,1), (1,1,1)
            // z0>x0>y0: (0,0,1), (1,0,1), (1,1,1)
            // z0>y0>x0: (0,0,1), (0,1,1), (1,1,1)
            // y0>z0>x0: (0,1,0), (0,1,1), (1,1,1)
            // y0>x0>z0: (0,1,0), (1,1,0), (1,1,1)
            //
            // The rule is:
            // * For offset1, set 1 at the max component, otherwise 0.
            // * For offset2, set 0 at the min component, otherwise 1.
            // * For offset3, set 1 for all.
            //
            // Encode x0-y0, y0-z0, z0-x0 in a vec3
            vec3 en = c0 - c0.yzx;
            // Each represents whether x0>y0, y0>z0, z0>x0
            en = step(vec3(0.), en);
            // en.zxy encodes z0>x0, x0>y0, y0>x0
            vec3 offset1 = en * (1. - en.zxy); // find max
            vec3 offset2 = 1. - en.zxy * (1. - en); // 1-(find min)
            vec3 offset3 = vec3(1.);

            vec3 c1 = c0 - offset1 + UNSKEW;
            vec3 c2 = c0 - offset2 + UNSKEW * 2.;
            vec3 c3 = c0 - offset3 + UNSKEW * 3.;

            // Kernel summation: dot(max(0, r^2-d^2))^4, noise contribution)
            //
            // First compute d^2, squared distance to the point.
            vec4 w; // w = max(0, r^2 - d^2))
            w.x = dot(c0, c0);
            w.y = dot(c1, c1);
            w.z = dot(c2, c2);
            w.w = dot(c3, c3);

            // Noise contribution should decay to zero before they cross the simplex boundary.
            // Usually r^2 is 0.5 or 0.6;
            // 0.5 ensures continuity but 0.6 increases the visual quality for the application
            // where discontinuity isn't noticeable.
            w = max(0.6 - w, 0.);

            // Noise contribution from each point.
            vec4 nc;
            nc.x = dot(hash(s), c0);
            nc.y = dot(hash(s + offset1), c1);
            nc.z = dot(hash(s + offset2), c2);
            nc.w = dot(hash(s + offset3), c3);

            nc *= w*w*w*w;

            // Add all the noise contributions.
            // Should multiply by the possible max contribution to adjust the range in [-1,1].
            return dot(vec4(32.), nc);
        }

        """

        // OpenGL ES 2.0 (GLSL ES 1.00) conversion of AOSP ShaderUtilLibrary.kt
        private const val GLSL_AOSP_SHADER_UTIL_LIB = """
            float triangleNoise(vec2 n) {
                n  = fract(n * vec2(5.3987, 5.4421));
                n += dot(n.yx, n.xy + vec2(21.5351, 14.3137));
                float xy = n.x * n.y;
                return fract(xy * 95.4307) + fract(xy * 75.04961) - 1.0;
            }

            const float PI = 3.1415926535897932384626;

            // Matches Skia/AGSL smoothstep without GLSL ES 1.00 undefined behavior when edge0 >= edge1
            float agslSmoothstep(float edge0, float edge1, float x) {
                float diff = edge1 - edge0;
                if (abs(diff) < 0.00001) return 0.0;
                float t = clamp((x - edge0) / diff, 0.0, 1.0);
                return t * t * (3.0 - 2.0 * t);
            }

            float sparkles(vec2 uv, float t) {
                float n = triangleNoise(uv);
                float s = 0.0;
                for (int k = 0; k < 4; k++) {
                    float i = float(k);
                    float l = i * 0.01;
                    float h = l + 0.1;
                    float o = agslSmoothstep(n - l, h, n);
                    o *= abs(sin(PI * o * (t + 0.55 * i)));
                    s += o;
                }
                return s;
            }

            vec2 distort(vec2 p, float time, float distort_amount_radial, float distort_amount_xy) {
                float angle = atan(p.y, p.x);
                return p + vec2(sin(angle * 8.0 + time * 0.003 + 1.641),
                                cos(angle * 5.0 + 2.14 + time * 0.00412)) * distort_amount_radial
                         + vec2(sin(p.x * 0.01 + time * 0.00215 + 0.8123),
                                cos(p.y * 0.01 + time * 0.005931)) * distort_amount_xy;
            }

            float getLuminosity(vec3 c) {
                return 0.3 * c.r + 0.59 * c.g + 0.11 * c.b;
            }

            vec3 maskLuminosity(vec3 dest, float lum) {
                dest.rgb *= vec3(lum);
                dest = clamp(dest, vec3(0.0), vec3(1.0));
                return dest;
            }

            const mat3 rot1 = mat3(1.0, 0.0, 0.0, 0.0, 0.15, -0.98, 0.0, 0.98, 0.15);
            const mat3 rot2 = mat3(-0.95, 0.0, -0.3, 0.0, 1.0, 0.0, 0.3, 0.0, -0.95);
            const mat3 rot3 = mat3(1.0, 0.0, 0.0, 0.0, -0.44, -0.89, 0.0, 0.89, -0.44);

            float simplex3d_fractal(vec3 p) {
                return 0.675 * simplex3d(p * rot1) + 0.225 * simplex3d(2.0 * p * rot2)
                        + 0.075 * simplex3d(4.0 * p * rot3) + 0.025 * simplex3d(8.0 * p);
            }

            vec3 screen(vec3 dest, vec3 src) {
                return dest + src - dest * src;
            }
        """

        // OpenGL ES 2.0 (GLSL ES 1.00) conversion of AOSP SdfShaderLibrary.kt
        private const val GLSL_AOSP_SDF_SHADER_LIB = """
            float soften(float d, float blur) {
                float blurHalf = blur * 0.5;
                return smoothstep(-blurHalf, blurHalf, d);
            }

            float subtract(float outer, float inner) {
                return max(outer, -inner);
            }

            float sdCircle(vec2 p, float r) {
                float safeR = max(r, 0.0001);
                return (length(p) - safeR) / safeR;
            }

            float circleRing(vec2 p, float radius) {
                float thicknessHalf = radius * 0.25;
                float outerCircle = sdCircle(p, radius + thicknessHalf);
                float innerCircle = sdCircle(p, radius);
                return subtract(outerCircle, innerCircle);
            }
        """

        // OpenGL ES 2.0 (GLSL ES 1.00) conversion of AOSP CompositeLoadingShader + SparkleShader + CircularRevealShader
        private const val COMPOSITE_LOADING_FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            precision highp int;
            #else
            precision mediump float;
            #endif

            varying vec2 v_TexCoordCurrent;
            varying vec2 v_TexCoordNext;
            varying vec2 v_ScreenUv;

            uniform sampler2D u_TextureCurrent;
            uniform sampler2D u_TextureNext;
            uniform float u_Alpha;
            uniform float u_Reveal;
            uniform sampler2D u_BlurredBackground;
            uniform float u_HasBlur;
            uniform float u_GlobalAlpha;
            uniform mat4 u_ColorMatrix;
            uniform vec4 u_ColorOffset;

            uniform float u_time;
            uniform vec3  u_noiseMove;
            uniform float u_gridNum;
            uniform vec2  u_screenSize;
            uniform float u_aspectRatio;
            uniform float u_pixelDensity;
            uniform vec3  u_accentColor;
            uniform vec3  u_screenColor;
        """ + GLSL_COMPOSITE_SIMPLEX + GLSL_AOSP_SHADER_UTIL_LIB + GLSL_AOSP_SDF_SHADER_LIB + """
            vec4 applyColorFilter(vec4 color) {
                vec4 res = u_ColorMatrix * color + u_ColorOffset;
                return clamp(res, 0.0, 1.0);
            }

            // AOSP SparkleShader.kt evaluation
            vec3 evalSparkleMask(vec2 p, vec3 noiseP) {
                float luma = 1.0 - getLuminosity(vec3(simplex3d(noiseP)));
                luma = max(1.75 * luma - 1.3, 0.0);
                float cellSize = u_pixelDensity * 0.8;
                vec2 sparkleCell = p - mod(p, cellSize);
                float sparkle = sparkles(sparkleCell, u_time);
                return maskLuminosity(u_accentColor * sparkle, luma);
            }

            // TurbulenceNoiseShader.Type.SIMPLEX_NOISE, inverse luminosity.
            // LoadingAnimation does not set luma matte factors: RuntimeShader defaults
            // both uniforms to zero, leaving the dark background plus gamma-space dither.
            vec3 evalColorTurbulenceMask(vec2 p) {
                return u_screenColor + vec3(triangleNoise(p * u_pixelDensity) / 255.0);
            }

            void main() {
                vec2 safeScreenSize = max(u_screenSize, vec2(1.0));
                vec2 fragP = v_ScreenUv * safeScreenSize;
                float in_alpha = u_Alpha;
                // The blur buffer is an OpenGL render target, so its Y axis is inverted
                // relative to the Bitmap textures and AGSL's top-left coordinates.
                // The old image stays under the loading effect until the reveal mask
                // removes both together, exposing the incoming image underneath.
                vec4 bgNext = texture2D(u_TextureNext, clamp(v_TexCoordNext, 0.0, 1.0));
                vec4 bgColor = texture2D(u_TextureCurrent, clamp(v_TexCoordCurrent, 0.0, 1.0));
                if (u_HasBlur > 0.5) {
                    vec4 blurred = texture2D(u_BlurredBackground, vec2(v_ScreenUv.x, 1.0 - v_ScreenUv.y));
                    // The quarter-resolution buffer must not replace the sharp image
                    // on the first frame, even when its blur radius is near zero.
                    bgColor = mix(bgColor, blurred, in_alpha);
                }
                vec2 uv = fragP / safeScreenSize;
                uv.x *= u_aspectRatio;
                vec3 noiseP = vec3(uv + u_noiseMove.xy, u_noiseMove.z) * u_gridNum;

                vec3 sparkleMask = evalSparkleMask(fragP, noiseP);
                vec3 colorMask = evalColorTurbulenceMask(fragP);
                float sparkleAlpha = smoothstep(0.0, 0.75, in_alpha);
                vec3 effect = screen(screen(bgColor.rgb, u_screenColor), colorMask * 0.22)
                            + sparkleMask * sparkleAlpha;
                vec4 loadingColor = mix(bgColor, vec4(effect, 1.0), in_alpha);

                float radius = u_Reveal * max(safeScreenSize.x, safeScreenSize.y) * 2.0;
                float blur = mix(2.5, 1.0, u_Reveal);
                float revealMask = 1.0;
                if (radius > 0.0) {
                    revealMask = soften(sdCircle(fragP - safeScreenSize * 0.5, radius), blur);
                }
                // CircularRevealShader multiplies premultiplied RGBA, then the overlay
                // is composited SRC_OVER onto the incoming image.
                vec4 overlay = loadingColor * revealMask;
                vec4 finalColor = overlay + bgNext * (1.0 - overlay.a);
                finalColor = applyColorFilter(finalColor);
                gl_FragColor = finalColor * u_GlobalAlpha;
            }
        """
    }
}
