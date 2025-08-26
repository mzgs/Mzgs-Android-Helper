# Mzgs-Android-Helper
Android helper library with utility tools and AdMob mediation support

## Features

- 🛠️ General utility functions (Toast, Network status, App version)
- 📱 Complete AdMob mediation implementation
- 🎯 Support for all ad formats (Banner, Interstitial, Rewarded, Native, App Open)
- 🔐 Built-in UMP consent management
- 🚀 AppLovin MAX mediation adapters included

## Installation

### Add to your project

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":mzgshelper"))
}
```

## AdMob Mediation Usage

### Required Imports for Compose

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mzgs.helper.admob.*
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
```

### Initial Setup

1. **Initialize AdMob in your Application class:**

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize AdMob
        val adManager = AdMobMediationManager.getInstance(this)
        adManager.initialize(
            testDeviceIds = listOf("YOUR_TEST_DEVICE_ID"), // Optional
            onInitComplete = {
                Log.d("AdMob", "Initialization complete")
            }
        )
        
        // Optional: Initialize App Open Ads
        AppOpenAdManager.initialize(
            application = this,
            adUnitId = "YOUR_APP_OPEN_AD_UNIT_ID"
        )
    }
}
```

2. **Request Consent (Required for EU users):**

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val adManager = AdMobMediationManager.getInstance(this)
        
        // Request consent
        adManager.requestConsentInfo(
            activity = this,
            debugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA, // For testing
            testDeviceHashedId = "YOUR_TEST_DEVICE_HASHED_ID", // For testing
            onConsentReady = {
                // Consent obtained, you can now load ads
                loadAds()
            },
            onConsentError = { error ->
                Log.e("Consent", "Error: $error")
            }
        )
    }
}
```

### Banner Ads in Compose

**Create a Composable for Banner Ads:**

```kotlin
@Composable
fun AdMobBanner(
    adUnitId: String,
    modifier: Modifier = Modifier,
    adSize: AdSize = AdSize.BANNER,
    isAdaptive: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            AdMobBannerView(context).apply {
                loadBanner(
                    adUnitId = adUnitId,
                    adSize = adSize,
                    isAdaptive = isAdaptive,
                    onAdLoaded = {
                        Log.d("Banner", "Ad loaded")
                    },
                    onAdFailedToLoad = { error ->
                        Log.e("Banner", "Failed to load: ${error.message}")
                    }
                )
            }
        },
        update = { bannerView ->
            // Handle lifecycle
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> bannerView.resume()
                        Lifecycle.Event.ON_PAUSE -> bannerView.pause()
                        Lifecycle.Event.ON_DESTROY -> bannerView.destroy()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    bannerView.destroy()
                }
            }
        }
    )
}

// Usage in your Compose screen
@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Your content
        Box(modifier = Modifier.weight(1f)) {
            // Main content
        }
        
        // Banner ad at bottom
        AdMobBanner(
            adUnitId = "YOUR_BANNER_AD_UNIT_ID",
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
```

### Interstitial Ads in Compose

```kotlin
@Composable
fun InterstitialAdScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val adManager = remember { AdMobMediationManager.getInstance(context) }
    var isAdLoaded by remember { mutableStateOf(false) }
    
    // Load ad when composable enters composition
    LaunchedEffect(Unit) {
        adManager.loadInterstitialAd(
            adUnitId = "YOUR_INTERSTITIAL_AD_UNIT_ID",
            onAdLoaded = {
                isAdLoaded = true
            },
            onAdFailedToLoad = { error ->
                Log.e("Interstitial", "Failed to load: ${error.message}")
                isAdLoaded = false
            }
        )
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                activity?.let {
                    if (adManager.isInterstitialReady()) {
                        adManager.showInterstitialAd(it)
                        // Load next ad
                        adManager.loadInterstitialAd("YOUR_INTERSTITIAL_AD_UNIT_ID")
                    }
                }
            },
            enabled = isAdLoaded
        ) {
            Text("Show Interstitial Ad")
        }
    }
}
```

### Rewarded Ads in Compose

```kotlin
@Composable
fun RewardedAdScreen(
    onRewardEarned: (amount: Int, type: String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val adManager = remember { AdMobMediationManager.getInstance(context) }
    var isAdLoaded by remember { mutableStateOf(false) }
    
    // Load ad
    LaunchedEffect(Unit) {
        adManager.loadRewardedAd(
            adUnitId = "YOUR_REWARDED_AD_UNIT_ID",
            onAdLoaded = {
                isAdLoaded = true
            },
            onAdFailedToLoad = { error ->
                Log.e("Rewarded", "Failed to load: ${error.message}")
                isAdLoaded = false
            }
        )
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = {
                activity?.let {
                    if (adManager.isRewardedAdReady()) {
                        adManager.showRewardedAd(
                            activity = it,
                            onUserEarnedReward = { rewardItem ->
                                onRewardEarned(rewardItem.amount, rewardItem.type)
                                // Load next ad
                                adManager.loadRewardedAd("YOUR_REWARDED_AD_UNIT_ID")
                            }
                        )
                    }
                }
            },
            enabled = isAdLoaded
        ) {
            Text("Watch Ad for Reward")
        }
    }
}

// Usage example
@Composable
fun GameScreen() {
    var coins by remember { mutableStateOf(0) }
    
    RewardedAdScreen(
        onRewardEarned = { amount, type ->
            coins += amount
            Log.d("Game", "User earned $amount $type")
        }
    )
    
    Text("Coins: $coins")
}
```

### Native Ads in Compose

