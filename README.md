# Mzgs-Android-Helper
[![JitPack](https://jitpack.io/v/mzgs/Mzgs-Android-Helper.svg)](https://jitpack.io/#mzgs/Mzgs-Android-Helper)
Android helper library with utility tools and dual ad mediation (AdMob + AppLovin MAX)

## 🚀 Features

- ✅ Unified Ads helper that falls back between AppLovin MAX and AdMob for interstitial, rewarded, banner, MREC, native, and app-open ads
- 🌍 Remote JSON config helper with country gating, ad-cycle control, and safe-mode flags
- 🧪 Debug/test controls: skip all ads in debug, test-mode IDs, empty debug IDs, AppLovin CMP test flow, GAID logger
- 🚦 Simple splash helper for quick branded splashes and ad preloading
- 📊 Firebase Analytics hooks for ad events plus Pref, ActionCounter, rating, toast, network/version helpers
- 🧱 Compose-ready ad views (banner, MREC) and classic ViewGroup helpers

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
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
    }
}
```

Then add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    // Replace <version> with the latest tag shown in the badge above (current: 1.0.9)
    implementation("com.github.mzgs:Mzgs-Android-Helper:<version>")
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

### Required Additions

Add this to your root project `build.gradle.kts` plugins block:

```kotlin
id("com.google.gms.google-services") version "4.4.4" apply false
```

Add this to your app module `build.gradle.kts`:

```kotlin
plugins {
    // ... existing plugins
    id("com.google.gms.google-services")
}

android {
    buildFeatures {
        buildConfig = true // Enable BuildConfig generation for debug mode detection
    }
}
```

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

If you need AppLovin CMP test flow, also provide your privacy/terms URLs in `AppLovinConfig`.

## 🎯 Quick Start

### 1) Bootstrap the helper and fetch remote config

```kotlin
class MainActivity : ComponentActivity() {
     private var isSplashComplete = mutableStateOf(false)
     
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MzgsHelper.init(this, this, skipAdsInDebug = false)
        FirebaseAnalyticsManager.initialize()

        lifecycleScope.launch {
            // Optional splash while you prep config/ads
            SimpleSplashHelper.showSplash(this@MainActivity)

            // Load remote JSON config, country, and register lifecycle observers
            Remote.initSync()
            MzgsHelper.setIPCountry()
            Ads.init()

            val admobConfig = AdMobConfig(
                appId = "ca-app-pub-XXXX~YYYY",
                bannerAdUnitId = "",
                interstitialAdUnitId = "",
                rewardedAdUnitId = "",
                rewardedInterstitialAdUnitId = "",
                nativeAdUnitId = "",
                mrecAdUnitId = "",
                appOpenAdUnitId = "",
                enableAppOpenAd = true,
                enableTestMode = true,
                testDeviceIds = listOf("HASHED_TEST_DEVICE_ID"), // AdMob requires hashed IDs
                debugEmptyIds = false
            )

            val appLovinConfig = AppLovinConfig(
                sdkKey = "YOUR_SDK_KEY",
                bannerAdUnitId = "",
                interstitialAdUnitId = "",
                rewardedAdUnitId = "",
                mrecAdUnitId = "",
                nativeAdUnitId = "",
                appOpenAdUnitId = "",
                enableAppOpenAd = true,
                enableTestCMP = true,
                consentFlowPrivacyPolicyUrl = "https://yourdomain.com/privacy",
                consentFlowTermsOfServiceUrl = "https://yourdomain.com/terms",
                testDeviceAdvertisingIds = listOf("YOUR_GAID_FOR_TESTS"), // MAX uses GAID
                enableTestMode = true,
                debugEmptyIds = false
            )

            Ads.initAppLovinMax(appLovinConfig) {
                SimpleSplashHelper.startProgress()
                Ads.initAdMob(admobConfig) {
                    Ads.loadAdmobInterstitial()
                }
            }

            // Finish splash and optionally show an interstitial before UI
            SimpleSplashHelper
                .setDuration(Remote.getLong("splash_time", 8_000))
                .setOnComplete {
                    val result = Ads.showInterstitialWithResult {
                        // Called when closed; preload next round here
                                                    // MzgsHelper.restrictedCountries = listOf("UK", "US", "GB", "CN", "MX", "JP", "KR", "AR", "HK", "IN", "PK", "TR", "VN", "RU", "SG", "MO", "TW", "PY","BR")

                        Ads.loadApplovinMaxInterstitial()
                        MzgsHelper.setRestrictedCountriesFromRemoteConfig()
                        MzgsHelper.setIsAllowedCountry()
                        isSplashComplete.value = true
                    }
                    FirebaseAnalyticsManager.logEvent(
                        if (result.success) "splash_ad_shown" else "splash_ad_failed",
                        Bundle().apply {
                            putString("ad_network", result.network ?: "unknown")
                        }
                    )
                }
          
        }
    }
}
```

### 2) Showing ads with unified API

```kotlin
// Interstitial with fallback (AppLovin -> AdMob) + optional callback
Ads.showInterstitial(onAdClosed = { /* resume flow */ })

