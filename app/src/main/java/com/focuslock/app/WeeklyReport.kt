package com.focuslock.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/** Posts the weekly focus summary. Opt-in only; timing is approximate (WorkManager). */
class WeeklyReportWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val stats = StatsStore(ctx)
        val total = stats.lastDays(7).sumOf { it.minutes }
        val text = if (total == 0) "No focus sessions this week. Start one today."
        else "You focused ${total / 60}h ${total % 60}m this week. Streak: ${stats.streak()} days."

        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("weekly", "Weekly report", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, "weekly")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your weekly focus report")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(1001, n)
        return Result.success()
    }
}

object WeeklyReminder {
    private const val NAME = "weekly_report"

    fun isEnabled(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("weekly", false)

    fun enable(ctx: Context) {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("weekly", true).apply()
        val now = LocalDateTime.now()
        var next = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).with(LocalTime.of(18, 0))
        if (!next.isAfter(now)) next = next.plusWeeks(1)
        val delay = Duration.between(now, next).toMinutes()
        val req = PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delay, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    fun disable(ctx: Context) {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("weekly", false).apply()
        WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}
