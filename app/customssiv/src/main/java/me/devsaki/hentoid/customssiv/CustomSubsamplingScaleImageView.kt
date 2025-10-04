package me.devsaki.hentoid.customssiv

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.annotation.AnyThread
import androidx.core.content.withStyledAttributes
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.customssiv.decoder.ImageDecoder
import me.devsaki.hentoid.customssiv.decoder.ImageRegionDecoder
import me.devsaki.hentoid.customssiv.decoder.SkiaImageDecoder
import me.devsaki.hentoid.customssiv.decoder.SkiaImageRegionDecoder
import me.devsaki.hentoid.customssiv.util.Debouncer
import me.devsaki.hentoid.customssiv.util.getImageDimensions
import me.devsaki.hentoid.customssiv.util.getScreenDimensionsPx
import me.devsaki.hentoid.customssiv.util.getScreenDpi
import me.devsaki.hentoid.customssiv.util.lifecycleScope
import me.devsaki.hentoid.customssiv.util.resizeBitmap
import me.devsaki.hentoid.customssiv.util.smartCropBitmap
import me.devsaki.hentoid.gles_renderer.GPUImage
import timber.log.Timber
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt


const val TILE_SIZE_AUTO = Int.MAX_VALUE

private const val MESSAGE_LONG_CLICK = 1

/**
 * Displays an image subsampled as necessary to avoid loading too much image data into memory. After zooming in,
 * a set of image tiles subsampled at higher resolution are loaded and displayed over the base layer. During pan and
 * zoom, tiles off screen or higher/lower resolution than required are discarded from memory.
 * </p><p>
 * Tiles are no larger than the max supported bitmap size, so with large images tiling may be used even when zoomed out.
 * </p><p>
 * v prefixes - coordinates, translations and distances measured in screen (view) pixels
 * <br>
 * s prefixes - coordinates, translations and distances measured in rotated and cropped source image pixels (scaled)
 * <br>
 * f prefixes - coordinates, translations and distances measured in original unrotated, uncropped source file pixels
 * </p><p>
 * <a href="https://github.com/davemorrissey/subsampling-scale-image-view">View project on GitHub</a>
 */
