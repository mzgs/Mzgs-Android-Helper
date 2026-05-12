package com.mzgs.helper

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.ads.AdSize

object Ads {

    private val validNetworks = setOf("applovin", "admob")

    /**
     * Registers a callback that runs after both AdMob and AppLovin MAX initialization complete.
     * If both networks are already initialized, the callback is invoked immediately.
     */
    fun setInitListener(onInitComplete: () -> Unit) {
        val lock = Any()
        var isAdmobInitialized = false
        var isAppLovinMaxInitialized = false
        var isCallbackInvoked = false

        fun markInitialized(isAdmob: Boolean) {
            val shouldInvoke = synchronized(lock) {
                if (isAdmob) {
                    isAdmobInitialized = true
                } else {
                    isAppLovinMaxInitialized = true
                }
                if (isAdmobInitialized && isAppLovinMaxInitialized && !isCallbackInvoked) {
                    isCallbackInvoked = true
                    true
                } else {
                    false
                }
            }
            if (shouldInvoke) {
                onInitComplete()
            }
        }

        AdmobMediation.setAdmobInitListener {
            markInitialized(isAdmob = true)
        }
        ApplovinMaxMediation.setInitListener {
            markInitialized(isAdmob = false)
        }
    }

//    order : netowrks by comma : applovin,admob
    fun showInterstitial(
        activity: Activity,
        networks: String = "applovin,admob",
        onAdClosed: () -> Unit = {},
    ): Boolean {
        return showInterstitialInternal(
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

    fun loadInterstitial(
        activity: Activity,
        networks: String = "applovin,admob",
    ): Boolean {
        val networks = normalizedNetworks(networks)
        var loadRequested = false

        for (network in networks) {
            val requested = when (network) {
                "applovin" -> ApplovinMaxMediation.loadInterstitial(activity)
                "admob" -> AdmobMediation.loadInterstitial(activity)
                else -> false
            }
            loadRequested = loadRequested || requested
        }

        return loadRequested
    }

    fun showRewarded(
        activity: Activity,
        networks: String = "applovin,admob",
        onRewarded: (type: String, amount: Int) -> Unit = { _, _ -> },
        onAdClosed: () -> Unit = {},
    ): Boolean {
        val networks = normalizedNetworks(networks)

        var shownNetwork: String? = null
        var closedNotified = false

        fun networkClosed(network: String) {
            if (shownNetwork == network && !closedNotified) {
                closedNotified = true
                onAdClosed()
            }
        }

        fun networkRewarded(network: String, type: String, amount: Int) {
            if (shownNetwork == network) {
                onRewarded(type, amount)
            }
        }

        for (network in networks) {
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
                shownNetwork = network
                return true
            }
        }

        if (!closedNotified) {
            onAdClosed()
        }
        return false
    }

    fun loadRewarded(
        activity: Activity,
        networks: String = "applovin,admob",
    ): Boolean {
        val networks = normalizedNetworks(networks)
        var loadRequested = false

        for (network in networks) {
            val requested = when (network) {
                "applovin" -> ApplovinMaxMediation.loadRewarded(activity)
                "admob" -> AdmobMediation.loadRewarded(activity)
                else -> false
            }
            loadRequested = loadRequested || requested
        }

        return loadRequested
    }

    private fun showInterstitialInternal(
        activity: Activity,
        networks: String,
        onAdClosed: () -> Unit,
    ): Boolean {
        val networks = normalizedNetworks(networks)

        var shownNetwork: String? = null
        var closedNotified = false

        fun networkClosed(network: String) {
            if (shownNetwork == network && !closedNotified) {
                closedNotified = true
                onAdClosed()
            }
        }

        for (network in networks) {
            val shown = when (network) {
                "applovin" -> ApplovinMaxMediation.showInterstitial(activity) { networkClosed(network) }
                "admob" -> AdmobMediation.showInterstitial(activity) { networkClosed(network) }
                else -> false
            }
            if (shown) {
                shownNetwork = network
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
    ): Boolean {
        val networks = normalizedNetworks(networks)

        var shownNetwork: String? = null
        var closedNotified = false

        fun networkClosed(network: String) {
            if (shownNetwork == network && !closedNotified) {
                closedNotified = true
                onAdClosed()
            }
        }

        for (network in networks) {
            val shown = when (network) {
                "applovin" -> ApplovinMaxMediation.showAppOpenAd(activity) { networkClosed(network) }
                "admob" -> AdmobMediation.showAppOpenAd(activity) { networkClosed(network) }
                else -> false
            }
            if (shown) {
                shownNetwork = network
                return true
            }
        }

        if (!closedNotified) {
            onAdClosed()
        }
        return false
    }

    fun loadAppOpenAd(
        activity: Activity,
        networks: String = "applovin,admob",
    ): Boolean {
        val networks = normalizedNetworks(networks)
        var loadRequested = false

        for (network in networks) {
            val requested = when (network) {
                "applovin" -> ApplovinMaxMediation.loadAppOpenAd(activity)
                "admob" -> AdmobMediation.loadAppOpenAd(activity)
                else -> false
            }
            loadRequested = loadRequested || requested
        }

        return loadRequested
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
        val isMrec = adSize == AdSize.MEDIUM_RECTANGLE

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
                    ?: if (isMrec) {
                        ApplovinMaxMediation.config.MREC_AD_UNIT_ID
                    } else {
                        ApplovinMaxMediation.config.BANNER_AD_UNIT_ID
                    }
                if (resolvedAdUnitId.isBlank()) {
                    val adTypeLabel = if (isMrec) "MREC" else "banner"
                    handleFailure("applovin", "AppLovin $adTypeLabel ad unit id is blank.")
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
                    ?: if (isMrec) {
                        AdmobMediation.config.MREC_AD_UNIT_ID
                    } else {
                        AdmobMediation.config.BANNER_AD_UNIT_ID
                    }
                if (resolvedAdUnitId.isBlank()) {
                    val adTypeLabel = if (isMrec) "MREC" else "banner"
                    handleFailure("admob", "AdMob $adTypeLabel ad unit id is blank.")
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
        showBanner(
            modifier = modifier,
            networks = networks,
            adUnitId = adUnitId,
            adSize = AdSize.MEDIUM_RECTANGLE,
            onAdFailedToLoad = onAdFailedToLoad,
        )
    }

    @Composable
    fun showNativeAd(
        modifier: Modifier = Modifier,
        networks: String = "applovin,admob",
        adUnitId: String? = null,
        onAdFailedToLoad: ((String) -> Unit)? = null,
    ) {
        val orderedNetworks = normalizedNetworks(networks)

        var activeIndex by remember(networks, adUnitId) { mutableStateOf(0) }
        var failedNetworks by remember(networks, adUnitId) { mutableStateOf(setOf<String>()) }
        var pendingIndex by remember(networks, adUnitId) { mutableStateOf<Int?>(null) }

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
                    ?: ApplovinMaxMediation.config.NATIVE_AD_UNIT_ID
                if (resolvedAdUnitId.isBlank()) {
                    handleFailure("applovin", "AppLovin native ad unit id is blank.")
                } else {
                    ApplovinMaxMediation.showNativeAd(
                        modifier = modifier,
                        adUnitId = resolvedAdUnitId,
                        onAdFailedToLoad = { errorMessage ->
                            handleFailure("applovin", errorMessage)
                        },
                    )
                }
            }
            "admob" -> {
                val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() }
                    ?: AdmobMediation.config.NATIVE_AD_UNIT_ID
                if (resolvedAdUnitId.isBlank()) {
                    handleFailure("admob", "AdMob native ad unit id is blank.")
                } else {
                    AdmobMediation.showNativeAd(
                        modifier = modifier,
                        adUnitId = resolvedAdUnitId,
                        onAdFailedToLoad = { errorMessage ->
                            handleFailure("admob", errorMessage)
                        },
                    )
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
