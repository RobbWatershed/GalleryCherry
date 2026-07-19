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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import me.devsaki.hentoid.util.image.getMediaDimensions
import me.devsaki.hentoid.util.image.loadBitmap
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.roundToInt

// Heavily inspired by
//  https://github.com/sixo/vid-proc/blob/master/app/src/main/java/eu/sisik/vidproc/TimeLapseEncoder.kt
//  https://bigflake.com/mediacodec/EncodeAndMuxTest.java.txt
class VideoEncoder : AnimationEncoder {

    // MediaCodec and encoding configuration
    private lateinit var encoder: MediaCodec

    private var muxer: MediaMuxer? = null
    private var muxerStarted = false

    private var outFileDescriptor: ParcelFileDescriptor? = null

    private var mime = MIMETYPE_VIDEO_AVC

    private var trackIndex = -1

    // Current video length, in microseconds
    private var presentationTimeUs = 0L

    private val timeoutUs = 10000L

    private val bufferInfo = MediaCodec.BufferInfo()


    // EGL
    private var eglDisplay: EGLDisplay? = null

    private var eglContext: EGLContext? = null

    private var eglSurface: EGLSurface? = null


    // Surface provided by MediaCodec and used to get data produced by OpenGL
    private var surface: Surface? = null


    /**
     * @param frames Frames : first = Frame file Uri; second = Frame duration (ms)
     *
     * Making sure we're using a single computing thread as GLES context requires it
     */
    override suspend fun encode(
        context: Context,
        outUri: Uri,
        frames: List<Pair<Uri, Int>>,
        quality: Float,
        isCanceled: () -> Boolean,
        onProgress: ((Float) -> Unit)?
    ) = withContext(Executors.newFixedThreadPool(1).asCoroutineDispatcher()) {
        val size = initEncoder(context, outUri, frames, quality)
        encodeImages(context, size, frames, isCanceled, onProgress)
    }

    private suspend fun initEncoder(
        context: Context,
        outVideoUri: Uri,
        frames: List<Pair<Uri, Int>>,
        quality: Float
    ): Size {
        encoder = MediaCodec.createEncoderByType(mime)

        // Try to find supported size by checking the resolution of first supplied image
        val size = getSupportedSize(context, frames[0].first)
        Timber.d("Using size ${size.width}x${size.height}")

        // Calculate max FPS given input frame values
        val maxFps = frames.filterNot { 0 == it.second }.maxOf { 1000f / it.second.toFloat() }
        Timber.d("Using maxFps=$maxFps; quality=$quality with ${frames.size} frames")
        val format = createFormat(size, maxFps, quality)

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
        muxerStarted = false
        return size
    }

    private suspend fun getSupportedSize(context: Context, inBitmapUri: Uri): Size =
        withContext(Dispatchers.IO) {
            val dims = getMediaDimensions(context, inBitmapUri.toString())
            return@withContext getBestSupportedResolution(encoder, mime, Size(dims.x, dims.y))
        }

