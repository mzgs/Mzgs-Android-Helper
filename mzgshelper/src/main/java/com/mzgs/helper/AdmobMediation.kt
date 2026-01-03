package com.mzgs.helper

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesView
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object AdmobMediation {

    const val TAG = "AdmobMediation"
    private const val ADMOB_APP_ID_KEY = "com.google.android.gms.ads.APPLICATION_ID"
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    private const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    private const val TEST_MREC_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    var config: AdmobConfig = AdmobConfig()

    @Volatile private var isAppOpenShowing = false
    private var appOpenObserverRegistered = false
    private var appWentToBackground = false

    fun initialize(activity: Activity, onInitComplete: () -> Unit = {}) {

        if (config.DEBUG.useTestAds && MzgsHelper.isDebug(activity)) {
            Log.d(TAG, "Using test AdMob ad unit IDs.")
            config.INTERSTITIAL_AD_UNIT_ID = TEST_INTERSTITIAL_AD_UNIT_ID
            config.BANNER_AD_UNIT_ID = TEST_BANNER_AD_UNIT_ID
            config.REWARDED_AD_UNIT_ID = TEST_REWARDED_AD_UNIT_ID
            config.NATIVE_AD_UNIT_ID = TEST_NATIVE_AD_UNIT_ID
            config.APP_OPEN_AD_UNIT_ID = TEST_APP_OPEN_AD_UNIT_ID
            config.MREC_AD_UNIT_ID = TEST_MREC_AD_UNIT_ID
        }

        if (config.DEBUG.useEmptyIds && MzgsHelper.isDebug(activity)) {
            Log.d(TAG, "Using empty AdMob ad unit IDs.")
            config.INTERSTITIAL_AD_UNIT_ID = ""
            config.BANNER_AD_UNIT_ID = ""
            config.REWARDED_AD_UNIT_ID = ""
            config.NATIVE_AD_UNIT_ID = ""
            config.APP_OPEN_AD_UNIT_ID = ""
            config.MREC_AD_UNIT_ID = ""
        }

        CoroutineScope(Dispatchers.IO).launch {
            val resolvedAppId = getAdMobAppId(activity)
            if (resolvedAppId.isNullOrBlank()) {
                Log.w(TAG, "AdMob app ID not found; set $ADMOB_APP_ID_KEY in the manifest or pass appId.")
                onInitComplete()
                return@launch
            }
            // Initialize the Google Mobile Ads SDK on a background thread.
            val initConfig = InitializationConfig.Builder(resolvedAppId).build()
            MobileAds.initialize(activity, initConfig) { initializationStatus ->
                for ((adapterClass, status) in initializationStatus.adapterStatusMap) {
                    Log.d(
                        TAG,
                        "Adapter: $adapterClass, Status: ${status.description}, Latency: ${status.latency}ms",
                    )
                }

                // autoload interstitial
                val adRequest = AdRequest.Builder(config.INTERSTITIAL_AD_UNIT_ID).build()
                val preloadConfig = PreloadConfiguration(adRequest)
                InterstitialAdPreloader.start(config.INTERSTITIAL_AD_UNIT_ID, preloadConfig)

                val rewardedRequest = AdRequest.Builder(config.REWARDED_AD_UNIT_ID).build()
                val rewardedPreloadConfig = PreloadConfiguration(rewardedRequest)
                RewardedAdPreloader.start(config.REWARDED_AD_UNIT_ID, rewardedPreloadConfig)

                val appOpenRequest = AdRequest.Builder(config.APP_OPEN_AD_UNIT_ID).build()
                val appOpenPreloadConfig = PreloadConfiguration(appOpenRequest)
                AppOpenAdPreloader.start(config.APP_OPEN_AD_UNIT_ID, appOpenPreloadConfig)

                onInitComplete()

            }
        }

    }

    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}) : Boolean {
        val ad = InterstitialAdPreloader.pollAd(config.INTERSTITIAL_AD_UNIT_ID)
        if (ad != null) {
            ad.adEventCallback = object : InterstitialAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    onAdClosed()
                    FirebaseAnalyticsManager.logEvent("interstitial_ad_failed_to_show",
                        Bundle().apply {
                            putString("ad_unit_id", config.INTERSTITIAL_AD_UNIT_ID)
                            putString("error_message", fullScreenContentError.message)
                        }
                    )
                }
            }
            ad.show(activity)
            return true
        }

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
        val isInitialized = remember { mutableStateOf(MobileAds.isInitialized) }
        val configuration = LocalConfiguration.current
        val resolvedAdSize = remember(adSize, configuration) {
            adSize
                ?: AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    configuration.screenWidthDp,
                )
        }
        val adViewState = remember { mutableStateOf<AdView?>(null) }
        val requestKey = remember(resolvedAdUnitId, resolvedAdSize) { "$resolvedAdUnitId:$resolvedAdSize" }
        val lastRequestKey = remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            while (!MobileAds.isInitialized) {
                delay(1000)
            }
            isInitialized.value = true
        }

        if (isInitialized.value) {
            AndroidView(
                modifier = modifier,
                factory = { viewContext ->
                    AdView(viewContext, null, 0, 0).also { adView ->
                        adViewState.value = adView
                        adView.resize(resolvedAdSize)
                        if (lastRequestKey.value != requestKey) {
                            val request = BannerAdRequest.Builder(resolvedAdUnitId, resolvedAdSize).build()
                            adView.loadAd(
                                request,
                                object : AdLoadCallback<com.google.android.libraries.ads.mobile.sdk.banner.BannerAd> {
                                    override fun onAdFailedToLoad(adError: LoadAdError) {
                                        onAdFailedToLoad?.invoke(adError.message)
                                    }
                                },
                            )
                            lastRequestKey.value = requestKey
                        }
                    }
                },
                update = { adView ->
                    adView.resize(resolvedAdSize)
                    if (lastRequestKey.value != requestKey) {
                        val request = BannerAdRequest.Builder(resolvedAdUnitId, resolvedAdSize).build()
                        adView.loadAd(request, object : AdLoadCallback<com.google.android.libraries.ads.mobile.sdk.banner.BannerAd> {
                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                onAdFailedToLoad?.invoke(adError.message)
                            }
                        })
                        lastRequestKey.value = requestKey
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
        val isInitialized = remember { mutableStateOf(MobileAds.isInitialized) }
        val nativeAdState = remember(resolvedAdUnitId) { mutableStateOf<NativeAd?>(null) }
        val nativeAdViewState = remember { mutableStateOf<NativeAdView?>(null) }
        val isLoading = remember(resolvedAdUnitId) { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (!MobileAds.isInitialized) {
                delay(1000)
            }
            isInitialized.value = true
        }

        LaunchedEffect(resolvedAdUnitId, isInitialized.value) {
            if (!isInitialized.value || isLoading.value || nativeAdState.value != null) {
                return@LaunchedEffect
            }
            isLoading.value = true
            val request = NativeAdRequest.Builder(
                resolvedAdUnitId,
                listOf(NativeAd.NativeAdType.NATIVE),
            ).build()
            NativeAdLoader.load(
                request,
                object : NativeAdLoaderCallback {
                    override fun onNativeAdLoaded(ad: NativeAd) {
                        nativeAdState.value?.destroy()
                        nativeAdState.value = ad
                        isLoading.value = false
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        isLoading.value = false
                        onAdFailedToLoad?.invoke(adError.message)
                        FirebaseAnalyticsManager.logEvent(
                            "native_ad_failed_to_load",
                            Bundle().apply {
                                putString("ad_unit_id", resolvedAdUnitId)
                                putString("error_message", adError.message)
                            },
                        )
                    }

                    override fun onAdLoadingCompleted() {
                        isLoading.value = false
                    }
                },
            )
        }

        if (isInitialized.value) {
            AndroidView(
                modifier = modifier,
                factory = { viewContext ->
                    val holder = createNativeAdViewHolder(viewContext)
                    val adView = holder.nativeAdView
                    adView.tag = holder
                    adView.visibility = View.GONE
                    nativeAdViewState.value = adView
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
        }

        DisposableEffect(nativeAdState.value) {
            val activeAd = nativeAdState.value
            onDispose {
                activeAd?.destroy()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                nativeAdViewState.value?.destroy()
                nativeAdViewState.value = null
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
            imageScaleType = ImageView.ScaleType.CENTER_CROP
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
        contentLayout.addView(bodyView)
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

        holder.nativeAdView.registerNativeAd(nativeAd, holder.mediaView)
    }

    fun showReward(
        activity: Activity,
        onRewarded: (type: String, amount: Int) -> Unit = { _, _ -> },
        onAdClosed: () -> Unit = {},
    ): Boolean {
        val ad = RewardedAdPreloader.pollAd(config.REWARDED_AD_UNIT_ID)
        if (ad != null) {
            ad.adEventCallback = object : RewardedAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    onAdClosed()
                    FirebaseAnalyticsManager.logEvent(
                        "rewarded_ad_failed_to_show",
                        Bundle().apply {
                            putString("ad_unit_id", config.REWARDED_AD_UNIT_ID)
                            putString("error_message", fullScreenContentError.message)
                        },
                    )
                }
            }
            ad.show(
                activity,
                OnUserEarnedRewardListener { rewardItem ->
                    onRewarded(rewardItem.type, rewardItem.amount)
                },
            )
            return true
        }

        onAdClosed()
        return false
    }

    fun showAppOpenAd(activity: Activity, onAdClosed: () -> Unit = {}): Boolean {
        return showAppOpenAdInternal(activity, onAdClosed, invokeOnAdClosedWhenNotShown = true)
    }

    fun enableAppOpen(
        activity: Activity,
        showOnColdStart: Boolean = false,
        onAdClosed: () -> Unit = {},
    ) {
        val owner = activity as? LifecycleOwner
        if (owner == null) {
            Log.w(TAG, "Activity must implement LifecycleOwner to enable app-open ads.")
            return
        }
        if (appOpenObserverRegistered) {
            return
        }
        appOpenObserverRegistered = true
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (showOnColdStart || appWentToBackground) {
                    appWentToBackground = false
                    showAppOpenAdInternal(activity, onAdClosed, invokeOnAdClosedWhenNotShown = false)
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                if (!activity.isChangingConfigurations) {
                    appWentToBackground = true
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                owner.lifecycle.removeObserver(this)
                appOpenObserverRegistered = false
            }
        }
        owner.lifecycle.addObserver(observer)
    }

    private fun showAppOpenAdInternal(
        activity: Activity,
        onAdClosed: () -> Unit,
        invokeOnAdClosedWhenNotShown: Boolean,
    ): Boolean {
        if (isAppOpenShowing || activity.isFinishing || activity.isDestroyed) {
            if (invokeOnAdClosedWhenNotShown) {
                onAdClosed()
            }
            return false
        }
        val ad = AppOpenAdPreloader.pollAd(config.APP_OPEN_AD_UNIT_ID)
        if (ad != null) {
            isAppOpenShowing = true
            ad.adEventCallback = object : AppOpenAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    isAppOpenShowing = false
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    isAppOpenShowing = false
                    onAdClosed()
                    FirebaseAnalyticsManager.logEvent(
                        "app_open_ad_failed_to_show",
                        Bundle().apply {
                            putString("ad_unit_id", config.APP_OPEN_AD_UNIT_ID)
                            putString("error_message", fullScreenContentError.message)
                        },
                    )
                }
            }
            ad.show(activity)
            return true
        }

        if (invokeOnAdClosedWhenNotShown) {
            onAdClosed()
        }
        return false
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
    var useEmptyIds : Boolean = false,
)
