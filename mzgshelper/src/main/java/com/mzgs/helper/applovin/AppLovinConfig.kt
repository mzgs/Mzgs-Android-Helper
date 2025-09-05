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
    val enableAppOpenAd: Boolean = false,
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
    val testDeviceAdvertisingIds: List<String> = emptyList()  // DEBUG ONLY: Protected by isDebugMode check
) {
    companion object {
        // IMPORTANT: AppLovin MAX does not provide generic test ad unit IDs
        // You must create REAL ad units in your AppLovin dashboard and use those IDs
        // Test ads are shown by adding your device's advertising ID to testDeviceAdvertisingIds
        
        // These constants should be replaced with your ACTUAL ad unit IDs from AppLovin dashboard
        const val TEST_BANNER_AD_UNIT_ID = "" // Add your real banner ad unit ID
        const val TEST_MREC_AD_UNIT_ID = "" // Add your real MREC ad unit ID
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "" // Add your real interstitial ad unit ID
        const val TEST_REWARDED_AD_UNIT_ID = "" // Add your real rewarded ad unit ID
        const val TEST_APP_OPEN_AD_UNIT_ID = "" // Add your real app open ad unit ID
        const val TEST_NATIVE_AD_UNIT_ID = "" // Add your real native ad unit ID
        
        // When testDeviceAdvertisingIds contains your device ID, AppLovin will show test ads
        // even when using production ad unit IDs
        
        fun createTestConfig(sdkKey: String): AppLovinConfig {
            return AppLovinConfig(
                sdkKey = sdkKey,
                bannerAdUnitId = TEST_BANNER_AD_UNIT_ID,
                mrecAdUnitId = TEST_MREC_AD_UNIT_ID,
                interstitialAdUnitId = TEST_INTERSTITIAL_AD_UNIT_ID,
                rewardedAdUnitId = TEST_REWARDED_AD_UNIT_ID,
                appOpenAdUnitId = TEST_APP_OPEN_AD_UNIT_ID,
                nativeAdUnitId = TEST_NATIVE_AD_UNIT_ID,
                enableAppOpenAd = true,
                enableTestMode = true,
                verboseLogging = true,
                creativeDebuggerEnabled = true
            )
        }
    }
    
    fun getEffectiveBannerAdUnitId(context: android.content.Context? = null): String {
        // SAFETY: Only use test ads if in debug mode AND enableTestMode is true
        val useTestAds = context?.let { MzgsHelper.isDebugMode() && enableTestMode } ?: enableTestMode
        return if (useTestAds) TEST_BANNER_AD_UNIT_ID else bannerAdUnitId
    }
    
    fun getEffectiveMrecAdUnitId(context: android.content.Context? = null): String {
        // SAFETY: Only use test ads if in debug mode AND enableTestMode is true
        val useTestAds = context?.let { MzgsHelper.isDebugMode() && enableTestMode } ?: enableTestMode
        return if (useTestAds) TEST_MREC_AD_UNIT_ID else mrecAdUnitId
    }
    
    fun getEffectiveInterstitialAdUnitId(context: android.content.Context? = null): String {
        // SAFETY: Only use test ads if in debug mode AND enableTestMode is true
        val useTestAds = context?.let { MzgsHelper.isDebugMode() && enableTestMode } ?: enableTestMode
        return if (useTestAds) TEST_INTERSTITIAL_AD_UNIT_ID else interstitialAdUnitId
    }
    
    fun getEffectiveRewardedAdUnitId(context: android.content.Context? = null): String {
        // SAFETY: Only use test ads if in debug mode AND enableTestMode is true
        val useTestAds = context?.let { MzgsHelper.isDebugMode() && enableTestMode } ?: enableTestMode
        return if (useTestAds) TEST_REWARDED_AD_UNIT_ID else rewardedAdUnitId
    }
    
    fun getEffectiveAppOpenAdUnitId(context: android.content.Context? = null): String {
        // SAFETY: Only use test ads if in debug mode AND enableTestMode is true
        val useTestAds = context?.let { MzgsHelper.isDebugMode() && enableTestMode } ?: enableTestMode
        return if (useTestAds) TEST_APP_OPEN_AD_UNIT_ID else appOpenAdUnitId
    }
    
    fun getEffectiveNativeAdUnitId(context: android.content.Context? = null): String {
        // SAFETY: Only use test ads if in debug mode AND enableTestMode is true
        val useTestAds = context?.let { MzgsHelper.isDebugMode() && enableTestMode } ?: enableTestMode
        return if (useTestAds) TEST_NATIVE_AD_UNIT_ID else nativeAdUnitId
    }
    
    fun shouldShowAds(context: android.content.Context): Boolean {
        if (!MzgsHelper.isDebugMode()) return true
        return showAdsInDebug
    }
    
    fun shouldShowInterstitials(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebugMode()) return true
        return showInterstitialsInDebug
    }
    
    fun shouldShowAppOpenAd(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebugMode()) return enableAppOpenAd
        return enableAppOpenAd && showAppOpenAdInDebug
    }
    
    fun shouldShowBanners(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebugMode()) return true
        return showBannersInDebug
    }
    
    fun shouldShowNativeAds(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebugMode()) return true
        return showNativeAdsInDebug
    }
    
    fun shouldShowRewardedAds(context: android.content.Context): Boolean {
        if (!shouldShowAds(context)) return false
        if (!MzgsHelper.isDebugMode()) return true
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