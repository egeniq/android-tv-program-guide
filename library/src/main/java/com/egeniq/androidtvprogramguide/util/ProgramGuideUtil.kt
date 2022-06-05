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

package com.egeniq.androidtvprogramguide.util

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView
import java.util.*
import java.util.concurrent.TimeUnit

object ProgramGuideUtil {
    private var WIDTH_PER_HOUR = 0
    private const val INVALID_INDEX = -1

    var lastClickedSchedule: ProgramGuideSchedule<*>? = null

    /**
     * Sets the width in pixels that corresponds to an hour in program guide. Assume that this is
     * called from main thread only, so, no synchronization.
     */
    fun setWidthPerHour(widthPerHour: Int) {
        WIDTH_PER_HOUR = widthPerHour
    }


    @JvmStatic
    fun convertMillisToPixel(millis: Long): Int {
        return (millis * WIDTH_PER_HOUR / TimeUnit.HOURS.toMillis(1)).toInt()
    }

    @JvmStatic
    fun convertMillisToPixel(startMillis: Long, endMillis: Long): Int {
        // Convert to pixels first to avoid accumulation of rounding errors.
        return convertMillisToPixel(endMillis) - convertMillisToPixel(startMillis)
    }

    /** Gets the time in millis that corresponds to the given pixels in the program guide.  */
    fun convertPixelToMillis(pixel: Int): Long {
        return pixel * TimeUnit.HOURS.toMillis(1) / WIDTH_PER_HOUR
    }

    /**
     * Return the view should be focused in the given program row according to the focus range.
     *
     * @param keepCurrentProgramFocused If `true`, focuses on the current program if possible,
     * else falls back the general logic.
     */
    fun findNextFocusedProgram(
        programRow: View,
        focusRangeLeft: Int,
        focusRangeRight: Int,
        keepCurrentProgramFocused: Boolean
    ): View? {
        val focusables = ArrayList<View>()
        findFocusables(programRow, focusables)

        if (lastClickedSchedule != null) {
            // Select the current program if possible.
            for (i in focusables.indices) {
                val focusable = focusables[i]
                if (focusable is ProgramGuideItemView<*> && focusable.schedule?.id == lastClickedSchedule?.id) {
                    lastClickedSchedule = null
                    return focusable
                }
            }
            lastClickedSchedule = null
        }

        if (keepCurrentProgramFocused) {
            // Select the current program if possible.
            for (i in focusables.indices) {
                val focusable = focusables[i]
                if (focusable is ProgramGuideItemView<*> && isCurrentProgram(focusable)) {
                    return focusable
                }
            }
        }

        // Find the largest focusable among fully overlapped focusables.
        var maxFullyOverlappedWidth = Integer.MIN_VALUE
        var maxPartiallyOverlappedWidth = Integer.MIN_VALUE
        var nextFocusIndex = INVALID_INDEX
        for (i in focusables.indices) {
            val focusable = focusables[i]
            val focusableRect = Rect()
            focusable.getGlobalVisibleRect(focusableRect)
            if (focusableRect.left <= focusRangeLeft && focusRangeRight <= focusableRect.right) {
                // the old focused range is fully inside the focusable, return directly.
                return focusable
            } else if (focusRangeLeft <= focusableRect.left && focusableRect.right <= focusRangeRight) {
                // the focusable is fully inside the old focused range, choose the widest one.
                val width = focusableRect.width()
                if (width > maxFullyOverlappedWidth) {
                    nextFocusIndex = i
                    maxFullyOverlappedWidth = width
                }
            } else if (maxFullyOverlappedWidth == Integer.MIN_VALUE) {
                val overlappedWidth = if (focusRangeLeft <= focusableRect.left)
                    focusRangeRight - focusableRect.left
                else
                    focusableRect.right - focusRangeLeft
                if (overlappedWidth > maxPartiallyOverlappedWidth) {
                    nextFocusIndex = i
                    maxPartiallyOverlappedWidth = overlappedWidth
                }
            }
        }
        return if (nextFocusIndex != INVALID_INDEX) {
            focusables[nextFocusIndex]
        } else null
    }

    /**
     * Returns `true` if the program displayed in the give [ ] is a current program.
     */
    fun isCurrentProgram(view: ProgramGuideItemView<*>): Boolean {
        return view.schedule?.isCurrentProgram == true
    }

    private fun findFocusables(v: View, outFocusable: ArrayList<View>) {
        if (v.isFocusable) {
            outFocusable.add(v)
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findFocusables(v.getChildAt(i), outFocusable)
            }
        }
    }

    /** Returns `true` if the given view is a descendant of the give container.  */
    fun isDescendant(container: ViewGroup, view: View?): Boolean {
        if (view == null) {
            return false
        }
        var p: ViewParent? = view.parent
        while (p != null) {
            if (p === container) {
                return true
            }
            p = p.parent
        }
        return false
    }

    /**
     * Floors time to the given `timeUnit`. For example, if time is 5:32:11 and timeUnit is
     * one hour (60 * 60 * 1000), then the output will be 5:00:00.
     */
    fun floorTime(timeMs: Long, timeUnit: Long): Long {
        return timeMs - timeMs % timeUnit
    }
}