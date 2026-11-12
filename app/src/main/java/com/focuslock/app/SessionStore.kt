package com.focuslock.app

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build

/** Persists the session end time so it survives process death, enforces strict DND (while keeping media sound), and logs focus time. */
class SessionStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val nm get() = context.getSystemService(NotificationManager::class.java)
    private val am get() = context.getSystemService(AudioManager::class.java)
    val stats = StatsStore(context)

    val endTimeMs: Long get() = prefs.getLong("end", 0L)
    val isActive: Boolean get() = endTimeMs > System.currentTimeMillis()

    fun start(minutes: Int) {
        if (nm.isNotificationPolicyAccessGranted) {
            try {
                prefs.edit().putInt("prevFilter", nm.currentInterruptionFilter).apply()
                val prevPolicy = nm.notificationPolicy
                if (prevPolicy != null) {
                    prefs.edit()
                        .putBoolean("hasPrevPolicy", true)
                        .putInt("prevCategories", prevPolicy.priorityCategories)
                        .putInt("prevCallSenders", prevPolicy.priorityCallSenders)
                        .putInt("prevMsgSenders", prevPolicy.priorityMessageSenders)
                        .putInt("prevSuppressed", prevPolicy.suppressedVisualEffects)
                        .apply()
                }
            } catch (_: Exception) {}
            applyStrictDnd()
        }
        val now = System.currentTimeMillis()
        prefs.edit().putLong("start", now).putLong("end", now + minutes * 60_000L).apply()
    }

    /**
     * Suppresses all visual and audible notifications (heads-up popups, status bar icons,
     * notification drawer list, badges, ambient wake) while keeping MEDIA sound (study music),
     * alarms, and emergency/repeat calls active.
     */
    fun applyStrictDnd() {
        if (!nm.isNotificationPolicyAccessGranted) return
        try {
            val categories = if (Build.VERSION.SDK_INT >= 28) {
                NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA or
                    NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
            } else {
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
                    NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
            }
            val suppressed = if (Build.VERSION.SDK_INT >= 28) {
                NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_BADGE or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST
            } else {
                @Suppress("DEPRECATION")
                NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON or
                    NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF
            }
            nm.notificationPolicy = NotificationManager.Policy(
                categories,
                NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                NotificationManager.Policy.PRIORITY_SENDERS_STARRED,
                suppressed
            )
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
            // Ensure media stream is unmuted in case a previous DND policy muted STREAM_MUSIC.
            unmuteMediaStream()
        } catch (_: Exception) {
            try {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                unmuteMediaStream()
            } catch (_: Exception) {}
        }
    }

    fun unmuteMediaStream() {
        try {
            am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        } catch (_: Exception) {}
    }

    /** Pauses the countdown while the screen is unpinned so unpinned time never counts toward the session. */
    fun shiftEndTime(deltaMs: Long) {
        if (deltaMs <= 0L) return
        val end = endTimeMs
        val start = prefs.getLong("start", 0L)
        if (end > 0L) {
            prefs.edit()
                .putLong("end", end + deltaMs)
                .putLong("start", start + deltaMs)
                .apply()
        }
    }

    /** Re-enforces DND if the user or system toggled it off while a session is still running. */
    fun enforceIfActive() {
        if (isActive && nm.isNotificationPolicyAccessGranted) {
            if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                applyStrictDnd()
            }
        }
    }

    /**
     * Ends the session, restores the user's previous DND policy and filter, and logs the minutes focused.
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
            try {
                if (prefs.getBoolean("hasPrevPolicy", false)) {
                    var restoredCategories = prefs.getInt("prevCategories", NotificationManager.Policy.PRIORITY_CATEGORY_CALLS)
                    if (Build.VERSION.SDK_INT >= 28) {
                        // Always ensure media audio is allowed when restoring policy.
                        restoredCategories = restoredCategories or NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA
                    }
                    val restored = NotificationManager.Policy(
                        restoredCategories,
                        prefs.getInt("prevCallSenders", NotificationManager.Policy.PRIORITY_SENDERS_ANY),
                        prefs.getInt("prevMsgSenders", NotificationManager.Policy.PRIORITY_SENDERS_ANY),
                        prefs.getInt("prevSuppressed", 0)
                    )
                    nm.notificationPolicy = restored
                }
                val prev = prefs.getInt("prevFilter", NotificationManager.INTERRUPTION_FILTER_ALL)
                nm.setInterruptionFilter(
                    if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN ||
                        prev == NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    ) NotificationManager.INTERRUPTION_FILTER_ALL else prev
                )
            } catch (_: Exception) {}
        }
        unmuteMediaStream()
        return minutes
    }

    /** If a session expired while the app was not running, close it out (restores DND, logs time). */
    fun reconcile() {
        if (endTimeMs > 0L && !isActive) end()
    }
}
