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

import android.util.Log
import androidx.annotation.MainThread
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.util.FixedZonedDateTime
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.max

class ProgramGuideManager<T> {

    /**
     * If the first entry's visible duration is shorter than this value, we should clip the entry out.
     * Note: If this value is larger than 1 min, it could cause mismatches between the entry's
     * position and detailed view's time range.
     */
    companion object {
        internal val ENTRY_MIN_DURATION = TimeUnit.MINUTES.toMillis(2) // 2 min
        private val MAX_UNACCOUNTED_TIME_BEFORE_GAP = TimeUnit.MINUTES.toMillis(15) // 15 min

        private const val DAY_STARTS_AT_HOUR = 5
        private const val DAY_ENDS_NEXT_DAY_AT_HOUR = 6

        private val TAG: String = ProgramGuideManager::class.java.name
    }


    private var startUtcMillis: Long = 0
    private var endUtcMillis: Long = 0
    private var fromUtcMillis: Long = 0
    private var toUtcMillis: Long = 0

    val listeners = mutableListOf<Listener>()

    private val channels = mutableListOf<ProgramGuideChannel>()
    private val channelEntriesMap = mutableMapOf<String, List<ProgramGuideSchedule<T>>>()

    val channelCount get() = channels.size


    /** Returns the start time of currently managed time range, in UTC millisecond.  */
    fun getFromUtcMillis(): Long {
        return fromUtcMillis
    }

    /** Returns the end time of currently managed time range, in UTC millisecond.  */
    fun getToUtcMillis(): Long {
        return toUtcMillis
    }

    /** Update the initial time range to manage.
     * This is the time window where the scroll starts. */
    internal fun updateInitialTimeRange(startUtcMillis: Long, endUtcMillis: Long) {
        this.startUtcMillis = startUtcMillis
        if (endUtcMillis > this.endUtcMillis) {
            this.endUtcMillis = endUtcMillis
        }
        setTimeRange(startUtcMillis, endUtcMillis)
    }


