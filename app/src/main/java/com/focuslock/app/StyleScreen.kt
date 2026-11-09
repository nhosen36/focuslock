package com.focuslock.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StyleScreen(onPaywall: () -> Unit) {
    val ctx = LocalContext.current
    var hue by remember { mutableStateOf(250f) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ThemeState.setBackground(ctx, uri)
    }
    val billing = Services.billing

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Accent color", fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENT_PRESETS.forEach { c ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(c)
                            .then(if (c == Accent) Modifier.border(2.dp, Fg, CircleShape) else Modifier)
                            .clickable { ThemeState.setAccent(ctx, c) }
                    )
                }
            }
            Text("Or pick your own", color = Muted, fontSize = 12.sp)
            Slider(
                value = hue, onValueChange = { hue = it; ThemeState.setAccent(ctx, Color.hsv(it, 0.55f, 1f)) },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Line)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Background image", fontSize = 16.sp)
            Text("Pick a photo from your gallery. It stays on your phone.", color = Muted, fontSize = 12.sp)
            Row {
                Link("Choose photo", color = Accent) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (ThemeState.background != null) Link("Remove") { ThemeState.clearBackground(ctx) }
            }
            if (ThemeState.background != null) {
                Text("Darken", color = Muted, fontSize = 12.sp)
                Slider(
                    value = ThemeState.dim, onValueChange = { ThemeState.setDim(ctx, it) },
                    valueRange = 0.2f..0.9f,
                    colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Line)
                )
            }
        }

        // Plan section: honest about what the user has and what Pro costs.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your plan", fontSize = 16.sp)
            when {
                billing.subscribed -> {
                    Text("Pro. Unlimited focus time, no ads. Thank you.", color = Muted, fontSize = 13.sp)
                    Link("Manage subscription", size = 13) {
                        ctx.openUrl("https://play.google.com/store/account/subscriptions")
                    }
                }
                !RemoteFlags.monetization ->
                    Text("Free for everyone right now.", color = Muted, fontSize = 13.sp)
                else -> {
                    val left = Entitlement.freeMinutesLeft(StatsStore(ctx))
                    Text(
                        "Free: $left of ${RemoteFlags.freeMinutes} focus minutes left this month, with small ads on Stats, Awards and Style. Never during a session.",
                        color = Muted, fontSize = 13.sp
                    )
                    Link("Go Pro: unlimited and no ads", color = Accent, size = 15, onClick = onPaywall)
                }
            }
        }

        Column {
            Link("Privacy policy", size = 13) { ctx.openUrl(RemoteFlags.PRIVACY_URL) }
            Text("FocusLock 1.0", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
        }
    }
}
