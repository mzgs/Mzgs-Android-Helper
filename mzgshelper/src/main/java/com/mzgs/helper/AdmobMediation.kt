package com.mzgs.helper

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdmobMediation {

    const val TAG = "AdmobMediation"
    private const val ADMOB_APP_ID_KEY = "com.google.android.gms.ads.APPLICATION_ID"

    fun initialize(context: Context, appId: String? = null, onInitComplete: () -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            val resolvedAppId = appId ?: getAdMobAppId(context)
            if (resolvedAppId.isNullOrBlank()) {
                Log.w(TAG, "AdMob app ID not found; set $ADMOB_APP_ID_KEY in the manifest or pass appId.")
                CoroutineScope(Dispatchers.Main).launch {
                    onInitComplete()
                }
                return@launch
            }
            // Initialize the Google Mobile Ads SDK on a background thread.
            val initConfig = InitializationConfig.Builder(resolvedAppId).build()
            MobileAds.initialize(context, initConfig) { initializationStatus ->
                for ((adapterClass, status) in initializationStatus.adapterStatusMap) {
                    Log.d(
                        TAG,
                        "Adapter: $adapterClass, Status: ${status.description}, Latency: ${status.latency}ms",
                    )
                }

                val adUnitId = "ca-app-pub-8689213949805403/4964803980"
                val adRequest = AdRequest.Builder(adUnitId).build()
                val preloadConfig = PreloadConfiguration(adRequest)
                InterstitialAdPreloader.start(adUnitId, preloadConfig)
                // Switch back to the main thread to invoke the callback.
                CoroutineScope(Dispatchers.Main).launch {
                    onInitComplete()
                }
            }
        }
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
