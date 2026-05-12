package com.mzgs.helper

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.MaxReward
import com.applovin.mediation.MaxRewardedAdListener
import com.applovin.mediation.ads.MaxAdView
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.google.android.gms.ads.AdSize
import com.mzgs.helper.FirebaseAnalyticsManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

object ApplovinMaxMediation {

    const val TAG = "ApplovinMaxMediation"
    private const val APPLOVIN_SDK_KEY_META = "applovin.sdk.key"
    private const val INIT_WAIT_TIMEOUT_MS = 120_000L
    private const val INIT_POLL_INTERVAL_MS = 1_000L
    private const val LOAD_REQUEST_MIN_INTERVAL_MS = 30_000L
    private const val LOAD_RETRY_INITIAL_DELAY_MS = 8_000L
    private const val LOAD_RETRY_MAX_DELAY_MS = 256_000L
    private const val AD_EXPIRATION_MS = 3_600_000L

    var config: ApplovinMaxConfig = ApplovinMaxConfig()

    @Volatile private var isAppOpenShowing = false
    @Volatile var isFullscreenAdShowing = false

    @Volatile private var isInitialized = false
    @Volatile private var isInitializing = false
    @Volatile private var initFailureMessage: String? = null
    private val pendingInitCallbacks = mutableListOf<() -> Unit>()

    private var interstitialAd: MaxInterstitialAd? = null
    private var rewardedAd: MaxRewardedAd? = null
    private var appOpenAd: MaxAppOpenAd? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var lastInterstitialLoadRequestedAtMs = 0L
    @Volatile private var lastRewardedLoadRequestedAtMs = 0L
    @Volatile private var lastAppOpenLoadRequestedAtMs = 0L
    @Volatile private var lastBannerLoadRequestedAtMs = 0L
    @Volatile private var lastMrecLoadRequestedAtMs = 0L
    @Volatile private var lastNativeLoadRequestedAtMs = 0L
    @Volatile private var interstitialLoadedAtMs = 0L
    @Volatile private var rewardedLoadedAtMs = 0L
    @Volatile private var appOpenLoadedAtMs = 0L
    @Volatile private var interstitialRetryAttempt = 0
    @Volatile private var rewardedRetryAttempt = 0
    @Volatile private var appOpenRetryAttempt = 0
    private var interstitialRetryRunnable: Runnable? = null
    private var rewardedRetryRunnable: Runnable? = null
    private var appOpenRetryRunnable: Runnable? = null

    private var interstitialOnAdClosed: () -> Unit = {}
    private var interstitialOnAdShowFailed: (String) -> Unit = {}
    private var rewardedOnAdClosed: () -> Unit = {}
    private var rewardedOnUserRewarded: (String, Int) -> Unit = { _, _ -> }
    private var appOpenOnAdClosedInternal: () -> Unit = {}

    fun initialize(context: Context, onInitComplete: () -> Unit = {}) {
        val appContext = context.applicationContext
        initFailureMessage = null
        if (config.DEBUG.useEmptyIds && MzgsHelper.isDebug(appContext)) {
            Log.d(TAG, "Using empty AppLovin MAX ad unit IDs.")
            config.INTERSTITIAL_AD_UNIT_ID = ""
            config.BANNER_AD_UNIT_ID = ""
            config.REWARDED_AD_UNIT_ID = ""
            config.NATIVE_AD_UNIT_ID = ""
            config.APP_OPEN_AD_UNIT_ID = ""
            config.MREC_AD_UNIT_ID = ""
        }

        if (isInitialized) {
            onInitComplete()
            return
        }
        pendingInitCallbacks.add(onInitComplete)
        if (isInitializing) {
            return
        }
        isInitializing = true

        val sdkKey = config.SDK_KEY.ifBlank { getAppLovinSdkKey(appContext) }.orEmpty()
        if (sdkKey.isBlank()) {
            initFailureMessage = "AppLovin SDK key not found; set $APPLOVIN_SDK_KEY_META in the manifest."
            Log.w(TAG, initFailureMessage!!)
            isInitializing = false
            isInitialized = false
            drainInitCallbacks()
            return
        }

        val initConfigBuilder = AppLovinSdkInitializationConfiguration.builder(sdkKey)
            .setMediationProvider(AppLovinMediationProvider.MAX)

        if (config.DEBUG.useTestAds && MzgsHelper.isDebug(appContext)) {
            if (config.DEBUG.testDeviceAdvertisingIds.isNotEmpty()) {
                initConfigBuilder.setTestDeviceAdvertisingIds(config.DEBUG.testDeviceAdvertisingIds)
            } else {
                Log.w(TAG, "useTestAds is enabled but no test device IDs were provided.")
            }
        }

        val initConfig = initConfigBuilder.build()
        AppLovinSdk.getInstance(appContext).initialize(initConfig) {
            initFailureMessage = null
            isInitialized = true
            isInitializing = false
            drainInitCallbacks()
        }
    }

