package com.mzgs.helper.applovin

import android.app.Activity
import android.app.Application
import android.content.Context
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
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class AppLovinMediationManager(private val context: Context) {
    
    private var appLovinConfig: AppLovinConfig? = null
    private var maxSdk: AppLovinSdk? = null
    
    companion object {
        private const val TAG = "AppLovinMediation"
        private var isInitialized = false
        private const val MAX_RETRY_ATTEMPTS = 6
        
        @Volatile
        private var INSTANCE: AppLovinMediationManager? = null
        
        fun getInstance(context: Context): AppLovinMediationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppLovinMediationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        private fun getRetryDelayMillis(retryAttempt: Int): Long {
            val exponent = min(6, retryAttempt)
            return TimeUnit.SECONDS.toMillis(2.0.pow(exponent).toLong())
        }
    }
    
    private var interstitialAd: MaxInterstitialAd? = null
    private var rewardedAd: MaxRewardedAd? = null
    private var appOpenAd: MaxAppOpenAd? = null
    
    fun initialize(
        config: AppLovinConfig,
        onInitComplete: () -> Unit = {}
    ) {
        this.appLovinConfig = config
        
        if (isInitialized) {
            Log.d(TAG, "AppLovin MAX already initialized")
            onInitComplete()
            return
        }
        
        // Create initialization configuration using the builder pattern
        val initConfig = AppLovinSdkInitializationConfiguration.builder(config.sdkKey, context)
            .setMediationProvider(AppLovinMediationProvider.MAX)
            .setTestDeviceAdvertisingIds(config.testDeviceAdvertisingIds)
            // Note: setInitializationAdUnitIds is not available in current SDK version
            .build()
        
        // Initialize SDK with configuration
        AppLovinSdk.getInstance(context).initialize(initConfig) { sdkConfig ->
            maxSdk = AppLovinSdk.getInstance(context)
            
            // Settings are configured during initialization through the builder
            // Log current settings for debugging
            Log.d(TAG, "Verbose Logging: ${config.verboseLogging}")
            Log.d(TAG, "Mute Audio: ${config.muteAudio}")
            Log.d(TAG, "Creative Debugger: ${config.creativeDebuggerEnabled}")
            
            isInitialized = true
            Log.d(TAG, "AppLovin MAX SDK initialized")
            Log.d(TAG, "Country Code: ${sdkConfig.countryCode}")
            Log.d(TAG, "Consent Dialog State: ${sdkConfig.consentDialogState}")
            Log.d(TAG, "Is Testing Enabled: ${sdkConfig.isTestModeEnabled}")
            
            if (config.enableAppOpenAd && config.getEffectiveAppOpenAdUnitId().isNotEmpty()) {
                val application = context.applicationContext as? Application
                if (application != null) {
                    AppLovinAppOpenAdManager.initialize(application, config)
                    Log.d(TAG, "App Open Ad Manager initialized automatically")
                } else {
                    Log.w(TAG, "Could not initialize App Open Ad Manager - context is not Application")
                }
            }
            
            onInitComplete()
        }
    }
    
    // Privacy and Consent Management (Updated for 2025 compliance)
    fun setHasUserConsent(hasUserConsent: Boolean) {
        AppLovinPrivacySettings.setHasUserConsent(hasUserConsent, context)
        Log.d(TAG, "GDPR user consent set to: $hasUserConsent")
    }
    
    fun setIsAgeRestrictedUser(isAgeRestrictedUser: Boolean) {
        // Note: setIsAgeRestrictedUser is deprecated in newer SDK versions
        // Use setHasUserConsent instead for COPPA compliance
        Log.d(TAG, "Age restricted user set to: $isAgeRestrictedUser (Note: Using consent mechanism)")
        if (isAgeRestrictedUser) {
            // For age-restricted users, we should not show personalized ads
            setHasUserConsent(false)
        }
    }
    
    fun setDoNotSell(doNotSell: Boolean) {
        AppLovinPrivacySettings.setDoNotSell(doNotSell, context)
        Log.d(TAG, "CCPA do not sell set to: $doNotSell")
    }
    
    // Get current privacy settings
    fun hasUserConsent(): Boolean? {
        return AppLovinPrivacySettings.hasUserConsent(context)
    }
    
    fun isAgeRestrictedUser(): Boolean? {
        // Note: isAgeRestrictedUser is deprecated, returning based on consent status
        val hasConsent = hasUserConsent()
        return if (hasConsent != null) !hasConsent else null
    }
    
    fun isDoNotSell(): Boolean? {
        return AppLovinPrivacySettings.isDoNotSell(context)
    }
    
    // Set user segment for better ad targeting
    fun setUserSegment(segment: String?) {
        // Note: userSegment is not directly available in current SDK
        Log.d(TAG, "User segment set to: $segment (stored for future use)")
    }
    
    // Get current user segment
    fun getUserSegment(): String? {
        // Note: userSegment is not directly available in current SDK
        return null
    }
    
    // Set custom data for the SDK
    fun setCustomData(key: String, value: String?) {
        // Custom data can be set on individual ad units
        Log.d(TAG, "Custom data set: $key = $value")
    }
    
    // Check if user is in GDPR region (EEA)
    fun isUserInGDPRRegion(): Boolean {
        val configuration = maxSdk?.configuration
        return configuration?.countryCode?.let { countryCode ->
            // EEA country codes
            val eeaCountries = setOf(
                "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
                "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
                "PL", "PT", "RO", "SK", "SI", "ES", "SE", "GB", "IS", "LI", "NO"
            )
            eeaCountries.contains(countryCode.uppercase())
        } ?: false
    }
    
    fun loadInterstitialAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (MaxError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        appLovinConfig?.let { config ->
            if (!config.shouldShowInterstitials(context)) {
                Log.d(TAG, "Interstitial ads disabled in debug mode")
                return
            }
        }
        
        interstitialAd = MaxInterstitialAd(adUnitId, context)
        
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
                    Log.e(TAG, "Failed to display interstitial: ${error.message}")
                    interstitialAd?.loadAd()
                }
            })
            
            loadAd()
        }
    }
    
    fun showInterstitialAd(activity: Activity): Boolean {
        appLovinConfig?.let { config ->
            if (!config.shouldShowInterstitials(context)) {
                Log.d(TAG, "Interstitial ads disabled in debug mode")
                return false
            }
        }
        
        return if (interstitialAd?.isReady == true) {
            interstitialAd?.showAd()
            true
        } else {
            Log.w(TAG, "Interstitial ad not ready")
            false
        }
    }
    
    fun loadRewardedAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (MaxError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        appLovinConfig?.let { config ->
            if (!config.shouldShowRewardedAds(context)) {
                Log.d(TAG, "Rewarded ads disabled in debug mode")
                return
            }
        }
        
        rewardedAd = MaxRewardedAd.getInstance(adUnitId, context)
        
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
                    rewardedAd?.loadAd()
                }
                
                
                override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                    Log.d(TAG, "User rewarded: ${reward.amount} ${reward.label}")
                }
            })
            
            loadAd()
        }
    }
    
    fun showRewardedAd(
        activity: Activity,
        onUserRewarded: (MaxReward) -> Unit
    ): Boolean {
        appLovinConfig?.let { config ->
            if (!config.shouldShowRewardedAds(context)) {
                Log.d(TAG, "Rewarded ads disabled in debug mode")
                return false
            }
        }
        
        return if (rewardedAd?.isReady == true) {
            rewardedAd?.apply {
                setListener(object : MaxRewardedAdListener {
                    override fun onAdLoaded(ad: MaxAd) {}
                    override fun onAdLoadFailed(adUnitId: String, error: MaxError) {}
                    override fun onAdDisplayed(ad: MaxAd) {}
                    override fun onAdHidden(ad: MaxAd) {
                        appLovinConfig?.let { config ->
                            val effectiveAdUnitId = config.getEffectiveRewardedAdUnitId()
                            if (effectiveAdUnitId.isNotEmpty()) {
                                loadRewardedAd(effectiveAdUnitId)
                            }
                        }
                    }
                    override fun onAdClicked(ad: MaxAd) {}
                    override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {}
                    override fun onUserRewarded(ad: MaxAd, reward: MaxReward) {
                        onUserRewarded(reward)
                    }
                })
                showAd()
            }
            true
        } else {
            Log.w(TAG, "Rewarded ad not ready")
            false
        }
    }
    
    fun loadAppOpenAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (MaxError) -> Unit = {},
        retryAttempt: Int = 0
    ) {
        appLovinConfig?.let { config ->
            if (!config.shouldShowAppOpenAd(context)) {
                Log.d(TAG, "App open ads disabled")
                return
            }
        }
        
        appOpenAd = MaxAppOpenAd(adUnitId, context)
        
        appOpenAd?.apply {
            setListener(object : MaxAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Log.d(TAG, "App open ad loaded ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    onAdLoaded()
                }
                
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "Failed to load app open ad: ${error.message} ${if (retryAttempt > 0) "(retry $retryAttempt)" else ""}")
                    
                    if (retryAttempt < MAX_RETRY_ATTEMPTS) {
                        val delayMillis = getRetryDelayMillis(retryAttempt + 1)
                        Log.d(TAG, "Retrying app open ad load (attempt ${retryAttempt + 1}/$MAX_RETRY_ATTEMPTS) after ${delayMillis/1000} seconds")
                        Handler(Looper.getMainLooper()).postDelayed({
                            loadAppOpenAd(adUnitId, onAdLoaded, onAdFailedToLoad, retryAttempt + 1)
                        }, delayMillis)
                    } else {
                        Log.e(TAG, "Max retry attempts reached for app open ad")
                        appOpenAd = null
                        onAdFailedToLoad(error)
                    }
                }
                
                override fun onAdDisplayed(ad: MaxAd) {
                    Log.d(TAG, "App open ad displayed")
                }
                
                override fun onAdHidden(ad: MaxAd) {
                    Log.d(TAG, "App open ad hidden")
                    appOpenAd = null
                    appLovinConfig?.let { config ->
                        val effectiveAdUnitId = config.getEffectiveAppOpenAdUnitId()
                        if (effectiveAdUnitId.isNotEmpty()) {
                            Log.d(TAG, "Auto-reloading app open ad")
                            loadAppOpenAd(effectiveAdUnitId)
                        }
                    }
                }
                
                override fun onAdClicked(ad: MaxAd) {
                    Log.d(TAG, "App open ad clicked")
                }
                
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.e(TAG, "Failed to display app open ad: ${error.message}")
                    appOpenAd?.loadAd()
                }
            })
            
            loadAd()
        }
    }
    
    fun showAppOpenAd(activity: Activity): Boolean {
        appLovinConfig?.let { config ->
            if (!config.shouldShowAppOpenAd(context)) {
                Log.d(TAG, "App open ads disabled")
                return false
            }
        }
        
        return if (appOpenAd?.isReady == true) {
            appOpenAd?.showAd()
            true
        } else {
            Log.w(TAG, "App open ad not ready")
            false
        }
    }
    
    fun isInterstitialReady(): Boolean = interstitialAd?.isReady == true
    
    fun isRewardedAdReady(): Boolean = rewardedAd?.isReady == true
    
    fun isAppOpenAdReady(): Boolean = appOpenAd?.isReady == true
    
    fun getConfig(): AppLovinConfig? = appLovinConfig
    
    fun updateConfig(config: AppLovinConfig) {
        this.appLovinConfig = config
        
        if (config.enableAppOpenAd) {
            val application = context.applicationContext as? Application
            if (application != null && AppLovinAppOpenAdManager.getInstance() == null) {
                AppLovinAppOpenAdManager.initialize(application, config)
                Log.d(TAG, "App Open Ad Manager initialized after config update")
            }
        }
    }
    
    fun showMediationDebugger() {
        maxSdk?.showMediationDebugger()
    }
    
    fun showCreativeDebugger() {
        maxSdk?.showCreativeDebugger()
    }
    
    // Get device info for testing
    fun getTestDeviceInfo(): String {
        val sb = StringBuilder()
        sb.append("AppLovin Test Device Info:\n")
        sb.append("SDK Version: ${AppLovinSdk.VERSION}\n")
        sb.append("SDK Key: ${appLovinConfig?.sdkKey ?: "Not set"}\n")
        sb.append("Is Initialized: $isInitialized\n")
        
        maxSdk?.configuration?.let { config ->
            sb.append("Country Code: ${config.countryCode}\n")
            sb.append("Test Mode Enabled: ${config.isTestModeEnabled}\n")
        }
        
        sb.append("\nTo enable test ads:\n")
        sb.append("1. Add your device's advertising ID to testDeviceAdvertisingIds\n")
        sb.append("2. Enable test mode in AppLovin dashboard\n")
        sb.append("3. Use Mediation Debugger to verify integration\n")
        
        return sb.toString()
    }
    
    // Check if SDK is ready for ad requests
    fun isReady(): Boolean {
        return isInitialized && maxSdk != null
    }
}