// Cycle-based interstitials from remote config (Remote.getInt("button_click", 3))
Ads.showInterstitialWithCycle("button_click", 3)

// Rewarded ad
Ads.showRewardedAd { type, amount ->
    Log.d("Ads", "User earned $amount $type")
}

// Banner / adaptive banner
val bannerContainer: FrameLayout = findViewById(R.id.banner_container)
Ads.showBanner(bannerContainer, Ads.BannerSize.ADAPTIVE)

// MREC
val mrecContainer: FrameLayout = findViewById(R.id.mrec_container)
Ads.showMREC(mrecContainer)

// Native ad (supports AppLovin -> AdMob fallback)
val nativeContainer: FrameLayout = findViewById(R.id.native_container)
Ads.showNativeAd(nativeContainer)
```

App-open ads are automatically handled when `enableAppOpenAd = true` in either config and `Ads.init()` has been called.

### 3) Remote config and country gating

```kotlin
Remote.init()                // Async; or Remote.initSync() to block until loaded
Remote.getBool("only_free_music", false)
Remote.getIntArray("download_rate_show_at_counts", listOf(1, 9, 30))

MzgsHelper.setRestrictedCountriesFromRemoteConfig()
MzgsHelper.setIPCountry()
MzgsHelper.setIsAllowedCountry() // Uses phone + IP country against restricted list

// Debug helper to fake country (debug builds only)
MzgsHelper.setDebugCountry("DE")
```

### 4) Utility Features

#### In-App Rating

Show native Google Play in-app rating dialog with customizable triggers:

```kotlin
// Simple usage - show rating dialog immediately
MzgsHelper.showInappRate(activity = this)

// With custom rate name for tracking
MzgsHelper.showInappRate(
    activity = this,
    rateName = "feature_rate"
)

// With specific show counts (shows at 1st, 9th, and 30th action)
MzgsHelper.showInappRate(
    activity = this,
    rateName = "download_rate",
    showAtCounts = listOf(1, 9, 30)
)

// Using remote config for dynamic control
MzgsHelper.showInappRate(
    activity = this@MainActivity,
    rateName = "download_rate",
    showAtCounts = Remote.getIntArray("download_rate_show_at_counts", listOf(1, 9, 30))
)
```

The rating dialog automatically tracks:
- When it was shown using `ActionCounter`
- The count for the specified `rateName`
- Shows only at specified counts if `showAtCounts` is provided

#### Show Toast Messages

```kotlin
// Show a simple toast
MzgsHelper.showToast("Hello World")

// With custom duration
MzgsHelper.showToast("Processing...", Toast.LENGTH_LONG)
```

#### Network Availability Check

```kotlin
if (MzgsHelper.isNetworkAvailable()) {
    // Network is available
} else {
    // No network connection
}
```

#### App Version Info

```kotlin
// Get app version name (e.g., "1.0.0")
val versionName = MzgsHelper.getAppVersion()

// Get app version code (e.g., 1)
val versionCode = MzgsHelper.getAppVersionCode()
```

#### Country Detection & Restrictions

```kotlin
// Get phone country codes (SIM, Network, Locale)
val countries = MzgsHelper.getPhoneCountry()

// Set IP-based country (async)
MzgsHelper.setIPCountry()

// Check if current country is allowed
MzgsHelper.setIsAllowedCountry()
if (MzgsHelper.isAllowedCountry) {
    // Country is allowed
}

// Set restricted countries from remote config
MzgsHelper.setRestrictedCountriesFromRemoteConfig()
```

#### Action Counter

Track and count user actions throughout your app:

```kotlin
// Increment a counter
ActionCounter.increase("button_clicks")

// Increment and get the new value
val clickCount = ActionCounter.increaseGet("button_clicks")
Log.d("Counter", "Button clicked $clickCount times")

// Get current count without incrementing
val currentCount = ActionCounter.get("button_clicks")

// Track with additional parameters (for analytics)
ActionCounter.increase("level_completed", mapOf(
    "level" to 5,
    "score" to 1500
))

// Initialize all saved counters (call in Application.onCreate)
ActionCounter.initAnalyticData()

// Get all tracked counter keys
val allKeys = ActionCounter.keys()
```

Common use cases:
- Track feature usage frequency
- Control show rates for ads/prompts
- Implement achievements or milestones
- Analytics and user behavior tracking

#### Preferences Helper & Counters

Simple key-value storage with type safety:

```kotlin
// Save values
Pref.set("username", "John Doe")
Pref.set("user_age", 25)
Pref.set("is_premium", true)
Pref.set("app_rating", 4.5f)
Pref.set("last_login", System.currentTimeMillis())
Pref.set("balance", 99.99)

// Get values with defaults
val username = Pref.get("username", "Guest")
val age = Pref.get("user_age", 0)
val isPremium = Pref.get("is_premium", false)
val rating = Pref.get("app_rating", 0.0f)
val lastLogin = Pref.get("last_login", 0L)
val balance = Pref.get("balance", 0.0)

