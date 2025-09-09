# Mzgs-Android-Helper
Android helper library with utility tools and comprehensive ad mediation support (AdMob & AppLovin MAX)

## 🚀 Features

- 🛠️ **General utility functions** (Toast, Network status, App version, Remote Config)
- 📱 **Complete AdMob integration** with all ad formats
- 🎯 **AppLovin MAX integration** with mediation adapters
- 🔐 **Built-in UMP consent management** for GDPR/CCPA compliance
- 🎨 **Unified Ads API** - Single interface for both ad networks
- ⚡ **Automatic test ad handling** in debug mode
- 🖼️ **App Open Ads** with lifecycle management
- 📊 **Firebase Analytics** integration for ad events
- 🔄 **Dual mediation support** (AdMob + AppLovin MAX)

## 📦 Installation

### Project Structure Changes
This library no longer uses Gradle Version Catalogs (`libs.versions.toml`). All dependencies are defined directly in the build files with explicit versions.

### Using JitPack

Add the required repositories to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Required: Mintegral repository for AppLovin mediation adapters
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
    }
}
```

Then add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.mzgs:Mzgs-Android-Helper:1.0.1")
}
```

### Local Module

If you're using it as a local module in your project:

```kotlin
dependencies {
    implementation(project(":mzgshelper"))
}
```

## 🔧 Build Configuration

### Root Project `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "8.12.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.android.library") version "8.12.1" apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
    id("maven-publish")
}
```

### App Module `build.gradle.kts`

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.yourapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.yourapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true // Enable BuildConfig generation
    }
}

dependencies {
    implementation(project(":mzgshelper"))
    
    // Compose dependencies
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Other AndroidX dependencies
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-compose:1.10.1")
}
```

## 🎯 Quick Start

### 1. Initialize in Activity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize MzgsHelper
        // Note: BuildConfig.DEBUG might need to be replaced with a boolean value
        // if BuildConfig is not properly generated
        MzgsHelper.init(this, true, skipAdsInDebug = false)
        
        // Initialize other components
        FirebaseAnalyticsManager.initialize(this)
        Remote.init(this)
        Ads.init(this)
        MzgsHelper.setIPCountry()
        
        // Configure AdMob
        val admobConfig = AdMobConfig(
            appId = "ca-app-pub-XXXXX~XXXXX",
            bannerAdUnitId = "", // Leave empty for test ads in debug
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            rewardedInterstitialAdUnitId = "",
            nativeAdUnitId = "",
            mrecAdUnitId = "",
            appOpenAdUnitId = "",
            enableAppOpenAd = true,
            enableTestMode = true,
            testDeviceIds = listOf("YOUR_DEVICE_ID"),
            showAdsInDebug = true,
            bannerAutoRefreshSeconds = 30
        )
        
        // Configure AppLovin
        val appLovinConfig = AppLovinConfig(
            sdkKey = "YOUR_SDK_KEY",
            bannerAdUnitId = "",
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            mrecAdUnitId = "",
            nativeAdUnitId = "",
            enableTestMode = true,
            verboseLogging = true,
            creativeDebuggerEnabled = true,
            showAdsInDebug = true,
            testDeviceAdvertisingIds = listOf("YOUR_DEVICE_ID")
        )
        
        // Show splash with interstitial
        MzgsHelper.initSplashWithInterstitialShow(
            activity = this,
            admobConfig = admobConfig,
            appLovinConfig = appLovinConfig,
            defaultSplashTime = 10000,
            onFinish = {
                Log.d("MainActivity", "Splash and ad sequence completed")
            },
            onFinishAndApplovinReady = {
                Log.d("MainActivity", "AppLovin SDK initialized successfully")
                // Preload ads
                AppLovinMediationManager.loadInterstitialAd()
            }
        )
    }
}
```

### 2. Using AdMob Directly

```kotlin
// Load and show rewarded ad
AdMobManager.loadRewardedAd(
    onAdLoaded = {
        Log.d("Ads", "Rewarded ad loaded")
    },
    onAdFailedToLoad = { error: LoadAdError ->
        Log.e("Ads", "Failed to load: ${error.message}")
    }
)

