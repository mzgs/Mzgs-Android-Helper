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
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import java.lang.ref.WeakReference

object AdMobMediationManager : Application.ActivityLifecycleCallbacks {
    
    private const val TAG = "AdMobMediation"
    private var isInitialized = false
    private const val MAX_RETRY_ATTEMPTS = 6 // Allow up to 6 retries for exponential backoff
    
    private var contextRef: WeakReference<Context>? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var adConfig: AdMobConfig? = null
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var consentInformation: ConsentInformation? = null
    
    @JvmStatic
    fun init(context: Context, config: AdMobConfig, onInitComplete: () -> Unit = {}) {
        this.contextRef = WeakReference(context.applicationContext)
        this.adConfig = config
        this.consentInformation = UserMessagingPlatform.getConsentInformation(context)
        
        // If context is an activity, store it as current activity
        if (context is Activity) {
            this.currentActivityRef = WeakReference(context)
        }
        
        // Register activity lifecycle callbacks to track current activity
        val application = context.applicationContext as? Application
        application?.registerActivityLifecycleCallbacks(this)
        
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
            
            initializationStatus.adapterStatusMap.forEach { (className, status) ->
                Log.d(TAG, "Adapter: $className, Status: ${status.initializationState}, Latency: ${status.latency}ms")
            }
            
            // Initialize App Open Ad if configured
            if (config.enableAppOpenAd) {
                val application = context.applicationContext as? Application
                if (application != null) {
                    AppOpenAdManager.initialize(application, config)
                    Log.d(TAG, "App Open Ad Manager initialized")
                }
            }
            
            onInitComplete()
        }
    }
    
    // Backward compatibility - getInstance returns this object
    @JvmStatic
    fun getInstance(context: Context): AdMobMediationManager {
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
    fun setCurrentActivity(activity: Activity?) {
        activity?.let {
            currentActivityRef = WeakReference(it)
        }
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
        
        val activity = currentActivityRef?.get() ?: (ctx as? Activity)
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
                onConsentInfoUpdateFailure(error.message)
            }
        )
    }
    
    @JvmStatic
    fun showConsentForm(
        activity: Activity,
        onConsentFormDismissed: (FormError?) -> Unit = {}
    ) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            if (formError != null) {
                Log.e(TAG, "Error showing consent form: ${formError.message}")
            } else {
                Log.d(TAG, "Consent form shown and handled")
            }
            onConsentFormDismissed(formError)
        }
    }
    
    @JvmStatic
    fun canRequestAds(): Boolean {
        // Check if consent information is available
        val canRequest = consentInformation?.canRequestAds()
        
        // The UMP SDK's canRequestAds() returns false until consent info is updated
        // So if it's false and consent status is UNKNOWN, we should still allow non-personalized ads
        val consentStatus = getConsentStatus()
        val allowAds = when {
            canRequest == true -> true
            canRequest == false && consentStatus == ConsentInformation.ConsentStatus.UNKNOWN -> {
                // Consent not yet determined, allow non-personalized ads
                Log.d(TAG, "Consent unknown, allowing non-personalized ads")
                true
            }
            canRequest == null -> true // No consent info available, allow non-personalized
            else -> false
        }
        
        Log.d(TAG, "canRequestAds check - consentInfo: ${consentInformation != null}, canRequest: $canRequest, status: $consentStatus, allowing: $allowAds")
        
        return allowAds
    }
    
    @JvmStatic
    fun getConsentStatus(): Int {
        return consentInformation?.consentStatus ?: ConsentInformation.ConsentStatus.UNKNOWN
    }
    
    @JvmStatic
    fun isConsentFormAvailable(): Boolean {
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
    fun canShowAds(): Boolean {
        val isEEA = isUserInEEA()
        val consentStatus = getConsentStatus()
        val consentObtained = consentStatus == ConsentInformation.ConsentStatus.OBTAINED
        val canRequestAds = canRequestAds()
        
        Log.d(TAG, "canShowAds check - isEEA: $isEEA, consentStatus: $consentStatus, consentObtained: $consentObtained, canRequestAds: $canRequestAds")
        
        return if (isEEA) {
            consentObtained && canRequestAds
        } else {
            canRequestAds
        }
    }
    
    @JvmStatic
    fun canShowNonPersonalizedAds(): Boolean {
        val isEEA = isUserInEEA()
        val consentStatus = getConsentStatus()
        
        // For non-personalized ads, we can show them even without explicit consent
        // We just need to pass the npa flag
        val canShow = when (consentStatus) {
            ConsentInformation.ConsentStatus.UNKNOWN -> true // Allow non-personalized when unknown
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> true
            ConsentInformation.ConsentStatus.OBTAINED -> true
            ConsentInformation.ConsentStatus.REQUIRED -> true // Allow non-personalized even when consent required
            else -> true
        }
        
        Log.d(TAG, "canShowNonPersonalizedAds check - isEEA: $isEEA, consentStatus: $consentStatus, canShow: $canShow")
        
        return canShow
    }
    
    @JvmStatic
    fun isUserInEEA(): Boolean {
        return false // You can implement actual EEA detection logic here
    }
    
    @JvmStatic
    fun createAdRequest(): AdRequest {
        val builder = AdRequest.Builder()
        
        val isEEA = isUserInEEA()
        val consentObtained = getConsentStatus() == ConsentInformation.ConsentStatus.OBTAINED
        
        if (isEEA && !consentObtained) {
            val extras = Bundle()
            extras.putString("npa", "1")
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }
        
        return builder.build()
    }
    
    @JvmStatic
    fun loadInterstitialAd(
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {}
    ) {
        val effectiveAdUnitId = adConfig?.getEffectiveInterstitialAdUnitId() ?: ""
        if (effectiveAdUnitId.isEmpty()) {
            Log.e(TAG, "No interstitial ad unit ID configured")
            return
        }
        loadInterstitialAd(effectiveAdUnitId, onAdLoaded, onAdFailedToLoad)
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
        
        // Check if we can show any type of ads (personalized or non-personalized)
        if (!canShowAds() && !canShowNonPersonalizedAds()) {
            Log.w(TAG, "Cannot request any type of ads")
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
    
    @JvmStatic
    fun showInterstitialAd(): Boolean {
        val activity = currentActivityRef?.get()
        if (activity == null) {
            Log.e(TAG, "No current activity available to show interstitial ad")
            return false
        }
        return showInterstitialAd(activity)
    }
    
    @JvmStatic
    fun showInterstitialAd(activity: Activity): Boolean {
        // Check debug flag
        adConfig?.let { config ->
            contextRef?.get()?.let { ctx ->
                if (!config.shouldShowInterstitials(ctx)) {
                    Log.d(TAG, "Interstitial ads disabled in debug mode")
                    return false
                }
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
    
    @JvmStatic
    fun loadRewardedAd(
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {}
    ) {
        val effectiveAdUnitId = adConfig?.getEffectiveRewardedAdUnitId() ?: ""
        if (effectiveAdUnitId.isEmpty()) {
            Log.e(TAG, "No rewarded ad unit ID configured")
            return
        }
        loadRewardedAd(effectiveAdUnitId, onAdLoaded, onAdFailedToLoad)
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
        
        // Check if we can show any type of ads (personalized or non-personalized)
        if (!canShowAds() && !canShowNonPersonalizedAds()) {
            Log.w(TAG, "Cannot request any type of ads")
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
    
    @JvmStatic
    fun showRewardedAd(
        onUserEarnedReward: (RewardItem) -> Unit = {}
    ): Boolean {
        val activity = currentActivityRef?.get()
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
        val effectiveAdUnitId = adConfig?.getEffectiveRewardedInterstitialAdUnitId() ?: ""
        if (effectiveAdUnitId.isEmpty()) {
            Log.e(TAG, "No rewarded interstitial ad unit ID configured")
            return
        }
        loadRewardedInterstitialAd(effectiveAdUnitId, onAdLoaded, onAdFailedToLoad)
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
        
        // Check if we can show any type of ads (personalized or non-personalized)
        if (!canShowAds() && !canShowNonPersonalizedAds()) {
            Log.w(TAG, "Cannot request any type of ads")
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
    
    @JvmStatic
    fun showRewardedInterstitialAd(
        onUserEarnedReward: (RewardItem) -> Unit = {}
    ): Boolean {
        val activity = currentActivityRef?.get()
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
        this.adConfig = config
        
        // Update App Open Ad Manager if needed
        if (config.enableAppOpenAd) {
            contextRef?.get()?.let { ctx ->
                val application = ctx.applicationContext as? Application
                if (application != null && AppOpenAdManager.getInstance() == null) {
                    AppOpenAdManager.initialize(application, config)
                    Log.d(TAG, "App Open Ad Manager initialized after config update")
                }
            }
        }
    }
    
    // Calculate exponential backoff delay
    private fun getRetryDelayMillis(retryAttempt: Int): Long {
        // Calculate delay: 2^retryAttempt seconds, capped at 2^6 (64 seconds)
        val exponent = min(6, retryAttempt)
        return TimeUnit.SECONDS.toMillis(2.0.pow(exponent).toLong())
    }
    
    // Activity Lifecycle Callbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    
    override fun onActivityStarted(activity: Activity) {}
    
    override fun onActivityResumed(activity: Activity) {
        currentActivityRef = WeakReference(activity)
    }
    
    override fun onActivityPaused(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef = null
        }
    }
    
    override fun onActivityStopped(activity: Activity) {}
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivityRef?.get() == activity) {
            currentActivityRef = null
        }
    }
}