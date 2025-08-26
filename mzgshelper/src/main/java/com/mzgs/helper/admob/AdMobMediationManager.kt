package com.mzgs.helper.admob

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class AdMobMediationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "AdMobMediation"
        private var isInitialized = false
        
        @Volatile
        private var INSTANCE: AdMobMediationManager? = null
        
        fun getInstance(context: Context): AdMobMediationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdMobMediationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    
    fun initialize(
        testDeviceIds: List<String> = emptyList(),
        onInitComplete: () -> Unit = {}
    ) {
        if (isInitialized) {
            Log.d(TAG, "AdMob already initialized")
            onInitComplete()
            return
        }
        
        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        
        MobileAds.setRequestConfiguration(requestConfiguration)
        
        MobileAds.initialize(context) { initializationStatus ->
            isInitialized = true
            Log.d(TAG, "AdMob SDK initialized")
            
            initializationStatus.adapterStatusMap.forEach { (adapterClass, status) ->
                Log.d(TAG, "Adapter: $adapterClass, Status: ${status.initializationState}, Latency: ${status.latency}ms")
            }
            
            onInitComplete()
        }
    }
    
    fun requestConsentInfo(
        activity: Activity,
        debugGeography: Int = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED,
        testDeviceHashedId: String? = null,
        onConsentReady: () -> Unit = {},
        onConsentError: (String) -> Unit = {}
    ) {
        val debugSettingsBuilder = ConsentDebugSettings.Builder(context)
            .setDebugGeography(debugGeography)
        
        testDeviceHashedId?.let {
            debugSettingsBuilder.addTestDeviceHashedId(it)
        }
        
        val params = ConsentRequestParameters.Builder()
            .setConsentDebugSettings(debugSettingsBuilder.build())
            .build()
        
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                if (consentInformation.isConsentFormAvailable) {
                    loadConsentForm(activity, onConsentReady, onConsentError)
                } else {
                    onConsentReady()
                }
            },
            { error ->
                Log.e(TAG, "Consent info error: ${error.message}")
                onConsentError(error.message)
            }
        )
    }
    
    private fun loadConsentForm(
        activity: Activity,
        onConsentReady: () -> Unit,
        onConsentError: (String) -> Unit
    ) {
        UserMessagingPlatform.loadConsentForm(
            context,
            { consentForm ->
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(activity) { error ->
                        if (error != null) {
                            Log.e(TAG, "Consent form error: ${error.message}")
                            onConsentError(error.message)
                        } else {
                            onConsentReady()
                        }
                    }
                } else {
                    onConsentReady()
                }
            },
            { error ->
                Log.e(TAG, "Failed to load consent form: ${error.message}")
                onConsentError(error.message)
            }
        )
    }
    
    fun canShowAds(): Boolean {
        return consentInformation.canRequestAds()
    }
    
    fun loadInterstitialAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {}
    ) {
        if (!canShowAds()) {
            Log.w(TAG, "Cannot request ads - consent not obtained")
            return
        }
        
        val adRequest = AdRequest.Builder().build()
        
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial ad loaded")
                    setupInterstitialCallbacks(ad)
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.e(TAG, "Failed to load interstitial: ${error.message}")
                    onAdFailedToLoad(error)
                }
            }
        )
    }
    
    private fun setupInterstitialCallbacks(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                interstitialAd = null
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Failed to show interstitial: ${error.message}")
                interstitialAd = null
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad showed")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "Interstitial ad impression")
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "Interstitial ad clicked")
            }
        }
    }
    
    fun showInterstitialAd(activity: Activity): Boolean {
        return if (interstitialAd != null) {
            interstitialAd?.show(activity)
            true
        } else {
            Log.w(TAG, "Interstitial ad not ready")
            false
        }
    }
    
    fun loadRewardedAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {}
    ) {
        if (!canShowAds()) {
            Log.w(TAG, "Cannot request ads - consent not obtained")
            return
        }
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded ad loaded")
                    setupRewardedCallbacks(ad)
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.e(TAG, "Failed to load rewarded ad: ${error.message}")
                    onAdFailedToLoad(error)
                }
            }
        )
    }
    
    private fun setupRewardedCallbacks(ad: RewardedAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                rewardedAd = null
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Failed to show rewarded ad: ${error.message}")
                rewardedAd = null
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "Rewarded ad impression")
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "Rewarded ad clicked")
            }
        }
    }
    
    fun showRewardedAd(
        activity: Activity,
        onUserEarnedReward: (RewardItem) -> Unit
    ): Boolean {
        return if (rewardedAd != null) {
            rewardedAd?.show(activity) { reward ->
                Log.d(TAG, "User earned reward: ${reward.amount} ${reward.type}")
                onUserEarnedReward(reward)
            }
            true
        } else {
            Log.w(TAG, "Rewarded ad not ready")
            false
        }
    }
    
    fun loadRewardedInterstitialAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {}
    ) {
        if (!canShowAds()) {
            Log.w(TAG, "Cannot request ads - consent not obtained")
            return
        }
        
        val adRequest = AdRequest.Builder().build()
        
        RewardedInterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    Log.d(TAG, "Rewarded interstitial ad loaded")
                    setupRewardedInterstitialCallbacks(ad)
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInterstitialAd = null
                    Log.e(TAG, "Failed to load rewarded interstitial: ${error.message}")
                    onAdFailedToLoad(error)
                }
            }
        )
    }
    
    private fun setupRewardedInterstitialCallbacks(ad: RewardedInterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial dismissed")
                rewardedInterstitialAd = null
            }
            
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "Failed to show rewarded interstitial: ${error.message}")
                rewardedInterstitialAd = null
            }
            
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial showed")
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "Rewarded interstitial impression")
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "Rewarded interstitial clicked")
            }
        }
    }
    
    fun showRewardedInterstitialAd(
        activity: Activity,
        onUserEarnedReward: (RewardItem) -> Unit
    ): Boolean {
        return if (rewardedInterstitialAd != null) {
            rewardedInterstitialAd?.show(activity) { reward ->
                Log.d(TAG, "User earned reward from interstitial: ${reward.amount} ${reward.type}")
                onUserEarnedReward(reward)
            }
            true
        } else {
            Log.w(TAG, "Rewarded interstitial not ready")
            false
        }
    }
    
    fun isInterstitialReady(): Boolean = interstitialAd != null
    
    fun isRewardedAdReady(): Boolean = rewardedAd != null
    
    fun isRewardedInterstitialReady(): Boolean = rewardedInterstitialAd != null
    
    fun resetConsent() {
        consentInformation.reset()
        Log.d(TAG, "Consent information reset")
    }
}