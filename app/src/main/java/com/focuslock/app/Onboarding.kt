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

/** Prominent disclosure shown before any permission is requested (Google Play requirement). */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("FocusLock", fontSize = 36.sp, fontWeight = FontWeight.Light)
            Text("Lock in and study without notifications or app-switching pulling you away.")
            Text("How FocusLock protects your focus:", color = Muted)
            Text("1. Do Not Disturb access. Silences all notification popups and alerts during a session while keeping your study music active, then restores your setting afterwards.")
            Text("2. Screen Pinning. Pins FocusLock on screen during your session so the Home button, Recents switcher and notification shade are locked until the timer finishes (or you solve 5 math challenges).")
            Text("What we don't do", color = Muted)
            Text("We don't replace your phone's launcher, and we never read your messages, screen, contacts or location. You can always end a session early by solving the math challenge or call emergency services.")
            Text(
                if (RemoteFlags.monetization)
                    "Free plan: ${RemoteFlags.freeMinutes} focus minutes every month, with small ads on the Stats, Awards and Style screens (never during a session). FocusLock Pro is an optional monthly or yearly subscription for unlimited focus time and no ads. You can cancel any time in Google Play."
                else "FocusLock is free to use right now. Later, a free plan with monthly minutes and small ads, plus an optional subscription, may be introduced.",
                color = Muted, fontSize = 13.sp
            )
            Text("By continuing you confirm you are 13 or older and accept the privacy policy.", color = Muted, fontSize = 13.sp)
            Link("Read the privacy policy", color = Accent) { ctx.openUrl(RemoteFlags.PRIVACY_URL) }
        }
        Box(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            contentAlignment = Alignment.Center
        ) { Link("I agree, continue", color = Accent, size = 20, onClick = onDone) }
    }
}
