package me.devsaki.hentoid.util.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.io.source.ByteArrayOutputStream
import com.itextpdf.kernel.colors.Color
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.exceptions.PdfException
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.ImageRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.properties.HorizontalAlignment
import me.devsaki.hentoid.core.READER_CACHE
import me.devsaki.hentoid.core.THUMB_FILE_NAME
import me.devsaki.hentoid.enums.PictureEncoder
import me.devsaki.hentoid.util.copy
import me.devsaki.hentoid.util.hash64
import me.devsaki.hentoid.util.image.TransformParams
import me.devsaki.hentoid.util.image.getImageDimensions
import me.devsaki.hentoid.util.image.getMimeTypeFromPictureBinary
import me.devsaki.hentoid.util.image.isSupportedImage
import me.devsaki.hentoid.util.image.loadBitmap
import me.devsaki.hentoid.util.image.screenWidth
import me.devsaki.hentoid.util.image.transform
import me.devsaki.hentoid.util.median
import me.devsaki.hentoid.util.network.UriParts
import me.devsaki.hentoid.util.pause
import timber.log.Timber
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

// Inspired by https://github.com/T8RIN/ImageToolbox/blob/master/feature/pdf-tools/src/main/java/ru/tech/imageresizershrinker/feature/pdf_tools/data/AndroidPdfManager.kt

class PdfManager {

    private val extractedFiles = ArrayList<Uri>()
    private val currentPageIndex = AtomicInteger(0)

    private fun processFile(context: Context, doc: DocumentFile, keepFormat: Boolean): ByteArray? {
        // TODO don't keep format when non-PNG/JPG/WEBP
        val stream = ByteArrayOutputStream()
        if (keepFormat) {
            getInputStream(context, doc).use { copy(it, stream) }
            return stream.toByteArray()
        } else {
            loadBitmap(context, doc)?.let { bmp ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
                return stream.toByteArray()
            }
        }
        return null
    }

    // Blocking call
    suspend fun convertImagesToPdf(
        context: Context,
        out: OutputStream,
        imageFiles: List<DocumentFile>,
        keepImgFormat: Boolean,
        background: android.graphics.Color? = null,
        onProgressChange: ((Float) -> Unit)? = null
    ) {
        val allDims = ArrayList<Point>()
        val imgFiles = imageFiles
            .filter { isSupportedImage(it.name ?: "") }
            .filterNot {
                getFileNameWithoutExtension(it.name ?: "")
                    .equals(THUMB_FILE_NAME, true)
            }
        imgFiles.forEach {
            allDims.add(getImageDimensions(context, it.uri.toString()))
        }
        val medianWidth = median(allDims.map { it.x }.toIntArray())
        val minWidth = screenWidth * 0.75
        val targetWidth = if (medianWidth < minWidth) minWidth else medianWidth
        Timber.v("Target width : $targetWidth")

        PdfDocument(PdfWriter(out)).use { pdfDoc ->
            Document(pdfDoc, PageSize.A4).use { doc ->
                doc.setMargins(0f, 0f, 0f, 0f)
                doc.setHorizontalAlignment(HorizontalAlignment.CENTER)

                // Background color
                background?.let {
                    val bgColor = Color.createColorWithColorSpace(
                        arrayOf(it.red(), it.green(), it.blue()).toFloatArray()
                    )
                    doc.setBackgroundColor(bgColor)

                    val bgEventHandler = PageBackgroundEventHandler()
                    pdfDoc.addEventHandler(PdfDocumentEvent.START_PAGE, bgEventHandler)
                    bgEventHandler.setBackground(bgColor)
                } ?: run { // NO background
                    val bgColor =
                        Color.createColorWithColorSpace(arrayOf(0f, 0f, 0f).toFloatArray())
                    doc.setBackgroundColor(bgColor, 0f)
                }

                imgFiles.forEachIndexed { index, file ->
                    // Convert to PNG or JPEG; use proper ratio to reach target width
                    val ratio = targetWidth / (allDims[index].x * 1.0)
                    val data = processFile(context, file, keepImgFormat) ?: return@forEachIndexed
                    val params = TransformParams(
                        true, 2, 0f, 0, 0, ratio.toFloat(), 0, 1, PictureEncoder.PNG,
                        PictureEncoder.JPEG, PictureEncoder.PNG, 90, allowUpscale = true
                    )
                    val dataOut = transform(context, data, params)
                    val image = Image(ImageDataFactory.create(dataOut))

                    Timber.d("Adding new page @ $index : ${image.imageWidth}x${image.imageHeight} (ratio : $ratio)")
                    pdfDoc.addNewPage(PageSize(image.imageWidth, image.imageHeight))
                    image.setFixedPosition(index + 1, 0f, 0f)

                    doc.add(image)

                    onProgressChange?.invoke((index + 1) * 1f / imgFiles.size)
                }
                doc.flush()
            }
        }
    }

