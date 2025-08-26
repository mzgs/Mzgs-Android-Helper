package com.mzgs.helper.admob

import com.google.android.gms.ads.AdSize

data class AdMobConfig(
    val appId: String? = null,
    val bannerAdUnitId: String = "",
    val interstitialAdUnitId: String = "",
    val rewardedAdUnitId: String = "",
    val rewardedInterstitialAdUnitId: String = "",
    val nativeAdUnitId: String = "",
    val testDeviceIds: List<String> = emptyList(),
    val enableTestMode: Boolean = false
) {
    companion object {
        const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
        const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
        const val TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
        const val TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
        const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
        
        fun createTestConfig(): AdMobConfig {
            return AdMobConfig(
                bannerAdUnitId = TEST_BANNER_AD_UNIT_ID,
                interstitialAdUnitId = TEST_INTERSTITIAL_AD_UNIT_ID,
                rewardedAdUnitId = TEST_REWARDED_AD_UNIT_ID,
                rewardedInterstitialAdUnitId = TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
                nativeAdUnitId = TEST_NATIVE_AD_UNIT_ID,
                enableTestMode = true
            )
        }
    }
    
    fun getEffectiveBannerAdUnitId(): String {
        return if (enableTestMode) TEST_BANNER_AD_UNIT_ID else bannerAdUnitId
    }
    
    fun getEffectiveInterstitialAdUnitId(): String {
        return if (enableTestMode) TEST_INTERSTITIAL_AD_UNIT_ID else interstitialAdUnitId
    }
    
    fun getEffectiveRewardedAdUnitId(): String {
        return if (enableTestMode) TEST_REWARDED_AD_UNIT_ID else rewardedAdUnitId
    }
    
    fun getEffectiveRewardedInterstitialAdUnitId(): String {
        return if (enableTestMode) TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID else rewardedInterstitialAdUnitId
    }
    
    fun getEffectiveNativeAdUnitId(): String {
        return if (enableTestMode) TEST_NATIVE_AD_UNIT_ID else nativeAdUnitId
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