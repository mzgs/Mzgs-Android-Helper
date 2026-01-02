package com.mzgs.helper

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdmobMediation {

    const val TAG = "AdmobMediation"
    private const val ADMOB_APP_ID_KEY = "com.google.android.gms.ads.APPLICATION_ID"

    var config: AdmobConfig = AdmobConfig()

    fun initialize(activity: Activity, config: AdmobConfig, onInitComplete: () -> Unit = {}) {
        this.config = config
        CoroutineScope(Dispatchers.IO).launch {
            val resolvedAppId = getAdMobAppId(activity)
            if (resolvedAppId.isNullOrBlank()) {
                Log.w(TAG, "AdMob app ID not found; set $ADMOB_APP_ID_KEY in the manifest or pass appId.")
                onInitComplete()
                return@launch
            }
            // Initialize the Google Mobile Ads SDK on a background thread.
            val initConfig = InitializationConfig.Builder(resolvedAppId).build()
            MobileAds.initialize(activity, initConfig) { initializationStatus ->
                for ((adapterClass, status) in initializationStatus.adapterStatusMap) {
                    Log.d(
                        TAG,
                        "Adapter: $adapterClass, Status: ${status.description}, Latency: ${status.latency}ms",
                    )
                }

                // autoload interstitial
                val adRequest = AdRequest.Builder(config.INTERSTITIAL_AD_UNIT_ID).build()
                val preloadConfig = PreloadConfiguration(adRequest)
                InterstitialAdPreloader.start(config.INTERSTITIAL_AD_UNIT_ID, preloadConfig)

                onInitComplete()

            }
        }
    }

    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit = {}) : Boolean {
        val ad = InterstitialAdPreloader.pollAd(config.INTERSTITIAL_AD_UNIT_ID)
        if (ad != null) {
            ad.adEventCallback = object : InterstitialAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) {
                    onAdClosed()
                    FirebaseAnalyticsManager.logEvent("interstitial_ad_failed_to_show",
                        Bundle().apply {
                            putString("ad_unit_id", config.INTERSTITIAL_AD_UNIT_ID)
                            putString("error_message", fullScreenContentError.message)
                        }
                    )
                }
            }
            ad.show(activity)
            return true
        }

        onAdClosed()
        return false
    }

    private fun getAdMobAppId(context: Context): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA,
            )
            appInfo.metaData?.getString(ADMOB_APP_ID_KEY)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.w(TAG, "Failed to read AdMob app ID from manifest", e)
            null
        }
    }

}

data class AdmobConfig(
    var INTERSTITIAL_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/1033173712",
    var BANNER_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/6300978111",
    var REWARDED_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/5224354917",
    var NATIVE_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/2247696110",
    var APP_OPEN_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/9257395921",
    var MREC_AD_UNIT_ID: String = "ca-app-pub-3940256099942544/6300978111",
)
