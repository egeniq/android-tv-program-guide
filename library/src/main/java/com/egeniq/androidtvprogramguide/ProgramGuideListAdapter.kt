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

import android.annotation.SuppressLint
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import androidx.recyclerview.widget.RecyclerView
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView

class ProgramGuideListAdapter<T>(
    res: Resources,
    private val programGuideFragment: ProgramGuideHolder<T>,
    private val channelIndex: Int
) :
    RecyclerView.Adapter<ProgramGuideListAdapter.ProgramItemViewHolder<T>>(),
    ProgramGuideManager.Listener {

    private val programGuideManager: ProgramGuideManager<T>
    private val noInfoProgramTitle: String

    private var channelId: String = ""

    init {
        setHasStableIds(true)
        programGuideManager = programGuideFragment.programGuideManager
        noInfoProgramTitle = res.getString(R.string.programguide_title_no_program)
        onSchedulesUpdated()
    }

    override fun onTimeRangeUpdated() {
        // Do nothing
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onSchedulesUpdated() {
        val channel = programGuideManager.getChannel(channelIndex)
        if (channel != null) {
            channelId = channel.id
            notifyDataSetChanged()
        }
    }

    fun updateProgram(program: ProgramGuideSchedule<*>): Boolean {
        for (position in 0 until itemCount) {
            if (programGuideManager.getScheduleForChannelIdAndIndex(
                    channelId,
                    position
                ).id == program.id
            ) {
                notifyItemChanged(position)
                return true
            }
        }
        return false
    }

    override fun getItemCount(): Int {
        return programGuideManager.getSchedulesCount(channelId)
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.programguide_item_program_container
    }

    override fun getItemId(position: Int): Long {
        return programGuideManager.getScheduleForChannelIdAndIndex(channelId, position).id
    }

    override fun onBindViewHolder(holder: ProgramItemViewHolder<T>, position: Int) {
        val programGuideSchedule =
            programGuideManager.getScheduleForChannelIdAndIndex(channelId, position)
        val gapTitle = noInfoProgramTitle
        holder.onBind(programGuideSchedule, programGuideFragment, gapTitle)
    }

    override fun onViewRecycled(holder: ProgramItemViewHolder<T>) {
        holder.onUnbind()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgramItemViewHolder<T> {
        val itemView = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return ProgramItemViewHolder(itemView)
    }

    class ProgramItemViewHolder<R>(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private var programGuideItemView: ProgramGuideItemView<R>? = null

        init {
            // Make all child view clip to the outline
            itemView.outlineProvider = ViewOutlineProvider.BACKGROUND
            itemView.clipToOutline = true
        }

        fun onBind(
            schedule: ProgramGuideSchedule<R>,
            programGuideHolder: ProgramGuideHolder<R>,
            gapTitle: String
        ) {
            val programManager = programGuideHolder.programGuideManager
            @Suppress("UNCHECKED_CAST")
            programGuideItemView = itemView as ProgramGuideItemView<R>

            programGuideItemView?.setOnClickListener {
                programGuideHolder.onScheduleClickedInternal(schedule)
            }
            programGuideItemView?.setValues(
                scheduleItem = schedule,
                fromUtcMillis = programManager.getFromUtcMillis(),
                toUtcMillis = programManager.getToUtcMillis(),
                gapTitle = gapTitle,
                displayProgress = programGuideHolder.DISPLAY_SHOW_PROGRESS
            )
        }

        fun onUnbind() {
            programGuideItemView?.setOnClickListener(null)
            programGuideItemView?.clearValues()
        }
    }
}