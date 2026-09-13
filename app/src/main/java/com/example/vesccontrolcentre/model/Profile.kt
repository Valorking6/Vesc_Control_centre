package com.example.vesccontrolcentre.model

import android.content.Context

enum class ProfileType(
    val key: String,
    val defaultTitle: String,
    val defaultSubtitle: String,
    val defaultCurrentMax: Float,
    val defaultInCurrentMax: Float,
    val defaultWattMax: Float
) {
    CRAWL("CRAWL", "CRAWL MODE", "LOW SPEED | SMOOTH THROTTLE", 20f, 10f, 500f),
    NORMAL("NORMAL", "NORMAL", "BALANCED | DAILY RIDE", 85f, 28f, 3500f),
    LONG_RANGE("LONG_RANGE", "LONG RANGE", "EFFICIENCY | CONSERVE BATTERY", 70f, 20f, 2500f),
    MAX_POWER("MAX_POWER", "MAX POWER", "PERFORMANCE | FULL BOOST", 120f, 45f, 5000f);

    companion object {
        fun fromKey(key: String): ProfileType? {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
        }
    }
}

data class ProfileConfig(
    val type: ProfileType,
    val lCurrentMax: Float,
    val lInCurrentMax: Float,
    val lWattMax: Float
) {
    fun save(context: Context) {
        val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("profile_${type.key}_current_max", lCurrentMax)
            .putFloat("profile_${type.key}_in_current_max", lInCurrentMax)
            .putFloat("profile_${type.key}_watt_max", lWattMax)
            .apply()
    }

    companion object {
        fun load(context: Context, type: ProfileType): ProfileConfig {
            val prefs = context.getSharedPreferences("vesc_prefs", Context.MODE_PRIVATE)
            val currentMax = prefs.getFloat("profile_${type.key}_current_max", type.defaultCurrentMax)
            val inCurrentMax = prefs.getFloat("profile_${type.key}_in_current_max", type.defaultInCurrentMax)
            val wattMax = prefs.getFloat("profile_${type.key}_watt_max", type.defaultWattMax)
            return ProfileConfig(type, currentMax, inCurrentMax, wattMax)
        }
    }
}
