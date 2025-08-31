package com.mzgs.helper

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.LoadAdError
import com.applovin.mediation.MaxError
import com.mzgs.helper.admob.AdMobMediationManager
import com.mzgs.helper.applovin.AppLovinMediationManager

object Ads {
    private const val TAG = "Ads"
    private const val ADMOB = "admob"
    private const val APPLOVIN_MAX = "applovin_max"
    
    private var applicationContext: Context? = null
    
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
    
    private fun getAdsOrder(): List<String> {
        return Remote.getStringArray("ads_order", listOf(APPLOVIN_MAX, ADMOB))
    }
    
    @JvmStatic
    fun showInterstitial(): Boolean {
        val context = applicationContext ?: return false
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show interstitial with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    if (AppLovinMediationManager.isInterstitialReady()) {
                        Log.d(TAG, "Showing AppLovin MAX interstitial")
                        return AppLovinMediationManager.showInterstitialAd()
                    }
                    Log.d(TAG, "AppLovin MAX interstitial not ready, trying next")
                }
                ADMOB -> {
                    if (AdMobMediationManager.isInterstitialReady()) {
                        Log.d(TAG, "Showing AdMob interstitial")
                        return AdMobMediationManager.showInterstitialAd()
                    }
                    Log.d(TAG, "AdMob interstitial not ready, trying next")
                }
                else -> {
                    Log.w(TAG, "Unknown ad network: $network")
                }
            }
        }
        
        Log.d(TAG, "No interstitial ads ready from any network")
        return false
    }
    
    @JvmStatic
    fun showBanner(
        activity: Activity,
        container: ViewGroup,
        adSize: BannerSize = BannerSize.ADAPTIVE
    ): Boolean {
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show banner with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    try {
                        Log.d(TAG, "Attempting to show AppLovin MAX banner")
                        val bannerHelper = com.mzgs.helper.applovin.AppLovinBannerHelper(activity)
                        val bannerType = when (adSize) {
                            BannerSize.ADAPTIVE, BannerSize.BANNER -> com.mzgs.helper.applovin.AppLovinBannerHelper.BannerType.BANNER
                            BannerSize.MEDIUM_RECTANGLE -> com.mzgs.helper.applovin.AppLovinBannerHelper.BannerType.MREC
                            BannerSize.LEADERBOARD -> com.mzgs.helper.applovin.AppLovinBannerHelper.BannerType.LEADER
                            else -> com.mzgs.helper.applovin.AppLovinBannerHelper.BannerType.BANNER
                        }
                        bannerHelper.createBannerView(
                            adUnitId = AppLovinMediationManager.getConfig()?.bannerAdUnitId ?: "",
                            bannerType = bannerType,
                            container = container as android.widget.FrameLayout,
                            onAdLoaded = {
                                Log.d(TAG, "AppLovin MAX banner loaded successfully")
                            },
                            onAdFailedToLoad = { error ->
                                Log.e(TAG, "AppLovin MAX banner failed: ${error.message}")
                            }
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AppLovin MAX banner: ${e.message}")
                    }
                }
                ADMOB -> {
                    try {
                        Log.d(TAG, "Attempting to show AdMob banner")
                        val bannerHelper = com.mzgs.helper.admob.BannerAdHelper(activity)
                        val bannerType = when (adSize) {
                            BannerSize.ADAPTIVE -> com.mzgs.helper.admob.BannerAdHelper.BannerType.ADAPTIVE_BANNER
                            BannerSize.BANNER -> com.mzgs.helper.admob.BannerAdHelper.BannerType.BANNER
                            BannerSize.LARGE_BANNER -> com.mzgs.helper.admob.BannerAdHelper.BannerType.LARGE_BANNER
                            BannerSize.MEDIUM_RECTANGLE -> com.mzgs.helper.admob.BannerAdHelper.BannerType.MEDIUM_RECTANGLE
                            BannerSize.FULL_BANNER -> com.mzgs.helper.admob.BannerAdHelper.BannerType.FULL_BANNER
                            BannerSize.LEADERBOARD -> com.mzgs.helper.admob.BannerAdHelper.BannerType.LEADERBOARD
                        }
                        bannerHelper.createBannerView(
                            adUnitId = AdMobMediationManager.getConfig()?.bannerAdUnitId ?: "",
                            bannerType = bannerType,
                            container = container as android.widget.FrameLayout,
                            onAdLoaded = {
                                Log.d(TAG, "AdMob banner loaded successfully")
                            },
                            onAdFailedToLoad = { error ->
                                Log.e(TAG, "AdMob banner failed: ${error.message}")
                            }
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AdMob banner: ${e.message}")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown ad network for banner: $network")
                }
            }
        }
        
        Log.d(TAG, "Failed to show banner from any network")
        return false
    }
    
    @JvmStatic
    fun showRewardedAd(): Boolean {
        val context = applicationContext ?: return false
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show rewarded ad with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    if (AppLovinMediationManager.isRewardedReady()) {
                        Log.d(TAG, "Showing AppLovin MAX rewarded ad")
                        return AppLovinMediationManager.showRewardedAd()
                    }
                    Log.d(TAG, "AppLovin MAX rewarded ad not ready, trying next")
                }
                ADMOB -> {
                    if (AdMobMediationManager.isRewardedReady()) {
                        Log.d(TAG, "Showing AdMob rewarded ad")
                        return AdMobMediationManager.showRewardedAd()
                    }
                    Log.d(TAG, "AdMob rewarded ad not ready, trying next")
                }
                else -> {
                    Log.w(TAG, "Unknown ad network: $network")
                }
            }
        }
        
        Log.d(TAG, "No rewarded ads ready from any network")
        return false
    }
    
    @JvmStatic
    fun showNativeAd(
        activity: Activity,
        container: ViewGroup,
        layoutResId: Int? = null
    ): Boolean {
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show native ad with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    try {
                        Log.d(TAG, "Attempting to show AppLovin MAX native ad")
                        val nativeHelper = com.mzgs.helper.applovin.AppLovinNativeAdHelper(activity)
                        nativeHelper.loadNativeAd(
                            adUnitId = AppLovinMediationManager.getConfig()?.nativeAdUnitId ?: "",
                            onAdLoaded = {
                                Log.d(TAG, "AppLovin MAX native ad loaded successfully")
                            },
                            onAdFailedToLoad = { error ->
                                Log.e(TAG, "AppLovin MAX native ad failed: ${error.message}")
                            }
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AppLovin MAX native ad: ${e.message}")
                    }
                }
                ADMOB -> {
                    try {
                        Log.d(TAG, "Attempting to show AdMob native ad")
                        val nativeHelper = com.mzgs.helper.admob.AdMobNativeAdHelper(activity)
                        nativeHelper.loadNativeAd(
                            onAdLoaded = { nativeAd ->
                                Log.d(TAG, "AdMob native ad loaded successfully")
                            },
                            onAdFailedToLoad = { error ->
                                Log.e(TAG, "AdMob native ad failed: ${error.message}")
                            }
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AdMob native ad: ${e.message}")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown ad network for native ad: $network")
                }
            }
        }
        
        Log.d(TAG, "Failed to show native ad from any network")
        return false
    }
    
    @JvmStatic
    fun isAnyInterstitialReady(): Boolean {
        val adsOrder = getAdsOrder()
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    if (AppLovinMediationManager.isInterstitialReady()) {
                        return true
                    }
                }
                ADMOB -> {
                    if (AdMobMediationManager.isInterstitialReady()) {
                        return true
                    }
                }
            }
        }
        
        return false
    }
    
    @JvmStatic
    fun isAnyRewardedAdReady(): Boolean {
        val adsOrder = getAdsOrder()
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    if (AppLovinMediationManager.isRewardedReady()) {
                        return true
                    }
                }
                ADMOB -> {
                    if (AdMobMediationManager.isRewardedReady()) {
                        return true
                    }
                }
            }
        }
        
        return false
    }
    
    @JvmStatic
    fun showMREC(
        activity: Activity,
        container: ViewGroup
    ): Boolean {
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show MREC with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    try {
                        Log.d(TAG, "Attempting to show AppLovin MAX MREC")
                        val bannerHelper = com.mzgs.helper.applovin.AppLovinBannerHelper(activity)
                        bannerHelper.createBannerView(
                            adUnitId = AppLovinMediationManager.getConfig()?.getEffectiveMrecAdUnitId() ?: "",
                            bannerType = com.mzgs.helper.applovin.AppLovinBannerHelper.BannerType.MREC,
                            container = container as android.widget.FrameLayout,
                            onAdLoaded = {
                                Log.d(TAG, "AppLovin MAX MREC loaded successfully")
                            },
                            onAdFailedToLoad = { error ->
                                Log.e(TAG, "AppLovin MAX MREC failed: ${error.message}")
                            }
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AppLovin MAX MREC: ${e.message}")
                    }
                }
                ADMOB -> {
                    try {
                        Log.d(TAG, "Attempting to show AdMob MREC")
                        val mrecView = com.mzgs.helper.admob.AdMobMRECView(activity)
                        mrecView.loadMREC(
                            adUnitId = AdMobMediationManager.getConfig()?.bannerAdUnitId ?: "",
                            onAdLoaded = {
                                Log.d(TAG, "AdMob MREC loaded successfully")
                                container.removeAllViews()
                                container.addView(mrecView)
                            },
                            onAdFailedToLoad = { error ->
                                Log.e(TAG, "AdMob MREC failed: ${error.message}")
                            }
                        )
                        return true
                    } catch (e: Exception) {
                        Log.e(TAG, "Error showing AdMob MREC: ${e.message}")
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown ad network for MREC: $network")
                }
            }
        }
        
        Log.d(TAG, "Failed to show MREC from any network")
        return false
    }
    
    @JvmStatic
    fun showAppOpenAd(): Boolean {
        val context = applicationContext ?: return false
        val adsOrder = getAdsOrder()
        
        Log.d(TAG, "Attempting to show app open ad with order: $adsOrder")
        
        for (network in adsOrder) {
            when (network.lowercase()) {
                APPLOVIN_MAX -> {
                    val appOpenManager = com.mzgs.helper.applovin.AppLovinAppOpenAdManager.getInstance()
                    if (appOpenManager != null) {
                        Log.d(TAG, "Attempting to show AppLovin MAX app open ad")
                        appOpenManager.showAdIfAvailable()
                        return true
                    }
                    Log.d(TAG, "AppLovin MAX app open ad manager not initialized, trying next")
                }
                ADMOB -> {
                    val appOpenManager = com.mzgs.helper.admob.AppOpenAdManager.getInstance()
                    if (appOpenManager != null && context is Activity) {
                        Log.d(TAG, "Attempting to show AdMob app open ad")
                        appOpenManager.showAdIfAvailable(context)
                        return true
                    }
                    Log.d(TAG, "AdMob app open ad not ready or context not an activity, trying next")
                }
                else -> {
                    Log.w(TAG, "Unknown ad network: $network")
                }
            }
        }
        
        Log.d(TAG, "No app open ads ready from any network")
        return false
    }
    
    enum class BannerSize {
        ADAPTIVE,
        BANNER,
        LARGE_BANNER,
        MEDIUM_RECTANGLE,
        FULL_BANNER,
        LEADERBOARD
    }
}