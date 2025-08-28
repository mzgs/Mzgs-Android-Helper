# Mzgs-Android-Helper
Android helper library with utility tools and comprehensive ad mediation support (AdMob & AppLovin MAX)

## Features

- 🛠️ General utility functions (Toast, Network status, App version)
- 📱 Complete AdMob mediation implementation
- 🎯 Support for all ad formats (Banner, Interstitial, Rewarded, Native, App Open)
- 🔐 Built-in UMP consent management
- 🚀 AppLovin MAX mediation adapters included
- 🔄 Dual mediation support (AdMob + AppLovin MAX)
- ⚡ Simplified API with automatic ad unit ID resolution from config

## Installation

### Add to your project

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":mzgshelper"))
}
```

## Quick Start

### 1. Initialize in Application Class

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob with config
        val adMobConfig = AdMobConfig(
            bannerAdUnitId = "YOUR_BANNER_ID",
            interstitialAdUnitId = "YOUR_INTERSTITIAL_ID",
            rewardedAdUnitId = "YOUR_REWARDED_ID",
            nativeAdUnitId = "YOUR_NATIVE_ID",
            appOpenAdUnitId = "YOUR_APP_OPEN_ID",
            enableTestMode = BuildConfig.DEBUG // Use test ads in debug
        )
        
        AdMobMediationManager.initialize(
            context = this,
            config = adMobConfig,
            onInitComplete = {
                Log.d("AdMob", "Initialized")
            }
        )
        
        // Initialize AppLovin with config
        val appLovinConfig = AppLovinConfig(
            bannerAdUnitId = "YOUR_BANNER_ID",
            interstitialAdUnitId = "YOUR_INTERSTITIAL_ID",
            rewardedAdUnitId = "YOUR_REWARDED_ID",
            enableTestMode = BuildConfig.DEBUG
        )
        
        AppLovinMediationManager.initialize(
            context = this,
            config = appLovinConfig,
            onInitComplete = {
                Log.d("AppLovin", "Initialized")
            }
        )
    }
}
```

### 2. Initialize in Activity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Set current activity for simplified show methods
        AdMobMediationManager.setCurrentActivity(this)
        AppLovinMediationManager.setCurrentActivity(this)
        
        // Request consent (AdMob)
        AdMobMediationManager.requestConsentInfo(
            onConsentReady = {
                // Start loading ads
                loadAds()
            }
        )
        
        setContent {
            MyApp()
        }
    }
    
    private fun loadAds() {
        // Load interstitial (auto-uses config's ad unit ID)
        AdMobMediationManager.loadInterstitialAd(
            onAdLoaded = { Log.d("Ad", "Interstitial ready") }
        )
        
        // Load rewarded (auto-uses config's ad unit ID)
        AdMobMediationManager.loadRewardedAd(
            onAdLoaded = { Log.d("Ad", "Rewarded ready") }
        )
    }
}
```

## AdMob Integration

### Simplified Banner Ads (Compose)

```kotlin
@Composable
fun MyScreen() {
    Scaffold(
        bottomBar = {
            // Automatically uses ad unit ID from config
            AdMobBanner(
                modifier = Modifier.fillMaxWidth(),
                isAdaptive = true // Default is true
            )
        }
    ) { paddingValues ->
        // Your content
    }
}

// Or with explicit ad unit ID
@Composable
fun CustomBanner() {
    AdMobBanner(
        adUnitId = "ca-app-pub-XXXXX/XXXXX",
        modifier = Modifier.fillMaxWidth(),
        isAdaptive = true
    )
}
```

### Inline Adaptive Banner in Scrollable Content

```kotlin
@Composable
fun ContentScreen() {
    LazyColumn {
        item {
            Text("Article Title", style = MaterialTheme.typography.headlineLarge)
        }
        
        item {
            Text("Some content paragraph...")
        }
        
        // Inline adaptive banner
        item {
            AdMobBanner(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                isAdaptive = true
            )
        }
        
        item {
            Text("More content...")
        }
    }
}
```

### Simplified Interstitial Ads

```kotlin
// Load interstitial (uses config's ad unit ID)
AdMobMediationManager.loadInterstitialAd(
    onAdLoaded = {
        Log.d("Ad", "Interstitial loaded")
    },
    onAdFailedToLoad = { error ->
        Log.e("Ad", "Failed: ${error.message}")
    }
)

// Show when ready (uses current activity set in init)
if (AdMobMediationManager.isInterstitialReady()) {
    AdMobMediationManager.showInterstitialAd()
}

