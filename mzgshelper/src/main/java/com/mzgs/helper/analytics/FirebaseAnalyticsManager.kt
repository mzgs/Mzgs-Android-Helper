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
    
    fun logAdLoadSuccess(
        adType: String,
        adUnitId: String,
        adNetwork: String = ""
    ) {
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("ad_unit_id", adUnitId)
            putString("ad_network", adNetwork)
            putString("status", "success")
        }
        logEvent("ad_load_event", bundle)
    }
    
    fun logAdLoadFailed(
        adType: String,
        adUnitId: String,
        errorMessage: String,
        errorCode: Int,
        adNetwork: String = ""
    ) {
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("ad_unit_id", adUnitId)
            putString("error_message", errorMessage)
            putInt("error_code", errorCode)
            putString("ad_network", adNetwork)
            putString("status", "failed")
        }
        logEvent("ad_load_event", bundle)
    }
    
    fun logAdImpression(
        adType: String,
        adUnitId: String,
        adNetwork: String = ""
    ) {
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("ad_unit_id", adUnitId)
            putString("ad_network", adNetwork)
        }
        logEvent("ad_impression", bundle)
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
    
    fun logAdRevenue(
        adType: String,
        adUnitId: String,
        revenue: Double,
        currency: String = "USD",
        adNetwork: String = ""
    ) {
        val bundle = Bundle().apply {
            putString("ad_type", adType)
            putString("ad_unit_id", adUnitId)
            putDouble("revenue", revenue)
            putString("currency", currency)
            putString("ad_network", adNetwork)
        }
        logEvent("ad_revenue", bundle)
    }
}