# EU Consent & Ad Loading Scenario

## The Challenge
For EU users, showing an interstitial ad after splash screen requires careful timing:

1. **Splash Duration**: 9 seconds
2. **Consent Required**: EU users must give consent before ads can load
3. **Ad Loading Time**: Interstitials take 1-3 seconds to load
4. **Problem**: Consent form blocks ad loading until user interacts

## Current Flow (Problematic for EU)
```
App Start → Splash (9s) → Try to show interstitial → FAILS (no consent yet)
         ↘ AdMob Init → Consent Form Shows → User gives consent → Too late!
```

## Recommended Solutions

### Solution 1: Extended Splash with Consent Check
```kotlin
class MainActivity : ComponentActivity() {
    private var splashHelper: SimpleSplashHelper? = null
    private var consentObtained = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AdMob with consent callback
        initAdMobWithConsentHandling()
        
        // Create splash but don't show yet
        splashHelper = SimpleSplashHelper.Builder(this)
            .setDuration(9000)
            .showProgress(true)
            .onComplete { 
                // Try to show interstitial after splash
                if (AdMobMediationManager.isInterstitialReady()) {
                    AdMobMediationManager.showInterstitialAd()
                }
            }
            .build()
        
        // Check if we need consent
        checkAndHandleConsent()
    }
    
    private fun initAdMobWithConsentHandling() {
        val adConfig = AdMobConfig(
            // ... your config
        )
        
        AdMobMediationManager.init(
            context = this,
            config = adConfig,
            onInitComplete = {
                val adManager = AdMobMediationManager.getInstance(this)
                
                // Check consent status
                when (adManager.getConsentStatus()) {
                    ConsentInformation.ConsentStatus.OBTAINED -> {
                        // Consent already given, load interstitial
                        AdMobMediationManager.loadInterstitialAd()
                        if (splashHelper?.isShowing() != true) {
                            splashHelper?.show() // Start splash if not started
                        }
                    }
                    ConsentInformation.ConsentStatus.NOT_REQUIRED -> {
                        // Not in EU, load normally
                        AdMobMediationManager.loadInterstitialAd()
                        if (splashHelper?.isShowing() != true) {
                            splashHelper?.show()
                        }
                    }
                    else -> {
                        // Consent required but not given yet
                        // Splash will start after consent
                    }
                }
            }
        )
    }
    
    private fun checkAndHandleConsent() {
        lifecycleScope.launch {
            delay(500) // Small delay to ensure initialization
            
            val adManager = AdMobMediationManager.getInstance(this@MainActivity)
            
            if (adManager.isConsentFormAvailable()) {
                // Show consent form for EU users
                AdMobMediationManager.showConsentForm(this@MainActivity) { error ->
                    if (error == null && adManager.canShowAds()) {
                        // Consent obtained, now load ad and start splash
                        AdMobMediationManager.loadInterstitialAd()
                        splashHelper?.show()
                    } else {
                        // Consent denied or error, start splash without ads
                        splashHelper?.show()
                    }
                }
            } else {
                // No consent needed, start splash immediately
                if (splashHelper?.isShowing() != true) {
                    splashHelper?.show()
                }
            }
        }
    }
}
```

### Solution 2: Preload After First Consent (Recommended)
```kotlin
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // For subsequent app launches after consent is given
        initAdMobWithPreloading()
        
        // Normal splash flow
        SimpleSplashHelper.Builder(this)
            .setDuration(9000)
            .showProgress(true)
            .onComplete { 
                // Show interstitial if ready
                if (AdMobMediationManager.isInterstitialReady()) {
                    AdMobMediationManager.showInterstitialAd()
                }
            }
            .build()
            .show()
    }
    
    private fun initAdMobWithPreloading() {
        val adConfig = AdMobConfig(
            // ... your config
        )
        
        AdMobMediationManager.init(
            context = this,
            config = adConfig,
            onInitComplete = {
                // Check if consent was previously obtained
                val adManager = AdMobMediationManager.getInstance(this)
                
                if (adManager.canShowAds()) {
                    // User already gave consent in previous session
                    // Load interstitial immediately
                    AdMobMediationManager.loadInterstitialAd(
                        onAdLoaded = {
                            Log.d("MainActivity", "Interstitial preloaded for splash")
                        }
                    )
                } else if (adManager.getConsentStatus() == ConsentInformation.ConsentStatus.REQUIRED) {
                    // First time EU user - consent will be requested
                    // Don't load ads until consent is given
                    Log.d("MainActivity", "Waiting for EU consent before loading ads")
                }
            }
        )
    }
}
```

### Solution 3: Delay Splash Until Consent (Best UX)
```kotlin
class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AdMob
        initAdMob()
        
        // Show content immediately (no splash blocking)
        setContent {
            MzgsAndroidHelperTheme {
                AdMobTestScreen()
            }
        }
    }
    
    private fun initAdMob() {
        val adConfig = AdMobConfig(
            // ... your config
        )
        
        AdMobMediationManager.init(
            context = this,
            config = adConfig,
            onInitComplete = {
                handleConsentAndAds()
            }
        )
    }
    
    private fun handleConsentAndAds() {
        val adManager = AdMobMediationManager.getInstance(this)
        
        when (adManager.getConsentStatus()) {
            ConsentInformation.ConsentStatus.OBTAINED,
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> {
                // Can show ads immediately
                loadAndShowInterstitialWhenReady()
            }
            else -> {
                // Need consent first
                if (adManager.isConsentFormAvailable()) {
                    AdMobMediationManager.showConsentForm(this) { error ->
                        if (error == null && adManager.canShowAds()) {
                            loadAndShowInterstitialWhenReady()
                        }
                    }
                }
            }
        }
    }
    
    private fun loadAndShowInterstitialWhenReady() {
        AdMobMediationManager.loadInterstitialAd(
            onAdLoaded = {
                // Show after a delay or on user action
                lifecycleScope.launch {
                    delay(3000) // Show after 3 seconds
                    AdMobMediationManager.showInterstitialAd()
                }
            }
        )
    }
}
```

## Best Practices for EU Users

1. **First Launch**: Don't try to show ads immediately. Get consent first.
2. **Subsequent Launches**: Check if consent was previously given, then preload ads.
3. **Splash Screen**: Consider showing splash AFTER consent for first-time EU users.
4. **User Experience**: Don't block app usage with both consent AND splash screens.

## Testing EU Scenario

To test EU consent flow:

```kotlin
// In your AdMobConfig
val adConfig = AdMobConfig(
    // ... other config
    testDeviceIds = listOf("YOUR_DEVICE_ID"),
    forceConsentDebugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
)
```

This forces your device to behave as if it's in the EU, triggering consent flow.

## Key Points

- **EU users must give consent** before any ad can be loaded
- **Consent form is blocking** - user must interact before continuing
- **Ads take time to load** - plan for 1-3 second loading time
- **Don't frustrate users** with multiple blocking screens
- **Save consent state** - it persists across app sessions