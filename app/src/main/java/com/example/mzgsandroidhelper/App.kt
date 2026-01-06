package com.example.mzgsandroidhelper

import android.app.Application
import com.mzgs.helper.AdmobConfig
import com.mzgs.helper.AdmobDebug
import com.mzgs.helper.AdmobMediation
import com.mzgs.helper.Ads
import com.mzgs.helper.ApplovinMaxConfig
import com.mzgs.helper.ApplovinMaxDebug
import com.mzgs.helper.ApplovinMaxMediation
import com.mzgs.helper.FirebaseAnalyticsManager
import com.mzgs.helper.MzgsHelper
import com.mzgs.helper.Pref
import com.mzgs.helper.Remote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()


        AdmobMediation.config = AdmobConfig(
            INTERSTITIAL_AD_UNIT_ID = "",
            BANNER_AD_UNIT_ID = "",
            MREC_AD_UNIT_ID = "",
            APP_OPEN_AD_UNIT_ID = "",
            REWARDED_AD_UNIT_ID = "",
            NATIVE_AD_UNIT_ID = "",
            DEBUG = AdmobDebug(
                useTestAds = true,
            )
        )

        ApplovinMaxMediation.config = ApplovinMaxConfig(
            INTERSTITIAL_AD_UNIT_ID = "b5d9132de55740f2",
            APP_OPEN_AD_UNIT_ID = "efacaf217df0d0c4",
            BANNER_AD_UNIT_ID = "2a850e4955fcac79",
            MREC_AD_UNIT_ID = "499681b3d7a48fbc",
            NATIVE_AD_UNIT_ID = "b93d53f11cb44097",
            REWARDED_AD_UNIT_ID = "",
            DEBUG = ApplovinMaxDebug(
                useEmptyIds = false,
            )

        )

        MzgsHelper.registerFirstActivityCallbacks(
            application = this,
            onActivityResumed = { activity ->
                MzgsHelper.showUmpConsent(activity, forceDebugConsentInEea = true) {
                    AdmobMediation.initialize(this@App)
                    ApplovinMaxMediation.initialize(this@App)
                }

                Ads.initialize(
                    activity,
                    onGoForeground = {
                        if (!ApplovinMaxMediation.isFullscreenAdShowing) {
                            Ads.showAppOpenAd(activity)
                        }else{
                            AdmobMediation.showAppOpenAd(activity)
                        }
                    }
                )
            },
        )

        FirebaseAnalyticsManager.initialize(this)
        Pref.init(this)

        remoteInitJob = applicationScope.launch {
            Remote.initSync(this@App)
        }




    }

    companion object {
        @Volatile
        private var remoteInitJob: Job? = null

        suspend fun waitForRemoteInit() {
            remoteInitJob?.join()
        }
    }
}
