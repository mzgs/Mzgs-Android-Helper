package com.mzgs.helper.applovin

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.applovin.mediation.*
import com.applovin.mediation.ads.MaxAppOpenAd
import com.applovin.mediation.ads.MaxInterstitialAd
import com.applovin.mediation.ads.MaxRewardedAd
import com.applovin.sdk.AppLovinMediationProvider
import com.applovin.sdk.AppLovinPrivacySettings
import com.applovin.sdk.AppLovinSdk
import com.applovin.sdk.AppLovinSdkConfiguration
import com.applovin.sdk.AppLovinSdkInitializationConfiguration
import com.applovin.sdk.AppLovinSdkSettings
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

object AppLovinMediationManager : Application.ActivityLifecycleCallbacks {
    
    private const val TAG = "AppLovinMediation"
    private var isInitialized = false
    private const val MAX_RETRY_ATTEMPTS = 6
    
    private var contextRef: WeakReference<Context>? = null
    private var currentActivityRef: WeakReference<Activity>? = null
    private var appLovinConfig: AppLovinConfig? = null
    private var maxSdk: AppLovinSdk? = null
    private var interstitialAd: MaxInterstitialAd? = null
    private var rewardedAd: MaxRewardedAd? = null
    private var appOpenAd: MaxAppOpenAd? = null
    
    @JvmStatic
    fun init(context: Context, config: AppLovinConfig, onInitComplete: () -> Unit = {}) {
        this.contextRef = WeakReference(context.applicationContext)
        this.appLovinConfig = config
        
        // If context is an activity, store it as current activity
        if (context is Activity) {
            this.currentActivityRef = WeakReference(context)
        }
        
        // Register activity lifecycle callbacks to track current activity
        val application = context.applicationContext as? Application
        application?.registerActivityLifecycleCallbacks(this)
        
        if (isInitialized) {
            Log.d(TAG, "AppLovin MAX already initialized")
            onInitComplete()
            return
        }
        
        // Create initialization configuration using the builder pattern
        val initConfig = AppLovinSdkInitializationConfiguration.builder(config.sdkKey, context)
            .setMediationProvider(AppLovinMediationProvider.MAX)
            .setTestDeviceAdvertisingIds(config.testDeviceAdvertisingIds)
            .build()
        
        // Initialize SDK with configuration
        AppLovinSdk.getInstance(context).initialize(initConfig) { sdkConfig ->
            maxSdk = AppLovinSdk.getInstance(context)
            
            // Settings are configured during initialization through the builder
            Log.d(TAG, "Verbose Logging: ${config.verboseLogging}")
            Log.d(TAG, "Mute Audio: ${config.muteAudio}")
            Log.d(TAG, "Creative Debugger: ${config.creativeDebuggerEnabled}")
            
            isInitialized = true
            Log.d(TAG, "AppLovin MAX SDK initialized")
            // Note: isTestModeEnabled is not available in current SDK version
            Log.d(TAG, "Country Code: ${sdkConfig.countryCode}")
            Log.d(TAG, "Consent Dialog State: ${sdkConfig.consentDialogState}")
            
            // Initialize App Open Ad if configured
            if (config.enableAppOpenAd) {
                initializeAppOpenAd(config.appOpenAdUnitId)
            }
            
            onInitComplete()
        }
    }
    
    // Backward compatibility - getInstance returns this object
    @JvmStatic
    fun getInstance(context: Context): AppLovinMediationManager {
        if (this.contextRef?.get() == null) {
            this.contextRef = WeakReference(context.applicationContext)
        }
        return this
    }
    
    @JvmStatic
    fun initialize(config: AppLovinConfig, onInitComplete: () -> Unit = {}) {
        contextRef?.get()?.let {
            init(it, config, onInitComplete)
        } ?: Log.e(TAG, "Context not set. Call init() first")
    }
    
