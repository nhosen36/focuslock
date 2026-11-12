package com.focuslock.app

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helpers for Do Not Disturb access settings and Android Screen Pinning (LockTask).
 */
object HomeRole {
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

    fun openSecuritySettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
}
