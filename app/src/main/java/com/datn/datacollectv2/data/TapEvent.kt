package com.datn.datacollectv2.data

data class TapEvent(
    val timestamp_ms: Long,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float,
    val answer_id: String,
    val phase: String,
    val hold_ms: Long = 0,
    val activity: String = "form"
)