    @JvmStatic
    fun setPrivacySettings(
        hasUserConsent: Boolean? = null,
        isAgeRestrictedUser: Boolean? = null,
        doNotSell: Boolean? = null
    ) {
        hasUserConsent?.let {
            AppLovinPrivacySettings.setHasUserConsent(it, contextRef?.get())
            Log.d(TAG, "Set user consent: $it")
        }
        
        isAgeRestrictedUser?.let {
            // Note: Age restricted user setting is handled differently in newer SDK versions
            Log.d(TAG, "Age restricted user setting: $it")
        }
        
        doNotSell?.let {
            AppLovinPrivacySettings.setDoNotSell(it, contextRef?.get())
            Log.d(TAG, "Set do not sell: $it")
        }
    }
    
    @JvmStatic
    fun showMediationDebugger() {
        maxSdk?.showMediationDebugger()
    }
    
    @JvmStatic
    fun showCreativeDebugger() {
        maxSdk?.showCreativeDebugger()
    }
    
    @JvmStatic
    fun getCountryCode(): String? {
        return maxSdk?.configuration?.countryCode
    }
    
    @JvmStatic
    fun getConsentDialogState(): AppLovinSdkConfiguration.ConsentDialogState? {
        return maxSdk?.configuration?.consentDialogState
    }
    
    @JvmStatic
    fun isTablet(): Boolean {
        // Check if device is tablet using screen size
        val context = contextRef?.get() ?: return false
        val configuration = context.resources.configuration
        val screenLayout = configuration.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK
        return screenLayout >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
    }
    
    @JvmStatic
    fun isUserInEEA(): Boolean {
        // List of EEA country codes
        val eeaCountries = setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE", "IS", "LI", "NO"
        )
        
