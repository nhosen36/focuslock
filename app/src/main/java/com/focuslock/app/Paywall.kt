package com.focuslock.app

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

private const val MANAGE_SUBS_URL = "https://play.google.com/store/account/subscriptions"

/**
 * The subscription screen. Opened when the free minutes run out, or from any "Go Pro" / "Remove ads" link.
 * Billing terms are shown plainly, as Google Play requires.
 */
@Composable
fun Paywall(outOfMinutes: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val billing = Services.billing

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                if (outOfMinutes) "You've used your ${RemoteFlags.freeMinutes} free minutes this month" else "FocusLock Pro",
                fontSize = 26.sp, fontWeight = FontWeight.Light
            )
            Text(
                if (outOfMinutes) "Free minutes reset on the 1st of every month. Or go Pro and keep focusing now."
                else "Unlimited focus time, and no ads.",
                color = Muted
            )
            Spacer(Modifier.height(4.dp))
            Text("• Unlimited focus sessions, every month")
            Text("• No ads")
            Text("• Cancel any time in Google Play")

            Spacer(Modifier.height(8.dp))
            if (billing.plans.isEmpty()) {
                Text("Loading prices...", color = Muted, fontSize = 13.sp)
            }
            billing.plans.forEach { p ->
                val yearly = p.basePlanId == BillingManager.YEARLY
                val label = if (yearly) "Yearly  ${p.price}  (best value)" else "Monthly  ${p.price}"
                Link(label, color = if (yearly) Accent else Fg, size = 18) {
                    ctx.findActivity()?.let { billing.buy(it, p) }
                }
            }

            Text(
                "Payment is charged to your Google Play account. The subscription renews automatically each month or year " +
                    "until you cancel at least 24 hours before the renewal date. Manage or cancel in Google Play, " +
                    "Payments & subscriptions.",
                color = Muted, fontSize = 12.sp
            )
            Row {
                Link("Restore purchase", size = 13) { billing.restore() }
                Link("Manage subscription", size = 13) { ctx.openUrl(MANAGE_SUBS_URL) }
            }
            Link("Privacy policy", size = 13) { ctx.openUrl(RemoteFlags.PRIVACY_URL) }
        }
        Link(if (outOfMinutes) "Not now" else "Close", size = 16, onClick = onClose)
    }
}
