package com.mzgs.helper

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AdmobMediation {

    const val TAG = "AdmobMediation"

    fun initialize(context: Context,  onInitComplete: () -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            // Initialize the Google Mobile Ads SDK on a background thread.
            MobileAds.initialize(context) { initializationStatus ->
                for ((adapterClass, status) in initializationStatus.adapterStatusMap) {
                    Log.d(
                        TAG,
                        "Adapter: $adapterClass, Status: ${status.description}, Latency: ${status.latency}ms",
                    )
                }
                // Switch back to the main thread to invoke the callback.
                CoroutineScope(Dispatchers.Main).launch {
                    onInitComplete()
                }
            }
        }
    }


}