        return getCountryCode()?.let { countryCode ->
            eeaCountries.contains(countryCode.uppercase())
        } ?: false
    }
    
    @JvmStatic
    fun loadInterstitialAd(
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (MaxError) -> Unit = {}
    ) {
        val effectiveAdUnitId = appLovinConfig?.getEffectiveInterstitialAdUnitId() ?: ""
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
        onAdFailedToLoad: (MaxError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        val ctx = contextRef?.get() ?: run {
            Log.e(TAG, "Context not set. Call init() first")
            return
        }
        
        appLovinConfig?.let { config ->
            if (!config.shouldShowInterstitials(ctx)) {
                Log.d(TAG, "Interstitial ads disabled in debug mode")
                return
            }
        }
        
        interstitialAd = MaxInterstitialAd(adUnitId, ctx)
        
        interstitialAd?.apply {
            setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Log.d(TAG, "Interstitial ad loaded ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    onAdLoaded()
                }
                
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "Failed to load interstitial: ${error.message} ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        val delayMillis = getRetryDelayMillis(retryAttempt + 1)
                        Log.d(TAG, "Retrying interstitial ad load (attempt ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS) after ${delayMillis/1000} seconds")
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadInterstitialAd(adUnitId, onAdLoaded, onAdFailedToLoad, retryAttempt + 1)
                        }, delayMillis)
                    } else {
                        Log.e(TAG, "Max retry attempts reached for interstitial ad")
                        interstitialAd = null
                        onAdFailedToLoad(error)
                    }
                }
                
                override fun onAdDisplayed(ad: MaxAd) {
                    Log.d(TAG, "Interstitial ad displayed")
                }
                
                override fun onAdHidden(ad: MaxAd) {
                    Log.d(TAG, "Interstitial ad hidden")
                    // Auto-reload the interstitial ad
                    appLovinConfig?.let { config ->
                        val effectiveAdUnitId = config.getEffectiveInterstitialAdUnitId()
                        if (effectiveAdUnitId.isNotEmpty()) {
                            Log.d(TAG, "Auto-reloading interstitial ad")
                            loadInterstitialAd(effectiveAdUnitId)
                        }
                    }
                }
                
                override fun onAdClicked(ad: MaxAd) {
                    Log.d(TAG, "Interstitial ad clicked")
                }
                
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.e(TAG, "Failed to display interstitial ad: ${error.message}")
                }
            })
            
            loadAd()
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
        appLovinConfig?.let { config ->
            if (!config.shouldShowInterstitials(activity)) {
                Log.d(TAG, "Interstitial ads disabled in debug mode")
                return false
            }
        }
        
        return if (interstitialAd?.isReady == true) {
            interstitialAd?.showAd(activity)
            true
        } else {
            Log.w(TAG, "Interstitial ad not ready")
            false
        }
    }
    
    @JvmStatic
    fun loadRewardedAd(
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (MaxError) -> Unit = {}
    ) {
        val effectiveAdUnitId = appLovinConfig?.getEffectiveRewardedAdUnitId() ?: ""
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
        onAdFailedToLoad: (MaxError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        val ctx = contextRef?.get() ?: run {
            Log.e(TAG, "Context not set. Call init() first")
            return
        }
        
        appLovinConfig?.let { config ->
            if (!config.shouldShowRewardedAds(ctx)) {
                Log.d(TAG, "Rewarded ads disabled in debug mode")
                return
            }
        }
        
        rewardedAd = MaxRewardedAd.getInstance(adUnitId, ctx)
        
        rewardedAd?.apply {
            setListener(object : MaxRewardedAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Log.d(TAG, "Rewarded ad loaded ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    onAdLoaded()
                }
                
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "Failed to load rewarded ad: ${error.message} ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        val delayMillis = getRetryDelayMillis(retryAttempt + 1)
                        Log.d(TAG, "Retrying rewarded ad load (attempt ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS) after ${delayMillis/1000} seconds")
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadRewardedAd(adUnitId, onAdLoaded, onAdFailedToLoad, retryAttempt + 1)
                        }, delayMillis)
                    } else {
                        Log.e(TAG, "Max retry attempts reached for rewarded ad")
                        rewardedAd = null
                        onAdFailedToLoad(error)
                    }
                }
                
                override fun onAdDisplayed(ad: MaxAd) {
                    Log.d(TAG, "Rewarded ad displayed")
                }
                
                override fun onAdHidden(ad: MaxAd) {
                    Log.d(TAG, "Rewarded ad hidden")
                    // Auto-reload the rewarded ad
                    appLovinConfig?.let { config ->
                        val effectiveAdUnitId = config.getEffectiveRewardedAdUnitId()
                        if (effectiveAdUnitId.isNotEmpty()) {
                            Log.d(TAG, "Auto-reloading rewarded ad")
                            loadRewardedAd(effectiveAdUnitId)
                        }
                    }
                }
                
                override fun onAdClicked(ad: MaxAd) {
                    Log.d(TAG, "Rewarded ad clicked")
                }
                
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.e(TAG, "Failed to display rewarded ad: ${error.message}")
                }
                
                override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                    Log.d(TAG, "User rewarded: ${reward.amount} ${reward.label}")
                }
                
                // Note: onRewardedVideoStarted and onRewardedVideoCompleted are not in MaxRewardedAdListener
            })
            
            loadAd()
        }
    }
    
    @JvmStatic
    fun showRewardedAd(
        onUserRewarded: (MaxReward) -> Unit = {}
    ): Boolean {
        val activity = currentActivityRef?.get()
        if (activity == null) {
            Log.e(TAG, "No current activity available to show rewarded ad")
            return false
        }
        return showRewardedAd(activity, onUserRewarded)
    }
    
    @JvmStatic
    fun showRewardedAd(
        activity: Activity,
        onUserRewarded: (MaxReward) -> Unit = {}
    ): Boolean {
        // Check debug flag
        appLovinConfig?.let { config ->
            if (!config.shouldShowRewardedAds(activity)) {
                Log.d(TAG, "Rewarded ads disabled in debug mode")
                return false
            }
        }
        
        if (rewardedAd?.isReady == true) {
            // Set up one-time reward listener
            rewardedAd?.setListener(object : MaxRewardedAdListener {
                override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                    Log.d(TAG, "User rewarded: ${reward.amount} ${reward.label}")
                    onUserRewarded(reward)
                    
                    // Reload ad after reward
                    appLovinConfig?.let { config ->
                        val effectiveAdUnitId = config.getEffectiveRewardedAdUnitId()
                        if (effectiveAdUnitId.isNotEmpty()) {
                            loadRewardedAd(effectiveAdUnitId)
                        }
                    }
                }
                override fun onAdLoaded(ad: MaxAd) {}
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
                override fun onAdDisplayed(ad: MaxAd) {}
                override fun onAdHidden(ad: MaxAd) {
                    // Reload ad after hidden
                    appLovinConfig?.let { config ->
                        val effectiveAdUnitId = config.getEffectiveRewardedAdUnitId()
                        if (effectiveAdUnitId.isNotEmpty()) {
                            loadRewardedAd(effectiveAdUnitId)
                        }
                    }
                }
                override fun onAdClicked(ad: MaxAd) {}
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
            })
            
            rewardedAd?.showAd(activity)
            return true
        } else {
            Log.w(TAG, "Rewarded ad not ready")
            return false
        }
    }
    
    private fun initializeAppOpenAd(adUnitId: String) {
        if (adUnitId.isEmpty()) {
            Log.w(TAG, "App Open Ad unit ID is empty")
            return
        }
        
        contextRef?.get()?.let { ctx ->
            appOpenAd = MaxAppOpenAd(adUnitId, ctx)
            appOpenAd?.setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Log.d(TAG, "App Open Ad loaded")
                }
                
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "Failed to load App Open Ad: ${error.message}")
                    // Retry after delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        appOpenAd?.loadAd()
                    }, 30000) // Retry after 30 seconds
                }
                
                override fun onAdDisplayed(ad: MaxAd) {
                    Log.d(TAG, "App Open Ad displayed")
                }
                
                override fun onAdHidden(ad: MaxAd) {
                    Log.d(TAG, "App Open Ad hidden")
                    appOpenAd?.loadAd() // Reload for next time
                }
                
                override fun onAdClicked(ad: MaxAd) {
                    Log.d(TAG, "App Open Ad clicked")
                }
                
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.e(TAG, "Failed to display App Open Ad: ${error.message}")
                    appOpenAd?.loadAd() // Reload for next time
                }
            })
            
            appOpenAd?.loadAd()
        }
    }
    
    @JvmStatic
    fun showAppOpenAd(): Boolean {
        val activity = currentActivityRef?.get()
        if (activity == null) {
            Log.e(TAG, "No current activity available to show app open ad")
            return false
        }
        return showAppOpenAd(activity)
    }
    
    @JvmStatic
    fun showAppOpenAd(activity: Activity): Boolean {
        return if (appOpenAd?.isReady == true) {
            appOpenAd?.showAd()
            true
        } else {
            Log.w(TAG, "App Open Ad not ready")
            false
        }
    }
    
    @JvmStatic
    fun isInitialized(): Boolean = isInitialized
    
    @JvmStatic
    fun isInterstitialReady(): Boolean = interstitialAd?.isReady == true
    
    @JvmStatic
    fun isRewardedReady(): Boolean = rewardedAd?.isReady == true
    
    @JvmStatic
    fun isAppOpenAdReady(): Boolean = appOpenAd?.isReady == true
    
    @JvmStatic
    fun getConfig(): AppLovinConfig? = appLovinConfig
    
    @JvmStatic
    fun updateConfig(config: AppLovinConfig) {
        this.appLovinConfig = config
        
        // Re-initialize App Open Ad if needed
        if (config.enableAppOpenAd && config.appOpenAdUnitId.isNotEmpty()) {
            initializeAppOpenAd(config.appOpenAdUnitId)
        }
    }
    
    // Calculate exponential backoff delay
    private fun getRetryDelayMillis(retryAttempt: Int): Long {
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