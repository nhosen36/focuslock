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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MANAGE_SUBS_URL = "https://play.google.com/store/account/subscriptions"

/**
 * Calm-inspired subscription screen. Opened when free monthly minutes run out or from any Pro upgrade CTA.
 */
@Composable
fun Paywall(outOfMinutes: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val billing = Services.billing

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text("FOCUSLOCK PRO", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
            Text(
                if (outOfMinutes) "Continue Your Focus Journey" else "Unlimited Deep Focus",
                fontFamily = SerifFont,
                fontSize = 28.sp,
                color = Fg,
                textAlign = TextAlign.Center
            )
            Text(
                if (outOfMinutes)
                    "You've completed your ${RemoteFlags.freeMinutes} free focus minutes for this month. Free minutes reset on the 1st, or unlock Pro now for unlimited sessions."
                else
                    "Experience limitless distraction-free study sessions and a completely ad-free sanctuary.",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                borderColor = Accent.copy(alpha = 0.35f),
                padding = 18.dp
            ) {
                Text("✦   Unlimited focus sessions every month", color = Fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text("✦   100% ad-free across Stats, Awards & Style", color = Fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text("✦   Full ambient study soundscapes & custom themes", color = Fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Text("✦   Cancel any time in Google Play", color = Fg, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }

            if (billing.plans.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, padding = 16.dp) {
                    Text(
                        "Connecting to Google Play Billing... (Plans: Monthly \$1.99 / Yearly \$14.99)",
                        color = Muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            billing.plans.forEach { p ->
                val yearly = p.basePlanId == BillingManager.YEARLY
                if (yearly) {
                    PrimaryPillButton(
                        text = "Yearly  •  ${p.price}/yr  (Best Value)",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { ctx.findActivity()?.let { billing.buy(it, p) } }
                    )
                } else {
                    SecondaryPillButton(
                        text = "Monthly  •  ${p.price}/mo",
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        textColor = Fg,
                        borderColor = Accent.copy(alpha = 0.45f),
                        onClick = { ctx.findActivity()?.let { billing.buy(it, p) } }
                    )
                }
            }

            Text(
                "Payment is charged to your Google Play account. Subscriptions renew automatically unless canceled at least 24 hours before the end of the current period. Manage or cancel anytime in Google Play → Payments & subscriptions.",
                color = Muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Link("Restore Purchase", color = Accent, size = 13) { billing.restore() }
                Link("Manage Subscription", color = Muted, size = 13) { ctx.openUrl(MANAGE_SUBS_URL) }
                Link("Privacy", color = Muted, size = 13) { ctx.openUrl(RemoteFlags.PRIVACY_URL) }
            }
        }
        Spacer(Modifier.height(8.dp))
        SecondaryPillButton(
            text = if (outOfMinutes) "Maybe Later" else "Close",
            onClick = onClose
        )
    }
}
