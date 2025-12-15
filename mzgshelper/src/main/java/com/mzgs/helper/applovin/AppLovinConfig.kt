package com.mzgs.helper.applovin

import com.mzgs.helper.MzgsHelper

data class AppLovinConfig(
    val sdkKey: String,
    val bannerAdUnitId: String = "",
    val mrecAdUnitId: String = "",
    val interstitialAdUnitId: String = "",
    val rewardedAdUnitId: String = "",
    val appOpenAdUnitId: String = "",
    val nativeAdUnitId: String = "",
    val bannerAutoRefreshSeconds: Int = 60,  // Auto-refresh banner/MREC ads every X seconds (0 to disable)
    val enableAppOpenAd: Boolean = true,
    val enableTestCMP: Boolean = false,
    val consentFlowPrivacyPolicyUrl: String? = null,
    val consentFlowTermsOfServiceUrl: String? = null,
    val enableTestMode: Boolean = false,  // DEBUG ONLY: Protected by isDebugMode check
    val verboseLogging: Boolean = false,  // Note: Currently only logged, not applied to SDK
    val muteAudio: Boolean = false,
    val creativeDebuggerEnabled: Boolean = false,  // Note: Currently only logged, not applied to SDK
    // Debug-only flags (only work when BuildConfig.DEBUG is true)
    val showAdsInDebug: Boolean = true,  // Control all ads in debug mode
    val showInterstitialsInDebug: Boolean = true,  // Control interstitials in debug
    val showAppOpenAdInDebug: Boolean = true,  // Control app open ads in debug
    val showBannersInDebug: Boolean = true,  // Control banner ads in debug
    val showNativeAdsInDebug: Boolean = true,  // Control native ads in debug
    val showRewardedAdsInDebug: Boolean = true,  // Control rewarded ads in debug
    val testDeviceAdvertisingIds: List<String> = emptyList(),  // DEBUG ONLY: Protected by isDebugMode check
    val debugEmptyIds: Boolean = false  // DEBUG ONLY: Use empty ad unit IDs in debug mode (prevents real ad loading)
) {

    
    fun shouldShowAds(context: android.content.Context): Boolean {
        if (!MzgsHelper.isDebug()) return true
        return showAdsInDebug
    }
    
    fun shouldShowInterstitials(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebug()) return true
        return showInterstitialsInDebug
    }
    
    fun shouldShowAppOpenAd(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebug()) return enableAppOpenAd
        return enableAppOpenAd && showAppOpenAdInDebug
    }
    
    fun shouldShowBanners(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebug()) return true
        return showBannersInDebug
    }
    
    fun shouldShowNativeAds(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebug()) return true
        return showNativeAdsInDebug
    }
    
    fun shouldShowRewardedAds(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebug()) return true
        return showRewardedAdsInDebug
    }
}

enum class AppLovinAdFormat {
    BANNER,
    MREC,
    INTERSTITIAL,
    REWARDED,
    APP_OPEN,
    NATIVE
}

data class AppLovinAdLoadState(
    val format: AppLovinAdFormat,
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val error: String? = null,
    val lastLoadTime: Long = 0L
) {
    fun isReady(): Boolean = isLoaded && !isLoading
    
    fun shouldReload(expirationTimeMs: Long = 3600000): Boolean {
        return !isLoaded && !isLoading && 
               (System.currentTimeMillis() - lastLoadTime) > expirationTimeMs
    }
}
