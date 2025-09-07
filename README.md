# Mzgs-Android-Helper
Android helper library with utility tools and comprehensive ad mediation support (AdMob & AppLovin MAX)

## 🚀 Features

- 🛠️ **General utility functions** (Toast, Network status, App version, Remote Config)
- 📱 **Complete AdMob mediation implementation** with all ad formats
- 🎯 **AppLovin MAX integration** with mediation adapters
- 🔐 **Built-in UMP consent management** for GDPR/CCPA compliance
- 🎨 **Unified Ads API** - Single interface for both ad networks
- ⚡ **Automatic test ad handling** in debug mode
- 🖼️ **App Open Ads** with lifecycle management
- 📊 **Firebase Analytics** integration for ad events
- 🔄 **Dual mediation support** (AdMob + AppLovin MAX)

## 📦 Installation

### Using JitPack

**Important:** This library requires the Mintegral Maven repository for AppLovin mediation adapters.

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
    implementation("com.github.mzgs:Mzgs-Android-Helper:v1.0.7")
}
```

⚠️ **Note:** The Mintegral repository is mandatory. Without it, you'll get build errors related to AppLovin mediation adapters.

### Local Module

If you're using it as a local module in your project:

```kotlin
dependencies {
    implementation(project(":mzgshelper"))
}
```

## 📚 Required Libraries & Setup

### If Using in Your App

When integrating this library into your app, you'll need to add certain dependencies and configurations depending on which features you use:

### 1. Google Services Plugin (Required for Firebase Features)

If you're using Firebase features (Analytics, Remote Config), add the Google Services plugin:

**In your app's `build.gradle.kts`:**
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services") // Add this line
}
```

**In your project's root `build.gradle.kts` or `settings.gradle.kts`:**
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

**Add `google-services.json`:**
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create or select your project
3. Add your Android app
4. Download `google-services.json`
5. Place it in your `app/` directory

### 2. AdMob SDK (Already Included)

The AdMob SDK is already included and exposed by the library, so you don't need to add it separately. You can directly use AdMob classes like `LoadAdError` and `RewardItem` in your app.

### 3. Firebase Analytics (Already Included)

Firebase Analytics is also included in the library, so you don't need to add it separately. It's automatically available when you use the library.

### 4. Permissions in AndroidManifest.xml

Add these permissions to your app's `AndroidManifest.xml`:

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

### 5. Complete Example Dependencies

Here's a complete example of dependencies for an app using all features:

```kotlin
// app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

dependencies {
    // The library itself
    implementation("com.github.mzgs:Mzgs-Android-Helper:v1.0.7")
    // OR for local module
    // implementation(project(":mzgshelper"))
    
    // That's it! AdMob SDK and Firebase are already included in the library
    
    // Your other dependencies...
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.compose.ui:ui:1.5.4")
    // etc...
}
```

### Common Issues & Solutions

**Issue: "Cannot access class 'LoadAdError'"**
- **Solution:** This should not occur with v1.0.7+. If using an older version or local module, ensure the library uses `api` instead of `implementation` for play-services-ads dependency

**Issue: "Plugin [id: 'com.google.gms.google-services'] was not found"**
- **Solution:** Add the plugin to your project-level build.gradle.kts as shown above

**Issue: "File google-services.json is missing"**
- **Solution:** Download from Firebase Console and place in your app/ directory

**Issue: "The google-services Gradle plugin needs to be applied on a project with com.android.application"**
- **Solution:** Only apply the plugin to your app module, not library modules

## 🎯 Quick Start with Unified Ads API

### 1. Initialize in Activity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Ads helper
        Ads.init(this)
        
        // Configure AdMob
        val admobConfig = AdMobConfig(
            appId = "ca-app-pub-XXXXX~XXXXX",
            bannerAdUnitId = "", // Leave empty for test ads in debug
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            nativeAdUnitId = "",
            mrecAdUnitId = "", // MREC (300x250) ad unit
            appOpenAdUnitId = "",
            enableAppOpenAd = true,
            enableTestMode = true, // Automatically uses test ads
            testDeviceIds = listOf("YOUR_DEVICE_ID")
        )
        
        // Configure AppLovin
        val appLovinConfig = AppLovinConfig(
            sdkKey = "YOUR_SDK_KEY",
            bannerAdUnitId = "",
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            enableTestMode = true
        )
        
        // Initialize both networks
        Ads.initBothNetworks(
            admobConfig,
            appLovinConfig,
            onBothInitComplete = {
                Log.d("Ads", "Both networks ready")
            }
        )
    }
}
```

### 2. Show Ads with Unified API

```kotlin
// Show interstitial (automatically selects best available network)
if (Ads.showInterstitial()) {
    Log.d("Ads", "Interstitial shown")
}

