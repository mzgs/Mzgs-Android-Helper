package com.mzgs.helper.admob

import com.google.android.gms.ads.AdSize
import com.mzgs.helper.MzgsHelper

data class AdMobConfig(
    val appId: String? = null,
    val bannerAdUnitId: String = "",
    val interstitialAdUnitId: String = "",
    val rewardedAdUnitId: String = "",
    val rewardedInterstitialAdUnitId: String = "",
    val nativeAdUnitId: String = "",
    val mrecAdUnitId: String = "",
    val appOpenAdUnitId: String = "",
    val enableAppOpenAd: Boolean = false,
    val bannerAutoRefreshSeconds: Int = 60,  // Auto-refresh banner ads every X seconds (0 to disable)
    val testDeviceIds: List<String> = emptyList(),
    val enableTestMode: Boolean = false,
    // Debug-only flags (only work when BuildConfig.DEBUG is true)
    val showAdsInDebug: Boolean = true,  // Master switch for all ads in debug mode
    val showInterstitialsInDebug: Boolean = true,  // Control interstitials in debug
    val showAppOpenAdInDebug: Boolean = true,  // Control app open ads in debug
    val showBannersInDebug: Boolean = true,  // Control banner ads in debug
    val showNativeAdsInDebug: Boolean = true,  // Control native ads in debug
    val showRewardedAdsInDebug: Boolean = true,  // Control rewarded ads in debug
    val debugRequireConsentAlways: Boolean = false,  // DEBUG ONLY: Forces consent form in debug builds ONLY - NEVER affects release
    val debugEmptyIds: Boolean = false  // DEBUG ONLY: Use empty ad unit IDs in debug mode (prevents real ad loading)
) {
    companion object {
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        const val TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
        const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        const val TEST_MREC_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"  // Same as banner for MREC in test
        const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
        
        fun createTestConfig(): AdMobConfig {
            return AdMobConfig(
                bannerAdUnitId = TEST_BANNER_AD_UNIT_ID,
                interstitialAdUnitId = TEST_INTERSTITIAL_AD_UNIT_ID,
                rewardedAdUnitId = TEST_REWARDED_AD_UNIT_ID,
                rewardedInterstitialAdUnitId = TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
                nativeAdUnitId = TEST_NATIVE_AD_UNIT_ID,
                mrecAdUnitId = TEST_MREC_AD_UNIT_ID,
                appOpenAdUnitId = TEST_APP_OPEN_AD_UNIT_ID,
                enableAppOpenAd = true,
                enableTestMode = true
            )
        }
    }
    
    // Debug control methods - these only affect DEBUG builds
    fun shouldShowAds(context: android.content.Context): Boolean {
        // In release mode, always show ads
        if (!MzgsHelper.isDebug()) return true
        
        // In debug mode, check the master switch
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

enum class AdFormat {
    BANNER,
    INTERSTITIAL,
    REWARDED,
    REWARDED_INTERSTITIAL,
    NATIVE,
    APP_OPEN
}

data class AdLoadState(
    val format: AdFormat,
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

object AdSizeHelper {
    fun getBannerSize(): AdSize = AdSize.BANNER
    
    fun getLargeBannerSize(): AdSize = AdSize.LARGE_BANNER
    
    fun getMediumRectangleSize(): AdSize = AdSize.MEDIUM_RECTANGLE
    
    fun getFullBannerSize(): AdSize = AdSize.FULL_BANNER
    
    fun getLeaderboardSize(): AdSize = AdSize.LEADERBOARD
    
    // SMART_BANNER is deprecated - use adaptive banner instead
    // fun getSmartBannerSize(): AdSize = AdSize.SMART_BANNER
    
    fun getFluidSize(): AdSize = AdSize.FLUID
    
    fun getWideSkyscraperSize(): AdSize = AdSize.WIDE_SKYSCRAPER
    
    fun getCustomSize(width: Int, height: Int): AdSize = AdSize(width, height)
    
    // Modern adaptive banner methods (recommended over deprecated SMART_BANNER)
    fun getAdaptiveBannerAdSize(context: android.content.Context, width: Int): AdSize {
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width)
    }
    
    fun getInlineAdaptiveBannerAdSize(context: android.content.Context, width: Int, maxHeight: Int): AdSize {
        return AdSize.getInlineAdaptiveBannerAdSize(width, maxHeight)
    }
    
    fun getLandscapeAdaptiveBannerAdSize(context: android.content.Context, width: Int): AdSize {
        return AdSize.getLandscapeAnchoredAdaptiveBannerAdSize(context, width)
    }
    
    fun getPortraitAdaptiveBannerAdSize(context: android.content.Context, width: Int): AdSize {
        return AdSize.getPortraitAnchoredAdaptiveBannerAdSize(context, width)
    }
}