package com.focuslock.app

import android.app.NotificationManager
import android.content.Context

/** Persists the session end time so it survives process death, handles DND, and logs focus time. */
class SessionStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val nm get() = context.getSystemService(NotificationManager::class.java)
    val stats = StatsStore(context)

    val endTimeMs: Long get() = prefs.getLong("end", 0L)
    val isActive: Boolean get() = endTimeMs > System.currentTimeMillis()

    fun start(minutes: Int) {
        if (nm.isNotificationPolicyAccessGranted) {
            prefs.edit().putInt("prevFilter", nm.currentInterruptionFilter).apply()
            // Priority only: calls/starred contacts per the user's own DND settings.
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
        val now = System.currentTimeMillis()
        prefs.edit().putLong("start", now).putLong("end", now + minutes * 60_000L).apply()
    }

    /**
     * Ends the session, restores DND, and logs the minutes actually focused.
     * Returns the logged minutes (0 if no session was in progress).
     */
    fun end(): Int {
        val end = endTimeMs
        val start = prefs.getLong("start", 0L)
        var minutes = 0
        if (end > 0L) {
            minutes = ((minOf(System.currentTimeMillis(), end) - start) / 60_000L).toInt().coerceAtLeast(0)
            stats.addSession(minutes)
        }
        prefs.edit().putLong("end", 0L).putLong("start", 0L).apply()
        if (nm.isNotificationPolicyAccessGranted) {
            val prev = prefs.getInt("prevFilter", NotificationManager.INTERRUPTION_FILTER_ALL)
            nm.setInterruptionFilter(
                if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
                    NotificationManager.INTERRUPTION_FILTER_ALL else prev
            )
        }
        return minutes
    }

    /** If a session expired while the app was not running, close it out (restores DND, logs time). */
    fun reconcile() {
        if (endTimeMs > 0L && !isActive) end()
    }
}