open class CustomSubsamplingScaleImageView(context: Context, attr: AttributeSet? = null) :
    View(context, attr) {

    enum class Direction(val code: Int) {
        VERTICAL(0),
        HORIZONTAL(1)
    }

    enum class Orientation(val code: Int) {
        // Display the image file in its native orientation.
        O_0(0),

        // Rotate the image 90 degrees clockwise.
        O_90(90),

        // Rotate the image 180 degrees.
        O_180(180),

        // Rotate the image 270 degrees clockwise.
        O_270(270),

        // Attempt to use EXIF information on the image to rotate it. Works for external files only.
        USE_EXIF(-1);

        companion object {
            fun fromCode(id: Int): Orientation {
                for (s in entries) if (id == s.code) return s
                return O_0
            }
        }
    }

    enum class ZoomStyle(val code: Int) {
        // During zoom animation, keep the point of the image that was tapped in the same place, and scale the image around it.
        FOCUS_FIXED(1),

        // During zoom animation, move the point of the image that was tapped to the center of the screen.
        FOCUS_CENTER(2),

        // Zoom in to and center the tapped point immediately without animating.
        FOCUS_CENTER_IMMEDIATE(3),
    }

    enum class Easing(val code: Int) {
        // Quadratic ease out. Not recommended for scale animation, but good for panning.
        OUT_QUAD(1),

        // Quadratic ease in and out.
        IN_OUT_QUAD(2)
    }

    enum class PanLimit(val code: Int) {
        // Don't allow the image to be panned off screen. As much of the image as possible is always displayed, centered in the view when it is smaller.
        // This is the best option for galleries.
        INSIDE(1),

        // Allows the image to be panned until it is just off screen, but no further.
        // The edge of the image will stop when it is flush with the screen edge.
        OUTSIDE(2),

        // Allows the image to be panned until a corner reaches the center of the screen but no further.
        // Useful when you want to pan any spot on the image to the exact center of the screen.
        CENTER(3)
    }

    enum class ScaleType(val code: Int) {
        // Scale the image so that both dimensions of the image will be equal to or less than the corresponding dimension of the view.
        // The image is then centered in the view. This is the default behaviour and best for galleries.
        CENTER_INSIDE(1),

        // Scale the image uniformly so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view.
        // The image is then centered in the view.
        CENTER_CROP(2),

        // Scale the image so that both dimensions of the image will be equal to or less than the maxScale and equal to or larger than minScale.
        // The image is then centered in the view.
        CUSTOM(3),

        // Scale the image so that both dimensions of the image will be equal to or larger than the corresponding dimension of the view.
        // The top left is shown.
        START(4),
        FIT_WIDTH(5),
        FIT_HEIGHT(6),
        SMART_FIT(7),
        SMART_FILL(8),
        ORIGINAL_SIZE(9),
        STRETCH_SCREEN(10)
    }

    enum class AnimOrigin(val code: Int) {
        // State change originated from animation.
        ANIM(1),

        // State change originated from touch gesture.
        TOUCH(2),

        // State change originated from a fling momentum anim.
        FLING(3),

        // State change originated from a double tap zoom anim.
        DOUBLE_TAP_ZOOM(4),

        // State change originated from a long tap zoom anim.
        LONG_TAP_ZOOM(5)
    }

    enum class AutoRotateMethod {
        NONE, LEFT, RIGHT
    }


    // Bitmap (preview or full image)
    private var bitmap: Bitmap? = null

    // Specifies if a cache handler is also referencing the bitmap. Do not recycle if so.
    private var bitmapIsCached = false

    // Uri of full size image
    private var uri: Uri? = null

    // Sample size used to display the whole image when fully zoomed out
    private var fullImageSampleSize = 0

    // TODO doc
    private val singleImage = SingleImage()

    // Map of zoom level to tile grid
    private var tileMap: MutableMap<Int, List<Tile>>? = null

    // Image orientation setting
    private var orientation = Orientation.O_0

    // Zoom cap for double-tap zoom
    // (factor of default scaling)
    private var doubleTapZoomCap = -1f

    // Max scale allowed (factor of source resolution)
    // Used to prevent infinite zoom
    private var maxScale = 2f

    // Density to reach before loading higher resolution tiles
    private var minimumTileDpi = -1

    // Pan limiting style
    private var panLimit = PanLimit.INSIDE

    // Minimum scale type
    private var minimumScaleType = ScaleType.CENTER_INSIDE

    // Min scale allowed (factor of source resolution)
    // Used to prevent infinite zoom
    private var minScale = minScale()

    // overrides for the dimensions of the generated tiles
    private var maxTileWidth = TILE_SIZE_AUTO
    private var maxTileHeight = TILE_SIZE_AUTO

    // Whether tiles should be loaded while gestures and animations are still in progress
    private val refreshSingleDebouncer: Debouncer<Boolean>
    private var eagerLoadingEnabled = true

    // Gesture detection settings
    private var panEnabled = true
    private var zoomEnabled = true
    private var quickScaleEnabled = true
    private var longTapZoomEnabled = true
    private var isDoubleTapZoomEnabled = true

    // Double tap zoom behaviour
    private var doubleTapZoomScale = 1f
    private var doubleTapZoomStyle = ZoomStyle.FOCUS_FIXED
    private var doubleTapZoomDuration = 500

    // Initial scale, according to panLimit and minimumScaleType
    private var initialScale = -1f

    // Current scale = "zoom level" applied by SSIV
    private var scale = 0f

    // Scale at start of zoom (transitional)
    private var scaleStart = 0f

    // Virtual scale = "zoom level" applied externally without the help of SSIV
    // Used to tell the picture resizer which is the actual scale to take into account
    private var virtualScale = 0f

    // Screen coordinate of top-left corner of source image (image offset relative to screen)
    private var vTranslate: PointF? = null
    private var vTranslateStart: PointF? = null

    // Source coordinate to center on, used when new position is set externally before view is ready
    private var pendingScale: Float? = null
    private var sPendingCenter: PointF? = null
    private var sRequestedCenter: PointF? = null

    // Source image dimensions and orientation - dimensions relate to the unrotated image
    private var sWidth = 0
    private var sHeight = 0
    private var sOrientation = Orientation.O_0
    private var sRegion: Rect? = null
    private var pRegion: Rect? = null

    // Is two-finger zooming in progress
    private var isZooming = false

    // Is one-finger panning in progress
    private var isPanning = false

    // Is quick-scale gesture in progress
    private var isQuickScaling = false

    // Is long tap zoom in progress
    private var isLongTapZooming = false

    // Max touches used in current gesture
    private var maxTouchCount = 0

    // Fling detector
    private var detector: GestureDetector? = null
    private var singleDetector: GestureDetector? = null

    // Tile and image decoding
    private var regionDecoder: ImageRegionDecoder? = null
    private val decoderLock: ReadWriteLock = ReentrantReadWriteLock(true)

    // Preference for bitmap color format
    private var preferredBitmapConfig = Bitmap.Config.RGB_565

    // Start of double-tap and long-tap zoom, in terms of screen (view) coordinates
    private var vCenterStart: PointF? = null
    private var vDistStart = 0f

    // Current quickscale state
    private val quickScaleThreshold: Float
    private var quickScaleLastDistance = 0f
    private var quickScaleMoved = false
    private var quickScaleVLastPoint: PointF? = null
    private var quickScaleSCenter: PointF? = null
    private var quickScaleVStart: PointF? = null

    // Scale and center animation tracking
    private var anim: Anim? = null

    // Whether a ready notification has been sent to subclasses
    private var readySent = false

    // Whether a base layer loaded notification has been sent to subclasses
    private var imageLoadedSent = false

    // Event listener
    private var onImageEventListener: OnImageEventListener? = null

    // Scale and center listeners
    private val scaleDebouncer: Debouncer<Float>
    private var scaleListener: Consumer<Float>? = null

    // Long click listener
    private var onLongClickListener: OnLongClickListener? = null

    // Long click handler
    private val handler: Handler

    // Paint objects created once and reused for efficiency
    private var bitmapPaint: Paint? = null
    private var tileBgPaint: Paint? = null

    // Volatile fields used to reduce object creation
    private var satTemp: ScaleAndTranslate? = null
    private var matrix: Matrix? = null
    private var sRect: RectF? = null
    private val srcArray = FloatArray(8)
    private val dstArray = FloatArray(8)

    //The logical density of the display
    private var density = 0f

    // Switch to ignore all touch events (used in vertical mode when the container view is the one handling touch events)
    private var ignoreTouchEvents = false

    // Dimensions used to preload the image before the view actually appears on screen / gets its display dimensions
    private var preloadDimensions: Point? = null

    // Direction of the swiping
    // Used to trigger a relevant "next page" event according to which border has been reached
    // (Vertical -> top and bottom borders / Horizontal -> left and right borders)
    private var swipeDirection = Direction.HORIZONTAL

    // True if the image offset should be its left side
    // Used to display the correct border in fill screen mode according to the book viewing mode
    // (LTR -> left / RTL -> right)
    private var offsetLeftSide = true

    // First-time flag for the side offset (prevents the image offsetting when the user has already scrolled it)
    private var sideOffsetConsumed = false

    // Flag for auto-rotate mode enabled
    private var autoRotate = AutoRotateMethod.NONE

    // Flag for smart crop mode enabled
    private var isSmartCrop = false

    // Phone screen width and height (stored here for optimization)
    private val screenWidth: Int
    private val screenHeight: Int

    private val screenDpi: Float

    // GPUImage instance to use to smoothen images; sharp mode will be used if not set
    private var glEsRenderer: GPUImage? = null


    init {
        density = resources.displayMetrics.density
        getScreenDimensionsPx(context).let {
            screenWidth = it.x
            screenHeight = it.y
        }
        screenDpi = getScreenDpi(context)
        setMinimumDpi(160)
        setDoubleTapZoomDpi(160)
        setMinimumTileDpi(320)
        setGestureDetector(context)
        handler = Handler(Looper.getMainLooper()) { message: Message ->
            if (message.what == MESSAGE_LONG_CLICK) {
                if (onLongClickListener != null) {
                    maxTouchCount = 0
                    super.setOnLongClickListener(onLongClickListener)
                    performLongClick()
                    super.setOnLongClickListener(null)
                }
                if (longTapZoomEnabled) longTapZoom(message.arg1, message.arg2)
            }
            true
        }
        // Handle XML attributes
        if (attr != null) {
            getContext().withStyledAttributes(
                attr,
                R.styleable.CustomSubsamplingScaleImageView
            ) {
                if (hasValue(R.styleable.CustomSubsamplingScaleImageView_assetName)) {
                    val assetName =
                        getString(R.styleable.CustomSubsamplingScaleImageView_assetName)
                    if (!assetName.isNullOrEmpty()) {
                        setImage(asset(assetName).enableTiling())
                    }
                }
                if (hasValue(R.styleable.CustomSubsamplingScaleImageView_panEnabled)) {
                    setPanEnabled(
                        getBoolean(
                            R.styleable.CustomSubsamplingScaleImageView_panEnabled,
                            true
                        )
                    )
                }
                if (hasValue(R.styleable.CustomSubsamplingScaleImageView_zoomEnabled)) {
                    setZoomEnabled(
                        getBoolean(
                            R.styleable.CustomSubsamplingScaleImageView_zoomEnabled,
                            true
                        )
                    )
                }
                if (hasValue(R.styleable.CustomSubsamplingScaleImageView_quickScaleEnabled)) {
                    setQuickScaleEnabled(
                        getBoolean(
                            R.styleable.CustomSubsamplingScaleImageView_quickScaleEnabled,
                            true
                        )
                    )
                }
                if (hasValue(R.styleable.CustomSubsamplingScaleImageView_tileBackgroundColor)) {
                    setTileBackgroundColor(
                        getColor(
                            R.styleable.CustomSubsamplingScaleImageView_tileBackgroundColor,
                            Color.argb(0, 0, 0, 0)
                        )
                    )
                }
            }
        }
        quickScaleThreshold = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            80f,
            context.resources.displayMetrics
        )
        scaleDebouncer = Debouncer(context, 200) { scaleListener?.accept(it) }
        refreshSingleDebouncer = Debouncer(context, 50) { refreshSingle(it) }
    }

    fun clear() {
        recycle()
        scaleDebouncer.clear()
        scaleListener = null
    }

    /**
     * Set a global preferred bitmap config shared by all view instance and applied to new instances
     * initialised after the call is made. This is a hint only; the bundled [ImageDecoder] and
     * [ImageRegionDecoder] classes all respect this (except when they were constructed with
     * an instance-specific config) but custom decoder classes will not.
     *
     * @param config the bitmap configuration to be used by future instances of the view. Pass null to restore the default.
     */
    fun setPreferredBitmapConfig(config: Bitmap.Config) {
        preferredBitmapConfig = config
    }

    /**
     * Sets the image orientation. It's best to call this before setting the image file or asset, because it may waste
     * loading of tiles. However, this can be freely called at any time.
     *
     * @param orientation orientation to be set. See ORIENTATION_ static fields for valid values.
     */
    fun setOrientation(orientation: Orientation) {
        this.orientation = orientation
        reset(false)
        invalidate()
        requestLayout()
    }

    /**
     * Set the image source from a bitmap, resource, asset, file or other URI, providing a preview image to be
     * displayed until the full size image is loaded, starting with a given orientation setting, scale and center.
     * This is the best method to use when you want scale and center to be restored after screen orientation change;
     * it avoids any redundant loading of tiles in the wrong orientation.
     *
     * You must declare the dimensions of the full size image by calling [ImageSource.dimensions]
     * on the imageSource object. The preview source will be ignored if you don't provide dimensions,
     * and if you provide a bitmap for the full size image.
     *
     * @param imageSource  Image source. Dimensions must be declared.
     */
    fun setImage(imageSource: ImageSource) {
        reset(true)

        if (imageSource.getBitmap() != null && imageSource.getSRegion() != null) {
            // Load part of Bitmap directly handed inside the source
            onImageLoaded(
                imageSource.getUri()?.toString() ?: "", 1f,
                Bitmap.createBitmap(
                    imageSource.getBitmap()!!,
                    imageSource.getSRegion()!!.left,
                    imageSource.getSRegion()!!.top,
                    imageSource.getSRegion()!!
                        .width(),
                    imageSource.getSRegion()!!.height()
                ), Orientation.O_0, false, 1f
            )
        } else if (imageSource.getBitmap() != null) {
            // Load Bitmap directly handed inside the source
            onImageLoaded(
                imageSource.getUri()?.toString() ?: "",
                1f,
                imageSource.getBitmap()!!,
                Orientation.O_0,
                imageSource.isCached(),
                1f
            )
        } else {
            sRegion = imageSource.getSRegion()
            uri = imageSource.getUri()
            // TODO tiling may create artifacts on certain screens for certain images (#1209)
            //if (minimumScaleType != ScaleType.STRETCH_SCREEN) imageSource.enableTiling()
            if (imageSource.getTile() || sRegion != null) {
                // Load the bitmap using tile decoding.
                lifecycleScope?.launch {
                    try {
                        val res = initTiles(context, uri!!)
                        // Remove invalid results
                        if (res[0] < 0) return@launch
                        withContext(Dispatchers.Main) {
                            onTilesInitialized(res[0], res[1], Orientation.fromCode(res[2]))
                        }
                    } catch (t: Throwable) {
                        onImageEventListener?.onImageLoadError(t)
                    }
                }
            } else {
                // Load the bitmap as a single image
                uri?.let { loadBitmapToImage(context, it) }
            }
        }
    }

    /**
     * Reset all state before setting/changing image or setting new rotation.
     */
    private fun reset(newImage: Boolean) {
        initialScale = -1f
        scale = 0f
        virtualScale = -1f
        scaleStart = 0f
        vTranslate = null
        vTranslateStart = null
        pendingScale = 0f
        sPendingCenter = null
        sRequestedCenter = null
        isZooming = false
        isPanning = false
        isQuickScaling = false
        isLongTapZooming = false
        maxTouchCount = 0
        fullImageSampleSize = 0
        vCenterStart = null
        vDistStart = 0f
        quickScaleLastDistance = 0f
        quickScaleMoved = false
        quickScaleSCenter = null
        quickScaleVLastPoint = null
        quickScaleVStart = null
        anim = null
        satTemp = null
        matrix = null
        sRect = null
        if (newImage) {
            uri = null
            decoderLock.writeLock().lock()
            try {
                regionDecoder?.recycle()
                regionDecoder = null
            } finally {
                decoderLock.writeLock().unlock()
            }
            if (!bitmapIsCached && !singleImage.loading) {
                bitmap?.recycle()
            }
            if (bitmap != null && bitmapIsCached) {
                onImageEventListener?.onPreviewReleased()
            }
            sWidth = 0
            sHeight = 0
            sOrientation = Orientation.O_0
            sRegion = null
            pRegion = null
            readySent = false
            imageLoadedSent = false
            bitmapIsCached = false
            singleImage.reset()
            bitmap = null
        }
        tileMap?.let { tm ->
            for ((_, value) in tm) {
                for (tile in value) {
                    tile.visible = false
                    if (!tile.loading) {
                        tile.bitmap?.recycle()
                        tile.bitmap = null
                    }
                }
            }
            tileMap = null
        }
        setGestureDetector(context)
    }

    private fun setGestureDetector(context: Context) {
        this.detector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // NB : Even though e1 and e2 are marked @NonNull on the overriden method, one of them may be actually null, hence the present implementation
                if (panEnabled && readySent && vTranslate != null && e1 != null
                    && (abs(e1.x - e2.x) > 50 || abs(e1.y - e2.y) > 50)
                    && (abs(velocityX) > 500 || abs(velocityY) > 500)
                    && !isZooming
                ) {
                    val vTranslateEnd = PointF(
                        vTranslate!!.x + (velocityX * 0.25f),
                        vTranslate!!.y + (velocityY * 0.25f)
                    )
                    val sCenterXEnd = (getWidthInternal() / 2f - vTranslateEnd.x) / scale
                    val sCenterYEnd = (getHeightInternal() / 2f - vTranslateEnd.y) / scale
                    AnimationBuilder(
                        scale,
                        PointF(sCenterXEnd, sCenterYEnd)
                    ).withEasing(Easing.OUT_QUAD)
                        .withPanLimited(false).withOrigin(AnimOrigin.FLING).start()
                    return true
                }
                return super.onFling(e1, e2, velocityX, velocityY)
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (zoomEnabled && readySent && vTranslate != null) {
                    // Hacky solution for #15 - after a double tap the GestureDetector gets in a state
                    // where the next fling is ignored, so here we replace it with a new one.
                    setGestureDetector(context)
                    if (quickScaleEnabled) {
                        // Store quick scale params. This will become either a double tap zoom or a
                        // quick scale depending on whether the user swipes.
                        vCenterStart = PointF(e.x, e.y)
                        vTranslateStart = PointF(vTranslate!!.x, vTranslate!!.y)
                        scaleStart = scale
                        isQuickScaling = true
                        isZooming = true
                        quickScaleLastDistance = -1f
                        quickScaleSCenter = viewToSourceCoord(vCenterStart!!)
                        quickScaleVStart = PointF(e.x, e.y)
                        quickScaleVLastPoint = PointF(quickScaleSCenter!!.x, quickScaleSCenter!!.y)
                        quickScaleMoved = false
                        // We need to get events in onTouchEvent after this.
                        return false
                    } else if (isDoubleTapZoomEnabled) {
                        // Start double tap zoom animation
                        val sCenter = viewToSourceCoord(PointF(e.x, e.y))
                        doubleTapZoom(sCenter, PointF(e.x, e.y))
                        return true
                    }
                }
                return super.onDoubleTapEvent(e)
            }
        })

        singleDetector = GestureDetector(context, object : SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                performClick()
                return true
            }
        })
    }

    fun longTapZoom(x: Int, y: Int) {
        if (zoomEnabled && longTapZoomEnabled && readySent) {
            vCenterStart = PointF(x.toFloat(), y.toFloat())
            val sCenter = viewToSourceCoord(vCenterStart!!)

            var targetDoubleTapZoomScale = min(maxScale, doubleTapZoomScale)
            if (doubleTapZoomCap > -1) {
                targetDoubleTapZoomScale =
                    min(targetDoubleTapZoomScale, (initialScale * doubleTapZoomCap))
                Timber.i(">> longTapZoomCap %s -> %s", initialScale, targetDoubleTapZoomScale)
            }

            AnimationBuilder(targetDoubleTapZoomScale, sCenter).withInterruptible(false)
                .withDuration(doubleTapZoomDuration.toLong()).withOrigin(
                    AnimOrigin.LONG_TAP_ZOOM
                ).start()

            isPanning = true
            isLongTapZooming = true
            // Panning gesture management will align itself on the new vTranslate coordinates calculated by the animator
            vTranslateStart = null
        }
    }

    /**
     * On resize, preserve center and scale. Various behaviours are possible, override this method to use another.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (readySent) {
            this.anim = null
            this.pendingScale = scale
            this.sPendingCenter = getCenter()
        }
    }

    /**
     * Measures the width and height of the view, preserving the aspect ratio of the image displayed if wrap_content is
     * used. The image will scale within this box, not resizing the view as it is zoomed.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSpecMode = MeasureSpec.getMode(widthMeasureSpec)
        val heightSpecMode = MeasureSpec.getMode(heightMeasureSpec)
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)
        val resizeWidth = widthSpecMode != MeasureSpec.EXACTLY
        val resizeHeight = heightSpecMode != MeasureSpec.EXACTLY
        var width = parentWidth
        var height = parentHeight
        if (sWidth() > 0 && sHeight() > 0) {
            if (resizeWidth && resizeHeight) {
                width = sWidth()
                height = sHeight()
            } else if (resizeHeight) {
                height = (sHeight() * 1f / sWidth() * width).toInt()
            } else if (resizeWidth) {
                width = (sWidth() * 1f / sHeight() * height).toInt()
            }
        }
        width = max(width, suggestedMinimumWidth)
        height = max(height, suggestedMinimumHeight)
        setMeasuredDimension(width, height)
    }

    fun setIgnoreTouchEvents(ignoreTouchEvents: Boolean) {
        this.ignoreTouchEvents = ignoreTouchEvents
    }

    /**
     * Handle touch events. One finger pans, and two finger pinch and zoom plus panning.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (ignoreTouchEvents) return false

        // During non-interruptible anims, ignore all touch events
        if (anim != null && !anim!!.interruptible) {
            requestDisallowInterceptTouchEvent(true)
            return true
        } else {
            anim = null
        }

        // Abort if not ready
        if (vTranslate == null) {
            singleDetector?.onTouchEvent(event)
            return true
        }
        // Detect flings, taps and double taps
        if (!isQuickScaling && (detector == null || detector!!.onTouchEvent(event))) {
            isZooming = false
            isPanning = false
            maxTouchCount = 0
            return true
        }

        if (vTranslateStart == null) vTranslateStart = PointF(0f, 0f)
        if (vCenterStart == null) vCenterStart = PointF(0f, 0f)

        val handled = onTouchEventInternal(event)
        return handled || super.onTouchEvent(event)
    }

    @Suppress("deprecation")
    private fun onTouchEventInternal(event: MotionEvent): Boolean {
        val touchCount = event.pointerCount
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_1_DOWN, MotionEvent.ACTION_POINTER_2_DOWN -> {
                anim = null
                requestDisallowInterceptTouchEvent(true)
                maxTouchCount = max(maxTouchCount, touchCount)
                if (touchCount >= 2) {
                    if (zoomEnabled) {
                        // Start pinch to zoom. Calculate distance between touch points and center point of the pinch.
                        val distance =
                            distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        scaleStart = scale
                        vDistStart = distance
                        vTranslateStart!![vTranslate!!.x] = vTranslate!!.y
                        vCenterStart!![(event.getX(0) + event.getX(1)) / 2] =
                            (event.getY(0) + event.getY(1)) / 2
                    } else {
                        // Abort all gestures on second touch
                        maxTouchCount = 0
                    }
                    // Cancel long click timer
                    handler.removeMessages(MESSAGE_LONG_CLICK)
                } else if (!isQuickScaling) {
                    // Start one-finger pan
                    vTranslateStart!![vTranslate!!.x] = vTranslate!!.y
                    vCenterStart!![event.x] = event.y

                    // Start long click timer
                    val m = Message()
                    m.what = MESSAGE_LONG_CLICK
                    m.arg1 = event.x.roundToInt()
                    m.arg2 = event.y.roundToInt()
                    handler.sendMessageDelayed(m, 500)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                var consumed = false
                if (maxTouchCount > 0) {
                    // TWO-FINGER GESTURES
                    if (touchCount >= 2) {
                        // Calculate new distance between touch points, to scale and pan relative to start values.
                        val vDistEnd =
                            distance(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                        val vCenterEndX = (event.getX(0) + event.getX(1)) / 2
                        val vCenterEndY = (event.getY(0) + event.getY(1)) / 2

                        if (zoomEnabled && (distance(
                                vCenterStart!!.x,
                                vCenterEndX,
                                vCenterStart!!.y,
                                vCenterEndY
                            ) > 5 || abs(vDistEnd - vDistStart) > 5 || isPanning)
                        ) {
                            isZooming = true
                            isPanning = true
                            consumed = true

                            val previousScale = scale
                            scale = min(maxScale, vDistEnd / vDistStart * scaleStart)
                            signalScaleChange(previousScale, scale)

                            if (scale <= minScale()) {
                                // Minimum scale reached so don't pan. Adjust start settings so any expand will zoom in.
                                vDistStart = vDistEnd
                                scaleStart = minScale()
                                vCenterStart!![vCenterEndX] = vCenterEndY
                                vTranslateStart!!.set(vTranslate!!)
                            } else if (panEnabled) {
                                // Translate to place the source image coordinate that was at the center of the pinch at the start
                                // at the center of the pinch now, to give simultaneous pan + zoom.
                                val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                                val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                                val vLeftNow = vLeftStart * (scale / scaleStart)
                                val vTopNow = vTopStart * (scale / scaleStart)
                                vTranslate!!.x = vCenterEndX - vLeftNow
                                vTranslate!!.y = vCenterEndY - vTopNow
                                if ((previousScale * sHeight() < getHeightInternal() && scale * sHeight() >= getHeightInternal()) || (previousScale * sWidth() < getWidthInternal() && scale * sWidth() >= getWidthInternal())) {
                                    fitToBounds(true)
                                    vCenterStart!![vCenterEndX] = vCenterEndY
                                    vTranslateStart!!.set(vTranslate!!)
                                    scaleStart = scale
                                    vDistStart = vDistEnd
                                }
                            } else if (sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate!!.x =
                                    (getWidthInternal() / 2f) - (scale * sRequestedCenter!!.x)
                                vTranslate!!.y =
                                    (getHeightInternal() / 2f) - (scale * sRequestedCenter!!.y)
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate!!.x = (getWidthInternal() / 2f) - (scale * sWidth() / 2f)
                                vTranslate!!.y =
                                    (getHeightInternal() / 2f) - (scale * sHeight() / 2f)
                            }

                            fitToBounds(true)
                            refreshRequiredResource(eagerLoadingEnabled)
                        }
                        // ONE-FINGER GESTURES
                    } else if (isQuickScaling) {
                        // One finger zoom
                        // Stole Google's Magical Formula™ to make sure it feels the exact same
                        var dist = abs(quickScaleVStart!!.y - event.y) * 2 + quickScaleThreshold

                        if (quickScaleLastDistance == -1f) {
                            quickScaleLastDistance = dist
                        }
                        val isUpwards = event.y > quickScaleVLastPoint!!.y
                        quickScaleVLastPoint!![0f] = event.y

                        val spanDiff = abs(1 - dist / quickScaleLastDistance) * 0.5f
                        if (spanDiff > 0.03f || quickScaleMoved) {
                            quickScaleMoved = true

                            var multiplier = 1f
                            if (quickScaleLastDistance > 0) {
                                multiplier = if (isUpwards) (1 + spanDiff) else (1 - spanDiff)
                            }

                            val previousScale = scale
                            scale = max(minScale(), min(maxScale, scale * multiplier))
                            signalScaleChange(previousScale, scale)

                            if (panEnabled) {
                                val vLeftStart = vCenterStart!!.x - vTranslateStart!!.x
                                val vTopStart = vCenterStart!!.y - vTranslateStart!!.y
                                val vLeftNow = vLeftStart * (scale / scaleStart)
                                val vTopNow = vTopStart * (scale / scaleStart)
                                vTranslate!!.x = vCenterStart!!.x - vLeftNow
                                vTranslate!!.y = vCenterStart!!.y - vTopNow
                                if ((previousScale * sHeight() < getHeightInternal() && scale * sHeight() >= getHeightInternal()) || (previousScale * sWidth() < getWidthInternal() && scale * sWidth() >= getWidthInternal())) {
                                    fitToBounds(true)
                                    vCenterStart!!.set(sourceToViewCoord(quickScaleSCenter!!))
                                    vTranslateStart!!.set(vTranslate!!)
                                    scaleStart = scale
                                    dist = 0f
                                }
                            } else if (sRequestedCenter != null) {
                                // With a center specified from code, zoom around that point.
                                vTranslate!!.x =
                                    (getWidthInternal() / 2f) - (scale * sRequestedCenter!!.x)
                                vTranslate!!.y =
                                    (getHeightInternal() / 2f) - (scale * sRequestedCenter!!.y)
                            } else {
                                // With no requested center, scale around the image center.
                                vTranslate!!.x = (getWidthInternal() / 2f) - (scale * sWidth() / 2f)
                                vTranslate!!.y =
                                    (getHeightInternal() / 2f) - (scale * sHeight() / 2f)
                            }
                        }

                        quickScaleLastDistance = dist

                        fitToBounds(true)
                        refreshRequiredResource(eagerLoadingEnabled)

                        consumed = true
                    } else if (!isZooming) {
                        // One finger pan - translate the image. We do this calculation even with pan disabled so click
                        // and long click behaviour is preserved.

                        // When long tap zoom animation has ended, use final vTranslate coordinates calculated by the animator

                        if (isLongTapZooming && vTranslateStart!!.equals(0f, 0f))
                            vTranslateStart = PointF(vTranslate!!.x, vTranslate!!.y)

                        val dx = abs(event.x - vCenterStart!!.x)
                        val dy = abs(event.y - vCenterStart!!.y)

                        //On the Samsung S6 long click event does not work, because the dx > 5 usually true
                        val offset = density * 5
                        if (dx > offset || dy > offset || isPanning) {
                            consumed = true

                            vTranslate!!.x = vTranslateStart!!.x + (event.x - vCenterStart!!.x)
                            vTranslate!!.y = vTranslateStart!!.y + (event.y - vCenterStart!!.y)

                            val lastX = vTranslate!!.x
                            val lastY = vTranslate!!.y
                            fitToBounds(true)

                            val atXEdge = lastX != vTranslate!!.x
                            val atYEdge = lastY != vTranslate!!.y
                            val edgeXSwipe = atXEdge && dx > dy && !isPanning
                            val edgeYSwipe = atYEdge && dy > dx && !isPanning
                            val yPan = lastY == vTranslate!!.y && dy > offset * 3

                            // Long tap zoom : slide vCenter to the center of the view as the user pans towards it
                            if (isLongTapZooming) {
                                val viewCenter = getvCenter()
                                if ((viewCenter.x > event.x && event.x > vCenterStart!!.x)
                                    || (viewCenter.x < event.x && event.x < vCenterStart!!.x)
                                ) vCenterStart!!.x = event.x
                                if ((viewCenter.y > event.y && event.y > vCenterStart!!.y)
                                    || (viewCenter.y < event.y && event.y < vCenterStart!!.y)
                                ) vCenterStart!!.y = event.y
                            }

                            if (!edgeXSwipe && !edgeYSwipe && (!atXEdge || !atYEdge || yPan || isPanning)) {
                                isPanning = true
                            } else if ((dx > offset && edgeXSwipe && swipeDirection == Direction.HORIZONTAL)
                                || (dy > offset && edgeYSwipe && swipeDirection == Direction.VERTICAL)
                            ) { // Page swipe
                                // Haven't panned the image, and we're at the left or right edge. Switch to page swipe.
                                maxTouchCount = 0
                                handler.removeMessages(MESSAGE_LONG_CLICK)
                                requestDisallowInterceptTouchEvent(false)
                            }

                            if (!panEnabled) {
                                vTranslate!!.x = vTranslateStart!!.x
                                vTranslate!!.y = vTranslateStart!!.y
                                requestDisallowInterceptTouchEvent(false)
                            }

                            refreshRequiredResource(eagerLoadingEnabled) // #TODO
                        }
                    }
                }
                if (consumed) {
                    handler.removeMessages(MESSAGE_LONG_CLICK)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_POINTER_2_UP -> {
                handler.removeMessages(MESSAGE_LONG_CLICK)
                if (isQuickScaling) {
                    isQuickScaling = false
                    if (!quickScaleMoved && isDoubleTapZoomEnabled) {
                        quickScaleSCenter?.let { qsc ->
                            vCenterStart?.let { vcs ->
                                doubleTapZoom(qsc, vcs)
                            }
                        }
                    }
                }
                if (maxTouchCount > 0 && (isZooming || isPanning)) {
                    if (isZooming && touchCount == 2) {
                        // Convert from zoom to pan with remaining touch
                        isPanning = true
                        vTranslateStart!![vTranslate!!.x] = vTranslate!!.y
                        if (event.actionIndex == 1) {
                            vCenterStart!![event.getX(0)] = event.getY(0)
                        } else {
                            vCenterStart!![event.getX(1)] = event.getY(1)
                        }
                    }
                    if (touchCount < 3) {
                        // End zooming when only one touch point
                        isZooming = false
                    }
                    if (touchCount < 2) {
                        // End panning when no touch points
                        isPanning = false
                        maxTouchCount = 0
                    }

                    if (isLongTapZooming) {
                        isLongTapZooming = false
                        resetScaleAndCenter()
                    }

                    // Trigger load of tiles now required
                    refreshRequiredResource(true)
                    return true
                }
                if (touchCount == 1) {
                    isZooming = false
                    isPanning = false
                    maxTouchCount = 0

                    if (isLongTapZooming) {
                        isLongTapZooming = false
                        resetScaleAndCenter()
                    }
                }
                return true
            }

            else -> {}
        }
        return false
    }

    private fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        val parent = parent
        parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    /**
     * Double tap zoom handler triggered from gesture detector or on touch, depending on whether
     * quick scale is enabled.
     */
    private fun doubleTapZoom(sCenter: PointF, vFocus: PointF) {
        if (!panEnabled) {
            if (sRequestedCenter != null) {
                // With a center specified from code, zoom around that point.
                sCenter.x = sRequestedCenter!!.x
                sCenter.y = sRequestedCenter!!.y
            } else {
                // With no requested center, scale around the image center.
                sCenter.x = sWidth() / 2f
                sCenter.y = sHeight() / 2f
            }
        }
        var targetDoubleTapZoomScale = min(maxScale, doubleTapZoomScale)
        if (doubleTapZoomCap > -1) {
            targetDoubleTapZoomScale =
                min(targetDoubleTapZoomScale, initialScale * doubleTapZoomCap)
        }
        Timber.d(">> doubleTapZoom $initialScale / $scale -> $targetDoubleTapZoomScale")

        val zoomIn = (scale <= targetDoubleTapZoomScale * 0.9) || scale == minScale
        val targetScale = if (zoomIn) targetDoubleTapZoomScale else minScale()
        if (doubleTapZoomStyle == ZoomStyle.FOCUS_CENTER_IMMEDIATE) {
            setScaleAndCenter(targetScale, sCenter)
        } else if (doubleTapZoomStyle == ZoomStyle.FOCUS_CENTER || !zoomIn || !panEnabled) {
            AnimationBuilder(targetScale, sCenter).withInterruptible(false)
                .withDuration(doubleTapZoomDuration.toLong())
                .withOrigin(AnimOrigin.DOUBLE_TAP_ZOOM).start()
        } else if (doubleTapZoomStyle == ZoomStyle.FOCUS_FIXED) {
            AnimationBuilder(targetScale, sCenter, vFocus).withInterruptible(false)
                .withDuration(doubleTapZoomDuration.toLong())
                .withOrigin(AnimOrigin.DOUBLE_TAP_ZOOM).start()
        }
        invalidate()
    }

    /**
     * Draw method should not be called until the view has dimensions so the first calls are used as triggers to calculate
     * the scaling and tiling required. Once the view is setup, tiles are displayed as they are loaded.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        createPaints()

        // If image or view dimensions are not known yet, abort.
        if (sWidth == 0 || sHeight == 0 || getWidthInternal() == 0 || getHeightInternal() == 0) return

        // When using tiles, on first render with no tile map ready, initialise it and kick off async base image loading.
        if (tileMap == null && regionDecoder != null)
            initialiseTileLayer(getMaxBitmapDimensions(canvas))

        // If image has been loaded or supplied as a bitmap, onDraw may be the first time the view has
        // dimensions and therefore the first opportunity to set scale and translate. If this call returns
        // false there is nothing to be drawn so return immediately.
        if (!checkReady()) return

        // Set scale and translate before draw.
        preDraw()

        // If animating scale, calculate current scale and center with easing equations
        if (anim?.vFocusStart != null) {
            var scaleElapsed = System.currentTimeMillis() - anim!!.time
            val finished = scaleElapsed > anim!!.duration
            scaleElapsed = min(scaleElapsed, anim!!.duration)
            val previousScale = scale
            scale = ease(
                anim!!.easing,
                scaleElapsed,
                anim!!.scaleStart,
                anim!!.scaleEnd - anim!!.scaleStart,
                anim!!.duration
            )
            signalScaleChange(previousScale, scale)

            // Apply required animation to the focal point
            val vFocusNowX = ease(
                anim!!.easing,
                scaleElapsed,
                anim!!.vFocusStart!!.x,
                anim!!.vFocusEnd!!.x - anim!!.vFocusStart!!.x,
                anim!!.duration
            )
            val vFocusNowY = ease(
                anim!!.easing,
                scaleElapsed,
                anim!!.vFocusStart!!.y,
                anim!!.vFocusEnd!!.y - anim!!.vFocusStart!!.y,
                anim!!.duration
            )
            // Find out where the focal point is at this scale and adjust its position to follow the animation path
            vTranslate!!.x -= sourceToViewX(anim!!.sCenterEnd!!.x) - vFocusNowX
            vTranslate!!.y -= sourceToViewY(anim!!.sCenterEnd!!.y) - vFocusNowY

            // For translate anims, showing the image non-centered is never allowed, for scaling anims it is during the animation.
            fitToBounds(finished || (anim!!.scaleStart == anim!!.scaleEnd))
            refreshRequiredResource(finished)
            if (finished) anim = null
            invalidate()
        }

        if (tileMap != null && isTileLayerReady()) {
            // Optimum sample size for current scale
            val sampleSize = min(fullImageSampleSize, calculateInSampleSize(scale))

            // First check for missing tiles - if there are any we need the base layer underneath to avoid gaps
            var hasMissingTiles = false
            for ((key, value) in tileMap!!) {
                if (key == sampleSize) {
                    for (tile in value) {
                        if (tile.visible && (tile.loading || tile.bitmap == null)) {
                            hasMissingTiles = true
                            break
                        }
                    }
                }
            }

            // Render all loaded tiles. LinkedHashMap used for bottom up rendering - lower res tiles underneath.
            for ((key, value) in tileMap!!) {
                if (key == sampleSize || hasMissingTiles) {
                    for (tile in value) {
                        if (tile.loading) continue
                        tile.bitmap?.let { tBmp ->
                            sourceToViewRect(tile.sRect!!, tile.vRect!!)
                            tileBgPaint?.let { bgPaint ->
                                tile.vRect?.let { canvas.drawRect(it, bgPaint) }
                            }
                            if (matrix == null) matrix = Matrix()
                            matrix?.reset()
                            setMatrixArray(
                                srcArray,
                                0f, 0f, tBmp.width.toFloat(), 0f,
                                tBmp.width.toFloat(), tBmp.height.toFloat(),
                                0f, tBmp.height.toFloat()
                            )
                            if (getRequiredRotation() == Orientation.O_0) {
                                tile.vRect?.apply {
                                    setMatrixArray(
                                        dstArray,
                                        left, top, right, top,
                                        right, bottom, left, bottom
                                    )
                                }
                            } else if (getRequiredRotation() == Orientation.O_90) {
                                tile.vRect?.apply {
                                    setMatrixArray(
                                        dstArray,
                                        right, top, right, bottom,
                                        left, bottom, left, top
                                    )
                                }
                            } else if (getRequiredRotation() == Orientation.O_180) {
                                tile.vRect?.apply {
                                    setMatrixArray(
                                        dstArray,
                                        right, bottom, left, bottom,
                                        left, top, right, top
                                    )
                                }
                            } else if (getRequiredRotation() == Orientation.O_270) {
                                tile.vRect?.apply {
                                    setMatrixArray(
                                        dstArray,
                                        left, bottom, left, top,
                                        right, top, right, bottom
                                    )
                                }
                            }
                            matrix?.setPolyToPoly(srcArray, 0, dstArray, 0, 4)
                            canvas.drawBitmap(tBmp, matrix!!, bitmapPaint)
                        }
                    }
                }
            }
        } else if (bitmap != null) {
            synchronized(singleImage) {
                val xScale = scale / singleImage.scale
                val yScale = scale / singleImage.scale

                if (matrix == null) matrix = Matrix()
                matrix?.apply {
                    reset()
                    postScale(xScale, yScale)
                    postRotate(getRequiredRotation().code.toFloat())
                    postTranslate(vTranslate!!.x, vTranslate!!.y)

                    if (getRequiredRotation() == Orientation.O_180) {
                        postTranslate(xScale * sWidth, yScale * sHeight)
                    } else if (getRequiredRotation() == Orientation.O_90) {
                        postTranslate(yScale * sHeight, 0f)
                    } else if (getRequiredRotation() == Orientation.O_270) {
                        postTranslate(0f, xScale * sWidth)
                    }
                }

                if (tileBgPaint != null) {
                    if (sRect == null) sRect = RectF()
                    sRect!![0f, 0f, sWidth.toFloat()] = sHeight.toFloat()
                    matrix!!.mapRect(sRect)
                    canvas.drawRect(sRect!!, tileBgPaint!!)
                }
                canvas.drawBitmap(bitmap!!, matrix!!, bitmapPaint)
            }
        }
    }

    /**
     * Helper method for setting the values of a tile matrix array.
     */
    private fun setMatrixArray(
        array: FloatArray,
        f0: Float,
        f1: Float,
        f2: Float,
        f3: Float,
        f4: Float,
        f5: Float,
        f6: Float,
        f7: Float
    ) {
        array[0] = f0
        array[1] = f1
        array[2] = f2
        array[3] = f3
        array[4] = f4
        array[5] = f5
        array[6] = f6
        array[7] = f7
    }

    /**
     * Checks whether the base layer of tiles or full size bitmap is ready.
     */
    private fun isTileLayerReady(): Boolean {
        if (bitmap != null) {
            return true
        } else if (tileMap != null) {
            var tileLayerReady = true
            for ((key, value) in tileMap!!) {
                if (key == fullImageSampleSize) {
                    for (tile in value) {
                        if (tile.loading || tile.bitmap == null) {
                            tileLayerReady = false
                            break
                        }
                    }
                }
            }
            return tileLayerReady
        }
        return false
    }

    /**
     * Check whether view and image dimensions are known and either a preview, full size image or
     * base layer tiles are loaded. First time, send ready event to listener. The next draw will
     * display an image.
     */
    private fun checkReady(): Boolean {
        val ready =
            getWidthInternal() > 0 && getHeightInternal() > 0 && sWidth > 0 && sHeight > 0 && (bitmap != null || isTileLayerReady())
        if (!readySent && ready) {
            preDraw()
            readySent = true
            onReady()
            onImageEventListener?.onReady()
        }
        return ready
    }

    /**
     * Check whether either the full size bitmap or base layer tiles are loaded. First time, send image
     * loaded event to listener.
     */
    private fun checkImageLoaded(): Boolean {
        val imageLoaded = isTileLayerReady()
        if (!imageLoadedSent && imageLoaded) {
            preDraw()
            imageLoadedSent = true
            onImageLoaded()
            onImageEventListener?.onImageLoaded()
        }
        return imageLoaded
    }

    /**
     * Creates Paint objects once when first needed.
     */
    private fun createPaints() {
        if (bitmapPaint == null) bitmapPaint = Paint()
        bitmapPaint?.apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
    }

    private fun loadBitmapToImage(context: Context, uri: Uri, inTargetScale: Float? = null) {
        // Don't load when previous loading is still happening
        if (singleImage.loading) return

        lifecycleScope?.launch {
            try {
                val targetScale = if (null == inTargetScale) {
                    val dims = getImageDimensions(context, uri)
                    val sat = ScaleAndTranslate(0f, PointF(0f, 0f))
                    fitToBounds(true, dims, sat).scale
                } else inTargetScale

                // Don't load something that's already loaded
                if (singleImage.location == uri.toString() && abs(singleImage.targetScale - targetScale) < 0.01) return@launch

                val bmp = loadBitmap(context, uri)
                // Remove invalid results
                val res = processBitmap(
                    uri,
                    context,
                    bmp,
                    this@CustomSubsamplingScaleImageView,
                    targetScale
                )
                withContext(Dispatchers.Main) {
                    onImageLoaded(
                        uri.toString(),
                        targetScale,
                        res.bitmap,
                        res.orientation,
                        false,
                        res.scale
                    )
                }
            } catch (t: Throwable) {
                onImageEventListener?.onImageLoadError(t)
            }
        }
    }

    /**
     * Called on first draw when the view has dimensions. Calculates the initial sample size and starts async loading of
     * the base layer image - the whole source subsampled as necessary.
     */
    @SuppressLint("NewApi")
    @Synchronized
    private fun initialiseTileLayer(maxTileDimensions: Point) {
        // null Uri's may happen when sliding fast, which causes views to be reset when recycled by the RecyclerView
        // they reset faster than their initialization can process them, hence initialiseTileLayer being called (e.g. through onDraw) _after_ recycle has been called
        if (null == uri) return

        satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        fitToBounds(true, Point(sWidth(), sHeight()), satTemp!!)
        val targetScale = satTemp!!.scale

        orientation = detectRotation()

        // Load double resolution - next level will be split into four tiles and at the center all four are required,
        // so don't bother with tiling until the next level 16 tiles are needed.
        fullImageSampleSize = calculateInSampleSize(satTemp!!.scale)
        if (fullImageSampleSize > 1) fullImageSampleSize /= 2

        if (fullImageSampleSize == 1 && sRegion == null && sWidth() < maxTileDimensions.x && sHeight() < maxTileDimensions.y) {
            Timber.d("Using basic loading")
            // Whole image is required at native resolution, and is smaller than the canvas max bitmap size.
            // Use BitmapDecoder for better image support.
            if (regionDecoder != null) regionDecoder?.recycle()
            regionDecoder = null

            loadBitmapToImage(context, uri!!, targetScale)
        } else {
            Timber.d("Using tiled loading")
            initialiseTileMap(maxTileDimensions)

            val baseGrid = tileMap!![fullImageSampleSize]
            baseGrid?.let { bg ->
                lifecycleScope?.launch {
                    try {
                        bg.forEach {
                            val tile =
                                loadTile(this@CustomSubsamplingScaleImageView, regionDecoder!!, it)
                            if (tile.bitmap?.isRecycled == false) {
                                processTile(tile, targetScale)
                                withContext(Dispatchers.Main) { onTileLoaded() }
                            }
                        }
                        withContext(Dispatchers.Main) { refreshRequiredResource(true) }
                    } catch (t: Throwable) {
                        onImageEventListener?.onTileLoadError(t)
                    }
                }
            }
        }
    }

    // TODO doc
    private fun refreshRequiredResource(load: Boolean) {
        if (regionDecoder == null || tileMap == null) refreshSingleDebouncer.submit(load)
        else {
            val sampleSize = min(fullImageSampleSize, calculateInSampleSize(scale))
            refreshRequiredTiles(load, sampleSize)
        }
    }

    // TODO doc
    private fun refreshSingle(load: Boolean) {
        if (!load) return
        uri?.let { loadBitmapToImage(context, it, getVirtualScale()) }
    }

    /**
     * Loads the optimum tiles for display at the current scale and translate, so the screen can be filled with tiles
     * that are at least as high resolution as the screen. Frees up bitmaps that are now off the screen.
     *
     * @param load Whether to load the new tiles needed. Use false while scrolling/panning for performance.
     */
    private fun refreshRequiredTiles(load: Boolean, sampleSize: Int) {
        // Load tiles of the correct sample size that are on screen. Discard tiles off screen, and those that are higher
        // resolution than required, or lower res than required but not the base layer, so the base layer is always present.
        for ((_, value) in tileMap!!) {
            for (tile in value) {
                if (tile.sampleSize < sampleSize || (tile.sampleSize > sampleSize && tile.sampleSize != fullImageSampleSize)) {
                    tile.visible = false
                    if (!tile.loading) {
                        tile.bitmap?.recycle()
                        tile.bitmap = null
                    }
                }
                if (tile.sampleSize == sampleSize) {
                    if (tileVisible(tile)) {
                        tile.visible = true
                        if (!tile.loading && tile.bitmap == null && load) {
                            lifecycleScope?.launch {
                                try {
                                    loadTile(
                                        this@CustomSubsamplingScaleImageView,
                                        regionDecoder!!,
                                        tile
                                    )
                                    if (tile.bitmap?.isRecycled == false) {
                                        processTile(tile, scale)
                                        withContext(Dispatchers.Main) { onTileLoaded() }
                                    }
                                } catch (t: Throwable) {
                                    onImageEventListener?.onTileLoadError(t)
                                }
                            }
                        }
                    } else if (tile.sampleSize != fullImageSampleSize) {
                        tile.visible = false
                        if (!tile.loading) {
                            tile.bitmap?.recycle()
                            tile.bitmap = null
                        }
                    }
                } else if (tile.sampleSize == fullImageSampleSize) {
                    tile.visible = true
                }
            }
        }
    }

    /**
     * Determine whether tile is visible.
     */
    private fun tileVisible(tile: Tile): Boolean {
        val sVisLeft = viewToSourceX(0f)
        val sVisRight = viewToSourceX(getWidthInternal().toFloat())
        val sVisTop = viewToSourceY(0f)
        val sVisBottom = viewToSourceY(getHeightInternal().toFloat())

        return tile.sRect?.let {
            !(sVisLeft > it.right || it.left > sVisRight || sVisTop > it.bottom || it.top > sVisBottom)
        } == true
    }

    /**
     * Sets scale and translate ready for the next draw.
     */
    private fun preDraw() {
        if (getWidthInternal() == 0 || getHeightInternal() == 0 || sWidth <= 0 || sHeight <= 0) {
            return
        }

        // If waiting to translate to new center position, set translate now
        if (sPendingCenter != null) {
            pendingScale?.let {
                val previousScale = scale
                scale = it
                signalScaleChange(previousScale, scale)
                if (vTranslate == null) vTranslate = PointF()
                vTranslate!!.x = (getWidthInternal() / 2f) - (scale * sPendingCenter!!.x)
                vTranslate!!.y = (getHeightInternal() / 2f) - (scale * sPendingCenter!!.y)
                sPendingCenter = null
                pendingScale = null
                fitToBounds(true)
                refreshRequiredResource(true)
            }
        }

        // On first display of base image set up position, and in other cases make sure scale is correct.
        fitToBounds(false)
    }

    /**
     * Calculates sample size to fit the source image in given bounds.
     */
    private fun calculateInSampleSize(scale: Float): Int {
        var theScale = scale
        if (minimumTileDpi > 0) {
            theScale *= (minimumTileDpi / screenDpi)
        }

        val reqWidth = (sWidth() * theScale).toInt()
        val reqHeight = (sHeight() * theScale).toInt()

        // Raw height and width of image
        var inSampleSize = 1
        if (reqWidth == 0 || reqHeight == 0) {
            return 32
        }

        if (sHeight() > reqHeight || sWidth() > reqWidth) {
            // Calculate ratios of height and width to requested height and width

            val heightRatio = (sHeight() * 1f / reqHeight).roundToInt()
            val widthRatio = (sWidth() * 1f / reqWidth).roundToInt()

            // Choose the smallest ratio as inSampleSize value, this will guarantee
            // a final image with both dimensions larger than or equal to the
            // requested height and width.
            inSampleSize = min(heightRatio, widthRatio)
        }

        // We want the actual sample size that will be used, so round down to nearest power of 2.
        var power = 1
        while (power * 2 < inSampleSize) power *= 2

        return power
    }

    /**
     * Adjusts current scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension.
     *
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     */
    private fun fitToBounds(center: Boolean) {
        fitToBounds(center, Point(sWidth(), sHeight()))
    }

    private fun fitToBounds(center: Boolean, sSize: Point) {
        var init = false
        if (vTranslate == null) {
            init = true
            vTranslate = PointF(0f, 0f)
        }
        if (satTemp == null) satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        satTemp?.let {
            it.scale = scale
            it.vTranslate.set(vTranslate!!)
            fitToBounds(center, sSize, it)
            val previousScale = scale
            scale = it.scale
            signalScaleChange(previousScale, scale)
            if (-1f == initialScale) {
                initialScale = scale
                Timber.i(">> initialScale : %s", initialScale)
            }
            vTranslate!!.set(it.vTranslate)
        }

        // Recenter images if their dimensions are lower than the view's dimensions after the above call to fitToBounds
        val viewHeight = getHeightInternal() - paddingBottom + paddingTop
        val viewWidth = getWidthInternal() - paddingLeft + paddingRight
        val sourcevWidth = sSize.x * scale
        val sourcevHeight = sSize.y * scale

        vTranslate?.let {
            if (sourcevWidth < viewWidth) it[(viewWidth - sourcevWidth) / 2] = it.y
            if (sourcevHeight < viewHeight) it[it.x] = (viewHeight - sourcevHeight) / 2
        }

        // Display images from the right side if asked to do so
        if (!offsetLeftSide && !sideOffsetConsumed
            && sourcevWidth > viewWidth
            && !isCentered(minimumScaleType)
        ) {
            vTranslate!![scale * (-sSize.x + viewWidth / scale)] = vTranslate!!.y
            sideOffsetConsumed = true
        }

        if (init && isCentered(minimumScaleType)) {
            vTranslate!!.set(vTranslateForSCenter(sSize.x / 2f, sSize.y / 2f, scale, sSize))
        }
    }

    /**
     * Adjusts hypothetical future scale and translate values to keep scale within the allowed range and the image on screen. Minimum scale
     * is set so one dimension fills the view and the image is centered on the other dimension. Used to calculate what the target of an
     * animation should be.
     *
     * @param center Whether the image should be centered in the dimension it's too small to fill. While animating this can be false to avoid changes in direction as bounds are reached.
     * @param sat    The scale we want and the translation we're aiming for. The values are adjusted to be valid.
     */
    private fun fitToBounds(
        center: Boolean,
        sSize: Point,
        sat: ScaleAndTranslate
    ): ScaleAndTranslate {
        var doCenter = center
        if (panLimit == PanLimit.OUTSIDE && isReady()) doCenter = false

        val targetvTranslate = sat.vTranslate

        val targetScale = limitedScale(sat.scale, sSize)
        val scaleWidth = targetScale * sSize.x
        val scaleHeight = targetScale * sSize.y

        if (panLimit == PanLimit.CENTER && isReady()) {
            targetvTranslate.x = max(targetvTranslate.x, getWidthInternal() / 2f - scaleWidth)
            targetvTranslate.y = max(targetvTranslate.y, getHeightInternal() / 2f - scaleHeight)
        } else if (doCenter) {
            targetvTranslate.x = max(targetvTranslate.x, getWidthInternal() - scaleWidth)
            targetvTranslate.y = max(targetvTranslate.y, getHeightInternal() - scaleHeight)
        } else {
            targetvTranslate.x = max(targetvTranslate.x, -scaleWidth)
            targetvTranslate.y = max(targetvTranslate.y, -scaleHeight)
        }

        // Asymmetric padding adjustments
        val maxTx: Float
        val maxTy: Float
        if (panLimit == PanLimit.CENTER && isReady()) {
            maxTx = max(0f, getWidthInternal() / 2f)
            maxTy = max(0f, getHeightInternal() / 2f)
        } else if (doCenter) {
            val xPaddingRatio =
                if (paddingLeft > 0 || paddingRight > 0) paddingLeft / (paddingLeft + paddingRight).toFloat() else 0.5f
            val yPaddingRatio =
                if (paddingTop > 0 || paddingBottom > 0) paddingTop / (paddingTop + paddingBottom).toFloat() else 0.5f
            maxTx = max(0f, (getWidthInternal() - scaleWidth) * xPaddingRatio)
            maxTy = max(0f, (getHeightInternal() - scaleHeight) * yPaddingRatio)
        } else {
            maxTx = max(0f, getWidthInternal().toFloat())
            maxTy = max(0f, getHeightInternal().toFloat())
        }

        targetvTranslate.x = min(targetvTranslate.x, maxTx)
        targetvTranslate.y = min(targetvTranslate.y, maxTy)

        sat.scale = targetScale
        return sat
    }

    /**
     * Once source image and view dimensions are known, creates a map of sample size to tile grid.
     */
    private fun initialiseTileMap(maxTileDimensions: Point) {
        tileMap = LinkedHashMap()
        var sampleSize = fullImageSampleSize
        var xTiles = 1
        var yTiles = 1
        while (true) {
            var sTileWidth = sWidth() / xTiles
            var sTileHeight = sHeight() / yTiles
            var subTileWidth = sTileWidth / sampleSize
            var subTileHeight = sTileHeight / sampleSize
            while (subTileWidth + xTiles + 1 > maxTileDimensions.x || (subTileWidth > getWidthInternal() * 1.25 && sampleSize < fullImageSampleSize)) {
                xTiles += 1
                sTileWidth = sWidth() / xTiles
                subTileWidth = sTileWidth / sampleSize
            }
            while (subTileHeight + yTiles + 1 > maxTileDimensions.y || (subTileHeight > getHeightInternal() * 1.25 && sampleSize < fullImageSampleSize)) {
                yTiles += 1
                sTileHeight = sHeight() / yTiles
                subTileHeight = sTileHeight / sampleSize
            }
            val tileGrid: MutableList<Tile> = ArrayList(xTiles * yTiles)
            for (x in 0 until xTiles) {
                for (y in 0 until yTiles) {
                    val tile = Tile()
                    tile.sampleSize = sampleSize
                    tile.visible = sampleSize == fullImageSampleSize
                    tile.sRect = Rect(
                        x * sTileWidth,
                        y * sTileHeight,
                        if (x == xTiles - 1) sWidth() else (x + 1) * sTileWidth,
                        if (y == yTiles - 1) sHeight() else (y + 1) * sTileHeight
                    )
                    tile.vRect = RectF(0f, 0f, 0f, 0f)
                    tile.fileSRect = Rect(tile.sRect)
                    tileGrid.add(tile)
                }
            }
            tileMap!![sampleSize] = tileGrid
            if (sampleSize == 1) {
                break
            } else {
                sampleSize /= 2
            }
        }
    }

    @Throws(Exception::class)
    private suspend fun initTiles(
        context: Context,
        source: Uri
    ): IntArray = withContext(Dispatchers.IO) {
        val sourceUri = source.toString()
        Timber.d("Init tiles BEGIN %s", sourceUri)
        SkiaImageRegionDecoder(preferredBitmapConfig).let { decoder ->
            regionDecoder = decoder
            val dimensions = decoder.init(context, source)
            var sWidthTile = dimensions.x
            var sHeightTile = dimensions.y
            val exifOrientation = getExifOrientation(context, sourceUri)
            if (sWidthTile > -1) {
                sRegion?.apply {
                    left = max(0, left)
                    top = max(0, top)
                    right = min(sWidthTile, right)
                    bottom = min(sHeightTile, bottom)
                    sWidthTile = width()
                    sHeightTile = height()
                }
            }
            Timber.d("Init tiles END %s", sourceUri)
            return@withContext intArrayOf(sWidthTile, sHeightTile, exifOrientation.code)
        }
    }

    /**
     * Called by worker task when decoder is ready and image size and EXIF orientation is known.
     */
    @Synchronized
    private fun onTilesInitialized(sWidth: Int, sHeight: Int, sOrientation: Orientation) {
        // If actual dimensions don't match the declared size, reset everything.
        if (this.sWidth > 0 && (this.sHeight > 0) && (this.sWidth != sWidth || this.sHeight != sHeight)) {
            reset(false)
            if (bitmap != null) {
                if (!bitmapIsCached && !singleImage.loading) bitmap?.recycle()
                bitmap = null
                if (bitmapIsCached) onImageEventListener?.onPreviewReleased()
                bitmapIsCached = false
            }
        }
        this.sWidth = sWidth
        this.sHeight = sHeight
        this.sOrientation = sOrientation
        checkReady()
        if (!checkImageLoaded() && maxTileWidth > 0 && maxTileWidth != TILE_SIZE_AUTO && maxTileHeight > 0 && maxTileHeight != TILE_SIZE_AUTO && getWidthInternal() > 0 && getHeightInternal() > 0) {
            initialiseTileLayer(Point(maxTileWidth, maxTileHeight))
        }
        invalidate()
        requestLayout()
    }

    private suspend fun loadTile(
        view: CustomSubsamplingScaleImageView,
        decoder: ImageRegionDecoder,
        tile: Tile
    ): Tile = withContext(Dispatchers.IO) {
        if (decoder.isReady() && tile.visible) {
            view.decoderLock.readLock().lock()
            try {
                if (decoder.isReady()) {
                    tile.loading = true
                    // Update tile's file sRect according to rotation
                    tile.sRect?.let { sRect ->
                        tile.fileSRect?.let { fileSRect ->
                            view.fileSRect(sRect, fileSRect)
                            view.sRegion?.let { sRegion ->
                                fileSRect.offset(sRegion.left, sRegion.top)
                            }
                            tile.bitmap = decoder.decodeRegion(fileSRect, tile.sampleSize)
                        }
                    }
                }
            } finally {
                view.decoderLock.readLock().unlock()
            }
        }
        if (null == tile.bitmap) tile.loading = false
        return@withContext tile
    }

    private suspend fun processTile(
        loadedTile: Tile,
        targetScale: Float
    ): Tile = withContext(Dispatchers.Default) {
        // Take any prior subsampling into consideration _before_ processing the tile
        loadedTile.sRect?.apply {
            Timber.v("Processing tile $left $top")
        }
        loadedTile.bitmap?.let { bmp ->
            val cropped = if (isSmartCrop) smartCropBitmap(bmp) else bmp
            val resizeScale = targetScale * loadedTile.sampleSize
            val resizeResult = resizeBitmap(glEsRenderer, cropped, resizeScale)
            loadedTile.bitmap = resizeResult.first
            loadedTile.loading = false
        }
        return@withContext loadedTile
    }

    /**
     * Called by worker task when a tile has loaded. Redraws the view.
     */
    @Synchronized
    private fun onTileLoaded() {
        checkReady()
        checkImageLoaded()
        if (isTileLayerReady()) {
            if (!bitmapIsCached && !singleImage.loading) bitmap?.recycle()
            bitmap = null
            if (bitmapIsCached) onImageEventListener?.onPreviewReleased()
            bitmapIsCached = false
        }
        invalidate()
    }

    @Throws(Exception::class)
    private suspend fun loadBitmap(context: Context, uri: Uri): Bitmap =
        withContext(Dispatchers.IO) {
            singleImage.loading = true
            val decoder: ImageDecoder = SkiaImageDecoder(preferredBitmapConfig)
            return@withContext decoder.decode(context, uri)
        }

    private suspend fun processBitmap(
        source: Uri,
        context: Context,
        bitmap: Bitmap,
        view: CustomSubsamplingScaleImageView,
        targetScale: Float
    ): ProcessBitmapResult = withContext(Dispatchers.Default) {
        singleImage.rawWidth = bitmap.width
        singleImage.rawHeight = bitmap.height

        // TODO sharp mode - don't ask to resize when the image in memory already has the correct target scale
        val isPrepareRotate = when (detectRotation()) {
            Orientation.O_90 -> true
            Orientation.O_270 -> true
            else -> false
        }
        val targetWidth = if (isPrepareRotate) getUsefulHeight() else getUsefulWidth()
        val targetHeight = if (isPrepareRotate) getUsefulWidth() else getUsefulHeight()
        val resizeResult = if (minimumScaleType == ScaleType.STRETCH_SCREEN) {
            val stretchedScale = PointF(
                targetWidth.toFloat() / bitmap.width,
                targetHeight.toFloat() / bitmap.height
            )
            Timber.d("stretchedScale ${stretchedScale.x}x${stretchedScale.y}")
            // That's more or less cheating but it does work
            singleImage.rawWidth = targetWidth
            singleImage.rawHeight = targetHeight
            val cropped = if (isSmartCrop) smartCropBitmap(bitmap) else bitmap
            resizeBitmap(glEsRenderer, cropped, stretchedScale)
        } else {
            val cropped = if (isSmartCrop) smartCropBitmap(bitmap) else bitmap
            resizeBitmap(glEsRenderer, cropped, targetScale)
        }

        singleImage.loading = false
        return@withContext ProcessBitmapResult(
            resizeResult.first,
            view.getExifOrientation(context, source.toString()),
            resizeResult.second
        )
    }

    /**
     * Called by worker task when full size image bitmap is ready (tiling is disabled).
     */
    @Synchronized
    private fun onImageLoaded(
        location: String,
        targetScale: Float,
        bitmap: Bitmap,
        sOrientation: Orientation,
        bitmapIsCached: Boolean,
        imageScale: Float
    ) {
        orientation = detectRotation()

        if (!this.bitmapIsCached && !singleImage.loading) this.bitmap?.recycle()

        if (this.bitmap != null && this.bitmapIsCached) onImageEventListener?.onPreviewReleased()

        Timber.v("imageLoaded $imageScale")
        synchronized(singleImage) {
            this.bitmapIsCached = bitmapIsCached
            singleImage.scale = imageScale
            singleImage.location = location
            singleImage.targetScale = targetScale
            if (!bitmap.isRecycled) {
                this.bitmap = bitmap
                this.sWidth = bitmap.width
                this.sHeight = bitmap.height
            } else {
                this.bitmap = null
            }
            this.sOrientation = sOrientation
        }
        val ready = checkReady()
        val imageLoaded = checkImageLoaded()
        if (ready || imageLoaded) {
            Timber.v("imageLoaded 2 $imageScale")
            invalidate()
            requestLayout()
        }
    }

    /**
     * Helper method for load tasks. Examines the EXIF info on the image file to determine the orientation.
     * This will only work for external files, not assets, resources or other URIs.
     */
    @AnyThread
    private fun getExifOrientation(context: Context, sourceUri: String): Orientation {
        var exifOrientation = Orientation.O_0
        if (sourceUri.startsWith(ContentResolver.SCHEME_CONTENT)) {
            var cursor: Cursor? = null
            try {
                val columns = arrayOf(MediaStore.MediaColumns.ORIENTATION)
                cursor =
                    context.contentResolver.query(sourceUri.toUri(), columns, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val targetOrientation = Orientation.fromCode(cursor.getInt(0))
                    if (targetOrientation != Orientation.USE_EXIF) {
                        exifOrientation = targetOrientation
                    } else {
                        Timber.w("Unsupported orientation: %s", targetOrientation)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not get orientation of image from media store")
            } finally {
                cursor?.close()
            }
        } else if (sourceUri.startsWith(FILE_SCHEME) && !sourceUri.startsWith(ASSET_SCHEME)) {
            try {
                val exifInterface = ExifInterface(sourceUri.substring(FILE_SCHEME.length - 1))
                val orientationAttr = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                if (orientationAttr == ExifInterface.ORIENTATION_NORMAL || orientationAttr == ExifInterface.ORIENTATION_UNDEFINED) {
                    exifOrientation = Orientation.O_0
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_90) {
                    exifOrientation = Orientation.O_90
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_180) {
                    exifOrientation = Orientation.O_180
                } else if (orientationAttr == ExifInterface.ORIENTATION_ROTATE_270) {
                    exifOrientation = Orientation.O_270
                } else {
                    Timber.w("Unsupported EXIF orientation: %s", orientationAttr)
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not get EXIF orientation of image")
            }
        }
        return exifOrientation
    }

    /**
     * Indicates if the picture needs to be rotated 90°, according to the given picture proportions (auto-rotate feature)
     * The goal is to align the picture's proportions with the phone screen's proportions
     * NB : The result of this method is independent from auto-rotate mode being enabled
     *
     * @param sWidth  Picture width
     * @param sHeight Picture height
     * @return True if the picture needs to be rotated 90°
     */
    private fun needsRotating(sWidth: Int, sHeight: Int): Boolean {
        val isSourceSquare = (abs(sHeight - sWidth) < sWidth * 0.1)
        if (isSourceSquare) return false

        val isSourceLandscape = (sWidth > sHeight * 1.33)
        val isScreenLandscape = (screenWidth > screenHeight * 1.33)
        return (isSourceLandscape != isScreenLandscape)
    }

    class SingleImage {
        var location = ""
        var targetScale = 1f

        var scale = 1f
        var rawWidth = -1
        var rawHeight = -1

        var loading = false

        fun reset() {
            location = ""
            targetScale = 1f
            scale = 1f
            rawWidth = -1
            rawHeight = -1
            loading = false
        }
    }

    class Tile {
        var sRect: Rect? = null
        var sampleSize = 0
        var bitmap: Bitmap? = null
        var loading = false
        var visible = false

        // Volatile fields instantiated once then updated before use to reduce GC.
        var vRect: RectF? = null
        var fileSRect: Rect? = null
    }

    class Anim {
        var scaleStart: Float = 0f // Scale at start of anim
        var scaleEnd: Float = 0f // Scale at end of anim (target)
        var sCenterStart: PointF? = null // Source center poin^t at start

        // Source center point at end, adjusted for pan limits
        var sCenterEnd: PointF? = null

        // Source center point that was requested, without adjustment
        var sCenterEndRequested: PointF? = null
        var vFocusStart: PointF? = null // View point that was double tapped

        // Where the view focal point should be moved to during the anim
        var vFocusEnd: PointF? = null
        var duration: Long = 500 // How long the anim takes
        var interruptible: Boolean = true // Whether the anim can be interrupted by a touch

        var easing = Easing.IN_OUT_QUAD // Easing style

        var origin = AnimOrigin.ANIM // Animation origin (API, double tap or fling)
        var time = System.currentTimeMillis() // Start time
    }

    class ScaleAndTranslate(var scale: Float, val vTranslate: PointF)

    /**
     * By default the View automatically calculates the optimal tile size. Set this to override this, and force an upper limit to the dimensions of the generated tiles. Passing [.TILE_SIZE_AUTO] will re-enable the default behaviour.
     *
     * @param maxPixelsX Maximum tile width.
     * @param maxPixelsY Maximum tile height.
     */
    fun setMaxTileSize(maxPixelsX: Int, maxPixelsY: Int) {
        this.maxTileWidth = maxPixelsX
        this.maxTileHeight = maxPixelsY
    }

    /**
     * Use canvas max bitmap width and height instead of the default 2048, to avoid redundant tiling.
     */
    private fun getMaxBitmapDimensions(canvas: Canvas): Point {
        return Point(
            min(canvas.maximumBitmapWidth, maxTileWidth),
            min(canvas.maximumBitmapHeight, maxTileHeight)
        )
    }

    /**
     * Get source width taking rotation into account.
     */
    private fun sWidth(): Int {
        val rotation = getRequiredRotation()
        return if (rotation.code == 90 || rotation.code == 270) {
            if (singleImage.rawHeight > -1) singleImage.rawHeight else sHeight
        } else {
            if (singleImage.rawWidth > -1) singleImage.rawWidth else sWidth
        }
    }

    /**
     * Get source height taking rotation into account.
     */
    private fun sHeight(): Int {
        val rotation = getRequiredRotation()
        return if (rotation.code == 90 || rotation.code == 270) {
            if (singleImage.rawWidth > -1) singleImage.rawWidth else sWidth
        } else {
            if (singleImage.rawHeight > -1) singleImage.rawHeight else sHeight
        }
    }

    /**
     * Converts source rectangle from tile, which treats the image file as if it were in the correct orientation already,
     * to the rectangle of the image that needs to be loaded.
     */
    @AnyThread
    private fun fileSRect(sRect: Rect, target: Rect) {
        if (getRequiredRotation().code == 0) {
            target.set(sRect)
        } else if (getRequiredRotation().code == 90) {
            target[sRect.top, sHeight - sRect.right, sRect.bottom] = sHeight - sRect.left
        } else if (getRequiredRotation().code == 180) {
            target[sWidth - sRect.right, sHeight - sRect.bottom, sWidth - sRect.left] =
                sHeight - sRect.top
        } else {
            target[sWidth - sRect.bottom, sRect.left, sWidth - sRect.top] = sRect.right
        }
    }

    /**
     * Determines the rotation to be applied to tiles, based on EXIF orientation or chosen setting.
     */
    @AnyThread
    private fun getRequiredRotation(): Orientation {
        return if (orientation == Orientation.USE_EXIF) sOrientation else orientation
    }

    /**
     * Pythagoras distance between two points.
     */
    private fun distance(x0: Float, x1: Float, y0: Float, y1: Float): Float {
        val x = x0 - x1
        val y = y0 - y1
        return sqrt(x * x + y * y)
    }

    /**
     * Releases all resources the view is using and resets the state, nulling any fields that use significant memory.
     * After you have called this method, the view can be re-used by setting a new image. Settings are remembered
     * but state (scale and center) is forgotten. You can restore these yourself if required.
     */
    fun recycle() {
        reset(true)
        bitmapPaint = null
        tileBgPaint = null
    }

    /**
     * Convert screen to source x coordinate.
     */
    private fun viewToSourceX(vx: Float): Float {
        return (vx - (if ((null == vTranslate)) 0f else vTranslate!!.x)) / scale
    }

    /**
     * Convert screen to source y coordinate.
     */
    private fun viewToSourceY(vy: Float): Float {
        return (vy - (if ((null == vTranslate)) 0f else vTranslate!!.y)) / scale
    }

    /**
     * Converts a rectangle within the view to the corresponding rectangle from the source file, taking
     * into account the current scale, translation, orientation and clipped region. This can be used
     * to decode a bitmap from the source file.
     *
     *
     * This method will only work when the image has fully initialised, after [.isReady] returns
     * true. It is not guaranteed to work with preloaded bitmaps.
     *
     *
     * The result is written to the fRect argument. Re-use a single instance for efficiency.
     *
     * @param vRect rectangle representing the view area to interpret.
     * @param fRect rectangle instance to which the result will be written. Re-use for efficiency.
     */
    fun viewToFileRect(vRect: Rect, fRect: Rect) {
        if (vTranslate == null || !readySent) {
            return
        }
        fRect[viewToSourceX(vRect.left.toFloat()).toInt(), viewToSourceY(vRect.top.toFloat()).toInt(), viewToSourceX(
            vRect.right.toFloat()
        ).toInt()] =
            viewToSourceY(vRect.bottom.toFloat()).toInt()
        fileSRect(fRect, fRect)
        fRect[max(0, fRect.left), max(0, fRect.top), min(sWidth, fRect.right)] =
            min(sHeight, fRect.bottom)
        if (sRegion != null) {
            fRect.offset(sRegion!!.left, sRegion!!.top)
        }
    }

    /**
     * Find the area of the source file that is currently visible on screen, taking into account the
     * current scale, translation, orientation and clipped region. This is a convenience method; see
     * [.viewToFileRect].
     *
     * @param fRect rectangle instance to which the result will be written. Re-use for efficiency.
     */
    fun visibleFileRect(fRect: Rect) {
        if (vTranslate == null || !readySent) {
            return
        }
        fRect[0, 0, getWidthInternal()] = getHeightInternal()
        viewToFileRect(fRect, fRect)
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vxy view X/Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    fun viewToSourceCoord(vxy: PointF): PointF {
        return viewToSourceCoord(vxy.x, vxy.y, PointF())
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vx view X coordinate.
     * @param vy view Y coordinate.
     * @return a coordinate representing the corresponding source coordinate.
     */
    fun viewToSourceCoord(vx: Float, vy: Float): PointF {
        return viewToSourceCoord(vx, vy, PointF())
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vxy     view coordinates to convert.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    fun viewToSourceCoord(vxy: PointF, sTarget: PointF): PointF {
        return viewToSourceCoord(vxy.x, vxy.y, sTarget)
    }

    /**
     * Convert screen coordinate to source coordinate.
     *
     * @param vx      view X coordinate.
     * @param vy      view Y coordinate.
     * @param sTarget target object for result. The same instance is also returned.
     * @return source coordinates. This is the same instance passed to the sTarget param.
     */
    fun viewToSourceCoord(vx: Float, vy: Float, sTarget: PointF): PointF {
        sTarget[viewToSourceX(vx)] = viewToSourceY(vy)
        return sTarget
    }

    /**
     * Convert source to view x coordinate.
     */
    private fun sourceToViewX(sx: Float): Float {
        return (sx * scale) + (if ((null == vTranslate)) 0f else vTranslate!!.x)
    }

    /**
     * Convert source to view y coordinate.
     */
    private fun sourceToViewY(sy: Float): Float {
        return (sy * scale) + (if ((null == vTranslate)) 0f else vTranslate!!.y)
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sxy source coordinates to convert.
     * @return view coordinates.
     */
    fun sourceToViewCoord(sxy: PointF): PointF {
        return sourceToViewCoord(sxy.x, sxy.y, PointF())
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sx source X coordinate.
     * @param sy source Y coordinate.
     * @return view coordinates.
     */
    fun sourceToViewCoord(sx: Float, sy: Float): PointF {
        return sourceToViewCoord(sx, sy, PointF())
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sxy     source coordinates to convert.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    fun sourceToViewCoord(sxy: PointF, vTarget: PointF): PointF {
        return sourceToViewCoord(sxy.x, sxy.y, vTarget)
    }

    /**
     * Convert source coordinate to view coordinate.
     *
     * @param sx      source X coordinate.
     * @param sy      source Y coordinate.
     * @param vTarget target object for result. The same instance is also returned.
     * @return view coordinates. This is the same instance passed to the vTarget param.
     */
    fun sourceToViewCoord(sx: Float, sy: Float, vTarget: PointF): PointF {
        vTarget[sourceToViewX(sx)] = sourceToViewY(sy)
        return vTarget
    }

    /**
     * Convert source rect to screen rect
     */
    private fun sourceToViewRect(sRect: Rect, vTarget: RectF) {
        vTarget[sourceToViewX(sRect.left.toFloat()), sourceToViewY(sRect.top.toFloat()),
            sourceToViewX(sRect.right.toFloat())] = sourceToViewY(sRect.bottom.toFloat())
    }

    /**
     * Get the translation required to place a given source coordinate at the center of the screen, with the center
     * adjusted for asymmetric padding. Accepts the desired scale as an argument, so this is independent of current
     * translate and scale. The result is fitted to bounds, putting the image point as near to the screen center as permitted.
     */
    private fun vTranslateForSCenter(
        sCenterX: Float,
        sCenterY: Float,
        scale: Float,
        sSize: Point
    ): PointF {
        val vxCenter = paddingLeft + getUsefulWidth() / 2
        val vyCenter = paddingTop + getUsefulHeight() / 2
        if (satTemp == null) satTemp = ScaleAndTranslate(0f, PointF(0f, 0f))
        satTemp!!.scale = scale
        satTemp!!.vTranslate[vxCenter - (sCenterX * scale)] = vyCenter - (sCenterY * scale)
        fitToBounds(true, sSize, satTemp!!)
        return satTemp!!.vTranslate
    }

    /**
     * Returns the minimum allowed scale.
     */
    private fun minScale(inDims: Point? = null): Float {
        val dims = inDims ?: Point(sWidth(), sHeight())
        var viewWidth = getWidthInternal() - paddingLeft + paddingRight
        if (0 == viewWidth) viewWidth = dims.x
        var viewHeight = getHeightInternal() - paddingBottom + paddingTop
        if (0 == viewHeight) viewHeight = dims.y

        when (minimumScaleType) {
            ScaleType.CENTER_CROP, ScaleType.START -> return max(
                viewWidth / dims.x.toFloat(),
                viewHeight / dims.y.toFloat()
            )

            ScaleType.FIT_WIDTH -> return viewWidth / dims.x.toFloat()
            ScaleType.FIT_HEIGHT -> return viewHeight / dims.y.toFloat()
            ScaleType.ORIGINAL_SIZE -> return 1f
            ScaleType.SMART_FIT -> return if (dims.y > dims.x) {
                // Fit to width
                viewWidth / dims.x.toFloat()
            } else {
                // Fit to height
                viewHeight / dims.y.toFloat()
            }

            ScaleType.SMART_FILL -> {
                val scale1 = viewHeight / dims.y.toFloat()
                val scale2 = viewWidth / dims.x.toFloat()
                return max(scale1, scale2)
            }

            ScaleType.CUSTOM -> return if (minScale > 0) minScale
            else min(
                viewWidth / dims.x.toFloat(),
                viewHeight / dims.y.toFloat()
            )

            ScaleType.CENTER_INSIDE -> return min(
                viewWidth / dims.x.toFloat(),
                viewHeight / dims.y.toFloat()
            )

            ScaleType.STRETCH_SCREEN -> return 1f
        }
    }

    /**
     * Adjust a requested scale to be within the allowed limits.
     */
    private fun limitedScale(targetScale: Float, sSize: Point? = null): Float {
        val min = minScale(sSize)
        // Sometimes minScale gets higher than maxScale => align both
        val max = if (min < maxScale) maxScale else min
        return if (max > 0) targetScale.coerceIn(min, max) else max(targetScale, min)
    }

    /**
     * Apply a selected type of easing.
     *
     * @param type     Easing type, from static fields
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun ease(
        type: Easing,
        time: Long,
        from: Float,
        change: Float,
        duration: Long
    ): Float {
        return when (type) {
            Easing.IN_OUT_QUAD -> easeInOutQuad(time, from, change, duration)
            Easing.OUT_QUAD -> easeOutQuad(time, from, change, duration)
        }
    }

    /**
     * Quadratic easing for fling. With thanks to Robert Penner - http://gizma.com/easing/
     *
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun easeOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        val progress = time.toFloat() / duration.toFloat()
        return -change * progress * (progress - 2) + from
    }

    /**
     * Quadratic easing for scale and center animations. With thanks to Robert Penner - http://gizma.com/easing/
     *
     * @param time     Elapsed time
     * @param from     Start value
     * @param change   Target value
     * @param duration Anm duration
     * @return Current value
     */
    private fun easeInOutQuad(time: Long, from: Float, change: Float, duration: Long): Float {
        var timeF = time / (duration / 2f)
        if (timeF < 1) {
            return (change / 2f * timeF * timeF) + from
        } else {
            timeF--
            return (-change / 2f) * (timeF * (timeF - 2) - 1) + from
        }
    }

    /**
     * Calculate how much further the image can be panned in each direction. The results are set on
     * the supplied [RectF] and expressed as screen pixels. For example, if the image cannot be
     * panned any further towards the left, the value of [RectF.left] will be set to 0.
     *
     * @param vTarget target object for results. Re-use for efficiency.
     */
    fun getPanRemaining(vTarget: RectF) {
        if (!isReady()) return

        val scaleWidth = scale * sWidth()
        val scaleHeight = scale * sHeight()

        if (panLimit == PanLimit.CENTER) {
            vTarget.top = max(0f, -(vTranslate!!.y - getHeightInternal() / 2f))
            vTarget.left = max(0f, -(vTranslate!!.x - getWidthInternal() / 2f))
            vTarget.bottom = max(0f, vTranslate!!.y - (getHeightInternal() / 2f - scaleHeight))
            vTarget.right = max(0f, vTranslate!!.x - (getWidthInternal() / 2f - scaleWidth))
        } else if (panLimit == PanLimit.OUTSIDE) {
            vTarget.top = max(0f, -(vTranslate!!.y - getHeightInternal()))
            vTarget.left = max(0f, -(vTranslate!!.x - getWidthInternal()))
            vTarget.bottom = max(0f, vTranslate!!.y + scaleHeight)
            vTarget.right = max(0f, vTranslate!!.x + scaleWidth)
        } else {
            vTarget.top = max(0f, -vTranslate!!.y)
            vTarget.left = max(0f, -vTranslate!!.x)
            vTarget.bottom = max(0f, scaleHeight + vTranslate!!.y - getHeightInternal())
            vTarget.right = max(0f, scaleWidth + vTranslate!!.x - getWidthInternal())
        }
    }

    /**
     * Set the pan limiting style. See static fields. Normally PanLimit.INSIDE is best, for image galleries.
     *
     * @param panLimit a pan limit constant. See static fields.
     */
    fun setPanLimit(panLimit: PanLimit) {
        this.panLimit = panLimit
        if (isReady()) {
            fitToBounds(true)
            invalidate()
        }
    }

    /**
     * Set the minimum scale type. See static fields. Normally ScaleType.CENTER_INSIDE is best, for image galleries.
     *
     * @param scaleType a scale type constant. See static fields.
     */
    fun setMinimumScaleType(scaleType: ScaleType) {
        this.minimumScaleType = scaleType
        if (isReady()) {
            fitToBounds(true)
            invalidate()
        }
    }

    /**
     * Set the maximum scale allowed. A value of 1 means 1:1 pixels at maximum scale. You may wish to set this according
     * to screen density - on a retina screen, 1:1 may still be too small. Consider using [.setMinimumDpi],
     * which is density aware.
     *
     * @param maxScale maximum scale expressed as a source/view pixels ratio.
     */
    fun setMaxScale(maxScale: Float) {
        this.maxScale = maxScale
    }

    /**
     * Set the minimum scale allowed. A value of 1 means 1:1 pixels at minimum scale. You may wish to set this according
     * to screen density. Consider using [.setMaximumDpi], which is density aware.
     *
     * @param minScale minimum scale expressed as a source/view pixels ratio.
     */
    fun setMinScale(minScale: Float) {
        this.minScale = minScale
    }

    /**
     * This is a screen density aware alternative to [.setMaxScale]; it allows you to express the maximum
     * allowed scale in terms of the minimum pixel density. This avoids the problem of 1:1 scale still being
     * too small on a high density screen. A sensible starting point is 160 - the default used by this view.
     *
     * @param dpi Source image pixel density at maximum zoom.
     */
    fun setMinimumDpi(dpi: Int) {
        setMaxScale(screenDpi / dpi)
    }

    /**
     * This is a screen density aware alternative to [.setMinScale]; it allows you to express the minimum
     * allowed scale in terms of the maximum pixel density.
     *
     * @param dpi Source image pixel density at minimum zoom.
     */
    fun setMaximumDpi(dpi: Int) {
        setMinScale(screenDpi / dpi)
    }

    /**
     * By default, image tiles are at least as high resolution as the screen. For a retina screen this may not be
     * necessary, and may increase the likelihood of an OutOfMemoryError. This method sets a DPI at which higher
     * resolution tiles should be loaded. Using a lower number will on average use less memory but result in a lower
     * quality image. 160-240dpi will usually be enough. This should be called before setting the image source,
     * because it affects which tiles get loaded. When using an untiled source image this method has no effect.
     *
     * @param minimumTileDpi Tile loading threshold.
     */
    fun setMinimumTileDpi(minimumTileDpi: Int) {
        this.minimumTileDpi = min(screenDpi.toInt(), minimumTileDpi)
        if (isReady()) {
            reset(false)
            invalidate()
        }
    }

    /**
     * Returns the source point at the center of the view.
     *
     * @return the source coordinates current at the center of the view.
     */
    fun getCenter(): PointF {
        val mX = getWidthInternal() / 2
        val mY = getHeightInternal() / 2
        return viewToSourceCoord(mX.toFloat(), mY.toFloat())
    }

    /**
     * Returns the screen coordinates of the center of the view
     *
     * @return screen coordinates of the center of the view
     */
    fun getvCenter(): PointF {
        val vxCenter = paddingLeft + getUsefulWidth() / 2
        val vyCenter = paddingTop + getUsefulHeight() / 2
        return PointF(vxCenter.toFloat(), vyCenter.toFloat())
    }

    /**
     * Returns the current absolute scale value.
     *
     * @return the current scale as a source/view pixels ratio.
     */
    fun getAbsoluteScale(): Float {
        return scale
    }

    /**
     * Externally change the absolute scale and translation of the source image. This may be used with getCenter() and getScale()
     * to restore the scale and zoom after a screen rotate.
     *
     * @param absoluteScale New scale to set.
     * @param sCenter       New source image coordinate to center on the screen, subject to boundaries.
     */
    fun setScaleAndCenter(absoluteScale: Float, sCenter: PointF?) {
        this.anim = null
        this.pendingScale = absoluteScale
        this.sPendingCenter = sCenter ?: getCenter()
        this.sRequestedCenter = sCenter ?: getCenter()
        invalidate()
    }

    /**
     * Fully zoom out and return the image to the middle of the screen. This might be useful if you have a view pager
     * and want images to be reset when the user has moved to another page.
     */
    fun resetScaleAndCenter() {
        this.anim = null
        this.pendingScale = limitedScale(0f)
        if (isReady() && isCentered(minimumScaleType)) {
            this.sPendingCenter = PointF(sWidth() / 2f, sHeight() / 2f)
        } else {
            this.sPendingCenter = PointF(0f, 0f)
        }
        invalidate()
    }

    private fun detectRotation(): Orientation {
        val dimX = if (singleImage.rawWidth > -1) singleImage.rawWidth else sWidth
        val dimY = if (singleImage.rawHeight > -1) singleImage.rawHeight else sHeight
        return if (needsRotating(dimX, dimY)) {
            when (autoRotate) {
                AutoRotateMethod.LEFT -> Orientation.O_90
                AutoRotateMethod.RIGHT -> Orientation.O_270
                else -> Orientation.O_0
            }
        } else Orientation.O_0
    }

    fun resetScale() {
        anim = null
        pendingScale = limitedScale(0f)
        invalidate()
    }

    fun getVirtualScale(): Float {
        return if (-1f == virtualScale) scale else virtualScale
    }

    fun setVirtualScale(targetScale: Float) {
        anim = null
        virtualScale = limitedScale(targetScale)
        // No change to actual parameters
        pendingScale = scale
        sPendingCenter = getCenter()
        invalidate()
    }

    /**
     * Call to find whether the view is initialised, has dimensions, and will display an image on
     * the next draw. If a preview has been provided, it may be the preview that will be displayed
     * and the full size image may still be loading. If no preview was provided, this is called once
     * the base layer tiles of the full size image are loaded.
     *
     * @return true if the view is ready to display an image and accept touch gestures.
     */
    fun isReady(): Boolean {
        return readySent
    }

    /**
     * Called once when the view is initialised, has dimensions, and will display an image on the
     * next draw. This is triggered at the same time as [OnImageEventListener.onReady] but
     * allows a subclass to receive this event without using a listener.
     */
    protected fun onReady() {
    }

    /**
     * Call to find whether the main image (base layer tiles where relevant) have been loaded. Before
     * this event the view is blank unless a preview was provided.
     *
     * @return true if the main image (not the preview) has been loaded and is ready to display.
     */
    fun isImageLoaded(): Boolean {
        return imageLoadedSent
    }

    /**
     * Called once when the full size image or its base layer tiles have been loaded.
     */
    protected fun onImageLoaded() {
    }

    /**
     * Get source width, ignoring orientation.
     * If [.getOrientation] returns 90 or 270, you can use [.getSHeight] for the apparent width.
     *
     * @return the source image width in pixels.
     */
    fun getSWidth(): Int {
        return sWidth
    }

    /**
     * Get source height, ignoring orientation.
     * If [.getOrientation] returns 90 or 270, you can use [.getSWidth] for the apparent height.
     *
     * @return the source image height in pixels.
     */
    fun getSHeight(): Int {
        return sHeight
    }

    /**
     * Enable or disable zoom gesture detection. Disabling zoom locks the the current scale.
     *
     * @param zoomEnabled true to enable zoom gestures, false to disable.
     */
    fun setZoomEnabled(zoomEnabled: Boolean) {
        this.zoomEnabled = zoomEnabled
    }

    /**
     * Enable or disable double tap &amp; swipe to zoom.
     *
     * @param quickScaleEnabled true to enable quick scale, false to disable.
     */
    fun setQuickScaleEnabled(quickScaleEnabled: Boolean) {
        this.quickScaleEnabled = quickScaleEnabled
    }

    /**
     * Enable or disable temp zoom on long tap
     *
     * @param value true to enable temp zoom on hold, false to disable.
     */
    fun setLongTapZoomEnabled(value: Boolean) {
        this.longTapZoomEnabled = value
    }

    fun setDoubleTapZoomEnabled(value: Boolean) {
        this.isDoubleTapZoomEnabled = value
    }

    /**
     * Enable or disable pan gesture detection. Disabling pan causes the image to be centered. Pan
     * can still be changed from code.
     *
     * @param panEnabled true to enable panning, false to disable.
     */
    fun setPanEnabled(panEnabled: Boolean) {
        this.panEnabled = panEnabled
        if (!panEnabled && vTranslate != null) {
            vTranslate!!.x = (getWidthInternal() / 2f) - (scale * (sWidth() / 2f))
            vTranslate!!.y = (getHeightInternal() / 2f) - (scale * (sHeight() / 2f))
            if (isReady()) {
                refreshRequiredResource(true)
                invalidate()
            }
        }
    }

    /**
     * Set the direction of the viewing (default : Horizontal)
     *
     * @param direction Direction to set
     */
    fun setDirection(direction: Direction) {
        this.swipeDirection = direction
    }

    /**
     * Indicate if the image offset should be its left side (default : true)
     *
     * @param offsetLeftSide True if the image offset is its left side; false for the right side
     */
    fun setOffsetLeftSide(offsetLeftSide: Boolean) {
        this.offsetLeftSide = offsetLeftSide
        this.sideOffsetConsumed = false
    }

    /**
     * Enable auto-rotate mode (default : false)
     *
     * Auto-rotate chooses automatically the most fitting orientation so that the image occupies
     * most of the screen, according to its dimensions and the device's screen dimensions and
     * the device's orientation (see needsRotating method)
     *
     * @param autoRotate True if auto-rotate mode should be on
     */
    fun setAutoRotate(autoRotate: AutoRotateMethod) {
        this.autoRotate = autoRotate
    }

    fun setGlEsRenderer(glEsRenderer: GPUImage?) {
        this.glEsRenderer = glEsRenderer
    }

    fun setSmartCrop(value: Boolean) {
        this.isSmartCrop = value
    }

    /**
     * Set a solid color to render behind tiles, useful for displaying transparent PNGs.
     *
     * @param tileBgColor Background color for tiles.
     */
    fun setTileBackgroundColor(tileBgColor: Int) {
        if (Color.alpha(tileBgColor) == 0) {
            tileBgPaint = null
        } else {
            tileBgPaint = Paint()
            tileBgPaint!!.style = Paint.Style.FILL
            tileBgPaint!!.color = tileBgColor
        }
        invalidate()
    }

    /**
     * Set the scale the image will zoom in to when double tapped. This also the scale point where a double tap is interpreted
     * as a zoom out gesture - if the scale is greater than 90% of this value, a double tap zooms out. Avoid using values
     * greater than the max zoom.
     *
     * @param doubleTapZoomScale New value for double tap gesture zoom scale.
     */
    fun setDoubleTapZoomScale(doubleTapZoomScale: Float) {
        this.doubleTapZoomScale = doubleTapZoomScale
    }

    /**
     * A density aware alternative to [.setDoubleTapZoomScale]; this allows you to express the scale the
     * image will zoom in to when double tapped in terms of the image pixel density. Values lower than the max scale will
     * be ignored. A sensible starting point is 160 - the default used by this view.
     *
     * @param dpi New value for double tap gesture zoom scale.
     */
    fun setDoubleTapZoomDpi(dpi: Int) {
        setDoubleTapZoomScale(screenDpi / dpi)
    }

    /**
     * Set the type of zoom animation to be used for double taps. See static fields.
     *
     * @param doubleTapZoomStyle New value for zoom style.
     */
    fun setDoubleTapZoomStyle(doubleTapZoomStyle: ZoomStyle) {
        this.doubleTapZoomStyle = doubleTapZoomStyle
    }

    /**
     * Set the duration of the double tap zoom animation.
     *
     * @param durationMs Duration in milliseconds.
     */
    fun setDoubleTapZoomDuration(durationMs: Int) {
        this.doubleTapZoomDuration = max(0, durationMs)
    }

    // TODO doc
    fun setDoubleTapZoomCap(cap: Float) {
        this.doubleTapZoomCap = cap
    }


    /**
     * Enable or disable eager loading of tiles that appear on screen during gestures or animations,
     * while the gesture or animation is still in progress. By default this is enabled to improve
     * responsiveness, but it can result in tiles being loaded and discarded more rapidly than
     * necessary and reduce the animation frame rate on old/cheap devices. Disable this on older
     * devices if you see poor performance. Tiles will then be loaded only when gestures and animations
     * are completed.
     *
     * @param eagerLoadingEnabled true to enable loading during gestures, false to delay loading until gestures end
     */
    fun setEagerLoadingEnabled(eagerLoadingEnabled: Boolean) {
        this.eagerLoadingEnabled = eagerLoadingEnabled
    }

    /**
     * {@inheritDoc}
     */
    override fun setOnLongClickListener(onLongClickListener: OnLongClickListener?) {
        this.onLongClickListener = onLongClickListener
    }

    /**
     * Add a listener allowing notification of load and error events. Extend [DefaultOnImageEventListener]
     * to simplify implementation.
     *
     * @param onImageEventListener an [OnImageEventListener] instance.
     */
    fun setOnImageEventListener(onImageEventListener: OnImageEventListener?) {
        this.onImageEventListener = onImageEventListener
    }

    fun setScaleListener(scaleListener: Consumer<Float>?) {
        this.scaleListener = scaleListener
    }

    private fun signalScaleChange(previousScale: Float, targetScale: Float) {
        if (abs(previousScale - targetScale) > 0.01) scaleDebouncer.submit(targetScale)
    }

    /**
     * Set temporary image dimensions to be used during pre-loading operations
     * (i.e. when actual source height and width are not known yet)
     */
    fun setPreloadDimensions(width: Int, height: Int) {
        preloadDimensions = Point(width, height)
    }

    /**
     * Get the width of the CustomSubsamplingScaleImageView
     * If not inflated/known yet, use the temporary image dimensions
     * WARNING : use this method instead of parent's getWidth
     *
     * @return Width of the view according to above specs
     */
    private fun getWidthInternal(): Int {
        return if (width > 0 || null == preloadDimensions) width
        else preloadDimensions!!.x
    }

    private fun getUsefulWidth(): Int {
        return getWidthInternal() - paddingLeft - paddingRight
    }

    /**
     * Get the height of the CustomSubsamplingScaleImageView
     * If not inflated/known yet, use the temporary image dimensions
     * WARNING : use this method instead of parent's getHeight
     *
     * @return Height of the view according to above specs
     */
    private fun getHeightInternal(): Int {
        return if (height > 0 || null == preloadDimensions) height
        else preloadDimensions!!.y
    }

    private fun getUsefulHeight(): Int {
        return getHeightInternal() - paddingTop - paddingBottom
    }

    private fun isCentered(st: ScaleType): Boolean {
        return st != ScaleType.START && st != ScaleType.FIT_HEIGHT && st != ScaleType.FIT_WIDTH && st != ScaleType.SMART_FIT && st != ScaleType.SMART_FILL
    }

    /**
     * Builder class used to set additional options for a scale animation. Create an instance using [.animateScale],
     * then set your options and call [.start].
     */
    inner class AnimationBuilder {
        private val targetScale: Float
        private val targetSCenter: PointF
        private val vFocus: PointF?

        private var duration: Long = 500

        private var easing = Easing.IN_OUT_QUAD

        private var origin = AnimOrigin.ANIM
        private var interruptible = true
        private var panLimited = true

        constructor(scale: Float, sCenter: PointF, vFocus: PointF? = null) {
            this.targetScale = scale
            this.targetSCenter = sCenter
            this.vFocus = vFocus
        }

        /**
         * Desired duration of the anim in milliseconds. Default is 500.
         *
         * @param duration duration in milliseconds.
         * @return this builder for method chaining.
         */
        fun withDuration(duration: Long): AnimationBuilder {
            this.duration = duration
            return this
        }

        /**
         * Whether the animation can be interrupted with a touch. Default is true.
         *
         * @param interruptible interruptible flag.
         * @return this builder for method chaining.
         */
        fun withInterruptible(interruptible: Boolean): AnimationBuilder {
            this.interruptible = interruptible
            return this
        }

        /**
         * Set the easing style. See static fields. Easing.IN_OUT_QUAD is recommended, and the default.
         *
         * @param easing easing style.
         * @return this builder for method chaining.
         */
        fun withEasing(easing: Easing): AnimationBuilder {
            this.easing = easing
            return this
        }

        /**
         * Only for internal use. When set to true, the animation proceeds towards the actual end point - the nearest
         * point to the center allowed by pan limits. When false, animation is in the direction of the requested end
         * point and is stopped when the limit for each axis is reached. The latter behaviour is used for flings but
         * nothing else.
         */
        fun withPanLimited(panLimited: Boolean): AnimationBuilder {
            this.panLimited = panLimited
            return this
        }

        /**
         * Only for internal use. Indicates what caused the animation.
         */
        fun withOrigin(origin: AnimOrigin): AnimationBuilder {
            this.origin = origin
            return this
        }

        /**
         * Starts the animation.
         */
        fun start() {
            val localTargetScale: Float = limitedScale(targetScale)
            val localTargetSCenter = if (panLimited) limitedSCenter(
                targetSCenter.x,
                targetSCenter.y,
                localTargetScale,
                PointF()
            ) else targetSCenter
            anim = Anim()
            anim?.let { anm ->
                anm.scaleStart = scale
                anm.scaleEnd = localTargetScale
                anm.time = System.currentTimeMillis()
                anm.sCenterEndRequested = localTargetSCenter
                anm.sCenterStart = getCenter()
                anm.sCenterEnd = localTargetSCenter
                anm.vFocusStart = sourceToViewCoord(localTargetSCenter)
                anm.vFocusEnd = getvCenter()
                anm.duration = duration
                anm.interruptible = interruptible
                anm.easing = easing
                anm.origin = origin
                anm.time = System.currentTimeMillis()

                if (vFocus != null) {
                    // Calculate where translation will be at the end of the anim
                    anm.sCenterStart?.let {
                        val vTranslateXEnd = vFocus.x - (localTargetScale * it.x)
                        val vTranslateYEnd = vFocus.y - (localTargetScale * it.y)
                        val satEnd =
                            ScaleAndTranslate(
                                localTargetScale,
                                PointF(vTranslateXEnd, vTranslateYEnd)
                            )
                        // Fit the end translation into bounds
                        fitToBounds(true, Point(sWidth(), sHeight()), satEnd)
                        // Adjust the position of the focus point at end so image will be in bounds
                        anm.vFocusEnd = PointF(
                            vFocus.x + (satEnd.vTranslate.x - vTranslateXEnd),
                            vFocus.y + (satEnd.vTranslate.y - vTranslateYEnd)
                        )
                    }
                }
            }

            invalidate()
        }

        /**
         * Given a requested source center and scale, calculate what the actual center will have to be to keep the image in
         * pan limits, keeping the requested center as near to the middle of the screen as allowed.
         */
        private fun limitedSCenter(
            sCenterX: Float,
            sCenterY: Float,
            scale: Float,
            sTarget: PointF
        ): PointF {
            val targetvTranslate: PointF =
                vTranslateForSCenter(sCenterX, sCenterY, scale, Point(sWidth(), sHeight()))
            val vxCenter = paddingLeft + getUsefulWidth() / 2
            val vyCenter = paddingTop + getUsefulHeight() / 2
            val sx = (vxCenter - targetvTranslate.x) / scale
            val sy = (vyCenter - targetvTranslate.y) / scale
            sTarget[sx] = sy
            return sTarget
        }
    }

    /**
     * An event listener, allowing subclasses and activities to be notified of significant events.
     */
    interface OnImageEventListener {
        /**
         * Called when the dimensions of the image and view are known, and either a preview image,
         * the full size image, or base layer tiles are loaded. This indicates the scale and translate
         * are known and the next draw will display an image. This event can be used to hide a loading
         * graphic, or inform a subclass that it is safe to draw overlays.
         */
        fun onReady()

        /**
         * Called when the full size image is ready. When using tiling, this means the lowest resolution
         * base layer of tiles are loaded, and when tiling is disabled, the image bitmap is loaded.
         * This event could be used as a trigger to enable gestures if you wanted interaction disabled
         * while only a preview is displayed, otherwise for most cases [.onReady] is the best
         * event to listen to.
         */
        fun onImageLoaded()

        /**
         * Called when a preview image could not be loaded. This method cannot be relied upon; certain
         * encoding types of supported image formats can result in corrupt or blank images being loaded
         * and displayed with no detectable error. The view will continue to load the full size image.
         *
         * @param e The exception thrown. This error is logged by the view.
         */
        fun onPreviewLoadError(e: Throwable)

        /**
         * Indicates an error initiliasing the decoder when using a tiling, or when loading the full
         * size bitmap when tiling is disabled. This method cannot be relied upon; certain encoding
         * types of supported image formats can result in corrupt or blank images being loaded and
         * displayed with no detectable error.
         *
         * @param e The exception thrown. This error is also logged by the view.
         */
        fun onImageLoadError(e: Throwable)

        /**
         * Called when an image tile could not be loaded. This method cannot be relied upon; certain
         * encoding types of supported image formats can result in corrupt or blank images being loaded
         * and displayed with no detectable error. Most cases where an unsupported file is used will
         * result in an error caught by [.onImageLoadError].
         *
         * @param e The exception thrown. This error is logged by the view.
         */
        fun onTileLoadError(e: Throwable)

        /**
         * Called when a bitmap set using imageSource.cachedBitmap is no longer being used by the View.
         * This is useful if you wish to manage the bitmap after the preview is shown
         */
        fun onPreviewReleased()
    }

    /**
     * Default implementation of [OnImageEventListener] for extension. This does nothing in any method.
     */
    class DefaultOnImageEventListener : OnImageEventListener {
        override fun onReady() {
        }

        override fun onImageLoaded() {
        }

        override fun onPreviewLoadError(e: Throwable) {
        }

        override fun onImageLoadError(e: Throwable) {
        }

        override fun onTileLoadError(e: Throwable) {
        }

        override fun onPreviewReleased() {
        }
    }

    class ProcessBitmapResult internal constructor(
        val bitmap: Bitmap,
        val orientation: Orientation,
        val scale: Float
    )
}