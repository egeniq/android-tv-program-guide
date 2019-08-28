package com.egeniq.androidtvprogramguide

import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule

interface ProgramGuideHolder<T> {
    val programGuideGrid : ProgramGuideGridView<T>
    val programGuideManager : ProgramGuideManager<T>

    fun getTimelineRowScrollOffset(): Int
    fun onScheduleClicked(schedule: ProgramGuideSchedule<T>)
}