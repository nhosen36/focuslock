package com.focuslock.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDate

private fun fmtMinutes(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

@Composable
private fun BentoMetricTile(
    icon: String,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        padding = 14.dp
    ) {
        Text(icon, color = Accent, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Fg)
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header + Share Pill
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("MINDFUL ANALYTICS", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                Text("Your Focus Journey", fontFamily = SerifFont, fontSize = 26.sp, color = Fg)
            }
            SecondaryPillButton(
                text = "↗  Share Card",
                textColor = Accent,
                borderColor = Accent.copy(alpha = 0.4f),
                onClick = {
                    ShareCard.share(
                        ctx,
                        if (monthly) "My Month in Focus" else "My Week in Focus",
                        fmtMinutes(total),
                        "$sessions sessions  •  $active active days  •  ${stats.streak()}-day streak"
                    )
                }
            )
        }

        // Segmented Period Switcher
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(4.dp)
        ) {
            listOf(false to "Last 7 Days", true to "Last 30 Days").forEach { (isMonth, title) ->
                val sel = monthly == isMonth
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (sel) Accent else Color.Transparent)
                        .clickable { monthly = isMonth }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sel) Color(0xFF080C18) else Muted
                    )
                }
            }
        }

        // Hero Total Time + Bento Grid
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            borderColor = Accent.copy(alpha = 0.28f),
            padding = 18.dp
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        fmtMinutes(total),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                        color = Fg
                    )
                    Text(
                        if (monthly) "Focused in the last 30 days" else "Focused in the last 7 days",
                        color = Muted,
                        fontSize = 13.sp
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Accent.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "$active/${days.size} active days",
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BentoMetricTile("◎", "$sessions", "Sessions", Modifier.weight(1f))
            BentoMetricTile("◷", fmtMinutes(total / days.size), "Daily Avg", Modifier.weight(1f))
            BentoMetricTile("✦", "${stats.streak()}d", "Streak", Modifier.weight(1f))
        }

        // Activity Bar Chart Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            padding = 18.dp
        ) {
            Text(
                if (monthly) "30-Day Rhythm" else "Daily Focus Rhythm",
                color = Fg,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(116.dp),
                horizontalArrangement = Arrangement.spacedBy(if (monthly) 3.dp else 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                val curAccent = Accent
                days.forEach { d ->
                    val h = if (d.minutes == 0) 6f else (d.minutes.toFloat() / maxMin * 112f).coerceAtLeast(12f)
                    val barShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(h.dp)
                            .clip(barShape)
                            .background(
                                Brush.verticalGradient(
                                    if (d.minutes == 0) listOf(Line, Line)
                                    else listOf(curAccent, curAccent.copy(alpha = 0.45f))
                                )
                            )
                    )
                }
            }
            if (!monthly) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    days.forEach { d ->
                        Text(
                            LocalDate.ofEpochDay(d.epochDay).dayOfWeek.name.take(3).lowercase()
                                .replaceFirstChar { it.uppercase() },
                            modifier = Modifier.weight(1f),
                            color = Muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Weekly Sunday Report Toggle Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            padding = 16.dp
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Sunday Evening Reflection", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Fg)
                    Text("Receive your weekly focus summary every Sunday at 6 PM", color = Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = reminder,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Accent,
                        checkedThumbColor = Color(0xFF080C18)
                    ),
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
}

@Composable
fun AwardsScreen(stats: StatsStore) {
    val totalMin = remember { stats.totalMinutes() }
    val hours = totalMin / 60f
    val best = remember { stats.bestStreak() }
    val next = HOUR_AWARDS.firstOrNull { hours < it.hours }
    val earnedCount = remember { stats.earned().size }
    val totalAwards = HOUR_AWARDS.size + STREAK_AWARDS.size

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                Text("MILESTONES & BADGES", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
                Text("Celebrate Consistency", fontFamily = SerifFont, fontSize = 26.sp, color = Fg)
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                borderColor = Accent.copy(alpha = 0.35f),
                padding = 18.dp
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(fmtMinutes(totalMin), fontSize = 42.sp, fontWeight = FontWeight.Light, color = Fg)
                        Text("Total lifetime deep focus", color = Muted, fontSize = 13.sp)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Accent.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "✦  $earnedCount / $totalAwards Unlocked",
                            color = Accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (next != null) {
                    Spacer(Modifier.height(14.dp))
                    val prevHours = HOUR_AWARDS.lastOrNull { hours >= it.hours }?.hours ?: 0
                    val frac = ((hours - prevHours) / (next.hours - prevHours)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { frac },
                        color = Accent,
                        trackColor = Line,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Next milestone: ${next.title} (${next.hours}h)", color = Muted, fontSize = 12.sp)
                        Text("${(frac * 100).toInt()}%", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        item {
            Text(
                "Focus Hour Milestones",
                color = Fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        items(HOUR_AWARDS) { a ->
            val earned = hours >= a.hours
            val frac = (hours / a.hours.toFloat()).coerceIn(0f, 1f)
            AwardCard(
                icon = if (earned) "★" else "✧",
                title = a.title,
                sub = "${a.hours} hours of deep focus",
                earned = earned,
                progress = frac
            )
        }

        item {
            Text(
                "Consistency Streaks",
                color = Fg,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        items(STREAK_AWARDS) { a ->
            val earned = best >= a.hours
            val frac = (best.toFloat() / a.hours.toFloat()).coerceIn(0f, 1f)
            AwardCard(
                icon = if (earned) "✦" else "◇",
                title = a.title,
                sub = "${a.hours} consecutive days focused",
                earned = earned,
                progress = frac
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun AwardCard(
    icon: String,
    title: String,
    sub: String,
    earned: Boolean,
    progress: Float
) {
    val ctx = LocalContext.current
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        borderColor = if (earned) Accent.copy(alpha = 0.45f) else GlassBorder,
        padding = 14.dp
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (earned) Accent.copy(alpha = 0.22f) else Line)
                    .border(
                        1.dp,
                        if (earned) Accent else Color.White.copy(alpha = 0.08f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    icon,
                    fontSize = 20.sp,
                    color = if (earned) Accent else Muted.copy(alpha = 0.5f)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (earned) Fg else Muted
                )
                Text(
                    sub,
                    color = Muted.copy(alpha = if (earned) 0.9f else 0.65f),
                    fontSize = 12.sp
                )
                if (!earned && progress > 0f) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = Accent.copy(alpha = 0.7f),
                        trackColor = Line,
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (earned) {
                SecondaryPillButton(
                    text = "Share",
                    textColor = Accent,
                    borderColor = Accent.copy(alpha = 0.45f),
                    onClick = { ShareCard.share(ctx, "Milestone Unlocked", title, sub) }
                )
            } else {
                Text(
                    "${(progress * 100).toInt()}%",
                    color = Muted.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
