package com.mzgs.helper.admob

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.*

/**
 * Helper class for creating different banner ad formats
 */
class BannerAdHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "BannerAdHelper"
    }
    
    /**
     * Banner types available
     */
    enum class BannerType {
        BANNER,              // 320x50
        LARGE_BANNER,        // 320x100
        MEDIUM_RECTANGLE,    // 300x250 (MREC)
        FULL_BANNER,         // 468x60
        LEADERBOARD,         // 728x90
        ADAPTIVE_BANNER,     // Adaptive width x adaptive height
        INLINE_ADAPTIVE,     // For scrollable content
        ANCHORED_ADAPTIVE    // For anchored positions (top/bottom)
    }
    
    /**
     * Create a banner ad view with specified type
     */
    fun createBannerView(
        adUnitId: String,
        bannerType: BannerType,
        container: FrameLayout,
        maxHeight: Int = 0, // For inline adaptive banners
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        onAdClicked: () -> Unit = {}
    ): AdView? {
        if (!AdMobMediationManager.getInstance(context).canShowAds()) {
            Log.w(TAG, "Cannot show ads - consent not obtained")
            return null
        }
        
        // Clear any existing ads in container
        container.removeAllViews()
        
        val adView = AdView(context).apply {
            this.adUnitId = adUnitId
            
            // Set ad size based on type
            setAdSize(getAdSize(bannerType, container.width, maxHeight))
            
            // Set up callbacks
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "$bannerType ad loaded")
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "$bannerType failed to load: ${error.message}")
                    onAdFailedToLoad(error)
                    
                    // Auto-retry loading the banner after a delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "Auto-retrying banner ad load after failure")
                        val retryRequest = AdRequest.Builder().build()
                        loadAd(retryRequest)
                    }, 5000) // Retry after 5 seconds
                }
                
                override fun onAdClicked() {
                    Log.d(TAG, "$bannerType ad clicked")
                    onAdClicked()
                }
                
                override fun onAdOpened() {
                    Log.d(TAG, "$bannerType ad opened")
                }
                
                override fun onAdClosed() {
                    Log.d(TAG, "$bannerType ad closed")
                }
                
                override fun onAdImpression() {
                    Log.d(TAG, "$bannerType ad impression")
                }
            }
        }
        
        // Add to container with appropriate layout params
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = when (bannerType) {
                BannerType.LEADERBOARD -> Gravity.CENTER_HORIZONTAL
                BannerType.MEDIUM_RECTANGLE -> Gravity.CENTER
                else -> Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            }
        }
        
        container.addView(adView, params)
        
        // Load the ad
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        
        return adView
    }
    
    /**
     * Get AdSize based on banner type
     */
    private fun getAdSize(bannerType: BannerType, containerWidth: Int, maxHeight: Int): AdSize {
        return when (bannerType) {
            BannerType.BANNER -> AdSize.BANNER
            BannerType.LARGE_BANNER -> AdSize.LARGE_BANNER
            BannerType.MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE
            BannerType.FULL_BANNER -> AdSize.FULL_BANNER
            BannerType.LEADERBOARD -> AdSize.LEADERBOARD
            BannerType.ADAPTIVE_BANNER -> {
                val display = context.resources.displayMetrics
                // Use screen width if container width is 0 or too small
                val screenWidth = display.widthPixels
                val adWidth = if (containerWidth > 0) {
                    (containerWidth / display.density).toInt()
                } else {
                    (screenWidth / display.density).toInt()
                }
                // Ensure minimum width of 320dp
                val finalWidth = adWidth.coerceAtLeast(320)
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, finalWidth)
            }
            BannerType.INLINE_ADAPTIVE -> {
                val display = context.resources.displayMetrics
                val screenWidth = display.widthPixels
                val adWidth = if (containerWidth > 0) {
                    (containerWidth / display.density).toInt()
                } else {
                    (screenWidth / display.density).toInt()
                }
                // Ensure minimum width of 320dp
                val finalWidth = adWidth.coerceAtLeast(320)
                
                if (maxHeight > 0) {
                    AdSize.getInlineAdaptiveBannerAdSize(finalWidth, maxHeight)
                } else {
                    // Use a default max height if not specified
                    AdSize.getInlineAdaptiveBannerAdSize(finalWidth, 250)
                }
            }
            BannerType.ANCHORED_ADAPTIVE -> {
                val display = context.resources.displayMetrics
                val screenWidth = display.widthPixels
                val adWidth = if (containerWidth > 0) {
                    (containerWidth / display.density).toInt()
                } else {
                    (screenWidth / display.density).toInt()
                }
                // Ensure minimum width of 320dp
                val finalWidth = adWidth.coerceAtLeast(320)
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, finalWidth)
            }
        }
    }
    
    /**
     * Create standard banner (320x50)
     */
    fun createStandardBanner(
        adUnitId: String,
        container: FrameLayout,
        onAdLoaded: () -> Unit = {}
    ): AdView? {
        return createBannerView(
            adUnitId = adUnitId,
            bannerType = BannerType.BANNER,
            container = container,
            onAdLoaded = onAdLoaded
        )
    }
    
    /**
     * Create MREC (300x250)
     */
    fun createMREC(
        adUnitId: String,
        container: FrameLayout,
        onAdLoaded: () -> Unit = {}
    ): AdView? {
        return createBannerView(
            adUnitId = adUnitId,
            bannerType = BannerType.MEDIUM_RECTANGLE,
            container = container,
            onAdLoaded = onAdLoaded
        )
    }
    
    /**
     * Create adaptive banner
     */
    fun createAdaptiveBanner(
        adUnitId: String,
        container: FrameLayout,
        onAdLoaded: () -> Unit = {}
    ): AdView? {
        return createBannerView(
            adUnitId = adUnitId,
            bannerType = BannerType.ADAPTIVE_BANNER,
            container = container,
            onAdLoaded = onAdLoaded
        )
    }
    
    /**
     * Create inline adaptive banner for scrollable content
     */
    fun createInlineAdaptiveBanner(
        adUnitId: String,
        container: FrameLayout,
        maxHeight: Int = 0,
        onAdLoaded: () -> Unit = {}
    ): AdView? {
        return createBannerView(
            adUnitId = adUnitId,
            bannerType = BannerType.INLINE_ADAPTIVE,
            container = container,
            maxHeight = maxHeight,
            onAdLoaded = onAdLoaded
        )
    }
    
    /**
     * Calculate proper ad width for adaptive banners
     */
    fun calculateAdaptiveWidth(context: Context): Int {
        val display = context.resources.displayMetrics
        val screenWidth = display.widthPixels
        val adWidth = (screenWidth / display.density).toInt()
        // AdMob requires minimum 320dp width for adaptive banners
        return adWidth.coerceAtLeast(320)
    }
    
    /**
     * Get size information for a banner type
     */
    fun getBannerSizeInfo(bannerType: BannerType): String {
        return when (bannerType) {
            BannerType.BANNER -> "320x50 dp"
            BannerType.LARGE_BANNER -> "320x100 dp"
            BannerType.MEDIUM_RECTANGLE -> "300x250 dp (MREC)"
            BannerType.FULL_BANNER -> "468x60 dp"
            BannerType.LEADERBOARD -> "728x90 dp (Tablet)"
            BannerType.ADAPTIVE_BANNER -> "Width: Flexible, Height: Adaptive"
            BannerType.INLINE_ADAPTIVE -> "Inline Adaptive (for scrollable content)"
            BannerType.ANCHORED_ADAPTIVE -> "Anchored Adaptive (for fixed positions)"
        }
    }
}