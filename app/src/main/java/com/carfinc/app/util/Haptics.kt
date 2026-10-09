package com.carfinc.app.util

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Central haptic vocabulary so every interaction feels consistent.
 *  tick    – slider detents / dragging across steps
 *  soft    – light touch when grabbing a piece
 *  click   – taps on tools and chips
 *  confirm – something was added / saved / applied
 *  reject  – delete / undo / error
 *  heavy   – long press / big moments
 */
object Haptics {
    @Volatile var enabled: Boolean = true

    private fun fire(v: View, c: Int) { if (enabled) v.performHapticFeedback(c) }

    fun tick(v: View) = fire(v, HapticFeedbackConstants.CLOCK_TICK)

    fun soft(v: View) = fire(
        v,
        if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
        else HapticFeedbackConstants.CLOCK_TICK
    )

    fun click(v: View) = fire(v, HapticFeedbackConstants.VIRTUAL_KEY)

    fun key(v: View) = fire(v, HapticFeedbackConstants.KEYBOARD_TAP)

    fun confirm(v: View) = fire(
        v,
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS
    )

    fun reject(v: View) = fire(
        v,
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
    )

    fun heavy(v: View) = fire(v, HapticFeedbackConstants.LONG_PRESS)
}
