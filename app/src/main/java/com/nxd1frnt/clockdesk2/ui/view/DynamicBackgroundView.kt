package com.nxd1frnt.clockdesk2.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.AttributeSet
import android.view.animation.AccelerateDecelerateInterpolator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class DynamicBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer: BackgroundRenderer
    private var transitionAnimator: ValueAnimator? = null

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        setZOrderMediaOverlay(false)

        renderer = BackgroundRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun transitionTo(bitmap: Bitmap, durationMs: Long = 1800L) {
        queueEvent {
            renderer.setNextTexture(bitmap)

            post {
                transitionAnimator?.cancel()
                transitionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = durationMs
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { anim ->
                        val progress = anim.animatedValue as Float
                        renderer.setTransitionProgress(progress)
                        requestRender()
                    }
                    start()
                }
            }
        }
    }

    fun setColorFilter(matrix: ColorMatrix) {
        queueEvent {
            renderer.updateColorMatrix(matrix)
            requestRender()
        }
    }

    override fun onPause() {
        transitionAnimator?.cancel()
        super.onPause()
    }

    private inner class BackgroundRenderer : Renderer {
        private var program = 0

        private var positionHandle = 0
        private var texCoordHandle = 0
        private var uvTransformCurrentHandle = 0
        private var uvTransformNextHandle = 0

        private var texCurrentHandle = 0
        private var texNextHandle = 0
        private var progressHandle = 0
        private var colorMatrixHandle = 0
        private var colorOffsetHandle = 0

        private var textureCurrent = 0
        private var texCurrentW = 0
        private var texCurrentH = 0

        private var textureNext = 0
        private var texNextW = 0
        private var texNextH = 0

        private var viewWidth = 0
        private var viewHeight = 0

        @Volatile private var transitionProgress = 0f

        private val glColorMatrix = FloatArray(16)
        private val glColorOffset = FloatArray(4)

        private val vertexData = floatArrayOf(
            -1f, -1f, 0f, 0f, 1f,
            1f, -1f, 0f, 1f, 1f,
            -1f,  1f, 0f, 0f, 0f,
            1f,  1f, 0f, 1f, 0f
        )
        private lateinit var vertexBuffer: FloatBuffer

        init {
            updateColorMatrix(ColorMatrix())
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            vertexBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData)
            vertexBuffer.position(0)

            val vertexShader = """
                attribute vec4 a_Position;
                attribute vec2 a_TexCoord;
                
                uniform vec4 u_UvTransformCurrent;
                uniform vec4 u_UvTransformNext;
                
                varying vec2 v_TexCoordCurrent;
                varying vec2 v_TexCoordNext;
                
                void main() {
                    gl_Position = a_Position;
                    v_TexCoordCurrent = a_TexCoord * u_UvTransformCurrent.xy + u_UvTransformCurrent.zw;
                    v_TexCoordNext = a_TexCoord * u_UvTransformNext.xy + u_UvTransformNext.zw;
                }
            """.trimIndent()

            val fragmentShader = """
                precision mediump float;
                
                varying vec2 v_TexCoordCurrent;
                varying vec2 v_TexCoordNext;
                
                uniform sampler2D u_TextureCurrent;
                uniform sampler2D u_TextureNext;
                uniform float u_Progress;
                
                uniform mat4 u_ColorMatrix;
                uniform vec4 u_ColorOffset;
                
                void main() {
                    float zoomCurrent = mix(1.0, 1.06, u_Progress);
                    vec2 uvCurrent = (v_TexCoordCurrent - 0.5) / zoomCurrent + 0.5;

                    float zoomNext = mix(0.94, 1.0, u_Progress);
                    vec2 uvNext = (v_TexCoordNext - 0.5) / zoomNext + 0.5;
                    
                    vec4 colorCurrent = texture2D(u_TextureCurrent, uvCurrent);
                    vec4 colorNext = texture2D(u_TextureNext, uvNext);
                    
                    float lumaNext = dot(colorNext.rgb, vec3(0.299, 0.587, 0.114));

                    float smoothProgress = smoothstep(0.0, 1.0, u_Progress);
                    float threshold = smoothProgress * 1.6 - 0.3;

                    float mixAlpha = smoothstep(lumaNext - 0.2, lumaNext + 0.2, threshold);
                    
                    
                    float edgeGlow = smoothstep(0.0, 0.5, mixAlpha) - smoothstep(0.5, 1.0, mixAlpha);
                    
                    vec4 blendedColor = mix(colorCurrent, colorNext, mixAlpha);
                   
                    blendedColor.rgb += vec3(edgeGlow * 0.12);
                    
                    vec4 transformedColor = u_ColorMatrix * blendedColor + u_ColorOffset;
                    
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

            colorMatrixHandle = GLES20.glGetUniformLocation(program, "u_ColorMatrix")
            colorOffsetHandle = GLES20.glGetUniformLocation(program, "u_ColorOffset")
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            viewWidth = width
            viewHeight = height
            requestRender()
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)

            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
            GLES20.glEnableVertexAttribArray(positionHandle)

            vertexBuffer.position(3)
            GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 5 * 4, vertexBuffer)
            GLES20.glEnableVertexAttribArray(texCoordHandle)

            val cropCurrent = calculateCenterCrop(texCurrentW, texCurrentH, viewWidth, viewHeight)
            GLES20.glUniform4fv(uvTransformCurrentHandle, 1, cropCurrent, 0)

            val cropNext = calculateCenterCrop(texNextW, texNextH, viewWidth, viewHeight)
            GLES20.glUniform4fv(uvTransformNextHandle, 1, cropNext, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureCurrent)
            GLES20.glUniform1i(texCurrentHandle, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureNext)
            GLES20.glUniform1i(texNextHandle, 1)

            GLES20.glUniform1f(progressHandle, transitionProgress)

            GLES20.glUniformMatrix4fv(colorMatrixHandle, 1, false, glColorMatrix, 0)
            GLES20.glUniform4fv(colorOffsetHandle, 1, glColorOffset, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            if (transitionProgress >= 1.0f && textureNext != 0) {
                commitTransition()
            }
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

        fun setNextTexture(bitmap: Bitmap) {
            if (textureNext != 0) deleteTexture(textureNext)

            textureNext = loadTexture(bitmap)
            texNextW = bitmap.width
            texNextH = bitmap.height
            transitionProgress = 0f

            if (textureCurrent == 0) {
                commitTransition()
            }
        }

        fun setTransitionProgress(progress: Float) {
            transitionProgress = progress
        }

        private fun commitTransition() {
            if (textureCurrent != 0) deleteTexture(textureCurrent)

            textureCurrent = textureNext
            texCurrentW = texNextW
            texCurrentH = texNextH

            textureNext = 0
            texNextW = 0
            texNextH = 0

            transitionProgress = 0f
            requestRender()
        }

        private fun calculateCenterCrop(imgW: Int, imgH: Int, vw: Int, vh: Int): FloatArray {
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
            return floatArrayOf(scaleX, scaleY, dx, dy)
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

        private fun deleteTexture(textureId: Int) {
            val textureIds = intArrayOf(textureId)
            GLES20.glDeleteTextures(1, textureIds, 0)
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            return program
        }

        private fun loadShader(type: Int, shaderCode: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            return shader
        }
    }
}