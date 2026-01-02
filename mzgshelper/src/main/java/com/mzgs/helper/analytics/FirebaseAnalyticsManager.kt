package com.mzgs.helper.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

object FirebaseAnalyticsManager {
    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var applicationContext: Context? = null
    
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        if (firebaseAnalytics == null) {
            firebaseAnalytics = FirebaseAnalytics.getInstance(appContext)
        }
    }

    private fun ensureInitialized(): Boolean {
        if (firebaseAnalytics == null) {
            val context = applicationContext ?: return false
            firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        }
        return firebaseAnalytics != null
    }
    
    fun logEvent(eventName: String, params: Bundle? = null) {
        if (!ensureInitialized()) return
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
        if (!ensureInitialized()) return
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
        if (!ensureInitialized()) return
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
        success: Boolean = true,
        errorMessage: String? = null
    ) {
        if (!ensureInitialized()) return
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("ad_network", adNetwork)
            if (!success) {
                errorMessage?.let { putString("error_message", it) }
            }
        }
        val eventName = if (success) "ad_show_success" else "ad_show_failed"
        logEvent(eventName, bundle)
    }
}
