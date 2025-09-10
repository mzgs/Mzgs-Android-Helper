package com.mzgs.helper.applovin

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxAdFormat
import com.applovin.mediation.MaxAdListener
import com.applovin.mediation.MaxAdViewAdListener
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView
import com.applovin.sdk.AppLovinSdkUtils
import com.mzgs.helper.analytics.FirebaseAnalyticsManager

class AppLovinBannerHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "AppLovinBannerHelper"
    }
    
    enum class BannerType {
        BANNER,           // 320x50
        MREC,            // 300x250
        LEADER           // 728x90 (tablet)
    }
    
    fun createBannerView(
        adUnitId: String,
        bannerType: BannerType,
        container: FrameLayout,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (MaxError) -> Unit = {},
        onAdClicked: () -> Unit = {},
        onAdExpanded: () -> Unit = {},
        onAdCollapsed: () -> Unit = {}
    ): MaxAdView? {
        val config = AppLovinMediationManager.getInstance(context).getConfig()
        if (config != null && !config.shouldShowBanners(context)) {
            Log.d(TAG, "Banner ads disabled in debug mode")
            return null
        }
        
        container.removeAllViews()
        
        @Suppress("DEPRECATION") // Using the recommended approach for SDK 13.x
        val adView = when (bannerType) {
            BannerType.BANNER -> MaxAdView(adUnitId, MaxAdFormat.BANNER, context)
            BannerType.MREC -> MaxAdView(adUnitId, MaxAdFormat.MREC, context)
            BannerType.LEADER -> MaxAdView(adUnitId, MaxAdFormat.LEADER, context)
        }
        
        adView.apply {
            setListener(object : MaxAdViewAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Log.d(TAG, "$bannerType ad loaded")
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "banner_$bannerType",
                        adUnitId = adUnitId,
                        adNetwork = "applovin_max",
                        success = true
                    )
                    onAdLoaded()
                }
                
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "$bannerType failed to load: ${error.message}")
                    FirebaseAnalyticsManager.logAdLoad(
                        adType = "banner_$bannerType",
                        adUnitId = adUnitId,
                        adNetwork = "applovin_max",
                        success = false,
                        errorMessage = error.message,
                        errorCode = error.code
                    )
                    onAdFailedToLoad(error)
                }
                
                override fun onAdDisplayed(ad: MaxAd) {
                    Log.d(TAG, "$bannerType ad displayed")
                }
                
                override fun onAdHidden(ad: MaxAd) {
                    Log.d(TAG, "$bannerType ad hidden")
                }
                
                override fun onAdClicked(ad: MaxAd) {
                    Log.d(TAG, "$bannerType ad clicked")
                    FirebaseAnalyticsManager.logAdClicked(
                        adType = "banner_$bannerType",
                        adUnitId = adUnitId,
                        adNetwork = "applovin"
                    )
                    onAdClicked()
                }
                
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.e(TAG, "$bannerType display failed: ${error.message}")
                }
                
                override fun onAdExpanded(ad: MaxAd) {
                    Log.d(TAG, "$bannerType ad expanded")
                    onAdExpanded()
                }
                
                override fun onAdCollapsed(ad: MaxAd) {
                    Log.d(TAG, "$bannerType ad collapsed")
                    onAdCollapsed()
                }
            })
            
            // Adaptive banner configuration is now handled by the SDK automatically
            
            if (bannerType == BannerType.BANNER) {
                setBackgroundColor(Color.TRANSPARENT)
            }
        }
        
        val params = FrameLayout.LayoutParams(
            when (bannerType) {
                BannerType.BANNER -> {
                    val widthPx = AppLovinSdkUtils.dpToPx(context, 320)
                    widthPx
                }
                BannerType.MREC -> {
                    val widthPx = AppLovinSdkUtils.dpToPx(context, 300)
                    widthPx
                }
                BannerType.LEADER -> {
                    val widthPx = AppLovinSdkUtils.dpToPx(context, 728)
                    widthPx
                }
            },
            when (bannerType) {
                BannerType.BANNER -> {
                    val heightPx = AppLovinSdkUtils.dpToPx(context, 50)
                    heightPx
                }
                BannerType.MREC -> {
                    val heightPx = AppLovinSdkUtils.dpToPx(context, 250)
                    heightPx
                }
                BannerType.LEADER -> {
                    val heightPx = AppLovinSdkUtils.dpToPx(context, 90)
                    heightPx
                }
            }
        ).apply {
            gravity = when (bannerType) {
                BannerType.MREC -> Gravity.CENTER
                BannerType.LEADER -> Gravity.CENTER_HORIZONTAL
                else -> Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            }
        }
        
        container.addView(adView, params)
        
        adView.loadAd()
        
        adView.startAutoRefresh()
        
        return adView
    }
    
    fun createAdaptiveBanner(
        adUnitId: String,
        container: FrameLayout,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (MaxError) -> Unit = {},
        onAdClicked: () -> Unit = {}
    ): MaxAdView? {
        val config = AppLovinMediationManager.getInstance(context).getConfig()
        if (config != null && !config.shouldShowBanners(context)) {
            Log.d(TAG, "Banner ads disabled in debug mode")
            return null
        }
        
        container.removeAllViews()
        
        @Suppress("DEPRECATION") // Using the recommended approach for SDK 13.x
        val adView = MaxAdView(adUnitId, MaxAdFormat.BANNER, context).apply {
            setListener(object : MaxAdViewAdListener {
                override fun onAdLoaded(ad: MaxAd) {
                    Log.d(TAG, "Adaptive banner ad loaded")
                    // Note: Adaptive size handling removed as it's not available in current SDK
                    onAdLoaded()
                }
                
                override fun onAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "Adaptive banner failed to load: ${error.message}")
                    onAdFailedToLoad(error)
                }
                
                override fun onAdDisplayed(ad: MaxAd) {
                    Log.d(TAG, "Adaptive banner ad displayed")
                }
                
                override fun onAdHidden(ad: MaxAd) {
                    Log.d(TAG, "Adaptive banner ad hidden")
                }
                
                override fun onAdClicked(ad: MaxAd) {
                    Log.d(TAG, "Adaptive banner ad clicked")
                    onAdClicked()
                }
                
                override fun onAdDisplayFailed(ad: MaxAd, error: MaxError) {
                    Log.e(TAG, "Adaptive banner display failed: ${error.message}")
                }
                
                override fun onAdExpanded(ad: MaxAd) {
                    Log.d(TAG, "Adaptive banner ad expanded")
                }
                
                override fun onAdCollapsed(ad: MaxAd) {
                    Log.d(TAG, "Adaptive banner ad collapsed")
                }
            })
            
            // Adaptive banner configuration is now handled by the SDK automatically
            setBackgroundColor(Color.TRANSPARENT)
        }
        
        val screenWidth = context.resources.displayMetrics.widthPixels
        val params = FrameLayout.LayoutParams(screenWidth, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        
        container.addView(adView, params)
        
        adView.loadAd()
        
        adView.startAutoRefresh()
        
        return adView
    }
    
    fun createStandardBanner(
        adUnitId: String,
        container: FrameLayout,
        onAdLoaded: () -> Unit = {}
    ): MaxAdView? {
        return createBannerView(
            adUnitId = adUnitId,
            bannerType = BannerType.BANNER,
            container = container,
            onAdLoaded = onAdLoaded
        )
    }
    
    fun createMREC(
        adUnitId: String,
        container: FrameLayout,
        onAdLoaded: () -> Unit = {}
    ): MaxAdView? {
        return createBannerView(
            adUnitId = adUnitId,
            bannerType = BannerType.MREC,
            container = container,
            onAdLoaded = onAdLoaded
        )
    }
    
    fun createLeaderBanner(
        adUnitId: String,
        container: FrameLayout,
        onAdLoaded: () -> Unit = {}
    ): MaxAdView? {
        return createBannerView(
            adUnitId = adUnitId,
            bannerType = BannerType.LEADER,
            container = container,
            onAdLoaded = onAdLoaded
        )
    }
    
    fun stopAutoRefresh(adView: MaxAdView) {
        adView.stopAutoRefresh()
        Log.d(TAG, "Auto-refresh stopped")
    }
    
    fun startAutoRefresh(adView: MaxAdView) {
        adView.startAutoRefresh()
        Log.d(TAG, "Auto-refresh started")
    }
    
    fun setPlacement(adView: MaxAdView, placement: String) {
        adView.placement = placement
        Log.d(TAG, "Placement set to: $placement")
    }
    
    fun setCustomData(adView: MaxAdView, customData: String) {
        // Note: customData property may not be directly available
        adView.setLocalExtraParameter("custom_data", customData)
        Log.d(TAG, "Custom data set")
    }
    
    fun getBannerSizeInfo(bannerType: BannerType): String {
        return when (bannerType) {
            BannerType.BANNER -> "320x50 dp"
            BannerType.MREC -> "300x250 dp (MREC)"
            BannerType.LEADER -> "728x90 dp (Tablet)"
        }
    }
    
    fun destroyBanner(adView: MaxAdView?) {
        adView?.let {
            it.stopAutoRefresh()
            it.destroy()
            Log.d(TAG, "Banner destroyed")
        }
    }
}