package com.mzgs.helper

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.ads.AdSize
import java.lang.ref.WeakReference

object Ads {

    private var startedActivityCount = 0
    private var appWentToBackground = false
    private var activityCallbacksRegistered = false
    private var componentCallbacksRegistered = false
    private var onGoBackground: (Activity) -> Unit = {}
    private var onGoForeground: (Activity) -> Unit = {}
    private var lastStartedActivityRef: WeakReference<Activity>? = null

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}

        override fun onActivityStarted(activity: Activity) {
            startedActivityCount += 1
            lastStartedActivityRef = WeakReference(activity)
            if (startedActivityCount == 1) {
                val shouldNotify = appWentToBackground
                if (appWentToBackground) {
                    appWentToBackground = false
                }
                if (shouldNotify) {
                    onGoForeground(activity)
                }
            }
        }

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            if (startedActivityCount > 0) {
                startedActivityCount -= 1
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
    }

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && !appWentToBackground) {
                appWentToBackground = true
                lastStartedActivityRef?.get()?.let { onGoBackground(it) }
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {}

        override fun onLowMemory() {}
    }

    fun initialize(
        activity: Activity,
        onGoBackground: (Activity) -> Unit = {},
        onGoForeground: (Activity) -> Unit = {},
    ) {
        this.onGoBackground = onGoBackground
        this.onGoForeground = onGoForeground
        if (!activityCallbacksRegistered) {
            activity.application.registerActivityLifecycleCallbacks(activityCallbacks)
            activityCallbacksRegistered = true
        }
        if (!componentCallbacksRegistered) {
            activity.application.registerComponentCallbacks(componentCallbacks)
            componentCallbacksRegistered = true
        }
    }

//    order : netowrks by comma : applovin,admob
    fun showInterstitial(
        activity: Activity,
        networks: String = "applovin,admob",
        onAdClosed: () -> Unit = {},
    ) {
        val requestedNetworks = networks
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val networks = requestedNetworks
            .filter { it == "applovin" || it == "admob" }
            .ifEmpty { listOf("applovin", "admob") }

        var activeNetwork: String? = null
        var closedNotified = false

        fun networkClosed(network: String) {
            if (activeNetwork == network && !closedNotified) {
                closedNotified = true
                onAdClosed()
            }
        }

        for (network in networks) {
            activeNetwork = network
            val shown = when (network) {
                "applovin" -> ApplovinMaxMediation.showInterstitial(activity) { networkClosed(network) }
                "admob" -> AdmobMediation.showInterstitial(activity) { networkClosed(network) }
                else -> false
            }
            if (shown) {
                return
            }
        }
        if (!closedNotified) {
            onAdClosed()
        }
    }

    @Composable
    fun showBanner(
        modifier: Modifier = Modifier,
        networks: String = "applovin,admob",
        adUnitId: String? = null,
        adSize: AdSize? = null,
        onAdFailedToLoad: ((String) -> Unit)? = null,
    ) {
        val requestedNetworks = networks
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val orderedNetworks = requestedNetworks
            .filter { it == "applovin" || it == "admob" }
            .ifEmpty { listOf("applovin", "admob") }

        var activeIndex by remember(networks, adUnitId, adSize?.toString()) { mutableStateOf(0) }
        var failedNetworks by remember(networks, adUnitId, adSize?.toString()) { mutableStateOf(setOf<String>()) }

        fun handleFailure(network: String, message: String) {
            val currentNetwork = orderedNetworks.getOrNull(activeIndex)
            if (network != currentNetwork || failedNetworks.contains(network)) {
                return
            }
            failedNetworks = failedNetworks + network
            val nextIndex = orderedNetworks.indexOfFirst { it !in failedNetworks }
            if (nextIndex == -1) {
                onAdFailedToLoad?.invoke(message)
            } else {
                activeIndex = nextIndex
            }
        }

        when (orderedNetworks.getOrNull(activeIndex)) {
            "applovin" -> {
                val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() }
                    ?: ApplovinMaxMediation.config.BANNER_AD_UNIT_ID
                if (resolvedAdUnitId.isBlank()) {
                    handleFailure("applovin", "AppLovin banner ad unit id is blank.")
                } else {
                    ApplovinMaxMediation.showBanner(
                        modifier = modifier,
                        adUnitId = resolvedAdUnitId,
                        adSize = adSize,
                    ) { errorMessage ->
                        handleFailure("applovin", errorMessage)
                    }
                }
            }
            "admob" -> {
                val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() }
                    ?: AdmobMediation.config.BANNER_AD_UNIT_ID
                if (resolvedAdUnitId.isBlank()) {
                    handleFailure("admob", "AdMob banner ad unit id is blank.")
                } else {
                    AdmobMediation.showBanner(
                        modifier = modifier,
                        adUnitId = resolvedAdUnitId,
                        adSize = adSize,
                    ) { errorMessage ->
                        handleFailure("admob", errorMessage)
                    }
                }
            }
        }
    }
}
