package com.example.mzgsandroidhelper

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.mzgs.helper.AdmobConfig
import com.mzgs.helper.AdmobDebug
import com.mzgs.helper.AdmobMediation
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
import java.util.concurrent.atomic.AtomicBoolean

class App : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val firstActivityHandled = AtomicBoolean(false)
    private val firstActivityResumedHandled = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()

        registerActivityDetect()

        FirebaseAnalyticsManager.initialize(this)
        Pref.init(this)

        remoteInitJob = applicationScope.launch {
            Remote.initSync(this@App)
        }


        AdmobMediation.config = AdmobConfig(
            INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8689213949805403/4964803980",
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
            DEBUG = ApplovinMaxDebug(
                useEmptyIds = false,
            )

        )

    }

    fun registerActivityDetect( ) {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (firstActivityHandled.compareAndSet(false, true)) {
                    onFirstActivityCreatedListener?.invoke(activity)
                }
            }

            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                if (firstActivityResumedHandled.compareAndSet(false, true)) {

                    MzgsHelper.showUmpConsent(activity,forceDebugConsentInEea = true) {

                        AdmobMediation.initialize(this@App)
                        ApplovinMaxMediation.initialize(this@App)

                    }

                }
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        @Volatile
        private var remoteInitJob: Job? = null
        @Volatile
        var onFirstActivityCreatedListener: ((Activity) -> Unit)? = null

        suspend fun waitForRemoteInit() {
            remoteInitJob?.join()
        }
    }
}
