package com.focuslock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat

/**
 * Helpers for Do Not Disturb access settings, background-leave alerts, and optional Screen Pinning.
 */
object HomeRole {
    private const val ALERT_CHANNEL_ID = "focuslock_lock_alert"
    private const val ALERT_NOTIF_ID = 9002

    fun openDndSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val detail = Intent(
                    "android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS",
                    Uri.parse("package:${ctx.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(detail)
                return
            } catch (_: Exception) {}
        }
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
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

    /** Shows an ongoing alert notification if the user minimizes FocusLock during an active session. */
    fun showReturnToLockNotification(ctx: Context) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Focus Session Lock",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts you when you leave an active focus session"
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
                .setContentTitle("Focus session paused!")
                .setContentText("Return to FocusLock to resume your focus timer.")
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
