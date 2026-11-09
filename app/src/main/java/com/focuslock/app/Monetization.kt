package com.focuslock.app

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Global handles created once in MainActivity. */
object Services {
    // Holds an application Context only (never an Activity), so there is no leak.
    @android.annotation.SuppressLint("StaticFieldLeak")
    lateinit var billing: BillingManager
}

/**
 * Remote settings, from a tiny JSON file you host (see docs/config.json). No user data is sent;
 * the app only downloads this file.
 *
 *  - monetization_enabled: true  = free users get the monthly minute allowance and see ads (the normal state).
 *                          false = launch/promo period: everyone gets unlimited free use and no ads.
 *  - free_minutes_per_month: the free allowance. Change it any time without an app update.
 */
object RemoteFlags {
    // Hosted via GitHub Pages from docs/ on branch main.
    const val CONFIG_URL = "https://nhosen36.github.io/focuslock/config.json"
    const val PRIVACY_URL = "https://nhosen36.github.io/focuslock/privacy.html"

    // Safe defaults if the file can't be reached: the normal free plan applies.
    var monetization by mutableStateOf(true); private set
    var freeMinutes by mutableStateOf(120); private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("flags", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        monetization = p.getBoolean("monetization", true)
        freeMinutes = p.getInt("freeMinutes", 120)
    }

    fun refresh(ctx: Context) {
        if (CONFIG_URL.contains("YOUR_GITHUB_USER")) return // not configured yet: keep defaults
        val app = ctx.applicationContext
        Thread {
            try {
                val c = URL(CONFIG_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 4000; c.readTimeout = 4000
                val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                prefs(app).edit()
                    .putBoolean("monetization", json.optBoolean("monetization_enabled", true))
                    .putInt("freeMinutes", json.optInt("free_minutes_per_month", 120).coerceIn(0, 100_000))
                    .apply()
                Handler(Looper.getMainLooper()).post { load(app) }
            } catch (_: Exception) {
                // Offline or file missing: keep the last known values.
            }
        }.start()
    }
}

object Entitlement {
    fun isPro(): Boolean = Services.billing.subscribed

    /** True when the free allowance and ads apply to this user. */
    fun limitsApply(): Boolean = RemoteFlags.monetization && !isPro()
    fun showAds(): Boolean = limitsApply()

    /** Free minutes left this calendar month (resets on the 1st). */
    fun freeMinutesLeft(stats: StatsStore): Int =
        (RemoteFlags.freeMinutes - stats.minutesThisMonth()).coerceAtLeast(0)
}

data class Plan(val basePlanId: String, val price: String, val offerToken: String)

/**
 * Google Play Billing, subscriptions. In Play Console create ONE subscription with id [SUB_ID] and two base plans:
 *   "monthly" (auto-renewing, 1 month, US$1.99)   and   "yearly" (auto-renewing, 1 year, US$14.99).
 */
class BillingManager(private val ctx: Context) : PurchasesUpdatedListener {
    companion object {
        const val SUB_ID = "focuslock_pro"
        const val MONTHLY = "monthly"
        const val YEARLY = "yearly"
    }

    private val prefs = ctx.getSharedPreferences("billing", Context.MODE_PRIVATE)
    var subscribed by mutableStateOf(prefs.getBoolean("subscribed", false)); private set
    var plans by mutableStateOf<List<Plan>>(emptyList()); private set
    private var details: ProductDetails? = null

    private val client = BillingClient.newBuilder(ctx)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) { loadDetails(); restore() }
            }
            override fun onBillingServiceDisconnected() {}
        })
    }

    private fun loadDetails() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(SUB_ID).setProductType(BillingClient.ProductType.SUBS).build()
            )
        ).build()
        client.queryProductDetailsAsync(params) { _, res ->
            val d = res.productDetailsList.firstOrNull()
            details = d
            // One entry per base plan. The last pricing phase is the normal recurring price.
            val list = d?.subscriptionOfferDetails.orEmpty()
                .distinctBy { it.basePlanId }
                .mapNotNull { o ->
                    val price = o.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice
                    if (price == null) null else Plan(o.basePlanId, price, o.offerToken)
                }
                .sortedBy { if (it.basePlanId == MONTHLY) 0 else 1 }
            Handler(Looper.getMainLooper()).post { plans = list }
        }
    }

    /** Re-checks the subscription (reinstall, new phone, renewal, expiry). */
    fun restore() {
        if (!client.isReady) { connect(); return }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, list -> if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(list, true) }
    }

    fun buy(activity: Activity, plan: Plan) {
        val d = details ?: return
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(
                BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(d).setOfferToken(plan.offerToken).build()
            )
        ).build()
        client.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, list: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && list != null) handle(list, false)
    }

    private fun handle(list: List<Purchase>, authoritative: Boolean) {
        var active = false
        list.filter { SUB_ID in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach {
            active = true
            // Unacknowledged purchases are refunded by Google after 3 days, so always acknowledge.
            if (!it.isAcknowledged) {
                client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(it.purchaseToken).build()) {}
            }
        }
        if (active || authoritative) {
            prefs.edit().putBoolean("subscribed", active).apply()
            Handler(Looper.getMainLooper()).post { subscribed = active }
        }
    }
}
