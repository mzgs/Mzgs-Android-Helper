package com.mzgs.helper.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseAnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null
    
    fun initialize(context: Context) {
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        }
    }
    
    fun logEvent(eventName: String, params: Bundle? = null) {
        firebaseAnalytics?.logEvent(eventName, params)
    }
    
    fun logAdLoad(
        adType: String,
        adUnitId: String,
        adNetwork: String = "",
        success: Boolean,
        errorMessage: String? = null,
        errorCode: Int? = null,
        retryAttempt: Int = 0
    ) {
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("ad_unit_id", adUnitId)
            putString("ad_network", adNetwork)
            if (retryAttempt > 0) {
                putInt("retry_attempt", retryAttempt)
            }
            if (!success) {
                errorMessage?.let { putString("error_message", it) }
                errorCode?.let { putInt("error_code", it) }
            }
        }
        val eventName = if (success) "ad_load_success" else "ad_load_failed"
        logEvent(eventName, bundle)
    }
    
    fun logAdClicked(
        adType: String,
        adUnitId: String,
        adNetwork: String = ""
    ) {
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("ad_unit_id", adUnitId)
            putString("ad_network", adNetwork)
        }
        logEvent("ad_click", bundle)
    }

    
    fun logAdShown(
        adType: String,
        adNetwork: String,
        success: Boolean = true
    ) {
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("ad_network", adNetwork)
        }
        val eventName = if (success) "ad_show_success" else "ad_show_failed"
        logEvent(eventName, bundle)
    }
}