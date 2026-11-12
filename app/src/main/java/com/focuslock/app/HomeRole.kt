package com.focuslock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Checks and requests the Default Home app role, DND access settings, and Android Screen Pinning (LockTask).
 */
object HomeRole {
    fun isHeld(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = ctx.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == ctx.packageName
    }

    fun requestIntent(ctx: Context): Intent {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = ctx.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.createRequestRoleIntent(RoleManager.ROLE_HOME)
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun openHomeSettings(ctx: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in intents) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {}
        }
    }

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
}
