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

package com.egeniq.androidtvprogramguide.row

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.egeniq.androidtvprogramguide.ProgramGuideHolder
import com.egeniq.androidtvprogramguide.ProgramGuideManager
import com.egeniq.androidtvprogramguide.R
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel
import com.egeniq.androidtvprogramguide.timeline.ProgramGuideTimelineGridView
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class ProgramGuideRowGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ProgramGuideTimelineGridView(context, attrs, defStyle) {

    companion object {
        private val ONE_HOUR_MILLIS = TimeUnit.HOURS.toMillis(1)
        private val HALF_HOUR_MILLIS = ONE_HOUR_MILLIS / 2
    }

    private var keepFocusToCurrentProgram: Boolean = false

    private lateinit var programGuideHolder: ProgramGuideHolder<*>
    private lateinit var programGuideManager: ProgramGuideManager<*>

    private var channel: ProgramGuideChannel? = null
    private val minimumStickOutWidth =
        resources.getDimensionPixelOffset(R.dimen.programguide_minimum_item_width_sticking_out_behind_channel_column)

    private val layoutListener = object : OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            updateChildVisibleArea()
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        val itemView = child as ProgramGuideItemView<*>
        if (left <= itemView.right && itemView.left <= right) {
            itemView.updateVisibleArea()
        }
    }

    override fun onScrolled(dx: Int, dy: Int) {
        // Remove callback to prevent updateChildVisibleArea being called twice.
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        super.onScrolled(dx, dy)
        updateChildVisibleArea()
    }

    // Call this API after RTL is resolved. (i.e. View is measured.)
    private fun isDirectionStart(direction: Int): Boolean {
        return if (layoutDirection == View.LAYOUT_DIRECTION_LTR)
            direction == View.FOCUS_LEFT
        else
            direction == View.FOCUS_RIGHT
    }

    // Call this API after RTL is resolved. (i.e. View is measured.)
    private fun isDirectionEnd(direction: Int): Boolean {
        return if (layoutDirection == View.LAYOUT_DIRECTION_LTR)
            direction == View.FOCUS_RIGHT
        else
            direction == View.FOCUS_LEFT
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val focusedEntry = (focused as ProgramGuideItemView<*>).schedule
            ?: return super.focusSearch(focused, direction)
        val fromMillis = programGuideManager.getFromUtcMillis()
        val toMillis = programGuideManager.getToUtcMillis()

        if (isDirectionStart(direction) || direction == View.FOCUS_BACKWARD) {
            if (focusedEntry.startsAtMillis < fromMillis) {
                // The current entry starts outside of the view; Align or scroll to the left.
                scrollByTime(
                    max(-ONE_HOUR_MILLIS, focusedEntry.startsAtMillis - fromMillis)
                )
                return focused
            }
        } else if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
            if (focusedEntry.endsAtMillis > toMillis) {
                // The current entry ends outside of the view; Scroll to the right (or left, if RTL).
                scrollByTime(ONE_HOUR_MILLIS)
                return focused
            }
        }

        val target = super.focusSearch(focused, direction)
        if (target !is ProgramGuideItemView<*>) {
            if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
                if (focusedEntry.endsAtMillis != toMillis) {
                    // The focused entry is the last entry; Align to the right edge.
                    scrollByTime(focusedEntry.endsAtMillis - toMillis)
                    return focused
                }
            }
            return target
        }

        val targetEntry = target.schedule ?: return target

        if (isDirectionStart(direction) || direction == View.FOCUS_BACKWARD) {
            if (targetEntry.startsAtMillis < fromMillis && targetEntry.endsAtMillis < fromMillis + HALF_HOUR_MILLIS) {
                // The target entry starts outside the view; Align or scroll to the left (or right, on RTL).
                scrollByTime(
                    max(-ONE_HOUR_MILLIS, targetEntry.startsAtMillis - fromMillis)
                )
            }
        } else if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
            if (targetEntry.startsAtMillis > fromMillis + ONE_HOUR_MILLIS + HALF_HOUR_MILLIS) {
                // The target entry starts outside the view; Align or scroll to the right (or left, on RTL).
                scrollByTime(
                    min(
                        ONE_HOUR_MILLIS,
                        targetEntry.startsAtMillis - fromMillis - ONE_HOUR_MILLIS
                    )
                )
            }
        }

        return target
    }


    private fun scrollByTime(timeToScroll: Long) {
        programGuideManager.shiftTime(timeToScroll)
    }

    override fun onChildDetachedFromWindow(child: View) {
        if (child.hasFocus()) {
            // Focused view can be detached only if it's updated.
            val entry = (child as ProgramGuideItemView<*>).schedule
            if (entry?.program == null) {
                // The focus is lost due to information loaded. Requests focus immediately.
                // (Because this entry is detached after real entries attached, we can't take
                // the below approach to resume focus on entry being attached.)
                post { requestFocus() }
            } else if (entry.isCurrentProgram) {
                // Current program is visible in the guide.
                // Updated entries including current program's will be attached again soon
                // so give focus back in onChildAttachedToWindow().
                keepFocusToCurrentProgram = true
            }
        }
        super.onChildDetachedFromWindow(child)
    }

    override fun onChildAttachedToWindow(child: View) {
        super.onChildAttachedToWindow(child)
        if (keepFocusToCurrentProgram) {
            val entry = (child as ProgramGuideItemView<*>).schedule
            if (entry?.isCurrentProgram == true) {
                keepFocusToCurrentProgram = false
                post { requestFocus() }
            }
        }
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        // This part is required to intercept the default child focus behavior.
        // When focus is coming from the top, and there's an item hiding behind the left channel column, default focus behavior
        // is to select that one, because it is the closest to the previously focused element.
        // But because this item is behind the channel column, it is not visible to the user that it is selected
        // So we check for this occurence, and select the next item if possible.
        val gridHasFocus = programGuideHolder.programGuideGrid.hasFocus()
        if (child == null) {
            super.requestChildFocus(child, focused)
            return
        }

        if (!gridHasFocus) {
            findNextFocusableChild(child)?.let {
                super.requestChildFocus(child, focused)
                it.requestFocus()
                // This skipping is required because in some weird way the global focus change listener gets the event
                // in the wrong order, so first the replacing item, then the old one.
                // By skipping the second one, only the (correct) replacing item will be notfied to the listeners
                programGuideHolder.programGuideGrid.markCorrectChild(it)
                return
            }
        }
        super.requestChildFocus(child, focused)
    }

    private fun findNextFocusableChild(child: View): View? {
        //Check if child is focusable and return
        val leftEdge = child.left
        val rightEdge = child.left + child.width
        val viewPosition = layoutManager?.getPosition(child)

        if (layoutDirection == LAYOUT_DIRECTION_LTR && (leftEdge >= programGuideHolder.programGuideGrid.getFocusRange().lower ||
                    rightEdge >= programGuideHolder.programGuideGrid.getFocusRange().lower + minimumStickOutWidth)
        ) {
            return child
        } else if (layoutDirection == LAYOUT_DIRECTION_RTL && (rightEdge <= programGuideHolder.programGuideGrid.getFocusRange().upper ||
                    leftEdge <= programGuideHolder.programGuideGrid.getFocusRange().upper - minimumStickOutWidth)
        ) {
            // RTL mode
            return child
        }

        //if not check if we have a next child and recursively test it again
        if (viewPosition != null && viewPosition >= 0 && viewPosition < (layoutManager?.itemCount
                ?: (0 - 1))
        ) {
            val nextChild = layoutManager?.findViewByPosition(viewPosition + 1)
            nextChild?.let {
                return findNextFocusableChild(it)
            }
        }

        return null
    }

    public override fun onRequestFocusInDescendants(
        direction: Int,
        previouslyFocusedRect: Rect?
    ): Boolean {
        val programGrid = programGuideHolder.programGuideGrid
        // Give focus according to the previous focused range
        val focusRange = programGrid.getFocusRange()
        val nextFocus = ProgramGuideUtil.findNextFocusedProgram(
            this,
            focusRange.lower,
            focusRange.upper,
            programGrid.isKeepCurrentProgramFocused()
        )

        if (nextFocus != null) {
            return nextFocus.requestFocus()
        }
        val result = super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
        if (!result) {
            // The default focus search logic of LeanbackLibrary is sometimes failed.
            // As a fallback solution, we request focus to the first focusable view.
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.isShown && child.hasFocusable()) {
                    return child.requestFocus()
                }
            }
        }
        return result
    }

    fun setChannel(channelToSet: ProgramGuideChannel) {
        channel = channelToSet
    }

    /** Sets the instance of [ProgramGuideHolder]  */
    fun setProgramGuideFragment(fragment: ProgramGuideHolder<*>) {
        programGuideHolder = fragment
        programGuideManager = programGuideHolder.programGuideManager
    }

    /** Resets the scroll with the initial offset `currentScrollOffset`.  */
    fun resetScroll(scrollOffset: Int) {
        val channel = channel
        val startTime =
            ProgramGuideUtil.convertPixelToMillis(scrollOffset) + programGuideManager.getStartTime()
        val position = if (channel == null) {
            -1
        } else {
            programGuideManager.getProgramIndexAtTime(channel.id, startTime)
        }
        if (position < 0) {
            layoutManager?.scrollToPosition(0)
        } else if (channel?.id != null) {
            val slug = channel.id
            val entry = programGuideManager.getScheduleForChannelIdAndIndex(slug, position)
            val offset = ProgramGuideUtil.convertMillisToPixel(
                programGuideManager.getStartTime(),
                entry.startsAtMillis
            ) - scrollOffset
            (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, offset)
            // Workaround to b/31598505. When a program's duration is too long,
            // RecyclerView.onScrolled() will not be called after scrollToPositionWithOffset().
            // Therefore we have to update children's visible areas by ourselves in this case.
            // Since scrollToPositionWithOffset() will call requestLayout(), we can listen to this
            // behavior to ensure program items' visible areas are correctly updated after layouts
            // are adjusted, i.e., scrolling is over.
            viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
    }

    internal fun updateChildVisibleArea() {
        for (i in 0 until childCount) {
            val child = getChildAt(i) as ProgramGuideItemView<*>
            if (left < child.right && child.left < right) {
                child.updateVisibleArea()
            }
        }
    }
}
