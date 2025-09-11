package com.mzgs.helper

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
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

object Ads : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private const val TAG = "Ads"
    private const val ADMOB = "admob"
    private const val APPLOVIN_MAX = "applovin_max"
    
    private var applicationContext: Context? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var isFirstLaunch = true
    private var appOpenAdEnabled = false
    
    fun init(context: Context) {
        applicationContext = context.applicationContext
        
        // Register for process lifecycle to detect app foreground/background
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // Register for activity lifecycle callbacks
        if (context is Activity) {
            currentActivityRef = WeakReference(context)
            context.application.registerActivityLifecycleCallbacks(this)
        } else if (context is Application) {
            context.registerActivityLifecycleCallbacks(this)
        } else if (context.applicationContext is Application) {
            (context.applicationContext as Application).registerActivityLifecycleCallbacks(this)
        }
        
        // Get and log the advertising ID (skip if ads are disabled)
        if (!MzgsHelper.debugNoAds) {
            getAdvertisingId(context)
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
        return currentActivityRef?.get()
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
                    FirebaseAnalyticsManager.logAdShown("app_open", "none", false)
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
                val context = applicationContext
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
        val context = applicationContext
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
        val context = applicationContext
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
        val context = applicationContext
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
        val context = applicationContext
        if (context == null) {
            Log.e(TAG, "Ads.init() must be called before getAdMobManager()")
            return null
        }
        return AdMobManager.getInstance(context)
    }
    
    @JvmStatic
    fun getAppLovinManager(): AppLovinMediationManager? {
        val context = applicationContext
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
        FirebaseAnalyticsManager.logAdShown("interstitial", "none", false)
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
        FirebaseAnalyticsManager.logAdShown("interstitial", "none", false)
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
        
        val context = applicationContext
        if (context == null) {
            Log.e(TAG, "No application context available for showing banner")
            return false
        }
        
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
                    container = container as android.widget.FrameLayout,
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
                    container = container as android.widget.FrameLayout,
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
        FirebaseAnalyticsManager.logAdShown("rewarded", "none", false)
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
        
        val context = applicationContext
        if (context == null) {
            Log.e(TAG, "No application context available for showing MREC")
            return false
        }
        
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
                        container.removeAllViews()
                        container.addView(mrecView)
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
                    container = container as android.widget.FrameLayout,
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
        
        // First try AppLovin MAX
        val appLovinAppOpenManager = AppLovinAppOpenAdManager.getInstance()
        if (appLovinAppOpenManager != null) {
            Log.d(TAG, "Attempting to show AppLovin MAX app open ad")
            appLovinAppOpenManager.showAdIfAvailable()
            FirebaseAnalyticsManager.logAdShown("app_open", APPLOVIN_MAX, true)
            return true
        }
        Log.d(TAG, "AppLovin MAX app open ad manager not initialized, trying AdMob")
        
        // Fallback to AdMob
        val admobAppOpenManager = AppOpenAdManager.getInstance()
        val activity = getCurrentActivity()
        if (admobAppOpenManager != null && activity != null) {
            Log.d(TAG, "Attempting to show AdMob app open ad")
            admobAppOpenManager.showAdIfAvailable(activity)
            FirebaseAnalyticsManager.logAdShown("app_open", ADMOB, true)
            return true
        }
        Log.d(TAG, "AdMob app open ad not ready or no activity available")
        
        Log.d(TAG, "No app open ads ready from any network")
        FirebaseAnalyticsManager.logAdShown("app_open", "none", false)
        return false
    }
    
    enum class BannerSize {
        ADAPTIVE,
        BANNER,
        LARGE_BANNER,
        MEDIUM_RECTANGLE,
        FULL_BANNER,
        LEADERBOARD
    }
    
    // Activity Lifecycle Callbacks
    // Automatic Activity Lifecycle Tracking
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d(TAG, "Activity created: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityStarted(activity: Activity) {
        // Update activity reference when activity becomes visible
        currentActivityRef = WeakReference(activity)
        Log.d(TAG, "Activity started, auto-tracking: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityResumed(activity: Activity) {
        // Activity is in foreground, definitely our current activity
        currentActivityRef = WeakReference(activity)
        Log.d(TAG, "Activity resumed, current activity auto-set to: ${activity.javaClass.simpleName}")
        // Note: AdMobManager now tracks its own activity automatically
    }
    
    override fun onActivityPaused(activity: Activity) {
        // Activity going to background, but don't clear yet - another activity might be starting
        Log.d(TAG, "Activity paused: ${activity.javaClass.simpleName}")
    }
    
    override fun onActivityStopped(activity: Activity) {
        // Clear reference only if this was the current activity and no new one has taken over
        if (currentActivityRef?.get() == activity) {
            // Small delay to check if another activity is taking over
            Handler(Looper.getMainLooper()).postDelayed({
                if (currentActivityRef?.get() == activity) {
                    currentActivityRef = null
                    Log.d(TAG, "Activity stopped and reference cleared: ${activity.javaClass.simpleName}")
                }
            }, 100)
        }
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Not needed
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef = null
            Log.d(TAG, "Activity destroyed and reference cleared: ${activity.javaClass.simpleName}")
            
            // Clean up ad resources to prevent memory leaks
            AdMobManager.onActivityDestroyed(activity)
        }
    }
}