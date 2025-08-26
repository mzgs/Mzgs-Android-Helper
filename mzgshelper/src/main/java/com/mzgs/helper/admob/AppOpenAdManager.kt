package com.mzgs.helper.admob

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

class AppOpenAdManager(
    private val application: Application,
    private val config: AdMobConfig
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    
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
    private var currentActivity: Activity? = null
    private var isFirstLaunch = true
    
    init {
        application.registerActivityLifecycleCallbacks(this)
    }
    
    private fun setup() {
        Log.d(TAG, "App Open Ad Manager initialized with enableAppOpenAd: ${config.enableAppOpenAd}")
        
        // Register for app lifecycle events AFTER initialization
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        if (config.enableAppOpenAd && config.getEffectiveAppOpenAdUnitId().isNotEmpty()) {
            // Fetch the first ad immediately after initialization
            Handler(Looper.getMainLooper()).postDelayed({
                fetchAd(application.applicationContext)
            }, 1000) // Small delay to ensure everything is initialized
        }
    }
    
    // Called when app comes to foreground
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "onStart - App in foreground, isFirstLaunch: $isFirstLaunch")
        
        // Don't show on first launch, only when returning from background
        if (!isFirstLaunch && config.shouldShowAppOpenAd(application)) {
            // Use a small delay to ensure activity is ready
            Handler(Looper.getMainLooper()).postDelayed({
                currentActivity?.let { activity ->
                    if (!activity.isFinishing && !isShowingAd) {
                        Log.d(TAG, "Attempting to show app open ad after returning from background")
                        showAdIfAvailable(activity)
                    }
                }
            }, 100)
        } else if (isFirstLaunch) {
            isFirstLaunch = false
            Log.d(TAG, "First launch detected, not showing ad")
        } else if (!config.shouldShowAppOpenAd(application)) {
            Log.d(TAG, "App open ads disabled for debug mode")
        }
    }
    
    // Called when app goes to background
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "onStop - App going to background")
        
        if (config.shouldShowAppOpenAd(application)) {
            // Preload ad for next time
            Handler(Looper.getMainLooper()).postDelayed({
                currentActivity?.let { activity ->
                    if (!isAdAvailable() && !isLoadingAd) {
                        Log.d(TAG, "Fetching ad for next app open")
                        fetchAd(activity)
                    } else {
                        Log.d(TAG, "Ad already available or loading, skipping fetch")
                    }
                }
            }, 100)
        }
    }
    
    fun fetchAd(context: Context) {
        if (!config.shouldShowAppOpenAd(context)) {
            Log.d(TAG, "App open ads are disabled")
            return
        }
        
        val adUnitId = config.getEffectiveAppOpenAdUnitId()
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
        
        if (!AdMobMediationManager.getInstance(context).canShowAds()) {
            Log.w(TAG, "Cannot show ads - consent not obtained")
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
                    
                    // Set up callbacks immediately
                    setupAdCallbacks(ad)
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    appOpenAd = null
                    Log.e(TAG, "Failed to load app open ad: ${loadAdError.message} (Code: ${loadAdError.code})")
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
                currentActivity?.let { fetchAd(it) }
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e(TAG, "Failed to show app open ad: ${adError.message}")
                appOpenAd = null
                isShowingAd = false
                // Try to fetch a new ad
                currentActivity?.let { fetchAd(it) }
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App open ad showed full screen content")
                isShowingAd = true
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "App open ad clicked")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "App open ad impression recorded")
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
    
    // Activity lifecycle callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d(TAG, "onActivityCreated: ${activity.localClassName}")
    }
    
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
        Log.d(TAG, "onActivityStarted: ${activity.localClassName}, currentActivity set")
    }
    
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
        Log.d(TAG, "onActivityResumed: ${activity.localClassName}, currentActivity updated")
    }
    
    override fun onActivityPaused(activity: Activity) {
        Log.d(TAG, "onActivityPaused: ${activity.localClassName}")
    }
    
    override fun onActivityStopped(activity: Activity) {
        Log.d(TAG, "onActivityStopped: ${activity.localClassName}")
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    
    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "onActivityDestroyed: ${activity.localClassName}")
        if (currentActivity == activity) {
            currentActivity = null
            Log.d(TAG, "currentActivity cleared")
        }
    }
    
    fun isShowingAd(): Boolean = isShowingAd
    
    fun setEnabled(enabled: Boolean) {
        Log.d(TAG, "setEnabled: $enabled")
        if (!enabled) {
            appOpenAd = null
            isLoadingAd = false
            isShowingAd = false
            loadTime = 0
        } else if (config.enableAppOpenAd) {
            currentActivity?.let { fetchAd(it) }
        }
    }
    
    // Manual method to show ad (for testing)
    fun showAdManually() {
        Log.d(TAG, "Manual show ad requested")
        currentActivity?.let { activity ->
            showAdIfAvailable(activity)
        } ?: Log.e(TAG, "No current activity available for manual show")
    }
}