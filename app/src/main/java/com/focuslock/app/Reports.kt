package com.focuslock.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate

private fun fmtMinutes(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Light)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun StatsScreen(stats: StatsStore) {
    val ctx = LocalContext.current
    var monthly by remember { mutableStateOf(false) }
    val days = remember(monthly) { stats.lastDays(if (monthly) 30 else 7) }
    val total = days.sumOf { it.minutes }
    val sessions = days.sumOf { it.sessions }
    val active = days.count { it.minutes > 0 }
    val maxMin = (days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
    var reminder by remember { mutableStateOf(WeeklyReminder.isEnabled(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { WeeklyReminder.enable(ctx); reminder = true }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Link("Week", color = if (!monthly) Fg else Muted) { monthly = false }
            Link("Month", color = if (monthly) Fg else Muted) { monthly = true }
            Spacer(Modifier.weight(1f))
            Link("Share", color = Accent) {
                ShareCard.share(
                    ctx, if (monthly) "My month in focus" else "My week in focus", fmtMinutes(total),
                    "$sessions sessions, $active active days, ${stats.streak()}-day streak"
                )
            }
        }

        Column {
            Text(fmtMinutes(total), fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(if (monthly) "last 30 days" else "last 7 days", color = Muted, fontSize = 13.sp)
        }

        Row(Modifier.fillMaxWidth()) {
            Stat("$sessions", "sessions", Modifier.weight(1f))
            Stat(fmtMinutes(total / days.size), "avg per day", Modifier.weight(1f))
            Stat("${stats.streak()}", "day streak", Modifier.weight(1f))
        }

        Row(
            Modifier.fillMaxWidth().height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(if (monthly) 2.dp else 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEach { d ->
                val h = if (d.minutes == 0) 3f else (d.minutes.toFloat() / maxMin * 108f).coerceAtLeast(6f)
                Box(Modifier.weight(1f).height(h.dp).background(if (d.minutes == 0) Line else Accent, RoundedCornerShape(2.dp)))
            }
        }
        if (!monthly) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                days.forEach { d ->
                    Text(
                        LocalDate.ofEpochDay(d.epochDay).dayOfWeek.name.take(1),
                        modifier = Modifier.weight(1f), color = Muted, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sunday report", fontSize = 15.sp)
                Text("A notification at 6 PM", color = Muted, fontSize = 12.sp)
            }
            Switch(
                checked = reminder,
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Bg),
                onCheckedChange = { on ->
                    if (!on) { WeeklyReminder.disable(ctx); reminder = false }
                    else if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { WeeklyReminder.enable(ctx); reminder = true }
                }
            )
        }
    }
}

@Composable
fun AwardsScreen(stats: StatsStore) {
    val totalMin = remember { stats.totalMinutes() }
    val hours = totalMin / 60
    val best = remember { stats.bestStreak() }
    val next = HOUR_AWARDS.firstOrNull { hours < it.hours }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fmtMinutes(totalMin), fontSize = 56.sp, fontWeight = FontWeight.Light)
                Text("total focus", color = Muted, fontSize = 13.sp)
                if (next != null) {
                    val prevHours = HOUR_AWARDS.lastOrNull { hours >= it.hours }?.hours ?: 0
                    val frac = ((totalMin / 60f - prevHours) / (next.hours - prevHours)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { frac }, color = Accent, trackColor = Line,
                        modifier = Modifier.fillMaxWidth().height(3.dp)
                    )
                    Text("Next: ${next.title} at ${next.hours}h", color = Muted, fontSize = 13.sp)
                }
            }
        }
        items(HOUR_AWARDS) { a -> AwardRow(a.title, "${a.hours} hours", hours >= a.hours) }
        item { Spacer(Modifier.height(12.dp)) }
        items(STREAK_AWARDS) { a -> AwardRow(a.title, "${a.hours} days in a row", best >= a.hours) }
    }
}

@Composable
private fun AwardRow(title: String, sub: String, earned: Boolean) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (earned) "★" else "☆", fontSize = 22.sp, color = if (earned) Accent else Muted.copy(alpha = 0.5f))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = if (earned) Fg else Muted)
            Text(sub, color = Muted.copy(alpha = if (earned) 1f else 0.6f), fontSize = 12.sp)
        }
        if (earned) Link("Share", color = Accent, size = 13) { ShareCard.share(ctx, "Award unlocked", title, sub) }
    }
}
