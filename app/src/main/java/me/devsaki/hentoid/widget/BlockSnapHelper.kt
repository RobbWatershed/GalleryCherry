package me.devsaki.hentoid.widget

import android.util.DisplayMetrics
import android.view.View
import android.view.animation.Interpolator
import android.widget.Scroller
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

private const val MILLISECONDS_PER_INCH = 100f

/**
 * Inspired by https://gist.github.com/Moes81/0cfbb1f2d8492025a7ddaa9549f870e7 + LinearSnapHelper.findCenterView
 *
 * A custom [SnapHelper] implementation for the [RecyclerView] that snaps to a whole page of items, instead of the
 * single recycler view items as with the `LinearSnapHelper`.
 *
 * The number of items in the RecyclerView should be a multiple of block size; otherwise, the extra item views will not
 * be positioned on a block boundary when the end of the data is reached. Pad out with empty item views if needed.
 *
 * Taken from: [https://stackoverflow.com/questions/47514072/how-to-snap-recyclerview-items-so-that-every-x-items-would-be-considered-like-a#]
 * Kudos to: Cheticamp [https://github.com/Cheticamp] for the idea
 * and AndroidDeveloperLB [https://github.com/AndroidDeveloperLB] for the Kotlin port.
 * Also check out the sample app: [https://github.com/Cheticamp/SnapToBlockDemo]
 *
 * Usage:
 * ```
 * val snapToBlock: SnapToBlock = SnapToBlock(mMaxFlingPages)
 * snapToBlock.attachToRecyclerView(recyclerView)
 * ```
 *
 * @param maxFlingBlocks Max blocks to move during most vigorous fling
 **/
