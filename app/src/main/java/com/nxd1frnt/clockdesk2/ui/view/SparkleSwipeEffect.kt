/*
 * Copyright (C) 2022 The Android Open Source Project
 * Copyright (C) 2025 The Android Open Source Project
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

import android.opengl.GLES20
import java.nio.FloatBuffer

/** A diagonal image wipe with the sparkle texture and timing used by SystemUI's RippleShader. */
internal class SparkleSwipeEffect(private val createProgram: (String, String) -> Int) {
    private var program = 0
    val isAvailable: Boolean get() = program != 0
    private var position = -1
    private var texCoord = -1
    private var currentCrop = -1
    private var nextCrop = -1
    private var currentTexture = -1
    private var nextTexture = -1
    private var progress = -1
    private var time = -1
    private var size = -1
    private var density = -1
    private var accent = -1
    private var alpha = -1
    private var matrix = -1
    private var offset = -1

    fun onContextCreated(vertexShader: String) {
        program = createProgram(vertexShader, FRAGMENT_SHADER)
        if (program == 0) return
        position = GLES20.glGetAttribLocation(program, "a_Position")
        texCoord = GLES20.glGetAttribLocation(program, "a_TexCoord")
        currentCrop = GLES20.glGetUniformLocation(program, "u_UvTransformCurrent")
        nextCrop = GLES20.glGetUniformLocation(program, "u_UvTransformNext")
        currentTexture = GLES20.glGetUniformLocation(program, "u_TextureCurrent")
        nextTexture = GLES20.glGetUniformLocation(program, "u_TextureNext")
        progress = GLES20.glGetUniformLocation(program, "u_Progress")
        time = GLES20.glGetUniformLocation(program, "u_Time")
        size = GLES20.glGetUniformLocation(program, "u_Size")
        density = GLES20.glGetUniformLocation(program, "u_Density")
        accent = GLES20.glGetUniformLocation(program, "u_Accent")
        alpha = GLES20.glGetUniformLocation(program, "u_GlobalAlpha")
        matrix = GLES20.glGetUniformLocation(program, "u_ColorMatrix")
        offset = GLES20.glGetUniformLocation(program, "u_ColorOffset")
    }

    fun draw(
        vertices: FloatBuffer, oldTexture: Int, newTexture: Int,
        oldCrop: FloatArray, newCrop: FloatArray,
        fraction: Float, seconds: Float, width: Int, height: Int, pixelDensity: Float,
        red: Float, green: Float, blue: Float, globalAlpha: Float,
        colorMatrix: FloatArray, colorOffset: FloatArray
    ) {
        GLES20.glUseProgram(program)
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 3, GLES20.GL_FLOAT, false, 5 * 4, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(3)
        GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 5 * 4, vertices)
        GLES20.glEnableVertexAttribArray(texCoord)
        GLES20.glUniform4fv(currentCrop, 1, oldCrop, 0)
        GLES20.glUniform4fv(nextCrop, 1, newCrop, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, oldTexture)
        GLES20.glUniform1i(currentTexture, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, newTexture)
        GLES20.glUniform1i(nextTexture, 1)
        GLES20.glUniform1f(progress, fraction)
        GLES20.glUniform1f(time, seconds)
        GLES20.glUniform2f(size, width.toFloat(), height.toFloat())
        GLES20.glUniform1f(density, pixelDensity)
        GLES20.glUniform3f(accent, red, green, blue)
        GLES20.glUniform1f(alpha, globalAlpha)
        GLES20.glUniformMatrix4fv(matrix, 1, false, colorMatrix, 0)
        GLES20.glUniform4fv(offset, 1, colorOffset, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    companion object {
        // Sparkle functions adapted from SystemUI ShaderUtilLibrary; pixelation, sparkle
        // speed (milliseconds * 0.00175), and strength (0.3) follow RippleShader.
        internal const val FRAGMENT_SHADER = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 v_TexCoordCurrent;
            varying vec2 v_TexCoordNext;
            varying vec2 v_ScreenUv;
            uniform sampler2D u_TextureCurrent;
            uniform sampler2D u_TextureNext;
            uniform float u_Progress;
            uniform float u_Time;
            uniform vec2 u_Size;
            uniform float u_Density;
            uniform vec3 u_Accent;
            uniform float u_GlobalAlpha;
            uniform mat4 u_ColorMatrix;
            uniform vec4 u_ColorOffset;

            float triangleNoise(vec2 n) {
                n = fract(n * vec2(5.3987, 5.4421));
                n += dot(n.yx, n.xy + vec2(21.5351, 14.3137));
                float xy = n.x * n.y;
                return fract(xy * 95.4307) + fract(xy * 75.04961) - 1.0;
            }
            float sparkleStep(float lo, float hi, float x) {
                float delta = hi - lo;
                if (abs(delta) < 0.00001) return 0.0;
                float t = clamp((x - lo) / delta, 0.0, 1.0);
                return t * t * (3.0 - 2.0 * t);
            }
            float sparkles(vec2 p, float t) {
                float n = triangleNoise(p);
                float sum = 0.0;
                for (int k = 0; k < 4; k++) {
                    float i = float(k);
                    float lo = i * 0.01;
                    float s = sparkleStep(n - lo, lo + 0.1, n);
                    sum += s * abs(sin(3.14159265359 * s * (t + 0.55 * i)));
                }
                return sum;
            }
            void main() {
                vec4 oldImage = texture2D(u_TextureCurrent, clamp(v_TexCoordCurrent, 0.0, 1.0));
                vec4 newImage = texture2D(u_TextureNext, clamp(v_TexCoordNext, 0.0, 1.0));
                float p = clamp(u_Progress, 0.0, 1.0);
                // Move from bottom-right to top-left, independently of wallpaper zoom/crop.
                float axis = (v_ScreenUv.x + v_ScreenUv.y) * 0.5;
                float front = mix(1.15, -0.15, p);
                float reveal = smoothstep(front - 0.035, front + 0.035, axis);
                float band = 1.0 - smoothstep(0.025, 0.10, abs(axis - front));
                float fade = smoothstep(0.0, 0.1, p) * (1.0 - smoothstep(0.4, 1.0, p));
                vec2 pixel = v_ScreenUv * u_Size;
                float cell = max(u_Density * 0.8, 0.001);
                float glitter = clamp(sparkles(pixel - mod(pixel, cell), u_Time * 1.75), 0.0, 1.0);
                vec4 image = mix(oldImage, newImage, reveal);
                vec3 tint = mix(u_Accent, vec3(1.0), 0.65);
                vec3 halo = tint * band * fade * 0.08;
                image.rgb += (vec3(1.0) - image.rgb) * halo * image.a;
                image.rgb = mix(image.rgb, vec3(image.a), glitter * band * fade * 0.3);
                // Guarantee clean endpoints even at low fragment precision.
                if (p <= 0.0) image = oldImage;
                if (p >= 1.0) image = newImage;
                gl_FragColor = clamp(u_ColorMatrix * image + u_ColorOffset, 0.0, 1.0) * u_GlobalAlpha;
            }
        """
    }
}
