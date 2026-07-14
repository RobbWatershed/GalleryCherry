package me.devsaki.hentoid.util.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaFormat.MIMETYPE_VIDEO_AVC
import android.media.MediaMuxer
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.ParcelFileDescriptor
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.util.image.getMediaDimensions
import me.devsaki.hentoid.util.image.loadBitmap
import timber.log.Timber

// Heavily inspired by https://github.com/sixo/vid-proc/blob/master/app/src/main/java/eu/sisik/vidproc/TimeLapseEncoder.kt
class VideoEncoder {

    // MediaCodec and encoding configuration
    private lateinit var encoder: MediaCodec

    private var muxer: MediaMuxer? = null

    private var outFileDescriptor: ParcelFileDescriptor? = null

    private var mime = MIMETYPE_VIDEO_AVC

    private var trackIndex = -1

    private var presentationTimeUs = 0L

    private var frameRate = 30

    private val timeoutUs = 10000L

    private val bufferInfo = MediaCodec.BufferInfo()

    private var size: Size? = null


    // EGL
    private var eglDisplay: EGLDisplay? = null

    private var eglContext: EGLContext? = null

    private var eglSurface: EGLSurface? = null


    // Surface provided by MediaCodec and used to get data produced by OpenGL
    private var surface: Surface? = null


    suspend fun encodeVideo(
        context: Context,
        outVideoUri: Uri,
        imageUris: List<Uri>
    ) {
        try {
            initEncoder(context, outVideoUri, imageUris)
            encodeImages(context, imageUris)
        } catch (e: Exception) {
            Timber.e(e, "Encoding failed")
        } finally {
            releaseEncoder()
        }
    }

    private suspend fun initEncoder(
        context: Context,
        outVideoUri: Uri,
        imageUris: List<Uri>
    ) = withContext(Dispatchers.Default) {
        encoder = MediaCodec.createEncoderByType(mime)

        // Try to find supported size by checking the resolution of first supplied image
        // This could also be set manually as parameter to TimeLapseEncoder
        size = getSupportedSize(context, imageUris[0])

        val format = getFormat(size!!)

        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        // Prepare surface
        initEgl()

        // Switch to executing state - we're ready to encode
        encoder.start()

        // Prepare muxer
        outFileDescriptor = context.contentResolver.openFileDescriptor(outVideoUri, "wt")
        outFileDescriptor?.let {
            muxer = MediaMuxer(it.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }
    }

    private suspend fun getSupportedSize(context: Context, inBitmapUri: Uri): Size =
        withContext(Dispatchers.IO) {
            val dims = getMediaDimensions(context, inBitmapUri.toString())
            return@withContext getBestSupportedResolution(encoder, mime, Size(dims.x, dims.y))
        }

    private fun getFormat(size: Size): MediaFormat {
        val format = MediaFormat.createVideoFormat(mime, size.width, size.height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 60)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 15)

        return format
    }

    private fun initEgl() {
        surface = encoder.createInputSurface()
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY)
            throw RuntimeException(
                "eglDisplay == EGL14.EGL_NO_DISPLAY: "
                        + GLUtils.getEGLErrorString(EGL14.eglGetError())
            )

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1))
            throw RuntimeException("eglInitialize(): " + GLUtils.getEGLErrorString(EGL14.eglGetError()))

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val nConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, nConfigs, 0)

        var err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS)
            throw RuntimeException(GLUtils.getEGLErrorString(err))

        val ctxAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext =
            EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)

        err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS)
            throw RuntimeException(GLUtils.getEGLErrorString(err))

        val surfaceAttribs = intArrayOf(
            EGL14.EGL_NONE
        )
        eglSurface =
            EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        err = EGL14.eglGetError()
        if (err != EGL14.EGL_SUCCESS)
            throw RuntimeException(GLUtils.getEGLErrorString(err))

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext))
            throw RuntimeException("eglMakeCurrent(): " + GLUtils.getEGLErrorString(EGL14.eglGetError()))
    }

    private suspend fun encodeImages(context: Context, imageUris: List<Uri>) =
        withContext(Dispatchers.Default) {
            // Init OpenGL, once we have initialized context and surface
            val renderer = TextureRenderer()

            for (imageUri in imageUris) {
                // Get encoded data and feed it to muxer
                drainEncoder(false)

                // Render the bitmap/texture here
                loadBitmap(context, imageUri)?.let { bitmap ->
                    try {
                        renderer.draw(size!!.width, size!!.height, bitmap, getMvp())
                    } finally {
                        bitmap.recycle()
                    }
                }

                EGLExt.eglPresentationTimeANDROID(
                    eglDisplay, eglSurface,
                    presentationTimeUs * 1000
                )

                // Feed encoder with next frame produced by OpenGL
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
            }

            // Drain last remaining encoded data and finalize the video file
            drainEncoder(true)
        }

    private suspend fun drainEncoder(endOfStream: Boolean) = withContext(Dispatchers.IO) {
        if (endOfStream) encoder.signalEndOfInputStream()

        while (true) {
            val outBufferId = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)

            if (outBufferId >= 0) {
                val encodedBuffer = encoder.getOutputBuffer(outBufferId) ?: break

                // MediaMuxer is ignoring KEY_FRAMERATE, so I set it manually here
                // to achieve the desired frame rate
                bufferInfo.presentationTimeUs = presentationTimeUs
                muxer?.writeSampleData(trackIndex, encodedBuffer, bufferInfo)

                presentationTimeUs += 1000000 / frameRate

                encoder.releaseOutputBuffer(outBufferId, false)

                // Are we finished here?
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                    break
            } else if (outBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream)
                    break

                // End of stream, but still no output available. Try again.
            } else if (outBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                muxer?.apply {
                    trackIndex = addTrack(encoder.outputFormat)
                    start()
                }
            }
        }
    }

    private fun getMvp(): FloatArray {
        val mvp = FloatArray(16)
        Matrix.setIdentityM(mvp, 0)
        Matrix.scaleM(mvp, 0, 1f, -1f, 1f)

        return mvp
    }

    private fun releaseEncoder() {
        encoder.stop()
        encoder.release()

        releaseEgl()

        muxer?.stop()
        muxer?.release()
        muxer = null

        outFileDescriptor?.close()
        outFileDescriptor = null

        size = null
        trackIndex = -1
        presentationTimeUs = 0L
    }

    private fun releaseEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }

        surface?.release()
        surface = null

        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}