package com.example.mzgsandroidhelper

import android.app.Application
import com.mzgs.helper.AdmobMediation
import com.mzgs.helper.analytics.FirebaseAnalyticsManager

class MzgsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseAnalyticsManager.initialize(this)

        AdmobMediation.initialize(this){
            // AdMob Mediation initialized
        }
    }
}
