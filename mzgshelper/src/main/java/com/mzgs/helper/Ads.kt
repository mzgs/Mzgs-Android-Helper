package com.mzgs.helper

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.mzgs.helper.admob.*
import com.mzgs.helper.applovin.*
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import java.lang.ref.WeakReference
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

data class AdShowResult(
    val success: Boolean,
    val network: String? = null
)

object Ads : DefaultLifecycleObserver {
    private const val TAG = "Ads"
    private const val ADMOB = "admob"
    private const val APPLOVIN_MAX = "applovin_max"
    
    private var applicationContextRef: WeakReference<Context>? = null
    private var isFirstLaunch = true
    private var appOpenAdEnabled = false

    private fun ensureFrameLayout(container: ViewGroup): FrameLayout {
        return if (container is FrameLayout) {
            container
        } else {
            val wrapper = FrameLayout(container.context)
            container.removeAllViews()
            container.addView(
                wrapper,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            wrapper
        }
    }
    
    private fun getAppContext(): Context? {
        return applicationContextRef?.get() ?: MzgsHelper.getContext()
    }

    private fun setAppContext(context: Context) {
        applicationContextRef = WeakReference(context.applicationContext)
    }

    fun init() {
        val baseContext = MzgsHelper.getContext()
        if (baseContext == null) {
            Log.e(TAG, "MzgsHelper.init(context, activity, ...) must be called before Ads.init()")
            return
        }

        if (!MzgsHelper.isInitialized()) {
            Log.w(TAG, "MzgsHelper.init(...) should be called before Ads.init() to provide shared references")
        }

        setAppContext(baseContext)

        // Register for process lifecycle to detect app foreground/background
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Get and log the advertising ID (skip if ads are disabled)
        val advertisingContext = getAppContext()
        if (!MzgsHelper.debugNoAds && advertisingContext != null) {
            getAdvertisingId(advertisingContext)
        }
    }
    
    private fun getAdvertisingId(context: Context) {
        // Skip if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            return
        }
        
        try {
            // Get advertising ID in background thread
            Thread {
                try {
                    val adInfo = com.google.android.gms.ads.identifier.AdvertisingIdClient.getAdvertisingIdInfo(context)
                    val advertisingId = adInfo.id
                    val isLimitAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled
                    
                    // Log on main thread
                    Handler(Looper.getMainLooper()).post {
                        Log.d(TAG, "")
                        Log.d(TAG, "|||============================================================|||")
                        Log.d(TAG, "|||                  YOUR DEVICE ADVERTISING ID                |||")
                        Log.d(TAG, "|||============================================================|||")
                        Log.d(TAG, "|||                                                            |||")
                        if (advertisingId != null && advertisingId != "00000000-0000-0000-0000-000000000000") {
                            Log.d(TAG, "|||  GAID: $advertisingId  |||")
                            Log.d(TAG, "|||                                                            |||")
                            Log.d(TAG, "|||  Copy this ID and add it to:                              |||")
                            Log.d(TAG, "|||  - testDeviceAdvertisingIds in AppLovinConfig             |||")
                            Log.d(TAG, "|||  - testDeviceIds in AdMobConfig (hashed version)          |||")
                        } else {
                            Log.d(TAG, "|||  GAID: Not available or advertising tracking disabled     |||")
                            Log.d(TAG, "|||                                                            |||")
                            Log.d(TAG, "|||  To enable:                                                |||")
                            Log.d(TAG, "|||  1. Go to Settings → Google → Ads                          |||")
                            Log.d(TAG, "|||  2. Enable 'Opt out of Ads Personalization' OFF           |||")
                        }
                        Log.d(TAG, "|||                                                            |||")
                        Log.d(TAG, "|||  Limit Ad Tracking: ${if (isLimitAdTrackingEnabled) "ENABLED" else "DISABLED"}                              |||")
                        Log.d(TAG, "|||                                                            |||")
                        Log.d(TAG, "|||============================================================|||")
                        Log.d(TAG, "")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get advertising ID: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advertising ID thread: ${e.message}")
        }
    }
    
    @JvmStatic
    fun getCurrentActivity(): Activity? {
        return runCatching { MzgsHelper.getActivity() }.getOrNull()
    }
    
    // Called when app comes to foreground
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "App came to foreground, isFirstLaunch: $isFirstLaunch")
        
        // Don't show on first launch, only when returning from background
        if (!isFirstLaunch && appOpenAdEnabled) {
            // Use a small delay to ensure activity is ready
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d(TAG, "Attempting to show app open ad after returning from background")
                val shown = showAppOpenAd()
                if (!shown) {
                    FirebaseAnalyticsManager.logAdShown(
                        adType = "app_open",
                        adNetwork = APPLOVIN_MAX,
                        success = false,
                        errorMessage = "App open ad not shown after resuming from background"
                    )
                }
            }, 100)
        } else if (isFirstLaunch) {
            isFirstLaunch = false
            Log.d(TAG, "First launch detected, not showing app open ad")
        }
    }
    
    // Called when app goes to background
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "App going to background - preloading app open ads for next time")
        
        // Preload both AdMob and AppLovin app open ads for when app returns
        Handler(Looper.getMainLooper()).postDelayed({
            // Load AdMob app open ad if available
            val admobAppOpenManager = AppOpenAdManager.getInstance()
            if (admobAppOpenManager != null) {
                val context = getAppContext()
                if (context != null) {
                    Log.d(TAG, "Fetching AdMob app open ad for next app resume")
                    admobAppOpenManager.fetchAd(context)
                }
            }
            
            // Load AppLovin app open ad if available
            val applovinAppOpenManager = AppLovinAppOpenAdManager.getInstance()
            if (applovinAppOpenManager != null) {
                Log.d(TAG, "Loading AppLovin app open ad for next app resume")
                applovinAppOpenManager.loadAd()
            }
        }, 500) // Small delay to ensure smooth background transition
    }
    
    @JvmStatic
    fun enableAppOpenAds(enabled: Boolean) {
        appOpenAdEnabled = enabled
        Log.d(TAG, "App open ads ${if (enabled) "enabled" else "disabled"}")
    }
    
    @JvmStatic
    fun initAdMob(
        config: AdMobConfig,
        onInitComplete: () -> Unit = {}
    ) {
        val context = getAppContext()
        if (context == null) {
            Log.e(TAG, "Ads.init() must be called before initAdMob()")
            return
        }
        Log.d(TAG, "Initializing AdMob through Ads helper")
        AdMobManager.init(context, config, onInitComplete)
        
        // Enable app open ads if configured
        if (config.enableAppOpenAd) {
            enableAppOpenAds(true)
        }
    }
    
    @JvmStatic
    fun initAppLovinMax(
        config: AppLovinConfig,
        onInitComplete: () -> Unit = {}
    ) {
        val context = getAppContext()
        if (context == null) {
            Log.e(TAG, "Ads.init() must be called before initAppLovinMax()")
            return
        }
        Log.d(TAG, "Initializing AppLovin MAX through Ads helper")
        AppLovinMediationManager.init(context, config, onInitComplete)
        
        // Enable app open ads if configured
        if (config.enableAppOpenAd) {
            enableAppOpenAds(true)
        }
    }
    
    @JvmStatic
    fun initBothNetworks(
        adMobConfig: AdMobConfig,
        appLovinConfig: AppLovinConfig,
        onBothInitComplete: () -> Unit = {}
    ) {
        val context = getAppContext()
        if (context == null) {
            Log.e(TAG, "Ads.init() must be called before initBothNetworks()")
            return
        }
        
        var adMobInitialized = false
        var appLovinInitialized = false
        
        fun checkBothInitialized() {
            if (adMobInitialized && appLovinInitialized) {
                Log.d(TAG, "Both ad networks initialized successfully")
                onBothInitComplete()
            }
        }
        
        initAdMob(adMobConfig) {
            adMobInitialized = true
            checkBothInitialized()
        }
        
        initAppLovinMax(appLovinConfig) {
            appLovinInitialized = true
            checkBothInitialized()
        }
    }
    
    @JvmStatic
    fun getAdMobManager(): AdMobManager? {
        val context = getAppContext()
        if (context == null) {
            Log.e(TAG, "Ads.init() must be called before getAdMobManager()")
            return null
        }
        return AdMobManager.getInstance(context)
    }
    
    @JvmStatic
    fun getAppLovinManager(): AppLovinMediationManager? {
        val context = getAppContext()
        if (context == null) {
            Log.e(TAG, "Ads.init() must be called before getAppLovinManager()")
            return null
        }
        return AppLovinMediationManager.getInstance(context)
    }
    
    
    @JvmStatic
    fun showInterstitial(onAdClosed: (() -> Unit)? = null): Boolean {
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "Interstitial ads skipped (debugNoAds mode)")
            onAdClosed?.invoke()
            return false
        }

        Log.d(TAG, "Attempting to show interstitial - checking AppLovin MAX first")
        
        // First try AppLovin MAX
        if (AppLovinMediationManager.isInterstitialReady()) {
            Log.d(TAG, "Showing AppLovin MAX interstitial")
            val shown = AppLovinMediationManager.showInterstitialAd(onAdClosed)
            if (shown) {
                FirebaseAnalyticsManager.logAdShown("interstitial", APPLOVIN_MAX, true)
            }
            return shown
        }
        Log.d(TAG, "AppLovin MAX interstitial not ready, trying AdMob")
        
        // Fallback to AdMob
        if (AdMobManager.isInterstitialReady()) {
            Log.d(TAG, "Showing AdMob interstitial")
            val shown = AdMobManager.showInterstitialAd(onAdClosed)
            if (shown) {
                FirebaseAnalyticsManager.logAdShown("interstitial", ADMOB, true)
            }
            return shown
        }
        Log.d(TAG, "AdMob interstitial not ready")
        
        Log.d(TAG, "No interstitial ads ready from any network")
        FirebaseAnalyticsManager.logAdShown(
            adType = "interstitial",
            adNetwork = ADMOB,
            success = false,
            errorMessage = "Interstitial not ready on AppLovin MAX or AdMob"
        )
        onAdClosed?.invoke()  // Call the callback if no ads were shown
        return false
    }
    
    @JvmStatic
    fun showInterstitialWithResult(onAdClosed: (() -> Unit)? = null): AdShowResult {
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "Interstitial ads skipped (debugNoAds mode)")
            onAdClosed?.invoke()
            return AdShowResult(false, null)
        }

        Log.d(TAG, "Attempting to show interstitial with result - checking AppLovin MAX first")
        
        // First try AppLovin MAX
        if (AppLovinMediationManager.isInterstitialReady()) {
            Log.d(TAG, "Showing AppLovin MAX interstitial")
            val shown = AppLovinMediationManager.showInterstitialAd(onAdClosed)
            if (shown) {
                FirebaseAnalyticsManager.logAdShown("interstitial", APPLOVIN_MAX, true)
                return AdShowResult(true, APPLOVIN_MAX)
            }
        }
        Log.d(TAG, "AppLovin MAX interstitial not ready, trying AdMob")
        
        // Fallback to AdMob
        if (AdMobManager.isInterstitialReady()) {
            Log.d(TAG, "Showing AdMob interstitial")
            val shown = AdMobManager.showInterstitialAd(onAdClosed)
            if (shown) {
                FirebaseAnalyticsManager.logAdShown("interstitial", ADMOB, true)
                return AdShowResult(true, ADMOB)
            }
        }
        Log.d(TAG, "AdMob interstitial not ready")
        
        Log.d(TAG, "No interstitial ads ready from any network")
        FirebaseAnalyticsManager.logAdShown(
            adType = "interstitial",
            adNetwork = ADMOB,
            success = false,
            errorMessage = "Interstitial not ready on AppLovin MAX or AdMob"
        )
        onAdClosed?.invoke()  // Call the callback if no ads were shown
        return AdShowResult(false, null)
    }
    
    @JvmStatic
    fun showInterstitialWithCycle(name: String, defaultValue: Int = 3, onAdClosed: (() -> Unit)? = null) {
        // Get the cycle value from remote config
        val cycleValue = Remote.getInt(name, defaultValue)
        
        // Increment counter using ActionCounter and get the new value
        val currentCounter = ActionCounter.increaseGet(name)
        
        Log.d(TAG, "Interstitial cycle for '$name': counter=$currentCounter, cycle=$cycleValue")
        
        // Check if we should show the ad using modulo
        if (currentCounter % cycleValue == 0) {
            // Show interstitial ad
            val shown = showInterstitial(onAdClosed)
            if (shown) {
                Log.d(TAG, "Showing interstitial ad for cycle '$name' at counter $currentCounter (every $cycleValue calls)")
            } else {
                Log.d(TAG, "Failed to show interstitial ad for cycle '$name' at counter $currentCounter")
            }
        } else {
            Log.d(TAG, "Not showing interstitial for '$name', counter=$currentCounter (shows every $cycleValue calls)")
            onAdClosed?.invoke()  // Call the callback even if ad not shown due to cycle
        }
    }
    
    @JvmStatic
    fun showBanner(
        container: ViewGroup,
        adSize: BannerSize = BannerSize.BANNER
    ): Boolean {
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "Banner ads skipped (debugNoAds mode)")
            return false
        }
        
        val context = getAppContext()
        if (context == null) {
            Log.e(TAG, "No application context available for showing banner")
            return false
        }
        
        val frameContainer = ensureFrameLayout(container)

        Log.d(TAG, "Attempting to show banner - checking AppLovin MAX first")
        
        // Helper function to try AdMob as fallback
        fun tryAdMobFallback() {
            try {
                Log.d(TAG, "Attempting to show AdMob banner as fallback")
                val bannerHelper = BannerAdHelper(context)
                val bannerType = when (adSize) {
                    BannerSize.ADAPTIVE -> BannerAdHelper.BannerType.ADAPTIVE_BANNER
                    BannerSize.BANNER -> BannerAdHelper.BannerType.BANNER
                    BannerSize.LARGE_BANNER -> BannerAdHelper.BannerType.LARGE_BANNER
                    BannerSize.MEDIUM_RECTANGLE -> BannerAdHelper.BannerType.MEDIUM_RECTANGLE
                    BannerSize.FULL_BANNER -> BannerAdHelper.BannerType.FULL_BANNER
                    BannerSize.LEADERBOARD -> BannerAdHelper.BannerType.LEADERBOARD
                }
                bannerHelper.createBannerView(
                    adUnitId = AdMobManager.getConfig()?.bannerAdUnitId ?: "",
                    bannerType = bannerType,
                    container = frameContainer,
                    onAdLoaded = {
                        Log.d(TAG, "AdMob banner loaded successfully (fallback)")
                        FirebaseAnalyticsManager.logAdShown("banner", ADMOB, true)
                    },
                    onAdFailedToLoad = { error ->
                        Log.e(TAG, "AdMob banner also failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error showing AdMob banner: ${e.message}")
            }
        }
        
        // First try AppLovin MAX
        try {
            val adUnitId = AppLovinMediationManager.getConfig()?.bannerAdUnitId ?: ""
            if (adUnitId.isNotEmpty() && AppLovinMediationManager.isInitialized()) {
                Log.d(TAG, "Attempting to show AppLovin MAX banner (primary)")
                val bannerHelper = AppLovinBannerHelper(context)
                val bannerType = when (adSize) {
                    BannerSize.ADAPTIVE, BannerSize.BANNER -> AppLovinBannerHelper.BannerType.BANNER
                    BannerSize.MEDIUM_RECTANGLE -> AppLovinBannerHelper.BannerType.MREC
                    BannerSize.LEADERBOARD -> AppLovinBannerHelper.BannerType.LEADER
                    else -> AppLovinBannerHelper.BannerType.BANNER
                }
                bannerHelper.createBannerView(
                    adUnitId = adUnitId,
                    bannerType = bannerType,
                    container = frameContainer,
                    onAdLoaded = {
                        Log.d(TAG, "AppLovin MAX banner loaded successfully (primary)")
                        FirebaseAnalyticsManager.logAdShown("banner", APPLOVIN_MAX, true)
                    },
                    onAdFailedToLoad = { error ->
                        Log.e(TAG, "AppLovin MAX banner failed: ${error.message}, trying AdMob fallback")
                        tryAdMobFallback()
                    }
                )
                return true
            } else {
                Log.d(TAG, "AppLovin MAX not initialized or no banner ad unit ID configured, trying AdMob fallback")
                tryAdMobFallback()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing AppLovin MAX banner: ${e.message}, trying AdMob fallback")
            tryAdMobFallback()
            return true
        }
    }
    
    @JvmStatic
    fun showRewardedAd(onUserEarnedReward: ((type: String, amount: Int) -> Unit)? = null): Boolean {
        Log.d(TAG, "Attempting to show rewarded ad - checking AppLovin MAX first")
        
        // First try AppLovin MAX
        if (AppLovinMediationManager.isRewardedReady()) {
            Log.d(TAG, "Showing AppLovin MAX rewarded ad")
            val shown = AppLovinMediationManager.showRewardedAd(
                onUserRewarded = { reward ->
                    onUserEarnedReward?.invoke(reward.label, reward.amount)
                }
            )
            if (shown) {
                FirebaseAnalyticsManager.logAdShown("rewarded", APPLOVIN_MAX, true)
            }
            return shown
        }
        Log.d(TAG, "AppLovin MAX rewarded ad not ready, trying AdMob")
        
        // Fallback to AdMob
        if (AdMobManager.isRewardedReady()) {
            Log.d(TAG, "Showing AdMob rewarded ad")
            val shown = AdMobManager.showRewardedAd(
                onUserEarnedReward = { reward ->
                    onUserEarnedReward?.invoke(reward.type, reward.amount)
                }
            )
            if (shown) {
                FirebaseAnalyticsManager.logAdShown("rewarded", ADMOB, true)
            }
            return shown
        }
        Log.d(TAG, "AdMob rewarded ad not ready")
        
        Log.d(TAG, "No rewarded ads ready from any network")
        FirebaseAnalyticsManager.logAdShown(
            adType = "rewarded",
            adNetwork = ADMOB,
            success = false,
            errorMessage = "Rewarded ad not ready on AppLovin MAX or AdMob"
        )
        return false
    }
    
    @JvmStatic
    fun showNativeAd(
        container: ViewGroup,
        layoutResId: Int? = null
    ): Boolean {
        val activity = getCurrentActivity()
        if (activity == null) {
            Log.e(TAG, "No current activity available for showing native ad")
            return false
        }
        
        Log.d(TAG, "Attempting to show native ad - checking AppLovin MAX first")
        
        // First try AppLovin MAX
        try {
            val adUnitId = AppLovinMediationManager.getConfig()?.nativeAdUnitId ?: ""
            if (adUnitId.isNotEmpty() && AppLovinMediationManager.isInitialized()) {
                Log.d(TAG, "Attempting to show AppLovin MAX native ad")
                val nativeHelper = AppLovinNativeAdHelper(activity)
                nativeHelper.loadNativeAd(
                    adUnitId = adUnitId,
                    onAdLoaded = {
                        Log.d(TAG, "AppLovin MAX native ad loaded successfully")
                        FirebaseAnalyticsManager.logAdShown("native", APPLOVIN_MAX, true)
                    },
                    onAdFailedToLoad = { error ->
                        Log.e(TAG, "AppLovin MAX native ad failed: ${error.message}")
                        // Try AdMob as fallback
                        try {
                            Log.d(TAG, "Attempting to show AdMob native ad as fallback")
                            val admobHelper = AdMobNativeAdHelper(activity)
                            admobHelper.loadNativeAd(
                                onAdLoaded = { nativeAd ->
                                    Log.d(TAG, "AdMob native ad loaded successfully (fallback)")
                                    FirebaseAnalyticsManager.logAdShown("native", ADMOB, true)
                                },
                                onAdFailedToLoad = { admobError ->
                                    Log.e(TAG, "AdMob native ad also failed: ${admobError.message}")
                                }
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error showing AdMob native ad: ${e.message}")
                        }
                    }
                )
                return true
            } else {
                Log.d(TAG, "AppLovin MAX not initialized or no native ad unit ID configured, trying AdMob")
                // Try AdMob directly
                try {
                    Log.d(TAG, "Attempting to show AdMob native ad")
                    val admobHelper = AdMobNativeAdHelper(activity)
                    admobHelper.loadNativeAd(
                        onAdLoaded = { nativeAd ->
                            Log.d(TAG, "AdMob native ad loaded successfully")
                            FirebaseAnalyticsManager.logAdShown("native", ADMOB, true)
                        },
                        onAdFailedToLoad = { error ->
                            Log.e(TAG, "AdMob native ad failed: ${error.message}")
                        }
                    )
                    return true
                } catch (e: Exception) {
                    Log.e(TAG, "Error showing AdMob native ad: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing AppLovin MAX native ad: ${e.message}, trying AdMob")
            // Try AdMob as fallback
            try {
                Log.d(TAG, "Attempting to show AdMob native ad as fallback")
                val admobHelper = AdMobNativeAdHelper(activity)
                admobHelper.loadNativeAd(
                    onAdLoaded = { nativeAd ->
                        Log.d(TAG, "AdMob native ad loaded successfully (fallback)")
                        FirebaseAnalyticsManager.logAdShown("native", ADMOB, true)
                    },
                    onAdFailedToLoad = { error ->
                        Log.e(TAG, "AdMob native ad also failed: ${error.message}")
                    }
                )
                return true
            } catch (ex: Exception) {
                Log.e(TAG, "Error showing AdMob native ad: ${ex.message}")
            }
        }
        
        Log.d(TAG, "Failed to show native ad from any network")
        return false
    }
    
    @JvmStatic
    fun isAnyInterstitialReady(): Boolean {
        // Check AppLovin MAX first
        if (AppLovinMediationManager.isInterstitialReady()) {
            return true
        }
        
        // Then check AdMob
        if (AdMobManager.isInterstitialReady()) {
            return true
        }
        
        return false
    }
    
    @JvmStatic
    fun isAnyRewardedAdReady(): Boolean {
        // Check AppLovin MAX first
        if (AppLovinMediationManager.isRewardedReady()) {
            return true
        }
        
        // Then check AdMob
        if (AdMobManager.isRewardedReady()) {
            return true
        }
        
        return false
    }
    
    @JvmStatic
    fun showMREC(
        container: ViewGroup
    ): Boolean {
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "MREC ads skipped (debugNoAds mode)")
            return false
        }
        
        val context = getAppContext()
        if (context == null) {
            Log.e(TAG, "No application context available for showing MREC")
            return false
        }
        
        val frameContainer = ensureFrameLayout(container)

        Log.d(TAG, "Attempting to show MREC - checking AppLovin MAX first")
        
        // Helper function to try AdMob as fallback
        fun tryAdMobFallback() {
            try {
                Log.d(TAG, "Attempting to show AdMob MREC as fallback")
                val mrecView = AdMobMRECView(context)
                mrecView.loadMREC(
                    adUnitId = AdMobManager.getConfig()?.mrecAdUnitId ?: "",
                    onAdLoaded = {
                        Log.d(TAG, "AdMob MREC loaded successfully (fallback)")
                        frameContainer.removeAllViews()
                        frameContainer.addView(mrecView)
                        FirebaseAnalyticsManager.logAdShown("mrec", ADMOB, true)
                    },
                    onAdFailedToLoad = { error ->
                        Log.e(TAG, "AdMob MREC also failed: ${error.message}")
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error showing AdMob MREC: ${e.message}")
            }
        }
        
        // First try AppLovin MAX
        try {
            val adUnitId = AppLovinMediationManager.getConfig()?.mrecAdUnitId ?: ""
            if (adUnitId.isNotEmpty() && AppLovinMediationManager.isInitialized()) {
                Log.d(TAG, "Attempting to show AppLovin MAX MREC (primary)")
                val bannerHelper = AppLovinBannerHelper(context)
                bannerHelper.createBannerView(
                    adUnitId = adUnitId,
                    bannerType = AppLovinBannerHelper.BannerType.MREC,
                    container = frameContainer,
                    onAdLoaded = {
                        Log.d(TAG, "AppLovin MAX MREC loaded successfully (primary)")
                        FirebaseAnalyticsManager.logAdShown("mrec", APPLOVIN_MAX, true)
                    },
                    onAdFailedToLoad = { error ->
                        Log.e(TAG, "AppLovin MAX MREC failed: ${error.message}, trying AdMob fallback")
                        tryAdMobFallback()
                    }
                )
                return true
            } else {
                Log.d(TAG, "AppLovin MAX not initialized or no MREC ad unit ID configured, trying AdMob fallback")
                tryAdMobFallback()
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing AppLovin MAX MREC: ${e.message}, trying AdMob fallback")
            tryAdMobFallback()
            return true
        }
    }
    
    @JvmStatic
    fun showAppOpenAd(): Boolean {
        Log.d(TAG, "Attempting to show app open ad - checking AppLovin MAX first")
        var shown = false
        
        // First try AppLovin MAX
        val appLovinAppOpenManager = AppLovinAppOpenAdManager.getInstance()
        if (appLovinAppOpenManager != null) {
            Log.d(TAG, "Attempting to show AppLovin MAX app open ad")
            shown = appLovinAppOpenManager.showAdIfAvailable()
            if (shown) {
                FirebaseAnalyticsManager.logAdShown("app_open", APPLOVIN_MAX, true)
                return true
            }
        }
        Log.d(TAG, "AppLovin MAX app open ad manager not initialized, trying AdMob")
        
        // Fallback to AdMob
        val admobAppOpenManager = AppOpenAdManager.getInstance()
        val activity = getCurrentActivity()
        if (!shown && admobAppOpenManager != null && activity != null) {
            Log.d(TAG, "Attempting to show AdMob app open ad")
            shown = admobAppOpenManager.showAdIfAvailable(activity)
            if (shown) {
                FirebaseAnalyticsManager.logAdShown("app_open", ADMOB, true)
                return true
            }
        }
        Log.d(TAG, "AdMob app open ad not ready or no activity available")
        
        Log.d(TAG, "No app open ads ready from any network")
        val failureNetwork = when {
            admobAppOpenManager != null -> ADMOB
            appLovinAppOpenManager != null -> APPLOVIN_MAX
            else -> APPLOVIN_MAX
        }
        FirebaseAnalyticsManager.logAdShown(
            adType = "app_open",
            adNetwork = failureNetwork,
            success = false,
            errorMessage = "App open ad not ready on AppLovin MAX or AdMob"
        )
        return false
    }
    
    @JvmStatic
    fun loadAdmobInterstitial() {
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "AdMob interstitial loading skipped (debugNoAds mode)")
            return
        }
        
        val adMobManager = getAdMobManager()
        if (adMobManager == null) {
            Log.e(TAG, "AdMob not initialized. Call initAdMob() first")
            return
        }
        
        Log.d(TAG, "Loading AdMob interstitial ad")
        adMobManager.loadInterstitialAd()
    }
    
    @JvmStatic
    fun loadAdmobRewarded() {
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "AdMob rewarded loading skipped (debugNoAds mode)")
            return
        }
        
        val adMobManager = getAdMobManager()
        if (adMobManager == null) {
            Log.e(TAG, "AdMob not initialized. Call initAdMob() first")
            return
        }
        
        Log.d(TAG, "Loading AdMob rewarded ad")
        adMobManager.loadRewardedAd()
    }
    
    @JvmStatic
    fun loadApplovinMaxInterstitial() {
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "AppLovin MAX interstitial loading skipped (debugNoAds mode)")
            return
        }
        
        val appLovinManager = getAppLovinManager()
        if (appLovinManager == null) {
            Log.e(TAG, "AppLovin MAX not initialized. Call initAppLovinMax() first")
            return
        }
        
        Log.d(TAG, "Loading AppLovin MAX interstitial ad")
        appLovinManager.loadInterstitialAd()
    }
    
    @JvmStatic
    fun loadApplovinMaxRewarded() {
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "AppLovin MAX rewarded loading skipped (debugNoAds mode)")
            return
        }
        
        val appLovinManager = getAppLovinManager()
        if (appLovinManager == null) {
            Log.e(TAG, "AppLovin MAX not initialized. Call initAppLovinMax() first")
            return
        }
        
        Log.d(TAG, "Loading AppLovin MAX rewarded ad")
        appLovinManager.loadRewardedAd()
    }
    

    enum class BannerSize {
        ADAPTIVE,
        BANNER,
        LARGE_BANNER,
        MEDIUM_RECTANGLE,
        FULL_BANNER,
        LEADERBOARD
    }
}
