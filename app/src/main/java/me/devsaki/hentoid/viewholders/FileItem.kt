package me.devsaki.hentoid.viewholders

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import coil3.dispose
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.drag.IExtendedDraggable
import com.mikepenz.fastadapter.items.AbstractItem
import com.mikepenz.fastadapter.listeners.TouchEventHook
import com.mikepenz.fastadapter.swipe.IDrawerSwipeableViewHolder
import com.mikepenz.fastadapter.swipe.ISwipeable
import me.devsaki.hentoid.BuildConfig
import me.devsaki.hentoid.R
import me.devsaki.hentoid.activities.bundles.FileItemBundle
import me.devsaki.hentoid.core.Consumer
import me.devsaki.hentoid.core.requireById
import me.devsaki.hentoid.ui.BlinkAnimation
import me.devsaki.hentoid.util.file.DisplayFile
import me.devsaki.hentoid.util.file.DisplayFile.SubType
import me.devsaki.hentoid.util.file.DisplayFile.Type
import me.devsaki.hentoid.util.image.loadStill
import timber.log.Timber

class FileItem : AbstractItem<FileItem.ViewHolder>,
    IExtendedDraggable<FileItem.ViewHolder>, ISwipeable {

    val doc: DisplayFile
    var refreshComplete: Boolean = false

    private val showDragHandle: Boolean
    private var deleteAction: Consumer<FileItem>? = null

    // Drag, drop & swipe
    override val touchHelper: ItemTouchHelper?
    override val isSwipeable: Boolean

    // Constructor for split
    constructor(d: DisplayFile, refreshed: Boolean = false) {
        doc = d
        refreshComplete = refreshed
        touchHelper = null
        showDragHandle = false
        isSwipeable = false
        isSelectable = (d.type != Type.ADD_BUTTON && d.type != Type.UP_BUTTON)
        identifier = doc.id
    }

    override fun getViewHolder(v: View): ViewHolder {
        return ViewHolder(v)
    }

    override val layoutRes: Int
        get() = R.layout.item_library_folder

    override val type: Int
        get() = R.id.folder

    override val isDraggable: Boolean
        get() = false

    override fun isDirectionSupported(direction: Int): Boolean {
        return ItemTouchHelper.LEFT == direction
    }

    override fun getDragView(viewHolder: ViewHolder): View? {
        return null
    }


    class ViewHolder internal constructor(view: View) :
        FastAdapter.ViewHolder<FileItem>(view), IDrawerSwipeableViewHolder, ISwipeableViewHolder {
        // Common elements
        private val baseLayout: View = view.requireById(R.id.item)
        private val tvTitle: TextView = view.requireById(R.id.tvTitle)
        private val ivCover: ImageView = view.requireById(R.id.ivCover)
        private val tvPages: TextView? = view.findViewById(R.id.tvPages)
        override val swipeableView: View = view.findViewById(R.id.item_card) ?: ivCover
        private var selectionBorder: View? = view.findViewById(R.id.selection_border)

        private var deleteActionRunnable: Runnable? = null

        // Extra info to display in stacktraces
        private var debugStr = "[no data]"


        override fun bindView(item: FileItem, payloads: List<Any>) {
            // Payloads are set when the content stays the same but some properties alone change
            if (payloads.isNotEmpty()) {
                val bundle = payloads[0] as Bundle
                val bundleParser = FileItemBundle(bundle)

                var strValue = bundleParser.coverUri
                if (!strValue.isNullOrBlank()) item.doc.coverUri = strValue.toUri()
                bundleParser.contentId?.let { item.doc.contentId = it }
                bundleParser.processed?.let { item.doc.isBeingProcessed = it }
                bundleParser.type?.let { item.doc.type = Type.entries[it] }
                bundleParser.subType?.let { item.doc.subType = SubType.entries[it] }
                bundleParser.refreshed?.let { item.refreshComplete = it }
            }

            item.deleteAction?.apply {
                deleteActionRunnable = Runnable { invoke(item) }
            }

            // Important to trigger the ViewHolder's global onClick/onLongClick events
            swipeableView.setOnClickListener { v: View -> if (v.parent is View) (v.parent as View).performClick() }
            swipeableView.setOnLongClickListener { v: View ->
                if (v.parent is View) return@setOnLongClickListener (v.parent as View).performLongClick()
                false
            }

            updateLayoutVisibility(item, item.doc)
            attachCover(item.doc)
            attachTitle(item.doc)
            attachMetrics(item.doc)
            attachButtons(item)
        }

        private fun updateLayoutVisibility(item: FileItem, doc: DisplayFile) {
            baseLayout.isVisible = true
            selectionBorder?.isVisible = item.isSelected

            if (doc.isBeingProcessed)
                baseLayout.startAnimation(BlinkAnimation(500, 250))
            else baseLayout.clearAnimation()
        }

        private fun attachCover(doc: DisplayFile) {
            val coverUri = doc.coverUri?.toString() ?: ""
            if (!coverUri.isBlank()) {
                ivCover.scaleType = ImageView.ScaleType.FIT_CENTER
                ivCover.loadStill(coverUri)
            } else {
                val icon = when (doc.type) {
                    Type.ROOT_FOLDER, Type.FOLDER -> R.drawable.ic_folder
                    Type.SUPPORTED_FILE -> {
                        if (doc.subType == SubType.PDF) R.drawable.ic_pdf_file
                        else if (doc.subType == SubType.ARCHIVE) R.drawable.ic_archive
                        else R.drawable.ic_cherry_icon
                    }

                    Type.ADD_BUTTON -> R.drawable.ic_add
                    Type.UP_BUTTON -> R.drawable.ic_keyboard_arrow_up
                    else -> R.drawable.ic_cherry_icon
                }
                ivCover.scaleType = ImageView.ScaleType.CENTER_INSIDE
                ivCover.setImageResource(icon)
            }
        }

        private fun attachTitle(doc: DisplayFile) {
            tvTitle.text = doc.name
        }

        private fun attachMetrics(doc: DisplayFile) {
            tvPages?.text = if (doc.nbChildren > 0) "${doc.nbChildren} images" else ""
        }

        private fun attachButtons(item: FileItem) {
            // TODO
        }

        override fun unbindView(item: FileItem) {
            deleteActionRunnable = null
            debugStr = "[no data]"
            swipeableView.translationX = 0f
            ivCover.dispose()
        }

        override fun onSwiped() {
            // Nothing
        }

        override fun onUnswiped() {
            // Nothing
        }

        override fun toString(): String {
            return super.toString() + " " + debugStr
        }
    }

    class DragHandlerTouchEvent(private val action: Consumer<Int>) : TouchEventHook<FileItem>() {
        override fun onTouch(
            v: View,
            event: MotionEvent,
            position: Int,
            fastAdapter: FastAdapter<FileItem>,
            item: FileItem
        ): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN) {
                action.invoke(position)
                return true
            }
            return false
        }
    }
}
