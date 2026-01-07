package com.mzgs.helper

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    private val validNetworks = setOf("applovin", "admob")

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
        showInterstitialInternal(
            activity = activity,
            networks = networks,
            onAdClosed = onAdClosed,
        )
    }

    fun showInterstitialWithCycle(
        activity: Activity,
        name: String,
        defaultValue: Int = 3,
        networks: String = "applovin,admob",
        onAdClosed: () -> Unit = {},
    ): Boolean {
        val cycleValue = Remote.getInt(name, defaultValue)
        val currentCounter = ActionCounter.increaseGet(name)

        if (cycleValue <= 0) {
            onAdClosed()
            return false
        }

        if (currentCounter % cycleValue == 0) {
            return showInterstitialInternal(
                activity = activity,
                networks = networks,
                onAdClosed = onAdClosed,
            )
        }

        onAdClosed()
        return false
    }

    fun showRewarded(
        activity: Activity,
        networks: String = "applovin,admob",
        onRewarded: (type: String, amount: Int) -> Unit = { _, _ -> },
        onAdClosed: () -> Unit = {},
    ) {
        val networks = normalizedNetworks(networks)

        var activeNetwork: String? = null
        var closedNotified = false

        fun networkClosed(network: String) {
            if (activeNetwork == network && !closedNotified) {
                closedNotified = true
                onAdClosed()
            }
        }

        fun networkRewarded(network: String, type: String, amount: Int) {
            if (activeNetwork == network) {
                onRewarded(type, amount)
            }
        }

        for (network in networks) {
            activeNetwork = network
            val shown = when (network) {
                "applovin" -> ApplovinMaxMediation.showReward(
                    activity = activity,
                    onRewarded = { type, amount -> networkRewarded(network, type, amount) },
                    onAdClosed = { networkClosed(network) },
                )
                "admob" -> AdmobMediation.showReward(
                    activity = activity,
                    onRewarded = { type, amount -> networkRewarded(network, type, amount) },
                    onAdClosed = { networkClosed(network) },
                )
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

    private fun showInterstitialInternal(
        activity: Activity,
        networks: String,
        onAdClosed: () -> Unit,
    ): Boolean {
        val networks = normalizedNetworks(networks)

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
                return true
            }
        }
        if (!closedNotified) {
            onAdClosed()
        }
        return false
    }

    fun showAppOpenAd(
        activity: Activity,
        networks: String = "applovin,admob",
        onAdClosed: () -> Unit = {},
    ) {
        val networks = normalizedNetworks(networks)

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
                "applovin" -> ApplovinMaxMediation.showAppOpenAd(activity) { networkClosed(network) }
                "admob" -> AdmobMediation.showAppOpenAd(activity) { networkClosed(network) }
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
        val orderedNetworks = normalizedNetworks(networks)

        var activeIndex by remember(networks, adUnitId, adSize?.toString()) { mutableStateOf(0) }
        var failedNetworks by remember(networks, adUnitId, adSize?.toString()) { mutableStateOf(setOf<String>()) }
        var pendingIndex by remember(networks, adUnitId, adSize?.toString()) { mutableStateOf<Int?>(null) }

        LaunchedEffect(pendingIndex) {
            val nextIndex = pendingIndex ?: return@LaunchedEffect
            activeIndex = nextIndex
            pendingIndex = null
        }

        fun handleFailure(network: String, message: String) {
            val currentNetwork = orderedNetworks.getOrNull(activeIndex)
            if (network != currentNetwork || failedNetworks.contains(network)) {
                return
            }
            failedNetworks = failedNetworks + network
            val nextIndex = orderedNetworks.indexOfFirst { it !in failedNetworks }
            if (nextIndex == -1) {
                activeIndex = -1
                pendingIndex = null
                onAdFailedToLoad?.invoke(message)
            } else {
                activeIndex = -1
                pendingIndex = nextIndex
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

    @Composable
    fun showMrec(
        modifier: Modifier = Modifier,
        networks: String = "applovin,admob",
        adUnitId: String? = null,
        onAdFailedToLoad: ((String) -> Unit)? = null,
    ) {
        val orderedNetworks = normalizedNetworks(networks)

        var activeIndex by remember(networks, adUnitId) { mutableStateOf(0) }
        var failedNetworks by remember(networks, adUnitId) { mutableStateOf(setOf<String>()) }

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
                    ?: ApplovinMaxMediation.config.MREC_AD_UNIT_ID
                if (resolvedAdUnitId.isBlank()) {
                    handleFailure("applovin", "AppLovin MREC ad unit id is blank.")
                } else {
                    ApplovinMaxMediation.showMrec(
                        modifier = modifier,
                        adUnitId = resolvedAdUnitId,
                    ) { errorMessage ->
                        handleFailure("applovin", errorMessage)
                    }
                }
            }
            "admob" -> {
                val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() }
                    ?: AdmobMediation.config.MREC_AD_UNIT_ID
                if (resolvedAdUnitId.isBlank()) {
                    handleFailure("admob", "AdMob MREC ad unit id is blank.")
                } else {
                    AdmobMediation.showMrec(
                        modifier = modifier,
                        adUnitId = resolvedAdUnitId,
                    ) { errorMessage ->
                        handleFailure("admob", errorMessage)
                    }
                }
            }
        }
    }

    private fun normalizedNetworks(networks: String): List<String> {
        val requested = networks
            .split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        return requested
            .filter { it in validNetworks }
            .ifEmpty { validNetworks.toList() }
    }
}
