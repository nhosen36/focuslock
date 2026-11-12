package com.focuslock.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private lateinit var store: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        store = SessionStore(this)
        ThemeState.load(this)
        Services.billing = BillingManager(applicationContext).also { it.connect() }
        RemoteFlags.load(this)
        RemoteFlags.refresh(this)
        if (store.isActive) {
            setImmersiveSessionMode(true)
            store.enforceIfActive()
        }
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent, background = Bg, surface = Bg, onSurface = Fg,
                    onBackground = Fg, error = Danger, surfaceVariant = Line
                )
            ) {
                ThemedBackground {
                    Surface(color = Color.Transparent, contentColor = Fg, modifier = Modifier.fillMaxSize()) { App(store) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        HomeRole.clearReturnToLockNotification(this)
        if (::store.isInitialized) {
            if (store.isActive) {
                store.enforceIfActive()
                setImmersiveSessionMode(true)
            } else {
                setImmersiveSessionMode(false)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::store.isInitialized && store.isActive) {
            setImmersiveSessionMode(true)
            store.enforceIfActive()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        reassertLockIfActive()
    }

    override fun onStop() {
        super.onStop()
        reassertLockIfActive()
    }

    private fun reassertLockIfActive() {
        if (::store.isInitialized && store.isActive && !isChangingConfigurations) {
            LockOverlay.reassertOnTop(applicationContext)
            if (HomeRole.canDrawOverlays(this)) {
                val i = Intent(this, MainActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                try {
                    startActivity(i)
                } catch (_: Exception) {}
            }
        }
    }

    fun setImmersiveSessionMode(active: Boolean) {
        try {
            if (active) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (active) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        } catch (_: Exception) {}
    }
}

fun Context.safeStart(intent: Intent) {
    try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

@Composable
fun Link(text: String, color: Color = Muted, size: Int = 15, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = size.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

private fun calmGreeting(): String {
    val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hr < 12 -> "Good morning"
        hr < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun App(store: SessionStore) {
    var active by remember { mutableStateOf(store.run { reconcile(); isActive }) }
    var challenge by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var showPaywall by remember { mutableStateOf(false) }
    var paywallOut by remember { mutableStateOf(false) }
    var showBreathing by remember { mutableStateOf(false) }
    var lastCompletedMinutes by remember { mutableStateOf(0) }
    var unlocked by remember { mutableStateOf<List<String>>(emptyList()) }
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    var onboarded by remember {
        mutableStateOf(appCtx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("onboarded", false))
    }
    val music = remember { MusicController(appCtx) }
    DisposableEffect(Unit) { onDispose { music.release() } }

    fun finish() {
        LockOverlay.hide(appCtx)
        val act = ctx.findActivity() as? MainActivity
        HomeRole.stopPinning(act)
        HomeRole.clearReturnToLockNotification(ctx)
        act?.setImmersiveSessionMode(false)
        val before = store.stats.earned()
        val focusedMins = store.end()
        music.stop()
        unlocked = (store.stats.earned() - before).toList()
        lastCompletedMinutes = focusedMins
        showBreathing = true
        active = false; challenge = false; tab = 0
    }

    LaunchedEffect(active) {
        if (active) {
            LockOverlay.show(appCtx, store, music) { finish() }
        } else {
            LockOverlay.hide(appCtx)
        }
        while (active) {
            delay(500)
            store.enforceIfActive()
            if (!LockOverlay.isShowing && HomeRole.canDrawOverlays(appCtx) && store.isActive) {
                LockOverlay.show(appCtx, store, music) { finish() }
            }
            if (!store.isActive) finish()
        }
    }

    if (unlocked.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { unlocked = emptyList() },
            containerColor = BgMid,
            confirmButton = { TextButton(onClick = { unlocked = emptyList() }) { Text("Continue", color = Accent) } },
            dismissButton = {
                TextButton(onClick = {
                    ShareCard.share(appCtx, "Milestone Unlocked", unlocked.joinToString(" + "), "Earned with FocusLock")
                    unlocked = emptyList()
                }) { Text("Share Story", color = Fg) }
            },
            title = { Text("Milestone Unlocked", fontFamily = SerifFont, fontSize = 22.sp, color = Fg) },
            text = { Text(unlocked.joinToString("\n") { "✦  $it" }, color = Accent, fontSize = 16.sp) }
        )
    }

    Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
        when {
            !onboarded -> Onboarding(onDone = {
                appCtx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
                onboarded = true
            })
            active && challenge -> Challenge(onSolved = { finish() }, onCancel = { challenge = false })
            active -> FocusScreen(store, music, onQuit = { challenge = true })
            showBreathing -> BreathingScreen(
                completedMinutes = lastCompletedMinutes,
                music = music,
                onDismiss = { showBreathing = false }
            )
            showPaywall -> Paywall(outOfMinutes = paywallOut, onClose = { showPaywall = false })
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> StartScreen(
                            store = store,
                            music = music,
                            onStarted = {
                                active = true
                                val act = ctx.findActivity() as? MainActivity
                                act?.setImmersiveSessionMode(true)
                                LockOverlay.show(appCtx, store, music) { finish() }
                            },
                            onPaywall = { paywallOut = true; showPaywall = true },
                            onOpenBreathing = {
                                lastCompletedMinutes = 0
                                showBreathing = true
                            }
                        )
                        1 -> StatsScreen(store.stats)
                        2 -> AwardsScreen(store.stats)
                        else -> StyleScreen(onPaywall = { paywallOut = false; showPaywall = true })
                    }
                }

                // Ads (free users only) appear on Stats, Awards and Style. Never on Focus or in a session.
                if (tab == 1 || tab == 2 || tab == 3) {
                    Spacer(Modifier.height(6.dp))
                    AdArea(onUpgrade = { paywallOut = false; showPaywall = true })
                }

                Spacer(Modifier.height(10.dp))
                CalmBottomNavBar(selectedTab = tab, onSelectTab = { tab = it })
            }
        }
    }
}

@Composable
private fun CalmBottomNavBar(selectedTab: Int, onSelectTab: (Int) -> Unit) {
    val items = listOf(
        "◎" to "Focus",
        "▤" to "Stats",
        "✦" to "Awards",
        "◐" to "Style"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xFF121A30).copy(alpha = 0.88f))
            .border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { i, (icon, label) ->
            val selected = selectedTab == i
            val pillShape = RoundedCornerShape(24.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(pillShape)
                    .background(if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent)
                    .then(
                        if (selected) Modifier.border(1.dp, Accent.copy(alpha = 0.42f), pillShape)
                        else Modifier
                    )
                    .clickable { onSelectTab(i) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = icon,
                    fontSize = 15.sp,
                    color = if (selected) Accent else Muted
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Fg else Muted
                )
            }
        }
    }
}

