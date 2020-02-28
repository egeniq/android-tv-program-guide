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

package com.egeniq.androidtvprogramguide.item

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.egeniq.androidtvprogramguide.R
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import kotlin.math.max
import kotlin.math.min


class ProgramGuideItemView<T> : FrameLayout {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var schedule: ProgramGuideSchedule<T>? = null

    private val staticItemPadding: Int = resources.getDimensionPixelOffset(R.dimen.programguide_item_padding)

    private var itemTextWidth: Int = 0
    private var maxWidthForRipple: Int = 0
    private var preventParentRelayout = false

    private val titleView: TextView
    private val progressView: ProgressBar

    init {
        View.inflate(context, R.layout.programguide_item_program, this)

        titleView = findViewById(R.id.title)
        progressView = findViewById(R.id.progress)
    }

    fun setValues(scheduleItem: ProgramGuideSchedule<T>, fromUtcMillis: Long, toUtcMillis: Long,
                  gapTitle: String, displayProgress: Boolean) {
        schedule = scheduleItem
        val layoutParams = layoutParams
        if (layoutParams != null) {
            val spacing = resources.getDimensionPixelSize(R.dimen.programguide_item_spacing)
            layoutParams.width = scheduleItem.width - 2 * spacing // Here we subtract the spacing, otherwise the calculations will be wrong at other places
            setLayoutParams(layoutParams)
        }
        var title = schedule?.displayTitle
        if (scheduleItem.isGap) {
            title = gapTitle
            setBackgroundResource(R.drawable.programguide_gap_item_background)
            isClickable = false
        } else {
            setBackgroundResource(R.drawable.programguide_item_program_background)
            isClickable = scheduleItem.isClickable
        }
        title = if (title?.isEmpty() == true) resources.getString(R.string.programguide_title_no_program) else title

        updateText(title)
        initProgress(ProgramGuideUtil.convertMillisToPixel(startMillis = scheduleItem.startsAtMillis, endMillis = scheduleItem.endsAtMillis))
        if (displayProgress) {
            updateProgress(System.currentTimeMillis())
        } else {
            progressView.visibility = View.GONE
        }

        titleView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        itemTextWidth = titleView.measuredWidth - titleView.paddingLeft - titleView.paddingRight
        // Maximum width for us to use a ripple
        maxWidthForRipple = ProgramGuideUtil.convertMillisToPixel(fromUtcMillis, toUtcMillis)
    }

    private fun updateText(title: String?) {
        titleView.text = title
    }

    private fun initProgress(width: Int) {
        progressView.max = width
    }

    fun updateProgress(now: Long) {
        with(progressView) {
            schedule?.let {
                if (it.isCurrentProgram.not()) {
                    visibility = View.GONE
                } else {
                    visibility = View.VISIBLE
                    progressView.progress = ProgramGuideUtil.convertMillisToPixel(it.startsAtMillis, now)
                }
            }
        }
    }

    /** Update programItemView to handle alignments of text. */
    fun updateVisibleArea() {
        val parentView = parent as View
        layoutVisibleArea(parentView.left + parentView.paddingLeft - left, right - parentView.right)
    }

    /**
     * Layout title and episode according to visible area.
     *
     *
     * Here's the spec.
     * 1. Don't show text if it's shorter than 48dp.
     * 2. Try showing whole text in visible area by placing and wrapping text, but do not wrap text less than 30min.
     * 3. Episode title is visible only if title isn't multi-line.
     *
     * @param startOffset Offset of the start position from the enclosing view's start position.
     * @param endOffset Offset of the end position from the enclosing view's end position.
     */
    private fun layoutVisibleArea(startOffset: Int, endOffset: Int) {
        val width = schedule?.width ?: 0
        var startPadding = max(0, startOffset)
        var endPadding = max(0, endOffset)
        val minWidth = min(width, itemTextWidth + 2 * staticItemPadding)
        if (startPadding > 0 && width - startPadding < minWidth) {
            startPadding = max(0, width - minWidth)
        }
        if (endPadding > 0 && width - endPadding < minWidth) {
            endPadding = max(0, width - minWidth)
        }

        if (startPadding + staticItemPadding != paddingStart || endPadding + staticItemPadding != paddingEnd) {
            preventParentRelayout = true // The size of this view is kept, no need to tell parent.
            titleView.setPaddingRelative(startPadding + staticItemPadding, 0, endPadding + staticItemPadding, 0)
            preventParentRelayout = false
        }
    }

    override fun requestLayout() {
        if (preventParentRelayout) {
            // Trivial layout, no need to tell parent.
            forceLayout()
        } else {
            super.requestLayout()
        }
    }

    fun clearValues() {
        tag = null
        schedule = null
    }
}