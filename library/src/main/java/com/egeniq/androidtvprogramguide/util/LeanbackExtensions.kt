@file:Suppress("PackageDirectoryMismatch")
/**
 * Extensions for Leanback. Placed into the Leanback-package to access package private stuff.
 */
package androidx.leanback.widget


fun BaseGridView.setFocusOutAllowed(throughFront: Boolean, throughEnd: Boolean) {
    mLayoutManager.setFocusOutAllowed(throughFront, throughEnd)
}

fun BaseGridView.setFocusOutSideAllowed(throughStart: Boolean, throughEnd: Boolean) {
    mLayoutManager.setFocusOutSideAllowed(throughStart, throughEnd)
}
