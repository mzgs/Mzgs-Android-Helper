# Ads Class Usage Example

The new `Ads` class provides a unified interface for showing ads with automatic fallback between ad networks based on remote configuration.

## Setup

### 1. Initialize the Ads helper in your Application class or Activity:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Remote configuration
        Remote.init(this, "https://your-remote-config-url.json")
        
        // Initialize Ads
        Ads.init(this)
        
        // Initialize both ad networks
        MzgsHelper.initializeBothNetworks(
            context = this,
            adMobConfig = AdMobConfig(
                bannerAdUnitId = "your-admob-banner-id",
                interstitialAdUnitId = "your-admob-interstitial-id",
                rewardedAdUnitId = "your-admob-rewarded-id",
                nativeAdUnitId = "your-admob-native-id"
            ),
            appLovinConfig = AppLovinConfig(
                sdkKey = "your-applovin-sdk-key",
                bannerAdUnitId = "your-applovin-banner-id",
                interstitialAdUnitId = "your-applovin-interstitial-id",
                rewardedAdUnitId = "your-applovin-rewarded-id",
                nativeAdUnitId = "your-applovin-native-id",
                mrecAdUnitId = "your-applovin-mrec-id"
            )
        )
    }
}
```

### 2. Configure Remote Config JSON:

Your remote configuration should include the `ads_order` array:

```json
{
  "com.your.package.name": {
    "ads_order": ["applovin_max", "admob"]
  }
}
```

## Usage Examples

### Show Interstitial Ad

```kotlin
// Show interstitial with automatic fallback
if (Ads.isAnyInterstitialReady()) {
    Ads.showInterstitial()
}

// Or without checking
Ads.showInterstitial() // Returns true if shown, false otherwise
```

### Show Banner Ad

```kotlin
// In your Activity or Fragment
val bannerContainer = findViewById<FrameLayout>(R.id.banner_container)

// Show adaptive banner (recommended)
Ads.showBanner(
    activity = this,
    container = bannerContainer,
    adSize = Ads.BannerSize.ADAPTIVE
)

// Show standard banner
Ads.showBanner(
    activity = this,
    container = bannerContainer,
    adSize = Ads.BannerSize.BANNER
)
```

### Show Rewarded Ad

```kotlin
// Check if rewarded ad is ready
if (Ads.isAnyRewardedAdReady()) {
    Ads.showRewardedAd()
}
```

### Show Native Ad

```kotlin
val nativeAdContainer = findViewById<ViewGroup>(R.id.native_ad_container)

Ads.showNativeAd(
    activity = this,
    container = nativeAdContainer
)
```

### Show MREC (Medium Rectangle) Ad

```kotlin
val mrecContainer = findViewById<FrameLayout>(R.id.mrec_container)

Ads.showMREC(
    activity = this,
    container = mrecContainer
)
```

### Show App Open Ad

```kotlin
// Show app open ad (usually called when app comes to foreground)
Ads.showAppOpenAd()
```

## How Fallback Works

1. The `Ads` class reads the `ads_order` array from Remote configuration
2. For each ad request, it tries networks in the specified order
3. If the first network has an ad ready, it shows it
4. If not, it tries the next network in the list
5. This continues until an ad is shown or all networks are exhausted

## Benefits

- **Automatic Fallback**: Maximizes fill rate by trying multiple networks
- **Remote Control**: Change ad network priority without app update
- **Simple API**: One unified interface for all ad types
- **Auto-reload**: Ads are automatically reloaded after being shown (handled by AdMob and AppLovin managers)

## Jetpack Compose Usage

### Show Banner Ad in Compose

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.app.Activity
import android.widget.FrameLayout

@Composable
fun BannerAd(
    modifier: Modifier = Modifier,
    adSize: Ads.BannerSize = Ads.BannerSize.ADAPTIVE
) {
    val context = LocalContext.current
    
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                
                // Show banner ad
                (ctx as? Activity)?.let { activity ->
                    Ads.showBanner(
                        activity = activity,
                        container = this,
                        adSize = adSize
                    )
                }
            }
        }
    )
}

// Usage in your Compose screen:
@Composable
fun MyScreen() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Your content at top
        Text("My App Content")
        
        // Push banner to bottom
        Spacer(modifier = Modifier.weight(1f))
        
        // Banner at bottom
        BannerAd(
            adSize = Ads.BannerSize.ADAPTIVE
        )
    }
}
```