// Show interstitial with cycle control
// Shows ad every N times based on remote config or default value
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

// Show App Open Ad
Ads.showAppOpenAd()
```

## 🔄 Interstitial Ads with Cycle Control

The `showInterstitialWithCycle` method provides intelligent frequency capping for interstitial ads, preventing ad fatigue while maintaining monetization.

### How It Works

This method tracks how many times a specific action occurs and only shows an interstitial ad every N times, where N is configurable via Firebase Remote Config.

### Usage

```kotlin
// Basic usage with default cycle
Ads.showInterstitialWithCycle("button_click", 3) // Shows ad every 3rd click

// Different cycles for different actions
Ads.showInterstitialWithCycle("level_complete", 2)  // Every 2 levels
Ads.showInterstitialWithCycle("item_viewed", 5)     // Every 5 items viewed
Ads.showInterstitialWithCycle("search_performed", 4) // Every 4 searches
```

### Parameters

- `name`: String - Unique identifier for the action being tracked
- `defaultValue`: Int - Default cycle count (used when remote config value is not available)

### Remote Configuration

Configure cycle values in Firebase Remote Config:

```json
{
  "button_click": 3,      // Show ad every 3 button clicks
  "level_complete": 2,    // Show ad every 2 levels completed
  "item_viewed": 5,       // Show ad every 5 items viewed
  "search_performed": 4   // Show ad every 4 searches
}
```

### Example Implementation

```kotlin
class GameActivity : AppCompatActivity() {
    
    // Level completion with cycle control
    fun onLevelComplete() {
        // Show interstitial every 2 levels (or value from remote config)
        Ads.showInterstitialWithCycle("level_complete", 2)
        
        // Navigate to next level
        startNextLevel()
    }
    
    // Button click with cycle control
    fun onSpecialButtonClick() {
        // Show interstitial every 3 clicks (or value from remote config)
        Ads.showInterstitialWithCycle("special_button", 3)
        
        // Perform button action
        performButtonAction()
    }
    
    // Product view with cycle control
    fun onProductViewed(productId: String) {
        // Track product view
        trackProductView(productId)
        
        // Show interstitial every 5 product views
        Ads.showInterstitialWithCycle("product_view", 5)
    }
}
```

### Compose Example

```kotlin
@Composable
fun ContentList() {
    var itemClickCount by remember { mutableStateOf(0) }
    
    LazyColumn {
        items(contentItems) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Handle item click
                        navigateToDetail(item)
                        
                        // Show interstitial every 4 item clicks
                        Ads.showInterstitialWithCycle("content_item_click", 4)
                    }
            ) {
                // Content item UI
                Text(item.title)
            }
        }
    }
}
```

### Benefits

1. **Prevents Ad Fatigue**: Users don't see ads too frequently
2. **Maintains Revenue**: Ensures ads are still shown regularly
3. **Flexible Control**: Adjust frequency via remote config without app update
4. **Action-Specific**: Different frequencies for different user actions
5. **Persistent Tracking**: Counter persists across app sessions

### How Counting Works

- Each action has its own independent counter
- Counters are stored persistently using SharedPreferences
- Counter increments with each call
- Ad shows when: `counter % cycleValue == 0`
- Example: With cycle=3, ads show on counts 3, 6, 9, 12, etc.

### Best Practices

1. **Choose Appropriate Cycles**: 
   - Frequent actions: Higher cycles (5-10)
   - Rare actions: Lower cycles (2-3)

2. **Use Descriptive Names**:
   - Good: "level_complete", "search_performed", "item_purchased"
   - Avoid: "action1", "click", "event"

3. **Monitor Performance**:
   - Track ad revenue vs user retention
   - Adjust cycles based on analytics

4. **Test Different Values**:
   - A/B test different cycle values
   - Find optimal balance for your app

## 📱 App Open Ads

App Open ads automatically show when users return to your app from the background.

### Configuration

```kotlin
val admobConfig = AdMobConfig(
    appId = "ca-app-pub-XXXXX~XXXXX",
    mrecAdUnitId = "ca-app-pub-XXXXX/XXXXX", // Your MREC ad unit ID
    appOpenAdUnitId = "ca-app-pub-XXXXX/XXXXX", // Your App Open ad unit ID
    enableAppOpenAd = true, // Enable App Open ads
    enableTestMode = true, // Uses test ad in debug: ca-app-pub-3940256099942544/9257395921
    showAppOpenAdInDebug = true // Control showing in debug mode
)

