package com.mzgs.helper.admob

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
        val adManager = AdMobMediationManager.getInstance(context)
        
        if (!adManager.canShowAds()) {
            Log.w(TAG, "Cannot show ads - consent not obtained")
            return
        }
        
        // Check debug flag from config
        adManager.getConfig()?.let { config ->
            if (!config.shouldShowNativeAds(context)) {
                Log.d(TAG, "Native ads disabled in debug mode")
                return
            }
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
        
        // Handle MediaView - ensure it's visible and properly sized
        adView.mediaView?.let { mediaView ->
            // MediaView will automatically display images or video from the ad
            // Set minimum height to meet video requirements (120dp minimum)
            if (mediaView.layoutParams != null) {
                val minHeightPx = (120 * context.resources.displayMetrics.density).toInt()
                if (mediaView.layoutParams.height < minHeightPx) {
                    mediaView.layoutParams.height = (200 * context.resources.displayMetrics.density).toInt()
                }
            }
            mediaView.visibility = View.VISIBLE
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
    
    fun createDefaultNativeAdView(nativeAd: NativeAd): NativeAdView {
        val layoutId = context.resources.getIdentifier(
            "native_ad_layout",
            "layout",
            context.packageName
        )
        
        val adView = if (layoutId != 0) {
            LayoutInflater.from(context).inflate(layoutId, null) as NativeAdView
        } else {
            // Fallback to programmatic creation if layout not found
            createProgrammaticNativeAdView(nativeAd)
        }
        
        // Set up view references
        adView.headlineView = adView.findViewById(context.resources.getIdentifier("ad_headline", "id", context.packageName))
        adView.bodyView = adView.findViewById(context.resources.getIdentifier("ad_body", "id", context.packageName))
        adView.callToActionView = adView.findViewById(context.resources.getIdentifier("ad_call_to_action", "id", context.packageName))
        adView.iconView = adView.findViewById(context.resources.getIdentifier("ad_app_icon", "id", context.packageName))
        adView.priceView = adView.findViewById(context.resources.getIdentifier("ad_price", "id", context.packageName))
        adView.starRatingView = adView.findViewById(context.resources.getIdentifier("ad_stars", "id", context.packageName))
        adView.storeView = adView.findViewById(context.resources.getIdentifier("ad_store", "id", context.packageName))
        adView.advertiserView = adView.findViewById(context.resources.getIdentifier("ad_advertiser", "id", context.packageName))
        adView.mediaView = adView.findViewById(context.resources.getIdentifier("ad_media", "id", context.packageName))
        
        populateNativeAdView(nativeAd, adView)
        
        return adView
    }
    
    private fun createProgrammaticNativeAdView(nativeAd: NativeAd): NativeAdView {
        val adView = NativeAdView(context)
        val linearLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        
        // Add headline
        val headline = TextView(context).apply {
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        linearLayout.addView(headline)
        adView.headlineView = headline
        
        // Add body
        val body = TextView(context).apply {
            textSize = 14f
            setPadding(0, 10, 0, 10)
        }
        linearLayout.addView(body)
        adView.bodyView = body
        
        // Add MediaView with proper minimum size (120dp x 120dp minimum)
        val mediaView = MediaView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (200 * context.resources.displayMetrics.density).toInt() // 200dp height
            ).apply {
                setMargins(0, 20, 0, 20)
            }
        }
        linearLayout.addView(mediaView)
        adView.mediaView = mediaView
        
        // Add call to action button
        val button = Button(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 0)
            }
        }
        linearLayout.addView(button)
        adView.callToActionView = button
        
        adView.addView(linearLayout)
        populateNativeAdView(nativeAd, adView)
        
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