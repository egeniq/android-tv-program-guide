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

import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.View
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView

/**
 * Listener to make focus change faster over time.
 * */
class OnRepeatedKeyInterceptListener(private val verticalGridView: VerticalGridView) :
    BaseGridView.OnKeyInterceptListener {

    companion object {
        private val THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS = intArrayOf(2000, 5000)
        private val MAX_SKIPPED_VIEW_COUNT = intArrayOf(1, 4)
        private const val MSG_MOVE_FOCUS = 1000
    }


    private val mHandler = KeyInterceptHandler(this)
    private var mDirection: Int = 0
    var isFocusAccelerated: Boolean = false
        private set
    private var mRepeatedKeyInterval: Long = 0

    override fun onInterceptKeyEvent(event: KeyEvent): Boolean {
        mHandler.removeMessages(MSG_MOVE_FOCUS)
        if (event.keyCode != KeyEvent.KEYCODE_DPAD_UP && event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) {
            return false
        }

        val duration = event.eventTime - event.downTime
        if (duration < THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS[0] || event.isCanceled) {
            isFocusAccelerated = false
            return false
        }
        mDirection =
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) View.FOCUS_UP else View.FOCUS_DOWN
        var skippedViewCount = MAX_SKIPPED_VIEW_COUNT[0]
        for (i in 1 until THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS.size) {
            if (THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS[i] < duration) {
                skippedViewCount = MAX_SKIPPED_VIEW_COUNT[i]
            } else {
                break
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            mRepeatedKeyInterval = duration / event.repeatCount
            isFocusAccelerated = true
        } else {
            // HACK: we move focus skippedViewCount times more even after ACTION_UP. Without this
            // hack, a focused view's position doesn't reach to the desired position
            // in ProgramGrid.
            isFocusAccelerated = false
        }
        for (i in 0 until skippedViewCount) {
            mHandler.sendEmptyMessageDelayed(
                MSG_MOVE_FOCUS,
                mRepeatedKeyInterval * i / (skippedViewCount + 1)
            )
        }
        return false
    }

    class KeyInterceptHandler(listener: OnRepeatedKeyInterceptListener) :
        WeakHandler<OnRepeatedKeyInterceptListener>(Looper.getMainLooper(), listener) {

        public override fun handleMessage(msg: Message, referent: OnRepeatedKeyInterceptListener) {
            if (msg.what == MSG_MOVE_FOCUS) {
                val focused = referent.verticalGridView.findFocus()
                if (focused != null) {
                    val v = focused.focusSearch(referent.mDirection)
                    if (v != null && v !== focused) {
                        v.requestFocus(referent.mDirection)
                    }
                }
            }
        }
    }
}
