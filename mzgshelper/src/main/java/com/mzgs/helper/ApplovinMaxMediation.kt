package com.mzgs.helper

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
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

    var config: ApplovinMaxConfig = ApplovinMaxConfig()

    @Volatile private var isAppOpenShowing = false
    @Volatile var isFullscreenAdShowing = false

    private var isInitialized = false
    private var isInitializing = false
    private val pendingInitCallbacks = mutableListOf<() -> Unit>()

    private var interstitialAd: MaxInterstitialAd? = null
    private var rewardedAd: MaxRewardedAd? = null
    private var appOpenAd: MaxAppOpenAd? = null

    private var interstitialOnAdClosed: () -> Unit = {}
    private var rewardedOnAdClosed: () -> Unit = {}
    private var rewardedOnUserRewarded: (String, Int) -> Unit = { _, _ -> }
    private var appOpenOnAdClosedInternal: () -> Unit = {}

    fun initialize(context: Context, onInitComplete: () -> Unit = {}) {
        val appContext = context.applicationContext
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
            Log.w(TAG, "AppLovin SDK key not found; set $APPLOVIN_SDK_KEY_META in the manifest or pass SDK_KEY.")
            isInitializing = false
            isInitialized = false
            val callbacks = pendingInitCallbacks.toList()
            pendingInitCallbacks.clear()
            callbacks.forEach { it() }
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
            isInitialized = true
            isInitializing = false
            if (config.INTERSTITIAL_AD_UNIT_ID.isNotBlank()) {
                createInterstitialAd(appContext)
            }
            if (config.REWARDED_AD_UNIT_ID.isNotBlank()) {
                createRewardedAd()
            }
            if (config.APP_OPEN_AD_UNIT_ID.isNotBlank()) {
                createAppOpenAd()
            }
            val callbacks = pendingInitCallbacks.toList()
            pendingInitCallbacks.clear()
            callbacks.forEach { it() }
        }
    }

    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}): Boolean {
        if (!AppLovinSdk.getInstance(activity).isInitialized) {
            onAdClosed()
            return false
        }
        val ad = interstitialAd
        if (ad != null && ad.isReady) {
            isFullscreenAdShowing = true
            interstitialOnAdClosed = onAdClosed
            ad.showAd(activity)
            return true
        }
        interstitialAd?.loadAd()
        onAdClosed()
        return false
    }

    @Composable
    fun showBanner(
        modifier: Modifier = Modifier,
        adUnitId: String? = null,
        adSize: AdSize? = null,
        onAdFailedToLoad: ((String) -> Unit)? = null,
    ) {
        val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.BANNER_AD_UNIT_ID
        if (resolvedAdUnitId.isBlank()) {
            return
        }
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val isInitialized = remember { mutableStateOf(AppLovinSdk.getInstance(context).isInitialized) }
        val isMrec = remember(adSize) { adSize == AdSize.MEDIUM_RECTANGLE }
        val adViewState = remember { mutableStateOf<MaxAdView?>(null) }
        val adContainerState = remember { mutableStateOf<FrameLayout?>(null) }
        val requestKey = remember(resolvedAdUnitId, isMrec) { "$resolvedAdUnitId:$isMrec" }
        val heightPx = remember(configuration, isMrec) {
            val heightDp = if (isMrec) 250 else if (configuration.smallestScreenWidthDp >= 600) 90 else 50
            dpToPx(context, heightDp)
        }
        val widthPx = remember(configuration, isMrec) {
            if (isMrec) dpToPx(context, 300) else ViewGroup.LayoutParams.MATCH_PARENT
        }

        LaunchedEffect(Unit) {
            while (!AppLovinSdk.getInstance(context).isInitialized) {
                delay(1000)
            }
            isInitialized.value = true
        }

        LaunchedEffect(requestKey, isInitialized.value, adContainerState.value, heightPx, widthPx) {
            val container = adContainerState.value ?: return@LaunchedEffect
            if (!isInitialized.value) {
                return@LaunchedEffect
            }

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
                            FirebaseAnalyticsManager.logAdLoad(
                                adType = if (isMrec) "mrec" else "banner",
                                adUnitId = resolvedAdUnitId,
                                adNetwork = "applovin",
                                success = true,
                            )
                        }

                        override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                            onAdFailedToLoad?.invoke(error.message)
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
                    adView.loadAd()
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

        if (isInitialized.value) {
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
        val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.NATIVE_AD_UNIT_ID
        if (resolvedAdUnitId.isBlank()) {
            return
        }
        val context = LocalContext.current
        val isInitialized = remember { mutableStateOf(AppLovinSdk.getInstance(context).isInitialized) }
        val nativeAdLoader = remember(resolvedAdUnitId) { MaxNativeAdLoader(resolvedAdUnitId) }
        val nativeAdContainerState = remember { mutableStateOf<FrameLayout?>(null) }
        val nativeAdViewState = remember(resolvedAdUnitId) { mutableStateOf<MaxNativeAdView?>(null) }
        val loadedNativeAdState = remember(resolvedAdUnitId) { mutableStateOf<MaxAd?>(null) }
        val isLoading = remember(resolvedAdUnitId) { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (!AppLovinSdk.getInstance(context).isInitialized) {
                delay(1000)
            }
            isInitialized.value = true
        }

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
                }

                override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                    isLoading.value = false
                    onAdFailedToLoad?.invoke(error.message)
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
            isInitialized.value,
            nativeAdContainerState.value,
            nativeAdViewState.value,
        ) {
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
            if (!isInitialized.value || isLoading.value || loadedNativeAdState.value != null) {
                return@LaunchedEffect
            }
            isLoading.value = true
            nativeAdLoader.loadAd(createNativeAdView(context))
        }

        if (isInitialized.value) {
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
        if (ad != null && ad.isReady) {
            isFullscreenAdShowing = true
            rewardedOnAdClosed = onAdClosed
            rewardedOnUserRewarded = onRewarded
            ad.showAd(activity)
            return true
        }
        rewardedAd?.loadAd()
        onAdClosed()
        return false
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
        if (ad != null && ad.isReady) {
            isAppOpenShowing = true
            isFullscreenAdShowing = true
            appOpenOnAdClosedInternal = onAdClosed
            ad.showAd()
            return true
        }
        appOpenAd?.loadAd()
        onAdClosed()
        return false
    }

    private fun createInterstitialAd(context: Context) {
        if (config.INTERSTITIAL_AD_UNIT_ID.isBlank()) {
            return
        }
        interstitialAd = MaxInterstitialAd(config.INTERSTITIAL_AD_UNIT_ID).also { interstitial ->
            interstitial.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "interstitial",
                        adUnitId = config.INTERSTITIAL_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
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
                    interstitialOnAdClosed()
                    interstitialOnAdClosed = {}
                    interstitial.loadAd()
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "interstitial",
                        adNetwork = "applovin",
                        success = false,
                        errorMessage = error.message,
                    )
                }

                override fun onAdHidden(ad: MaxAd) {
                    isFullscreenAdShowing = false
                    interstitialOnAdClosed()
                    interstitialOnAdClosed = {}
                    interstitial.loadAd()
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
            interstitial.loadAd()
        }
    }

    private fun createRewardedAd() {
        if (config.REWARDED_AD_UNIT_ID.isBlank()) {
            return
        }
        rewardedAd = MaxRewardedAd.getInstance(config.REWARDED_AD_UNIT_ID).also { rewarded ->
            rewarded.setListener(object : MaxRewardedAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "rewarded",
                        adUnitId = config.REWARDED_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
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
                    rewardedOnAdClosed()
                    rewardedOnAdClosed = {}
                    rewarded.loadAd()
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "rewarded",
                        adNetwork = "applovin",
                        success = false,
                        errorMessage = error.message,
                    )
                }

                override fun onAdHidden(ad: MaxAd) {
                    isFullscreenAdShowing = false
                    rewardedOnAdClosed()
                    rewardedOnAdClosed = {}
                    rewarded.loadAd()
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
            rewarded.loadAd()
        }
    }

    private fun createAppOpenAd() {
        if (config.APP_OPEN_AD_UNIT_ID.isBlank()) {
            return
        }
        appOpenAd = MaxAppOpenAd(config.APP_OPEN_AD_UNIT_ID).also { appOpen ->
            appOpen.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "app_open",
                        adUnitId = config.APP_OPEN_AD_UNIT_ID,
                        adNetwork = "applovin",
                        success = true,
                    )
                }

                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
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
                    appOpenOnAdClosedInternal()
                    appOpenOnAdClosedInternal = {}
                    appOpen.loadAd()
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
                    appOpenOnAdClosedInternal()
                    appOpenOnAdClosedInternal = {}
                    appOpen.loadAd()
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
            appOpen.loadAd()
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
