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

package com.egeniq.androidtvprogramguide.timeline

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.egeniq.androidtvprogramguide.R
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import org.threeten.bp.Instant
import org.threeten.bp.ZoneId
import org.threeten.bp.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Adapts the time range from ProgramManager to the timeline header row of the program guide
 * table.
 */
class ProgramGuideTimeListAdapter(
    res: Resources,
    private val displayTimezone: ZoneId
) : RecyclerView.Adapter<ProgramGuideTimeListAdapter.TimeViewHolder>() {

    companion object {
        private val TIME_UNIT_MS = TimeUnit.MINUTES.toMillis(30)
        private var rowHeaderOverlapping: Int = 0
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm")
    }

    // Nearest half hour at or before the start time.
    private var startUtcMs: Long = 0
    private var timelineAdjustmentPixels = 0

    init {
        if (rowHeaderOverlapping == 0) {
            rowHeaderOverlapping =
                abs(res.getDimensionPixelOffset(R.dimen.programguide_time_row_negative_margin))
        }

    }

    fun update(startTimeMs: Long, timelineAdjustmentPx: Int) {
        startUtcMs = startTimeMs
        timelineAdjustmentPixels = timelineAdjustmentPx
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return Integer.MAX_VALUE
    }


    override fun getItemViewType(position: Int): Int {
        return R.layout.programguide_item_time
    }

    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        val startTime = startUtcMs + position * TIME_UNIT_MS
        val endTime = startTime + TIME_UNIT_MS

        val itemView = holder.itemView
        val timeDate = Instant.ofEpochMilli(startTime).atZone(displayTimezone)
        val timeString = TIME_FORMATTER.format(timeDate)
        (itemView as TextView).text = timeString

        val lp = itemView.layoutParams as RecyclerView.LayoutParams
        lp.width = ProgramGuideUtil.convertMillisToPixel(startTime, endTime)
        if (position == 0) {
            // Adjust width for the first entry to make the item starts from the fading edge.
            lp.marginStart = rowHeaderOverlapping - lp.width / 2 - timelineAdjustmentPixels
        } else {
            lp.marginStart = 0
        }
        itemView.layoutParams = lp
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return TimeViewHolder(itemView)
    }

    class TimeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}
