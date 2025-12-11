package com.mzgs.helper.admob

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import com.google.android.gms.ads.*
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
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.mzgs.helper.MzgsHelper
import com.mzgs.helper.p
import com.mzgs.helper.Ads
import java.lang.ref.WeakReference

object AdMobManager {
    
    private const val TAG = "AdMob"
    private var isInitialized = false
    private const val MAX_RETRY_ATTEMPTS = 6 // Allow up to 6 retries for exponential backoff
    
    private var contextRef: WeakReference<Context>? = null
    private var adConfig: AdMobConfig? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var consentInformation: ConsentInformation? = null
    private var handler: Handler? = null
    
    @JvmStatic
    fun init(context: Context, config: AdMobConfig, onInitComplete: () -> Unit = {}) {
        this.contextRef = WeakReference(context.applicationContext)
        
        // Check if ads are disabled in debug mode
        if (MzgsHelper.debugNoAds) {
            Log.d(TAG, "AdMob initialization skipped (debugNoAds mode)")
            isInitialized = false
            onInitComplete()
            return
        }
        
        // Override config with test IDs or empty IDs based on debug flags
        this.adConfig = when {
            MzgsHelper.isDebug() && config.debugEmptyIds -> {
                // Use empty IDs when debugEmptyIds is true
                config.copy(
                    bannerAdUnitId = "",
                    interstitialAdUnitId = "",
                    rewardedAdUnitId = "",
                    rewardedInterstitialAdUnitId = "",
                    nativeAdUnitId = "",
                    mrecAdUnitId = "",
                    appOpenAdUnitId = ""
                )
            }
            MzgsHelper.isDebug() && config.enableTestMode -> {
                // Use test IDs when test mode is enabled
                config.copy(
                    bannerAdUnitId = AdMobConfig.TEST_BANNER_AD_UNIT_ID,
                    interstitialAdUnitId = AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID,
                    rewardedAdUnitId = AdMobConfig.TEST_REWARDED_AD_UNIT_ID,
                    rewardedInterstitialAdUnitId = AdMobConfig.TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
                    nativeAdUnitId = AdMobConfig.TEST_NATIVE_AD_UNIT_ID,
                    mrecAdUnitId = AdMobConfig.TEST_MREC_AD_UNIT_ID,
                    appOpenAdUnitId = AdMobConfig.TEST_APP_OPEN_AD_UNIT_ID
                )
            }
            else -> config
        }
        
        this.consentInformation = UserMessagingPlatform.getConsentInformation(context)
        
        // Activity tracking is handled by Ads class
        
        // Activity tracking is handled by Ads class
        
        if (isInitialized) {
            Log.d(TAG, "AdMob already initialized")
            onInitComplete()
            return
        }
        
        // SAFETY: Only set test device IDs in debug builds
        val testDeviceIds = if (MzgsHelper.isDebug()) {
            config.testDeviceIds
        } else {
            emptyList() // Never use test device IDs in release
        }
        
        val requestConfiguration = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        
        MobileAds.setRequestConfiguration(requestConfiguration)
        

        
        MobileAds.initialize(context) { initializationStatus ->
            isInitialized = true
            
            // Log initialization status with test device info
            Log.d(TAG, "")
            Log.d(TAG, "|||------------------------------------------------------------|||")
            Log.d(TAG, "|||                AdMob SDK INITIALIZED                       |||")
            Log.d(TAG, "|||------------------------------------------------------------|||")
            Log.d(TAG, "|||                                                            |||")
            if (testDeviceIds.isNotEmpty()) {
                Log.d(TAG, "|||  TEST MODE: ENABLED                                        |||")
                Log.d(TAG, "|||  Test Device IDs (Hashed):                                |||")
                testDeviceIds.forEach { id ->
                    val displayId = if (id.length > 40) "${id.substring(0, 40)}..." else id
                    Log.d(TAG, "|||  - $displayId")
                }
            } else {
                Log.d(TAG, "|||  TEST MODE: DISABLED (No test device IDs)                 |||")
                Log.d(TAG, "|||                                                            |||")
                Log.d(TAG, "|||  Look for this in logcat to get your test device ID:      |||")
                Log.d(TAG, "|||  'Use new ConsentDebugSettings.Builder().addTestDeviceHashedId(\"YOUR_ID\")'|||")
            }
            Log.d(TAG, "|||                                                            |||")
            Log.d(TAG, "|||  Initialized:                                              |||")
            initializationStatus.adapterStatusMap.forEach { (className, status) ->
                val shortName = className.substringAfterLast(".")
                Log.d(TAG, "|||  - $shortName: ${status.initializationState}")
            }
            Log.d(TAG, "|||                                                            |||")
            Log.d(TAG, "|||------------------------------------------------------------|||")
            Log.d(TAG, "")
            
            // Initialize App Open Ad if configured
            adConfig?.let { config ->
                if (config.enableAppOpenAd) {
                    val application = context.applicationContext as? Application
                    if (application != null) {
                        AppOpenAdManager.initialize(application, config)
                        Log.d(TAG, "App Open Ad Manager initialized")
                    }
                }
            }
            
            onInitComplete()
        }
    }
    
