/*
 * Copyright (c) 2019, Egeniq
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

package com.egeniq.androidtvprogramguide.timeline

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

class ProgramGuideTimelineRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) :
        ProgramGuideTimelineGridView(context, attrs, defStyle) {

    companion object {
        private const val FADING_EDGE_STRENGTH_START = 1.0f
    }

    private var scrollPosition: Int = 0

    /** Returns the current scroll position  */
    val currentScrollOffset: Int
        get() = abs(scrollPosition)

    fun resetScroll() {
        layoutManager?.scrollToPosition(0)
        scrollPosition = 0
    }

    /** Scrolls horizontally to the given position.  */
    fun scrollTo(scrollOffset: Int, smoothScroll: Boolean) {
        val dx = scrollOffset - currentScrollOffset
        if (smoothScroll) {
            smoothScrollBy(dx, 0)
        } else {
            scrollBy(dx, 0)
        }
    }


    override fun onScrolled(dx: Int, dy: Int) {
        scrollPosition += dx
    }

    override fun getLeftFadingEdgeStrength(): Float {
        return FADING_EDGE_STRENGTH_START
    }

    override fun getRightFadingEdgeStrength(): Float {
        return 0f
    }


    // State saving part

    public override fun onSaveInstanceState(): Parcelable? {
        //begin boilerplate code that allows parent classes to save state
        val superState = super.onSaveInstanceState()
        val ss = SavedState(superState)
        //end
        ss.scrollPosition = scrollPosition
        return ss
    }

    public override fun onRestoreInstanceState(state: Parcelable) {
        //begin boilerplate code so parent classes can restore state
        if (state !is SavedState) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.superState)
        //end
        this.scrollPosition = state.scrollPosition
    }

    internal class SavedState : BaseSavedState {

        var scrollPosition = 0

        constructor(superState: Parcelable?) : super(superState)

        private constructor(`in`: Parcel) : super(`in`) {
            this.scrollPosition = `in`.readInt()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(this.scrollPosition)
        }

        companion object {
            @Suppress("unused")
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
}
