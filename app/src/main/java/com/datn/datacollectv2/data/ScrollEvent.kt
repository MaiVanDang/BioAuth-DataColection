package com.datn.datacollectv2.data

data class ScrollEvent(
    val timestamp_ms: Long,
    val x: Float,
    val y: Float,
    val pressure: Float,
    val size: Float,
    val phase: String,
    val pointer_id: Int,
    val activity: String = "form"
)