// Or with explicit ad unit ID
AdMobMediationManager.loadInterstitialAd(
    adUnitId = "ca-app-pub-XXXXX/XXXXX",
    onAdLoaded = { /* ... */ }
)
```

### Simplified Rewarded Ads

```kotlin
// Load rewarded ad (uses config's ad unit ID)
AdMobMediationManager.loadRewardedAd(
    onAdLoaded = {
        Log.d("Ad", "Rewarded ad ready")
    }
)

// Show when ready (uses current activity)
if (AdMobMediationManager.isRewardedAdReady()) {
    AdMobMediationManager.showRewardedAd(
        onUserEarnedReward = { rewardItem ->
            val amount = rewardItem.amount
            val type = rewardItem.type
            Log.d("Reward", "User earned $amount $type")
        }
    )
}
```

### Simplified Native Ads

```kotlin
@Composable
fun NativeAdView() {
    val context = LocalContext.current
    val helper = remember { AdMobNativeAdHelper(context) }
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    
    LaunchedEffect(Unit) {
        // Uses config's native ad unit ID
        helper.loadNativeAd(
            onAdLoaded = { ad ->
                nativeAd = ad
            }
        )
    }
    
    DisposableEffect(Unit) {
        onDispose {
            helper.destroy()
        }
    }
    
    nativeAd?.let { ad ->
        // Display your native ad UI
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Ad", style = MaterialTheme.typography.labelSmall)
                ad.headline?.let { 
                    Text(it, style = MaterialTheme.typography.headlineSmall)
                }
                ad.body?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                ad.callToAction?.let { cta ->
                    Button(onClick = {}) {
                        Text(cta)
                    }
                }
            }
        }
    }
}
```

### Splash Screen with Auto Interstitial

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Initialize ads
    AdMobMediationManager.setCurrentActivity(this)
    
    // Load interstitial for splash
    AdMobMediationManager.loadInterstitialAd()
    
    // Setup splash screen
    SimpleSplashHelper.Builder(this)
        .setDuration(3000) // 3 seconds
        .showProgress(true)
        .onComplete {
            // Show interstitial after splash if ready
            if (AdMobMediationManager.isInterstitialReady()) {
                AdMobMediationManager.showInterstitialAd()
            }
            // Navigate to main screen
            startMainActivity()
        }
        .build()
        .show()
}
```

## AppLovin MAX Integration

### Simplified Banner Ads (Compose)

```kotlin
@Composable
fun AppLovinBannerExample() {
    // Uses config's banner ad unit ID
    AppLovinBannerView(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.Gray
    )
}

// Or with explicit ad unit ID
@Composable
fun CustomAppLovinBanner() {
    AppLovinBannerView(
        adUnitId = "YOUR_BANNER_AD_UNIT_ID",
        modifier = Modifier.fillMaxWidth()
    )
}
```

### Simplified MREC Ads

```kotlin
@Composable
fun AppLovinMRECExample() {
    LazyColumn {
        item { Text("Content above MREC") }
        
        // MREC ad (uses config's MREC ad unit ID)
        item {
            AppLovinMRECView(
                modifier = Modifier.padding(16.dp),
                backgroundColor = Color.LightGray
            )
        }
        
        item { Text("Content below MREC") }
    }
}
```

### Simplified Interstitial & Rewarded

```kotlin
// Load interstitial (uses config)
AppLovinMediationManager.loadInterstitialAd(
    onAdLoaded = { Log.d("AppLovin", "Interstitial ready") }
)

// Show when ready
if (AppLovinMediationManager.isInterstitialReady()) {
    AppLovinMediationManager.showInterstitialAd()
}

// Load rewarded (uses config)
AppLovinMediationManager.loadRewardedAd(
    onAdLoaded = { Log.d("AppLovin", "Rewarded ready") }
)

// Show rewarded
if (AppLovinMediationManager.isRewardedAdReady()) {
    AppLovinMediationManager.showRewardedAd(
        onUserEarnedReward = { reward ->
            Log.d("Reward", "User earned: ${reward.amount} ${reward.label}")
        }
    )
}
```

## Complete Example App

