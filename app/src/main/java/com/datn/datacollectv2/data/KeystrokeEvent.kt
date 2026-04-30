package com.datn.datacollectv2.data

data class KeystrokeEvent(
    val timestamp_ms: Long,
    val field_id: String,
    val char_count: Int,
    val inter_key_ms: Long,
    val is_delete: Boolean,
    val activity: String = "form"
)