// Type-specific getters
val name = Pref.getString("username", "Guest")
val premium = Pref.getBool("is_premium", false)
val userRating = Pref.getFloat("app_rating", 0.0f)
val userAge = Pref.getInt("user_age", 0)
val userBalance = Pref.getDouble("balance", 0.0)

// Check if key exists
if (Pref.exists("username")) {
    // User has set a username
}

// Remove a specific key
Pref.remove("temp_data")

// Clear all preferences
Pref.clearAll()
```

Common use cases:
- User settings and preferences
- App configuration
- Caching data
- First-time user detection
- Session management / analytics

#### Quick helpers

```kotlin
MzgsHelper.showToast("Hello World")
val online = MzgsHelper.isNetworkAvailable()
val versionName = MzgsHelper.getAppVersion()
val versionCode = MzgsHelper.getAppVersionCode()
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

## 📊 Firebase Analytics Integration

The library includes built-in Firebase Analytics support for tracking ad events and custom events.

### Initialize Firebase Analytics

```kotlin
// Initialize in your Application class or MainActivity
FirebaseAnalyticsManager.initialize()
```

### Log Custom Events

```kotlin
// Simple event logging
FirebaseAnalyticsManager.logEvent("button_clicked")

// Event with parameters
val params = Bundle().apply {
    putString("button_name", "purchase")
    putString("screen_name", "home")
    putInt("user_level", 5)
}
FirebaseAnalyticsManager.logEvent("custom_interaction", params)

// Log user actions
val actionParams = Bundle().apply {
    putString("action_type", "share")
    putString("content_type", "image")
    putString("content_id", "img_123")
}
FirebaseAnalyticsManager.logEvent("user_action", actionParams)
```
 

### Best Practices

1. **Event Naming**: Use lowercase with underscores (e.g., `user_signup`, `level_complete`)
2. **Parameter Limits**: Maximum 25 parameters per event, 40 characters for names, 100 for values
3. **Reserved Names**: Avoid Firebase reserved event/parameter names unless using them correctly
4. **User Properties**: Set user properties for segmentation:
   ```kotlin
   // Note: Access Firebase instance directly for user properties
   val firebaseAnalytics = FirebaseAnalytics.getInstance(context)
   firebaseAnalytics.setUserProperty("user_type", "premium")
   ```

### Automatic Ad Event Tracking

When using the Ads API, many events are automatically tracked:

- `onstart_ad_shown_admob` - When AdMob interstitial shows at app start
- `onstart_ad_shown_applovin` - When AppLovin interstitial shows at app start
- `onstart_ad_not_ready_all_networks` - When no ads are available at start
- `ad_load_event` - Tracks all ad load attempts with success/failure status
- `ad_impression` - Tracks when ads are displayed
- `ad_click` - Tracks when ads are clicked
- `ad_revenue` - Tracks estimated ad revenue

## 🔬 Debug & Test Controls

- `skipAdsInDebug` on `MzgsHelper.init(...)` turns off all ads for debug sessions
- `enableTestMode` swaps in Google test IDs for AdMob; `debugEmptyIds` zeros IDs to prevent loading anything in debug
- `testDeviceIds` (hashed) for AdMob and `testDeviceAdvertisingIds` (GAID) for AppLovin MAX enable device-scoped test traffic
- `enableTestCMP` plus `consentFlowPrivacyPolicyUrl`/`consentFlowTermsOfServiceUrl` forces AppLovin CMP test flow in debug
- Advertising ID helper logs your GAID when `Ads.init()` runs (copy into test device lists)

## ⚠️ Deprecated API Warnings

The library suppresses certain deprecated API warnings that are still the recommended approach for the current SDK versions:

- AppLovin SDK 13.x constructors (MaxAdView, MaxInterstitialAd, etc.)
- Android FLAG_FULLSCREEN (handled with backward compatibility)
- setColorFilter methods (handled with BlendModeColorFilter for API 29+)

These are handled internally with proper version checks and @Suppress annotations.

## 📋 Dependencies

The library includes these dependencies (exposed via `api` configuration):

- Google Mobile Ads SDK: 24.8.0
- AppLovin SDK: 13.5.1
- AppLovin Mediation adapters: Google 24.8.0.0, Unity 4.16.5.0, Facebook 6.21.0.0, Fyber 8.4.1.0, Vungle 7.6.1.0
- Firebase BOM: 34.6.0
- Firebase Analytics
- Google UMP: 4.0.0
- Play Review: 2.0.2
- Various AppLovin mediation adapters

## 🐛 Common Issues & Solutions

### BuildConfig.DEBUG not found
- **Solution**: Enable `buildConfig = true` in your app's buildFeatures, or replace with a boolean value

### LoadAdError/RewardItem type inference errors
- **Solution**: Add explicit type annotations: `{ error: LoadAdError ->` and `{ reward: RewardItem ->`

### Plugin version conflicts
- **Solution**: Ensure consistent Android Gradle Plugin version (8.12.3) across all modules and `com.google.gms.google-services` 4.4.4 at the root

### Google Services plugin not found
- **Solution**: Add `id("com.google.gms.google-services") version "4.4.4" apply false` to root build.gradle.kts

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