```kotlin
@Composable
fun NativeAdView(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val nativeAdHelper = remember { AdMobNativeAdHelper(context) }
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    
    // Load native ad
    LaunchedEffect(Unit) {
        nativeAdHelper.loadNativeAd(
            adUnitId = adUnitId,
            onAdLoaded = { ad ->
                nativeAd = ad
            },
            onAdFailedToLoad = { error ->
                Log.e("Native", "Failed to load: ${error.message}")
            },
            mediaAspectRatio = NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE
        )
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            nativeAdHelper.destroy()
        }
    }
    
    nativeAd?.let { ad ->
        Card(
            modifier = modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Ad attribution
                Text(
                    text = "Ad",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Headline
                ad.headline?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Body
                ad.body?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // Call to action button
                ad.callToAction?.let { cta ->
                    Button(
                        onClick = { /* Handled by native ad */ },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(cta)
                    }
                }
            }
        }
    }
}

// Usage in your screen
@Composable
fun FeedScreen() {
    LazyColumn {
        items(10) { index ->
            if (index == 3) {
                // Show native ad after 3rd item
                NativeAdView(
                    adUnitId = "YOUR_NATIVE_AD_UNIT_ID",
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Regular content
                ContentItem()
            }
        }
    }
}
```

### App Open Ads

App Open Ads are automatically shown when the app comes to foreground after being initialized:

```kotlin
// In your Application class
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize App Open Ad Manager
        val appOpenManager = AppOpenAdManager.initialize(
            application = this,
            adUnitId = "YOUR_APP_OPEN_AD_UNIT_ID"
        )
        
        // Optional: Manually control when to show
        // appOpenManager.showAdIfAvailable(activity)
    }
}
```

### Test Ad Units

For testing, use these provided test ad unit IDs:

```kotlin
val testConfig = AdMobConfig.createTestConfig()

// Test ad unit IDs:
// Banner: ca-app-pub-3940256099942544/6300978111
// Interstitial: ca-app-pub-3940256099942544/1033173712
// Rewarded: ca-app-pub-3940256099942544/5224354917
// Native: ca-app-pub-3940256099942544/2247696110
// App Open: ca-app-pub-3940256099942544/3419835294
```

### Configuration Options

Create a custom configuration:

```kotlin
val config = AdMobConfig(
    bannerAdUnitId = "YOUR_BANNER_ID",
    interstitialAdUnitId = "YOUR_INTERSTITIAL_ID",
    rewardedAdUnitId = "YOUR_REWARDED_ID",
    nativeAdUnitId = "YOUR_NATIVE_ID",
    testDeviceIds = listOf("DEVICE_ID_1", "DEVICE_ID_2"),
    enableTestMode = BuildConfig.DEBUG // Use test ads in debug mode
)

// Get appropriate ad unit ID based on mode
val bannerId = config.getEffectiveBannerAdUnitId() // Returns test ID if in test mode
```

### Adaptive Banner Sizes

Use adaptive banners for better responsiveness:

```kotlin
// Get adaptive banner size
val display = resources.displayMetrics
val adWidth = (display.widthPixels / display.density).toInt()

// Current orientation adaptive banner
val adaptiveSize = AdSizeHelper.getAdaptiveBannerAdSize(context, adWidth)

// Inline adaptive banner with max height
val inlineSize = AdSizeHelper.getInlineAdaptiveBannerAdSize(context, adWidth, 90)

// Landscape adaptive banner
val landscapeSize = AdSizeHelper.getLandscapeAdaptiveBannerAdSize(context, adWidth)

// Portrait adaptive banner
val portraitSize = AdSizeHelper.getPortraitAdaptiveBannerAdSize(context, adWidth)
```

### Complete Compose Integration Example

```kotlin
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val adManager = remember { AdMobMediationManager.getInstance(context) }
    
    // Initialize ads on first composition
    LaunchedEffect(Unit) {
        if (activity != null) {
            adManager.requestConsentInfo(
                activity = activity,
                onConsentReady = {
                    // Start loading ads after consent
                    Log.d("Ads", "Consent obtained, ready to show ads")
                }
            )
        }
    }
    
    Scaffold(
        bottomBar = {
            // Adaptive banner at bottom
            AdMobBanner(
                adUnitId = if (BuildConfig.DEBUG) {
                    AdMobConfig.TEST_BANNER_AD_UNIT_ID
                } else {
                    "YOUR_PRODUCTION_BANNER_ID"
                },
                isAdaptive = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Your app content
        }
    }
}
```

## General Helper Functions

### Show Toast

```kotlin
MzgsHelper.showToast(context, "Hello World")
MzgsHelper.showToast(context, "Long message", Toast.LENGTH_LONG)
```

### Check Network Availability

```kotlin
// Requires ACCESS_NETWORK_STATE permission
if (MzgsHelper.isNetworkAvailable(context)) {
    // Network is available
}
```

### Get App Version

```kotlin
val versionName = MzgsHelper.getAppVersion(context)
val versionCode = MzgsHelper.getAppVersionCode(context)
```

## Permissions Required

Add these to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<application>
    <!-- AdMob App ID (required) -->
    <meta-data
        android:name="com.google.android.gms.ads.APPLICATION_ID"
        android:value="YOUR_ADMOB_APP_ID" />
</application>
```

## ProGuard Rules

If using ProGuard/R8, the library includes consumer rules automatically. No additional configuration needed.

## Requirements

- Minimum SDK: 24 (Android 7.0)
- Target SDK: 36 (Android 14)
- Google Mobile Ads SDK: 24.5.0

## License

[Add your license here]
