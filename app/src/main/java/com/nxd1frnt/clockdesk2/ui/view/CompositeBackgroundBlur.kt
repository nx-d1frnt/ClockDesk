package com.nxd1frnt.clockdesk2.ui.view

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

/** Separable, mirror-tiled Gaussian approximation of LoadingAnimation's RenderEffect blur. */
internal class CompositeBackgroundBlur(
    private val createProgram: (String, String) -> Int
) {
    private var program = 0
    private var unavailable = false
    private val textures = IntArray(2)
    private val framebuffers = IntArray(2)
    private var width = 0
    private var height = 0
    private var cachedSource = 0
    private var cachedRadius = Float.NaN
    private var cachedViewWidth = 0
    private var cachedViewHeight = 0
    private val cachedCrop = FloatArray(4)
    private var position = -1
    private var source = -1
    private var crop = -1
    private var step = -1
    private var bitmap = -1
    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    /** Old names belong to the lost context and must never be deleted in the new one. */
    fun onContextCreated() {
        program = 0
        cachedSource = 0
        unavailable = false
        textures.fill(0)
        framebuffers.fill(0)
        width = 0
        height = 0
    }

    fun release() {
        if (width != 0 || textures.any { it != 0 }) releaseBuffers()
    }

    private fun releaseBuffers() {
        cachedSource = 0
        GLES20.glDeleteFramebuffers(2, framebuffers, 0)
        GLES20.glDeleteTextures(2, textures, 0)
        framebuffers.fill(0)
        textures.fill(0)
        width = 0
        height = 0
    }

    /** Returns a bottom-left-origin texture. Restores the caller's framebuffer and viewport. */
    fun render(texture: Int, cropTransform: FloatArray, viewWidth: Int, viewHeight: Int, radius: Float): Int {
        if (unavailable) return 0
        if (program == 0) {
            program = createProgram(VERTEX, FRAGMENT)
            if (program == 0) {
                unavailable = true
                android.util.Log.w("CompositeBackgroundBlur", "Gaussian blur shader unavailable; using unblurred background")
                return 0
            }
            position = GLES20.glGetAttribLocation(program, "a_Position")
            source = GLES20.glGetUniformLocation(program, "u_Source")
            crop = GLES20.glGetUniformLocation(program, "u_Crop")
            step = GLES20.glGetUniformLocation(program, "u_Step")
            bitmap = GLES20.glGetUniformLocation(program, "u_Bitmap")
        }
        // The outgoing bitmap and maximum blur stay fixed throughout the reveal.
        if (cachedSource == texture && cachedRadius == radius &&
            cachedViewWidth == viewWidth && cachedViewHeight == viewHeight &&
            cachedCrop.contentEquals(cropTransform)) return textures[1]
        val previousFramebuffer = IntArray(1)
        val previousViewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, previousFramebuffer, 0)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, previousViewport, 0)
        try {
            // Downsample only the blurred photograph, never the procedural sparkle mask.
            // Quarter resolution keeps the wide (80 dp) kernel affordable.
            val w = maxOf(1, ceil(viewWidth / 4.0).toInt())
            val h = maxOf(1, ceil(viewHeight / 4.0).toInt())
            if (w != width || h != height) {
                releaseBuffers()
                GLES20.glGenTextures(2, textures, 0)
                GLES20.glGenFramebuffers(2, framebuffers, 0)
                for (i in 0..1) {
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[i])
                    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, textures[i], 0)
                    if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                        releaseBuffers()
                        unavailable = true
                        android.util.Log.w("CompositeBackgroundBlur", "Gaussian blur framebuffer unavailable; using unblurred background")
                        return 0
                    }
                }
                width = w
                height = h
            }
            GLES20.glUseProgram(program)
            GLES20.glViewport(0, 0, width, height)
            quad.position(0)
            GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glEnableVertexAttribArray(position)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glUniform1i(source, 0)
            // Android hwui converts RenderEffect's legacy radius to Gaussian sigma.
            val sigma = if (radius > 0f) radius * 0.57735f + 0.5f else 0f
            for (i in 0..1) {
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffers[i])
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, if (i == 0) texture else textures[0])
                GLES20.glUniform1f(bitmap, if (i == 0) 1f else 0f)
                if (i == 0) GLES20.glUniform4fv(crop, 1, cropTransform, 0)
                else GLES20.glUniform4f(crop, 1f, 1f, 0f, 0f)
                // 49 taps spanning +/- 3 sigma, in view coordinates before cropping.
                GLES20.glUniform2f(step, if (i == 0) sigma / (8f * viewWidth) else 0f,
                    if (i == 1) sigma / (8f * viewHeight) else 0f)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
            GLES20.glDisableVertexAttribArray(position)
            cachedSource = texture
            cachedRadius = radius
            cachedViewWidth = viewWidth
            cachedViewHeight = viewHeight
            cropTransform.copyInto(cachedCrop)
            return textures[1]
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, previousFramebuffer[0])
            GLES20.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3])
        }
    }

    companion object {
        private const val VERTEX = """
            attribute vec2 a_Position;
            varying vec2 v_Uv;
            void main() {
                gl_Position = vec4(a_Position, 0.0, 1.0);
                v_Uv = a_Position * 0.5 + 0.5;
            }
        """
        private const val FRAGMENT = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 v_Uv;
            uniform sampler2D u_Source;
            uniform vec4 u_Crop;
            uniform vec2 u_Step;
            uniform float u_Bitmap;
            vec2 mirrorUv(vec2 uv) {
                return 1.0 - abs(mod(uv, 2.0) - 1.0);
            }
            void main() {
                vec4 sum = vec4(0.0);
                float weights = 0.0;
                for (int i = -24; i <= 24; i++) {
                    float x = float(i) / 8.0;
                    float weight = exp(-0.5 * x * x);
                    vec2 uv = mirrorUv(v_Uv + float(i) * u_Step);
                    // Bitmap uploads use top-left UVs; render targets use bottom-left UVs.
                    uv.y = mix(uv.y, 1.0 - uv.y, u_Bitmap);
                    uv = uv * u_Crop.xy + u_Crop.zw;
                    sum += texture2D(u_Source, uv) * weight;
                    weights += weight;
                }
                gl_FragColor = sum / weights;
            }
        """
    }
}