    @Suppress("ConvertTwoComparisonsToRangeCheck")
    private fun updateChannelsTimeRange(selectedDate: LocalDate, timeZone: ZoneId) {
        val viewPortWidth = toUtcMillis - fromUtcMillis
        var newStartMillis: Long? = null
        var newEndMillis: Long? = null
        for (channel in channels) {
            val channelId = channel.id
            val entries = channelEntriesMap[channelId]
            val size = entries?.size
            if (size == 0 || size == null) {
                continue
            }
            val lastEntry = entries.last()
            val firstEntry = entries.first()
            if (newStartMillis == null || newEndMillis == null) {
                newStartMillis = firstEntry.startsAtMillis
                newEndMillis = lastEntry.endsAtMillis
            }
            if (newStartMillis > firstEntry.startsAtMillis && firstEntry.startsAtMillis > 0) {
                newStartMillis = firstEntry.startsAtMillis
            }
            if ((newEndMillis < lastEntry.endsAtMillis && lastEntry.endsAtMillis != java.lang.Long.MAX_VALUE)) {
                newEndMillis = lastEntry.endsAtMillis
            }
        }
        startUtcMillis = newStartMillis ?: fromUtcMillis
        endUtcMillis = newEndMillis ?: toUtcMillis
        if (endUtcMillis > startUtcMillis) {
            for (channel in channels) {
                val channelId = channel.id
                val entries = (channelEntriesMap[channelId] ?: emptyList()).toMutableList()
                if (entries.isEmpty()) {
                    entries.add(ProgramGuideSchedule.createGap(startUtcMillis, endUtcMillis))
                } else {
                    // Cut off items which don't belong in the desired timeframe
                    val timelineStartsAt =
                        selectedDate.atStartOfDay(timeZone).withHour(DAY_STARTS_AT_HOUR)
                    val timelineEndsAt =
                        timelineStartsAt.plusDays(1).withHour(DAY_ENDS_NEXT_DAY_AT_HOUR)

                    val timelineStartsAtMillis = timelineStartsAt.toEpochSecond() * 1_000
                    val timelineEndsAtMillis = timelineEndsAt.toEpochSecond() * 1_000

                    val timelineIterator = entries.listIterator()
                    while (timelineIterator.hasNext()) {
                        val current = timelineIterator.next()
                        if (current.endsAtMillis < timelineStartsAtMillis || current.startsAtMillis > timelineEndsAtMillis) {
                            // Is before start or after end
                            timelineIterator.remove()
                        } else if (current.startsAtMillis < timelineStartsAtMillis && current.endsAtMillis < timelineEndsAtMillis) {
                            // Sticks out both sides
                            timelineIterator.set(
                                current.copy(
                                    startsAtMillis = timelineStartsAtMillis,
                                    endsAtMillis = timelineEndsAtMillis
                                )
                            )
                        } else if (current.startsAtMillis < timelineStartsAtMillis) {
                            // Sticks out left
                            timelineIterator.set(current.copy(startsAtMillis = timelineStartsAtMillis))
                        } else if (current.endsAtMillis > timelineEndsAtMillis) {
                            // Sticks out right
                            timelineIterator.set(current.copy(endsAtMillis = timelineEndsAtMillis))
                        }
                    }

                    if (startUtcMillis < timelineStartsAtMillis) {
                        startUtcMillis = timelineStartsAtMillis
                    }
                    if (endUtcMillis > timelineEndsAtMillis) {
                        endUtcMillis = timelineEndsAtMillis
                    }

                    // Pad the items on the right
                    val lastEntry = entries.lastOrNull()
                    if (lastEntry == null || endUtcMillis > lastEntry.endsAtMillis) {
                        // We need to add a gap item to fill the place
                        entries.add(
                            ProgramGuideSchedule.createGap(
                                lastEntry?.endsAtMillis
                                    ?: startUtcMillis, endUtcMillis
                            )
                        )
                    } else if (lastEntry.endsAtMillis == java.lang.Long.MAX_VALUE) {
                        entries.removeAt(entries.size - 1)
                        entries.add(
                            ProgramGuideSchedule.createGap(
                                lastEntry.startsAtMillis,
                                endUtcMillis
                            )
                        )
                    }
                    // Pad the items on the left
                    val firstEntry = entries.firstOrNull()
                    if (firstEntry == null || startUtcMillis < firstEntry.startsAtMillis) {
                        // We need to add a gap item to fill the place
                        entries.add(
                            0, ProgramGuideSchedule.createGap(
                                startUtcMillis, firstEntry?.startsAtMillis
                                    ?: endUtcMillis
                            )
                        )
                    } else if (firstEntry.startsAtMillis <= 0) {
                        entries.removeAt(0)
                        entries.add(
                            0,
                            ProgramGuideSchedule.createGap(startUtcMillis, firstEntry.endsAtMillis)
                        )
                    }
                    // Entries in the API not always follow each other. There are empty places which have not been accounted for, which offsets our calculations
                    // At this place, we adjust the ending times to be that of the next item. If the difference here is too big, we will insert a gap manually.
                    // The originalTimes property can be used to retrieve the original starting and ending times.
                    val listIterator = entries.listIterator()
                    while (listIterator.hasNext()) {
                        val current = listIterator.next()
                        if (listIterator.hasNext()) {
                            val next = entries[listIterator.nextIndex()]
                            val timeDifference = next.startsAtMillis - current.endsAtMillis
                            if (timeDifference < MAX_UNACCOUNTED_TIME_BEFORE_GAP) {
                                listIterator.set(current.copy(endsAtMillis = next.startsAtMillis))
                            } else {
                                listIterator.add(
                                    ProgramGuideSchedule.createGap(
                                        current.endsAtMillis,
                                        next.startsAtMillis
                                    )
                                )
                            }
                        }
                    }
                    // Iterate the list again, this time to find a very short schedule. The schedules should be shifted to account for this.
                    val shortIterator = entries.listIterator()
                    var millisToAddToNextStart = 0L
                    while (shortIterator.hasNext()) {
                        val current = shortIterator.next()
                        val currentDuration =
                            current.endsAtMillis - (current.startsAtMillis + millisToAddToNextStart)
                        val hasNext = shortIterator.hasNext()
                        if (!hasNext && (millisToAddToNextStart > 0 || currentDuration < ENTRY_MIN_DURATION)) {
                            Log.i(
                                TAG,
                                "The last schedule (${current.program}) has been extended because it was too short."
                            )
                            val replacingSchedule = current.copy(
                                startsAtMillis = current.startsAtMillis + millisToAddToNextStart,
                                endsAtMillis = max(
                                    current.startsAtMillis + ENTRY_MIN_DURATION,
                                    current.endsAtMillis
                                )
                            )
                            shortIterator.set(replacingSchedule)
                        } else if (currentDuration < ENTRY_MIN_DURATION) {
                            Log.i(
                                TAG,
                                "The schedule ${current.program} has been extended because it was too short."
                            )
                            val replacingSchedule = current.copy(
                                startsAtMillis = current.startsAtMillis + millisToAddToNextStart,
                                endsAtMillis = current.startsAtMillis + millisToAddToNextStart + ENTRY_MIN_DURATION
                            )
                            shortIterator.set(replacingSchedule)
                            millisToAddToNextStart =
                                replacingSchedule.endsAtMillis - current.endsAtMillis
                        } else if (millisToAddToNextStart > 0) {
                            Log.i(
                                TAG,
                                "The schedule ${current.program} has been shortened because the previous schedule had to be extended."
                            )
                            val replacingSchedule =
                                current.copy(startsAtMillis = current.startsAtMillis + millisToAddToNextStart)
                            shortIterator.set(replacingSchedule)
                            millisToAddToNextStart = 0
                        }
                    }
                }
                channelEntriesMap[channelId] = entries
            }
        }
        setTimeRange(startUtcMillis, startUtcMillis + viewPortWidth)
    }

