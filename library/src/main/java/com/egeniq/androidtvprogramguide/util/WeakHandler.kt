package com.egeniq.androidtvprogramguide.util

import android.os.Handler
import android.os.Looper
import android.os.Message

import java.lang.ref.WeakReference

/**
 * A Handler that keeps a [WeakReference] to an object.
 *
 * Use this to prevent leaking an Activity or other Context while messages are still pending.
 * When you extend this class you **MUST NOT** use a non static inner class, or the
 * containing object will still be leaked.
 *
 * See [Avoiding memory leaks](http://android-developers.blogspot.com/2009/01/avoiding-memory-leaks.html).
 */
@Suppress("unused")
abstract class WeakHandler<T> : Handler {
    private val mRef: WeakReference<T>

    /**
     * Constructs a new handler with a weak reference to the given referent using the provided
     * Looper instead of the default one.
     *
     * @param looper The looper, must not be null.
     * @param ref the referent to track
     */
    constructor(looper: Looper, ref: T) : super(looper) {
        mRef = WeakReference(ref)
    }

    /**
     * Constructs a new handler with a weak reference to the given referent.
     *
     * @param ref the referent to track
     */
    constructor(ref: T) {
        mRef = WeakReference(ref)
    }

    /** Calls [.handleMessage] if the WeakReference is not cleared.  */
    override fun handleMessage(msg: Message) {
        val referent = mRef.get() ?: return
        handleMessage(msg, referent)
    }

    /**
     * Subclasses must implement this to receive messages.
     *
     *
     * If the WeakReference is cleared this method will no longer be called.
     *
     * @param msg the message to handle
     * @param referent the referent. Guaranteed to be non null.
     */
    protected abstract fun handleMessage(msg: Message, referent: T)
}