// Initialize
Ads.init(this)
Ads.initAdMob(admobConfig)
```

### How App Open Ads Work

1. **Automatic Loading**: Ads are preloaded when app goes to background
2. **Automatic Display**: Shows when user returns to foreground (not on first launch)
3. **Test Mode**: Automatically uses Google's test App Open ad ID in debug mode
4. **Lifecycle Aware**: Fully integrated with Android lifecycle

## 🎨 Adaptive Banner in Lists/Search Results

Perfect for integrating ads naturally within content lists or search results.

### Example: Search Results with Adaptive Banner

```kotlin
@Composable
fun SearchResultsWithAds() {
    var searchQuery by remember { mutableStateOf("") }
    var showResults by remember { mutableStateOf(false) }
    
    Column {
        // Search input
        TextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                showResults = it.isNotEmpty()
            },
            placeholder = { Text("Search products...") },
            leadingIcon = { Icon(Icons.Default.Search, "Search") }
        )
        
        // Show results with ads
        if (showResults) {
            LazyColumn {
                items(searchResults.size) { index ->
                    when {
                        // Show adaptive banner as 2nd item
                        index == 1 -> {
                            AdaptiveBannerItem()
                        }
                        // Show another ad every 5 items
                        index > 0 && index % 5 == 0 -> {
                            NativeAdItem()
                        }
                        else -> {
                            SearchResultItem(searchResults[index])
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdaptiveBannerItem() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column {
            Text(
                "Sponsored",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Adaptive banner that adjusts to screen width
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context ->
                    FrameLayout(context).apply {
                        Ads.showBanner(this, Ads.BannerSize.ADAPTIVE)
                    }
                }
            )
        }
    }
}
```

### Dynamic Ad Placement in Lists

```kotlin
@Composable
fun ContentFeedWithAds() {
    LazyColumn {
        itemsIndexed(contentItems) { index, item ->
            // Regular content item
            ContentCard(item)
            
            // Insert ads at strategic positions
            when {
                // First ad after 3 items
                index == 2 -> {
                    AdaptiveBannerItem()
                }
                // Then every 8 items
                index > 2 && (index - 2) % 8 == 0 -> {
                    // Alternate between banner and native ads
                    if ((index - 2) % 16 == 0) {
                        NativeAdItem()
                    } else {
                        AdaptiveBannerItem()
                    }
                }
            }
        }
    }
}
```

## 🎯 Test Mode Configuration

The library automatically handles test ads in debug mode:

### How Test Mode Works

```kotlin
val config = AdMobConfig(
    appOpenAdUnitId = "", // Can be empty
    enableTestMode = true, // Enable test mode
    enableAppOpenAd = true
)

// In debug mode with enableTestMode = true:
// - Automatically uses Google's test ad unit IDs
// - App Open: ca-app-pub-3940256099942544/9257395921
// - Banner: ca-app-pub-3940256099942544/6300978111
// - Interstitial: ca-app-pub-3940256099942544/1033173712
// - Rewarded: ca-app-pub-3940256099942544/5224354917
// - Native: ca-app-pub-3940256099942544/2247696110
// - MREC: ca-app-pub-3940256099942544/6300978111

// In release mode or enableTestMode = false:
// - Uses your actual ad unit IDs
```

## 🚀 Complete Integration Example

### Splash Screen with Ads

```kotlin
class MainActivity : ComponentActivity() {
    private var isSplashComplete = mutableStateOf(false)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize everything
        MzgsHelper.init(this)
        FirebaseAnalyticsManager.initialize(this)
        Remote.init(this)
        Ads.init(this)
        MzgsHelper.setIPCountry()
        
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
            enableTestMode = BuildConfig.DEBUG,
            testDeviceIds = listOf("YOUR_DEVICE_ID"),
            showAdsInDebug = true,
            showInterstitialsInDebug = true,
            showAppOpenAdInDebug = true,
            showBannersInDebug = true,
            showNativeAdsInDebug = true,
            showRewardedAdsInDebug = true
        )
        
        val appLovinConfig = AppLovinConfig(
            sdkKey = "YOUR_SDK_KEY",
            bannerAdUnitId = "",
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            mrecAdUnitId = "",
            nativeAdUnitId = "",
            enableTestMode = BuildConfig.DEBUG,
            verboseLogging = true,
            creativeDebuggerEnabled = true,
            showAdsInDebug = true,
            testDeviceAdvertisingIds = listOf("YOUR_DEVICE_ID")
        )
        
        // Show splash with automatic ad display
        MzgsHelper.initSplashWithAdmobShow(
            activity = this,
            admobConfig = admobConfig,
            appLovinConfig = appLovinConfig,
            defaultSplashTime = 10000, // Optional: default splash duration in milliseconds (default: 10000)
            onFinish = {
                Log.d("MainActivity", "Splash and ad sequence completed")
                MzgsHelper.setRestrictedCountriesFromRemoteConfig()
                MzgsHelper.setIsAllowedCountry()
            },
            onFinishAndApplovinReady = {
                Log.d("MainActivity", "AppLovin SDK initialized successfully")
                isSplashComplete.value = true
                // Preload ads for better performance
                AppLovinMediationManager.loadInterstitialAd()
            }
        )
        
        setContent {
            MyApp(isSplashComplete = isSplashComplete.value)
        }
    }
}
```

### Using isSplashComplete to Show Ads

```kotlin
@Composable
fun MyApp(isSplashComplete: Boolean) {
    MzgsAndroidHelperTheme {
        Scaffold(
            bottomBar = {
                // Show banner ad only after splash is complete
                if (isSplashComplete) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { context ->
                                FrameLayout(context).apply {
                                    // Load adaptive banner
                                    Ads.showBanner(this, Ads.BannerSize.ADAPTIVE)
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Your main content here
                Text("Welcome to the app!")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Show MREC ad in content only after splash is complete
                if (isSplashComplete) {
                    Card(
                        modifier = Modifier
                            .width(300.dp)
                            .height(250.dp)
                            .align(Alignment.CenterHorizontally),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                FrameLayout(context).apply {
                                    // Load MREC ad
                                    Ads.showMREC(this)
                                }
                            }
                        )
                    }
                }
                
                // Alternative: Conditional ad loading in LazyColumn
                LazyColumn {
                    item {
                        Text("Content Item 1")
                    }
                    
                    // Insert ads only after splash
                    if (isSplashComplete) {
                        item {
                            // Adaptive banner between content
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { context ->
                                    FrameLayout(context).apply {
                                        Ads.showBanner(this, Ads.BannerSize.ADAPTIVE)
                                    }
                                }
                            )
                        }
                    }
                    
                    items(10) { index ->
                        Text("Content Item ${index + 2}")
                    }
                }
            }
        }
    }
}
```

### Why Use isSplashComplete?

1. **Prevents Ad Loading Conflicts**: Ensures splash screen ad completes before showing other ads
2. **Better User Experience**: Avoids overwhelming users with multiple ads at startup
3. **Performance**: Prevents simultaneous ad requests that could slow app launch
4. **Ad Policy Compliance**: Helps maintain proper ad spacing and frequency

## 📊 Ad Formats Reference

### AdMob Banner Sizes

| Format | Size (dp) | Usage |
|--------|-----------|--------|
| BANNER | 320x50 | Standard banner |
| LARGE_BANNER | 320x100 | Large banner |
| MEDIUM_RECTANGLE (MREC) | 300x250 | Medium rectangle - use dedicated mrecAdUnitId |
| FULL_BANNER | 468x60 | Tablet full banner |
| LEADERBOARD | 728x90 | Tablet leaderboard |
| ADAPTIVE | Flexible | **Recommended** - Adapts to screen width |

### Adaptive Banner Best Practices

1. **Use in scrollable content**: Place between content items
2. **Maintain spacing**: Add padding around ads
3. **Label as ads**: Always indicate sponsored content
4. **Frequency**: Show every 5-8 content items
5. **First ad placement**: After 2-3 content items

## 🔧 Advanced Features

### Splash Screen with AdMob

The `initSplashWithAdmobShow` method provides a complete splash screen experience with automatic AdMob interstitial ad display:

```kotlin
MzgsHelper.initSplashWithAdmobShow(
    activity = this,
    admobConfig = admobConfig,
    appLovinConfig = appLovinConfig,
    defaultSplashTime = 10000, // Optional: splash duration in ms (default: 10000)
    onFinish = {
        // Called when splash and ad sequence is complete
    },
    onFinishAndApplovinReady = {
        // Called when both splash is complete AND AppLovin is initialized
    }
)
```

#### Parameters:
- `activity`: The ComponentActivity instance
- `admobConfig`: AdMob configuration object
- `appLovinConfig`: AppLovin configuration object  
- `defaultSplashTime`: Optional splash duration in milliseconds (default: 10000ms/10 seconds)
  - This value is used as fallback when remote config "splash_time" is not available
  - Can be overridden by remote configuration
- `onFinish`: Callback when splash and ad sequence completes
- `onFinishAndApplovinReady`: Callback when both splash completes AND AppLovin SDK is ready

### Remote Configuration

```kotlin
// Initialize Remote Config
Remote.init(context)