    /**
     * Jumps to a specific position.
     * @param timeMillis The time in milliseconds to jump to.
     * @return True if the time was shifted. False if not change was triggered (time was the same as before).
     */
    internal fun jumpTo(timeMillis: Long): Boolean {
        val timeShift = timeMillis - fromUtcMillis
        shiftTime(timeShift)
        return timeShift != 0L
    }

    /** Shifts the time range by the given time. Also makes the guide scroll the views.  */
    internal fun shiftTime(timeMillisToScroll: Long) {
        var fromUtcMillis = fromUtcMillis + timeMillisToScroll
        var toUtcMillis = toUtcMillis + timeMillisToScroll
        // We tried to scroll before the initial start time
        if (fromUtcMillis < startUtcMillis) {
            toUtcMillis += startUtcMillis - fromUtcMillis
            fromUtcMillis = startUtcMillis
        }
        // We tried to scroll over the initial end time
        if (toUtcMillis > endUtcMillis) {
            fromUtcMillis -= toUtcMillis - endUtcMillis
            toUtcMillis = endUtcMillis
        }
        setTimeRange(fromUtcMillis, toUtcMillis)
    }

    /** Returned the scrolled(shifted) time in milliseconds.  */
    internal fun getShiftedTime(): Long {
        return fromUtcMillis - startUtcMillis
    }

    /** Returns the start time set by [.updateInitialTimeRange].  */
    internal fun getStartTime(): Long {
        return startUtcMillis
    }

    internal fun getEndTime(): Long {
        return endUtcMillis
    }

    private fun setTimeRange(fromUtcMillis: Long, toUtcMillis: Long) {
        if (this.fromUtcMillis != fromUtcMillis || this.toUtcMillis != toUtcMillis) {
            this.fromUtcMillis = fromUtcMillis
            this.toUtcMillis = toUtcMillis
            notifyTimeRangeUpdated()
        }
    }

    /**
     * Returns an entry as [ProgramGuideSchedule] for a given `channelId` and `index` of
     * entries within the currently managed time range. Returned [ProgramGuideSchedule] can be a dummy one
     * (e.g., whose channelId is INVALID_ID), when it corresponds to a gap between programs.
     */
    internal fun getScheduleForChannelIdAndIndex(
        channelId: String,
        index: Int
    ): ProgramGuideSchedule<T> {
        return channelEntriesMap.getValue(channelId)[index]
    }

