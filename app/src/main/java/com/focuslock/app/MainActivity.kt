package com.focuslock.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    private lateinit var store: SessionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is dark-only, so always use light status and navigation bar icons.
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
        if (::store.isInitialized) {
            store.allowingDialerExit = false
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
        bringBackIfLocked()
    }

    override fun onStop() {
        super.onStop()
        bringBackIfLocked()
    }

    private fun bringBackIfLocked() {
        if (::store.isInitialized && store.isActive && !store.allowingDialerExit) {
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

    fun setImmersiveSessionMode(active: Boolean) {
        try {
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
        text, color = color, fontSize = size.sp,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun App(store: SessionStore) {
    var active by remember { mutableStateOf(store.run { reconcile(); isActive }) }
    var challenge by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var showPaywall by remember { mutableStateOf(false) }
    var paywallOut by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf<List<String>>(emptyList()) }
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    var onboarded by remember {
        mutableStateOf(appCtx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("onboarded", false))
    }
    val music = remember { MusicController(appCtx) }
    DisposableEffect(Unit) { onDispose { music.release() } }

    fun finish() {
        val act = ctx.findActivity() as? MainActivity
        HomeRole.stopPinning(act)
        act?.setImmersiveSessionMode(false)
        val before = store.stats.earned()
        store.end()
        music.stop()
        unlocked = (store.stats.earned() - before).toList()
        active = false; challenge = false; tab = 0
    }

    LaunchedEffect(active) {
        while (active) {
            delay(500)
            store.enforceIfActive()
            if (!store.isActive) finish()
        }
    }

    if (unlocked.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { unlocked = emptyList() },
            containerColor = Line,
            confirmButton = { TextButton(onClick = { unlocked = emptyList() }) { Text("Nice", color = Accent) } },
            dismissButton = {
                TextButton(onClick = {
                    ShareCard.share(appCtx, "Award unlocked", unlocked.joinToString(" + "), "Earned with FocusLock")
                    unlocked = emptyList()
                }) { Text("Share", color = Muted) }
            },
            title = { Text("Award unlocked") },
            text = { Text(unlocked.joinToString("\n") { "★ $it" }) }
        )
    }

    Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        when {
            !onboarded -> Onboarding(onDone = {
                appCtx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("onboarded", true).apply()
                onboarded = true
            })
            active && challenge -> Challenge(onSolved = { finish() }, onCancel = { challenge = false })
            active -> FocusScreen(store, music, onQuit = { challenge = true })
            showPaywall -> Paywall(outOfMinutes = paywallOut, onClose = { showPaywall = false })
            else -> Column {
                Row {
                    listOf("Focus", "Stats", "Awards", "Apps", "Style").forEachIndexed { i, t ->
                        Link(t, color = if (tab == i) Fg else Muted, size = 14) { tab = i }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(Modifier.weight(1f)) {
                    when (tab) {
                        0 -> StartScreen(
                            store = store,
                            onStarted = {
                                active = true
                                val act = ctx.findActivity() as? MainActivity
                                act?.setImmersiveSessionMode(true)
                                HomeRole.startPinning(act)
                            },
                            onPaywall = { paywallOut = true; showPaywall = true }
                        )
                        1 -> StatsScreen(store.stats)
                        2 -> AwardsScreen(store.stats)
                        3 -> AppList()
                        else -> StyleScreen(onPaywall = { paywallOut = false; showPaywall = true })
                    }
                }
                // Ads (free users only) appear on Stats, Awards and Style. Never on Focus, Apps or in a session.
                if (tab == 1 || tab == 2 || tab == 4) AdArea(onUpgrade = { paywallOut = false; showPaywall = true })
            }
        }
    }
}

@Composable
private fun StartScreen(store: SessionStore, onStarted: () -> Unit, onPaywall: () -> Unit) {
    val ctx = LocalContext.current
    val nm = ctx.getSystemService(NotificationManager::class.java)
    var minutes by remember { mutableStateOf(25) }
    var dndOk by remember { mutableStateOf(nm.isNotificationPolicyAccessGranted) }
    var homeOk by remember { mutableStateOf(HomeRole.isHeld(ctx)) }
    var showSetupGate by remember { mutableStateOf(false) }

    val homeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val held = HomeRole.isHeld(ctx)
        homeOk = held
        if (!held) {
            // Many OEM phones (Samsung, Honor, Xiaomi, Vivo, Oppo) require picking FocusLock in Default Home Settings.
            HomeRole.openHomeSettings(ctx)
        }
    }

    fun requestHomeRole() {
        try {
            homeLauncher.launch(HomeRole.requestIntent(ctx))
        } catch (_: Exception) {
            HomeRole.openHomeSettings(ctx)
        }
    }

    fun requestDndAccess() {
        HomeRole.openDndSettings(ctx)
    }

    LaunchedEffect(Unit) {
        while (true) {
            dndOk = nm.isNotificationPolicyAccessGranted
            homeOk = HomeRole.isHeld(ctx)
            if (dndOk && homeOk && showSetupGate) {
                showSetupGate = false
            }
            delay(500)
        }
    }

    if (showSetupGate) {
        AlertDialog(
            onDismissRequest = { showSetupGate = false },
            containerColor = Line,
            title = { Text("Enable Lock Protection First", color = Fg, fontSize = 20.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "To block notifications and lock the screen during a session, enable both steps below:",
                        color = Muted, fontSize = 14.sp
                    )
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bg)
                            .clickable { if (!dndOk) requestDndAccess() }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("1. Block Notifications", color = Fg, fontWeight = FontWeight.Medium)
                            Text(
                                if (dndOk) "Do Not Disturb access granted" else "Tap to allow FocusLock in Do Not Disturb settings",
                                color = if (dndOk) Accent else Muted, fontSize = 12.sp
                            )
                        }
                        Text(if (dndOk) "✓ Done" else "Enable ›", color = Accent, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Bg)
                            .clickable { if (!homeOk) requestHomeRole() }
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("2. Lock Home Screen", color = Fg, fontWeight = FontWeight.Medium)
                            Text(
                                if (homeOk) "FocusLock is set as your Home app" else "Tap and select FocusLock as Default Home app",
                                color = if (homeOk) Accent else Muted, fontSize = 12.sp
                            )
                        }
                        Text(if (homeOk) "✓ Done" else "Select ›", color = Accent, fontWeight = FontWeight.Medium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!dndOk) requestDndAccess()
                    else if (!homeOk) requestHomeRole()
                    else showSetupGate = false
                }) {
                    Text(
                        when {
                            !dndOk -> "1. Grant DND Access"
                            !homeOk -> "2. Set Home App"
                            else -> "Done"
                        },
                        color = Accent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetupGate = false }) { Text("Close", color = Muted) }
            }
        )
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Link("−", size = 28) { minutes = (minutes - 5).coerceAtLeast(5) }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(140.dp)) {
                Text("$minutes", fontSize = 80.sp, fontWeight = FontWeight.Light)
                Text("minutes", color = Muted, fontSize = 14.sp)
            }
            Link("+", size = 28) { minutes = (minutes + 5).coerceAtMost(240) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(15, 25, 50, 90).forEach { m -> Link("$m", color = if (m == minutes) Accent else Muted) { minutes = m } }
        }

        Spacer(Modifier.height(32.dp))
        val limited = Entitlement.limitsApply()
        val left = if (limited) Entitlement.freeMinutesLeft(store.stats) else Int.MAX_VALUE
        val readyToLock = dndOk && homeOk
        Box(
            Modifier.size(132.dp).clip(CircleShape).background(Accent)
                .clickable {
                    if (!readyToLock) {
                        showSetupGate = true
                    } else if (limited && left <= 0) {
                        onPaywall()
                    } else {
                        store.start(minOf(minutes, left))
                        onStarted()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (readyToLock) "Start" else "Setup\nLock",
                color = Bg, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
            )
        }

        // Free-plan meter: one quiet line, only for free users.
        if (limited) {
            Spacer(Modifier.height(8.dp))
            if (left > 0) {
                Text(
                    "$left of ${RemoteFlags.freeMinutes} free minutes left this month" +
                        if (minutes > left) ". This session will be $left min." else "",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center
                )
            } else {
                Text("No free minutes left this month", color = Muted, fontSize = 13.sp)
                Link("Go Pro to keep focusing", color = Accent, size = 14, onClick = onPaywall)
            }
        }

        Spacer(Modifier.weight(1f))

        // Prominent setup card whenever either permission is missing.
        if (!readyToLock) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Line)
                    .border(1.dp, Accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Required for Screen & Notification Lock:", color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (!dndOk) {
                    Link("1. Allow Do Not Disturb (blocks notifications)  ›", color = Accent, size = 14) {
                        requestDndAccess()
                    }
                } else {
                    Text("✓ 1. Notification blocking ready", color = Muted, fontSize = 13.sp)
                }
                if (!homeOk) {
                    Link("2. Set FocusLock as Default Home app (locks screen)  ›", color = Accent, size = 14) {
                        requestHomeRole()
                    }
                } else {
                    Text("✓ 2. Home screen lock ready", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun AppList() {
    val ctx = LocalContext.current
    val isHome = remember { mutableStateOf(HomeRole.isHeld(ctx)) }
    val apps = remember {
        val pm = ctx.packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(q, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { it.loadLabel(pm).toString() to pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
            .filter { it.second != null }
            .sortedBy { it.first.lowercase() }
    }
    Column(Modifier.fillMaxSize()) {
        if (isHome.value) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Done studying?", color = Muted, fontSize = 13.sp)
                Link("Switch back to normal Home launcher ›", color = Accent, size = 13) {
                    HomeRole.openHomeSettings(ctx)
                }
            }
        }
        LazyColumn(Modifier.weight(1f)) {
            items(apps) { (label, intent) ->
                Text(
                    label, fontSize = 20.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.fillMaxWidth().clickable { ctx.safeStart(intent!!) }.padding(vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun FocusScreen(store: SessionStore, music: MusicController, onQuit: () -> Unit) {
    val ctx = LocalContext.current
    var left by remember { mutableStateOf(store.endTimeMs - System.currentTimeMillis()) }
    var pinned by remember { mutableStateOf(HomeRole.isScreenPinned(ctx)) }

    LaunchedEffect(Unit) {
        HomeRole.startPinning(ctx.findActivity())
        var prevPinned = HomeRole.isScreenPinned(ctx)
        var lastTick = System.currentTimeMillis()
        while (true) {
            delay(200)
            val now = System.currentTimeMillis()
            val dt = (now - lastTick).coerceAtLeast(0L)
            lastTick = now

            val curPinned = HomeRole.isScreenPinned(ctx)
            // If the user just held Back + Recents to unpin while in the session, immediately re-request pinning!
            if (prevPinned && !curPinned && !store.allowingDialerExit) {
                HomeRole.startPinning(ctx.findActivity())
            }
            // Pause the countdown timer whenever the screen is unpinned so unpinning never earns focus time.
            if (!curPinned && !store.allowingDialerExit) {
                store.shiftEndTime(dt)
            }
            prevPinned = curPinned
            pinned = curPinned
            left = store.endTimeMs - System.currentTimeMillis()
        }
    }
    BackHandler(true) { /* swallow back during a session */ }

    fun launchAllowedOutsideApp(intent: Intent) {
        store.allowingDialerExit = true
        HomeRole.stopPinning(ctx.findActivity())
        ctx.safeStart(intent)
    }

    val s = (left / 1000).coerceAtLeast(0)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!pinned) {
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Line)
                    .border(1.dp, Danger, RoundedCornerShape(14.dp))
                    .clickable { HomeRole.startPinning(ctx.findActivity()) }
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "TIMER PAUSED — SCREEN UNPINNED",
                    color = Danger, fontSize = 14.sp, fontWeight = FontWeight.Medium
                )
                Text(
                    "Your timer only counts down while the screen is pinned. Tap here and choose 'Got it' / 'Allow' to lock the screen.",
                    color = Fg, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Link("Lock Screen Now  ›", color = Accent, size = 15) {
                    HomeRole.startPinning(ctx.findActivity())
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "%02d:%02d".format(s / 60, s % 60),
            fontSize = 88.sp,
            fontWeight = FontWeight.Thin,
            color = if (pinned) Fg else Muted
        )
        Text(
            if (pinned) "screen locked  •  notifications silenced" else "timer paused until screen is pinned",
            color = if (pinned) Muted else Danger,
            fontSize = 14.sp
        )

        if (music.hasMusic) {
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Line).clickable { music.toggle() },
                contentAlignment = Alignment.Center
            ) { Text(if (music.isPlaying) "❚❚" else "▶", fontSize = 26.sp, color = Fg) }
        }
        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Link("Phone", size = 16) { launchAllowedOutsideApp(Intent(Intent.ACTION_DIAL)) }
            Link("Contacts", size = 16) {
                launchAllowedOutsideApp(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS))
            }
            Link("Emergency", color = Danger, size = 16) {
                launchAllowedOutsideApp(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${emergencyNumber()}")))
            }
        }
        Link("End early", color = Muted.copy(alpha = 0.7f), size = 14, onClick = onQuit)
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
        Text("${index + 1} of $total", color = Muted, fontSize = 14.sp)
        Text("$a × $b", fontSize = 56.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = answer, onValueChange = { answer = it.filter(Char::isDigit); wrong = false },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, isError = wrong, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, textAlign = TextAlign.Center),
            modifier = Modifier.width(180.dp)
        )
        Text(if (wrong) "Wrong. Starting over." else "Solve all $total to end early.", color = if (wrong) Danger else Muted, fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        Link("Submit", color = Accent, size = 18) {
            if (answer.toIntOrNull() == a * b) {
                if (index + 1 == total) onSolved()
                else { index++; a = Random.nextInt(12, 40); b = Random.nextInt(12, 40); answer = "" }
            } else {
                wrong = true; index = 0; answer = ""
                a = Random.nextInt(12, 40); b = Random.nextInt(12, 40)
            }
        }
        Spacer(Modifier.weight(1f))
        Link("Back to focus", onClick = onCancel)
    }
}
