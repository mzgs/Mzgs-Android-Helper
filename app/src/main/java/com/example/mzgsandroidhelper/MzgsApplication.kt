package com.example.mzgsandroidhelper

import android.app.Application
import com.mzgs.helper.AdmobMediation
import com.mzgs.helper.MzgsHelper
import com.mzgs.helper.analytics.FirebaseAnalyticsManager

class MzgsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MzgsHelper.init(this)
         FirebaseAnalyticsManager.initialize()

        AdmobMediation.initialize(this){
            // AdMob Mediation initialized
        }
    }
}