    /** Returns the program index of the program at `time` or -1 if not found.  */
    internal fun getProgramIndexAtTime(channelId: String?, time: Long): Int {
        channelEntriesMap[channelId]?.forEachIndexed { index, entry ->
            if (entry.startsAtMillis <= time && time < entry.endsAtMillis) {
                return index
            }
        }
        return -1
    }

    /**
     * Returns the number of schedules, which lies within the currently managed time range, for a
     * given `channelId`.
     */
    internal fun getSchedulesCount(channelId: String): Int {
        return channelEntriesMap[channelId]?.size ?: 0
    }


    private fun notifyTimeRangeUpdated() {
        for (listener in listeners) {
            listener.onTimeRangeUpdated()
        }
    }

    private fun notifySchedulesUpdated() {
        for (listener in listeners) {
            listener.onSchedulesUpdated()
        }
    }

    /**
     * Returns a [ProgramGuideChannel] at a given `channelIndex` of the currently managed channels.
     * Returns `null` if such a channel is not found.
     */
    fun getChannel(channelIndex: Int): ProgramGuideChannel? {
        return if (channelIndex < 0 || channelIndex >= channels.size) {
            null
        } else channels[channelIndex]
    }

    fun getCurrentProgram(specificChannelId: String? = null): ProgramGuideSchedule<T>? {
        val firstChannel = channels.firstOrNull() ?: return null
        val now = FixedZonedDateTime.now().toEpochSecond() * 1000
        var bestMatch: ProgramGuideSchedule<T>? = null
        val channelId = specificChannelId ?: firstChannel.id
        channelEntriesMap[channelId]?.let {
            it.forEach { schedule ->
                if (schedule.startsAtMillis < now) {
                    bestMatch = schedule
                    if (schedule.endsAtMillis > now) {
                        return schedule
                    }
                }
            }
        }
        return bestMatch
    }

    fun getChannelIndex(channelId: String): Int? {
        val index = channels.indexOfFirst { it.id == channelId }
        return if (index < 0) {
            null
        } else {
            index
        }
    }

    @MainThread
    fun setData(
        newChannels: List<ProgramGuideChannel>,
        newChannelEntries: Map<String, List<ProgramGuideSchedule<T>>>,
        selectedDate: LocalDate,
        timeZone: ZoneId
    ) {
        channels.clear()
        channelEntriesMap.clear()

        channels.addAll(newChannels)
        channelEntriesMap.putAll(newChannelEntries)

        updateChannelsTimeRange(selectedDate, timeZone)
        notifySchedulesUpdated()
    }

    /**
     * Replaces a program in the entries based on the ID of the supplied program.
     * Since IDs should be unique, only the first match will be replaced.
     *
     * @param program The program with the new data.
     * @return The resulting program of the replacement. Null if no replacement happened
     */
    fun updateProgram(program: ProgramGuideSchedule<T>): ProgramGuideSchedule<T>? {
        val channelEntriesMapKeys = channelEntriesMap.keys
        var replacement: ProgramGuideSchedule<T>? = null
        for (key in channelEntriesMapKeys) {
            val list = channelEntriesMap[key]
            var mutatedList: MutableList<ProgramGuideSchedule<T>>? = null
            if (list != null && replacement == null) {
                for (possibleMatch in list) {
                    if (possibleMatch.id == program.id && replacement == null) {
                        if (possibleMatch.originalTimes.startsAtMillis != program.originalTimes.startsAtMillis ||
                            possibleMatch.originalTimes.endsAtMillis != program.originalTimes.endsAtMillis
                        ) {
                            Log.w(
                                TAG,
                                "Different times found when updating program with ID: ${program.id}. Replacement will happen, but times will not be changed."
                            )
                        }
                        if (mutatedList == null) {
                            mutatedList = list.toMutableList()
                        }
                        val index = list.indexOf(possibleMatch)
                        replacement = possibleMatch.copy(
                            isClickable = program.isClickable,
                            displayTitle = program.displayTitle,
                            program = program.program
                        )
                        mutatedList[index] = replacement
                    }
                }
            }
            if (mutatedList != null) {
                channelEntriesMap[key] = mutatedList
            }
        }
        return replacement
    }

    interface Listener {
        fun onTimeRangeUpdated()
        fun onSchedulesUpdated()
    }
}