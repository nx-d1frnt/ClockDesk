package com.nxd1frnt.clockdesk2.ui.view.shader

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/** Shader that renders sparkles used in [CompositeLoadingShader]. */
@RequiresApi(33)
class SparkleShader : RuntimeShader(SPARKLE_SHADER) {

    companion object {
        // language=AGSL
        private const val UNIFORMS = """
            uniform float in_gridNum;
            uniform vec3 in_noiseMove;
            uniform vec2 in_size;
            uniform float in_aspectRatio;
            uniform half in_time;
            uniform half in_pixelDensity;
            layout(color) uniform vec4 in_color;
        """

        private const val MAIN_SHADER = """
            vec4 main(vec2 p) {
                vec2 uv = p / in_size.xy;
                uv.x *= in_aspectRatio;
                vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                // Inverse luminosity per spec.
                half luma = 1.0 - getLuminosity(vec3(simplex3d(noiseP)));
                luma = max(/* intensity= */ 1.75 * luma - /* dim= */ 1.3, 0.);
                float sparkle = sparkles(p - mod(p, in_pixelDensity * 0.8), in_time);
                return vec4(maskLuminosity(in_color.rgb * sparkle, luma) * in_color.a, in_color.a);
            }
        """

        private val SPARKLE_SHADER = UNIFORMS + ShaderUtilLibrary.SHADER_LIB + MAIN_SHADER
    }

    /** Sets noise move offset in x, y, and z direction. */
    fun setNoiseMove(x: Float, y: Float, z: Float) {
        setFloatUniform("in_noiseMove", x, y, z)
    }

    /** Sets the number of grids for generating noise. */
    fun setGridCount(gridNumber: Float = 1.0f) {
        setFloatUniform("in_gridNum", gridNumber)
    }

    /** Sets the size of the shader. */
    fun setSize(width: Float, height: Float) {
        setFloatUniform("in_size", width, height)
        setFloatUniform("in_aspectRatio", width / java.lang.Float.max(height, 0.001f))
    }

    /** Sets the pixel density of the screen. */
    fun setPixelDensity(pixelDensity: Float) {
        setFloatUniform("in_pixelDensity", pixelDensity)
    }

    fun setColor(color: Int) {
        setColorUniform("in_color", color)
    }

    fun setTime(time: Float) {
        setFloatUniform("in_time", time)
    }
}