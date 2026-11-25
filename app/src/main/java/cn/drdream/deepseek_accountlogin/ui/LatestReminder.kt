package cn.drdream.deepseek_accountlogin.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the `latest_reminder` message accepted by the Chat Engine.
 * The wire format is `<date>,<weekday>,<location>,<platform>,<language>`;
 * platform must be `App` or `Web` (Android is rejected with HTTP 400).
 */
object LatestReminder {
    const val PLATFORM = "App"

    fun build(
        date: Date,
        locale: Locale,
        region: String,
        language: String,
    ): String {
        val dateText = SimpleDateFormat("yyyy-MM-dd", locale).format(date)
        val weekday = SimpleDateFormat("EEEE", locale).format(date)
        return "$dateText,$weekday,$region,$PLATFORM,$language"
    }
}
