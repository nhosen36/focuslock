package com.focuslock.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun Context.openUrl(url: String) = safeStart(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

/** Region-aware emergency number for the dialer shortcut. Defaults to 112, which works in most countries. */
fun emergencyNumber(): String = when (Locale.getDefault().country) {
    "BD", "GB", "HK", "SG", "NG", "KE" -> "999"
    "US", "CA", "PH" -> "911"
    "AU" -> "000"
    "NZ" -> "111"
    "CN" -> "110"
    else -> "112"
}

/** Calm-inspired prominent disclosure shown before any permission is requested. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text("WELCOME TO FOCUSLOCK", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
            Text(
                "A quiet sanctuary for deep study.",
                fontFamily = SerifFont,
                fontSize = 30.sp,
                color = Fg,
                textAlign = TextAlign.Center
            )
            Text(
                "Silence notifications, lock away distractions, and immerse yourself in calm ambient study music.",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                borderColor = Accent.copy(alpha = 0.35f),
                padding = 18.dp
            ) {
                Text("How FocusLock Protects Your Time", color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "1. Do Not Disturb Access — Silences notification popups and alerts during your session while keeping study music active, then restores your setting afterwards.",
                    color = Muted,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "2. Display Over Other Apps — Keeps the FocusLock timer screen locked on top during a session so pressing Home cannot exit until the timer ends (or you solve 5 math challenges).",
                    color = Muted,
                    fontSize = 13.sp
                )
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                padding = 16.dp
            ) {
                Text("Your Privacy & Control", color = Fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "We never change your phone's Home launcher, and we never read your messages, screen, contacts, or location. All focus history stays privately on your device.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }

            Text(
                "By continuing you confirm you are 13 or older and accept our privacy policy.",
                color = Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Link("Read Privacy Policy ›", color = Accent, size = 13) { ctx.openUrl(RemoteFlags.PRIVACY_URL) }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryPillButton(
            text = "Enter Focus Sanctuary",
            modifier = Modifier.fillMaxWidth(),
            onClick = onDone
        )
    }
}
