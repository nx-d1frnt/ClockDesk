/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.Float.max

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class TurbulenceNoiseShader(val baseType: Type = Type.SIMPLEX_NOISE) : RuntimeShader(getShader(baseType)) {

    companion object {

        private const val SHADER_LIB = """
            // Simplex 3D Noise function integration
            vec3 mod289(vec3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
            vec4 mod289(vec4 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
            vec4 permute(vec4 x) { return mod289(((x*34.0)+1.0)*x); }
            vec4 taylorInvSqrt(vec4 r) { return 1.79284291400159 - 0.85373472095314 * r; }
            
            float simplex3d(vec3 v) {
              const vec2  C = vec2(1.0/6.0, 1.0/3.0) ;
              const vec4  D = vec4(0.0, 0.5, 1.0, 2.0);

              vec3 i  = floor(v + dot(v, C.yyy) );
              vec3 x0 = v - i + dot(i, C.xxx) ;

              vec3 g = step(x0.yzx, x0.xyz);
              vec3 l = 1.0 - g;
              vec3 i1 = min( g.xyz, l.zxy );
              vec3 i2 = max( g.xyz, l.zxy );

              vec3 x1 = x0 - i1 + C.xxx;
              vec3 x2 = x0 - i2 + C.yyy;
              vec3 x3 = x0 - D.yyy;

              i = mod289(i); 
              vec4 p = permute( permute( permute( 
                         i.z + vec4(0.0, i1.z, i2.z, 1.0 ))
                       + i.y + vec4(0.0, i1.y, i2.y, 1.0 )) 
                       + i.x + vec4(0.0, i1.x, i2.x, 1.0 ));

              float n_ = 0.142857142857;
              vec3  ns = n_ * D.wyz - D.xzx;

              vec4 j = p - 49.0 * floor(p * ns.z * ns.z);

              vec4 x_ = floor(j * ns.z);
              vec4 y_ = floor(j - 7.0 * x_ );

              vec4 x = x_ *ns.x + ns.yyyy;
              vec4 y = y_ *ns.x + ns.yyyy;
              vec4 h = 1.0 - abs(x) - abs(y);

              vec4 b0 = vec4( x.xy, y.xy );
              vec4 b1 = vec4( x.zw, y.zw );

              vec4 s0 = floor(b0)*2.0 + 1.0;
              vec4 s1 = floor(b1)*2.0 + 1.0;
              vec4 sh = -step(h, vec4(0.0));

              vec4 a0 = b0.xzyw + s0.xzyw*sh.xxyy ;
              vec4 a1 = b1.xzyw + s1.xzyw*sh.zzww ;

              vec3 p0 = vec3(a0.xy,h.x);
              vec3 p1 = vec3(a0.zw,h.y);
              vec3 p2 = vec3(a1.xy,h.z);
              vec3 p3 = vec3(a1.zw,h.w);

              vec4 norm = taylorInvSqrt(vec4(dot(p0,p0), dot(p1,p1), dot(p2, p2), dot(p3,p3)));
              p0 *= norm.x;
              p1 *= norm.y;
              p2 *= norm.z;
              p3 *= norm.w;

              vec4 m = max(0.6 - vec4(dot(x0,x0), dot(x1,x1), dot(x2,x2), dot(x3,x3)), 0.0);
              m = m * m;
              return 42.0 * dot( m*m, vec4( dot(p0,x0), dot(p1,x1), 
                                            dot(p2,x2), dot(p3,x3) ) );
            }
        """

        private const val UNIFORMS = """
            uniform float in_gridNum;
            uniform vec3 in_noiseMove;
            uniform vec2 in_size;
            uniform float in_aspectRatio;
            uniform float in_opacity;
            uniform float in_inverseLuma;
            layout(color) uniform vec4 in_color;
        """
        private const val SIMPLEX_SHADER = """
            vec4 main(vec2 p) {
                vec2 uv = p / in_size.xy;
                uv.x *= in_aspectRatio;

                // Генерация шума
                vec3 noiseP = vec3(uv + in_noiseMove.xy, in_noiseMove.z) * in_gridNum;
                float noiseVal = simplex3d(noiseP);
                
                // Нормализация
                float noiseAlpha = noiseVal * 0.5 + 0.5;
                if (in_inverseLuma < 0.0) noiseAlpha = 1.0 - noiseAlpha;
                noiseAlpha = smoothstep(0.2, 0.8, noiseAlpha);
                
                // === ИЗМЕНЕНИЕ: Учитываем альфа-канал самого цвета ===
                // in_color.a - это прозрачность, которую мы передадим (например, 0.5 для 50%)
                float finalAlpha = noiseAlpha * in_opacity * in_color.a;
                
                // Premultiplied Alpha: умножаем RGB на итоговую прозрачность
                return vec4(in_color.rgb * finalAlpha, finalAlpha);
            }
        """

        private const val FULL_SHADER = SHADER_LIB + UNIFORMS + SIMPLEX_SHADER

        enum class Type { SIMPLEX_NOISE }

        fun getShader(type: Type): String {
            return FULL_SHADER
        }
    }

    fun applyConfig(config: TurbulenceNoiseAnimationConfig) {
        setFloatUniform("in_gridNum", config.gridCount)
        setColorUniform("in_color", config.color)
        setFloatUniform("in_size", config.width, config.height)
        setFloatUniform("in_aspectRatio", config.width / max(config.height, 0.001f))
        setFloatUniform("in_inverseLuma", if (config.shouldInverseNoiseLuminosity) -1f else 1f)
        setNoiseMove(config.noiseOffsetX, config.noiseOffsetY, config.noiseOffsetZ)
    }

    fun setColor(color: Int) {
        setColorUniform("in_color", color)
    }

    fun setOpacity(opacity: Float) {
        setFloatUniform("in_opacity", opacity)
    }

    var noiseOffsetX: Float = 0f; private set
    var noiseOffsetY: Float = 0f; private set
    var noiseOffsetZ: Float = 0f; private set

    fun setNoiseMove(x: Float, y: Float, z: Float) {
        noiseOffsetX = x; noiseOffsetY = y; noiseOffsetZ = z
        setFloatUniform("in_noiseMove", x, y, z)
    }
}