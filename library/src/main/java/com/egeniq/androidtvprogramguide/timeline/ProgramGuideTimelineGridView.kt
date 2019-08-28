package com.egeniq.androidtvprogramguide.timeline

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

open class ProgramGuideTimelineGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RecyclerView(context, attrs, defStyle) {

    init {

        layoutManager = object : LinearLayoutManager(context, HORIZONTAL, false) {
            override fun onRequestChildFocus(
                parent: RecyclerView,
                state: State,
                child: View,
                focused: View?
            ): Boolean {
                // This disables the default scroll behavior for focus movement.
                return true
            }
        }

        // RecyclerView is always focusable, however this is not desirable for us, so disable.
        // See b/18863217 (ag/634046) for reasons to why RecyclerView is focusable.
        isFocusable = false

        // Don't cache anything that is off screen. Normally it is good to prefetch and prepopulate
        // off screen views in order to reduce jank, however the program guide is capable to scroll
        // in all four directions so not only would we prefetch views in the scrolling direction
        // but also keep views in the perpendicular direction up to date.
        // E.g. when scrolling horizontally we would have to update rows above and below the current
        // view port even though they are not visible.
        setItemViewCacheSize(0)
    }

    final override fun setItemViewCacheSize(size: Int) {
        super.setItemViewCacheSize(size)
    }
}