### Show MREC in Compose

```kotlin
@Composable
fun MRECAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    AndroidView(
        modifier = modifier
            .width(300.dp)
            .height(250.dp),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                (ctx as? Activity)?.let { activity ->
                    Ads.showMREC(
                        activity = activity,
                        container = this
                    )
                }
            }
        }
    )
}
```

### Show Native Ad in Compose

```kotlin
@Composable
fun NativeAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { ctx ->
            FrameLayout(ctx).apply {
                (ctx as? Activity)?.let { activity ->
                    Ads.showNativeAd(
                        activity = activity,
                        container = this
                    )
                }
            }
        }
    )
}
```

### Advanced Banner with Lifecycle and State Management

```kotlin
@Composable
fun AdaptiveBannerAd(
    modifier: Modifier = Modifier,
    onAdLoaded: () -> Unit = {},
    onAdFailed: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAdLoaded by remember { mutableStateOf(false) }
    
    DisposableEffect(lifecycleOwner) {
        onDispose {
            // Cleanup if needed
        }
    }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            FrameLayout(ctx).apply {
                (ctx as? Activity)?.let { activity ->
                    val success = Ads.showBanner(
                        activity = activity,
                        container = this,
                        adSize = Ads.BannerSize.ADAPTIVE
                    )
                    if (success) {
                        isAdLoaded = true
                        onAdLoaded()
                    } else {
                        onAdFailed()
                    }
                }
            }
        }
    )
}

// Usage with state management:
@Composable
fun ScreenWithAds() {
    var showAd by remember { mutableStateOf(true) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Main content
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(50) { index ->
                Text("Item $index")
            }
        }
        
        // Conditional banner display
        if (showAd) {
            AdaptiveBannerAd(
                onAdLoaded = {
                    Log.d("Ads", "Banner loaded successfully")
                },
                onAdFailed = {
                    Log.e("Ads", "Banner failed to load")
                    showAd = false // Hide container if ad fails
                }
            )
        }
    }
}
```

### Show Interstitial from Compose

```kotlin
@Composable
fun MyGameScreen() {
    Button(
        onClick = {
            // Show interstitial when button clicked
            if (Ads.isAnyInterstitialReady()) {
                Ads.showInterstitial()
            }
        }
    ) {
        Text("Continue to Next Level")
    }
}
```

### Show Rewarded Ad from Compose

```kotlin
@Composable
fun RewardedAdButton() {
    var rewardEarned by remember { mutableStateOf(false) }
    
    Button(
        onClick = {
            if (Ads.isAnyRewardedAdReady()) {
                val shown = Ads.showRewardedAd()
                if (shown) {
                    // Note: You'll need to implement reward callbacks
                    // through the underlying ad managers
                    rewardEarned = true
                }
            }
        },
        enabled = Ads.isAnyRewardedAdReady()
    ) {
        Text(if (rewardEarned) "Reward Earned!" else "Watch Ad for Reward")
    }
}
```

### Banner in a Scrollable List

```kotlin
@Composable
fun ContentWithInlineBanners() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(10) { index ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Content Item $index",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        
        // Insert banner ad after 5 items
        item {
            BannerAd(
                modifier = Modifier.padding(vertical = 8.dp),
                adSize = Ads.BannerSize.MEDIUM_RECTANGLE
            )
        }
        
        items(10) { index ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Content Item ${index + 10}",
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
```

## Available Banner Sizes

```kotlin
enum class BannerSize {
    ADAPTIVE,         // Recommended: Adapts to screen width
    BANNER,           // 320x50
    LARGE_BANNER,     // 320x100
    MEDIUM_RECTANGLE, // 300x250 (MREC)
    FULL_BANNER,      // 468x60
    LEADERBOARD       // 728x90
}
```

## Notes

- All ads are automatically reloaded after being shown or failed (handled by the underlying AdMob and AppLovin managers)
- No need to manually call load functions
- The class handles all error cases and logging
- Debug mode settings from AdMobConfig and AppLovinConfig are respected