    /**
     * Registers a callback for AppLovin MAX initialization.
     * If MAX is already initialized, the callback is invoked immediately.
     */
    fun setInitListener(onInitComplete: () -> Unit) {
        if (isInitialized) {
            onInitComplete()
            return
        }
        pendingInitCallbacks.add(onInitComplete)
    }

    private fun drainInitCallbacks() {
        val callbacks = pendingInitCallbacks.toList()
        pendingInitCallbacks.clear()
        callbacks.forEach { it() }
    }

    private fun buildNotInitializedMessage(): String {
        return "AppLovin MAX SDK not initialized. Call ApplovinMaxMediation.initialize(context) before showing ads."
    }

    private suspend fun awaitInitializationError(): String? {
        var waitedMs = 0L
        while (!isInitialized && waitedMs < INIT_WAIT_TIMEOUT_MS) {
            initFailureMessage?.let { return it }
            delay(INIT_POLL_INTERVAL_MS)
            waitedMs += INIT_POLL_INTERVAL_MS
        }
        return when {
            isInitialized -> null
            initFailureMessage != null -> initFailureMessage
            else -> buildNotInitializedMessage()
        }
    }

    fun showInterstitial(
        activity: Activity,
        onAdShowFailed: (errorMessage: String) -> Unit = {},
        onAdClosed: () -> Unit = {},
    ): Boolean {
        if (!AppLovinSdk.getInstance(activity).isInitialized) {
            onAdClosed()
            return false
        }
        val ad = interstitialAd
        if (refreshExpiredInterstitial()) {
            onAdClosed()
            return false
        }
        if (ad == null || !ad.isReady) {
            onAdClosed()
            return false
        }
        isFullscreenAdShowing = true
        interstitialOnAdShowFailed = onAdShowFailed
        interstitialOnAdClosed = onAdClosed
        ad.showAd(activity)
        return true
    }

    @Composable
    fun showBanner(
        modifier: Modifier = Modifier,
        adUnitId: String? = null,
        adSize: AdSize? = null,
        onAdFailedToLoad: ((String) -> Unit)? = null,
    ) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val isInitialized = remember { mutableStateOf(AppLovinSdk.getInstance(context).isInitialized) }
        val initErrorState = remember { mutableStateOf<String?>(null) }
        val isMrec = remember(adSize) { adSize == AdSize.MEDIUM_RECTANGLE }
        val adViewState = remember { mutableStateOf<MaxAdView?>(null) }
        val adContainerState = remember { mutableStateOf<FrameLayout?>(null) }
        val heightPx = remember(configuration, isMrec) {
            val heightDp = if (isMrec) 250 else if (configuration.smallestScreenWidthDp >= 600) 90 else 50
            dpToPx(context, heightDp)
        }
        val widthPx = remember(configuration, isMrec) {
            if (isMrec) dpToPx(context, 300) else ViewGroup.LayoutParams.MATCH_PARENT
        }

        LaunchedEffect(Unit) {
            val initError = awaitInitializationError()
            initErrorState.value = initError
            isInitialized.value = initError == null
        }

        LaunchedEffect(initErrorState.value) {
            initErrorState.value?.let { onAdFailedToLoad?.invoke(it) }
        }

