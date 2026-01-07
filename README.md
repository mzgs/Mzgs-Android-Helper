# Mzgs-Android-Helper
[![JitPack](https://jitpack.io/v/mzgs/Mzgs-Android-Helper.svg)](https://jitpack.io/#mzgs/Mzgs-Android-Helper)  

Android helper library with utility tools and dual ad mediation (AdMob + AppLovin MAX).

## 🚀 Features
- Dual-network ads helper with ordered fallback (AdMob + AppLovin MAX).
- Fullscreen ad helpers: interstitial, rewarded, and app open with callbacks.
- Compose ad views for banner, MREC, and native ads (AdMob + AppLovin).
- UMP consent flow helper with optional debug EEA forcing.
- Simple splash screen with progress + auto-dismiss.
- Remote config (JSON), country allow/deny gating, and in-app review helper.
- Firebase Analytics helper for ad events plus simple prefs/counters.

## ✅ Requirements

- minSdk 24, compileSdk 36
- Java 17 / Kotlin JVM target 17
- Jetpack Compose enabled for banner/MREC/native composables


## 📦 Installation

> No version catalogs are used; all dependency versions live in the Gradle files. The library targets `compileSdk 36` and `minSdk 24`.

### Using JitPack

Add the required repositories to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.mzgs:Mzgs-Android-Helper:<version>")
}
```
 

## 🔧 Build Configuration

 

### Android Manifest & Permissions

Add to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID"/>

<application>
    <!-- AdMob App ID (Required if using AdMob) -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
    
    <!-- AppLovin SDK Key (Required if using AppLovin) -->
    <meta-data
        android:name="applovin.sdk.key"
        android:value="YOUR_SDK_KEY_HERE" />
</application>
```

## 🧩 Example Usage

### Application class (App.kt)

```kotlin

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
import com.mzgs.helper.printLine
import kotlinx.coroutines.CompletableDeferred
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
            INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX",
            BANNER_AD_UNIT_ID = "",
            MREC_AD_UNIT_ID = "",
            APP_OPEN_AD_UNIT_ID = "",
            REWARDED_AD_UNIT_ID = "",
            NATIVE_AD_UNIT_ID = "",
            DEBUG = AdmobDebug(useTestAds = true),
        )

        ApplovinMaxMediation.config = ApplovinMaxConfig(
            INTERSTITIAL_AD_UNIT_ID = "",
            APP_OPEN_AD_UNIT_ID = "",
            BANNER_AD_UNIT_ID = "",
            MREC_AD_UNIT_ID = "",
            NATIVE_AD_UNIT_ID = "",
            REWARDED_AD_UNIT_ID = "",
            DEBUG = ApplovinMaxDebug(useEmptyIds = false),
        )

        MzgsHelper.registerFirstActivityCallbacks(
            application = this,
            onActivityResumed = { activity ->
                MzgsHelper.showUmpConsent(activity, forceDebugConsentInEea = true) {
                    AdmobMediation.initialize(this@App)
                    ApplovinMaxMediation.initialize(this@App)
                    App.notifyUmpConsentDone()

                }

                Ads.initialize(
                    activity,
                    onGoForeground = {
                        if (!ApplovinMaxMediation.isFullscreenAdShowing) {
                            Ads.showAppOpenAd(activity)
                        } else {
                            AdmobMediation.showAppOpenAd(activity)
                        }
                    },
                )
            },
        )

        FirebaseAnalyticsManager.initialize(this)
        Pref.init(this)

        applicationScope.launch {
            Remote.initSync(this@App)
        }

    }

    companion object {
        @Volatile
        private var remoteInitJob: Job? = null
        private val umpConsentDeferred = CompletableDeferred<Unit>()

        suspend fun waitForRemoteInit() {
            remoteInitJob?.join()
        }

        suspend fun waitForUmpConsent() {
            umpConsentDeferred.await()
        }

        internal fun notifyUmpConsentDone() {
            if (!umpConsentDeferred.isCompleted) {
                umpConsentDeferred.complete(Unit)
            }
        }
    }
}

```

### MainActivity splash + interstitial

```kotlin
override fun onStart() {
    super.onStart()

    lifecycleScope.launch {
        val activity = this@MainActivity
        SimpleSplashHelper.showSplash(activity)
        App.waitForRemoteInit()
        MzgsHelper.initAllowedCountry(activity)
        App.waitForUmpConsent()


        SimpleSplashHelper.setOnComplete {
            Ads.showInterstitial(activity) {
                isSplashComplete.value = true
            }
        }

        val splashDuration = if (MzgsHelper.isDebug(activity)) {
            500
        } else {
            Remote.getLong("splash_time", 11_000)
        }
        SimpleSplashHelper.setDuration(splashDuration)
        SimpleSplashHelper.startProgress(activity)
    }
}
```

### Fullscreen ads with fallback ordering

```kotlin
Ads.showInterstitial(
    activity,
    networks = "applovin,admob",
) {
    // Called when the shown ad is closed or no ad is available.
}

Ads.showRewarded(
    activity,
    networks = "admob,applovin",
    onRewarded = { type, amount ->
        // Reward the user.
    },
)
```

### Interstitial with cycle (remote-configurable)

```kotlin
Ads.showInterstitialWithCycle(
    activity = activity,
    name = "interstitial_cycle_home",
    defaultValue = 3,
    networks = "applovin,admob",
) {
    // Called when an ad closes or when the cycle skips showing.
}
```

### Compose banners, MREC, and native ads

```kotlin
Ads.showBanner(
    modifier = Modifier.fillMaxWidth(),
    networks = "admob,applovin",
    adSize = AdSize.BANNER,
) { error ->
    // Handle banner load failure.
}

Ads.showMrec(
    modifier = Modifier.fillMaxWidth(),
    networks = "applovin,admob",
)

Ads.showNativeAd(
    modifier = Modifier.fillMaxWidth(),
    networks = "applovin,admob",
) { error ->
    // Handle native ad load failure.
}
```

### Remote config + country gating

```kotlin
Remote.init(application)
// Or, in a coroutine: Remote.initSync(application)
MzgsHelper.initAllowedCountry(activity)
if (!MzgsHelper.isAllowedCountry) {
    // Skip monetization or limit features.
}

val splashTime = Remote.getLong("splash_time", 11_000)
```

### Other helpers

- `SimpleSplashHelper` for quick splash UI with progress.
- `MzgsHelper.showInappRate(...)` for Google Play in-app review prompts.
- `Pref` + `ActionCounter` for lightweight local storage and counters.

```kotlin
// In-app review prompt (optionally show at specific counts)
MzgsHelper.showInappRate(
    activity = activity,
    rateName = "rate",
    showAtCounts = listOf(3, 7, 12),
)
```

```kotlin
// Pref + ActionCounter
Pref.init(application)

val launchCount = ActionCounter.increaseGet("app_launch")
if (launchCount % 5 == 0) {
    // Do something every 5 launches.
}

Pref.set("user_name", "Mustafa")
val userName = Pref.get("user_name", "Guest")

// Remove or reset
Pref.remove("user_name")
Pref.clearAll()
```

```kotlin
// Remote config quick example
Remote.init(application)
val splashTime = Remote.getLong("splash_time", 11_000)
val isFeatureEnabled = Remote.getBool("new_feature", false)
```

## Mediation adapters
- AdMob Mediation adapers

 ```kotlin
    implementation("com.google.ads.mediation:applovin:13.5.1.0")
    implementation("com.google.ads.mediation:fyber:8.4.2.0")
    implementation("com.google.ads.mediation:vungle:7.6.2.0")
    implementation("com.unity3d.ads:unity-ads:4.16.5")
    implementation("com.google.ads.mediation:unity:4.16.5.0")
 ```

- AppLovin MAX Mediation adapters

 ```kotlin
    implementation("com.applovin.mediation:google-adapter:24.9.0.0")
    implementation("com.applovin.mediation:unityads-adapter:4.16.5.0")
    implementation("com.applovin.mediation:fyber-adapter:8.4.2.0")
    implementation("com.applovin.mediation:vungle-adapter:7.6.2.0")
 ```
