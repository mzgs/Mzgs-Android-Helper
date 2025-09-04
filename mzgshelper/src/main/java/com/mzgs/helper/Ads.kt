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
import java.lang.ref.WeakReference
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

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
        
        // Get and log the advertising ID
        getAdvertisingId(context)
    }
    
    private fun getAdvertisingId(context: Context) {
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
                showAppOpenAd()
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
        AdMobMediationManager.init(context, config, onInitComplete)
        
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
    fun getAdMobManager(): AdMobMediationManager? {
        val context = applicationContext
        if (context == null) {
            Log.e(TAG, "Ads.init() must be called before getAdMobManager()")
            return null
        }
        return AdMobMediationManager.getInstance(context)
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
    
    private fun getAdsOrder(): List<String> {
        // Try AppLovin first, fallback to AdMob if not available
        return listOf(APPLOVIN_MAX, ADMOB)
    }
    
    @JvmStatic
    fun showInterstitial(): Boolean {
        val context = applicationContext ?: return false
        val activity = currentActivityRef?.get()
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show interstitial with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    if (AppLovinMediationManager.isInterstitialReady()) {
                        Log.d(TAG, "Showing AppLovin MAX interstitial")
                        return AppLovinMediationManager.showInterstitialAd()
                    }
                    Log.d(TAG, "AppLovin MAX interstitial not ready, trying next")
                }
                ADMOB -> {
                    if (AdMobMediationManager.isInterstitialReady()) {
                        if (activity != null) {
                            // Activity is now tracked automatically via lifecycle callbacks
                            Log.d(TAG, "Showing AdMob interstitial")
                            return AdMobMediationManager.showInterstitialAd()
                        } else {
                            Log.e(TAG, "No current activity available for AdMob interstitial")
                        }
                    }
                    Log.d(TAG, "AdMob interstitial not ready, trying next")
                }
                else -> {
                    Log.w(TAG, "Unknown ad network: $network")
                }
            }
        }
        
        Log.d(TAG, "No interstitial ads ready from any network")
        return false
    }
    
    @JvmStatic
    fun showBanner(
        container: ViewGroup,
        adSize: BannerSize = BannerSize.ADAPTIVE
    ): Boolean {
        val activity = getCurrentActivity()
        if (activity == null) {
            Log.e(TAG, "No current activity available for showing banner")
            return false
        }
        
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show banner with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    try {
                        val adUnitId = AppLovinMediationManager.getConfig()?.bannerAdUnitId ?: ""
                        if (adUnitId.isNotEmpty() && AppLovinMediationManager.isInitialized()) {
                            Log.d(TAG, "Attempting to show AppLovin MAX banner")
                            val bannerHelper = AppLovinBannerHelper(activity)
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
                                    Log.d(TAG, "AppLovin MAX banner loaded successfully")
                                },
                                onAdFailedToLoad = { error ->
                                    Log.e(TAG, "AppLovin MAX banner failed: ${error.message}")
                                }
                            )
                            return true
                        } else {
                            Log.d(TAG, "AppLovin MAX not initialized or no banner ad unit ID configured, skipping")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AppLovin MAX banner: ${e.message}")
                    }
                }
                ADMOB -> {
                    try {
                        Log.d(TAG, "Attempting to show AdMob banner")
                        val bannerHelper = BannerAdHelper(activity)
                        val bannerType = when (adSize) {
                            BannerSize.ADAPTIVE -> BannerAdHelper.BannerType.ADAPTIVE_BANNER
                            BannerSize.BANNER -> BannerAdHelper.BannerType.BANNER
                            BannerSize.LARGE_BANNER -> BannerAdHelper.BannerType.LARGE_BANNER
                            BannerSize.MEDIUM_RECTANGLE -> BannerAdHelper.BannerType.MEDIUM_RECTANGLE
                            BannerSize.FULL_BANNER -> BannerAdHelper.BannerType.FULL_BANNER
                            BannerSize.LEADERBOARD -> BannerAdHelper.BannerType.LEADERBOARD
                        }
                        bannerHelper.createBannerView(
                            adUnitId = AdMobMediationManager.getConfig()?.bannerAdUnitId ?: "",
                            bannerType = bannerType,
                            container = container as android.widget.FrameLayout,
                            onAdLoaded = {
                                Log.d(TAG, "AdMob banner loaded successfully")
                            },
                            onAdFailedToLoad = { error ->
                                Log.e(TAG, "AdMob banner failed: ${error.message}")
                            }
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AdMob banner: ${e.message}")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown ad network for banner: $network")
                }
            }
        }
        
        Log.d(TAG, "Failed to show banner from any network")
        return false
    }
    
    @JvmStatic
    fun showRewardedAd(): Boolean {
        val context = applicationContext ?: return false
        val activity = currentActivityRef?.get()
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show rewarded ad with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    if (AppLovinMediationManager.isRewardedReady()) {
                        Log.d(TAG, "Showing AppLovin MAX rewarded ad")
                        return AppLovinMediationManager.showRewardedAd()
                    }
                    Log.d(TAG, "AppLovin MAX rewarded ad not ready, trying next")
                }
                ADMOB -> {
                    if (AdMobMediationManager.isRewardedReady()) {
                        if (activity != null) {
                            // Activity is now tracked automatically via lifecycle callbacks
                            Log.d(TAG, "Showing AdMob rewarded ad")
                            return AdMobMediationManager.showRewardedAd()
                        } else {
                            Log.e(TAG, "No current activity available for AdMob rewarded ad")
                        }
                    }
                    Log.d(TAG, "AdMob rewarded ad not ready, trying next")
                }
                else -> {
                    Log.w(TAG, "Unknown ad network: $network")
                }
            }
        }
        
        Log.d(TAG, "No rewarded ads ready from any network")
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
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show native ad with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    try {
                        val adUnitId = AppLovinMediationManager.getConfig()?.nativeAdUnitId ?: ""
                        if (adUnitId.isNotEmpty() && AppLovinMediationManager.isInitialized()) {
                            Log.d(TAG, "Attempting to show AppLovin MAX native ad")
                            val nativeHelper = AppLovinNativeAdHelper(activity)
                            nativeHelper.loadNativeAd(
                                adUnitId = adUnitId,
                                onAdLoaded = {
                                    Log.d(TAG, "AppLovin MAX native ad loaded successfully")
                                },
                                onAdFailedToLoad = { error ->
                                    Log.e(TAG, "AppLovin MAX native ad failed: ${error.message}")
                                }
                            )
                            return true
                        } else {
                            Log.d(TAG, "AppLovin MAX not initialized or no native ad unit ID configured, skipping")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AppLovin MAX native ad: ${e.message}")
                    }
                }
                ADMOB -> {
                    try {
                        Log.d(TAG, "Attempting to show AdMob native ad")
                        val nativeHelper = AdMobNativeAdHelper(activity)
                        nativeHelper.loadNativeAd(
                            onAdLoaded = { nativeAd ->
                                Log.d(TAG, "AdMob native ad loaded successfully")
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
                else -> {
                    Log.w(TAG, "Unknown ad network for native ad: $network")
                }
            }
        }
        
        Log.d(TAG, "Failed to show native ad from any network")
        return false
    }
    
    @JvmStatic
    fun isAnyInterstitialReady(): Boolean {
        val adsOrder = getAdsOrder()
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    if (AppLovinMediationManager.isInterstitialReady()) {
                        return true
                    }
                }
                ADMOB -> {
                    if (AdMobMediationManager.isInterstitialReady()) {
                        return true
                    }
                }
            }
        }
        
        return false
    }
    
    @JvmStatic
    fun isAnyRewardedAdReady(): Boolean {
        val adsOrder = getAdsOrder()
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    if (AppLovinMediationManager.isRewardedReady()) {
                        return true
                    }
                }
                ADMOB -> {
                    if (AdMobMediationManager.isRewardedReady()) {
                        return true
                    }
                }
            }
        }
        
        return false
    }
    
    @JvmStatic
    fun showMREC(
        container: ViewGroup
    ): Boolean {
        val activity = getCurrentActivity()
        if (activity == null) {
            Log.e(TAG, "No current activity available for showing MREC")
            return false
        }
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show MREC with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    try {
                        val adUnitId = AppLovinMediationManager.getConfig()?.getEffectiveMrecAdUnitId() ?: ""
                        if (adUnitId.isNotEmpty() && AppLovinMediationManager.isInitialized()) {
                            Log.d(TAG, "Attempting to show AppLovin MAX MREC")
                            val bannerHelper = AppLovinBannerHelper(activity)
                            bannerHelper.createBannerView(
                                adUnitId = adUnitId,
                                bannerType = AppLovinBannerHelper.BannerType.MREC,
                                container = container as android.widget.FrameLayout,
                                onAdLoaded = {
                                    Log.d(TAG, "AppLovin MAX MREC loaded successfully")
                                },
                                onAdFailedToLoad = { error ->
                                    Log.e(TAG, "AppLovin MAX MREC failed: ${error.message}")
                                }
                            )
                            return true
                        } else {
                            Log.d(TAG, "AppLovin MAX not initialized or no MREC ad unit ID configured, skipping")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AppLovin MAX MREC: ${e.message}")
                    }
                }
                ADMOB -> {
                    try {
                        Log.d(TAG, "Attempting to show AdMob MREC")
                        val mrecView = AdMobMRECView(activity)
                        mrecView.loadMREC(
                            adUnitId = AdMobMediationManager.getConfig()?.bannerAdUnitId ?: "",
                            onAdLoaded = {
                                Log.d(TAG, "AdMob MREC loaded successfully")
                                container.removeAllViews()
                                container.addView(mrecView)
                            },
                            onAdFailedToLoad = { error ->
                                Log.e(TAG, "AdMob MREC failed: ${error.message}")
                            }
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AdMob MREC: ${e.message}")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown ad network for MREC: $network")
                }
            }
        }
        
        Log.d(TAG, "Failed to show MREC from any network")
        return false
    }
    
    @JvmStatic
    fun showAppOpenAd(): Boolean {
        val context = applicationContext ?: return false
        val activity = currentActivityRef?.get()
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show app open ad with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    val appOpenManager = AppLovinAppOpenAdManager.getInstance()
                    if (appOpenManager != null) {
                        Log.d(TAG, "Attempting to show AppLovin MAX app open ad")
                        appOpenManager.showAdIfAvailable()
                        return true
                    }
                    Log.d(TAG, "AppLovin MAX app open ad manager not initialized, trying next")
                }
                ADMOB -> {
                    val appOpenManager = AppOpenAdManager.getInstance()
                    if (appOpenManager != null && activity != null) {
                        Log.d(TAG, "Attempting to show AdMob app open ad")
                        appOpenManager.showAdIfAvailable(activity)
                        return true
                    }
                    Log.d(TAG, "AdMob app open ad not ready or no activity available, trying next")
                }
                else -> {
                    Log.w(TAG, "Unknown ad network: $network")
                }
            }
        }
        
        Log.d(TAG, "No app open ads ready from any network")
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
        // Note: AdMobMediationManager now tracks its own activity automatically
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
        }
    }
}