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
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ViewAnimator
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.setFocusOutAllowed
import androidx.leanback.widget.setFocusOutSideAllowed
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView
import com.egeniq.androidtvprogramguide.row.ProgramGuideRowAdapter
import com.egeniq.androidtvprogramguide.timeline.ProgramGuideTimeListAdapter
import com.egeniq.androidtvprogramguide.timeline.ProgramGuideTimelineRow
import com.egeniq.androidtvprogramguide.util.FilterOption
import com.egeniq.androidtvprogramguide.util.FixedZonedDateTime
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit

abstract class ProgramGuideFragment<T> : Fragment(), ProgramGuideManager.Listener, ProgramGuideGridView.ChildFocusListener,
    ProgramGuideGridView.ScheduleSelectionListener<T>, ProgramGuideHolder<T> {

    companion object {
        private val HOUR_IN_MILLIS = TimeUnit.HOURS.toMillis(1)
        private val HALF_HOUR_IN_MILLIS = HOUR_IN_MILLIS / 2
        // We keep the duration between mStartTime and the current time larger than this value.
        // We clip out the first program entry in ProgramManager, if it does not have enough width.
        // In order to prevent from clipping out the current program, this value need be larger than
        // or equal to ProgramManager.ENTRY_MIN_DURATION.
        private val MIN_DURATION_FROM_START_TIME_TO_CURRENT_TIME = ProgramGuideManager.ENTRY_MIN_DURATION
        private val TIME_INDICATOR_UPDATE_INTERVAL = TimeUnit.SECONDS.toMillis(5)

        private const val TIME_OF_DAY_MORNING = "time_of_day_morning"
        private const val TIME_OF_DAY_AFTERNOON = "time_of_day_afternoon"
        private const val TIME_OF_DAY_EVENING = "time_of_day_evening"

        private const val MORNING_STARTS_AT_HOUR = 6
        private const val MORNING_UNTIL_HOUR = 12
        private const val AFTERNOON_UNTIL_HOUR = 19

        private val TAG: String = ProgramGuideFragment::class.java.name
    }

    protected val FILTER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

    // Config values, override in subclass if necessary
    protected open val DISPLAY_LOCALE = Locale("en", "US")
    protected open val DISPLAY_TIMEZONE: ZoneId = ZoneOffset.UTC
    protected open val SELECTABLE_DAYS_IN_PAST = 7
    protected open val SELECTABLE_DAYS_IN_FUTURE = 7
    protected open val USE_HUMAN_DATES = true
    @Suppress("LeakingThis")
    protected open val DATE_WITH_DAY_FORMATTER = DateTimeFormatter.ofPattern("EEE d MMM").withLocale(DISPLAY_LOCALE)
    protected open val DISPLAY_CURRENT_TIME_INDICATOR = true

    override val DISPLAY_SHOW_PROGRESS = true
    @LayoutRes
    protected open val OVERRIDE_LAYOUT_ID: Int? = null


    private var selectionRow = 0
    private var rowHeight = 0
    private var didScrollToBestProgramme = false
    private var currentTimeIndicatorWidth = 0
    private var timelineAdjustmentPixels = 0
    private var isInitialScroll = true

    @Suppress("LeakingThis")
    protected var currentlySelectedFilterIndex = SELECTABLE_DAYS_IN_PAST
        private set
    protected var currentlySelectedTimeOfDayFilterIndex = -1 // Correct value will be set later
        private set
    private var currentState = State.Loading

    private var created = false

    override val programGuideGrid get() = view?.findViewById<ProgramGuideGridView<T>>(R.id.programguide_grid)!!
    private val timeRow get() = view?.findViewById<ProgramGuideTimelineRow>(R.id.programguide_time_row)
    private val currentDateView get() = view?.findViewById<TextView>(R.id.programguide_current_date)
    private val jumpToLive get() = view?.findViewById<TextView>(R.id.programguide_jump_to_live)
    private val currentTimeIndicator get() = view?.findViewById<FrameLayout>(R.id.programguide_current_time_indicator)
    private val timeOfDayFilter get() = view?.findViewById<View>(R.id.programguide_time_of_day_filter)
    private val dayFilter get() = view?.findViewById<View>(R.id.programguide_day_filter)
    private val focusCatcher get() = view?.findViewById<View>(R.id.programguide_focus_catcher)
    private val contentAnimator get() = view?.findViewById<ViewAnimator>(R.id.programguide_content_animator)

    private var timelineStartMillis = 0L

    override val programGuideManager = ProgramGuideManager<T>()

    private var gridWidth = 0
    private var widthPerHour = 0
    private var viewportMillis = 0L

    private var focusEnabledScrollListener: RecyclerView.OnScrollListener? = null

    protected var currentDate: LocalDate = LocalDate.now()
        private set

    private val progressUpdateHandler: Handler = Handler()
    private val progressUpdateRunnable: Runnable = object : Runnable {
        override fun run() {
            with(System.currentTimeMillis()) {
                updateCurrentTimeIndicator(this)
                updateCurrentProgramProgress(this)
            }
            progressUpdateHandler.postDelayed(this, TIME_INDICATOR_UPDATE_INTERVAL)
        }
    }

    enum class State {
        Loading, Content, Error
    }

    /**
     * Used if you have a collapsible top menu.
     * Return false if you don't
     */
    abstract fun isTopMenuVisible(): Boolean

    /**
     * The user has selected a date, and wants to see the program guide for the date supplied in the parameter.
     * When loading data, you can use the setState(State) method to toggle between the different views.
     */
    abstract fun requestingProgramGuideFor(localDate: LocalDate)

    /**
     * Denotes that the fragment wants to refresh its data, now only used at initialization.
     * You should probably request the program guide for the current date at this point.
     */
    abstract fun requestRefresh()

    /**
     *  Called when the user has selected a schedule from the grid.
     *  When no schedule is selected (such as when navigating outside the grid), the parameter will be null.
     */
    abstract fun onScheduleSelected(programGuideSchedule: ProgramGuideSchedule<T>?)

    /**
     * Called when the user has clicked on a schedule.
     * The schedule parameter contains all the info you need for taking an action.
     */
    abstract fun onScheduleClicked(programGuideSchedule: ProgramGuideSchedule<T>)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Overriding the layout ID is also possible, so that if your layout naming follows a specific
        // structure, you are not obligated to use the name we use in the library.
        val view = inflater.inflate(OVERRIDE_LAYOUT_ID ?: R.layout.programguide_fragment, container, false)
        setupFilters(view)
        setupComponents(view)
        return view
    }

    /**
     * We call the day and daytime switchers filters here.
     * The selectable days and their displays can be changed by the config parameters.
     * Also you can change the display values by overriding the string resources.
     */
    private fun setupFilters(view: View) {
        // Day filter
        val now = FixedZonedDateTime.now().withZoneSameInstant(DISPLAY_TIMEZONE)
        val dayFilterOptions = (-SELECTABLE_DAYS_IN_PAST until SELECTABLE_DAYS_IN_FUTURE).map { dayIndex ->
                val indexLong = dayIndex.toLong()
                when {
                    USE_HUMAN_DATES && dayIndex == -1 -> FilterOption(getString(R.string.programguide_day_yesterday), FILTER_DATE_FORMATTER.format(now.plusDays(indexLong)), false)
                    USE_HUMAN_DATES && dayIndex == 0 -> FilterOption(getString(R.string.programguide_day_today), FILTER_DATE_FORMATTER.format(now.plusDays(indexLong)), true)
                    USE_HUMAN_DATES && dayIndex == 1 -> FilterOption(getString(R.string.programguide_day_tomorrow), FILTER_DATE_FORMATTER.format(now.plusDays(indexLong)), false)
                    else -> FilterOption(DATE_WITH_DAY_FORMATTER.format(now.plusDays(indexLong)), FILTER_DATE_FORMATTER.format(now.plusDays(indexLong)), false)
                }
            }
        val dayFilter = view.findViewById<View>(R.id.programguide_day_filter)
        dayFilter.findViewById<TextView>(R.id.programguide_filter_title).text = dayFilterOptions[currentlySelectedFilterIndex].displayTitle
        dayFilter.setOnClickListener { filterView ->
            AlertDialog.Builder(filterView.context)
                .setTitle(R.string.programguide_day_selector_title)
                .setSingleChoiceItems(
                    dayFilterOptions.map { it.displayTitle }.toTypedArray(),
                    currentlySelectedFilterIndex
                ) { dialogInterface, position ->
                    currentlySelectedFilterIndex = position
                    dialogInterface.dismiss()

                    dayFilter.findViewById<TextView>(R.id.programguide_filter_title).text =
                        dayFilterOptions[currentlySelectedFilterIndex].displayTitle
                    didScrollToBestProgramme = false
                    setJumpToLiveButtonVisible(false)
                    currentDate =
                        LocalDate.parse(dayFilterOptions[position].value, FILTER_DATE_FORMATTER)
                    requestingProgramGuideFor(currentDate)
                }
                .show()

        }

        // Time of day filter
        val isItMorning = now.hour < MORNING_UNTIL_HOUR
        val isItAfternoon = !isItMorning && now.hour < AFTERNOON_UNTIL_HOUR
        val isItEvening = !isItMorning && !isItAfternoon

        val timeOfDayFilterOptions = listOf(
            FilterOption(getString(R.string.programguide_part_of_day_morning), TIME_OF_DAY_MORNING, isItMorning),
            FilterOption(getString(R.string.programguide_part_of_day_afternoon), TIME_OF_DAY_AFTERNOON, isItAfternoon),
            FilterOption(getString(R.string.programguide_part_of_day_evening), TIME_OF_DAY_EVENING, isItEvening)
        )

        if (currentlySelectedTimeOfDayFilterIndex == -1) {
            currentlySelectedTimeOfDayFilterIndex = when {
                isItMorning -> 0
                isItAfternoon -> 1
                else -> 2
            }
        }
        val timeOfDayFilter = view.findViewById<View>(R.id.programguide_time_of_day_filter)
        timeOfDayFilter.findViewById<TextView>(R.id.programguide_filter_title).text = timeOfDayFilterOptions[currentlySelectedTimeOfDayFilterIndex].displayTitle
        timeOfDayFilter?.setOnClickListener {
            AlertDialog.Builder(it.context)
                .setTitle(R.string.programguide_day_time_selector_title)
                .setSingleChoiceItems(timeOfDayFilterOptions.map { it.displayTitle }.toTypedArray(), currentlySelectedTimeOfDayFilterIndex) { dialogInterface, position ->
                    currentlySelectedTimeOfDayFilterIndex = position
                    timeOfDayFilter.findViewById<TextView>(R.id.programguide_filter_title).text = timeOfDayFilterOptions[currentlySelectedTimeOfDayFilterIndex].displayTitle
                    dialogInterface.dismiss()
                    autoScrollToBestProgramme(useTimeOfDayFilter = true)
                }
                .show()
        }
    }

    /**
     * The 'jump to live' button visibility can be set here.
     * It should only be visible if the date is today, and the current scroll range does not show
     * the current timestamp.
     */
    private fun setJumpToLiveButtonVisible(visible: Boolean) {
        jumpToLive?.visibility = if (visible) View.VISIBLE else View.GONE
    }


    /**
     * Sets the selected schedule internally.
     */
    private fun setSelectedSchedule(schedule: ProgramGuideSchedule<T>?) {
        onScheduleSelected(schedule)
    }


    /**
     * When the row view is created, it needs to know the current scroll offset, so it stays in sync
     * with the other, already existing rows
     */
    override fun getTimelineRowScrollOffset(): Int {
        return timeRow?.currentScrollOffset ?: 0
    }


    /**
     * Sets up all the components to be used by the fragment.
     */
    @Suppress("ObjectLiteralToLambda", "DEPRECATION")
    @SuppressLint("RestrictedApi")
    private fun setupComponents(view: View) {
        selectionRow = resources.getInteger(R.integer.programguide_selection_row)
        rowHeight = resources.getDimensionPixelSize(R.dimen.programguide_program_row_height_with_empty_space)
        widthPerHour = resources.getDimensionPixelSize(R.dimen.programguide_table_width_per_hour)
        ProgramGuideUtil.setWidthPerHour(widthPerHour)
        val displayWidth = Resources.getSystem().displayMetrics.widthPixels
        gridWidth = (displayWidth - resources.getDimensionPixelSize(R.dimen.programguide_channel_column_width))
        val onScrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onHorizontalScrolled(dx)
            }
        }
        val timeRow = view.findViewById<RecyclerView>(R.id.programguide_time_row)!!
        timeRow.addOnScrollListener(onScrollListener)
        if (!created) {
            viewportMillis = gridWidth * HOUR_IN_MILLIS / widthPerHour
            timelineStartMillis = ProgramGuideUtil.floorTime(
                System.currentTimeMillis() - MIN_DURATION_FROM_START_TIME_TO_CURRENT_TIME,
                HALF_HOUR_IN_MILLIS
            )
            programGuideManager.updateInitialTimeRange(
                timelineStartMillis,
                timelineStartMillis + viewportMillis
            )
        }
        view.findViewById<ProgramGuideGridView<T>>(R.id.programguide_grid)?.let {
            it.initialize(programGuideManager)
            // Set the feature flags
            it.setFocusOutSideAllowed(false, false)
            it.setFocusOutAllowed(true, false)
            it.featureKeepCurrentProgramFocused = false
            it.featureFocusWrapAround = false

            it.overlapLeft = it.resources.getDimensionPixelOffset(R.dimen.programguide_channel_column_width)
            it.childFocusListener = this
            it.scheduleSelectionListener = this
            it.focusScrollStrategy = BaseGridView.FOCUS_SCROLL_ALIGNED
            it.windowAlignmentOffset = selectionRow * rowHeight
            it.windowAlignmentOffsetPercent = BaseGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED
            it.itemAlignmentOffset = 0
            it.itemAlignmentOffsetPercent = BaseGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED

            val adapter = ProgramGuideRowAdapter(it.context, this)
            it.adapter = adapter
        }
        programGuideManager.listeners.add(this)
        currentDateView?.alpha = 0f
        timeRow.let { timelineRow ->
            val timelineAdapter = ProgramGuideTimeListAdapter(resources, DISPLAY_TIMEZONE)
            timelineRow.adapter = timelineAdapter
            timelineRow.recycledViewPool.setMaxRecycledViews(R.layout.programguide_item_time, resources.getInteger(R.integer.programguide_max_recycled_view_pool_time_row_item))
        }
        val jumpToLive = view.findViewById<View>(R.id.programguide_jump_to_live)!!
        jumpToLive.setOnClickListener { autoScrollToBestProgramme() }
    }

    /**
     * Called when the fragment view has been created. We initialize some of our views here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if ((savedInstanceState == null && !created) || currentState != State.Content) {
            created = true
            // Only get data when fragment is created first time, not recreated from backstack.
            // Also when the content was not loaded yet before.
            requestRefresh()
        } else {
            setTopMarginVisibility(isTopMenuVisible())
            timeRow?.alpha = 1f
            currentDateView?.alpha = 1f
            updateCurrentDateText()
            updateCurrentTimeIndicator()
            updateTimeOfDayFilter()
            didScrollToBestProgramme = false
            setState(State.Content)
        }
    }

    /**
     * The top margin visibility can be changed, this is useful if you have a menu which collapses and
     * expands on top.
     */
    private fun setTopMarginVisibility(visible: Boolean) {
        val constraint = ConstraintSet()
        val constraintRoot = view?.findViewById<ConstraintLayout>(R.id.programguide_constraint_root) ?: return
        val topMargin = view?.findViewById<View>(R.id.programguide_top_margin) ?: return
        val menuVisibleMargin = view?.findViewById<View>(R.id.programguide_menu_visible_margin) ?: return
        constraint.clone(constraintRoot)

        if (visible) {
            constraint.clear(topMargin.id, ConstraintSet.TOP)
            constraint.connect(topMargin.id, ConstraintSet.TOP, menuVisibleMargin.id, ConstraintSet.BOTTOM)
        } else {
            constraint.clear(topMargin.id, ConstraintSet.TOP)
            constraint.connect(topMargin.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        }
        constraint.applyTo(constraintRoot)
    }

    /**
     * Called when the timerow has scrolled. We should scroll the grid the same way.
     */
    private fun onHorizontalScrolled(dx: Int) {
        if (dx == 0) {
            return
        }
        updateCurrentTimeIndicator()
        var i = 0

        programGuideGrid.let { grid ->
            val n = grid.childCount
            while (i < n) {
                grid.getChildAt(i).findViewById<View>(R.id.row).scrollBy(dx, 0)
                ++i
            }
        }
    }

    /**
     * Updates the vertical bar which displays the current time.
     * If the currently visible time range does not contain the live timestamp, it should be hidden.
     */
    protected fun updateCurrentTimeIndicator(now: Long = System.currentTimeMillis()) {
        // No content, of feature is disabled -> hide
        if (currentState != State.Content || !DISPLAY_CURRENT_TIME_INDICATOR) {
            currentTimeIndicator?.visibility = View.GONE
            return
        }

        val offset = ProgramGuideUtil.convertMillisToPixel(timelineStartMillis, now) - (timeRow?.currentScrollOffset ?: 0) - timelineAdjustmentPixels
        if (offset < 0) {
            currentTimeIndicator?.visibility = View.GONE
            setJumpToLiveButtonVisible(currentState != State.Loading && (programGuideManager.getStartTime() <= now && now <= programGuideManager.getEndTime()))
        } else {
            if (currentTimeIndicatorWidth == 0) {
                currentTimeIndicator?.measure(
                    View.MeasureSpec.UNSPECIFIED,
                    View.MeasureSpec.UNSPECIFIED
                )
                currentTimeIndicatorWidth = currentTimeIndicator?.measuredWidth ?: 0
            }
            currentTimeIndicator?.translationX = offset - currentTimeIndicatorWidth / 2f
            currentTimeIndicator?.visibility = View.VISIBLE
            setJumpToLiveButtonVisible(currentState != State.Loading && offset > gridWidth)
        }
    }

    /**
     * Update the progressbar in each visible program view.
     */
    private fun updateCurrentProgramProgress(now: Long = System.currentTimeMillis()) {
        if (!DISPLAY_SHOW_PROGRESS) {
            return
        }
        for (i in 0 until programGuideGrid.childCount) {
            programGuideGrid.getChildAt(i)?.let {
                it.findViewById<RecyclerView>(R.id.row)?.let { recycler ->
                    for (j in 0 until recycler.childCount) {
                        val row = recycler.getChildAt(j)
                        if (row is ProgramGuideItemView<*>) {
                            row.updateProgress(now)
                        }
                    }
                }
            }
        }
    }

    /**
     * Called when the fragment will be resumed.
     * Starts the progress updates for the programs.
     */
    override fun onResume() {
        super.onResume()
        if (DISPLAY_SHOW_PROGRESS) {
            progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
            progressUpdateHandler.post(progressUpdateRunnable)
        }
    }

    /**
     * Called when the fragment will be paused. We stop the progress updates in this case.
     */
    override fun onPause() {
        super.onPause()
        if (DISPLAY_SHOW_PROGRESS) {
            progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        }
    }

    /**
     * Called when the fragment will be destroyed. Removes the listeners to avoid memory leaks.
     */
    override fun onDestroyView() {
        programGuideManager.listeners.remove(this)
        programGuideGrid.scheduleSelectionListener = null
        programGuideGrid.childFocusListener = null
        super.onDestroyView()
    }

    /**
     * Via this method you can supply the data to be displayed to the fragment
     */
    @MainThread
    fun setData(
        newChannels: List<ProgramGuideChannel>,
        newChannelEntries: Map<String, List<ProgramGuideSchedule<T>>>,
        selectedDate: LocalDate
    ) {
        programGuideManager.setData(newChannels, newChannelEntries, selectedDate, DISPLAY_TIMEZONE)
    }

    override fun onTimeRangeUpdated() {
        val scrollOffset =
            (widthPerHour * programGuideManager.getShiftedTime() / HOUR_IN_MILLIS).toInt()
        Log.v(TAG, "Scrolling program guide with ${scrollOffset}px.")
        if (timeRow?.layoutManager?.childCount == 0 || isInitialScroll) {
            isInitialScroll = false
            timeRow?.post {
                timeRow?.scrollTo(scrollOffset, false)
            }
        } else {
            if (!programGuideGrid.hasFocus()) {
                // We will temporarily catch the focus, so that the program guide does not focus on all the views while it is scrolling.
                // This is better for performance, and also avoids a bug where the focused view would be out of scope.
                focusEnabledScrollListener?.let {
                    timeRow?.removeOnScrollListener(it)
                }
                focusCatcher?.visibility = View.VISIBLE
                focusCatcher?.requestFocus()
                programGuideGrid.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                var willUseScrollListener = false
                val idleScrollRunnable = Runnable {
                    programGuideGrid.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
                    focusEnabledScrollListener = null
                    programGuideGrid.requestFocus()
                    focusCatcher?.visibility = View.GONE
                    updateCurrentTimeIndicator()

                }
                focusEnabledScrollListener = object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        willUseScrollListener =
                            true // The listener has fired, so later it will also fire at the correct state.
                        timeRow?.removeCallbacks(idleScrollRunnable)
                        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                            timeRow?.removeOnScrollListener(this)
                            idleScrollRunnable.run()
                        }
                    }
                }.also {
                    timeRow?.addOnScrollListener(it)
                    // Rarely the scroll listener does not fire. In this case we rely on a child attach listener
                    var childAttachStateChangeListener: RecyclerView.OnChildAttachStateChangeListener? = null
                    childAttachStateChangeListener = object : RecyclerView.OnChildAttachStateChangeListener {

                            private var didPostCallback = false

                            override fun onChildViewDetachedFromWindow(view: View) {
                                // Unused.
                            }

                            override fun onChildViewAttachedToWindow(view: View) {
                                childAttachStateChangeListener?.let {
                                    timeRow?.removeOnChildAttachStateChangeListener(it)
                                }
                                if (!willUseScrollListener && !didPostCallback) {
                                    Log.v(TAG, "Scroll listener will not fire, posting idle scroll runnable.")
                                    timeRow?.postDelayed(idleScrollRunnable, 50L)
                                    didPostCallback = true
                                }
                            }

                        }
                    timeRow?.addOnChildAttachStateChangeListener(childAttachStateChangeListener)

                }
            }
            timeRow?.scrollTo(scrollOffset, true)
        }
        // Might just be a reset
        if (scrollOffset != 0) {
            updateTimeOfDayFilter()
            updateCurrentDateText()
        }
    }

    private fun updateTimeOfDayFilter() {
        val leftHour =
            Instant.ofEpochMilli(programGuideManager.getFromUtcMillis()).atZone(DISPLAY_TIMEZONE)
                .hour
        val selectedItemPosition = when {
            leftHour < MORNING_UNTIL_HOUR -> 0
            leftHour < AFTERNOON_UNTIL_HOUR -> 1
            else -> 2
        }
        if (currentlySelectedTimeOfDayFilterIndex != selectedItemPosition) {
            currentlySelectedTimeOfDayFilterIndex = selectedItemPosition
            val displayText = getString(
                listOf(
                    R.string.programguide_part_of_day_morning,
                    R.string.programguide_part_of_day_afternoon,
                    R.string.programguide_part_of_day_evening
                )[selectedItemPosition]
            )
            timeOfDayFilter?.findViewById<TextView>(R.id.programguide_filter_title)?.text =
                displayText
        }
    }

    private fun updateCurrentDateText() {
        // The day might have changed
        val viewportStartTime = Instant.ofEpochMilli(programGuideManager.getFromUtcMillis()).atZone(DISPLAY_TIMEZONE)
        var dateText = DATE_WITH_DAY_FORMATTER.format(viewportStartTime)
        if (dateText.endsWith(".")) {
            dateText = dateText.dropLast(1)
        }
        currentDateView?.text = dateText.capitalize()
    }

    private fun updateTimeline() {
        timelineStartMillis = ProgramGuideUtil.floorTime(
            programGuideManager.getStartTime() - MIN_DURATION_FROM_START_TIME_TO_CURRENT_TIME,
            HALF_HOUR_IN_MILLIS
        )
        val timelineDifference = programGuideManager.getStartTime() - timelineStartMillis
        timelineAdjustmentPixels = ProgramGuideUtil.convertMillisToPixel(timelineDifference)
        Log.i(TAG, "Adjusting timeline with ${timelineAdjustmentPixels}px, for a difference of ${timelineDifference / 60_000f} minutes.")
        timeRow?.let { timelineRow ->
            (timelineRow.adapter as? ProgramGuideTimeListAdapter)?.let { adapter ->
                adapter.update(timelineStartMillis, timelineAdjustmentPixels)
                for (i in 0 until programGuideGrid.childCount) {
                    programGuideGrid.getChildAt(i)?.let { (it.findViewById<RecyclerView>(R.id.row).layoutManager as LinearLayoutManager).scrollToPosition(0) }
                }
                timelineRow.resetScroll()
            }
        }

    }

    fun setTopMenuVisibility(isVisible: Boolean) {
        setTopMarginVisibility(isVisible)
    }

    /**
     * Changes the state, used for animated and handling visibility of some screen components.
     */
    fun setState(state: State) {
        currentState = state
        val alpha: Float
        when (state) {
            State.Content -> {
                alpha = 1f
                contentAnimator?.displayedChild = 2
            }
            State.Error -> {
                alpha = 0f
                contentAnimator?.displayedChild = 1
            }
            else -> {
                alpha = 0f
                contentAnimator?.displayedChild = 0
            }
        }
        listOf(currentDateView, timeRow, currentTimeIndicator).map {
            if (it == null) {
                return
            }
            it.animate().cancel()
            it.animate().alpha(alpha).setDuration(500)
        }
    }

    /**
     * The GridView calls this method on the fragment when the focused child changes.
     * This is important because we scroll the grid vertically if there are still
     * channels to be shown in the desired direction.
     */
    override fun onRequestChildFocus(oldFocus: View?, newFocus: View?) {
        if (oldFocus != null && newFocus != null) {
            val selectionRowOffset = selectionRow * rowHeight
            if (oldFocus.top < newFocus.top) {
                // Selection moves downwards
                // Adjust scroll offset to be at the bottom of the target row and to expand up. This
                // will set the scroll target to be one row height up from its current position.
                programGuideGrid.windowAlignmentOffset = selectionRowOffset + rowHeight
                programGuideGrid.itemAlignmentOffsetPercent = 100f
            } else if (oldFocus.top > newFocus.top) {
                // Selection moves upwards
                // Adjust scroll offset to be at the top of the target row and to expand down. This
                // will set the scroll target to be one row height down from its current position.
                programGuideGrid.windowAlignmentOffset = selectionRowOffset
                programGuideGrid.itemAlignmentOffsetPercent = 0f
            }
        }
    }

    /**
     * The gridview calls this method on the fragment when the focus changes on one of their child changes.
     */
    override fun onSelectionChanged(schedule: ProgramGuideSchedule<T>?) {
        setSelectedSchedule(schedule)
    }

    /**
     * This method is called from the ProgramGuideListAdapter, when the OnClickListener is triggered.
     */
    override fun onScheduleClickedInternal(schedule: ProgramGuideSchedule<T>) {
        ProgramGuideUtil.lastClickedSchedule = schedule
        onScheduleClicked(schedule)
    }

    /**
     * Called by the manager if the channels and programs are ready to be displayed.
     */
    override fun onSchedulesUpdated() {
        (programGuideGrid.adapter as? ProgramGuideRowAdapter)?.update()
        updateTimeline()

        progressUpdateHandler.removeCallbacks(progressUpdateRunnable)
        progressUpdateHandler.post(progressUpdateRunnable)

        if (!didScrollToBestProgramme) {
            didScrollToBestProgramme = true
            isInitialScroll = true
            autoScrollToBestProgramme()
        }

        timeRow?.animate()?.alpha(1f)?.setDuration(500)
    }

    /**
     * After laying out all the views inside the grid, we want to scroll
     * to the most relevant programme to the user. This function takes care of that.
     *
     * @param useTimeOfDayFilter If the time of day filter was used to do the scroll. In this case
     * the scroll will be done to a hardcoded time, instead of the current live programme.
     * @param specificChannelId The specific channel ID to scroll. Will be the first channel in
     * the list of not specified.
     */
    private fun autoScrollToBestProgramme(useTimeOfDayFilter: Boolean = false, specificChannelId: String? = null) {
        val nowMillis = Instant.now().toEpochMilli()
        // If the current time is within the managed frame, jump to it.
        if (!useTimeOfDayFilter && programGuideManager.getStartTime() <= nowMillis && nowMillis <= programGuideManager.getEndTime()) {
            val currentProgram = programGuideManager.getCurrentProgram(specificChannelId)
            if (currentProgram == null) {
                Log.w(TAG, "Can't scroll to current program because schedule not found.")
            } else {
                Log.i(TAG, "Scrolling to ${currentProgram.displayTitle}, started at ${currentProgram.startsAtMillis}")
                if (!programGuideManager.jumpTo(currentProgram.startsAtMillis)) {
                    programGuideGrid.requestFocus()
                }
            }
        } else {
            // The day is not today.
            // Go to the selected time of day.
            val timelineDate = Instant.ofEpochMilli((programGuideManager.getStartTime() + programGuideManager.getEndTime()) / 2).atZone(DISPLAY_TIMEZONE)
            val scrollToHour = when (currentlySelectedTimeOfDayFilterIndex) {
                0 -> MORNING_STARTS_AT_HOUR
                1 -> MORNING_UNTIL_HOUR
                else -> AFTERNOON_UNTIL_HOUR
            }
            val scrollToMillis =
                timelineDate.withHour(scrollToHour).truncatedTo(ChronoUnit.HOURS).toEpochSecond() * 1000
            if (programGuideManager.jumpTo(scrollToMillis)) {
                programGuideGrid.requestFocus()
            }
        }
    }

    /**
     * Scrolls to a channel with a specific ID vertically and horizontally. Highlights the current
     * program with focus after the scroll.
     *
     * @param channelId The channel ID to scroll to.
     */
    fun scrollToChannelWithId(channelId: String) {
        val index = programGuideManager.getChannelIndex(channelId)
        if (index != null) {
            programGuideGrid.smoothScrollToPosition(index)
            autoScrollToBestProgramme(useTimeOfDayFilter = false, specificChannelId = channelId)
        }
    }
}
