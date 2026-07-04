package com.nxd1frnt.clockdesk2.utils

import android.os.Process
import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class PerformanceTracker {

    data class Metrics(
        val fps: Int,
        val renderTimeMs: Float,
        val gpuLoadPercent: Float,
        val cpuLoadPercent: Float
    )

    private val numCores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    
    // CPU Tracking variables
    private var lastCpuTime = 0L
    private var lastRealTime = 0L

    // GL Thread metrics
    private val frameCount = AtomicInteger(0)
    private val totalRenderTimeNano = AtomicLong(0L)
    private val glFrameCountForAvg = AtomicInteger(0)
    private var lastFpsUpdateTime = 0L

    // Cached current metrics
    private val currentMetrics = AtomicReference(Metrics(0, 0f, 0f, 0f))

    @Volatile
    var isEnabled: Boolean = false
        set(value) {
            field = value
            if (value) {
                reset()
            }
        }

    init {
        reset()
    }

    private fun reset() {
        lastCpuTime = Process.getElapsedCpuTime()
        lastRealTime = SystemClock.elapsedRealtime()
        frameCount.set(0)
        totalRenderTimeNano.set(0L)
        glFrameCountForAvg.set(0)
        lastFpsUpdateTime = SystemClock.elapsedRealtime()
        currentMetrics.set(Metrics(0, 0f, 0f, 0f))
    }

    /**
     * Called by the GL thread after rendering a frame.
     * @param renderTimeNs Time spent rendering the frame in nanoseconds.
     */
    fun trackFrame(renderTimeNs: Long) {
        if (!isEnabled) return

        frameCount.incrementAndGet()
        glFrameCountForAvg.incrementAndGet()
        totalRenderTimeNano.addAndGet(renderTimeNs)
    }

    /**
     * Periodically updates metrics (usually called once per second from a handler or main thread).
     */
    fun updateMetrics(): Metrics {
        if (!isEnabled) {
            return currentMetrics.get()
        }

        val now = SystemClock.elapsedRealtime()
        val elapsedRealMs = now - lastRealTime
        val elapsedFpsMs = now - lastFpsUpdateTime

        if (elapsedRealMs <= 100) return currentMetrics.get() // Avoid division by zero/very small intervals

        // 1. Calculate CPU load
        val currentCpuTime = Process.getElapsedCpuTime()
        val elapsedCpuMs = currentCpuTime - lastCpuTime

        // CPU Usage % = (CPU Time / Real Time) / Cores * 100
        val rawCpuLoad = (elapsedCpuMs.toFloat() / (elapsedRealMs * numCores)) * 100f
        val cpuLoad = rawCpuLoad.coerceIn(0f, 100f)

        lastCpuTime = currentCpuTime
        lastRealTime = now

        // 2. Calculate FPS & GPU render time
        var fps = 0
        var avgRenderTimeMs = 0f
        var gpuLoad = 0f

        if (elapsedFpsMs >= 500) { // Update FPS and rendering times at least every 500ms
            val frames = frameCount.getAndSet(0)
            fps = Math.round((frames.toFloat() / elapsedFpsMs) * 1000f)

            val totalNano = totalRenderTimeNano.getAndSet(0L)
            val avgCount = glFrameCountForAvg.getAndSet(0)
            
            if (avgCount > 0) {
                avgRenderTimeMs = (totalNano.toFloat() / avgCount) / 1_000_000f
                
                // Estimate GPU load: Render time relative to frame budget.
                // Assuming 60fps (16.6ms) budget as baseline, or dynamic depending on actual refresh rate.
                // We clamp to 100% maximum estimated load.
                val frameBudgetMs = 16.67f
                gpuLoad = ((avgRenderTimeMs / frameBudgetMs) * 100f).coerceIn(0f, 100f)
            }
            
            lastFpsUpdateTime = now
            
            val newMetrics = Metrics(fps, avgRenderTimeMs, gpuLoad, cpuLoad)
            currentMetrics.set(newMetrics)
            return newMetrics
        } else {
            // Keep previous FPS & GPU metrics, just update CPU
            val prev = currentMetrics.get()
            val newMetrics = Metrics(prev.fps, prev.renderTimeMs, prev.gpuLoadPercent, cpuLoad)
            currentMetrics.set(newMetrics)
            return newMetrics
        }
    }

    fun getMetrics(): Metrics = currentMetrics.get()
}