// Fetch remote values
Remote.fetchRemoteValues(
    onSuccess = {
        val adFrequency = Remote.getInt("ad_frequency", 5)
        val showAds = Remote.getBoolean("show_ads", true)
    }
)
```

### Firebase Analytics for Ads

```kotlin
// Initialize Firebase Analytics
FirebaseAnalyticsManager.initialize(context)

// Automatically tracked ad events:
// - ad_impression
// - ad_clicked
// - ad_load_success
// - ad_load_failed
// - ad_dismissed
// - ad_reward_earned

// Custom event logging examples:

// Simple event without parameters
FirebaseAnalyticsManager.logEvent("app_opened")

// Event with custom parameters
val params = Bundle().apply {
    putString("button_name", "purchase")
    putString("screen_name", "home")
    putInt("button_index", 1)
}
FirebaseAnalyticsManager.logEvent("button_clicked", params)

// User action tracking
val actionParams = Bundle().apply {
    putString("action_type", "share")
    putString("content_type", "product")
    putString("content_id", "12345")
}
FirebaseAnalyticsManager.logEvent("user_action", actionParams)

// Screen view tracking
val screenParams = Bundle().apply {
    putString(FirebaseAnalytics.Param.SCREEN_NAME, "product_detail")
    putString(FirebaseAnalytics.Param.SCREEN_CLASS, "ProductDetailActivity")
}
FirebaseAnalyticsManager.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, screenParams)

