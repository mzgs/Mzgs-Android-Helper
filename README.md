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

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseAnalyticsManager.initialize(this)
        FirebaseAnalyticsManager.logEvent("mzgs_app_started")
        Pref.init(this)
        Remote.init(this)

        AdmobMediation.config = AdmobConfig(
            INTERSTITIAL_AD_UNIT_ID = "",
            BANNER_AD_UNIT_ID = "",
            MREC_AD_UNIT_ID = "",
            APP_OPEN_AD_UNIT_ID = "",
            REWARDED_AD_UNIT_ID = "",
            NATIVE_AD_UNIT_ID = "",
            DEBUG = AdmobDebug(useTestAds = true),
        )

        ApplovinMaxMediation.config = ApplovinMaxConfig(
            INTERSTITIAL_AD_UNIT_ID = "YOUR_MAX_INTERSTITIAL_AD_UNIT_ID",
            APP_OPEN_AD_UNIT_ID = "YOUR_MAX_APP_OPEN_AD_UNIT_ID",
            BANNER_AD_UNIT_ID = "YOUR_MAX_BANNER_AD_UNIT_ID",
            MREC_AD_UNIT_ID = "YOUR_MAX_MREC_AD_UNIT_ID",
            NATIVE_AD_UNIT_ID = "YOUR_MAX_NATIVE_AD_UNIT_ID",
            REWARDED_AD_UNIT_ID = "",
            DEBUG = ApplovinMaxDebug(useEmptyIds = false),
        )

        MzgsHelper.registerFirstActivityCallbacks(
            application = this,
            onActivityResumed = { activity ->
                AdmobMediation.initialize(this@App) {
                    AdmobMediation.loadAppOpenAd(activity)
                    AdmobMediation.loadInterstitial(activity)
                }
                ApplovinMaxMediation.initialize(this@App)
            },
        )

        MzgsHelper.registerAppLifecycleCallbacks(
            application = this,
            onGoForeground = { activity ->
                if (!ApplovinMaxMediation.isFullscreenAdShowing) {
                    Ads.showAppOpenAd(activity)
                } else {
                    AdmobMediation.showAppOpenAd(activity)
                }
            },
        )
    }
}

```

### Init listeners

```kotlin
// Runs after both AdMob and AppLovin MAX initialization callbacks complete.
// If both are already initialized, it runs immediately.
Ads.setInitListener {
    Ads.loadInterstitial(activity)
    Ads.loadRewarded(activity)
    Ads.loadAppOpenAd(activity)
}

// Runs after AppLovin MAX initialization completes.
// If AppLovin MAX is already initialized, it runs immediately.
ApplovinMaxMediation.setInitListener {
    // AppLovin MAX is ready.
}
```

### MainActivity splash + interstitial

Optional custom splash image:

```kotlin
SimpleSplashHelper.setCustomImage(R.drawable.my_splash_image)
SimpleSplashHelper.showSplash(activity)
```

Without `setCustomImage`, the splash keeps the default rotating app logo behavior.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    lifecycleScope.launch {
        val activity = this@MainActivity

        MzgsHelper.initAllowedCountry(
            activity,
            debugAllow = false,
            defaultRestrictedCountries = listOf(
                "UK", "US", "GB", "CN", "MX", "JP", "KR", "AR", "HK", "IN",
                "PK", "TR", "VN", "RU", "SG", "MO", "TW", "PY",
            ),
        )

        SimpleSplashHelper.showSplash(activity)
        // SimpleSplashHelper.setCustomImage(R.drawable.cleaner)
        SimpleSplashHelper.setOnComplete {
            FirebaseAnalyticsManager.logEvent("mzgs_splash_completed")

            Ads.showSplashAds(activity) {
                FirebaseAnalyticsManager.logEvent("mzgs_splash_completed_ads_closed")
                isSplashComplete.value = true

                ApplovinMaxMediation.setInitListener {
                    ApplovinMaxMediation.loadInterstitial(activity)
                    ApplovinMaxMediation.loadAppOpenAd(activity)
                }
            }
        }

        val splashDuration = if (MzgsHelper.isDebug(activity)) 9500 else Remote.getLong("splash_time", 12_000)
        SimpleSplashHelper.setDuration(splashDuration)
        SimpleSplashHelper.startProgress(activity)
    }

    setContent {
        MzgsAndroidHelperTheme {
            MainExampleScreen(isSplashComplete = isSplashComplete.value)
        }
    }
}
```

### Fullscreen ads with fallback ordering

```kotlin
// Fullscreen ads are cached. Load before show, ideally after SDK init completes.
Ads.loadInterstitial(activity, networks = "applovin,admob")

Ads.showInterstitial(
    activity,
    networks = "applovin,admob",
) { adShowed ->
    // adShowed is true only when an ad was actually shown and then closed.
}

Ads.loadRewarded(activity, networks = "admob,applovin")

Ads.showRewarded(
    activity,
    networks = "admob,applovin",
    onRewarded = { type, amount ->
        // Reward the user.
    },
) { adShowed ->
    // adShowed is true only when a rewarded ad was actually shown and then closed.
}
```

`showInterstitial`, `showRewarded`, and `showAppOpenAd` report the final show result through
`onAdClosed(adShowed)`.
If a cached interstitial, rewarded, or app open ad is selected but fails while showing, the helper
tries the next configured network before calling `onAdClosed`.

### Interstitial with cycle (remote-configurable)

```kotlin
Ads.showInterstitialWithCycle(
    activity = activity,
    name = "interstitial_cycle_home",
    defaultValue = 3,
    networks = "applovin,admob",
    onAdShowFailed = { network, errorMessage ->
        // Called if a loaded ad fails while showing.
    },
) { adShowed ->
    // adShowed is true only when an ad was actually shown and then closed.
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
MzgsHelper.initAllowedCountry(
    activity,
    defaultRestrictedCountries = listOf("US", "GB", "CN"),
)
if (!MzgsHelper.isAllowedCountry) {
    // Skip monetization or limit features.
}

// Remote.init loads the last fetched config from prefs immediately,
// then fetches in the background with a 30-second timeout.
// New remote values are applied and saved to prefs when the fetch completes.
val splashTime = Remote.getLong("splash_time", 11_000)
```

If you need remote values before continuing, use `initSync` from a coroutine.
It reads cached values first, then waits for the fetch to finish or fail, and defaults to waiting 5 seconds.
If the wait timeout elapses, the same fetch continues in the background and remote values are applied when it completes.

```kotlin
lifecycleScope.launch {
    Remote.initSync(application, timeoutMs = 5_000)

    val splashTime = Remote.getLong("splash_time", 11_000)
}
```

Custom URL with a custom wait timeout:

```kotlin
Remote.initSync(
    context = application,
    timeoutMs = 10_000,
    url = "https://example.com/android.json",
)
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
- AdMob Mediation adapters

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
