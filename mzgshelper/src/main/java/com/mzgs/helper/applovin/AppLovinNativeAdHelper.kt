package com.mzgs.helper.applovin

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.applovin.mediation.MaxAd
import com.applovin.mediation.MaxError
import com.applovin.mediation.nativeAds.MaxNativeAdListener
import com.applovin.mediation.nativeAds.MaxNativeAdLoader
import com.applovin.mediation.nativeAds.MaxNativeAdView
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder
import com.mzgs.helper.R
import com.mzgs.helper.analytics.FirebaseAnalyticsManager

class AppLovinNativeAdHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "AppLovinNativeHelper"
    }
    
    private var nativeAdLoader: MaxNativeAdLoader? = null
    private var loadedNativeAd: MaxAd? = null
    private var nativeAdView: MaxNativeAdView? = null
    
    fun loadNativeAd(
        adUnitId: String,
        onAdLoaded: () -> Unit = {},
        onAdFailedToLoad: (MaxError) -> Unit = {},
        onAdClicked: () -> Unit = {}
    ) {
        val config = AppLovinMediationManager.getInstance(context).getConfig()
        if (config != null && !config.shouldShowNativeAds(context)) {
            Log.d(TAG, "Native ads disabled in debug mode")
            return
        }
        
        nativeAdLoader = MaxNativeAdLoader(adUnitId, context)
        nativeAdLoader?.setNativeAdListener(object : MaxNativeAdListener() {
            override fun onNativeAdLoaded(nativeAdView: MaxNativeAdView?, ad: MaxAd) {
                if (loadedNativeAd != null) {
                    nativeAdLoader?.destroy(loadedNativeAd)
                }
                
                loadedNativeAd = ad
                this@AppLovinNativeAdHelper.nativeAdView = nativeAdView
                
                Log.d(TAG, "Native ad loaded")
                FirebaseAnalyticsManager.logAdLoadSuccess(
                    adType = "native",
                    adUnitId = adUnitId,
                    adNetwork = "applovin"
                )
                onAdLoaded()
            }
            
            override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                Log.e(TAG, "Failed to load native ad: ${error.message}")
                FirebaseAnalyticsManager.logAdLoadFailed(
                    adType = "native",
                    adUnitId = adUnitId,
                    errorMessage = error.message,
                    errorCode = error.code,
                    adNetwork = "applovin"
                )
                onAdFailedToLoad(error)
            }
            
            override fun onNativeAdClicked(ad: MaxAd) {
                Log.d(TAG, "Native ad clicked")
                FirebaseAnalyticsManager.logAdClicked(
                    adType = "native",
                    adUnitId = adUnitId,
                    adNetwork = "applovin"
                )
                onAdClicked()
            }
            
            override fun onNativeAdExpired(ad: MaxAd) {
                Log.d(TAG, "Native ad expired")
                loadNativeAd(adUnitId, onAdLoaded, onAdFailedToLoad, onAdClicked)
            }
        })
        
        nativeAdLoader?.loadAd()
    }
    
    fun createNativeAdView(): MaxNativeAdView? {
        val binder = MaxNativeAdViewBinder.Builder(R.layout.applovin_native_ad_layout)
            .setTitleTextViewId(R.id.applovin_native_title_text_view)
            .setBodyTextViewId(R.id.applovin_native_body_text_view)
            .setAdvertiserTextViewId(R.id.applovin_native_advertiser_text_view)
            .setIconImageViewId(R.id.applovin_native_icon_image_view)
            .setMediaContentViewGroupId(R.id.applovin_native_media_content_view)
            .setOptionsContentViewGroupId(R.id.applovin_native_options_view)
            .setCallToActionButtonId(R.id.applovin_native_call_to_action_button)
            .setStarRatingContentViewGroupId(R.id.applovin_native_star_rating_view)
            .build()
        
        return MaxNativeAdView(binder, context)
    }
    
    fun createManualNativeAdView(
        titleViewId: Int,
        bodyViewId: Int,
        iconViewId: Int,
        mediaViewId: Int,
        callToActionViewId: Int,
        advertiserViewId: Int? = null,
        storeViewId: Int? = null,
        priceViewId: Int? = null,
        starRatingViewId: Int? = null,
        optionsViewId: Int? = null,
        layoutResourceId: Int
    ): MaxNativeAdView {
        val builder = MaxNativeAdViewBinder.Builder(layoutResourceId)
            .setTitleTextViewId(titleViewId)
            .setBodyTextViewId(bodyViewId)
            .setIconImageViewId(iconViewId)
            .setMediaContentViewGroupId(mediaViewId)
            .setCallToActionButtonId(callToActionViewId)
        
        advertiserViewId?.let { builder.setAdvertiserTextViewId(it) }
        // Note: Store and Price view setters are not available in current SDK version
        starRatingViewId?.let { builder.setStarRatingContentViewGroupId(it) }
        optionsViewId?.let { builder.setOptionsContentViewGroupId(it) }
        
        return MaxNativeAdView(builder.build(), context)
    }
    
    fun showNativeAd(container: FrameLayout, adUnitId: String = ""): Boolean {
        val config = AppLovinMediationManager.getInstance(context).getConfig()
        if (config != null && !config.shouldShowNativeAds(context)) {
            Log.d(TAG, "Native ads disabled in debug mode")
            return false
        }
        
        if (loadedNativeAd == null || nativeAdView == null) {
            Log.w(TAG, "Native ad not ready")
            return false
        }
        
        container.removeAllViews()
        
        nativeAdView?.let { adView ->
            val parent = adView.parent as? ViewGroup
            parent?.removeView(adView)
            
            container.addView(adView)
            
            nativeAdLoader?.render(adView, loadedNativeAd)
            
            Log.d(TAG, "Native ad displayed")
            if (adUnitId.isNotEmpty()) {
                FirebaseAnalyticsManager.logAdImpression(
                    adType = "native",
                    adUnitId = adUnitId,
                    adNetwork = "applovin"
                )
            }
            return true
        }
        
        return false
    }
    
    fun preloadMultipleNativeAds(
        adUnitId: String,
        numberOfAds: Int = 3,
        onAdsLoaded: (List<MaxAd>) -> Unit = {},
        onAdsFailed: (MaxError) -> Unit = {}
    ) {
        val config = AppLovinMediationManager.getInstance(context).getConfig()
        if (config != null && !config.shouldShowNativeAds(context)) {
            Log.d(TAG, "Native ads disabled in debug mode")
            return
        }
        
        val loadedAds = mutableListOf<MaxAd>()
        var loadedCount = 0
        
        for (i in 0 until numberOfAds) {
            val loader = MaxNativeAdLoader(adUnitId, context)
            loader.setNativeAdListener(object : MaxNativeAdListener() {
                override fun onNativeAdLoaded(nativeAdView: MaxNativeAdView?, ad: MaxAd) {
                    loadedAds.add(ad)
                    loadedCount++
                    
                    if (loadedCount == numberOfAds) {
                        Log.d(TAG, "All $numberOfAds native ads loaded")
                        onAdsLoaded(loadedAds)
                    }
                }
                
                override fun onNativeAdLoadFailed(adUnitId: String, error: MaxError) {
                    Log.e(TAG, "Failed to preload native ad ${i + 1}: ${error.message}")
                    loadedCount++
                    
                    if (loadedCount == numberOfAds) {
                        if (loadedAds.isNotEmpty()) {
                            Log.d(TAG, "${loadedAds.size} out of $numberOfAds native ads loaded")
                            onAdsLoaded(loadedAds)
                        } else {
                            onAdsFailed(error)
                        }
                    }
                }
            })
            
            loader.loadAd()
        }
    }
    
    fun destroyNativeAd() {
        loadedNativeAd?.let {
            nativeAdLoader?.destroy(it)
            loadedNativeAd = null
            nativeAdView = null
            Log.d(TAG, "Native ad destroyed")
        }
    }
    
    fun setPlacement(placement: String) {
        nativeAdLoader?.placement = placement
        Log.d(TAG, "Native ad placement set to: $placement")
    }
    
    fun setCustomData(customData: String) {
        // Note: customData setter may not be available on the loader directly
        // It's typically set on individual ad views
        Log.d(TAG, "Native ad custom data set: $customData")
    }
    
    fun isNativeAdReady(): Boolean = loadedNativeAd != null && nativeAdView != null
}