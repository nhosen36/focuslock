package com.focuslock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Helpers for Do Not Disturb access settings, Display Over Other Apps (overlay lock), and notifications.
 */
object HomeRole {
    private const val ALERT_CHANNEL_ID = "focuslock_lock_alert"
    private const val ALERT_NOTIF_ID = 9002

    /**
     * Launches an intent directly on the current Activity (without FLAG_ACTIVITY_NEW_TASK)
     * so if the system Settings app was already open in the background on another page,
     * Android opens the target sub-screen directly instead of merely resuming the old Settings page.
     */
    private fun startSettingsIntent(ctx: Context, intent: Intent): Boolean {
        val act = ctx.findActivity()
        return try {
            if (act != null) {
                act.startActivity(intent)
            } else {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens the system "Do Not Disturb access" screen directly across Android 8–17
     * (including Honor MagicOS, Huawei EMUI, Samsung One UI, Xiaomi HyperOS, Vivo, Oppo, Pixel).
     */
    fun openDndSettings(ctx: Context) {
        val candidates = listOf(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$NotificationAccessSettingsActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$ZenAccessSettingsActivity"
                )
            ),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in candidates) {
            if (startSettingsIntent(ctx, i)) return
        }
    }

    fun canDrawOverlays(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun openOverlaySettings(ctx: Context) {
        val candidates = listOf(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            ),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in candidates) {
            if (startSettingsIntent(ctx, i)) return
        }
    }

    fun isScreenPinned(ctx: Context): Boolean {
        val am = ctx.getSystemService(ActivityManager::class.java) ?: return false
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    fun startPinning(activity: Activity?) {
        if (activity == null) return
        try {
            if (!isScreenPinned(activity)) {
                activity.startLockTask()
            }
        } catch (_: Exception) {}
    }

    fun stopPinning(activity: Activity?) {
        if (activity == null) return
        try {
            if (isScreenPinned(activity)) {
                activity.stopLockTask()
            }
        } catch (_: Exception) {}
    }

    fun showReturnToLockNotification(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Focus Session Lock",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when a focus session is active"
                setBypassDnd(true)
            }
            nm.createNotificationChannel(channel)

            val launchIntent = Intent(ctx, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pi = PendingIntent.getActivity(
                ctx,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = NotificationCompat.Builder(ctx, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("FocusLock session active")
                .setContentText("Tap to return to FocusLock.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .build()
            nm.notify(ALERT_NOTIF_ID, notif)
        } catch (_: Exception) {}
    }

    fun clearReturnToLockNotification(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.cancel(ALERT_NOTIF_ID)
        } catch (_: Exception) {}
    }
}