@Composable
private fun StartScreen(
    store: SessionStore,
    music: MusicController,
    onStarted: () -> Unit,
    onPaywall: () -> Unit,
    onOpenBreathing: () -> Unit
) {
    val ctx = LocalContext.current
    val nm = ctx.getSystemService(NotificationManager::class.java)
    var minutes by remember { mutableStateOf(25) }
    var dndOk by remember { mutableStateOf(nm.isNotificationPolicyAccessGranted) }
    var overlayOk by remember { mutableStateOf(HomeRole.canDrawOverlays(ctx)) }
    var showSetupGate by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "breath")
    val pulse by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    fun requestDndAccess() = HomeRole.openDndSettings(ctx)
    fun requestOverlayAccess() = HomeRole.openOverlaySettings(ctx)

    LaunchedEffect(Unit) {
        while (true) {
            dndOk = nm.isNotificationPolicyAccessGranted
            overlayOk = HomeRole.canDrawOverlays(ctx)
            if (dndOk && overlayOk && showSetupGate) {
                showSetupGate = false
            }
            delay(500)
        }
    }

    if (showSetupGate) {
        AlertDialog(
            onDismissRequest = { showSetupGate = false },
            containerColor = BgMid,
            title = {
                Text(
                    "Enable Deep Focus Lock",
                    fontFamily = SerifFont,
                    color = Fg,
                    fontSize = 22.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Grant these two one-time permissions so FocusLock can silence notifications and stay locked on screen during your session:",
                        color = Muted, fontSize = 14.sp
                    )
                    GlassCard(
                        padding = 14.dp,
                        cornerRadius = 18.dp,
                        borderColor = if (dndOk) Accent.copy(alpha = 0.45f) else GlassBorder,
                        onClick = { if (!dndOk) requestDndAccess() }
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("1. Do Not Disturb Access", color = Fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    if (dndOk) "Ready — Notifications will be silenced" else "Find 'FocusLock' in list and switch ON",
                                    color = if (dndOk) Accent else Muted, fontSize = 12.sp
                                )
                            }
                            Text(if (dndOk) "✓" else "Enable ›", color = Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    GlassCard(
                        padding = 14.dp,
                        cornerRadius = 18.dp,
                        borderColor = if (overlayOk) Accent.copy(alpha = 0.45f) else GlassBorder,
                        onClick = { if (!overlayOk) requestOverlayAccess() }
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("2. Display Over Other Apps", color = Fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                Text(
                                    if (overlayOk) "Ready — Home button is locked" else "Keeps lock screen on top during sessions",
                                    color = if (overlayOk) Accent else Muted, fontSize = 12.sp
                                )
                            }
                            Text(if (overlayOk) "✓" else "Enable ›", color = Accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        !dndOk -> requestDndAccess()
                        !overlayOk -> requestOverlayAccess()
                        else -> showSetupGate = false
                    }
                }) {
                    Text(
                        when {
                            !dndOk -> "1. Open DND Settings"
                            !overlayOk -> "2. Allow Appear on Top"
                            else -> "Done"
                        },
                        color = Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupGate = false }) { Text("Close", color = Muted) }
            }
        )
    }

    val streakDays = remember { store.stats.streak() }
    val totalHrs = remember { store.stats.totalMinutes() / 60 }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Greeting Header
        Column(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                calmGreeting().uppercase(),
                color = Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.4.sp
            )
            Text(
                "Find your focus.",
                fontFamily = SerifFont,
                fontSize = 26.sp,
                color = Fg
            )
        }

        Spacer(Modifier.height(12.dp))

        // Aligned Streak & Breathing Quick-Action Row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassSurface)
                    .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "✦  $streakDays day streak  •  ${totalHrs}h",
                    color = Fg,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlassSurface)
                    .border(1.dp, Accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .clickable { onOpenBreathing() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "◯  Breathing Exercise",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Breathing Halo Timer Dial
        val curAccent = Accent
        Box(
            modifier = Modifier.size(256.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.42f

                // Outer breathing aura
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(curAccent.copy(alpha = 0.22f), Color.Transparent),
                        center = center,
                        radius = baseRadius * 1.18f * pulse
                    ),
                    radius = baseRadius * 1.18f * pulse,
                    center = center
                )

                // Track ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = baseRadius,
                    center = center,
                    style = Stroke(width = 6.dp.toPx())
                )

                // Progress arc (up to 120m)
                val sweep = (minutes.coerceAtMost(120) / 120f) * 360f
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(curAccent.copy(alpha = 0.45f), curAccent, curAccent)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - baseRadius, center.y - baseRadius),
                    size = Size(baseRadius * 2f, baseRadius * 2f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { minutes = (minutes - 5).coerceAtLeast(5) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("−", fontSize = 22.sp, color = Fg, fontWeight = FontWeight.Light)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$minutes",
                        fontSize = 68.sp,
                        fontWeight = FontWeight.Light,
                        color = Fg
                    )
                    Text(
                        "MINUTES",
                        color = Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp
                    )
                }

                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(GlassSurface)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { minutes = (minutes + 5).coerceAtMost(240) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", fontSize = 22.sp, color = Fg, fontWeight = FontWeight.Light)
                }
            }
        }

        // Duration Preset Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 6.dp)
        ) {
            listOf(15, 25, 45, 60, 90).forEach { m ->
                val selected = m == minutes
                val chipShape = RoundedCornerShape(20.dp)
                Box(
                    Modifier
                        .clip(chipShape)
                        .background(if (selected) Accent.copy(alpha = 0.22f) else GlassSurface)
                        .border(
                            1.dp,
                            if (selected) Accent else GlassBorder,
                            chipShape
                        )
                        .clickable { minutes = m }
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${m}m",
                        color = if (selected) Accent else Muted,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Ambient Soundscape Preview Card
        if (music.hasMusic) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                padding = 14.dp,
                borderColor = if (music.isPlaying) Accent.copy(alpha = 0.5f) else GlassBorder,
                onClick = { music.toggle() }
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (music.isPlaying) "❚❚" else "▶", color = Accent, fontSize = 15.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Ambient Study Soundscape", color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (music.isPlaying) "Playing calm background audio" else "Tap to play serene focus music",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Text(
                        if (music.isPlaying) "Playing" else "Preview",
                        color = Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        // Primary Start CTA Button
        val limited = Entitlement.limitsApply()
        val left = if (limited) Entitlement.freeMinutesLeft(store.stats) else Int.MAX_VALUE
        val readyToLock = dndOk && overlayOk

        PrimaryPillButton(
            text = if (readyToLock) "Begin Focus Session" else "Setup Lock Protection",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!readyToLock) {
                    showSetupGate = true
                } else if (limited && left <= 0) {
                    onPaywall()
                } else {
                    store.start(minOf(minutes, left))
                    onStarted()
                }
            }
        )

        // Free-plan meter
        if (limited) {
            Spacer(Modifier.height(10.dp))
            if (left > 0) {
                Text(
                    "$left of ${RemoteFlags.freeMinutes} free minutes remaining this month" +
                        if (minutes > left) "  •  Session capped at ${left}m" else "",
                    color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Monthly free minutes used  •  ", color = Muted, fontSize = 12.sp)
                    Link("Unlock Unlimited Pro ›", color = Accent, size = 13, onClick = onPaywall)
                }
            }
        }

        // Setup checklist card if either permission is not yet enabled
        if (!readyToLock) {
            Spacer(Modifier.height(14.dp))
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                borderColor = Accent.copy(alpha = 0.4f),
                padding = 14.dp
            ) {
                Text("One-Time Lock Setup", color = Fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (!dndOk) {
                    Link("1. Allow Do Not Disturb (silences notifications)  ›", color = Accent, size = 13) {
                        requestDndAccess()
                    }
                } else {
                    Text("✓  1. Notification silencing ready", color = Muted, fontSize = 12.sp)
                }
                if (!overlayOk) {
                    Link("2. Allow Display over other apps (locks Home button)  ›", color = Accent, size = 13) {
                        requestOverlayAccess()
                    }
                } else {
                    Text("✓  2. Screen overlay lock ready", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun FocusScreen(store: SessionStore, music: MusicController, onQuit: () -> Unit) {
    val ctx = LocalContext.current
    var left by remember { mutableStateOf(store.endTimeMs - System.currentTimeMillis()) }
    var showEmergencyConfirm by remember { mutableStateOf(false) }

    val infinite = rememberInfiniteTransition(label = "session_breath")
    val pulse by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "session_pulse"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            left = store.endTimeMs - System.currentTimeMillis()
        }
    }
    BackHandler(true) { /* swallow back during a session */ }

    if (showEmergencyConfirm) {
        val num = emergencyNumber()
        AlertDialog(
            onDismissRequest = { showEmergencyConfirm = false },
            containerColor = BgMid,
            title = { Text("Emergency Call ($num)", fontFamily = SerifFont, color = Danger, fontSize = 22.sp) },
            text = {
                Text(
                    "Do you need to dial emergency services ($num)?\n\nFor normal phone use, tap Cancel and use 'End early' (solve 5 math problems) to unlock your session.",
                    color = Fg, fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showEmergencyConfirm = false
                    LockOverlay.hide(ctx)
                    ctx.safeStart(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")))
                }) { Text("Dial $num", color = Danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyConfirm = false }) { Text("Cancel", color = Accent) }
            }
        )
    }

    val s = (left / 1000).coerceAtLeast(0)
    val curAccent = Accent
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text("DEEP FOCUS IN PROGRESS", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Spacer(Modifier.height(6.dp))
        Text("Stay present. Distractions are silenced.", fontFamily = SerifFont, color = Muted, fontSize = 16.sp)

        Spacer(Modifier.weight(1f))

        Box(Modifier.size(270.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val r = size.minDimension * 0.42f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(curAccent.copy(alpha = 0.24f), Color.Transparent),
                        center = center,
                        radius = r * 1.25f * pulse
                    ),
                    radius = r * 1.25f * pulse,
                    center = center
                )
                drawCircle(
                    color = curAccent.copy(alpha = 0.32f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "%02d:%02d".format(s / 60, s % 60),
                    fontSize = 76.sp,
                    fontWeight = FontWeight.Light,
                    color = Fg
                )
                Text(
                    "REMAINING",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.sp
                )
            }
        }

        if (music.hasMusic) {
            Spacer(Modifier.height(28.dp))
            SecondaryPillButton(
                text = if (music.isPlaying) "❚❚  Pause Ambient Sound" else "▶  Play Ambient Sound",
                textColor = if (music.isPlaying) Accent else Fg,
                borderColor = if (music.isPlaying) Accent.copy(alpha = 0.5f) else GlassBorder,
                onClick = { music.toggle() }
            )
        }

        Spacer(Modifier.weight(1f))

        SecondaryPillButton(
            text = "End Early (Solve 5 Math Challenges)",
            textColor = Fg,
            onClick = onQuit
        )
        Spacer(Modifier.height(8.dp))
        Link("Emergency Call (${emergencyNumber()})", color = Danger.copy(alpha = 0.85f), size = 13) {
            showEmergencyConfirm = true
        }
    }
}

@Composable
private fun Challenge(onSolved: () -> Unit, onCancel: () -> Unit) {
    val total = 5
    var index by remember { mutableStateOf(0) }
    var a by remember { mutableStateOf(Random.nextInt(12, 40)) }
    var b by remember { mutableStateOf(Random.nextInt(12, 40)) }
    var answer by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    BackHandler(true) { onCancel() }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Text("MIND CHECK  •  ${index + 1} OF $total", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
        Spacer(Modifier.height(8.dp))
        Text("$a × $b", fontSize = 54.sp, fontWeight = FontWeight.Light, color = Fg)
        Spacer(Modifier.height(14.dp))

        Box(
            Modifier
                .width(200.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(1.dp, if (wrong) Danger else Accent.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (answer.isEmpty()) " " else answer,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Fg
            )
        }

        Text(
            if (wrong) "Incorrect — restarting from problem 1 of $total" else "Solve all $total problems to end your session early",
            color = if (wrong) Danger else Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 16.dp)
        )

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "✓")
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { k ->
                        val isSubmit = k == "✓"
                        Box(
                            Modifier
                                .size(width = 82.dp, height = 56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSubmit) Accent else GlassSurface)
                                .border(1.dp, if (isSubmit) Accent else GlassBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    when (k) {
                                        "⌫" -> {
                                            if (answer.isNotEmpty()) answer = answer.dropLast(1)
                                            wrong = false
                                        }
                                        "✓" -> {
                                            if (answer.toIntOrNull() == a * b) {
                                                if (index + 1 == total) onSolved()
                                                else {
                                                    index++
                                                    a = Random.nextInt(12, 40)
                                                    b = Random.nextInt(12, 40)
                                                    answer = ""
                                                    wrong = false
                                                }
                                            } else {
                                                wrong = true
                                                index = 0
                                                answer = ""
                                                a = Random.nextInt(12, 40)
                                                b = Random.nextInt(12, 40)
                                            }
                                        }
                                        else -> {
                                            if (answer.length < 6) {
                                                answer += k
                                                wrong = false
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                k,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSubmit) Color(0xFF080C18) else Fg
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))
        SecondaryPillButton(text = "Return to Focus Session", onClick = onCancel)
    }
}
