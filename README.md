# Mzgs-Android-Helper
[![JitPack](https://jitpack.io/v/mzgs/Mzgs-Android-Helper.svg)](https://jitpack.io/#mzgs/Mzgs-Android-Helper)  

Android helper library with utility tools and dual ad mediation (AdMob + AppLovin MAX)

## 🚀 Features



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
                }

                Ads.initialize(activity,
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
}
```

### MainActivity onStart

```kotlin
override fun onStart() {
    super.onStart()

    lifecycleScope.launch {
        val activity = this@MainActivity
        SimpleSplashHelper.showSplash(activity)
        App.waitForRemoteInit()
        MzgsHelper.initAllowedCountry(activity)

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
