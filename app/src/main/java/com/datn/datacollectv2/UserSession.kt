package com.datn.datacollectv2

import android.content.Context

object UserSession {

    private const val PREF          = "user_session_v1"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_USER_ID   = "user_id"
    private const val KEY_NAME      = "name"
    private const val KEY_AGE       = "age"
    private const val KEY_GENDER    = "gender"
    private const val KEY_HAND      = "dominant_hand"
    private const val KEY_DEVICE    = "device"

    data class Profile(
        val userId : String,
        val name   : String,
        val age    : String,
        val gender : String,
        val hand   : String,
        val device : String
    )

    // ── Read ───────────────────────────────────────────────────────────

    fun isLoggedIn(ctx: Context): Boolean =
        pref(ctx).getBoolean(KEY_LOGGED_IN, false)

    fun getProfile(ctx: Context): Profile? {
        val p = pref(ctx)
        if (!p.getBoolean(KEY_LOGGED_IN, false)) return null
        return Profile(
            userId = p.getString(KEY_USER_ID, "") ?: "",
            name   = p.getString(KEY_NAME,    "") ?: "",
            age    = p.getString(KEY_AGE,     "") ?: "",
            gender = p.getString(KEY_GENDER,  "") ?: "",
            hand   = p.getString(KEY_HAND,    "") ?: "",
            device = p.getString(KEY_DEVICE,  "") ?: ""
        )
    }

    // ── Write ──────────────────────────────────────────────────────────

    fun saveAndLogin(ctx: Context, profile: Profile) {
        pref(ctx).edit().apply {
            putBoolean(KEY_LOGGED_IN, true)
            putString(KEY_USER_ID,   profile.userId)
            putString(KEY_NAME,      profile.name)
            putString(KEY_AGE,       profile.age)
            putString(KEY_GENDER,    profile.gender)
            putString(KEY_HAND,      profile.hand)
            putString(KEY_DEVICE,    profile.device)
            apply()
        }
    }

    fun logout(ctx: Context) {
        pref(ctx).edit().clear().apply()
    }

    // ── Private ────────────────────────────────────────────────────────

    private fun pref(ctx: Context) =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
}
