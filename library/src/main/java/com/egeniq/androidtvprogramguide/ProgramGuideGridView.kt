/*
 * Copyright (c) 2020, Egeniq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.egeniq.androidtvprogramguide

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.Range
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import androidx.leanback.widget.VerticalGridView
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.util.OnRepeatedKeyInterceptListener
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class ProgramGuideGridView<T>(context: Context, attrs: AttributeSet?, defStyle: Int) :
    VerticalGridView(context, attrs, defStyle) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    companion object {
        private const val INVALID_INDEX = -1
        private val FOCUS_AREA_SIDE_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(15)
        private val TAG: String = ProgramGuideGridView::class.java.name
    }

    interface ChildFocusListener {
        /**
         * Is called before focus is moved. Only children to `ProgramGrid` will be passed. See
         * `ProgramGuideGridView#setChildFocusListener(ChildFocusListener)`.
         */
        fun onRequestChildFocus(oldFocus: View?, newFocus: View?)
    }

    interface ScheduleSelectionListener<T> {
        // Can be null if nothing is selected
        fun onSelectionChanged(schedule: ProgramGuideSchedule<T>?)
    }

    private lateinit var programGuideManager: ProgramGuideManager<*>

    // New focus will be overlapped with [focusRangeLeft, focusRangeRight].
    private var focusRangeLeft: Int = 0
    private var focusRangeRight: Int = 0
    private var lastUpDownDirection: Int = 0
    private var internalKeepCurrentProgramFocused: Boolean = false
    private val tempRect = Rect()
    private var nextFocusByUpDown: View? = null
    private val rowHeight: Int
    private val selectionRow: Int
    private var lastFocusedView: View? = null
    private var correctScheduleView: View? = null

    private val onRepeatedKeyInterceptListener: OnRepeatedKeyInterceptListener

    var childFocusListener: ChildFocusListener? = null
    var scheduleSelectionListener: ScheduleSelectionListener<T>? = null

    var featureKeepCurrentProgramFocused = true
        set(value) {
            field = value
            internalKeepCurrentProgramFocused = internalKeepCurrentProgramFocused && value
        }

    var featureFocusWrapAround = true

    var featureNavigateWithChannelKeys = false

    var overlapStart = 0

    private val programManagerListener = object : ProgramGuideManager.Listener {

        override fun onSchedulesUpdated() {
            // Do nothing
        }

        override fun onTimeRangeUpdated() {
            // When time range is changed, we clear the focus state.
            clearUpDownFocusState(null)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val globalFocusChangeListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus !== nextFocusByUpDown) {
                // If focus is changed by other buttons than UP/DOWN buttons,
                // we clear the focus state.
                clearUpDownFocusState(newFocus)
            }
            nextFocusByUpDown = null
            if (ProgramGuideUtil.isDescendant(this@ProgramGuideGridView, newFocus)) {
                lastFocusedView = newFocus
                if (newFocus is ProgramGuideItemView<*> && (correctScheduleView == null || correctScheduleView == newFocus)) {
                    scheduleSelectionListener?.onSelectionChanged(newFocus.schedule as ProgramGuideSchedule<T>?)
                }
                correctScheduleView = null
            } else {
                scheduleSelectionListener?.onSelectionChanged(null)
            }
        }


    init {
        clearUpDownFocusState(null)
        // Don't cache anything that is off screen. Normally it is good to prefetch and prepopulate
        // off screen views in order to reduce jank, however the program guide is capable to scroll
        // in all four directions so not only would we prefetch views in the scrolling direction
        // but also keep views in the perpendicular direction up to date.
        // E.g. when scrolling horizontally we would have to update rows above and below the current
        // view port even though they are not visible.
        setItemViewCacheSize(0)
        val res = context.resources
        rowHeight = res.getDimensionPixelSize(R.dimen.programguide_program_row_height)
        selectionRow = res.getInteger(R.integer.programguide_selection_row)
        onRepeatedKeyInterceptListener = OnRepeatedKeyInterceptListener(this)
        setOnKeyInterceptListener(onRepeatedKeyInterceptListener)
    }

    /**
     * Initializes the grid view. It must be called before the view is actually attached to a window.
     */
    internal fun initialize(programManager: ProgramGuideManager<*>) {
        programGuideManager = programManager
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusChangeListener)
        if (!isInEditMode) {
            programGuideManager.listeners.add(programManagerListener)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusChangeListener)
        if (!isInEditMode) {
            programGuideManager.listeners.remove(programManagerListener)
        }
        clearUpDownFocusState(null)
    }


    /** Returns the currently focused item's horizontal range.  */
    internal fun getFocusRange(): Range<Int> {
        if (focusRangeLeft == Int.MIN_VALUE && focusRangeRight == Int.MAX_VALUE) {
            clearUpDownFocusState(null)
        }
        return Range(focusRangeLeft, focusRangeRight)
    }

    private fun updateUpDownFocusState(focused: View, direction: Int) {
        lastUpDownDirection = direction
        val rightMostFocusablePosition = getRightMostFocusablePosition()
        val focusedRect = tempRect

        // In order to avoid from focusing small width item, we clip the position with
        // mostRightFocusablePosition.
        focused.getGlobalVisibleRect(focusedRect)
        focusRangeLeft = min(focusRangeLeft, rightMostFocusablePosition)
        focusRangeRight = min(focusRangeRight, rightMostFocusablePosition)
        focusedRect.left = min(focusedRect.left, rightMostFocusablePosition)
        focusedRect.right = min(focusedRect.right, rightMostFocusablePosition)

        if (focusedRect.left > focusRangeRight || focusedRect.right < focusRangeLeft) {
            Log.w(TAG, "The current focus is out of [focusRangeLeft, focusRangeRight]")
            focusRangeLeft = focusedRect.left
            focusRangeRight = focusedRect.right
            return
        }
        focusRangeLeft = max(focusRangeLeft, focusedRect.left)
        focusRangeRight = min(focusRangeRight, focusedRect.right)
    }

    private fun clearUpDownFocusState(focus: View?) {
        lastUpDownDirection = 0
        if (layoutDirection == LAYOUT_DIRECTION_LTR) {
            focusRangeLeft = overlapStart
            focusRangeRight = getRightMostFocusablePosition()
        } else {
            focusRangeLeft = getLeftMostFocusablePosition()
            focusRangeRight = if (!getGlobalVisibleRect(tempRect)) {
                Int.MAX_VALUE
            } else {
                tempRect.width() - overlapStart
            }

        }
        nextFocusByUpDown = null
        // If focus is not a program item, drop focus to the current program when back to the grid
        // Only used if the feature flag is enabled
        internalKeepCurrentProgramFocused =
            featureKeepCurrentProgramFocused && (focus !is ProgramGuideItemView<*> || ProgramGuideUtil.isCurrentProgram(
                focus
            ))
    }

    private fun getRightMostFocusablePosition(): Int {
        return if (!getGlobalVisibleRect(tempRect)) {
            Integer.MAX_VALUE
        } else tempRect.right - ProgramGuideUtil.convertMillisToPixel(FOCUS_AREA_SIDE_MARGIN_MILLIS)
    }

    private fun getLeftMostFocusablePosition(): Int {
        return if (!getGlobalVisibleRect(tempRect)) {
            Integer.MIN_VALUE
        } else tempRect.left + ProgramGuideUtil.convertMillisToPixel(FOCUS_AREA_SIDE_MARGIN_MILLIS)
    }

    private fun focusFind(focused: View, direction: Int): View? {
        val focusedChildIndex = getFocusedChildIndex()
        if (focusedChildIndex == INVALID_INDEX) {
            Log.w(TAG, "No child view has focus")
            return null
        }
        val nextChildIndex =
            if (direction == View.FOCUS_UP) focusedChildIndex - 1 else focusedChildIndex + 1
        if (nextChildIndex < 0 || nextChildIndex >= childCount) {
            // Wraparound if reached head or end
            if (featureFocusWrapAround) {
                if (selectedPosition == 0) {
                    adapter?.let { adapter ->
                        scrollToPosition(adapter.itemCount - 1)
                    }
                    return null
                } else if (adapter != null && selectedPosition == adapter!!.itemCount - 1) {
                    scrollToPosition(0)
                    return null
                }
                return focused
            } else {
                return null
            }
        }
        val nextFocusedProgram = ProgramGuideUtil.findNextFocusedProgram(
            getChildAt(nextChildIndex),
            focusRangeLeft,
            focusRangeRight,
            internalKeepCurrentProgramFocused
        )
        if (nextFocusedProgram != null) {
            nextFocusedProgram.getGlobalVisibleRect(tempRect)
            nextFocusByUpDown = nextFocusedProgram

        } else {
            Log.w(TAG, "focusFind didn't find any proper focusable")
        }
        return nextFocusedProgram
    }

    // Returned value is not the position of VerticalGridView. But it's the index of ViewGroup
    // among visible children.
    private fun getFocusedChildIndex(): Int {
        for (i in 0 until childCount) {
            if (getChildAt(i).hasFocus()) {
                return i
            }
        }
        return INVALID_INDEX
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        nextFocusByUpDown = null
        if (focused == null || focused !== this && !ProgramGuideUtil.isDescendant(this, focused)) {
            return super.focusSearch(focused, direction)
        }
        if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
            updateUpDownFocusState(focused, direction)
            val nextFocus = focusFind(focused, direction)
            if (nextFocus != null) {
                return nextFocus
            }
        }
        return super.focusSearch(focused, direction)
    }

    override fun requestChildFocus(child: View, focused: View) {
        childFocusListener?.onRequestChildFocus(focusedChild, child)
        super.requestChildFocus(child, focused)
    }

    override fun onRequestFocusInDescendants(
        direction: Int,
        previouslyFocusedRect: Rect?
    ): Boolean {
        if (lastFocusedView?.isShown == true) {
            if (lastFocusedView?.requestFocus() == true) {
                return true
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    fun focusCurrentProgram() {
        internalKeepCurrentProgramFocused = true
        requestFocus()
    }

    fun isKeepCurrentProgramFocused(): Boolean {
        return internalKeepCurrentProgramFocused
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        // It is required to properly handle OnRepeatedKeyInterceptListener. If the focused
        // item's are at the almost end of screen, focus change to the next item doesn't work.
        // It restricts that a focus item's position cannot be too far from the desired position.
        val focusedView = findFocus()
        if (focusedView != null && onRepeatedKeyInterceptListener.isFocusAccelerated) {
            val location = IntArray(2)
            getLocationOnScreen(location)
            val focusedLocation = IntArray(2)
            focusedView.getLocationOnScreen(focusedLocation)
            val y = focusedLocation[1] - location[1]

            val minY = (selectionRow - 1) * rowHeight
            if (y < minY) {
                scrollBy(0, y - minY)
            }

            val maxY = (selectionRow + 1) * rowHeight
            if (y > maxY) {
                scrollBy(0, y - maxY)
            }
        }
    }

    /**
     * Intercept the channel up / down keys to navigate with them, if this feature is enabled.
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (featureNavigateWithChannelKeys && event?.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            val focusedChild = focusedChild
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                focusFind(focusedChild, View.FOCUS_UP)?.requestFocus()
                return true
            } else if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                focusFind(focusedChild, View.FOCUS_DOWN)?.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun markCorrectChild(view: View) {
        correctScheduleView = view
    }
}