    private fun saveExtractedImage(
        context: Context,
        id: Long,
        fileName: String,
        data: ByteArray,
        fileFinder: (String) -> Uri?,
        fileCreator: (String) -> Uri?,
        onExtracted: ((Long, Uri) -> Unit)? = null
    ) {
        val existing = fileFinder.invoke(fileName)
        Timber.v("Extract PDF, get stream: id $fileName")
        try {
            val target =
                existing ?: fileCreator.invoke(fileName)
                ?: throw IOException("Can't create file $fileName")
            getOutputStream(context, target)?.use { saveBinary(it, data) }
            onExtracted?.invoke(id, target)
        } catch (e: IOException) {
            throw PdfException(e)
        }
    }

    fun extractImagesCached(
        context: Context,
        pdf: DocumentFile,
        entriesToExtract: List<Triple<String, Long, String>>?,
        interrupt: (() -> Boolean)? = null,
        onExtracted: ((Long, Uri) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        extractImages(
            context,
            pdf,
            entriesToExtract, interrupt,
            { name, data, id ->
                saveExtractedImage(
                    context,
                    id,
                    name,
                    data,
                    StorageCache.createFinder(READER_CACHE),
                    StorageCache.createCreator(READER_CACHE),
                    onExtracted
                )
            },
            onComplete
        )
    }

    @Throws(IOException::class)
    fun extractImagesBlocking(
        context: Context,
        pdf: DocumentFile,
        targetFolder: Uri,
        entriesToExtract: List<Triple<String, Long, String>>,
        onProgress: (() -> Unit)? = null,
        interrupt: (() -> Boolean)? = null
    ): List<Uri> {
        val fileCreator: (String) -> Uri? =
            { targetFileName ->
                createFile(
                    context, targetFolder, targetFileName,
                    getMimeTypeFromFileName(targetFileName), false
                )
            }
        // List once, search the map during extraction
        val targetFolderList = listFiles(context, targetFolder)
            .groupBy { UriParts(it.toString()).fileNameFull }
        val fileFinder: (String) -> Uri? = { targetFolderList[it]?.firstOrNull() }

        extractedFiles.clear()
        extractImages(
            context,
            pdf,
            entriesToExtract, interrupt,
            { name, data, id ->
                saveExtractedImage(
                    context,
                    id,
                    name,
                    data,
                    fileFinder,
                    fileCreator
                ) { _, uri ->
                    onProgress?.invoke()
                    extractedFiles.add(uri)
                }
            }
        )
        // Block calling thread until all entries are processed
        val delay = 250
        var nbPauses = 0
        var lastSize = 0
        while (extractedFiles.size < entriesToExtract.size) {
            extractedFiles.apply {
                if (lastSize == size) {
                    // 3 seconds timeout when no progression
                    if (nbPauses++ > 3.0 * 1000.0 / delay) throw IOException("Extraction timed out")
                } else {
                    nbPauses = 0
                }
                lastSize = size
            }
            pause(delay)
        }
        return extractedFiles
    }

    /**
     * Extract images
     *
     * @param context              Context to use
     * @param pdf                  PDF file to extract from
     * @param entriesToExtract     Entries to extract; null to extract everything
     *      first : Path of the resource (format is "pageNumber.indexInPage.extension")
     *      second : Resource identifier set by the caller (for remapping purposes)
     *      third : Target file name
     * @param interrupt            Kill switch
     * @param onExtract            Extraction callback
     *      Long : Resource identifier set by the caller; internal hash if none
     *      String : Mime-type of the extracted picture
     *      ByteArray : Data of the extracted picture
     * @param onComplete           Completion callback
     */
    private fun extractImages(
        context: Context,
        pdf: DocumentFile,
        entriesToExtract: List<Triple<String, Long, String>>?,
        interrupt: (() -> Boolean)? = null,
        onExtract: (String, ByteArray, Long) -> Unit,
        onComplete: (() -> Unit)? = null
    ) {
        getInputStream(context, pdf).use { input ->
            PdfDocument(PdfReader(input)).use { pdfDoc ->
                val listener: IEventListener = ImageRenderListener(entriesToExtract, onExtract)
                val parser = PdfCanvasProcessor(listener)
                if (entriesToExtract.isNullOrEmpty()) { // Extract everything
                    for (i in 1..pdfDoc.numberOfPages) {
                        currentPageIndex.set(i)
                        parser.processPageContent(pdfDoc.getPage(i))
                        if (interrupt?.invoke() == true) return
                    }
                } else { // Targeted extraction
                    val targetEntries = entriesToExtract
                        .map {
                            val s = it.first.split(".")
                            if (s.size < 2) Triple(s[0].toInt(), 0, it.second)
                            Triple(s[0].toInt(), s[1].toInt(), it.second)
                        }
                        .filterNot { it.first < 0 || it.first > pdfDoc.numberOfPages }
                    targetEntries.forEach {
                        currentPageIndex.set(it.first)
                        // TODO selectively extract image X within selected page
                        parser.processPageContent(pdfDoc.getPage(it.first))
                        if (interrupt?.invoke() == true) return@forEach
                    }
                }
            }
        }
        onComplete?.invoke()
    }

    fun getEntries(
        context: Context,
        uri: Uri,
        stopFirst: Boolean = false
    ): List<ArchiveEntry> {
        val result = ArrayList<ArchiveEntry>()
        getInputStream(context, uri).use { input ->
            PdfDocument(PdfReader(input)).use { pdfDoc ->
                val listener: IEventListener = ImageRenderListener(null)
                { name, data, _ -> result.add(ArchiveEntry(false, name, data.size.toLong())) }
                val parser = PdfCanvasProcessor(listener)
                for (i in 1..pdfDoc.numberOfPages) {
                    currentPageIndex.set(i)
                    parser.processPageContent(pdfDoc.getPage(i))
                    if (stopFirst && result.isNotEmpty()) return result
                }
            }
        }
        return result
    }

    private class PageBackgroundEventHandler : IEventHandler {
        private var color: Color? = null

        fun setBackground(color: Color) {
            this.color = color
        }

        override fun handleEvent(currentEvent: Event) {
            if (null == color) return

            val docEvent = currentEvent as PdfDocumentEvent
            val page = docEvent.page

            val canvas = PdfCanvas(page)
            val rect: Rectangle = page.pageSize
            canvas
                .saveState()
                .setFillColor(color)
                .rectangle(rect)
                .fillStroke()
                .restoreState()
        }
    }

    /**
     * Class that implements extraction actions "driven" by the PDF library
     */
    inner class ImageRenderListener(
        private val entriesToExtract: List<Triple<String, Long, String>>?,
        private val onExtract: (String, ByteArray, Long) -> Unit
    ) : IEventListener {
        private var page = -1
        private var indexInPage = 0
        override fun eventOccurred(evt: IEventData, type: EventType?) {
            when (type) {
                EventType.RENDER_IMAGE -> try {
                    val renderInfo = evt as ImageRenderInfo
                    val image = renderInfo.image ?: return
                    val curPage = currentPageIndex.get()
                    if (curPage != page) {
                        indexInPage = 0
                        page = curPage
                    }
                    val data = image.getImageBytes(false)
                    val mimeType = getMimeTypeFromPictureBinary(data)
                    val ext = getExtensionFromMimeType(mimeType)
                    val internalUniqueName = "$curPage.$indexInPage.$ext"
                    val identifier = entriesToExtract?.let { entry ->
                        entry.firstOrNull { it.first == internalUniqueName }?.second
                    } ?: hash64(internalUniqueName)
                    onExtract.invoke(internalUniqueName, data, identifier)
                } catch (e: com.itextpdf.io.exceptions.IOException) {
                    Timber.w(e)
                } catch (e: IOException) {
                    Timber.w(e)
                }

                else -> {}
            }
        }

        override fun getSupportedEvents(): Set<EventType> {
            return setOf(EventType.RENDER_IMAGE)
        }
    }
}