    private fun createFormat(size: Size, maxFps: Float, quality: Float): MediaFormat {
        val format = MediaFormat.createVideoFormat(mime, size.width, size.height)
        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, (3000000f * quality).roundToInt())
        format.setInteger(MediaFormat.KEY_FRAME_RATE, maxFps.roundToInt())
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, (maxFps / 2).roundToInt())

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

        if (!makeCurrent())
            throw RuntimeException("eglMakeCurrent(): " + GLUtils.getEGLErrorString(EGL14.eglGetError()))
    }

    private fun makeCurrent(): Boolean {
        return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    private suspend fun encodeImages(
        context: Context,
        size: Size,
        frames: List<Pair<Uri, Int>>,
        isCanceled: () -> Boolean,
        onProgress: ((Float) -> Unit)? = null
    ) {
        // Init OpenGL, once we have initialized context and surface
        val renderer = TextureRenderer()

        var frameNum = 0
        var framesMissed = 0
        for (frame in frames) {
            if (isCanceled.invoke()) break
            try {
                frameNum++

                // Render the bitmap/texture here
                loadBitmap(context, frame.first)?.let { bitmap ->
                    try {
                        renderer.draw(size.width, size.height, bitmap, getMvp())
                    } finally {
                        bitmap.recycle()
                    }
                } ?: throw IOException("Cannot open ${frame.first}")

                EGLExt.eglPresentationTimeANDROID(
                    eglDisplay, eglSurface,
                    presentationTimeUs * 1000 // yes, those are nanoseconds
                )
                checkEglError("eglPresentationTimeANDROID")

                // Feed encoder with next frame produced by OpenGL
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)
                checkEglError("eglSwapBuffers")

                // Get encoded data and feed it to muxer
                val frameProcessed = drainEncoder(frameNum == frames.size, frameNum)
                if (!frameProcessed) {
                    Timber.d("FRAME MISSED @$frameNum") // Not super reliable; encoder may just be waiting to flush its buffer
                    framesMissed++
                }

                onProgress?.apply {
                    if (0 == frameNum % 10) {
                        // Handle notifications on another coroutine not to steal focus for unnecessary stuff
                        invoke(frameNum * 1f / frames.size)
                    }
                }
                presentationTimeUs += frame.second * 1000
            } catch (e: Exception) {
                Timber.w(e, "An issue occured while rendering frame $frameNum")
                framesMissed++
            }
        }
        if (framesMissed > 0) Timber.w("Frames missed : $framesMissed")
    }

    private fun checkEglError(msg: String?) {
        val error: Int
        if ((EGL14.eglGetError().also { error = it }) != EGL14.EGL_SUCCESS) {
            throw java.lang.RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }

    private suspend fun drainEncoder(
        endOfStream: Boolean,
        frameNum: Int
    ): Boolean {
        if (endOfStream) encoder.signalEndOfInputStream()
        Timber.d("drainEncoder 0 @$frameNum")
        var isFrameProcessed = false

        while (true) {
            val encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)

            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    Timber.v("no output available")
                    break      // out of while
                } else {
                    Timber.d("no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (muxerStarted) throw RuntimeException("format changed twice")

                val newFormat = encoder.outputFormat
                Timber.d("encoder output format changed: $newFormat")

                // Start the muxer for good
                muxer?.apply {
                    trackIndex = addTrack(newFormat)
                    start()
                    muxerStarted = true
                } ?: throw RuntimeException("muxer should be initialized")
            } else if (encoderStatus < 0) {
                Timber.w("unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
                // let's ignore it
            } else {
                val encodedData = encoder.getOutputBuffer(encoderStatus)
                    ?: throw RuntimeException("encoderOutputBuffer $encoderStatus was null")

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    Timber.d("ignoring BUFFER_FLAG_CODEC_CONFIG")
                    bufferInfo.size = 0
                }

                if (bufferInfo.size != 0) {
                    if (!muxerStarted) throw RuntimeException("muxer hasn't started")

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    isFrameProcessed = true

                    withContext(Dispatchers.IO) {
                        muxer?.writeSampleData(trackIndex, encodedData, bufferInfo)
                        Timber.d("sent ${bufferInfo.size} bytes to muxer")
                    }
                }

                encoder.releaseOutputBuffer(encoderStatus, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Timber.w("reached end of stream unexpectedly")
                    } else {
                        Timber.d("end of stream reached")
                    }
                    break      // out of while
                }
            }
        }
        return isFrameProcessed
    }

    private fun getMvp(): FloatArray {
        val mvp = FloatArray(16)
        Matrix.setIdentityM(mvp, 0)
        Matrix.scaleM(mvp, 0, 1f, -1f, 1f)

        return mvp
    }

    override fun close() {
        Timber.d("Releasing encoder")
        encoder.stop()
        encoder.release()

        releaseEgl()

        muxer?.stop()
        muxer?.release()
        muxer = null
        muxerStarted = false

        outFileDescriptor?.close()
        outFileDescriptor = null

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