// Purchase event
val purchaseParams = Bundle().apply {
    putString(FirebaseAnalytics.Param.CURRENCY, "USD")
    putDouble(FirebaseAnalytics.Param.VALUE, 9.99)
    putString(FirebaseAnalytics.Param.ITEM_ID, "premium_upgrade")
}
FirebaseAnalyticsManager.logEvent(FirebaseAnalytics.Event.PURCHASE, purchaseParams)

// Level completion in a game
val levelParams = Bundle().apply {
    putString(FirebaseAnalytics.Param.LEVEL_NAME, "level_5")
    putInt("score", 1500)
    putInt("time_seconds", 120)
    putBoolean("first_attempt", true)
}
FirebaseAnalyticsManager.logEvent("level_completed", levelParams)

// Search event
val searchParams = Bundle().apply {
    putString(FirebaseAnalytics.Param.SEARCH_TERM, "kotlin tutorial")
    putInt("results_count", 25)
}
FirebaseAnalyticsManager.logEvent(FirebaseAnalytics.Event.SEARCH, searchParams)

// Ad-specific tracking (automatically handled by the library)
FirebaseAnalyticsManager.logAdLoadSuccess(
    adType = "interstitial",
    adUnitId = "ca-app-pub-xxx",
    adNetwork = "admob"
)