```kotlin
@Composable
fun CompleteAdExample() {
    var showInterstitial by remember { mutableStateOf(false) }
    var showRewarded by remember { mutableStateOf(false) }
    var coins by remember { mutableStateOf(0) }
    
    // Load ads on first composition
    LaunchedEffect(Unit) {
        // These use config's ad unit IDs automatically
        AdMobMediationManager.loadInterstitialAd()
        AdMobMediationManager.loadRewardedAd()
        AppLovinMediationManager.loadInterstitialAd()
        AppLovinMediationManager.loadRewardedAd()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ad Example - Coins: $coins") }
            )
        },
        bottomBar = {
            // Adaptive banner at bottom (uses config)
            AdMobBanner(
                modifier = Modifier.fillMaxWidth(),
                isAdaptive = true
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            // AdMob Section
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AdMob Ads", style = MaterialTheme.typography.headlineMedium)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    if (AdMobMediationManager.isInterstitialReady()) {
                                        AdMobMediationManager.showInterstitialAd()
                                        // Reload for next time
                                        AdMobMediationManager.loadInterstitialAd()
                                    }
                                }
                            ) {
                                Text("Show Interstitial")
                            }
                            
                            Button(
                                onClick = {
                                    if (AdMobMediationManager.isRewardedAdReady()) {
                                        AdMobMediationManager.showRewardedAd(
                                            onUserEarnedReward = { reward ->
                                                coins += reward.amount
                                                // Reload for next time
                                                AdMobMediationManager.loadRewardedAd()
                                            }
                                        )
                                    }
                                }
                            ) {
                                Text("Watch for Coins")
                            }
                        }
                    }
                }
            }
            
            // Native Ad
            item {
                NativeAdView()
            }
            
            // AppLovin Section
            item {
                Card {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("AppLovin MAX", style = MaterialTheme.typography.headlineMedium)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = {
                                    if (AppLovinMediationManager.isInterstitialReady()) {
                                        AppLovinMediationManager.showInterstitialAd()
                                        AppLovinMediationManager.loadInterstitialAd()
                                    }
                                }
                            ) {
                                Text("Show Interstitial")
                            }
                            
                            Button(
                                onClick = {
                                    if (AppLovinMediationManager.isRewardedAdReady()) {
                                        AppLovinMediationManager.showRewardedAd(
                                            onUserEarnedReward = { reward ->
                                                coins += reward.amount
                                                AppLovinMediationManager.loadRewardedAd()
                                            }
                                        )
                                    }
                                }
                            ) {
                                Text("Watch for Coins")
                            }
                        }
                    }
                }
            }
            
            // MREC Ad
            item {
                AppLovinMRECView(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color.LightGray
                )
            }
            
            // Content items
            items(10) { index ->
                Card {
                    ListItem(
                        headlineContent = { Text("Content Item $index") },
                        supportingContent = { Text("This is sample content") }
                    )
                }
            }
        }
    }
}
```

## Ad Formats Reference

### AdMob Banner Sizes

| Format | Size (dp) | Usage |
|--------|-----------|--------|
| BANNER | 320x50 | Standard banner |
| LARGE_BANNER | 320x100 | Large banner |
| MEDIUM_RECTANGLE | 300x250 | MREC/Medium rectangle |
| FULL_BANNER | 468x60 | Tablet full banner |
| LEADERBOARD | 728x90 | Tablet leaderboard |
| ADAPTIVE | Flexible | Adapts to screen width |

### AppLovin MAX Formats

| Format | Size (dp) | Usage |
|--------|-----------|--------|
| BANNER | 320x50 | Standard banner |
| LEADER | 728x90 | Tablet leaderboard |
| MREC | 300x250 | Medium rectangle |

## Testing

### Test Ad Unit IDs (AdMob)

```kotlin
// Use test config for development
val testConfig = AdMobConfig.createTestConfig()

// Test IDs included:
// Banner: ca-app-pub-3940256099942544/6300978111
// Interstitial: ca-app-pub-3940256099942544/1033173712
// Rewarded: ca-app-pub-3940256099942544/5224354917
// Native: ca-app-pub-3940256099942544/2247696110
// App Open: ca-app-pub-3940256099942544/3419835294
```

### Test Configuration (AppLovin)

```kotlin
// Use test config for development
val testConfig = AppLovinConfig.createTestConfig()
// AppLovin will automatically use test ads in debug builds
```

## Configuration Options

### AdMob Configuration

