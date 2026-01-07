package com.nxd1frnt.clockdesk2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class WeatherView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class WeatherType { NONE, RAIN, SNOW, FOG, THUNDERSTORM, CLOUDY, CLEAR }

    private var currentWeather = WeatherType.NONE
    private var isNight = false

    private var windFactor: Float = 0f
    private var intensity: Float = 1.0f

    private var lightningAlpha: Int = 0
    private var nextLightningFrame: Int = 0

    private val maxParticles = 800
    private val particles = ArrayList<Particle>(maxParticles)


    private lateinit var softTextureBitmap: Bitmap
    private lateinit var smallTextureBitmap: Bitmap

    private val destRect = android.graphics.RectF()

    private var screenWidth = 0
    private var screenHeight = 0
    private var lastUpdateTime = 0L
    private val targetFrameTime = 16L // ~60 FPS

    private val rainPaint = Paint().apply {
        color = Color.parseColor("#90FFFFFF")
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val snowPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val texturePaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = true
        isDither = false
    }

    private val starPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private class Particle {
        var x: Float = 0f
        var y: Float = 0f
        var speedY: Float = 0f
        var speedX: Float = 0f
        var size: Float = 0f
        var scaleX: Float = 1f
        var alpha: Int = 255
        var angle: Float = 0f
        var alphaSpeed: Float = 0f
        var active: Boolean = false
        var layer: Int = 0

        fun reset(w: Int, h: Int, type: WeatherType, intensityMult: Float, wind: Float, isNight: Boolean) {
            val windOffset = abs(wind * h)
            scaleX = 1f

            when (type) {
                WeatherType.RAIN, WeatherType.THUNDERSTORM -> {
                    x = Random.nextFloat() * (w + windOffset * 2) - windOffset
                    y = -Random.nextFloat() * h * 0.5f
                    speedY = (30f + Random.nextFloat() * 15f) * (0.8f + intensityMult * 0.2f)
                    size = 20f + Random.nextFloat() * 30f
                    alpha = (100 + Random.nextInt(155)).coerceAtMost(255)
                }
                WeatherType.SNOW -> {
                    x = Random.nextFloat() * (w + windOffset * 2) - windOffset
                    y = -Random.nextFloat() * h * 0.5f
                    speedY = 2f + Random.nextFloat() * 4f
                    size = 4f + Random.nextFloat() * 6f
                    alpha = 150 + Random.nextInt(105)
                    angle = Random.nextFloat() * 360f
                }
                WeatherType.FOG -> {
                    x = Random.nextFloat() * w
                    y = Random.nextFloat() * h
                    speedX = (Random.nextFloat() - 0.5f) * 0.5f + (wind * 0.05f)
                    speedY = (Random.nextFloat() - 0.5f) * 0.2f
                    size = 200f + Random.nextFloat() * 200f
                    scaleX = 1.2f + Random.nextFloat() * 0.8f
                    alpha = 15 + Random.nextInt(35)
                    layer = if (Random.nextBoolean()) 0 else 1
                }
                WeatherType.CLOUDY -> {
                    x = Random.nextFloat() * w
                    y = Random.nextFloat() * h
                    speedX = (wind * 0.2f) + (Random.nextFloat() - 0.5f) * 0.5f
                    speedY = 0f
                    size = 250f + Random.nextFloat() * 250f
                    scaleX = 1.0f + Random.nextFloat() * 0.5f
                    alpha = 10 + Random.nextInt(20)
                    layer = if (Random.nextBoolean()) 0 else 1
                }
                WeatherType.CLEAR -> {
                    if (isNight) {
                        x = Random.nextFloat() * w
                        y = Random.nextFloat() * h
                        speedX = 0f; speedY = 0f
                        size = 2f + Random.nextFloat() * 4f
                        alpha = Random.nextInt(255)
                        angle = Random.nextFloat() * 360f
                        alphaSpeed = 0.02f + Random.nextFloat() * 0.03f
                    } else {
                        x = Random.nextFloat() * w
                        y = Random.nextFloat() * h
                        speedX = (Random.nextFloat() - 0.5f) * 0.3f
                        speedY = (Random.nextFloat() - 0.5f) * 0.3f
                        size = 50f + Random.nextFloat() * 150f
                        alpha = 10 + Random.nextInt(30)
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

    init {
        for (i in 0 until maxParticles) particles.add(Particle())
        createTextures()
        resetLightningTimer()
    }

    private fun createTextures() {
        val size = 128
        softTextureBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(softTextureBitmap)
        val paint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                size / 2f, size / 2f, size / 2f,
                Color.WHITE, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        val smallSize = 64
        smallTextureBitmap = Bitmap.createBitmap(smallSize, smallSize, Bitmap.Config.ARGB_8888)
        val smallCanvas = Canvas(smallTextureBitmap)
        val smallPaint = Paint().apply {
            isAntiAlias = true
            shader = RadialGradient(
                smallSize / 2f, smallSize / 2f, smallSize / 2f,
                Color.WHITE, Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        smallCanvas.drawCircle(smallSize / 2f, smallSize / 2f, smallSize / 2f, smallPaint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w
        screenHeight = h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastUpdateTime

        if (lightningAlpha > 0) {
            canvas.drawColor(Color.argb(lightningAlpha, 255, 255, 255))
            lightningAlpha -= 15
            postInvalidateOnAnimation()
        }

        if (currentWeather == WeatherType.THUNDERSTORM) updateLightning()
        if (currentWeather == WeatherType.NONE) return

        val w = width
        val h = height
        if (w == 0 || h == 0) return

        val activeLimit = when (currentWeather) {
            WeatherType.FOG -> {
                (15 + (25 * intensity)).toInt().coerceIn(10, 40)
            }
            WeatherType.CLOUDY -> {
                (15 + (25 * intensity)).toInt().coerceIn(10, 40) // Уменьшено с 80
            }
            WeatherType.CLEAR -> {
                if (isNight) {
                    (50 + (200 * intensity)).toInt().coerceIn(50, 300)
                } else {
                    (10 + (50 * intensity)).toInt().coerceIn(10, 80)
                }
            }
            else -> {
                (maxParticles * intensity).toInt().coerceIn(0, maxParticles)
            }
        }

        val shouldUpdate = when (currentWeather) {
            WeatherType.FOG, WeatherType.CLOUDY -> deltaTime >= targetFrameTime * 2
            else -> true
        }

        for (i in 0 until maxParticles) {
            val p = particles[i]
            if (i >= activeLimit) {
                p.active = false
                continue
            }
            if (!p.active) p.reset(w, h, currentWeather, intensity, windFactor, isNight)

            if (shouldUpdate) {
                updateParticle(p, w, h)
            }

            if (p.isVisible(w, h)) {
                drawParticle(canvas, p)
            }
        }

        if (shouldUpdate) {
            lastUpdateTime = currentTime
        }

        postInvalidateOnAnimation()
    }

    private fun updateParticle(p: Particle, w: Int, h: Int) {
        when (currentWeather) {
            WeatherType.RAIN, WeatherType.THUNDERSTORM -> {
                p.x += windFactor
                p.y += p.speedY
            }
            WeatherType.SNOW -> {
                p.x += windFactor
                p.y += p.speedY
                p.angle += 0.03f
                p.x += sin(p.angle) * 1.5f
            }
            WeatherType.FOG, WeatherType.CLOUDY -> {
                p.x += p.speedX * intensity
                p.y += p.speedY * intensity
                val margin = p.size * 2
                if (p.x > w + margin) p.x = -margin
                if (p.x < -margin) p.x = w + margin
                return
            }
            WeatherType.CLEAR -> {
                if (isNight) {
                    p.angle += p.alphaSpeed * intensity
                    val blink = (sin(p.angle) * 0.5f + 0.5f)
                    p.alpha = (50 + (blink * 200)).toInt()
                } else {
                    p.x += p.speedX * intensity
                    p.y += p.speedY * intensity
                    val margin = p.size
                    if (p.x > w + margin) p.x = -margin
                    if (p.x < -margin) p.x = w + margin
                    if (p.y > h + margin) p.y = -margin
                    if (p.y < -margin) p.y = h + margin
                }
                return
            }
            else -> {}
        }

        if (p.y > h + p.size) {
            p.reset(w, h, currentWeather, intensity, windFactor, isNight)
            p.y = -p.size * 2
        }
    }

    private fun drawParticle(canvas: Canvas, p: Particle) {
        when (currentWeather) {
            WeatherType.RAIN, WeatherType.THUNDERSTORM -> {
                rainPaint.alpha = p.alpha
                val tailY = p.y + p.size
                val horizontalOffset = if (p.speedY != 0f) {
                    (windFactor / p.speedY) * p.size * 0.8f
                } else {
                    windFactor * 2f
                }
                val tailX = p.x + horizontalOffset
                canvas.drawLine(p.x, p.y, tailX, tailY, rainPaint)
            }
            WeatherType.SNOW -> {
                snowPaint.alpha = p.alpha
                canvas.drawCircle(p.x, p.y, p.size, snowPaint)
            }
            WeatherType.FOG, WeatherType.CLOUDY -> {
                val adjustedAlpha = (p.alpha * (0.5f + intensity * 0.5f)).toInt().coerceIn(0, 255)
                texturePaint.alpha = adjustedAlpha
                drawTextureOptimized(canvas, p, true)
            }
            WeatherType.CLEAR -> {
                if (isNight) {
                    val adjustedAlpha = (p.alpha * (0.3f + intensity * 0.7f)).toInt().coerceIn(0, 255)
                    starPaint.alpha = adjustedAlpha
                    canvas.drawCircle(p.x, p.y, p.size, starPaint)
                } else {
                    val adjustedAlpha = (p.alpha * (0.3f + intensity * 0.7f)).toInt().coerceIn(0, 255)
                    texturePaint.alpha = adjustedAlpha
                    drawTextureOptimized(canvas, p, false)
                }
            }
            else -> {}
        }
    }

    private fun drawTextureOptimized(canvas: Canvas, p: Particle, useSmall: Boolean) {
        val halfSize = p.size / 2f
        val left = p.x - halfSize * p.scaleX
        val top = p.y - halfSize
        val right = p.x + halfSize * p.scaleX
        val bottom = p.y + halfSize
        destRect.set(left, top, right, bottom)

        val bitmap = if (useSmall) smallTextureBitmap else softTextureBitmap
        canvas.drawBitmap(bitmap, null, destRect, texturePaint)
    }

    private fun updateLightning() {
        if (nextLightningFrame > 0) {
            nextLightningFrame--
        } else {
            lightningAlpha = 150 + Random.nextInt(80)
            resetLightningTimer()
        }
    }

    private fun resetLightningTimer() {
        nextLightningFrame = 100 + Random.nextInt(500)
    }

    fun forceWeather(type: WeatherType, intens: Float, wind: Float, night: Boolean) {
        if (currentWeather != type || isNight != night) {
            currentWeather = type
            isNight = night
            particles.forEach { it.active = false }
        }
        windFactor = wind
        intensity = intens
        invalidate()
    }

    fun updateFromOpenMeteo(wmoCode: Int, windSpeedKmh: Double, night: Boolean) {
        windFactor = (windSpeedKmh.toFloat() / 2f).coerceIn(-20f, 20f)

        if (isNight != night && currentWeather == WeatherType.CLEAR) {
            particles.forEach { it.active = false }
        }
        isNight = night

        when (wmoCode) {
            0 -> configure(WeatherType.CLEAR, 1.0f)
            1, 2, 3 -> configure(WeatherType.CLOUDY, 0.5f)
            45, 48 -> configure(WeatherType.FOG, 0.6f)
            51, 53, 55 -> configure(WeatherType.RAIN, 0.4f)
            61, 63 -> configure(WeatherType.RAIN, 1.0f)
            65, 80, 81, 82 -> configure(WeatherType.RAIN, 1.6f)
            71, 73, 75, 85, 86 -> configure(WeatherType.SNOW, 0.8f)
            95, 96, 99 -> configure(WeatherType.THUNDERSTORM, 1.8f)
            else -> configure(WeatherType.NONE, 0f)
        }
    }

    fun updateFromOpenMeteoSmart(
        wmoCode: Int,
        windSpeedKmh: Double,
        night: Boolean,
        precipitation: Double?,
        cloudCover: Int?,
        visibility: Double?
    ) {
        windFactor = (windSpeedKmh.toFloat() / 2f).coerceIn(-20f, 20f)

        if (isNight != night && currentWeather == WeatherType.CLEAR) {
            particles.forEach { it.active = false }
        }
        isNight = night

        val calculatedIntensity = calculateWeatherIntensity(
            wmoCode, windSpeedKmh, precipitation, cloudCover, visibility
        )

        when (wmoCode) {
            0 -> configure(WeatherType.CLEAR, calculatedIntensity)
            1, 2, 3 -> configure(WeatherType.CLOUDY, calculatedIntensity)
            45, 48 -> configure(WeatherType.FOG, calculatedIntensity)
            51, 53, 55, 56, 57 -> configure(WeatherType.RAIN, calculatedIntensity)
            61, 63, 65, 80, 81, 82 -> configure(WeatherType.RAIN, calculatedIntensity)
            71, 73, 75, 77, 85, 86 -> configure(WeatherType.SNOW, calculatedIntensity)
            95, 96, 99 -> configure(WeatherType.THUNDERSTORM, calculatedIntensity)
            else -> configure(WeatherType.NONE, 0f)
        }
    }

    private fun configure(type: WeatherType, intens: Float) {
        if (currentWeather != type) {
            currentWeather = type
            particles.forEach { it.active = false }
        }
        intensity = intens
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (::softTextureBitmap.isInitialized && !softTextureBitmap.isRecycled) {
            softTextureBitmap.recycle()
        }
        if (::smallTextureBitmap.isInitialized && !smallTextureBitmap.isRecycled) {
            smallTextureBitmap.recycle()
        }
    }
}