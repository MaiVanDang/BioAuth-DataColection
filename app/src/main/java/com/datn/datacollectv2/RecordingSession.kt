package com.datn.datacollectv2

import android.os.SystemClock

data class RecordingSession(
    val activityLabel: String,
    val accumulatedMs: Long = 0L
) {
    fun totalElapsed(anchorMs: Long): Long =
        accumulatedMs + (SystemClock.elapsedRealtime() - anchorMs)
}