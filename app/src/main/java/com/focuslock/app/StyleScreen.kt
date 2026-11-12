package com.focuslock.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StyleScreen(onPaywall: () -> Unit) {
    val ctx = LocalContext.current
    var hue by remember { mutableStateOf(200f) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ThemeState.setBackground(ctx, uri)
    }
    val billing = Services.billing

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.padding(top = 6.dp)) {
            Text("ATMOSPHERE & MEMBERSHIP", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp)
            Text("Personalize Your Sanctuary", fontFamily = SerifFont, fontSize = 26.sp, color = Fg)
        }

        // Atmosphere Color Themes Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            padding = 18.dp
        ) {
            Text("Atmosphere Glow", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Fg)
            Text("Choose the ambient light accent across your focus space", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ACCENT_PRESETS_NAMED.forEach { preset ->
                    val isSelected = preset.color == Accent
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) preset.color.copy(alpha = 0.16f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isSelected) preset.color else GlassBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { ThemeState.setAccent(ctx, preset.color) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(preset.color)
                                .then(if (isSelected) Modifier.border(2.dp, Fg, CircleShape) else Modifier)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            preset.name,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Fg else Muted
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Custom Hue Spectrum", color = Muted, fontSize = 12.sp)
            Slider(
                value = hue,
                onValueChange = { hue = it; ThemeState.setAccent(ctx, Color.hsv(it, 0.52f, 1f)) },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Line
                )
            )
        }

        // Gallery Backdrop Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            padding = 18.dp
        ) {
            Text("Custom Sanctuary Backdrop", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Fg)
            Text("Set a serene photo from your gallery. Stored privately on your device.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryPillButton(
                    text = "Choose Gallery Photo",
                    textColor = Accent,
                    borderColor = Accent.copy(alpha = 0.45f),
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                if (ThemeState.background != null) {
                    SecondaryPillButton(
                        text = "Reset Default",
                        textColor = Muted,
                        onClick = { ThemeState.clearBackground(ctx) }
                    )
                }
            }
            if (ThemeState.background != null) {
                Spacer(Modifier.height(12.dp))
                Text("Twilight Dimming", color = Muted, fontSize = 12.sp)
                Slider(
                    value = ThemeState.dim,
                    onValueChange = { ThemeState.setDim(ctx, it) },
                    valueRange = 0.25f..0.9f,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Line
                    )
                )
            }
        }

        // Membership Status Card
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            borderColor = Accent.copy(alpha = 0.4f),
            padding = 18.dp
        ) {
            Text("FocusLock Membership", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Fg)
            Spacer(Modifier.height(6.dp))
            when {
                billing.subscribed -> {
                    Text(
                        "✦  FocusLock Pro Active — Unlimited deep focus time & zero ads.",
                        color = Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryPillButton(
                        text = "Manage Subscription in Google Play",
                        onClick = { ctx.openUrl("https://play.google.com/store/account/subscriptions") }
                    )
                }
                !RemoteFlags.monetization -> {
                    Text("Unlimited free access enabled during launch.", color = Muted, fontSize = 13.sp)
                }
                else -> {
                    val left = Entitlement.freeMinutesLeft(StatsStore(ctx))
                    Text(
                        "Free Plan: $left of ${RemoteFlags.freeMinutes} focus minutes remaining this month. Upgrade to Pro for unlimited focus sessions and an ad-free sanctuary.",
                        color = Muted,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryPillButton(
                        text = "Unlock FocusLock Pro",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onPaywall
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Link("Privacy Policy", color = Muted, size = 13) { ctx.openUrl(RemoteFlags.PRIVACY_URL) }
            Text("FocusLock v1.0.7", color = Muted.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}
