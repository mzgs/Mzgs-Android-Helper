package com.mzgs.helper

import android.app.Activity
import android.os.Bundle
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
        useAppOpenFallback: Boolean = false,
        onAdClosed: (adShowed: Boolean) -> Unit = {},
    ) {
        showInterstitialInternal(
            activity = activity,
            networks = networks,
            onAdShowFailed = { _, _ -> },
            onAdClosed = { interstitialShowed ->
                if (!interstitialShowed && useAppOpenFallback) {
                    showAppOpenAd(
                        activity = activity,
                        networks = networks,
                        onAdClosed = onAdClosed,
                    )
                } else {
                    onAdClosed(interstitialShowed)
                }
            },
        )
    }

    fun showInterstitialWithCycle(
        activity: Activity,
        name: String,
        defaultValue: Int = 3,
        networks: String = "applovin,admob",
        useAppOpenFallback: Boolean = true,
        onAdShowFailed: (network: String, errorMessage: String) -> Unit = { _, _ -> },
        onAdClosed: (adShowed: Boolean) -> Unit = {},
    ) {
        val cycleValue = Remote.getInt(name, defaultValue)
        val currentCounter = ActionCounter.increaseGet(name)

        if (cycleValue <= 0) {
            onAdClosed(false)
            return
        }

        if (currentCounter % cycleValue == 0) {
            showInterstitialInternal(
                activity = activity,
                networks = networks,
                onAdShowFailed = onAdShowFailed,
                onAdClosed = { interstitialShowed ->
                    if (!interstitialShowed && useAppOpenFallback) {
                        showAppOpenAd(
                            activity = activity,
                            networks = networks,
                            onAdClosed = onAdClosed,
                        )
                    } else {
                        onAdClosed(interstitialShowed)
                    }
                },
            )
            return
        }

        onAdClosed(false)
    }

    fun showSplashAds(
        activity: Activity,
        interstitialNetworks: String = "applovin,admob",
        appOpenNetworks: String = "applovin,admob",
        onSplashAdClosed: () -> Unit = {},
    ) {
        showInterstitial(
            activity = activity,
            networks = interstitialNetworks,
            onAdClosed = { interstitialShowed ->
                if (interstitialShowed) {
                    FirebaseAnalyticsManager.logEvent(
                        "splash_ads_success",
                        Bundle().apply { putString("ad_type", "interstitial") },
                    )
                    onSplashAdClosed()
                } else {
                    showAppOpenAd(
                        activity = activity,
                        networks = appOpenNetworks,
                        onAdClosed = { appOpenShowed ->
                            FirebaseAnalyticsManager.logEvent(
                                "splash_ads_" + (if (appOpenShowed) "success" else "fail"),
                                Bundle().apply { putString("ad_type", "app_open") },
                            )
                            onSplashAdClosed()
                        },
                    )
                }
            },
        )
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
        onAdClosed: (adShowed: Boolean) -> Unit = {},
    ) {
        val networks = normalizedNetworks(networks)

        var shownNetwork: String? = null
        var shownNetworkIndex = -1
        var closedNotified = false
        val failedToShowNetworks = mutableSetOf<String>()
        lateinit var tryShowFrom: (startIndex: Int) -> Boolean

        fun notifyClosed(adShowed: Boolean) {
            if (!closedNotified) {
                closedNotified = true
                onAdClosed(adShowed)
            }
        }

        fun networkClosed(network: String) {
            if (shownNetwork != network || closedNotified) {
                return
            }

            if (failedToShowNetworks.remove(network)) {
                val nextIndex = shownNetworkIndex + 1
                shownNetwork = null
                shownNetworkIndex = -1
                tryShowFrom(nextIndex)
            } else {
                notifyClosed(adShowed = true)
            }
        }

        fun networkRewarded(network: String, type: String, amount: Int) {
            if (shownNetwork == network) {
                onRewarded(type, amount)
            }
        }

        fun networkShowFailed(network: String) {
            if (shownNetwork == network && !closedNotified) {
                failedToShowNetworks.add(network)
            }
        }

        tryShowFrom = { startIndex ->
            var shownAny = false
            for (index in startIndex until networks.size) {
                val network = networks[index]
                val shown = when (network) {
                    "applovin" -> ApplovinMaxMediation.showReward(
                        activity = activity,
                        onRewarded = { type, amount -> networkRewarded(network, type, amount) },
                        onAdShowFailed = { networkShowFailed(network) },
                        onAdClosed = { networkClosed(network) },
                    )
                    "admob" -> AdmobMediation.showReward(
                        activity = activity,
                        onRewarded = { type, amount -> networkRewarded(network, type, amount) },
                        onAdShowFailed = { networkShowFailed(network) },
                        onAdClosed = { networkClosed(network) },
                    )
                    else -> false
                }
                if (shown) {
                    shownNetwork = network
                    shownNetworkIndex = index
                    shownAny = true
                    break
                }
            }
            if (!shownAny) {
                notifyClosed(adShowed = false)
            }
            shownAny
        }

        tryShowFrom(0)
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
        onAdShowFailed: (network: String, errorMessage: String) -> Unit,
        onAdClosed: (adShowed: Boolean) -> Unit,
    ) {
        val networks = normalizedNetworks(networks)

        var shownNetwork: String? = null
        var shownNetworkIndex = -1
        var closedNotified = false
        val failedToShowNetworks = mutableSetOf<String>()
        lateinit var tryShowFrom: (startIndex: Int) -> Boolean

        fun notifyClosed(adShowed: Boolean) {
            if (!closedNotified) {
                closedNotified = true
                onAdClosed(adShowed)
            }
        }

        fun networkClosed(network: String) {
            if (shownNetwork != network || closedNotified) {
                return
            }

            if (failedToShowNetworks.remove(network)) {
                val nextIndex = shownNetworkIndex + 1
                shownNetwork = null
                shownNetworkIndex = -1
                tryShowFrom(nextIndex)
            } else {
                notifyClosed(adShowed = true)
            }
        }

        fun networkShowFailed(network: String, errorMessage: String) {
            if (shownNetwork == network && !closedNotified) {
                failedToShowNetworks.add(network)
                onAdShowFailed(network, errorMessage)
            }
        }

        tryShowFrom = { startIndex ->
            var shownAny = false
            for (index in startIndex until networks.size) {
                val network = networks[index]
                val shown = when (network) {
                    "applovin" -> ApplovinMaxMediation.showInterstitial(
                        activity = activity,
                        onAdShowFailed = { errorMessage -> networkShowFailed(network, errorMessage) },
                        onAdClosed = { networkClosed(network) },
                    )
                    "admob" -> AdmobMediation.showInterstitial(
                        activity = activity,
                        onAdShowFailed = { errorMessage -> networkShowFailed(network, errorMessage) },
                        onAdClosed = { networkClosed(network) },
                    )
                    else -> false
                }
                if (shown) {
                    shownNetwork = network
                    shownNetworkIndex = index
                    shownAny = true
                    break
                }
            }
            if (!shownAny) {
                notifyClosed(adShowed = false)
            }
            shownAny
        }

        tryShowFrom(0)
    }

    fun showAppOpenAd(
        activity: Activity,
        networks: String = "applovin,admob",
        onAdClosed: (adShowed: Boolean) -> Unit = {},
    ) {
        val networks = normalizedNetworks(networks)

        var shownNetwork: String? = null
        var shownNetworkIndex = -1
        var closedNotified = false
        val failedToShowNetworks = mutableSetOf<String>()
        lateinit var tryShowFrom: (startIndex: Int) -> Boolean

        fun notifyClosed(adShowed: Boolean) {
            if (!closedNotified) {
                closedNotified = true
                onAdClosed(adShowed)
            }
        }

        fun networkClosed(network: String) {
            if (shownNetwork != network || closedNotified) {
                return
            }

            if (failedToShowNetworks.remove(network)) {
                val nextIndex = shownNetworkIndex + 1
                shownNetwork = null
                shownNetworkIndex = -1
                tryShowFrom(nextIndex)
            } else {
                notifyClosed(adShowed = true)
            }
        }

        fun networkShowFailed(network: String) {
            if (shownNetwork == network && !closedNotified) {
                failedToShowNetworks.add(network)
            }
        }

        tryShowFrom = { startIndex ->
            var shownAny = false
            for (index in startIndex until networks.size) {
                val network = networks[index]
                val shown = when (network) {
                    "applovin" -> ApplovinMaxMediation.showAppOpenAdWithFailure(
                        activity = activity,
                        onAdShowFailed = { networkShowFailed(network) },
                        onAdClosed = { networkClosed(network) },
                    )
                    "admob" -> AdmobMediation.showAppOpenAdWithFailure(
                        activity = activity,
                        onAdShowFailed = { networkShowFailed(network) },
                        onAdClosed = { networkClosed(network) },
                    )
                    else -> false
                }
                if (shown) {
                    shownNetwork = network
                    shownNetworkIndex = index
                    shownAny = true
                    break
                }
            }
            if (!shownAny) {
                notifyClosed(adShowed = false)
            }
            shownAny
        }

        tryShowFrom(0)
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
