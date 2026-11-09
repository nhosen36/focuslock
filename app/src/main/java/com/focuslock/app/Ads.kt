package com.focuslock.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Ads appear only for free users AFTER the launch period (see RemoteFlags), and only on the Stats and Awards tabs.
 * Never on the Focus screen, the app list, during a session, or near Phone/Emergency.
 *
 * The IDs are Google's public TEST IDs. Replace them with your own AdMob IDs before release.
 */
object Ads {
    const val TEST_BANNER_ID = "ca-app-pub-3940256099942544/6300978111"
    private var started = false
    var ready by mutableStateOf(false); private set

    /** Shows Google's consent form where required (EEA/UK), then starts the Ads SDK. Runs only when ads are needed. */
    fun ensureInit(activity: Activity) {
        if (started) return
        started = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                if (info.canRequestAds()) { MobileAds.initialize(activity); ready = true }
            }
        }, { started = false })
    }
}

@Composable
fun AdArea(onUpgrade: () -> Unit) {
    if (!Entitlement.showAds()) return
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ctx.findActivity()?.let { Ads.ensureInit(it) } }
    Column(Modifier.fillMaxWidth()) {
        Link("Remove ads", color = Accent, size = 12, onClick = onUpgrade)
        if (Ads.ready) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { c ->
                    AdView(c).apply {
                        setAdSize(AdSize.BANNER)
                        adUnitId = Ads.TEST_BANNER_ID
                        loadAd(AdRequest.Builder().build())
                    }
                }
            )
        }
    }
}
