package com.nxd1frnt.clockdesk2.ui.view.shader

import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * Shader rendered when images are loading. Composites:
 * - Sparkles   ([SparkleShader])
 * - Color turbulence noise clouds
 * - Displacement / blur handled externally via RenderEffect
 */
@RequiresApi(33)
class CompositeLoadingShader : RuntimeShader(LOADING_SHADER) {

    companion object {
        // language=AGSL
        private const val UNIFORMS = """
            uniform shader in_background;
            uniform shader in_sparkleMask;
            uniform shader in_colorMask;
            uniform half in_alpha;
            layout(color) uniform vec4 in_screenColor;
        """

        private const val MAIN_SHADER = """
            vec4 main(vec2 p) {
                half4 bgColor     = in_background.eval(p);
                half3 sparkleMask = in_sparkleMask.eval(p).rgb;
                half3 colorMask   = in_colorMask.eval(p).rgb;
                float sparkleAlpha = smoothstep(0, 0.75, in_alpha);
                half3 effect = screen(screen(bgColor.rgb, in_screenColor.rgb), colorMask * 0.22)
                             + sparkleMask * sparkleAlpha;
                return mix(bgColor, vec4(effect, 1.), in_alpha);
            }
        """

        private val LOADING_SHADER = UNIFORMS + ShaderUtilLibrary.SHADER_LIB + MAIN_SHADER
    }

    /** Sets the overall opacity of the effect (0 = transparent, 1 = fully opaque). */
    fun setAlpha(alpha: Float) {
        setFloatUniform("in_alpha", alpha)
    }

    /** Sets the color applied with screen blending on top of the background image. */
    fun setScreenColor(color: Int) {
        setColorUniform("in_screenColor", color)
    }

    /** Sets the sparkle layer (color-tinted sparkles turbulence noise shader). */
    fun setSparkle(sparkleTurbulenceMask: RuntimeShader) {
        setInputShader("in_sparkleMask", sparkleTurbulenceMask)
    }

    /** Sets the color turbulence layer. */
    fun setColorTurbulenceMask(colorTurbulenceMask: RuntimeShader) {
        setInputShader("in_colorMask", colorTurbulenceMask)
    }
}