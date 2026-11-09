package com.focuslock.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * "Is FocusLock the Home app, and how does the user make it so?"
 * Android 10+ has a proper role request. Android 8 and 9 do not, so we check the default Home app directly
 * and send the user to the system Home-app setting.
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
}
