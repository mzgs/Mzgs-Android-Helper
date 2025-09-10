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

### Required Additions

Add these to your root project `build.gradle.kts` plugins block:

```kotlin
id("com.google.gms.google-services") version "4.4.3" apply false
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

### Android Manifest

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

## 🎯 Quick Start

### 1. Initialize in Activity

```kotlin
class MainActivity : ComponentActivity() {
    private var isFullyInitialized = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize MzgsHelper
        MzgsHelper.init(this, BuildConfig.DEBUG, skipAdsInDebug = false)
        
        // Initialize other components
        FirebaseAnalyticsManager.initialize(this)
        Remote.init(this)
        Ads.init(this)
        MzgsHelper.setIPCountry()
        
        // Configure AdMob - All available parameters
        val admobConfig = AdMobConfig(
            appId = "ca-app-pub-XXXXX~XXXXX",  // Optional: Can be null if set in AndroidManifest.xml
            bannerAdUnitId = "",  // Leave empty for test ads
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            rewardedInterstitialAdUnitId = "",
            nativeAdUnitId = "",
            mrecAdUnitId = "",
            appOpenAdUnitId = "",
            enableAppOpenAd = false,  // Default: false
            bannerAutoRefreshSeconds = 60,  // Default: 60 (0 to disable auto-refresh)
            // Debug-only flags (only work when BuildConfig.DEBUG is true)
            testDeviceIds = emptyList(),  // Default: emptyList()
            enableTestMode = false,  // Default: false - Uses test ad unit IDs when true
            showAdsInDebug = true,  // Default: true - Master switch for all ads in debug
            showInterstitialsInDebug = true,  // Default: true
            showAppOpenAdInDebug = true,  // Default: true
            showBannersInDebug = true,  // Default: true
            showNativeAdsInDebug = true,  // Default: true
            showRewardedAdsInDebug = true,  // Default: true
            debugRequireConsentAlways = false,  // Default: false - Forces consent form in debug
            debugEmptyIds = false  // Default: false - Use empty ad unit IDs in debug (prevents real ad loading)
        )
        
        // Configure AppLovin - All available parameters
        val appLovinConfig = AppLovinConfig(
            sdkKey = "YOUR_SDK_KEY",  // Required
            bannerAdUnitId = "",  // Default: ""
            mrecAdUnitId = "",  // Default: ""
            interstitialAdUnitId = "",  // Default: ""
            rewardedAdUnitId = "",  // Default: ""
            appOpenAdUnitId = "",  // Default: ""
            nativeAdUnitId = "",  // Default: ""
            enableAppOpenAd = false,  // Default: false
            muteAudio = false,  // Default: false
            // Debug-only flags (only work when BuildConfig.DEBUG is true)
            enableTestMode = false,  // Default: false
            verboseLogging = false,  // Default: false - Note: Currently only logged, not applied to SDK
            creativeDebuggerEnabled = false,  // Default: false - Note: Currently only logged, not applied to SDK
            testDeviceAdvertisingIds = emptyList(),  // Default: emptyList()
            showAdsInDebug = true,  // Default: true - Master switch for all ads in debug
            showInterstitialsInDebug = true,  // Default: true
            showAppOpenAdInDebug = true,  // Default: true
            showBannersInDebug = true,  // Default: true
            showNativeAdsInDebug = true,  // Default: true
            showRewardedAdsInDebug = true,  // Default: true
            debugEmptyIds = false  // Default: false - Use empty ad unit IDs in debug (prevents real ad loading)
        )
        
        // Show splash with interstitial
        MzgsHelper.initSplashWithInterstitialShow(
            activity = this,
            admobConfig = admobConfig,
            appLovinConfig = appLovinConfig,
            defaultSplashTime = 10000,
            onSplashCompleteAdClosed = {
                
            },
            onCompleteWithAdsReady = {
                // Preload ads
                isFullyInitialized.value = true
                AdMobManager.loadRewardedAd()
                AppLovinMediationManager.loadRewardedAd()
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

### 4. Utility Features

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

#### Preferences Helper

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
- Session management

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
FirebaseAnalyticsManager.initialize(context)
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