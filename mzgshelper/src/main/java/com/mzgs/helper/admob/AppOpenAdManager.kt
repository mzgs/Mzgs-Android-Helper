package com.mzgs.helper.admob

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
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
    private val adUnitId: String
) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val AD_EXPIRY_HOURS = 4L
        
        @Volatile
        private var INSTANCE: AppOpenAdManager? = null
        
        fun initialize(application: Application, adUnitId: String): AppOpenAdManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppOpenAdManager(application, adUnitId).also { 
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
    
    init {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    
    private fun setup() {
        Log.d(TAG, "App Open Ad Manager initialized")
    }
    
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        currentActivity?.let {
            if (!isShowingAd) {
                showAdIfAvailable(it)
            }
        }
    }
    
    fun fetchAd(context: Context) {
        if (isLoadingAd || isAdAvailable()) {
            return
        }
        
        if (!AdMobMediationManager.getInstance(context).canShowAds()) {
            Log.w(TAG, "Cannot show ads - consent not obtained")
            return
        }
        
        isLoadingAd = true
        val request = AdRequest.Builder().build()
        
        AppOpenAd.load(
            context,
            adUnitId,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    loadTime = Date().time
                    Log.d(TAG, "App open ad loaded")
                }
                
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isLoadingAd = false
                    Log.e(TAG, "Failed to load app open ad: ${loadAdError.message}")
                }
            }
        )
    }
    
    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference = Date().time - loadTime
        val numMilliSecondsPerHour = 3600000L
        return dateDifference < numMilliSecondsPerHour * numHours
    }
    
    private fun isAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(AD_EXPIRY_HOURS)
    }
    
    fun showAdIfAvailable(
        activity: Activity,
        onAdDismissed: () -> Unit = {},
        onAdFailedToShow: () -> Unit = {}
    ) {
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
        
        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                fetchAd(activity)
                onAdDismissed()
                Log.d(TAG, "App open ad dismissed")
            }
            
            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                fetchAd(activity)
                onAdFailedToShow()
                Log.e(TAG, "Failed to show app open ad: ${adError.message}")
            }
            
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                Log.d(TAG, "App open ad showed")
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "App open ad clicked")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "App open ad impression")
            }
        }
        
        isShowingAd = true
        appOpenAd?.show(activity)
    }
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    
    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) {
            currentActivity = activity
        }
    }
    
    override fun onActivityResumed(activity: Activity) {
        if (!isShowingAd) {
            currentActivity = activity
        }
    }
    
    override fun onActivityPaused(activity: Activity) {}
    
    override fun onActivityStopped(activity: Activity) {}
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
    
    fun isShowingAd(): Boolean = isShowingAd
    
    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            appOpenAd = null
            isLoadingAd = false
            isShowingAd = false
            loadTime = 0
        } else {
            currentActivity?.let { fetchAd(it) }
        }
    }
}