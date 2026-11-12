package com.example.gl

import android.content.Context
import android.opengl.GLES20
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class ChromaKeyFilterRender : BaseFilterRender() {

    private val vertexShader = """
        attribute vec4 aPosition;
        attribute vec4 aTextureCoord;
        varying vec2 vTextureCoord;
        void main() {
            gl_Position = aPosition;
            vTextureCoord = aTextureCoord.xy;
        }
    """.trimIndent()

    private val fragmentShader = """
        precision mediump float;
        varying vec2 vTextureCoord;
        uniform sampler2D uSampler;
        
        uniform vec3 uKeyColor;
        uniform float uSimilarity;
        uniform float uSmoothness;
        uniform float uSpill;
        uniform float uOpacity;

        void main() {
            vec4 color = texture2D(uSampler, vTextureCoord);
            
            // Euclidean distance in RGB color space
            float diff = distance(color.rgb, uKeyColor);
            
            // Smooth alpha falloff between similarity threshold and softness boundary
            float alpha = smoothstep(uSimilarity, uSimilarity + uSmoothness, diff);
            
            // Spill suppression: suppress green or blue color bleeding on edges
            if (uKeyColor.g > uKeyColor.r && uKeyColor.g > uKeyColor.b) {
                // Green screen spill reduction
                float maxRB = max(color.r, color.b);
                if (color.g > maxRB) {
                    color.g = mix(color.g, maxRB, uSpill);
                }
            } else if (uKeyColor.b > uKeyColor.r && uKeyColor.b > uKeyColor.g) {
                // Blue screen spill reduction
                float maxRG = max(color.r, color.g);
                if (color.b > maxRG) {
                    color.b = mix(color.b, maxRG, uSpill);
                }
            }
            
            color.a *= alpha * uOpacity;
            gl_FragColor = color;
        }
    """.trimIndent()

    private var program = 0
    private var aPositionHandle = -1
    private var aTextureCoordHandle = -1
    private var uSamplerHandle = -1
    private var uKeyColorHandle = -1
    private var uSimilarityHandle = -1
    private var uSmoothnessHandle = -1
    private var uSpillHandle = -1
    private var uOpacityHandle = -1

    var keyColorR: Float = 0.0f
    var keyColorG: Float = 1.0f
    var keyColorB: Float = 0.0f
    var similarity: Float = 0.40f
    var smoothness: Float = 0.12f
    var spill: Float = 0.50f
    var opacity: Float = 1.0f

    fun setKeyColorHex(hex: Long) {
        val r = ((hex shr 16) and 0xFF).toFloat() / 255.0f
        val g = ((hex shr 8) and 0xFF).toFloat() / 255.0f
        val b = (hex and 0xFF).toFloat() / 255.0f
        keyColorR = r
        keyColorG = g
        keyColorB = b
    }

    override fun initGlFilter(context: Context?) {
        program = GlUtil.createProgram(vertexShader, fragmentShader)

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uSamplerHandle = GLES20.glGetUniformLocation(program, "uSampler")
        uKeyColorHandle = GLES20.glGetUniformLocation(program, "uKeyColor")
        uSimilarityHandle = GLES20.glGetUniformLocation(program, "uSimilarity")
        uSmoothnessHandle = GLES20.glGetUniformLocation(program, "uSmoothness")
        uSpillHandle = GLES20.glGetUniformLocation(program, "uSpill")
        uOpacityHandle = GLES20.glGetUniformLocation(program, "uOpacity")
    }

    override fun drawFilter() {
        GLES20.glUseProgram(program)

        squareVertex.position(0)
        GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 8, squareVertex)
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        squareVertex.position(0)
        GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 8, squareVertex)
        GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

        GLES20.glUniform1i(uSamplerHandle, 0)
        GLES20.glUniform3f(uKeyColorHandle, keyColorR, keyColorG, keyColorB)
        GLES20.glUniform1f(uSimilarityHandle, similarity)
        GLES20.glUniform1f(uSmoothnessHandle, smoothness)
        GLES20.glUniform1f(uSpillHandle, spill)
        GLES20.glUniform1f(uOpacityHandle, opacity)
    }

    override fun disableResources() {
        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTextureCoordHandle)
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
    }
}
