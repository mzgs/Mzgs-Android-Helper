package com.mzgs.helper.admob

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.*

class AdMobBannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "AdMobBannerView"
        private const val AUTO_REFRESH_INTERVAL_MS = 60000L // 60 seconds
    }
    
    private var adView: AdView? = null
    private var adSize: AdSize = AdSize.BANNER
    private var adUnitId: String? = null
    private var isAdaptive: Boolean = false
    private var autoRefreshHandler: Handler? = null
    private var autoRefreshRunnable: Runnable? = null
    private var isAutoRefreshEnabled: Boolean = true
    private var lastAdLoadedTime: Long = 0
    
    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    fun loadBanner(
        adUnitId: String,
        adSize: AdSize = AdSize.BANNER,
        isAdaptive: Boolean = true,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        onAdOpened: () -> Unit = {},
        onAdClosed: () -> Unit = {},
        onAdClicked: () -> Unit = {}
    ) {
        val adManager = AdMobMediationManager.getInstance(context)
        
        // Check if we can show any type of ads (personalized or non-personalized)
        if (!adManager.canShowAds() && !adManager.canShowNonPersonalizedAds()) {
            Log.w(TAG, "Cannot show any type of ads")
            return
        }
        
        // Check debug flag from config
        adManager.getConfig()?.let { config ->
            if (!config.shouldShowBanners(context)) {
                Log.d(TAG, "Banner ads disabled in debug mode")
                visibility = View.GONE
                return
            }
        }
        
        this.adUnitId = adUnitId
        this.isAdaptive = isAdaptive
        
        removeAllViews()
        adView?.destroy()
        
        adView = AdView(context).apply {
            this.adUnitId = adUnitId
            
            if (isAdaptive) {
                // Calculate adaptive ad size properly
                val display = context.resources.displayMetrics
                val screenWidth = display.widthPixels
                // Calculate width in dp
                val adWidth = (screenWidth / display.density).toInt()
                // Ensure minimum width of 320dp for adaptive banners
                val finalWidth = adWidth.coerceAtLeast(320)
                
                this@AdMobBannerView.adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, finalWidth)
                this.setAdSize(this@AdMobBannerView.adSize)
            } else {
                this@AdMobBannerView.adSize = adSize
                this.setAdSize(adSize)
            }
            
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner ad loaded")
                    lastAdLoadedTime = System.currentTimeMillis()
                    onAdLoaded()
                    
                    // Start auto-refresh timer
                    if (isAutoRefreshEnabled) {
                        startAutoRefresh()
                    }
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Banner failed to load: ${error.message}")
                    onAdFailedToLoad(error)
                    
                    // Auto-retry loading the banner after a delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "Auto-retrying banner ad load after failure")
                        val retryRequest = AdMobMediationManager.getInstance(context).createAdRequest()
                        loadAd(retryRequest)
                    }, 5000) // Retry after 5 seconds
                }
                
                override fun onAdOpened() {
                    Log.d(TAG, "Banner ad opened")
                    onAdOpened()
                }
                
                override fun onAdClosed() {
                    Log.d(TAG, "Banner ad closed")
                    onAdClosed()
                }
                
                override fun onAdClicked() {
                    Log.d(TAG, "Banner ad clicked")
                    onAdClicked()
                }
                
                override fun onAdImpression() {
                    Log.d(TAG, "Banner ad impression")
                }
            }
        }
        
        addView(adView)
        
        val adRequest = adManager.createAdRequest()
        adView?.loadAd(adRequest)
    }
    
    fun pause() {
        adView?.pause()
    }
    
    fun resume() {
        adView?.resume()
    }
    
    fun destroy() {
        stopAutoRefresh()
        adView?.destroy()
        adView = null
    }
    
    fun refreshAd() {
        adUnitId?.let { id ->
            loadBanner(
                adUnitId = id,
                adSize = adSize,
                isAdaptive = isAdaptive
            )
        }
    }
    
    private fun startAutoRefresh() {
        // Cancel any existing refresh timer
        stopAutoRefresh()
        
        // Get refresh interval from config or use default
        val config = AdMobMediationManager.getInstance(context).getConfig()
        val refreshInterval = config?.bannerAutoRefreshSeconds?.times(1000L) ?: AUTO_REFRESH_INTERVAL_MS
        
        if (refreshInterval <= 0) {
            Log.d(TAG, "Auto-refresh disabled (interval: $refreshInterval)")
            return
        }
        
        autoRefreshHandler = Handler(Looper.getMainLooper())
        autoRefreshRunnable = Runnable {
            if (adView != null && isAutoRefreshEnabled) {
                val timeSinceLastLoad = System.currentTimeMillis() - lastAdLoadedTime
                if (timeSinceLastLoad >= refreshInterval) {
                    Log.d(TAG, "Auto-refreshing banner ad after ${refreshInterval/1000} seconds")
                    refreshAd()
                }
            }
        }
        
        autoRefreshHandler?.postDelayed(autoRefreshRunnable!!, refreshInterval)
        Log.d(TAG, "Auto-refresh scheduled for ${refreshInterval/1000} seconds")
    }
    
    private fun stopAutoRefresh() {
        autoRefreshRunnable?.let { runnable ->
            autoRefreshHandler?.removeCallbacks(runnable)
        }
        autoRefreshHandler = null
        autoRefreshRunnable = null
        Log.d(TAG, "Auto-refresh stopped")
    }
    
    fun setAutoRefreshEnabled(enabled: Boolean) {
        isAutoRefreshEnabled = enabled
        if (enabled && adView != null) {
            startAutoRefresh()
        } else {
            stopAutoRefresh()
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }
    
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isAutoRefreshEnabled && adView != null && lastAdLoadedTime > 0) {
            startAutoRefresh()
        }
    }
}