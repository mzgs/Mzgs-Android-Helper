package com.mzgs.helper.admob

import android.content.Context
import android.util.AttributeSet
import android.util.Log
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
    }
    
    private var adView: AdView? = null
    private var adSize: AdSize = AdSize.BANNER
    private var adUnitId: String? = null
    private var isAdaptive: Boolean = false
    
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
        if (!AdMobMediationManager.getInstance(context).canShowAds()) {
            Log.w(TAG, "Cannot show ads - consent not obtained")
            return
        }
        
        this.adUnitId = adUnitId
        this.isAdaptive = isAdaptive
        
        removeAllViews()
        adView?.destroy()
        
        adView = AdView(context).apply {
            this.adUnitId = adUnitId
            
            if (isAdaptive) {
                val display = context.resources.displayMetrics
                val adWidth = (display.widthPixels / display.density).toInt()
                this@AdMobBannerView.adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)
                this.setAdSize(this@AdMobBannerView.adSize)
            } else {
                this@AdMobBannerView.adSize = adSize
                this.setAdSize(adSize)
            }
            
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner ad loaded")
                    onAdLoaded()
                }
                
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Banner failed to load: ${error.message}")
                    onAdFailedToLoad(error)
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
        
        val adRequest = AdRequest.Builder().build()
        adView?.loadAd(adRequest)
    }
    
    fun pause() {
        adView?.pause()
    }
    
    fun resume() {
        adView?.resume()
    }
    
    fun destroy() {
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
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        destroy()
    }
}