// Show rewarded ad
if (AdMobManager.showRewardedAd(
    activity = this,
    onUserEarnedReward = { reward: RewardItem ->
        Log.d("Ads", "Earned ${reward.amount} ${reward.type}")
    }
)) {
    // Ad was shown
}

// Load and show interstitial
AdMobManager.loadInterstitialAd()
if (AdMobManager.isInterstitialReady()) {
    AdMobManager.showInterstitialAd()
}
```

### 3. Using Unified Ads API

```kotlin
// Show interstitial (automatically selects best available network)
if (Ads.showInterstitial()) {
    Log.d("Ads", "Interstitial shown")
}

// Show interstitial with cycle control
Ads.showInterstitialWithCycle("button_click", 3) // Shows ad every 3rd click

// Show rewarded ad
if (Ads.showRewardedAd()) {
    Log.d("Ads", "Rewarded ad shown")
}

// Show banner
val bannerContainer: FrameLayout = findViewById(R.id.banner_container)
Ads.showBanner(bannerContainer, Ads.BannerSize.ADAPTIVE)

// Show MREC (300x250)
val mrecContainer: FrameLayout = findViewById(R.id.mrec_container)
Ads.showMREC(mrecContainer)
```

## 🎨 Compose Integration

### Banner in Compose

```kotlin
@Composable
fun BannerAdView() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            FrameLayout(context).apply {
                Ads.showBanner(this, Ads.BannerSize.ADAPTIVE)
            }
        }
    )
}
```

### MREC in Compose

```kotlin
@Composable
fun MRECAdView() {
    Card(
        modifier = Modifier
            .width(300.dp)
            .height(250.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                FrameLayout(context).apply {
                    Ads.showMREC(this)
                }
            }
        )
    }
}
```

## ⚠️ Deprecated API Warnings

The library suppresses certain deprecated API warnings that are still the recommended approach for the current SDK versions:

- AppLovin SDK 13.x constructors (MaxAdView, MaxInterstitialAd, etc.)
- Android FLAG_FULLSCREEN (handled with backward compatibility)
- setColorFilter methods (handled with BlendModeColorFilter for API 29+)

These are handled internally with proper version checks and @Suppress annotations.

## 📋 Dependencies

The library includes these dependencies (exposed via `api` configuration):

- Google Mobile Ads SDK: 24.6.0
- AppLovin SDK: 13.4.0
- Firebase BOM: 34.2.0
- Firebase Analytics
- Google UMP: 3.2.0
- Play Review: 2.0.2
- Various AppLovin mediation adapters

## 🔧 Required Permissions

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

## 🐛 Common Issues & Solutions

### BuildConfig.DEBUG not found
- **Solution**: Enable `buildConfig = true` in your app's buildFeatures, or replace with a boolean value

### LoadAdError/RewardItem type inference errors
- **Solution**: Add explicit type annotations: `{ error: LoadAdError ->` and `{ reward: RewardItem ->`

### Plugin version conflicts
- **Solution**: Ensure consistent Android Gradle Plugin version (8.12.1) across all modules

### Google Services plugin not found
- **Solution**: Add `id("com.google.gms.google-services") version "4.4.3" apply false` to root build.gradle.kts

## 🧪 Testing

### Test Ad Unit IDs (Automatically Used)

When `enableTestMode = true`, the library automatically uses Google's test ad unit IDs:

```
Banner: ca-app-pub-3940256099942544/6300978111
Interstitial: ca-app-pub-3940256099942544/1033173712
Rewarded: ca-app-pub-3940256099942544/5224354917
Native: ca-app-pub-3940256099942544/2247696110
MREC: ca-app-pub-3940256099942544/6300978111
App Open: ca-app-pub-3940256099942544/9257395921
```

## 📄 License

[Add your license here]