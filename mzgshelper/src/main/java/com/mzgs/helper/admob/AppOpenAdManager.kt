package com.mzgs.helper.admob

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import java.util.Date

class AppOpenAdManager(
    private val application: Application,
    private val config: AdMobConfig
) {
    
    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val AD_EXPIRY_HOURS = 4L
        
        @Volatile
        private var INSTANCE: AppOpenAdManager? = null
        
        fun initialize(application: Application, config: AdMobConfig): AppOpenAdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppOpenAdManager(application, config).also { 
                    INSTANCE = it
                    it.setup()
                }
            }
        }
        
        fun getInstance(): AppOpenAdManager? = INSTANCE
    }
    
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var isShowingAd = false
    private var loadTime: Long = 0
    private var isFirstLaunch = true
    
    // No need for init - no lifecycle callbacks to register
    
    private fun setup() {
        Log.d(TAG, "App Open Ad Manager initialized with enableAppOpenAd: ${config.enableAppOpenAd}")
        
        // Don't register for lifecycle - Ads class handles this centrally
        // ProcessLifecycleOwner.get().lifecycle.addObserver(this) - REMOVED
        
        // Don't load on app start - will load when app goes to background
        // or when explicitly requested
    }
    
    // Lifecycle methods removed - Ads class handles app lifecycle centrally
    // The Ads class will call fetchAd() and showAdIfAvailable() as needed
    
    fun fetchAd(context: Context) {
        if (!config.shouldShowAppOpenAd(context)) {
            Log.d(TAG, "App open ads are disabled")
            return
        }
        
        val adUnitId = config.appOpenAdUnitId
        if (adUnitId.isEmpty()) {
            Log.w(TAG, "App open ad unit ID is empty")
            return
        }
        
        if (isLoadingAd) {
            Log.d(TAG, "Ad is already loading")
            return
        }
        
        if (isAdAvailable()) {
            Log.d(TAG, "Valid ad already available")
            return
        }
        
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        
        Log.d(TAG, "Loading app open ad with unit ID: $adUnitId")
        
        AppOpenAd.load(
            context,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    Log.d(TAG, "App open ad loaded successfully at ${Date(loadTime)}")
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "app_open",
                        adUnitId = adUnitId,
                        adNetwork = "admob",
                        success = true
                    )
                    
                    // Set up callbacks immediately
                    setupAdCallbacks(ad)
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    appOpenAd = null
                    Log.e(TAG, "Failed to load app open ad: ${loadAdError.message} (Code: ${loadAdError.code})")
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "app_open",
                        adUnitId = adUnitId,
                        adNetwork = "admob",
                        success = false,
                        errorMessage = loadAdError.message,
                        errorCode = loadAdError.code
                    )
                }
            }
        )
    }
    
    private fun setupAdCallbacks(ad: AppOpenAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App open ad dismissed")
                appOpenAd = null
                isShowingAd = false
                // Immediately fetch next ad
                com.mzgs.helper.Ads.getCurrentActivity()?.let { fetchAd(it) }
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Failed to show app open ad: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                // Try to fetch a new ad
                com.mzgs.helper.Ads.getCurrentActivity()?.let { fetchAd(it) }
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App open ad showed full screen content")
                isShowingAd = true
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "App open ad clicked")
                val adUnitId = config.appOpenAdUnitId
                FirebaseAnalyticsManager.logAdClicked(
                    adType = "app_open",
                    adUnitId = adUnitId,
                    adNetwork = "admob"
                )
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "App open ad impression recorded")
                val adUnitId = config.appOpenAdUnitId
                FirebaseAnalyticsManager.logAdImpression(
                    adType = "app_open",
                    adUnitId = adUnitId,
                    adNetwork = "admob"
                )
            }
        }
    }
    
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = 3600000L
        return dateDifference < numMilliSecondsPerHour * numHours
    }
    
    private fun isAdAvailable(): Boolean {
        val available = appOpenAd != null && wasLoadTimeLessThanNHoursAgo(AD_EXPIRY_HOURS)
        if (available) {
            Log.d(TAG, "Ad is available (loaded ${(Date().time - loadTime) / 1000} seconds ago)")
        }
        return available
    }
    
    fun showAdIfAvailable(
        activity: Activity,
        onAdDismissed: () -> Unit = {},
        onAdFailedToShow: () -> Unit = {}
    ) {
        Log.d(TAG, "showAdIfAvailable called")
        
        if (!config.shouldShowAppOpenAd(activity)) {
            Log.d(TAG, "App open ads are disabled")
            onAdFailedToShow()
            return
        }
        
        if (isShowingAd) {
            Log.d(TAG, "App open ad is already showing")
            return
        }
        
        if (!isAdAvailable()) {
            Log.d(TAG, "App open ad not available, fetching new ad")
            fetchAd(activity)
            onAdFailedToShow()
            return
        }
        
        val ad = appOpenAd
        if (ad == null) {
            Log.e(TAG, "App open ad is null despite being available")
            onAdFailedToShow()
            return
        }
        
        Log.d(TAG, "Showing app open ad now")
        
        // Set callbacks for this show
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Ad dismissed by user")
                appOpenAd = null
                isShowingAd = false
                fetchAd(activity) // Fetch next ad
                onAdDismissed()
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Ad failed to show: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                fetchAd(activity) // Try to fetch new ad
                onAdFailedToShow()
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Ad is now showing full screen")
                isShowingAd = true
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "User clicked the ad")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "Ad impression recorded")
            }
        }
        
        // Mark as showing before calling show()
        isShowingAd = true
        ad.show(activity)
        Log.d(TAG, "show() method called on ad")
    }
    
    // Activity lifecycle callbacks removed - using Ads.getCurrentActivity() instead
    
    fun isShowingAd(): Boolean = isShowingAd
    
    fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "setEnabled: $enabled")
        if (!enabled) {
            appOpenAd = null
            isLoadingAd = false
            isShowingAd = false
            loadTime = 0
        } else if (config.enableAppOpenAd) {
            com.mzgs.helper.Ads.getCurrentActivity()?.let { fetchAd(it) }
        }
    }
    
    // Manual method to show ad (for testing)
    fun showAdManually() {
        Log.d(TAG, "Manual show ad requested")
        com.mzgs.helper.Ads.getCurrentActivity()?.let { activity ->
            showAdIfAvailable(activity)
        } ?: Log.e(TAG, "No current activity available for manual show")
    }
}