package com.nxd1frnt.clockdesk2.ui.view.shader

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * Reveals whatever is behind the given image in a circular fashion.
 * Imagine a hole in the given image that grows until it's invisible.
 */
@RequiresApi(33)
class CircularRevealShader : RuntimeShader(REVEAL_SHADER) {

    companion object {
        // language=AGSL
        private const val UNIFORMS = """
            uniform shader in_src;
            uniform half in_radius;
            uniform vec2 in_maskCenter;
            uniform half in_blur;
        """

        private const val MAIN_SHADER = """
            vec4 main(vec2 p) {
                half4 src = in_src.eval(p);
                half mask = soften(sdCircle(p - in_maskCenter, in_radius), in_blur);
                return src * mask;
            }
        """

        private val REVEAL_SHADER =
            UNIFORMS +
                    SdfShaderLibrary.SHADER_SDF_OPERATION_LIB +
                    SdfShaderLibrary.CIRCLE_SDF +
                    MAIN_SHADER
    }

    fun setCenter(x: Float, y: Float) {
        setFloatUniform("in_maskCenter", x, y)
    }

    fun setRadius(radius: Float) {
        setFloatUniform("in_radius", radius)
    }

    fun setBlur(blur: Float) {
        setFloatUniform("in_blur", blur)
    }
}