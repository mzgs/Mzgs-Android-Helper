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
    private val pendingInitCallbacks = mutableListOf<() -> Unit>()

    @Volatile private var interstitialAd: InterstitialAd? = null
    @Volatile private var rewardedAd: RewardedAd? = null
    @Volatile private var appOpenAd: AppOpenAd? = null
    @Volatile private var isInterstitialLoading = false
    @Volatile private var isRewardedLoading = false
    @Volatile private var isAppOpenLoading = false

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

        if (isInitialized) {
            onInitComplete()
            return
        }
        pendingInitCallbacks.add(onInitComplete)
        if (isInitializing) {
            return
        }
        isInitializing = true

        val resolvedAppId = getAdMobAppId(activity)
        if (resolvedAppId.isNullOrBlank()) {
            Log.w(TAG, "AdMob app ID not found; set $ADMOB_APP_ID_KEY in the manifest or pass appId.")
            isInitializing = false
            isInitialized = false
            drainInitCallbacks()
            return
        }

        MobileAds.initialize(activity) { initializationStatus ->
            for ((adapterClass, status) in initializationStatus.adapterStatusMap) {
                Log.d(
                    TAG,
                    "Adapter: $adapterClass, Status: ${status.description}, Latency: ${status.latency}ms",
                )
            }
            isInitialized = true
            isInitializing = false
            loadInterstitial(activity)
            loadRewarded(activity)
            loadAppOpenAd(activity)
            drainInitCallbacks()
        }
    }

    private fun drainInitCallbacks() {
        val callbacks = pendingInitCallbacks.toList()
        pendingInitCallbacks.clear()
        callbacks.forEach { it() }
    }

    private fun loadInterstitial(context: Context) {
        if (!isInitialized || config.INTERSTITIAL_AD_UNIT_ID.isBlank()) {
            return
        }
        if (isInterstitialLoading || interstitialAd != null) {
            return
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
                    isInterstitialLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isInterstitialLoading = false
                    Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
                }
            },
        )
    }

    private fun loadRewarded(context: Context) {
        if (!isInitialized || config.REWARDED_AD_UNIT_ID.isBlank()) {
            return
        }
        if (isRewardedLoading || rewardedAd != null) {
            return
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
                    isRewardedLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isRewardedLoading = false
                    Log.w(TAG, "Rewarded failed to load: ${loadAdError.message}")
                }
            },
        )
    }

    private fun loadAppOpenAd(context: Context) {
        if (!isInitialized || config.APP_OPEN_AD_UNIT_ID.isBlank()) {
            return
        }
        if (isAppOpenLoading || appOpenAd != null) {
            return
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
                    isAppOpenLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    appOpenAd = null
                    isAppOpenLoading = false
                    Log.w(TAG, "App open failed to load: ${loadAdError.message}")
                }
            },
        )
    }

    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "MobileAds not initialized; skipping interstitial.")
            onAdClosed()
            return false
        }
        val ad = interstitialAd
        if (ad == null) {
            loadInterstitial(activity)
            onAdClosed()
            return false
        }
        interstitialAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onAdClosed()
                loadInterstitial(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                onAdClosed()
                FirebaseAnalyticsManager.logEvent(
                    "interstitial_ad_failed_to_show",
                    Bundle().apply {
                        putString("ad_unit_id", config.INTERSTITIAL_AD_UNIT_ID)
                        putString("error_message", adError.message)
                    },
                )
                loadInterstitial(activity)
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
        val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.BANNER_AD_UNIT_ID
        if (resolvedAdUnitId.isBlank()) {
            return
        }
        val context = LocalContext.current
        val isInitializedState = remember { mutableStateOf(isInitialized) }
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
        val hasLoaded = remember(requestKey) { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (!isInitialized) {
                delay(1000)
            }
            isInitializedState.value = true
        }

        if (isInitializedState.value) {
            key(requestKey) {
                AndroidView(
                    modifier = modifier,
                    factory = { viewContext ->
                        AdView(viewContext).also { adView ->
                            adViewState.value = adView
                            adView.adUnitId = resolvedAdUnitId
                            adView.setAdSize(resolvedAdSize)
                            adView.adListener = object : AdListener() {
                                override fun onAdFailedToLoad(adError: LoadAdError) {
                                    onAdFailedToLoad?.invoke(adError.message)
                                }
                            }
                            if (!hasLoaded.value) {
                                adView.loadAd(AdRequest.Builder().build())
                                hasLoaded.value = true
                            }
                        }
                    },
                    update = { adView ->
                        if (!hasLoaded.value) {
                            adView.loadAd(AdRequest.Builder().build())
                            hasLoaded.value = true
                        }
                    },
                )
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
        val resolvedAdUnitId = adUnitId?.takeIf { it.isNotBlank() } ?: config.NATIVE_AD_UNIT_ID
        if (resolvedAdUnitId.isBlank()) {
            return
        }
        val context = LocalContext.current
        val isInitializedState = remember { mutableStateOf(isInitialized) }
        val nativeAdState = remember(resolvedAdUnitId) { mutableStateOf<NativeAd?>(null) }
        val isLoading = remember(resolvedAdUnitId) { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (!isInitialized) {
                delay(1000)
            }
            isInitializedState.value = true
        }

        LaunchedEffect(resolvedAdUnitId, isInitializedState.value) {
            if (!isInitializedState.value || isLoading.value || nativeAdState.value != null) {
                return@LaunchedEffect
            }
            isLoading.value = true
            val adLoader = AdLoader.Builder(context, resolvedAdUnitId)
                .forNativeAd { nativeAd ->
                    nativeAdState.value?.destroy()
                    nativeAdState.value = nativeAd
                    isLoading.value = false
                }
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .withAdListener(object : AdListener() {
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
                })
                .build()
            adLoader.loadAd(AdRequest.Builder().build())
        }

        if (isInitializedState.value) {
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
        }

        DisposableEffect(nativeAdState.value) {
            val activeAd = nativeAdState.value
            onDispose {
                activeAd?.destroy()
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
        onAdClosed: () -> Unit = {},
    ): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "MobileAds not initialized; skipping rewarded.")
            onAdClosed()
            return false
        }
        val ad = rewardedAd
        if (ad == null) {
            loadRewarded(activity)
            onAdClosed()
            return false
        }
        rewardedAd = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onAdClosed()
                loadRewarded(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                onAdClosed()
                FirebaseAnalyticsManager.logEvent(
                    "rewarded_ad_failed_to_show",
                    Bundle().apply {
                        putString("ad_unit_id", config.REWARDED_AD_UNIT_ID)
                        putString("error_message", adError.message)
                    },
                )
                loadRewarded(activity)
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

    fun showAppOpenAd(activity: Activity, onAdClosed: () -> Unit = {}): Boolean {
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
        if (ad == null) {
            loadAppOpenAd(activity)
            FirebaseAnalyticsManager.logEvent("app_open_not_ready")
            onAdClosed()
            return false
        }
        appOpenAd = null
        isAppOpenShowing = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                isAppOpenShowing = false
                onAdClosed()
                loadAppOpenAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isAppOpenShowing = false
                onAdClosed()
                FirebaseAnalyticsManager.logEvent(
                    "app_open_ad_failed_to_show",
                    Bundle().apply {
                        putString("ad_unit_id", config.APP_OPEN_AD_UNIT_ID)
                        putString("error_message", adError.message)
                    },
                )
                loadAppOpenAd(activity)
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