    // Backward compatibility - getInstance returns this object
    @JvmStatic
    fun getInstance(context: Context): AdMobManager {
        if (this.contextRef?.get() == null) {
            this.contextRef = WeakReference(context.applicationContext)
        }
        return this
    }
    
    @JvmStatic
    fun initialize(config: AdMobConfig, onInitComplete: () -> Unit = {}) {
        contextRef?.get()?.let {
            init(it, config, onInitComplete)
        } ?: Log.e(TAG, "Context not set. Call init() first")
    }
    
    @JvmStatic
    @Deprecated("Use Ads.getCurrentActivity() instead. Activity tracking is centralized in Ads class.")
    fun setCurrentActivity(activity: Activity?) {
        // This method is kept for backward compatibility but does nothing
        // Activity tracking is now centralized in the Ads class
        Log.d(TAG, "setCurrentActivity called - activity tracking is now handled by Ads class")
    }
    
    @JvmStatic
    fun requestConsentInfoUpdate(
        underAgeOfConsent: Boolean = false,
        debugGeography: Int? = null,  // Use Int for DebugGeography constants
        testDeviceHashedId: String? = null,
        onConsentInfoUpdateSuccess: () -> Unit = {},
        onConsentInfoUpdateFailure: (String) -> Unit = {}
    ) {
        val ctx = contextRef?.get() ?: run {
            Log.e(TAG, "Context not set. Call init() first")
            return
        }
        
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(underAgeOfConsent)
        
        if (debugGeography != null && testDeviceHashedId != null) {
            val debugSettings = ConsentDebugSettings.Builder(ctx)
                .setDebugGeography(debugGeography)
                .addTestDeviceHashedId(testDeviceHashedId)
                .build()
            params.setConsentDebugSettings(debugSettings)
        }
        
        val activity = Ads.getCurrentActivity() ?: (ctx as? Activity)
        if (activity == null) {
            Log.e(TAG, "No activity available for consent info update")
            onConsentInfoUpdateFailure("No activity available")
            return
        }
        
        consentInformation?.requestConsentInfoUpdate(
            activity,
            params.build(),
            {
                Log.d(TAG, "Consent info updated successfully")
                onConsentInfoUpdateSuccess()
            },
            { error ->
                Log.e(TAG, "Failed to update consent info: ${error.message}")
                // Check if it's a network error and provide better messaging
                val errorMessage = when {
                    error.message.contains("Error making request", ignoreCase = true) ->
                        "Network error. Please check internet connection."
                    error.message.contains("timeout", ignoreCase = true) ->
                        "Request timed out. Please try again."
                    else -> error.message
                }
                onConsentInfoUpdateFailure(errorMessage)
            }
        )
    }
    
