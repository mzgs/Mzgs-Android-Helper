package com.mzgs.helper.admob

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

class AdMobNativeAdHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "AdMobNativeAd"
    }
    
    private var nativeAd: NativeAd? = null
    private var adLoader: AdLoader? = null
    
    fun loadNativeAd(
        adUnitId: String,
        onAdLoaded: (NativeAd) -> Unit = {},
        onAdFailedToLoad: (LoadAdError) -> Unit = {},
        onAdClicked: () -> Unit = {},
        mediaAspectRatio: Int = NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE,
        requestMultipleImages: Boolean = false,
        returnUrlsForImageAssets: Boolean = false,
        requestCustomMuteThisAd: Boolean = false,
        shouldRequestMultipleAds: Boolean = false,
        numberOfAds: Int = 1
    ) {
        if (!AdMobMediationManager.getInstance(context).canShowAds()) {
            Log.w(TAG, "Cannot show ads - consent not obtained")
            return
        }
        
        val builder = AdLoader.Builder(context, adUnitId)
        
        builder.forNativeAd { ad ->
            nativeAd?.destroy()
            nativeAd = ad
            Log.d(TAG, "Native ad loaded")
            onAdLoaded(ad)
        }
        
        val adOptions = NativeAdOptions.Builder()
            .setMediaAspectRatio(mediaAspectRatio)
            .setRequestMultipleImages(requestMultipleImages)
            .setReturnUrlsForImageAssets(returnUrlsForImageAssets)
            .setRequestCustomMuteThisAd(requestCustomMuteThisAd)
            .build()
        
        builder.withNativeAdOptions(adOptions)
        
        builder.withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.e(TAG, "Failed to load native ad: ${error.message}")
                onAdFailedToLoad(error)
            }
            
            override fun onAdClicked() {
                Log.d(TAG, "Native ad clicked")
                onAdClicked()
            }
            
            override fun onAdImpression() {
                Log.d(TAG, "Native ad impression")
            }
        })
        
        adLoader = builder.build()
        
        if (shouldRequestMultipleAds && numberOfAds > 1) {
            adLoader?.loadAds(AdRequest.Builder().build(), numberOfAds)
        } else {
            adLoader?.loadAd(AdRequest.Builder().build())
        }
    }
    
    fun populateNativeAdView(
        nativeAd: NativeAd,
        adView: NativeAdView
    ) {
        adView.headlineView?.let { view ->
            (view as? TextView)?.text = nativeAd.headline
        }
        
        adView.bodyView?.let { view ->
            if (nativeAd.body == null) {
                view.visibility = View.INVISIBLE
            } else {
                view.visibility = View.VISIBLE
                (view as? TextView)?.text = nativeAd.body
            }
        }
        
        adView.callToActionView?.let { view ->
            if (nativeAd.callToAction == null) {
                view.visibility = View.INVISIBLE
            } else {
                view.visibility = View.VISIBLE
                (view as? Button)?.text = nativeAd.callToAction
            }
        }
        
        adView.iconView?.let { view ->
            if (nativeAd.icon == null) {
                view.visibility = View.GONE
            } else {
                (view as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
                view.visibility = View.VISIBLE
            }
        }
        
        adView.priceView?.let { view ->
            if (nativeAd.price == null) {
                view.visibility = View.INVISIBLE
            } else {
                view.visibility = View.VISIBLE
                (view as? TextView)?.text = nativeAd.price
            }
        }
        
        adView.starRatingView?.let { view ->
            if (nativeAd.starRating == null) {
                view.visibility = View.INVISIBLE
            } else {
                (view as? android.widget.RatingBar)?.rating = nativeAd.starRating!!.toFloat()
                view.visibility = View.VISIBLE
            }
        }
        
        adView.storeView?.let { view ->
            if (nativeAd.store == null) {
                view.visibility = View.INVISIBLE
            } else {
                view.visibility = View.VISIBLE
                (view as? TextView)?.text = nativeAd.store
            }
        }
        
        adView.advertiserView?.let { view ->
            if (nativeAd.advertiser == null) {
                view.visibility = View.INVISIBLE
            } else {
                view.visibility = View.VISIBLE
                (view as? TextView)?.text = nativeAd.advertiser
            }
        }
        
        adView.setNativeAd(nativeAd)
    }
    
    fun createSimpleNativeAdView(
        layoutResId: Int,
        container: FrameLayout,
        nativeAd: NativeAd,
        headlineViewId: Int? = null,
        bodyViewId: Int? = null,
        callToActionViewId: Int? = null,
        iconViewId: Int? = null,
        mediaViewId: Int? = null,
        priceViewId: Int? = null,
        starRatingViewId: Int? = null,
        storeViewId: Int? = null,
        advertiserViewId: Int? = null
    ): NativeAdView {
        val adView = LayoutInflater.from(context)
            .inflate(layoutResId, null) as NativeAdView
        
        headlineViewId?.let { adView.headlineView = adView.findViewById(it) }
        bodyViewId?.let { adView.bodyView = adView.findViewById(it) }
        callToActionViewId?.let { adView.callToActionView = adView.findViewById(it) }
        iconViewId?.let { adView.iconView = adView.findViewById(it) }
        mediaViewId?.let { adView.mediaView = adView.findViewById<MediaView>(it) }
        priceViewId?.let { adView.priceView = adView.findViewById(it) }
        starRatingViewId?.let { adView.starRatingView = adView.findViewById(it) }
        storeViewId?.let { adView.storeView = adView.findViewById(it) }
        advertiserViewId?.let { adView.advertiserView = adView.findViewById(it) }
        
        populateNativeAdView(nativeAd, adView)
        
        container.removeAllViews()
        container.addView(adView)
        
        return adView
    }
    
    fun destroy() {
        nativeAd?.destroy()
        nativeAd = null
        adLoader = null
    }
    
    fun isAdLoaded(): Boolean = nativeAd != null
    
    fun getCurrentNativeAd(): NativeAd? = nativeAd
}