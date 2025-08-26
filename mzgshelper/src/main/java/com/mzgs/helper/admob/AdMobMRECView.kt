package com.mzgs.helper.admob

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.ads.*

/**
 * MREC (Medium Rectangle) Ad View
 * Standard size: 300x250 dp
 * Can be placed inline with content or in dedicated ad spaces
 */
class AdMobMRECView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "AdMobMRECView"
        const val MREC_WIDTH = 300
        const val MREC_HEIGHT = 250
    }
    
    private var adView: AdView? = null
    private var adUnitId: String? = null
    
    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    /**
     * Load MREC ad
     * @param adUnitId Your MREC ad unit ID
     * @param onAdLoaded Callback when ad loads successfully
     * @param onAdFailedToLoad Callback when ad fails to load
     * @param onAdOpened Callback when ad is opened
     * @param onAdClosed Callback when ad is closed
     * @param onAdClicked Callback when ad is clicked
     */
    fun loadMREC(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        onAdOpened: () -> Unit = {},
        onAdClosed: () -> Unit = {},
        onAdClicked: () -> Unit = {}
    ) {
        if (!AdMobMediationManager.getInstance(context).canShowAds()) {
            Log.w(TAG, "Cannot show ads - consent not obtained")
            return
        }
        
        this.adUnitId = adUnitId
        
        // Clear any existing ad
        removeAllViews()
        adView?.destroy()
        
        // Create new ad view
        adView = AdView(context).apply {
            this.adUnitId = adUnitId
            setAdSize(AdSize.MEDIUM_RECTANGLE) // 300x250
            
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "MREC ad loaded")
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "MREC failed to load: ${error.message}")
                    onAdFailedToLoad(error)
                }
                
                override fun onAdOpened() {
                    Log.d(TAG, "MREC ad opened")
                    onAdOpened()
                }
                
                override fun onAdClosed() {
                    Log.d(TAG, "MREC ad closed")
                    onAdClosed()
                }
                
                override fun onAdClicked() {
                    Log.d(TAG, "MREC ad clicked")
                    onAdClicked()
                }
                
                override fun onAdImpression() {
                    Log.d(TAG, "MREC ad impression")
                }
            }
        }
        
        // Center the MREC in its container
        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
        
        addView(adView, params)
        
        // Load the ad
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }
    
    /**
     * Check if MREC is loaded
     */
    fun isLoaded(): Boolean {
        return adView != null
    }
    
    /**
     * Pause the MREC ad
     */
    fun pause() {
        adView?.pause()
    }
    
    /**
     * Resume the MREC ad
     */
    fun resume() {
        adView?.resume()
    }
    
    /**
     * Destroy the MREC ad
     */
    fun destroy() {
        adView?.destroy()
        adView = null
    }
    
    /**
     * Refresh the MREC ad
     */
    fun refreshAd() {
        adUnitId?.let { id ->
            loadMREC(adUnitId = id)
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }
}