        if (isInitialized.value) {
            val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.BANNER_AD_UNIT_ID
            if (resolvedAdUnitId.isBlank()) {
                onAdFailedToLoad?.invoke("AppLovin banner ad unit id is blank.")
            } else {
                val requestKey = remember(resolvedAdUnitId, isMrec) { "$resolvedAdUnitId:$isMrec" }
                val retryAttempt = remember(requestKey) { mutableStateOf(0) }
                val retrySignal = remember(requestKey) { mutableStateOf(0) }

                LaunchedEffect(requestKey, retrySignal.value) {
                    val signal = retrySignal.value
                    if (signal <= 0) {
                        return@LaunchedEffect
                    }
                    delay(retryDelayMs((retryAttempt.value - 1).coerceAtLeast(0)))
                    adViewState.value?.let { requestAdViewLoad(it, isMrec) }
                }

                LaunchedEffect(requestKey, adContainerState.value, heightPx, widthPx) {
                    val container = adContainerState.value ?: return@LaunchedEffect

                        val existing = adViewState.value
                        if (existing == null || existing.tag != requestKey) {
                            existing?.destroy()
                            val adFormat = if (isMrec) MaxAdFormat.MREC else MaxAdFormat.BANNER
                            val newAdView = MaxAdView(resolvedAdUnitId, adFormat).also { adView ->
                                adView.tag = requestKey
                                adView.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
                                adView.setBackgroundColor(Color.TRANSPARENT)
                                adView.setListener(object : MaxAdViewAdListener {
                                    override fun onAdLoaded(ad: MaxAd) {
                                        retryAttempt.value = 0
                                        FirebaseAnalyticsManager.logAdLoad(
                                            adType = if (isMrec) "mrec" else "banner",
                                            adUnitId = resolvedAdUnitId,
                                            adNetwork = "applovin",
                                            success = true,
                                        )
                                    }

                                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                                        onAdFailedToLoad?.invoke(error.message)
                                        retryAttempt.value += 1
                                        retrySignal.value += 1
                                        FirebaseAnalyticsManager.logAdLoad(
                                            adType = if (isMrec) "mrec" else "banner",
                                            adUnitId = resolvedAdUnitId,
                                            adNetwork = "applovin",
                                            success = false,
                                            errorMessage = error.message,
                                            errorCode = error.code,
                                        )
                                    }

                                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
                                override fun onAdClicked(ad: MaxAd) {
                                    FirebaseAnalyticsManager.logAdClicked(
                                        adType = if (isMrec) "mrec" else "banner",
                                        adUnitId = resolvedAdUnitId,
                                        adNetwork = "applovin",
                                    )
                                }
                                override fun onAdExpanded(ad: MaxAd) {}
                                override fun onAdCollapsed(ad: MaxAd) {}
                                override fun onAdDisplayed(ad: MaxAd) {}
                                override fun onAdHidden(ad: MaxAd) {}
                            })
                            requestAdViewLoad(adView, isMrec)
                        }
                        container.removeAllViews()
                        container.addView(newAdView)
                        adViewState.value = newAdView
                    } else {
                        val params = existing.layoutParams as? FrameLayout.LayoutParams
                        if (params == null || params.width != widthPx || params.height != heightPx) {
                            existing.layoutParams = FrameLayout.LayoutParams(widthPx, heightPx)
                        }
                        val parent = existing.parent as? ViewGroup
                        if (parent != container) {
                            parent?.removeView(existing)
                            container.removeAllViews()
                            container.addView(existing)
                        }
                    }
                }

                AndroidView(
                    modifier = modifier,
                    factory = { viewContext ->
                        FrameLayout(viewContext).also { container ->
                            adContainerState.value = container
                        }
                    },
                    update = { container ->
                        if (adContainerState.value !== container) {
                            adContainerState.value = container
                        }
                    },
                )
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                adViewState.value?.destroy()
                adViewState.value = null
            }
        }
    }

    @Composable
    fun showMrec(
        modifier: Modifier = Modifier,
        adUnitId: String? = null,
        onAdFailedToLoad: ((String) -> Unit)? = null,
    ) {
        val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.MREC_AD_UNIT_ID
        showBanner(
            modifier = modifier,
            adUnitId = resolvedAdUnitId,
            adSize = AdSize.MEDIUM_RECTANGLE,
            onAdFailedToLoad = onAdFailedToLoad,
        )
    }

    @Composable
    fun showNativeAd(
        modifier: Modifier = Modifier,
        adUnitId: String? = null,
        onAdFailedToLoad: ((String) -> Unit)? = null,
    ) {
        val context = LocalContext.current
        val isInitialized = remember { mutableStateOf(AppLovinSdk.getInstance(context).isInitialized) }
        val initErrorState = remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val initError = awaitInitializationError()
            initErrorState.value = initError
            isInitialized.value = initError == null
        }

        LaunchedEffect(initErrorState.value) {
            initErrorState.value?.let { onAdFailedToLoad?.invoke(it) }
        }

        if (isInitialized.value) {
            val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.NATIVE_AD_UNIT_ID
            if (resolvedAdUnitId.isBlank()) {
                onAdFailedToLoad?.invoke("AppLovin native ad unit id is blank.")
            } else {
                val nativeAdLoader = remember(resolvedAdUnitId) { MaxNativeAdLoader(resolvedAdUnitId) }
                val nativeAdContainerState = remember { mutableStateOf<FrameLayout?>(null) }
                val nativeAdViewState = remember(resolvedAdUnitId) { mutableStateOf<MaxNativeAdView?>(null) }
                val loadedNativeAdState = remember(resolvedAdUnitId) { mutableStateOf<MaxAd?>(null) }
                val isLoading = remember(resolvedAdUnitId) { mutableStateOf(false) }
                val retryAttempt = remember(resolvedAdUnitId) { mutableStateOf(0) }
                val retrySignal = remember(resolvedAdUnitId) { mutableStateOf(0) }

                DisposableEffect(resolvedAdUnitId) {
                    nativeAdLoader.setNativeAdListener(object : MaxNativeAdListener() {
                        override fun onNativeAdLoaded(nativeAdView: MaxNativeAdView?, nativeAd: MaxAd) {
                            val container = nativeAdContainerState.value
                            if (container != null && nativeAdView != null) {
                                loadedNativeAdState.value?.let { nativeAdLoader.destroy(it) }
                                loadedNativeAdState.value = nativeAd
                                nativeAdViewState.value = nativeAdView
                                container.removeAllViews()
                                container.addView(nativeAdView)
                                FirebaseAnalyticsManager.logAdLoad(
                                    adType = "native",
                                    adUnitId = resolvedAdUnitId,
                                    adNetwork = "applovin",
                                    success = true,
                                )
                            }
                            isLoading.value = false
                            retryAttempt.value = 0
                        }

                        override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                            isLoading.value = false
                            onAdFailedToLoad?.invoke(error.message)
                            retryAttempt.value += 1
                            retrySignal.value += 1
                            FirebaseAnalyticsManager.logAdLoad(
                                adType = "native",
                                adUnitId = resolvedAdUnitId,
                                adNetwork = "applovin",
                                success = false,
                                errorMessage = error.message,
                                errorCode = error.code,
                            )
                        }

                        override fun onNativeAdClicked(nativeAd: MaxAd) {
                            FirebaseAnalyticsManager.logAdClicked(
                                adType = "native",
                                adUnitId = resolvedAdUnitId,
                                adNetwork = "applovin",
                            )
                        }
                    })
                    onDispose {
                        loadedNativeAdState.value?.let { nativeAdLoader.destroy(it) }
                        nativeAdViewState.value = null
                        nativeAdLoader.destroy()
                    }
                }

                LaunchedEffect(
                    resolvedAdUnitId,
                    nativeAdContainerState.value,
                    nativeAdViewState.value,
                    retrySignal.value,
                ) {
                    val signal = retrySignal.value
                    if (signal > 0) {
                        delay(retryDelayMs((retryAttempt.value - 1).coerceAtLeast(0)))
                    }
                    val container = nativeAdContainerState.value ?: return@LaunchedEffect
                    val nativeAdView = nativeAdViewState.value
                    if (nativeAdView != null) {
                        val parent = nativeAdView.parent as? ViewGroup
                        if (parent != container) {
                            parent?.removeView(nativeAdView)
                            container.removeAllViews()
                            container.addView(nativeAdView)
                        }
                        return@LaunchedEffect
                    }
                    if (isLoading.value || loadedNativeAdState.value != null) {
                        return@LaunchedEffect
                    }
                    if (!shouldRequestNativeLoad()) {
                        return@LaunchedEffect
                    }
                    isLoading.value = true
                    nativeAdLoader.loadAd(createNativeAdView(context))
                }

                AndroidView(
                    modifier = modifier,
                    factory = { viewContext ->
                        FrameLayout(viewContext).also { container ->
                            nativeAdContainerState.value = container
                        }
                    },
                    update = { container ->
                        if (nativeAdContainerState.value !== container) {
                            nativeAdContainerState.value = container
                        }
                    },
                )
            }
        }
    }

    fun showReward(
        activity: Activity,
        onRewarded: (type: String, amount: Int) -> Unit = { _, _ -> },
        onAdClosed: () -> Unit = {},
    ): Boolean {
        if (!AppLovinSdk.getInstance(activity).isInitialized) {
            onAdClosed()
            return false
        }
        val ad = rewardedAd
        if (refreshExpiredRewarded()) {
            onAdClosed()
            return false
        }
        if (ad == null || !ad.isReady) {
            onAdClosed()
            return false
        }
        isFullscreenAdShowing = true
        rewardedOnAdClosed = onAdClosed
        rewardedOnUserRewarded = onRewarded
        ad.showAd(activity)
        return true
    }

    fun loadInterstitial(context: Context): Boolean {
        if (!AppLovinSdk.getInstance(context).isInitialized || config.INTERSTITIAL_AD_UNIT_ID.isBlank()) {
            return false
        }

        val ad = interstitialAd
        if (ad == null) {
            createInterstitialAd(context.applicationContext)
            return interstitialAd != null
        }
        if (isAdExpired(interstitialLoadedAtMs)) {
            interstitialLoadedAtMs = 0L
        }
        if (ad.isReady) {
            return true
        }

        return requestInterstitialLoad(ad)
    }

    fun loadRewarded(context: Context): Boolean {
        if (!AppLovinSdk.getInstance(context).isInitialized || config.REWARDED_AD_UNIT_ID.isBlank()) {
            return false
        }

        val ad = rewardedAd
        if (ad == null) {
            createRewardedAd()
            return rewardedAd != null
        }
        if (isAdExpired(rewardedLoadedAtMs)) {
            rewardedLoadedAtMs = 0L
        }
        if (ad.isReady) {
            return true
        }

        return requestRewardedLoad(ad)
    }

    fun loadAppOpenAd(context: Context): Boolean {
        if (!AppLovinSdk.getInstance(context).isInitialized || config.APP_OPEN_AD_UNIT_ID.isBlank()) {
            return false
        }

        val ad = appOpenAd
        if (ad == null) {
            createAppOpenAd()
            return appOpenAd != null
        }
        if (isAdExpired(appOpenLoadedAtMs)) {
            appOpenLoadedAtMs = 0L
        }
        if (ad.isReady) {
            return true
        }

        return requestAppOpenLoad(ad)
    }

    fun showMediationDebugger(context: Context) {
        AppLovinSdk.getInstance(context).showMediationDebugger()
    }

    fun showAppOpenAd(activity: Activity, onAdClosed: () -> Unit = {}): Boolean {
        if (isAppOpenShowing || activity.isFinishing || activity.isDestroyed) {
            onAdClosed()
            return false
        }
        if (!AppLovinSdk.getInstance(activity).isInitialized) {
            onAdClosed()
            return false
        }
        val ad = appOpenAd
        if (refreshExpiredAppOpenAd()) {
            onAdClosed()
            return false
        }
        if (ad == null || !ad.isReady) {
            onAdClosed()
            return false
        }
        isAppOpenShowing = true
        isFullscreenAdShowing = true
        appOpenOnAdClosedInternal = onAdClosed
        ad.showAd()
        return true
    }

    private fun requestInterstitialLoad(ad: MaxInterstitialAd, force: Boolean = false): Boolean {
        if (!force && !shouldRequestInterstitialLoad()) {
            return false
        }
        if (force) {
            lastInterstitialLoadRequestedAtMs = SystemClock.elapsedRealtime()
        }
        ad.loadAd()
        return true
    }

    private fun requestRewardedLoad(ad: MaxRewardedAd, force: Boolean = false): Boolean {
        if (!force && !shouldRequestRewardedLoad()) {
            return false
        }
        if (force) {
            lastRewardedLoadRequestedAtMs = SystemClock.elapsedRealtime()
        }
        ad.loadAd()
        return true
    }

    private fun requestAppOpenLoad(ad: MaxAppOpenAd, force: Boolean = false): Boolean {
        if (!force && !shouldRequestAppOpenLoad()) {
            return false
        }
        if (force) {
            lastAppOpenLoadRequestedAtMs = SystemClock.elapsedRealtime()
        }
        ad.loadAd()
        return true
    }

    private fun requestAdViewLoad(adView: MaxAdView, isMrec: Boolean): Boolean {
        val shouldRequest = if (isMrec) {
            shouldRequestMrecLoad()
        } else {
            shouldRequestBannerLoad()
        }
        if (!shouldRequest) {
            return false
        }
        adView.loadAd()
        return true
    }

    private fun shouldRequestInterstitialLoad(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastInterstitialLoadRequestedAtMs != 0L &&
            now - lastInterstitialLoadRequestedAtMs < LOAD_REQUEST_MIN_INTERVAL_MS
        ) {
            return false
        }
        lastInterstitialLoadRequestedAtMs = now
        return true
    }

    private fun shouldRequestRewardedLoad(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastRewardedLoadRequestedAtMs != 0L &&
            now - lastRewardedLoadRequestedAtMs < LOAD_REQUEST_MIN_INTERVAL_MS
        ) {
            return false
        }
        lastRewardedLoadRequestedAtMs = now
        return true
    }

    private fun shouldRequestAppOpenLoad(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastAppOpenLoadRequestedAtMs != 0L &&
            now - lastAppOpenLoadRequestedAtMs < LOAD_REQUEST_MIN_INTERVAL_MS
        ) {
            return false
        }
        lastAppOpenLoadRequestedAtMs = now
        return true
    }

    private fun shouldRequestBannerLoad(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastBannerLoadRequestedAtMs != 0L &&
            now - lastBannerLoadRequestedAtMs < LOAD_REQUEST_MIN_INTERVAL_MS
        ) {
            return false
        }
        lastBannerLoadRequestedAtMs = now
        return true
    }

    private fun shouldRequestMrecLoad(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastMrecLoadRequestedAtMs != 0L &&
            now - lastMrecLoadRequestedAtMs < LOAD_REQUEST_MIN_INTERVAL_MS
        ) {
            return false
        }
        lastMrecLoadRequestedAtMs = now
        return true
    }

    private fun shouldRequestNativeLoad(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastNativeLoadRequestedAtMs != 0L &&
            now - lastNativeLoadRequestedAtMs < LOAD_REQUEST_MIN_INTERVAL_MS
        ) {
            return false
        }
        lastNativeLoadRequestedAtMs = now
        return true
    }

    private fun isAdExpired(loadedAtMs: Long): Boolean {
        return loadedAtMs != 0L && SystemClock.elapsedRealtime() - loadedAtMs >= AD_EXPIRATION_MS
    }

    private fun refreshExpiredInterstitial(): Boolean {
        val ad = interstitialAd ?: return false
        if (!isAdExpired(interstitialLoadedAtMs)) {
            return false
        }
        interstitialLoadedAtMs = 0L
        requestInterstitialLoad(ad, force = true)
        return true
    }

    private fun refreshExpiredRewarded(): Boolean {
        val ad = rewardedAd ?: return false
        if (!isAdExpired(rewardedLoadedAtMs)) {
            return false
        }
        rewardedLoadedAtMs = 0L
        requestRewardedLoad(ad, force = true)
        return true
    }

    private fun refreshExpiredAppOpenAd(): Boolean {
        val ad = appOpenAd ?: return false
        if (!isAdExpired(appOpenLoadedAtMs)) {
            return false
        }
        appOpenLoadedAtMs = 0L
        requestAppOpenLoad(ad, force = true)
        return true
    }

    private fun retryDelayMs(retryAttempt: Int): Long {
        val multiplier = 1L shl retryAttempt.coerceAtMost(5)
        return (LOAD_RETRY_INITIAL_DELAY_MS * multiplier).coerceAtMost(LOAD_RETRY_MAX_DELAY_MS)
    }

    private fun scheduleInterstitialRetry(ad: MaxInterstitialAd) {
        if (interstitialRetryRunnable != null) {
            return
        }
        val delayMs = retryDelayMs(interstitialRetryAttempt++)
        interstitialRetryRunnable = Runnable {
            interstitialRetryRunnable = null
            requestInterstitialLoad(ad, force = true)
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun scheduleRewardedRetry(ad: MaxRewardedAd) {
        if (rewardedRetryRunnable != null) {
            return
        }
        val delayMs = retryDelayMs(rewardedRetryAttempt++)
        rewardedRetryRunnable = Runnable {
            rewardedRetryRunnable = null
            requestRewardedLoad(ad, force = true)
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun scheduleAppOpenRetry(ad: MaxAppOpenAd) {
        if (appOpenRetryRunnable != null) {
            return
        }
        val delayMs = retryDelayMs(appOpenRetryAttempt++)
        appOpenRetryRunnable = Runnable {
            appOpenRetryRunnable = null
            requestAppOpenLoad(ad, force = true)
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun createInterstitialAd(context: Context) {
        if (config.INTERSTITIAL_AD_UNIT_ID.isBlank()) {
            return
        }
        interstitialAd = MaxInterstitialAd(config.INTERSTITIAL_AD_UNIT_ID).also { interstitial ->
            interstitial.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    interstitialLoadedAtMs = SystemClock.elapsedRealtime()
                    interstitialRetryAttempt = 0
                    interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    interstitialRetryRunnable = null
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "interstitial",
                        adUnitId = config.INTERSTITIAL_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    interstitialLoadedAtMs = 0L
                    scheduleInterstitialRetry(interstitial)
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "interstitial",
                        adUnitId = config.INTERSTITIAL_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = false,
                        errorMessage = error.message,
                        errorCode = error.code,
                    )
                }

                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    isFullscreenAdShowing = false
                    interstitialLoadedAtMs = 0L
                    interstitialOnAdShowFailed(error.message)
                    interstitialOnAdShowFailed = {}
                    interstitialOnAdClosed()
                    interstitialOnAdClosed = {}
                    requestInterstitialLoad(interstitial, force = true)
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "interstitial",
                        adNetwork = "applovin",
                        success = false,
                        errorMessage = error.message,
                    )
                }

                override fun onAdHidden(ad: MaxAd) {
                    isFullscreenAdShowing = false
                    interstitialLoadedAtMs = 0L
                    interstitialOnAdShowFailed = {}
                    interstitialOnAdClosed()
                    interstitialOnAdClosed = {}
                    requestInterstitialLoad(interstitial, force = true)
                }

                override fun onAdDisplayed(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "interstitial",
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdClicked(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdClicked(
                        adType = "interstitial",
                        adUnitId = config.INTERSTITIAL_AD_UNIT_ID,
                        adNetwork = "applovin",
                    )
                }
            })
            requestInterstitialLoad(interstitial)
        }
    }

    private fun createRewardedAd() {
        if (config.REWARDED_AD_UNIT_ID.isBlank()) {
            return
        }
        rewardedAd = MaxRewardedAd.getInstance(config.REWARDED_AD_UNIT_ID).also { rewarded ->
            rewarded.setListener(object : MaxRewardedAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    rewardedLoadedAtMs = SystemClock.elapsedRealtime()
                    rewardedRetryAttempt = 0
                    rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    rewardedRetryRunnable = null
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "rewarded",
                        adUnitId = config.REWARDED_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    rewardedLoadedAtMs = 0L
                    scheduleRewardedRetry(rewarded)
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "rewarded",
                        adUnitId = config.REWARDED_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = false,
                        errorMessage = error.message,
                        errorCode = error.code,
                    )
                }

                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    isFullscreenAdShowing = false
                    rewardedLoadedAtMs = 0L
                    rewardedOnAdClosed()
                    rewardedOnAdClosed = {}
                    requestRewardedLoad(rewarded, force = true)
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "rewarded",
                        adNetwork = "applovin",
                        success = false,
                        errorMessage = error.message,
                    )
                }

                override fun onAdHidden(ad: MaxAd) {
                    isFullscreenAdShowing = false
                    rewardedLoadedAtMs = 0L
                    rewardedOnAdClosed()
                    rewardedOnAdClosed = {}
                    requestRewardedLoad(rewarded, force = true)
                }

                override fun onAdDisplayed(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "rewarded",
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdClicked(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdClicked(
                        adType = "rewarded",
                        adUnitId = config.REWARDED_AD_UNIT_ID,
                        adNetwork = "applovin",
                    )
                }

                override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                    rewardedOnUserRewarded(reward.label, reward.amount)
                }
            })
            requestRewardedLoad(rewarded)
        }
    }

    private fun createAppOpenAd() {
        if (config.APP_OPEN_AD_UNIT_ID.isBlank()) {
            return
        }
        appOpenAd = MaxAppOpenAd(config.APP_OPEN_AD_UNIT_ID).also { appOpen ->
            appOpen.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    appOpenLoadedAtMs = SystemClock.elapsedRealtime()
                    appOpenRetryAttempt = 0
                    appOpenRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    appOpenRetryRunnable = null
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "app_open",
                        adUnitId = config.APP_OPEN_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    appOpenLoadedAtMs = 0L
                    scheduleAppOpenRetry(appOpen)
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "app_open",
                        adUnitId = config.APP_OPEN_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = false,
                        errorMessage = error.message,
                        errorCode = error.code,
                    )
                }

                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    isAppOpenShowing = false
                    isFullscreenAdShowing = false
                    appOpenLoadedAtMs = 0L
                    appOpenOnAdClosedInternal()
                    appOpenOnAdClosedInternal = {}
                    requestAppOpenLoad(appOpen, force = true)
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "app_open",
                        adNetwork = "applovin",
                        success = false,
                        errorMessage = error.message,
                    )
                }

                override fun onAdHidden(ad: MaxAd) {
                    isAppOpenShowing = false
                    isFullscreenAdShowing = false
                    appOpenLoadedAtMs = 0L
                    appOpenOnAdClosedInternal()
                    appOpenOnAdClosedInternal = {}
                    requestAppOpenLoad(appOpen, force = true)
                }

                override fun onAdDisplayed(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "app_open",
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdClicked(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdClicked(
                        adType = "app_open",
                        adUnitId = config.APP_OPEN_AD_UNIT_ID,
                        adNetwork = "applovin",
                    )
                }
            })
            requestAppOpenLoad(appOpen)
        }
    }

    private fun createNativeAdView(context: Context): MaxNativeAdView {
        val binder = MaxNativeAdViewBinder.Builder(R.layout.applovin_native_ad_view)
            .setTitleTextViewId(R.id.applovin_native_title)
            .setBodyTextViewId(R.id.applovin_native_body)
            .setAdvertiserTextViewId(R.id.applovin_native_advertiser)
            .setIconImageViewId(R.id.applovin_native_icon)
            .setMediaContentViewGroupId(R.id.applovin_native_media)
            .setOptionsContentViewGroupId(R.id.applovin_native_options)
            .setCallToActionButtonId(R.id.applovin_native_cta)
            .setStarRatingContentViewGroupId(R.id.applovin_native_star_rating)
            .build()
        return MaxNativeAdView(binder, context)
    }

    private fun getAppLovinSdkKey(context: Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            appInfo.metaData?.getString(APPLOVIN_SDK_KEY_META)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to read AppLovin SDK key from manifest", e)
            null
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).roundToInt()
    }
}

data class ApplovinMaxConfig(
    var SDK_KEY: String = "",
    var INTERSTITIAL_AD_UNIT_ID: String = "",
    var BANNER_AD_UNIT_ID: String = "",
    var REWARDED_AD_UNIT_ID: String = "",
    var NATIVE_AD_UNIT_ID: String = "",
    var APP_OPEN_AD_UNIT_ID: String = "",
    var MREC_AD_UNIT_ID: String = "",
    var DEBUG: ApplovinMaxDebug = ApplovinMaxDebug(),
)

data class ApplovinMaxDebug(
    var useTestAds: Boolean = false,
    var testDeviceAdvertisingIds: List<String> = emptyList(),
    var useEmptyIds: Boolean = false,
)