class BlockSnapHelper(var maxFlingBlocks: Int) : SnapHelper() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var displayMetrics: DisplayMetrics

    // Total number of items in a block of view in the RecyclerView
    private var blocksize: Int = 0

    private var flingSensitivity = 0f

    // Width of a RecyclerView item if orientation is horizonal; height of the item if vertical
    private var itemDimension: Int = 0

    /**
     * Callback interface when blocks are snapped.
     */
    private var snapBlockCallback: SnapBlockCallback? = null

    /**
     * Our private scroller
     */
    private lateinit var scroller: Scroller

    /**
     * Horizontal/vertical layout helper
     */
    private lateinit var orientationHelper: OrientationHelper

    /**
     * LTR/RTL helper
     */
    private val layoutDirectionHelper: LayoutDirectionHelper by lazy { LayoutDirectionHelper() }


    @Throws(IllegalStateException::class)
    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        if (recyclerView != null) {
            this.recyclerView = recyclerView
            val layoutManager = this.recyclerView.layoutManager as LinearLayoutManager
            orientationHelper = when {
                layoutManager.canScrollHorizontally() ->
                    OrientationHelper.createHorizontalHelper(layoutManager)

                layoutManager.canScrollVertically() ->
                    OrientationHelper.createVerticalHelper(layoutManager)

                else -> throw IllegalStateException("RecyclerView must be scrollable")
            }
            scroller = Scroller(this.recyclerView.context, sInterpolator)
            refreshItemDimension(layoutManager)
            displayMetrics = recyclerView.context.resources.displayMetrics
        }
        super.attachToRecyclerView(recyclerView)
    }

    // Called when the target view is available and we need to know how much more
    // to scroll to get it lined up with the side of the RecyclerView.
    override fun calculateDistanceToFinalSnap(
        layoutManager: RecyclerView.LayoutManager,
        targetView: View
    ): IntArray {
        val out = IntArray(2)
        if (layoutManager.canScrollHorizontally())
            out[0] = layoutDirectionHelper.getScrollToAlignView(targetView)
        if (layoutManager.canScrollVertically())
            out[1] = layoutDirectionHelper.getScrollToAlignView(targetView)

        if (out[0] == 0 && out[1] == 0)
            snapBlockCallback?.onBlockSnapped(layoutManager.getPosition(targetView))
        else
            snapBlockCallback?.onBlockSnap(layoutManager.getPosition(targetView))
        return out
    }

    // We are flinging and need to know where we are heading.
    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager,
        velocityX: Int,
        velocityY: Int
    ): Int {
        val lm = layoutManager as LinearLayoutManager
        refreshItemDimension(layoutManager)
        scroller.fling(
            0,
            0,
            velocityX,
            velocityY,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE,
            Integer.MIN_VALUE,
            Integer.MAX_VALUE
        )
        return when {
            velocityX != 0 -> layoutDirectionHelper.getPositionsToMove(
                lm,
                scroller.finalX,
                itemDimension
            )

            velocityY != 0 -> layoutDirectionHelper.getPositionsToMove(
                lm,
                scroller.finalY,
                itemDimension
            )

            else -> RecyclerView.NO_POSITION
        }
    }

    // We have scrolled to the neighborhood where we will snap. Determine the snap position.
    override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
        // Snap to a view that is either
        // 1) toward the bottom of the data and therefore on screen, or
        // 2) toward the top of the data and may be off-screen.
        val snapPos = calcTargetPosition(layoutManager as LinearLayoutManager)
        val snapView = if (snapPos == RecyclerView.NO_POSITION) null
        else layoutManager.findViewByPosition(snapPos)
        snapView?.tag = snapPos
        return snapView
    }

    override fun createScroller(layoutManager: RecyclerView.LayoutManager): LinearSmoothScroller? {
        return if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) null
        else object : LinearSmoothScroller(recyclerView.context) {

            override fun onTargetFound(
                targetView: View,
                state: RecyclerView.State,
                action: Action
            ) {
                val snapDistances =
                    calculateDistanceToFinalSnap(recyclerView.layoutManager!!, targetView)
                val dx = snapDistances[0]
                val dy = snapDistances[1]
                val time = calculateTimeForDeceleration(max(abs(dx), abs(dy)))
                if (time > 0) action.update(dx, dy, time, sInterpolator)
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                val factor =
                    if (layoutManager.canScrollHorizontally() && recyclerView.width > recyclerView.height) 1.75f else 1f
                return MILLISECONDS_PER_INCH / factor / displayMetrics.densityDpi
            }

            override fun calculateTimeForScrolling(dx: Int): Int {
                return ceil(abs(dx) * calculateSpeedPerPixel(displayMetrics)).toInt()
            }
        }
    }

    /**
     * Set a [SnapBlockCallback] to get informed, when the recyclerView snaps to a block.
     */
    fun setSnapBlockCallback(callback: SnapBlockCallback) {
        snapBlockCallback = callback
    }

    // Does the heavy lifting for findSnapView
    private fun calcTargetPosition(layoutManager: LinearLayoutManager): Int {
        val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePos == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION

        val center: Int = orientationHelper.startAfterPadding + orientationHelper.totalSpace / 2
        var absClosest = Int.Companion.MAX_VALUE

        var closestChildPos = RecyclerView.NO_POSITION
        for (i in firstVisiblePos..<layoutManager.childCount) {
            val child = layoutManager.getChildAt(i)
            val childCenter: Int = (orientationHelper.getDecoratedStart(child)
                    + (orientationHelper.getDecoratedMeasurement(child) / 2))
            val absDistance = abs(childCenter - center)

            // if child center is closer than previous closest, set it as closest
            if (absDistance < absClosest) {
                absClosest = absDistance
                closestChildPos = i
            }
        }
        return closestChildPos
    }

    private fun refreshItemDimension(layoutManager: RecyclerView.LayoutManager) {
        val child = layoutManager.getChildAt(0) ?: return
        if (layoutManager.canScrollHorizontally()) {
            itemDimension = child.width
            blocksize = getSpanCount(layoutManager) * (recyclerView.width / itemDimension)
        } else if (layoutManager.canScrollVertically()) {
            itemDimension = child.height
            blocksize = getSpanCount(layoutManager) * (recyclerView.height / itemDimension)
        }
    }

    private fun getMaxPositionsToMove(): Int {
        return blocksize * maxFlingBlocks
    }

    private fun getSpanCount(layoutManager: RecyclerView.LayoutManager): Int =
        (layoutManager as? GridLayoutManager)?.spanCount ?: 1

    private fun roundDownToBlockSize(trialPosition: Int): Int =
        trialPosition - trialPosition % blocksize

    private fun roundUpToBlockSize(trialPosition: Int): Int =
        roundDownToBlockSize(trialPosition + blocksize - 1)

    /**
     * Helper class that handles calculations for LTR and RTL layouts.
     */
    private inner class LayoutDirectionHelper {

        private val isRTL: Boolean
            get() {
                // Adapted to the specific case of Hentoid
                recyclerView.layoutManager?.let {
                    if (it.canScrollVertically()) return false
                    return if (it is LinearLayoutManager) it.reverseLayout
                    else false
                }
                return false
            }

        /**
         * Calculate the amount of scroll needed to align the target view with the layout edge.
         */
        fun getScrollToAlignView(targetView: View): Int = if (isRTL)
            orientationHelper.getDecoratedEnd(targetView) - recyclerView.width
        else
            orientationHelper.getDecoratedStart(targetView)

        /**
         * Calculate the distance to final snap position when the view corresponding to the snap
         * position is not currently available.
         *
         * @param layoutManager LinearLayoutManager or descendent class
         * @param targetPos     - Adapter position to snap to
         * @return int[2] {x-distance in pixels, y-distance in pixels}
         */
        fun calculateDistanceToScroll(
            layoutManager: LinearLayoutManager,
            targetPos: Int
        ): IntArray {
            val out = IntArray(2)
            val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
            if (layoutManager.canScrollHorizontally()) {
                if (targetPos <= firstVisiblePos)  // scrolling toward top of data
                    out[0] = if (isRTL) {
                        val lastView =
                            layoutManager.findViewByPosition(layoutManager.findLastVisibleItemPosition())
                        orientationHelper.getDecoratedEnd(lastView) + (firstVisiblePos - targetPos) * itemDimension
                    } else {
                        val firstView = layoutManager.findViewByPosition(firstVisiblePos)
                        orientationHelper.getDecoratedStart(firstView) - (firstVisiblePos - targetPos) * itemDimension
                    }
            }
            if (layoutManager.canScrollVertically() && targetPos <= firstVisiblePos) {
                // scrolling toward top of data
                out[1] = layoutManager.findViewByPosition(firstVisiblePos)?.let { firstView ->
                    firstView.top - (firstVisiblePos - targetPos) * itemDimension
                }
                    ?: throw IllegalStateException("layoutManager.findViewByPosition($firstVisiblePos) does not find any view!")
            }
            return out
        }

        /**
         * Calculate the number of positions to move in the RecyclerView given a scroll amount
         * and the size of the items to be scrolled. Return integral multiple of mBlockSize not
         * equal to zero.
         */
        fun getPositionsToMove(llm: LinearLayoutManager, scroll: Int, itemSize: Int): Int {
            if (0 == itemSize) return 0
            var positionsToMove = roundUpToBlockSize(abs(scroll) / itemSize)

            // Must move at least one block
            if (positionsToMove < blocksize) positionsToMove = blocksize

            // Clamp number of positions to move so we don't get wild flinging.
            else if (positionsToMove > getMaxPositionsToMove())
                positionsToMove = getMaxPositionsToMove()

            if (scroll < 0) positionsToMove *= -1
            if (isRTL) positionsToMove *= -1

            return if (layoutDirectionHelper.isDirectionToBottom(scroll < 0)) {
                roundDownToBlockSize(llm.findFirstVisibleItemPosition()) + positionsToMove
            } else roundDownToBlockSize(llm.findLastVisibleItemPosition()) + positionsToMove
        }

        fun isDirectionToBottom(velocityNegative: Boolean): Boolean =
            if (isRTL) velocityNegative else !velocityNegative
    }

    fun setFlingSensitivity(sensitivity: Float) {
        flingSensitivity = sensitivity
    }

    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
        val min = recyclerView.minFlingVelocity
        val max = recyclerView.maxFlingVelocity
        val threshold = (max * (1.0 - flingSensitivity) + min * flingSensitivity).toInt()
        return if (abs(velocityX) > threshold || abs(velocityX) > threshold) false
        else super.onFling(velocityX, velocityY)
    }

    /**
     * Implement this interface and call [setSnapBlockCallback] with it to get informed, when the recyclerView snaps
     * to a certain block.
     */
    interface SnapBlockCallback {
        fun onBlockSnap(snapPosition: Int)
        fun onBlockSnapped(snapPosition: Int)
    }

    // Borrowed from ViewPager.java
    private val sInterpolator = Interpolator { input ->
        var t = input
        t -= 1.0f
        t * t * t + 1.0f
    }
}