package com.mzgs.helper.applovin

import android.app.Activity
import android.app.Application
import android.util.Log
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAppOpenAd
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import java.util.Date

class AppLovinAppOpenAdManager private constructor(
    private val application: Application,
    private val config: AppLovinConfig
) {
    
    private var appOpenAd: MaxAppOpenAd? = null
    private var loadTime: Long = 0
    private var isShowingAd = false
    
    companion object {
        private const val TAG = "AppLovinAppOpenManager"
        private const val AD_EXPIRATION_HOURS = 4
        
        @Volatile
        private var INSTANCE: AppLovinAppOpenAdManager? = null
        
        fun getInstance(): AppLovinAppOpenAdManager? = INSTANCE
        
        fun initialize(application: Application, config: AppLovinConfig) {
            if (INSTANCE == null) {
                synchronized(this) {
                    if (INSTANCE == null) {
                        INSTANCE = AppLovinAppOpenAdManager(application, config)
                    }
                }
            }
        }
    }
    
    // No init needed - no lifecycle callbacks to register
    
    // Lifecycle methods removed - Ads class handles app lifecycle centrally
    // The Ads class will call loadAd() and showAdIfAvailable() as needed
    
    fun loadAd() {
        if (!config.shouldShowAppOpenAd(application)) {
            Log.d(TAG, "App open ads disabled")
            return
        }
        
        val adUnitId = config.appOpenAdUnitId
        if (adUnitId.isEmpty()) {
            Log.w(TAG, "App open ad unit ID is empty")
            return
        }
        
        @Suppress("DEPRECATION") // Using the recommended approach for SDK 13.x
        appOpenAd = MaxAppOpenAd(adUnitId, application)
        appOpenAd?.setListener(object : MaxAdListener {
            override fun onAdLoaded(ad: MaxAd) {
                Log.d(TAG, "App open ad loaded")
                loadTime = Date().time
                FirebaseAnalyticsManager.logAdLoad(
                    adType = "app_open",
                    adUnitId = adUnitId,
                    adNetwork = "applovin_max",
                    success = true
                )
            }
            
            override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.e(TAG, "Failed to load app open ad: ${error.message}")
                FirebaseAnalyticsManager.logAdLoad(
                    adType = "app_open",
                    adUnitId = adUnitId,
                    adNetwork = "applovin_max",
                    success = false,
                    errorMessage = error.message,
                    errorCode = error.code
                )
                appOpenAd = null
            }
            
            override fun onAdDisplayed(ad: MaxAd) {
                Log.d(TAG, "App open ad displayed")
                isShowingAd = true
            }
            
            override fun onAdHidden(ad: MaxAd) {
                Log.d(TAG, "App open ad hidden")
                isShowingAd = false
                appOpenAd = null
                loadAd()
            }
            
            override fun onAdClicked(ad: MaxAd) {
                Log.d(TAG, "App open ad clicked")
            }
            
            override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                Log.e(TAG, "Failed to display app open ad: ${error.message}")
                isShowingAd = false
                appOpenAd = null
                loadAd()
            }
        })
        
        appOpenAd?.loadAd()
    }
    
    fun showAdIfAvailable() {
        if (!config.shouldShowAppOpenAd(application)) {
            Log.d(TAG, "App open ads disabled")
            loadAd()
            return
        }
        
        if (isShowingAd) {
            Log.d(TAG, "App open ad is already showing")
            return
        }
        
        if (!isAdAvailable()) {
            Log.d(TAG, "App open ad not available, loading new ad")
            loadAd()
            return
        }
        
        com.mzgs.helper.Ads.getCurrentActivity()?.let { activity ->
            if (shouldNotShowAppOpenAd(activity)) {
                Log.d(TAG, "Skipping app open ad for excluded activity: ${activity.javaClass.simpleName}")
                return
            }
            
            appOpenAd?.showAd()
        }
    }
    
    private fun isAdAvailable(): Boolean {
        if (appOpenAd?.isReady != true) {
            return false
        }
        
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * AD_EXPIRATION_HOURS
    }
    
    private fun shouldNotShowAppOpenAd(activity: Activity): Boolean {
        val excludedActivities = listOf(
            "SplashActivity",
            "LoadingActivity",
            "OnboardingActivity",
            "LoginActivity",
            "SignupActivity",
            "PermissionActivity"
        )
        
        val activityName = activity.javaClass.simpleName
        return excludedActivities.any { activityName.contains(it, ignoreCase = true) }
    }
    
    fun setEnabled(enabled: Boolean) {
        if (enabled && config.enableAppOpenAd) {
            loadAd()
        } else {
            appOpenAd = null
        }
    }
    
    fun forceShowAd(activity: Activity) {
        if (!config.shouldShowAppOpenAd(application)) {
            Log.d(TAG, "App open ads disabled")
            return
        }
        
        // Activity tracked via Ads.getCurrentActivity()
        
        if (isAdAvailable() && !isShowingAd) {
            appOpenAd?.showAd()
        } else {
            loadAd()
        }
    }
    
    // Activity lifecycle callbacks removed - using Ads.getCurrentActivity() instead
}