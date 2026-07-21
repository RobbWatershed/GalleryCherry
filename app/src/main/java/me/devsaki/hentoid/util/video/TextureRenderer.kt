package me.devsaki.hentoid.util.video

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

// Credits go to https://github.com/sixo/vid-proc/blob/master/app/src/main/java/eu/sisik/vidproc/TimeLapseEncoder.kt
class TextureRenderer {

    private val vertexShaderCode =
        "precision highp float;\n" +
                "attribute vec3 vertexPosition;\n" +
                "attribute vec2 uvs;\n" +
                "varying vec2 varUvs;\n" +
                "uniform mat4 mvp;\n" +
                "\n" +
                "void main()\n" +
                "{\n" +
                "\tvarUvs = uvs;\n" +
                "\tgl_Position = mvp * vec4(vertexPosition, 1.0);\n" +
                "}"

    private val fragmentShaderCode =
        "precision mediump float;\n" +
                "\n" +
                "varying vec2 varUvs;\n" +
                "uniform sampler2D texSampler;\n" +
                "\n" +
                "void main()\n" +
                "{\t\n" +
                "\tgl_FragColor = texture2D(texSampler, varUvs);\n" +
                "}"


    private var vertices = floatArrayOf(
        // x, y, z, u, v
        -1.0f, -1.0f, 0.0f, 0f, 0f,
        -1.0f, 1.0f, 0.0f, 0f, 1f,
        1.0f, 1.0f, 0.0f, 1f, 1f,
        1.0f, -1.0f, 0.0f, 1f, 0f
    )

    private var indices = intArrayOf(
        2, 1, 0, 0, 3, 2
    )

    private var program: Int
    private var vertexHandle: Int = 0
    private var bufferHandles = IntArray(2)
    private var uvsHandle: Int = 0
    private var mvpHandle: Int = 0
    private var samplerHandle: Int = 0
    private val textureHandle = IntArray(1)

    var vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
        order(ByteOrder.nativeOrder())
        asFloatBuffer().apply {
            put(vertices)
            position(0)
        }
    }

    var indexBuffer: IntBuffer = ByteBuffer.allocateDirect(indices.size * 4).run {
        order(ByteOrder.nativeOrder())
        asIntBuffer().apply {
            put(indices)
            position(0)
        }
    }

    init {

        // Create program
        val vertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {

            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)

            vertexHandle = GLES20.glGetAttribLocation(it, "vertexPosition")
            uvsHandle = GLES20.glGetAttribLocation(it, "uvs")
            mvpHandle = GLES20.glGetUniformLocation(it, "mvp")
            samplerHandle = GLES20.glGetUniformLocation(it, "texSampler")
        }

        // Initialize buffers
        GLES20.glGenBuffers(2, bufferHandles, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferHandles[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            vertices.size * 4,
            vertexBuffer,
            GLES20.GL_DYNAMIC_DRAW
        )

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, bufferHandles[1])
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER,
            indices.size * 4,
            indexBuffer,
            GLES20.GL_DYNAMIC_DRAW
        )

        // Init texture handle
        GLES20.glGenTextures(1, textureHandle, 0)

        // Ensure I can draw transparent stuff that overlaps properly
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {

        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    fun draw(viewportWidth: Int, viewportHeight: Int, bitmap: Bitmap, mvpMatrix: FloatArray) {

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glClearColor(0f, 0f, 0f, 0f)

        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)

        GLES20.glUseProgram(program)

        // Pass transformations to shader
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        // Prepare texture for drawing
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_NEAREST
        )

        // Prepare buffers with vertices and indices & draw
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufferHandles[0])
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, bufferHandles[1])

        GLES20.glEnableVertexAttribArray(vertexHandle)
        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false, 4 * 5, 0)

        GLES20.glEnableVertexAttribArray(uvsHandle)
        GLES20.glVertexAttribPointer(uvsHandle, 2, GLES20.GL_FLOAT, false, 4 * 5, 3 * 4)

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_INT, 0)
    }
}