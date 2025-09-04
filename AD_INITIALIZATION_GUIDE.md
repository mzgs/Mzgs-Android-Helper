# Ad Initialization Guide

The ad initialization methods have been moved from `MzgsHelper` to the `Ads` class for better organization and easier access. Here are different approaches to initialize and use ads:

## Method 1: Using the Ads Class (Recommended - Easiest)

The `Ads` class now provides static initialization methods for easy access, plus automatic network fallback. It also automatically handles activity lifecycle for showing ads.

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Remote config (required for Ads class priority)
        Remote.init(this)
        
        // Initialize Ads helper (automatically tracks current activity)
        Ads.init(this) // Registers activity lifecycle callbacks automatically!
        
        // Initialize AdMob using Ads helper - no context needed!
        val adMobConfig = AdMobConfig(
            appId = "YOUR_ADMOB_APP_ID",
            bannerAdUnitId = "YOUR_BANNER_ID",
            interstitialAdUnitId = "YOUR_INTERSTITIAL_ID",
            // ... other config
        )
        Ads.initAdMob(adMobConfig) {
            Log.d("Ads", "AdMob initialized")
        }
        
        // Initialize AppLovin MAX using Ads helper - no context needed!
        val appLovinConfig = AppLovinConfig(
            sdkKey = "YOUR_SDK_KEY",
            bannerAdUnitId = "YOUR_BANNER_ID",
            // ... other config
        )
        Ads.initAppLovinMax(appLovinConfig) {
            Log.d("Ads", "AppLovin initialized")
        }
        
        // Or initialize both at once - no context needed!
        Ads.initBothNetworks(
            adMobConfig = adMobConfig,
            appLovinConfig = appLovinConfig,
            onBothInitComplete = {
                Log.d("Ads", "Both networks ready")
            }
        )
        
        // Now use the Ads class to show ads
        // It will automatically use the first available network
        Ads.showInterstitial()
        Ads.showBanner(this, bannerContainer)
        Ads.showRewardedAd()
    }
}
```

## Method 2: Direct AdMob Initialization

Use `AdMobMediationManager` directly for AdMob-only integration:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val adConfig = AdMobConfig(
            appId = "ca-app-pub-xxxxx",
            bannerAdUnitId = "ca-app-pub-xxxxx/xxxxx",
            interstitialAdUnitId = "ca-app-pub-xxxxx/xxxxx",
            rewardedAdUnitId = "ca-app-pub-xxxxx/xxxxx",
            nativeAdUnitId = "ca-app-pub-xxxxx/xxxxx",
            appOpenAdUnitId = "ca-app-pub-xxxxx/xxxxx",
            enableAppOpenAd = true,
            enableTestMode = true,
            showAdsInDebug = true
        )
        
        // Initialize AdMob directly
        AdMobMediationManager.init(
            context = this,
            config = adConfig,
            onInitComplete = {
                Log.d("MainActivity", "AdMob initialized")
                
                // Load and show ads
                AdMobMediationManager.loadInterstitialAd()
                AdMobMediationManager.loadRewardedAd()
            }
        )
        
        // Show ads when needed
        findViewById<Button>(R.id.showInterstitial).setOnClickListener {
            AdMobMediationManager.showInterstitialAd(this)
        }
    }
}
```

## Method 3: Direct AppLovin MAX Initialization