    @JvmStatic
    fun showConsentForm(
        activity: Activity,
        onConsentFormDismissed: (FormError?) -> Unit = {}
    ) {
        // SAFETY CHECK: Only allow debug flags in debug builds
        val shouldForceShow = adConfig?.let { config ->
            contextRef?.get()?.let { context ->
                // CRITICAL: Must be debug mode AND flag enabled
                MzgsHelper.isDebug() && config.debugRequireConsentAlways
            }
        } ?: false
        
        if (shouldForceShow) {
            // Force load and show consent form regardless of requirements
            UserMessagingPlatform.loadConsentForm(
                activity,
                { consentForm ->
                    consentForm.show(activity) { formError ->
                        if (formError != null) {
                            Log.e(TAG, "Error showing forced consent form: ${formError.message}")
                        } else {
                            Log.d(TAG, "Forced consent form shown and handled")
                        }
                        onConsentFormDismissed(formError)
                    }
                },
                { formError ->
                    Log.e(TAG, "Error loading consent form: ${formError.message}")
                    onConsentFormDismissed(formError)
                }
            )
        } else {
            // Normal flow - only show if required
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                if (formError != null) {
                    Log.e(TAG, "Error showing consent form: ${formError.message}")
                } else {
                    Log.d(TAG, "Consent form shown and handled")
                }
                onConsentFormDismissed(formError)
            }
        }
    }
    
    
    @JvmStatic
    fun getConsentStatus(): Int {
        return consentInformation?.consentStatus ?: ConsentInformation.ConsentStatus.UNKNOWN
    }
    
    @JvmStatic
    fun isConsentFormAvailable(): Boolean {
        // SAFETY CHECK: Only allow debug override in debug builds
        adConfig?.let { config ->
            contextRef?.get()?.let { context ->
                // CRITICAL: Must be debug mode AND flag enabled - NEVER affects release
                if (MzgsHelper.isDebug() && config.debugRequireConsentAlways) {
                    Log.d(TAG, "DEBUG ONLY: Forcing consent form availability for testing")
                    return true
                }
            }
        }
        // Normal production flow
        return consentInformation?.isConsentFormAvailable ?: false
    }
    
    @JvmStatic
    fun getPrivacyOptionsRequirementStatus(): ConsentInformation.PrivacyOptionsRequirementStatus {
        return consentInformation?.privacyOptionsRequirementStatus 
            ?: ConsentInformation.PrivacyOptionsRequirementStatus.UNKNOWN
    }
    
    @JvmStatic
    fun showPrivacyOptionsForm(
        activity: Activity,
        onConsentFormDismissed: (FormError?) -> Unit = {}
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.e(TAG, "Error showing privacy options form: ${formError.message}")
            }
            onConsentFormDismissed(formError)
        }
    }
    
    
    @JvmStatic
    fun isUserInEEA(): Boolean {
        return false // You can implement actual EEA detection logic here
    }
    
    @JvmStatic
    fun createAdRequest(): AdRequest {
        // UMP SDK automatically handles personalized/non-personalized ads
        return AdRequest.Builder().build()
    }
    
    @JvmStatic
    fun loadInterstitialAd(
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {}
    ) {
        val adUnitId = adConfig?.interstitialAdUnitId ?: ""
        if (adUnitId.isEmpty()) {
            Log.e(TAG, "No interstitial ad unit ID configured")
            return
        }
        loadInterstitialAd(adUnitId, onAdLoaded, onAdFailedToLoad)
    }
    
    @JvmStatic
    fun loadInterstitialAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        val ctx = contextRef?.get() ?: run {
            Log.e(TAG, "Context not set. Call init() first")
            return
        }
        
        // Check debug flag
        adConfig?.let { config ->
            if (!config.shouldShowInterstitials(ctx)) {
                Log.d(TAG, "Interstitial ads disabled in debug mode")
                return
            }
        }
        
        val adRequest = createAdRequest()
        
        InterstitialAd.load(
            ctx,
            adUnitId,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Interstitial ad loaded ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    setupInterstitialCallbacks(ad)
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "interstitial",
                        adUnitId = adUnitId,
                        adNetwork = "admob",
                        success = true,
                        retryAttempt = retryAttempt
                    )
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.e(TAG, "Failed to load interstitial: ${error.message} ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    
                    // Log first failure immediately
                    if (retryAttempt == 0) {
                        FirebaseAnalyticsManager.logAdLoad(
                            adType = "interstitial",
                            adUnitId = adUnitId,
                            adNetwork = "admob",
                            success = false,
                            errorMessage = error.message,
                            errorCode = error.code,
                            retryAttempt = retryAttempt
                        )
                    }
                    
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        val delayMillis = getRetryDelayMillis(retryAttempt + 1)
                        Log.d(TAG, "Retrying interstitial ad load (attempt ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS) after ${delayMillis/1000} seconds")
                        if (handler == null) {
                            handler = Handler(Looper.getMainLooper())
                        }
                        handler?.postDelayed({
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
                    val adUnitId = config.interstitialAdUnitId
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
    
    @JvmStatic
    fun showInterstitialAd(onAdDismissed: (() -> Unit)? = null): Boolean {
        val activity = Ads.getCurrentActivity()
        if (activity == null) {
            Log.e(TAG, "No current activity available to show interstitial ad")
            onAdDismissed?.invoke()
            return false
        }
        return showInterstitialAd(activity, onAdDismissed)
    }
    
    @JvmStatic
    fun showInterstitialAd(activity: Activity, onAdDismissed: (() -> Unit)? = null): Boolean {
        // Check debug flag
        adConfig?.let { config ->
            contextRef?.get()?.let { ctx ->
                if (!config.shouldShowInterstitials(ctx)) {
                    Log.d(TAG, "Interstitial ads disabled in debug mode")
                    onAdDismissed?.invoke()
                    return false
                }
            }
        }
        
        return if (interstitialAd != null) {
            // Set up callback for ad dismissal if provided
            onAdDismissed?.let { callback ->
                interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        Log.d(TAG, "Interstitial ad dismissed")
                        callback.invoke()
                        interstitialAd = null
                        // Auto-reload the interstitial ad
                        adConfig?.let { config ->
                            val adUnitId = config.interstitialAdUnitId
                            if (adUnitId.isNotEmpty()) {
                                Log.d(TAG, "Auto-reloading interstitial ad")
                                loadInterstitialAd(adUnitId)
                            }
                        }
                    }
                    
                    override fun onAdFailedToShowFullScreenContent(error: AdError) {
                        Log.e(TAG, "Failed to show interstitial: ${error.message}")
                        callback.invoke()
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
            interstitialAd?.show(activity)
            true
        } else {
            Log.w(TAG, "Interstitial ad not ready")
            onAdDismissed?.invoke()
            false
        }
    }
    
    @JvmStatic
    fun loadRewardedAd(
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {}
    ) {
        val adUnitId = adConfig?.rewardedAdUnitId ?: ""
        if (adUnitId.isEmpty()) {
            Log.e(TAG, "No rewarded ad unit ID configured")
            return
        }
        loadRewardedAd(adUnitId, onAdLoaded, onAdFailedToLoad)
    }
    
    @JvmStatic
    fun loadRewardedAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        val ctx = contextRef?.get() ?: run {
            Log.e(TAG, "Context not set. Call init() first")
            return
        }
        
        // Check debug flag
        adConfig?.let { config ->
            if (!config.shouldShowRewardedAds(ctx)) {
                Log.d(TAG, "Rewarded ads disabled in debug mode")
                return
            }
        }
        
        val adRequest = createAdRequest()
        
        RewardedAd.load(
            ctx,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "Rewarded ad loaded ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    setupRewardedCallbacks(ad)
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "rewarded",
                        adUnitId = adUnitId,
                        adNetwork = "admob",
                        success = true,
                        retryAttempt = retryAttempt
                    )
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.e(TAG, "Failed to load rewarded ad: ${error.message} ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    
                    // Log first failure immediately
                    if (retryAttempt == 0) {
                        FirebaseAnalyticsManager.logAdLoad(
                            adType = "rewarded",
                            adUnitId = adUnitId,
                            adNetwork = "admob",
                            success = false,
                            errorMessage = error.message,
                            errorCode = error.code,
                            retryAttempt = retryAttempt
                        )
                    }
                    
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        val delayMillis = getRetryDelayMillis(retryAttempt + 1)
                        Log.d(TAG, "Retrying rewarded ad load (attempt ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS) after ${delayMillis/1000} seconds")
                        if (handler == null) {
                            handler = Handler(Looper.getMainLooper())
                        }
                        handler?.postDelayed({
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
                    val adUnitId = config.rewardedAdUnitId
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
    
    @JvmStatic
    fun showRewardedAd(
        onUserEarnedReward: (RewardItem) -> Unit = {}
    ): Boolean {
        val activity = Ads.getCurrentActivity()
        if (activity == null) {
            Log.e(TAG, "No current activity available to show rewarded ad")
            return false
        }
        return showRewardedAd(activity, onUserEarnedReward)
    }
    
    @JvmStatic
    fun showRewardedAd(
        activity: Activity,
        onUserEarnedReward: (RewardItem) -> Unit = {}
    ): Boolean {
        // Check debug flag
        adConfig?.let { config ->
            contextRef?.get()?.let { ctx ->
                if (!config.shouldShowRewardedAds(ctx)) {
                    Log.d(TAG, "Rewarded ads disabled in debug mode")
                    return false
                }
            }
        }
        
        return if (rewardedAd != null) {
            rewardedAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onUserEarnedReward(rewardItem)
            }
            true
        } else {
            Log.w(TAG, "Rewarded ad not ready")
            false
        }
    }
    
    @JvmStatic
    fun loadRewardedInterstitialAd(
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {}
    ) {
        val adUnitId = adConfig?.rewardedInterstitialAdUnitId ?: ""
        if (adUnitId.isEmpty()) {
            Log.e(TAG, "No rewarded interstitial ad unit ID configured")
            return
        }
        loadRewardedInterstitialAd(adUnitId, onAdLoaded, onAdFailedToLoad)
    }
    
    @JvmStatic
    fun loadRewardedInterstitialAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        val ctx = contextRef?.get() ?: run {
            Log.e(TAG, "Context not set. Call init() first")
            return
        }
        
        // Check debug flag
        adConfig?.let { config ->
            if (!config.shouldShowRewardedAds(ctx)) {
                Log.d(TAG, "Rewarded interstitial ads disabled in debug mode")
                return
            }
        }
        
        val adRequest = createAdRequest()
        
        RewardedInterstitialAd.load(
            ctx,
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
                        if (handler == null) {
                            handler = Handler(Looper.getMainLooper())
                        }
                        handler?.postDelayed({
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
                    val adUnitId = config.rewardedInterstitialAdUnitId
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
    
    @JvmStatic
    fun showRewardedInterstitialAd(
        onUserEarnedReward: (RewardItem) -> Unit = {}
    ): Boolean {
        val activity = Ads.getCurrentActivity()
        if (activity == null) {
            Log.e(TAG, "No current activity available to show rewarded interstitial ad")
            return false
        }
        return showRewardedInterstitialAd(activity, onUserEarnedReward)
    }
    
    @JvmStatic
    fun showRewardedInterstitialAd(
        activity: Activity,
        onUserEarnedReward: (RewardItem) -> Unit = {}
    ): Boolean {
        // Check debug flag
        adConfig?.let { config ->
            contextRef?.get()?.let { ctx ->
                if (!config.shouldShowRewardedAds(ctx)) {
                    Log.d(TAG, "Rewarded interstitial ads disabled in debug mode")
                    return false
                }
            }
        }
        
        return if (rewardedInterstitialAd != null) {
            rewardedInterstitialAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward from interstitial: ${rewardItem.amount} ${rewardItem.type}")
                onUserEarnedReward(rewardItem)
            }
            true
        } else {
            Log.w(TAG, "Rewarded interstitial ad not ready")
            false
        }
    }
    
    @JvmStatic
    fun isInterstitialReady(): Boolean = interstitialAd != null
    
    @JvmStatic
    fun isRewardedReady(): Boolean = rewardedAd != null
    
    @JvmStatic
    fun isRewardedInterstitialReady(): Boolean = rewardedInterstitialAd != null
    
    @JvmStatic
    fun resetConsent() {
        consentInformation?.reset()
        Log.d(TAG, "Consent information reset")
    }
    
    @JvmStatic
    fun getConfig(): AdMobConfig? = adConfig
    
    @JvmStatic
    fun updateConfig(config: AdMobConfig) {
        contextRef?.get()?.let { context ->
            // Override config with test IDs or empty IDs based on debug flags
            this.adConfig = when {
                MzgsHelper.isDebug() && config.debugEmptyIds -> {
                    // Use empty IDs when debugEmptyIds is true
                    config.copy(
                        bannerAdUnitId = "",
                        interstitialAdUnitId = "",
                        rewardedAdUnitId = "",
                        rewardedInterstitialAdUnitId = "",
                        nativeAdUnitId = "",
                        mrecAdUnitId = "",
                        appOpenAdUnitId = ""
                    )
                }
                MzgsHelper.isDebug() && config.enableTestMode -> {
                    // Use test IDs when test mode is enabled
                    config.copy(
                        bannerAdUnitId = AdMobConfig.TEST_BANNER_AD_UNIT_ID,
                        interstitialAdUnitId = AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID,
                        rewardedAdUnitId = AdMobConfig.TEST_REWARDED_AD_UNIT_ID,
                        rewardedInterstitialAdUnitId = AdMobConfig.TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
                        nativeAdUnitId = AdMobConfig.TEST_NATIVE_AD_UNIT_ID,
                        mrecAdUnitId = AdMobConfig.TEST_MREC_AD_UNIT_ID,
                        appOpenAdUnitId = AdMobConfig.TEST_APP_OPEN_AD_UNIT_ID
                    )
                }
                else -> config
            }
            
            // Update App Open Ad Manager if needed
            adConfig?.let { updatedConfig ->
                if (updatedConfig.enableAppOpenAd) {
                    val application = context.applicationContext as? Application
                    if (application != null && AppOpenAdManager.getInstance() == null) {
                        AppOpenAdManager.initialize(application, updatedConfig)
                        Log.d(TAG, "App Open Ad Manager initialized after config update")
                    }
                }
            }
        } ?: run {
            // If context is not available, just update the config without test mode override
            this.adConfig = config
        }
    }
    
    // Calculate exponential backoff delay
    private fun getRetryDelayMillis(retryAttempt: Int): Long {
        // Start at 4 seconds for first retry, then exponential backoff
        // Retry 1: 4s, Retry 2: 8s, Retry 3: 16s, Retry 4: 32s, Retry 5: 64s, Retry 6: 64s (capped)
        val baseDelaySeconds = when (retryAttempt) {
            1 -> 4L  // First retry: 4 seconds
            2 -> 8L  // Second retry: 8 seconds
            3 -> 16L // Third retry: 16 seconds
            4 -> 32L // Fourth retry: 32 seconds
            else -> 64L // Fifth and sixth retry: 64 seconds (capped)
        }
        return TimeUnit.SECONDS.toMillis(baseDelaySeconds)
    }
    
    // Note: Activity lifecycle tracking is handled by the Ads class
    // AdMobManager gets activity reference from Ads when needed
    
    @JvmStatic
    fun cleanup() {
        Log.d(TAG, "Cleaning up AdMob resources")
        
        // Cancel and null all ad references
        interstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
        
        rewardedAd?.fullScreenContentCallback = null
        rewardedAd = null
        
        rewardedInterstitialAd?.fullScreenContentCallback = null
        rewardedInterstitialAd = null
        
        // Clear handler and runnable references
        handler?.removeCallbacksAndMessages(null)
        handler = null
        
        // Clear context reference
        contextRef?.clear()
        contextRef = null
        
        Log.d(TAG, "AdMob resources cleaned up")
    }
    
    @JvmStatic
    fun onActivityDestroyed(activity: Activity) {
        // Clean up any ad-related resources when activity is destroyed
        if (contextRef?.get() == activity) {
            cleanup()
        }
    }
}