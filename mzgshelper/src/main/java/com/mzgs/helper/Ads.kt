package com.mzgs.helper

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import java.lang.ref.WeakReference

object Ads {

    private var startedActivityCount = 0
    private var appWentToBackground = false
    private var activityCallbacksRegistered = false
    private var componentCallbacksRegistered = false
    private var triggerOnColdStart = false
    private var coldStartHandled = false
    private var onGoBackground: (Activity) -> Unit = {}
    private var onGoForeground: (Activity) -> Unit = {}
    private var lastStartedActivityRef: WeakReference<Activity>? = null

    private val activityCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}

        override fun onActivityStarted(activity: Activity) {
            startedActivityCount += 1
            lastStartedActivityRef = WeakReference(activity)
            if (startedActivityCount == 1) {
                val shouldNotify = appWentToBackground || (triggerOnColdStart && !coldStartHandled)
                if (triggerOnColdStart && !coldStartHandled) {
                    coldStartHandled = true
                }
                if (appWentToBackground) {
                    appWentToBackground = false
                }
                if (shouldNotify) {
                    onGoForeground(activity)
                }
            }
        }

        override fun onActivityResumed(activity: Activity) {}

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            if (startedActivityCount > 0) {
                startedActivityCount -= 1
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {}
    }

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN && !appWentToBackground) {
                appWentToBackground = true
                lastStartedActivityRef?.get()?.let { onGoBackground(it) }
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {}

        override fun onLowMemory() {}
    }

    fun initialize(
        activity: Activity,
        onGoBackground: (Activity) -> Unit = {},
        onGoForeground: (Activity) -> Unit = {},
        triggerOnColdStart: Boolean = false,
    ) {
        this.onGoBackground = onGoBackground
        this.onGoForeground = onGoForeground
        this.triggerOnColdStart = triggerOnColdStart
        if (!activityCallbacksRegistered) {
            activity.application.registerActivityLifecycleCallbacks(activityCallbacks)
            activityCallbacksRegistered = true
        }
        if (!componentCallbacksRegistered) {
            activity.application.registerComponentCallbacks(componentCallbacks)
            componentCallbacksRegistered = true
        }
    }
}
