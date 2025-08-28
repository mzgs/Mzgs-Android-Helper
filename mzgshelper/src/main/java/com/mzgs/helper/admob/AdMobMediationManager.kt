package com.mzgs.helper.admob

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import com.google.android.gms.ads.*
import com.google.ads.mediation.admob.AdMobAdapter
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow
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
    
    private var adConfig: AdMobConfig? = null
    
    companion object {
        private const val TAG = "AdMobMediation"
        private var isInitialized = false
        private const val MAX_RETRY_ATTEMPTS = 6 // Allow up to 6 retries for exponential backoff
        
        @Volatile
        private var INSTANCE: AdMobMediationManager? = null
        
        fun getInstance(context: Context): AdMobMediationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AdMobMediationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        // Calculate exponential backoff delay
        // AppLovin recommends exponentially higher delays up to a maximum delay (64 seconds)
        private fun getRetryDelayMillis(retryAttempt: Int): Long {
            // Calculate delay: 2^retryAttempt seconds, capped at 2^6 (64 seconds)
            val exponent = min(6, retryAttempt)
            return TimeUnit.SECONDS.toMillis(2.0.pow(exponent).toLong())
        }
    }
    
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private val consentInformation: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    
    fun initialize(
        config: AdMobConfig,
        onInitComplete: () -> Unit = {}
    ) {
        this.adConfig = config
        
        if (isInitialized) {
            Log.d(TAG, "AdMob already initialized")
            onInitComplete()
            return
        }
        
        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(config.testDeviceIds)
            .build()
        
        MobileAds.setRequestConfiguration(requestConfiguration)
        
        MobileAds.initialize(context) { initializationStatus ->
            isInitialized = true
            Log.d(TAG, "AdMob SDK initialized")
            
            initializationStatus.adapterStatusMap.forEach { (adapterClass, status) ->
                Log.d(TAG, "Adapter: $adapterClass, Status: ${status.initializationState}, Latency: ${status.latency}ms")
            }
            
            // Initialize App Open Ad Manager if configured
            if (config.enableAppOpenAd && config.getEffectiveAppOpenAdUnitId().isNotEmpty()) {
                val application = context.applicationContext as? Application
                if (application != null) {
                    AppOpenAdManager.initialize(application, config)
                    Log.d(TAG, "App Open Ad Manager initialized automatically")
                } else {
                    Log.w(TAG, "Could not initialize App Open Ad Manager - context is not Application")
                }
            }
            
            onInitComplete()
        }
    }
    
    @Deprecated("Use initialize(config: AdMobConfig) instead", ReplaceWith("initialize(config, onInitComplete)"))
    fun initialize(
        testDeviceIds: List<String> = emptyList(),
        onInitComplete: () -> Unit = {}
    ) {
        // Create a default config with the provided test device IDs
        val config = AdMobConfig(
            testDeviceIds = testDeviceIds
        )
        initialize(config, onInitComplete)
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
    
    fun canShowNonPersonalizedAds(): Boolean {
        // Can show non-personalized ads even without consent in some regions
        // Check if we can at least request ads (covers cases like child-directed treatment)
        return true // You can always attempt to show non-personalized ads
    }
    
    private fun createAdRequest(): AdRequest {
        val builder = AdRequest.Builder()
        
        // If user hasn't consented, request non-personalized ads
        if (!canShowAds()) {
            val extras = Bundle()
            extras.putString("npa", "1") // Non-personalized ads
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
            Log.d(TAG, "Requesting non-personalized ads due to consent status")
        }
        
        return builder.build()
    }
    
    fun loadInterstitialAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        // Check if we can show any type of ads (personalized or non-personalized)
        if (!canShowAds() && !canShowNonPersonalizedAds()) {
            Log.w(TAG, "Cannot request any type of ads")
            return
        }
        
        // Check debug flag
        adConfig?.let { config ->
            if (!config.shouldShowInterstitials(context)) {
                Log.d(TAG, "Interstitial ads disabled in debug mode")
                return
            }
        }
        
        val adRequest = createAdRequest()
        
        InterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial ad loaded ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    setupInterstitialCallbacks(ad)
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.e(TAG, "Failed to load interstitial: ${error.message} ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        val delayMillis = getRetryDelayMillis(retryAttempt + 1)
                        Log.d(TAG, "Retrying interstitial ad load (attempt ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS) after ${delayMillis/1000} seconds")
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadInterstitialAd(adUnitId, onAdLoaded, onAdFailedToLoad, retryAttempt + 1)
                        }, delayMillis)
                    } else {
                        Log.e(TAG, "Max retry attempts reached for interstitial ad")
                        onAdFailedToLoad(error)
                    }
                }
            }
        )
    }
    
    private fun setupInterstitialCallbacks(ad: InterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed")
                interstitialAd = null
                // Auto-reload the interstitial ad
                adConfig?.let { config ->
                    val adUnitId = config.getEffectiveInterstitialAdUnitId()
                    if (adUnitId.isNotEmpty()) {
                        Log.d(TAG, "Auto-reloading interstitial ad")
                        loadInterstitialAd(adUnitId)
                    }
                }
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
        // Check debug flag
        adConfig?.let { config ->
            if (!config.shouldShowInterstitials(context)) {
                Log.d(TAG, "Interstitial ads disabled in debug mode")
                return false
            }
        }
        
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
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        // Check if we can show any type of ads (personalized or non-personalized)
        if (!canShowAds() && !canShowNonPersonalizedAds()) {
            Log.w(TAG, "Cannot request any type of ads")
            return
        }
        
        // Check debug flag
        adConfig?.let { config ->
            if (!config.shouldShowRewardedAds(context)) {
                Log.d(TAG, "Rewarded ads disabled in debug mode")
                return
            }
        }
        
        val adRequest = createAdRequest()
        
        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded ad loaded ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    setupRewardedCallbacks(ad)
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.e(TAG, "Failed to load rewarded ad: ${error.message} ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        val delayMillis = getRetryDelayMillis(retryAttempt + 1)
                        Log.d(TAG, "Retrying rewarded ad load (attempt ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS) after ${delayMillis/1000} seconds")
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadRewardedAd(adUnitId, onAdLoaded, onAdFailedToLoad, retryAttempt + 1)
                        }, delayMillis)
                    } else {
                        Log.e(TAG, "Max retry attempts reached for rewarded ad")
                        onAdFailedToLoad(error)
                    }
                }
            }
        )
    }
    
    private fun setupRewardedCallbacks(ad: RewardedAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed")
                rewardedAd = null
                // Auto-reload the rewarded ad
                adConfig?.let { config ->
                    val adUnitId = config.getEffectiveRewardedAdUnitId()
                    if (adUnitId.isNotEmpty()) {
                        Log.d(TAG, "Auto-reloading rewarded ad")
                        loadRewardedAd(adUnitId)
                    }
                }
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
        // Check debug flag
        adConfig?.let { config ->
            if (!config.shouldShowRewardedAds(context)) {
                Log.d(TAG, "Rewarded ads disabled in debug mode")
                return false
            }
        }
        
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
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        // Check if we can show any type of ads (personalized or non-personalized)
        if (!canShowAds() && !canShowNonPersonalizedAds()) {
            Log.w(TAG, "Cannot request any type of ads")
            return
        }
        
        val adRequest = createAdRequest()
        
        RewardedInterstitialAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    rewardedInterstitialAd = ad
                    Log.d(TAG, "Rewarded interstitial ad loaded ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    setupRewardedInterstitialCallbacks(ad)
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedInterstitialAd = null
                    Log.e(TAG, "Failed to load rewarded interstitial: ${error.message} ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        val delayMillis = getRetryDelayMillis(retryAttempt + 1)
                        Log.d(TAG, "Retrying rewarded interstitial ad load (attempt ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS) after ${delayMillis/1000} seconds")
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadRewardedInterstitialAd(adUnitId, onAdLoaded, onAdFailedToLoad, retryAttempt + 1)
                        }, delayMillis)
                    } else {
                        Log.e(TAG, "Max retry attempts reached for rewarded interstitial ad")
                        onAdFailedToLoad(error)
                    }
                }
            }
        )
    }
    
    private fun setupRewardedInterstitialCallbacks(ad: RewardedInterstitialAd) {
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded interstitial dismissed")
                rewardedInterstitialAd = null
                // Auto-reload the rewarded interstitial ad
                adConfig?.let { config ->
                    val adUnitId = config.getEffectiveRewardedInterstitialAdUnitId()
                    if (adUnitId.isNotEmpty()) {
                        Log.d(TAG, "Auto-reloading rewarded interstitial ad")
                        loadRewardedInterstitialAd(adUnitId)
                    }
                }
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
    
    fun getConfig(): AdMobConfig? = adConfig
    
    fun updateConfig(config: AdMobConfig) {
        this.adConfig = config
        
        // Update App Open Ad Manager if needed
        if (config.enableAppOpenAd) {
            val application = context.applicationContext as? Application
            if (application != null && AppOpenAdManager.getInstance() == null) {
                AppOpenAdManager.initialize(application, config)
                Log.d(TAG, "App Open Ad Manager initialized after config update")
            }
        }
    }
}