package com.focuslock.app

import android.app.Activity
import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Checks and requests the Default Home app role, plus Android Screen Pinning (LockTask) helpers
 * so the user cannot minimize FocusLock or pull down the notification shade during a session.
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
        return homeSettingsIntent()
    }

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

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