FirebaseAnalyticsManager.logAdRevenue(
    adType = "rewarded",
    adUnitId = "ca-app-pub-xxx",
    revenue = 0.05,
    currency = "USD",
    adNetwork = "admob"
)
```

### Consent Management

```kotlin
// Request consent (GDPR/CCPA)
AdMobMediationManager.requestConsentInfo(
    debugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
    onConsentReady = {
        // Can show ads
    }
)
```

## 🧪 Testing

### Getting Your Device ID

1. Run the app with the library initialized
2. Check logcat for: `YOUR DEVICE ADVERTISING ID`
3. Copy the GAID and add to config:

```kotlin
val config = AdMobConfig(
    testDeviceIds = listOf("your-device-id-here"),
    enableTestMode = true
)
```

### Test Ad Unit IDs (Automatically Used)

```kotlin
// AdMob Test IDs (used when enableTestMode = true)
Banner: ca-app-pub-3940256099942544/6300978111
Interstitial: ca-app-pub-3940256099942544/1033173712
Rewarded: ca-app-pub-3940256099942544/5224354917
Native: ca-app-pub-3940256099942544/2247696110
MREC: ca-app-pub-3940256099942544/6300978111
App Open: ca-app-pub-3940256099942544/9257395921
```

## 📱 Memory Management

The library handles memory management automatically:

- Uses WeakReference for context storage
- Automatic lifecycle management
- Proper ad cleanup on destroy
- No manual cleanup required in most cases

## ⚙️ Configuration Options

### Debug Control

```kotlin
val config = AdMobConfig(
    // Debug-only flags (only work in debug builds)
    showAdsInDebug = true,           // Master switch
    showInterstitialsInDebug = false, // Disable interstitials while testing
    showAppOpenAdInDebug = true,      // Control app open ads
    showBannersInDebug = true,        // Control banners
    showNativeAdsInDebug = true,      // Control native ads
    showRewardedAdsInDebug = true     // Control rewarded ads
)
```

## 📄 AndroidManifest.xml Setup

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="com.google.android.gms.permission.AD_ID"/>

<application>
    <!-- AdMob App ID (Required) -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
    
    <!-- AppLovin SDK Key (Optional - only if using AppLovin) -->
    <meta-data
        android:name="applovin.sdk.key"
        android:value="YOUR_SDK_KEY_HERE" />
</application>
```

## 🔌 Google Services Plugin Setup

The Google Services plugin is required for Firebase integration (Analytics, Remote Config). 

### Important: Application Module Only

The `com.google.gms.google-services` plugin must be applied **only to application modules** (`com.android.application`), not to library modules (`com.android.library`).

### Setup Instructions

1. **Add the plugin to your app's `build.gradle.kts`** (not the library module):

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services") // Add this line
}
```

2. **Add the Google Services classpath** to your project's `build.gradle.kts`:

```kotlin
// build.gradle.kts (Project level)
plugins {
    // ... other plugins
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

Or if using version catalogs in `gradle/libs.versions.toml`:

```toml
[versions]
googleServices = "4.4.2"

[plugins]
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

3. **Add your `google-services.json` file**:
   - Download from Firebase Console
   - Place in `app/google-services.json` (at app module root)

### Why Application Module Only?

- Firebase services require an application context
- The plugin generates application-specific resources
- Library modules should remain application-agnostic
- This prevents build errors: "The google-services Gradle plugin needs to be applied on a project with com.android.application"

## 🔍 Troubleshooting

### App Open Ads Not Showing

1. **Check ad unit ID**: Ensure it's not empty (unless using test mode)
2. **Verify enableAppOpenAd = true** in config
3. **Test mode**: Set `enableTestMode = true` for automatic test ads
4. **Check logs**: Look for "App open ad" messages in logcat
5. **Background/Foreground**: Ads only show when returning from background

### Ads Not Loading

1. Check network connection
2. Verify consent status
3. Enable debug logging
4. Check ad unit IDs
5. For test ads, ensure `enableTestMode = true`

## 📋 Requirements

- Minimum SDK: 24 (Android 7.0)
- Target SDK: 35 (Android 15)
- Kotlin: 1.9+
- Compose: Latest stable
- Google Mobile Ads SDK: 24.0.0+
- AppLovin MAX SDK: Latest version

## 📜 License

[Add your license here]