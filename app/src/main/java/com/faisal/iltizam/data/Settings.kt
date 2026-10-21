package com.faisal.iltizam.data

import android.content.Context

class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("finnhub_key", "") ?: ""
        set(v) = prefs.edit().putString("finnhub_key", v.trim()).apply()

    /** دقائق بين كل فحص في الخلفية (أندرويد لا يسمح بأقل من 15). */
    var intervalMinutes: Int
        get() = prefs.getInt("interval", 15)
        set(v) = prefs.edit().putInt("interval", v).apply()

    var onlyMarketHours: Boolean
        get() = prefs.getBoolean("market_hours", true)
        set(v) = prefs.edit().putBoolean("market_hours", v).apply()
}