Use `AppLovinMediationManager` directly for AppLovin-only integration:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val appLovinConfig = AppLovinConfig(
            sdkKey = "YOUR_APPLOVIN_SDK_KEY",
            bannerAdUnitId = "YOUR_BANNER_UNIT_ID",
            interstitialAdUnitId = "YOUR_INTERSTITIAL_UNIT_ID",
            rewardedAdUnitId = "YOUR_REWARDED_UNIT_ID",
            nativeAdUnitId = "YOUR_NATIVE_UNIT_ID",
            mrecAdUnitId = "YOUR_MREC_UNIT_ID",
            enableTestMode = true,
            verboseLogging = true
        )
        
        // Initialize AppLovin directly
        AppLovinMediationManager.init(
            context = this,
            config = appLovinConfig,
            onInitComplete = {
                Log.d("MainActivity", "AppLovin MAX initialized")
                
                // Load ads
                AppLovinMediationManager.loadInterstitialAd()
                AppLovinMediationManager.loadRewardedAd()
            }
        )
        
        // Show ads when needed
        findViewById<Button>(R.id.showInterstitial).setOnClickListener {
            AppLovinMediationManager.showInterstitialAd()
        }
    }
}
```

## Method 4: Initialize Both Networks

You can initialize both networks and use them independently:

```kotlin
class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize both networks
        initializeAdMob()
        initializeAppLovin()
    }
    
    private fun initializeAdMob() {
        val config = AdMobConfig(
            // ... your config
        )
        
        AdMobMediationManager.init(this, config) {
            Log.d("Ads", "AdMob ready")
            AdMobMediationManager.loadInterstitialAd()
        }
    }
    
    private fun initializeAppLovin() {
        val config = AppLovinConfig(
            // ... your config
        )
        
        AppLovinMediationManager.init(this, config) {
            Log.d("Ads", "AppLovin ready")
            AppLovinMediationManager.loadInterstitialAd()
        }
    }
    
    private fun showAd() {
        // Try AppLovin first, fallback to AdMob
        if (AppLovinMediationManager.isInterstitialReady()) {
            AppLovinMediationManager.showInterstitialAd()
        } else if (AdMobMediationManager.isInterstitialReady()) {
            AdMobMediationManager.showInterstitialAd(this)
        }
    }
}
```

## Using the Ads Class with Remote Configuration

The `Ads` class supports remote configuration for ad network priority:

```kotlin
// In your remote config JSON:
{
  "your.package.name": {
    "ads_order": ["applovin_max", "admob"]
  }
}

// In your app:
Remote.init(this, "https://your-config-url.com/config.json")
Ads.init(this)

// Initialize both networks
AdMobMediationManager.init(this, adMobConfig)
AppLovinMediationManager.init(this, appLovinConfig)

// Ads class will respect the priority order from remote config
Ads.showInterstitial() // Shows from AppLovin first, then AdMob if not available
```

## Key Benefits of Direct Initialization

1. **More Control**: Direct access to all features of each ad network
2. **Better Debugging**: Clearer understanding of which network is being used
3. **Flexibility**: Easy to switch between networks or use specific features
4. **Simplified Code**: No extra abstraction layer when not needed

## New Ads Class Methods

The `Ads` class now provides these convenient static methods (no context parameter needed after calling `Ads.init()`):

```kotlin
// First, initialize Ads helper once
Ads.init(context)

// Then use these methods without context
Ads.initAdMob(config, onInitComplete)
Ads.initAppLovinMax(config, onInitComplete)

// Initialize both networks at once
Ads.initBothNetworks(adMobConfig, appLovinConfig, onBothInitComplete)

// Get network managers
val adMobManager = Ads.getAdMobManager()
val appLovinManager = Ads.getAppLovinManager()

// Show ads (automatic fallback)
Ads.showInterstitial()
Ads.showBanner(activity, container)
Ads.showRewardedAd()
Ads.showNativeAd(activity, container)
Ads.showMREC(activity, container)
Ads.showAppOpenAd()
```

## Migration from MzgsHelper

If you were previously using MzgsHelper methods:

```kotlin
// Old way (removed from MzgsHelper):
MzgsHelper.initializeAdMob(context, config, onInitComplete)
MzgsHelper.initializeAppLovinMAX(context, config, onInitComplete)

// New way (using Ads class - simpler!):
Ads.init(context) // Call once
Ads.initAdMob(config, onInitComplete) // No context needed
Ads.initAppLovinMax(config, onInitComplete) // No context needed
```

## How Activity Handling Works

The `Ads` class now automatically tracks the current activity:

1. When you call `Ads.init(context)`, it registers for activity lifecycle callbacks
2. The current activity is automatically tracked and updated
3. When showing ads (especially AdMob), the correct activity is automatically used
4. No need to manually manage activities or pass them around

## Summary

- **Easiest**: Use `Ads.initAdMob()` and `Ads.initAppLovinMax()` for simple initialization
- **All-in-one**: Use `Ads.initBothNetworks()` to initialize both networks at once
- **Automatic activity tracking**: `Ads.init()` handles activity lifecycle automatically
- **Automatic fallback**: After initialization, `Ads.showInterstitial()` etc. will automatically use the first available network
- **Direct control**: Still can use `AdMobMediationManager` or `AppLovinMediationManager` directly if needed
- **Migration**: Simply replace `MzgsHelper.initializeAdMob` with `Ads.initAdMob`