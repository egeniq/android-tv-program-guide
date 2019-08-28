package com.egeniq.androidtvprogramguide.entity

import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import org.threeten.bp.Instant

data class ProgramGuideSchedule<T>(
        val id: Long,
        val startsAtMillis: Long,
        val endsAtMillis: Long,
        val originalTimes: OriginalTimes,
        val isClickable: Boolean,
        val displayTitle: String?,
        val program: T?) {

    data class OriginalTimes(
            val startsAtMillis: Long,
            val endsAtMillis: Long
    )

    companion object {
        private const val GAP_ID = -1L

        fun <T> createGap(from: Long, to: Long): ProgramGuideSchedule<T> {
            return ProgramGuideSchedule(GAP_ID,
                    from,
                    to,
                    OriginalTimes(from, to),
                    false,
                    null,
                    null)
        }

        fun <T> createScheduleWithProgram(id: Long, startsAt: Instant, endsAt: Instant, isClickable: Boolean, displayTitle: String?, program: T): ProgramGuideSchedule<T> {
            return ProgramGuideSchedule(id,
                    startsAt.toEpochMilli(),
                    endsAt.toEpochMilli(),
                    OriginalTimes(startsAt.toEpochMilli(), endsAt.toEpochMilli()),
                    isClickable,
                    displayTitle,
                    program)
        }
    }

    val width = ProgramGuideUtil.convertMillisToPixel(startsAtMillis, endsAtMillis)
    val isGap = program == null
    val isCurrentProgram: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now in startsAtMillis..endsAtMillis
        }
}