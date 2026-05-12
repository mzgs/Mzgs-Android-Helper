package com.mzgs.helper

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

object AdmobMediation {

    const val TAG = "AdmobMediation"
    private const val ADMOB_APP_ID_KEY = "com.google.android.gms.ads.APPLICATION_ID"
    private const val INIT_WAIT_TIMEOUT_MS = 120_000L
    private const val INIT_POLL_INTERVAL_MS = 1_000L
    private const val LOAD_REQUEST_MIN_INTERVAL_MS = 30_000L
    private const val LOAD_RETRY_INITIAL_DELAY_MS = 8_000L
    private const val LOAD_RETRY_MAX_DELAY_MS = 256_000L
    private const val AD_EXPIRATION_MS = 3_600_000L
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_MREC_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    var config: AdmobConfig = AdmobConfig()

    @Volatile private var isAppOpenShowing = false
    @Volatile private var isInitialized = false
    @Volatile private var isInitializing = false
    @Volatile private var initFailureMessage: String? = null
    private val pendingInitCallbacks = mutableListOf<() -> Unit>()

    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var rewardedAd: RewardedAd? = null
    @Volatile private var appOpenAd: AppOpenAd? = null
    @Volatile private var isInterstitialLoading = false
    @Volatile private var isRewardedLoading = false
    @Volatile private var isAppOpenLoading = false
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