```kotlin
val config = AdMobConfig(
    bannerAdUnitId = "YOUR_BANNER_ID",
    interstitialAdUnitId = "YOUR_INTERSTITIAL_ID",
    rewardedAdUnitId = "YOUR_REWARDED_ID",
    nativeAdUnitId = "YOUR_NATIVE_ID",
    appOpenAdUnitId = "YOUR_APP_OPEN_ID",
    testDeviceIds = listOf("DEVICE_ID"),
    enableTestMode = BuildConfig.DEBUG,
    enableDebugLogging = true,
    showInterstitialsInDebug = false, // Disable interstitials in debug
    showBannersInDebug = true,
    showRewardedInDebug = true,
    showNativeInDebug = true
)

// Initialize with config
AdMobMediationManager.initialize(context, config)
```

### AppLovin Configuration

```kotlin
val config = AppLovinConfig(
    bannerAdUnitId = "YOUR_BANNER_ID",
    mrecAdUnitId = "YOUR_MREC_ID",
    interstitialAdUnitId = "YOUR_INTERSTITIAL_ID",
    rewardedAdUnitId = "YOUR_REWARDED_ID",
    nativeAdUnitId = "YOUR_NATIVE_ID",
    appOpenAdUnitId = "YOUR_APP_OPEN_ID",
    enableTestMode = BuildConfig.DEBUG,
    enableVerboseLogging = true,
    showInterstitialsInDebug = false,
    showBannersInDebug = true,
    showRewardedInDebug = true
)

// Initialize with config
AppLovinMediationManager.initialize(context, config)
```

## Consent Management

### AdMob UMP Consent

```kotlin
// Request consent with debug settings for testing
AdMobMediationManager.requestConsentInfo(
    debugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
    testDeviceHashedId = "YOUR_TEST_DEVICE_HASHED_ID",
    onConsentReady = {
        // Consent obtained, can show ads
        Log.d("Consent", "Ready to show ads")
    },
    onConsentError = { error ->
        Log.e("Consent", "Error: $error")
    }
)

// Check consent status
val canShowAds = AdMobMediationManager.canShowAds()
val canShowNonPersonalizedAds = AdMobMediationManager.canShowNonPersonalizedAds()

// Reset consent (for testing)
AdMobMediationManager.resetConsent()
```

## Helper Functions

### Toast Messages

```kotlin
MzgsHelper.showToast(context, "Hello World")
MzgsHelper.showToast(context, "Long message", Toast.LENGTH_LONG)
```

### Network Check

```kotlin
if (MzgsHelper.isNetworkAvailable(context)) {
    // Network is available
} else {
    // No network connection
}
```

### App Version

```kotlin
val versionName = MzgsHelper.getAppVersion(context)
val versionCode = MzgsHelper.getAppVersionCode(context)
```

## AndroidManifest.xml Setup

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<application>
    <!-- AdMob App ID (Required) -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY" />
    
    <!-- AppLovin SDK Key (Required for AppLovin) -->
    <meta-data
        android:name="applovin.sdk.key"
        android:value="YOUR_SDK_KEY_HERE" />
</application>
```

## Memory Management

The library uses WeakReference for context storage to prevent memory leaks. Always ensure proper lifecycle management:

```kotlin
// In Activity
override fun onResume() {
    super.onResume()
    AdMobMediationManager.setCurrentActivity(this)
    AppLovinMediationManager.setCurrentActivity(this)
}

// In Composables with ads
DisposableEffect(Unit) {
    onDispose {
        // Cleanup handled automatically
    }
}
```

## ProGuard Rules

The library includes consumer ProGuard rules automatically. No additional configuration needed.

## Requirements

- Minimum SDK: 24 (Android 7.0)
- Target SDK: 36 (Android 14)
- Kotlin: 1.9+
- Compose: Latest stable
- Google Mobile Ads SDK: 24.5.0
- AppLovin MAX SDK: Latest version

## Troubleshooting

### Ads Not Loading

1. Check your ad unit IDs are correct
2. Verify consent status: `AdMobMediationManager.canShowAds()`
3. Enable debug logging: `config.enableDebugLogging = true`
4. Check network connection: `MzgsHelper.isNetworkAvailable(context)`
5. For test ads, ensure test mode is enabled: `enableTestMode = true`

### Memory Leaks

- Always use the provided composables which handle lifecycle automatically
- Set activity in onResume: `AdMobMediationManager.setCurrentActivity(this)`
- The library uses WeakReference internally to prevent leaks

### Consent Issues

- For EU users, consent is required before showing ads
- Test with debug geography: `DEBUG_GEOGRAPHY_EEA`
- Non-personalized ads work without explicit consent
- Reset consent for testing: `AdMobMediationManager.resetConsent()`

## License

[Add your license here]