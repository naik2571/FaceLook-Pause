package com.facelook.pause

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("facelook_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_SERVICE_ENABLED = "service_enabled"
        const val KEY_LOOK_AWAY_DELAY_MS = "look_away_delay_ms"
        const val KEY_LOOK_BACK_DELAY_MS = "look_back_delay_ms"
        const val KEY_AUTO_RESUME_ENABLED = "auto_resume_enabled"
        const val KEY_YAW_THRESHOLD = "yaw_threshold"
        const val KEY_PITCH_THRESHOLD = "pitch_threshold"
        const val KEY_VIBRATE_ON_ACTION = "vibrate_on_action"
    }

    // Master Switch: DORMANT BY DEFAULT (false)
    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var lookAwayDelayMs: Long
        get() = prefs.getLong(KEY_LOOK_AWAY_DELAY_MS, 800L)
        set(value) = prefs.edit().putLong(KEY_LOOK_AWAY_DELAY_MS, value).apply()

    var lookBackDelayMs: Long
        get() = prefs.getLong(KEY_LOOK_BACK_DELAY_MS, 400L)
        set(value) = prefs.edit().putLong(KEY_LOOK_BACK_DELAY_MS, value).apply()

    var isAutoResumeEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RESUME_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RESUME_ENABLED, value).apply()

    var yawThreshold: Float
        get() = prefs.getFloat(KEY_YAW_THRESHOLD, 25f)
        set(value) = prefs.edit().putFloat(KEY_YAW_THRESHOLD, value).apply()

    var pitchThreshold: Float
        get() = prefs.getFloat(KEY_PITCH_THRESHOLD, 28f)
        set(value) = prefs.edit().putFloat(KEY_PITCH_THRESHOLD, value).apply()

    var isVibrateOnAction: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ON_ACTION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ON_ACTION, value).apply()
}