    fun initialize(context: Context, onInitComplete: () -> Unit = {}) {
        val appContext = context.applicationContext
        initFailureMessage = null
        if (config.DEBUG.useTestAds && MzgsHelper.isDebug(appContext)) {
            Log.d(TAG, "Using test AdMob ad unit IDs.")
            config.INTERSTITIAL_AD_UNIT_ID = TEST_INTERSTITIAL_AD_UNIT_ID
            config.BANNER_AD_UNIT_ID = TEST_BANNER_AD_UNIT_ID
            config.REWARDED_AD_UNIT_ID = TEST_REWARDED_AD_UNIT_ID
            config.NATIVE_AD_UNIT_ID = TEST_NATIVE_AD_UNIT_ID
            config.APP_OPEN_AD_UNIT_ID = TEST_APP_OPEN_AD_UNIT_ID
            config.MREC_AD_UNIT_ID = TEST_MREC_AD_UNIT_ID
        }

        if (config.DEBUG.useEmptyIds && MzgsHelper.isDebug(appContext)) {
            Log.d(TAG, "Using empty AdMob ad unit IDs.")
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

        val resolvedAppId = getAdMobAppId(appContext)
        if (resolvedAppId.isNullOrBlank()) {
            initFailureMessage = "AdMob app ID not found; set $ADMOB_APP_ID_KEY in the manifest."
            Log.w(TAG, initFailureMessage!!)
            isInitializing = false
            isInitialized = false
            drainInitCallbacks()
            return
        }

        MobileAds.initialize(appContext) { initializationStatus ->
            for ((adapterClass, status) in initializationStatus.adapterStatusMap) {
                Log.d(
                    TAG,
                    "Adapter: $adapterClass, Status: ${status.description}, Latency: ${status.latency}ms",
                )
            }
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
    fun setInitListener(onAppLovinMaxInitComplete: () -> Unit) {
        ApplovinMaxMediation.setInitListener(onAppLovinMaxInitComplete)
    }

    /**
     * Registers a callback for AdMob initialization.
     * If AdMob is already initialized, the callback is invoked immediately.
     */
    fun setAdmobInitListener(onInitComplete: () -> Unit) {
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
        return "AdMob SDK not initialized. Call AdmobMediation.initialize(context) before showing ads."
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

    fun loadInterstitial(context: Context): Boolean {
        return requestInterstitialLoad(context)
    }

    private fun requestInterstitialLoad(context: Context, force: Boolean = false): Boolean {
        if (!isInitialized || config.INTERSTITIAL_AD_UNIT_ID.isBlank()) {
            return false
        }
        if (isAdExpired(interstitialLoadedAtMs)) {
            interstitialAd = null
            interstitialLoadedAtMs = 0L
        }
        if (isInterstitialLoading || interstitialAd != null) {
            return true
        }
        if (!force && !shouldRequestInterstitialLoad()) {
            return false
        }
        if (force) {
            lastInterstitialLoadRequestedAtMs = SystemClock.elapsedRealtime()
        }
        isInterstitialLoading = true
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            config.INTERSTITIAL_AD_UNIT_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    interstitialLoadedAtMs = SystemClock.elapsedRealtime()
                    isInterstitialLoading = false
                    interstitialRetryAttempt = 0
                    interstitialRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    interstitialRetryRunnable = null
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    interstitialLoadedAtMs = 0L
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
                    scheduleInterstitialRetry(context.applicationContext)
                }
            },
        )
        return true
    }

    fun loadRewarded(context: Context): Boolean {
        return requestRewardedLoad(context)
    }

    private fun requestRewardedLoad(context: Context, force: Boolean = false): Boolean {
        if (!isInitialized || config.REWARDED_AD_UNIT_ID.isBlank()) {
            return false
        }
        if (isAdExpired(rewardedLoadedAtMs)) {
            rewardedAd = null
            rewardedLoadedAtMs = 0L
        }
        if (isRewardedLoading || rewardedAd != null) {
            return true
        }
        if (!force && !shouldRequestRewardedLoad()) {
            return false
        }
        if (force) {
            lastRewardedLoadRequestedAtMs = SystemClock.elapsedRealtime()
        }
        isRewardedLoading = true
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            config.REWARDED_AD_UNIT_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    rewardedLoadedAtMs = SystemClock.elapsedRealtime()
                    isRewardedLoading = false
                    rewardedRetryAttempt = 0
                    rewardedRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    rewardedRetryRunnable = null
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    rewardedLoadedAtMs = 0L
                    isRewardedLoading = false
                    Log.w(TAG, "Rewarded failed to load: ${loadAdError.message}")
                    scheduleRewardedRetry(context.applicationContext)
                }
            },
        )
        return true
    }

    fun loadAppOpenAd(context: Context): Boolean {
        return requestAppOpenAdLoad(context)
    }

    private fun requestAppOpenAdLoad(context: Context, force: Boolean = false): Boolean {
        if (!isInitialized || config.APP_OPEN_AD_UNIT_ID.isBlank()) {
            return false
        }
        if (isAdExpired(appOpenLoadedAtMs)) {
            appOpenAd = null
            appOpenLoadedAtMs = 0L
        }
        if (isAppOpenLoading || appOpenAd != null) {
            return true
        }
        if (!force && !shouldRequestAppOpenLoad()) {
            return false
        }
        if (force) {
            lastAppOpenLoadRequestedAtMs = SystemClock.elapsedRealtime()
        }
        isAppOpenLoading = true
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            config.APP_OPEN_AD_UNIT_ID,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenLoadedAtMs = SystemClock.elapsedRealtime()
                    isAppOpenLoading = false
                    appOpenRetryAttempt = 0
                    appOpenRetryRunnable?.let { mainHandler.removeCallbacks(it) }
                    appOpenRetryRunnable = null
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    appOpenAd = null
                    appOpenLoadedAtMs = 0L
                    isAppOpenLoading = false
                    Log.w(TAG, "App open failed to load: ${loadAdError.message}")
                    scheduleAppOpenRetry(context.applicationContext)
                }
            },
        )
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

    private fun shouldRequestBannerLikeLoad(adSize: AdSize): Boolean {
        return if (adSize == AdSize.MEDIUM_RECTANGLE) {
            shouldRequestMrecLoad()
        } else {
            shouldRequestBannerLoad()
        }
    }

    private fun isAdExpired(loadedAtMs: Long): Boolean {
        return loadedAtMs != 0L && SystemClock.elapsedRealtime() - loadedAtMs >= AD_EXPIRATION_MS
    }

    private fun refreshExpiredInterstitial(context: Context): Boolean {
        if (interstitialAd == null) {
            return false
        }
        if (!isAdExpired(interstitialLoadedAtMs)) {
            return false
        }
        interstitialAd = null
        interstitialLoadedAtMs = 0L
        requestInterstitialLoad(context.applicationContext, force = true)
        return true
    }

    private fun refreshExpiredRewarded(context: Context): Boolean {
        if (rewardedAd == null) {
            return false
        }
        if (!isAdExpired(rewardedLoadedAtMs)) {
            return false
        }
        rewardedAd = null
        rewardedLoadedAtMs = 0L
        requestRewardedLoad(context.applicationContext, force = true)
        return true
    }

    private fun refreshExpiredAppOpenAd(context: Context): Boolean {
        if (appOpenAd == null) {
            return false
        }
        if (!isAdExpired(appOpenLoadedAtMs)) {
            return false
        }
        appOpenAd = null
        appOpenLoadedAtMs = 0L
        requestAppOpenAdLoad(context.applicationContext, force = true)
        return true
    }

    private fun retryDelayMs(retryAttempt: Int): Long {
        val multiplier = 1L shl retryAttempt.coerceAtMost(5)
        return (LOAD_RETRY_INITIAL_DELAY_MS * multiplier).coerceAtMost(LOAD_RETRY_MAX_DELAY_MS)
    }

    private fun scheduleInterstitialRetry(context: Context) {
        if (interstitialRetryRunnable != null) {
            return
        }
        val delayMs = retryDelayMs(interstitialRetryAttempt++)
        interstitialRetryRunnable = Runnable {
            interstitialRetryRunnable = null
            requestInterstitialLoad(context, force = true)
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun scheduleRewardedRetry(context: Context) {
        if (rewardedRetryRunnable != null) {
            return
        }
        val delayMs = retryDelayMs(rewardedRetryAttempt++)
        rewardedRetryRunnable = Runnable {
            rewardedRetryRunnable = null
            requestRewardedLoad(context, force = true)
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    private fun scheduleAppOpenRetry(context: Context) {
        if (appOpenRetryRunnable != null) {
            return
        }
        val delayMs = retryDelayMs(appOpenRetryAttempt++)
        appOpenRetryRunnable = Runnable {
            appOpenRetryRunnable = null
            requestAppOpenAdLoad(context, force = true)
        }.also { mainHandler.postDelayed(it, delayMs) }
    }

    fun showInterstitial(
        activity: Activity,
        onAdShowFailed: (errorMessage: String) -> Unit = {},
        onAdClosed: () -> Unit = {},
    ): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "MobileAds not initialized; skipping interstitial.")
            onAdClosed()
            return false
        }
        val ad = interstitialAd
        if (refreshExpiredInterstitial(activity)) {
            onAdClosed()
            return false
        }
        if (ad == null) {
            onAdClosed()
            return false
        }
        interstitialAd = null
        interstitialLoadedAtMs = 0L
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onAdClosed()
                requestInterstitialLoad(activity, force = true)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                onAdShowFailed(adError.message)
                onAdClosed()
                FirebaseAnalyticsManager.logEvent(
                    "interstitial_ad_failed_to_show",
                    Bundle().apply {
                        putString("ad_unit_id", config.INTERSTITIAL_AD_UNIT_ID)
                        putString("error_message", adError.message)
                    },
                )
                requestInterstitialLoad(activity, force = true)
            }
        }
        ad.show(activity)
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
        val isInitializedState = remember { mutableStateOf(isInitialized) }
        val initErrorState = remember { mutableStateOf<String?>(null) }
        val configuration = LocalConfiguration.current
        val resolvedAdSize = remember(adSize, configuration) {
            adSize
                ?: AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    configuration.screenWidthDp,
                )
        }
        val adViewState = remember { mutableStateOf<AdView?>(null) }

        LaunchedEffect(Unit) {
            val initError = awaitInitializationError()
            initErrorState.value = initError
            isInitializedState.value = initError == null
        }

        LaunchedEffect(initErrorState.value) {
            initErrorState.value?.let { onAdFailedToLoad?.invoke(it) }
        }

        if (isInitializedState.value) {
            val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.BANNER_AD_UNIT_ID
            if (resolvedAdUnitId.isBlank()) {
                onAdFailedToLoad?.invoke("AdMob banner ad unit id is blank.")
            } else {
                val requestKey = remember(resolvedAdUnitId, resolvedAdSize) {
                    "$resolvedAdUnitId:$resolvedAdSize"
                }
                val hasLoaded = remember(requestKey) { mutableStateOf(false) }
                val retryAttempt = remember(requestKey) { mutableStateOf(0) }
                val retrySignal = remember(requestKey) { mutableStateOf(0) }

                LaunchedEffect(requestKey, retrySignal.value) {
                    val signal = retrySignal.value
                    if (signal <= 0) {
                        return@LaunchedEffect
                    }
                    delay(retryDelayMs((retryAttempt.value - 1).coerceAtLeast(0)))
                    hasLoaded.value = false
                }

                fun requestAdViewLoad(adView: AdView) {
                    if (shouldRequestBannerLikeLoad(resolvedAdSize)) {
                        adView.loadAd(AdRequest.Builder().build())
                        hasLoaded.value = true
                    } else {
                        hasLoaded.value = true
                        retryAttempt.value = retryAttempt.value.coerceAtLeast(1)
                        retrySignal.value += 1
                    }
                }

                key(requestKey) {
                    AndroidView(
                        modifier = modifier,
                        factory = { viewContext ->
                            AdView(viewContext).also { adView ->
                                adViewState.value = adView
                                adView.adUnitId = resolvedAdUnitId
                                adView.setAdSize(resolvedAdSize)
                                adView.adListener = object : AdListener() {
                                    override fun onAdLoaded() {
                                        retryAttempt.value = 0
                                    }

                                    override fun onAdFailedToLoad(adError: LoadAdError) {
                                        onAdFailedToLoad?.invoke(adError.message)
                                        retryAttempt.value += 1
                                        retrySignal.value += 1
                                    }
                                }
                                if (!hasLoaded.value) {
                                    requestAdViewLoad(adView)
                                }
                            }
                        },
                        update = { adView ->
                            if (!hasLoaded.value) {
                                requestAdViewLoad(adView)
                            }
                        },
                    )
                }
            }
        }

        DisposableEffect(adViewState.value) {
            val adView = adViewState.value
            onDispose {
                adView?.destroy()
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
        val isInitializedState = remember { mutableStateOf(isInitialized) }
        val initErrorState = remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val initError = awaitInitializationError()
            initErrorState.value = initError
            isInitializedState.value = initError == null
        }

        LaunchedEffect(initErrorState.value) {
            initErrorState.value?.let { onAdFailedToLoad?.invoke(it) }
        }

        if (isInitializedState.value) {
            val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.NATIVE_AD_UNIT_ID
            if (resolvedAdUnitId.isBlank()) {
                onAdFailedToLoad?.invoke("AdMob native ad unit id is blank.")
            } else {
                val nativeAdState = remember(resolvedAdUnitId) { mutableStateOf<NativeAd?>(null) }
                val isLoading = remember(resolvedAdUnitId) { mutableStateOf(false) }
                val retryAttempt = remember(resolvedAdUnitId) { mutableStateOf(0) }
                val retrySignal = remember(resolvedAdUnitId) { mutableStateOf(0) }

                LaunchedEffect(resolvedAdUnitId, retrySignal.value) {
                    val signal = retrySignal.value
                    if (signal > 0) {
                        delay(retryDelayMs((retryAttempt.value - 1).coerceAtLeast(0)))
                    }
                    if (isLoading.value || nativeAdState.value != null) {
                        return@LaunchedEffect
                    }
                    if (!shouldRequestNativeLoad()) {
                        return@LaunchedEffect
                    }
                    isLoading.value = true
                    val adLoader = AdLoader.Builder(context, resolvedAdUnitId)
                        .forNativeAd { nativeAd ->
                            nativeAdState.value?.destroy()
                            nativeAdState.value = nativeAd
                            isLoading.value = false
                            retryAttempt.value = 0
                        }
                        .withNativeAdOptions(NativeAdOptions.Builder().build())
                        .withAdListener(object : AdListener() {
                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                isLoading.value = false
                                onAdFailedToLoad?.invoke(adError.message)
                                retryAttempt.value += 1
                                retrySignal.value += 1
                                FirebaseAnalyticsManager.logEvent(
                                    "native_ad_failed_to_load",
                                    Bundle().apply {
                                        putString("ad_unit_id", resolvedAdUnitId)
                                        putString("error_message", adError.message)
                                    },
                                )
                            }
                        })
                        .build()
                    adLoader.loadAd(AdRequest.Builder().build())
                }

                AndroidView(
                    modifier = modifier,
                    factory = { viewContext ->
                        val holder = createNativeAdViewHolder(viewContext)
                        val adView = holder.nativeAdView
                        adView.tag = holder
                        adView.visibility = View.GONE
                        adView
                    },
                    update = { adView ->
                        val holder = adView.tag as? NativeAdViewHolder ?: return@AndroidView
                        val nativeAd = nativeAdState.value
                        if (nativeAd == null) {
                            adView.visibility = View.GONE
                            return@AndroidView
                        }
                        adView.visibility = View.VISIBLE
                        bindNativeAd(holder, nativeAd)
                    },
                )

                DisposableEffect(nativeAdState.value) {
                    val activeAd = nativeAdState.value
                    onDispose {
                        activeAd?.destroy()
                    }
                }
            }
        }
    }

    private data class NativeAdViewHolder(
        val nativeAdView: NativeAdView,
        val mediaView: MediaView,
        val headlineView: TextView,
        val bodyView: TextView,
        val callToActionView: Button,
        val iconView: ImageView,
        val advertiserView: TextView,
        val starRatingView: RatingBar,
        val priceView: TextView,
        val storeView: TextView,
        val adChoicesView: AdChoicesView,
    )

    private fun createNativeAdViewHolder(context: Context): NativeAdViewHolder {
        fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).roundToInt()

        val nativeAdView = NativeAdView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }

        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)).apply {
                marginEnd = dpToPx(12)
            }
            visibility = View.GONE
        }

        val headlineView = TextView(context).apply {
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val adLabelView = TextView(context).apply {
            text = "Ad"
            setTextColor(Color.DKGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            setBackgroundColor(Color.LTGRAY)
        }

        val headlineRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        headlineRow.addView(adLabelView)
        headlineRow.addView(headlineView.apply {
            setPadding(dpToPx(8), 0, 0, 0)
        })

        val advertiserView = TextView(context).apply {
            setTextColor(Color.DKGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val headerTextLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            )
        }
        headerTextLayout.addView(headlineRow)
        headerTextLayout.addView(advertiserView)
        headerLayout.addView(iconView)
        headerLayout.addView(headerTextLayout)

        val mediaView = MediaView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
            ).apply {
                weight = 1f
                topMargin = dpToPx(8)
            }
            setImageScaleType(ImageView.ScaleType.CENTER_CROP)
            minimumHeight = dpToPx(120)
        }

        val bodyView = TextView(context).apply {
            setTextColor(Color.DKGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dpToPx(8)
            }
        }

        val starRatingView = RatingBar(context, null, android.R.attr.ratingBarStyleSmall).apply {
            numStars = 5
            stepSize = 0.5f
            setIsIndicator(true)
            visibility = View.GONE
        }

        val priceView = TextView(context).apply {
            setTextColor(Color.DKGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
        }

        val storeView = TextView(context).apply {
            setTextColor(Color.DKGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            visibility = View.GONE
        }

        val metaLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dpToPx(6)
            }
        }
        metaLayout.addView(starRatingView)
        metaLayout.addView(priceView.apply { setPadding(dpToPx(8), 0, 0, 0) })
        metaLayout.addView(storeView.apply { setPadding(dpToPx(8), 0, 0, 0) })

        val callToActionView = Button(context).apply {
            isAllCaps = false
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dpToPx(10)
            }
        }

        contentLayout.addView(headerLayout)
        contentLayout.addView(mediaView)
        contentLayout.addView(metaLayout)
        contentLayout.addView(callToActionView)

        val adChoicesView = AdChoicesView(context)
        val adChoicesParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.END or Gravity.TOP
        }

        nativeAdView.addView(contentLayout)
        nativeAdView.addView(adChoicesView, adChoicesParams)

        nativeAdView.mediaView = mediaView
        nativeAdView.headlineView = headlineView
        nativeAdView.bodyView = bodyView
        nativeAdView.callToActionView = callToActionView
        nativeAdView.iconView = iconView
        nativeAdView.advertiserView = advertiserView
        nativeAdView.starRatingView = starRatingView
        nativeAdView.priceView = priceView
        nativeAdView.storeView = storeView
        nativeAdView.adChoicesView = adChoicesView

        return NativeAdViewHolder(
            nativeAdView = nativeAdView,
            mediaView = mediaView,
            headlineView = headlineView,
            bodyView = bodyView,
            callToActionView = callToActionView,
            iconView = iconView,
            advertiserView = advertiserView,
            starRatingView = starRatingView,
            priceView = priceView,
            storeView = storeView,
            adChoicesView = adChoicesView,
        )
    }

    private fun bindNativeAd(holder: NativeAdViewHolder, nativeAd: NativeAd) {
        holder.headlineView.text = nativeAd.headline

        holder.bodyView.visibility = View.GONE

        val callToAction = nativeAd.callToAction
        if (callToAction.isNullOrBlank()) {
            holder.callToActionView.visibility = View.GONE
        } else {
            holder.callToActionView.text = callToAction
            holder.callToActionView.visibility = View.VISIBLE
        }

        val advertiser = nativeAd.advertiser
        if (advertiser.isNullOrBlank()) {
            holder.advertiserView.visibility = View.GONE
        } else {
            holder.advertiserView.text = advertiser
            holder.advertiserView.visibility = View.VISIBLE
        }

        val store = nativeAd.store
        if (store.isNullOrBlank()) {
            holder.storeView.visibility = View.GONE
        } else {
            holder.storeView.text = store
            holder.storeView.visibility = View.VISIBLE
        }

        val price = nativeAd.price
        if (price.isNullOrBlank()) {
            holder.priceView.visibility = View.GONE
        } else {
            holder.priceView.text = price
            holder.priceView.visibility = View.VISIBLE
        }

        val icon = nativeAd.icon
        if (icon?.drawable == null) {
            holder.iconView.visibility = View.GONE
        } else {
            holder.iconView.setImageDrawable(icon.drawable)
            holder.iconView.visibility = View.VISIBLE
        }

        val rating = nativeAd.starRating
        if (rating == null || rating <= 0.0) {
            holder.starRatingView.visibility = View.GONE
        } else {
            holder.starRatingView.rating = rating.toFloat()
            holder.starRatingView.visibility = View.VISIBLE
        }

        val mediaContent = nativeAd.mediaContent
        if (mediaContent != null) {
            holder.mediaView.mediaContent = mediaContent
            holder.mediaView.doOnLayout { view ->
                val minHeightPx = (120f * view.resources.displayMetrics.density).roundToInt()
                if (view.height > 0) {
                    return@doOnLayout
                }
                val aspectRatio = mediaContent.aspectRatio
                if (aspectRatio > 0f && view.width > 0) {
                    val targetHeight = (view.width / aspectRatio).roundToInt().coerceAtLeast(minHeightPx)
                    val params = view.layoutParams as? LinearLayout.LayoutParams ?: return@doOnLayout
                    params.height = targetHeight
                    params.weight = 0f
                    view.layoutParams = params
                }
            }
        }

        holder.nativeAdView.setNativeAd(nativeAd)
    }

    fun showReward(
        activity: Activity,
        onRewarded: (type: String, amount: Int) -> Unit = { _, _ -> },
        onAdShowFailed: (errorMessage: String) -> Unit = {},
        onAdClosed: () -> Unit = {},
    ): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "MobileAds not initialized; skipping rewarded.")
            onAdClosed()
            return false
        }
        val ad = rewardedAd
        if (refreshExpiredRewarded(activity)) {
            onAdClosed()
            return false
        }
        if (ad == null) {
            onAdClosed()
            return false
        }
        rewardedAd = null
        rewardedLoadedAtMs = 0L
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onAdClosed()
                requestRewardedLoad(activity, force = true)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                onAdShowFailed(adError.message)
                onAdClosed()
                FirebaseAnalyticsManager.logEvent(
                    "rewarded_ad_failed_to_show",
                    Bundle().apply {
                        putString("ad_unit_id", config.REWARDED_AD_UNIT_ID)
                        putString("error_message", adError.message)
                    },
                )
                requestRewardedLoad(activity, force = true)
            }
        }
        ad.show(
            activity,
            OnUserEarnedRewardListener { rewardItem: RewardItem ->
                onRewarded(rewardItem.type, rewardItem.amount)
            },
        )
        return true
    }

    fun showReward(
        activity: Activity,
        onRewarded: (type: String, amount: Int) -> Unit,
        onAdClosed: () -> Unit,
    ): Boolean {
        return showReward(
            activity = activity,
            onRewarded = onRewarded,
            onAdShowFailed = {},
            onAdClosed = onAdClosed,
        )
    }

    fun showAppOpenAd(activity: Activity, onAdClosed: () -> Unit = {}): Boolean {
        return showAppOpenAdWithFailure(
            activity = activity,
            onAdShowFailed = {},
            onAdClosed = onAdClosed,
        )
    }

    internal fun showAppOpenAdWithFailure(
        activity: Activity,
        onAdShowFailed: (errorMessage: String) -> Unit = {},
        onAdClosed: () -> Unit = {},
    ): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "MobileAds not initialized; skipping app open.")
            onAdClosed()
            return false
        }
        if (isAppOpenShowing || activity.isFinishing || activity.isDestroyed) {
            onAdClosed()
            return false
        }
        val ad = appOpenAd
        if (refreshExpiredAppOpenAd(activity)) {
            FirebaseAnalyticsManager.logEvent("app_open_not_ready")
            onAdClosed()
            return false
        }
        if (ad == null) {
            FirebaseAnalyticsManager.logEvent("app_open_not_ready")
            onAdClosed()
            return false
        }
        appOpenAd = null
        appOpenLoadedAtMs = 0L
        isAppOpenShowing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isAppOpenShowing = false
                onAdClosed()
                requestAppOpenAdLoad(activity, force = true)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isAppOpenShowing = false
                onAdShowFailed(adError.message)
                onAdClosed()
                FirebaseAnalyticsManager.logEvent(
                    "app_open_ad_failed_to_show",
                    Bundle().apply {
                        putString("ad_unit_id", config.APP_OPEN_AD_UNIT_ID)
                        putString("error_message", adError.message)
                    },
                )
                requestAppOpenAdLoad(activity, force = true)
            }
        }
        ad.show(activity)
        return true
    }

    private fun getAdMobAppId(context: Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            appInfo.metaData?.getString(ADMOB_APP_ID_KEY)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to read AdMob app ID from manifest", e)
            null
        }
    }

}

data class AdmobConfig(
    var INTERSTITIAL_AD_UNIT_ID: String = "",
    var BANNER_AD_UNIT_ID: String = "",
    var REWARDED_AD_UNIT_ID: String = "",
    var NATIVE_AD_UNIT_ID: String = "",
    var APP_OPEN_AD_UNIT_ID: String = "",
    var MREC_AD_UNIT_ID: String = "",
    var DEBUG: AdmobDebug = AdmobDebug(),
)

data class AdmobDebug(
    var useTestAds: Boolean = true,
    var useEmptyIds: Boolean = false,
)
