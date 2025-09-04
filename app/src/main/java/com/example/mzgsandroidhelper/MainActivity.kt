package com.example.mzgsandroidhelper

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.mzgsandroidhelper.ui.theme.MzgsAndroidHelperTheme
import com.google.android.gms.ads.AdSize
import com.google.android.ump.ConsentDebugSettings
import com.mzgs.helper.MzgsHelper
import com.mzgs.helper.Remote
import com.mzgs.helper.SimpleSplashHelper
import com.mzgs.helper.admob.*
import com.mzgs.helper.applovin.AppLovinConfig
import com.mzgs.helper.applovin.AppLovinMediationManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.mzgs.helper.Ads
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import com.mzgs.helper.p

class MainActivity : ComponentActivity() {
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        

        FirebaseAnalyticsManager.initialize(this)
        Remote.init(this)
        Ads.init(this)


        val admobConfig = AdMobConfig(
            appId = "",
            bannerAdUnitId = "",
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            rewardedInterstitialAdUnitId = "",
            nativeAdUnitId = "",
            appOpenAdUnitId = "",
            enableAppOpenAd = true,
            enableTestMode = true,
            testDeviceIds = listOf("3d6496d1-4784-4b96-bf5e-2d61200765de"),
            showAdsInDebug = true,
            showInterstitialsInDebug = true,
            showAppOpenAdInDebug = true,
            showBannersInDebug = true,
            showNativeAdsInDebug = true,
            showRewardedAdsInDebug = true,
            debugRequireConsentAlways = false  // Set to true to always show consent form for testing
        )


        val appLovinConfig = AppLovinConfig(
            sdkKey = "sTOrf_0s7y7dzVqfTPRR0Ck_synT0Xrs0DgfChVKedyc7nGgAi6BwrAnnxEoT3dTHJ7T0dpfFmGNXX3hE9u9_2",
            // Add your real AppLovin ad unit IDs here (from your AppLovin dashboard)
            bannerAdUnitId = "",
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            mrecAdUnitId = "",
            nativeAdUnitId = "",
            enableTestMode = true,
            verboseLogging = true,
            creativeDebuggerEnabled = true,
            showAdsInDebug = true,
            // Add your device's advertising ID here to see test ads
            testDeviceAdvertisingIds = listOf("3d6496d1-4784-4b96-bf5e-2d61200765de") // e.g., listOf("38400000-8cf0-11bd-b23e-10b96e40000d")
        )

        MzgsHelper.initSplashWithAdmobShow(
            activity = this,
            admobConfig = admobConfig,
            onFinish = {
                Log.d("MainActivity", "Splash and ad sequence completed")
                Ads.initAppLovinMax(appLovinConfig){
                    p("ApplovinMax init completed.")

                }
            }
        )


        setContent {
            MzgsAndroidHelperTheme {
                AdMobTestScreen()
            }
        }
    }


    

    // Method 3: Initialize AppLovin MAX directly
    private fun initApplovinMax() {
        val appLovinConfig = AppLovinConfig(
            sdkKey = "sTOrf_0s7y7dzVqfTPRR0Ck_synT0Xrs0DgfChVKedyc7nGgAi6BwrAnnxEoT3dTHJ7T0dpfFmGNXX3hE9u9_2",
            // Add your real AppLovin ad unit IDs here (from your AppLovin dashboard)
            bannerAdUnitId = "", // e.g., "abcd1234"
            interstitialAdUnitId = "", // e.g., "efgh5678"
            rewardedAdUnitId = "", // e.g., "ijkl9012"
            mrecAdUnitId = "", // e.g., "mnop3456"
            nativeAdUnitId = "", // e.g., "qrst7890"
            enableTestMode = true,
            verboseLogging = true,
            creativeDebuggerEnabled = true,
            showAdsInDebug = true,
            // Add your device's advertising ID here to see test ads
            testDeviceAdvertisingIds = listOf() // e.g., listOf("38400000-8cf0-11bd-b23e-10b96e40000d")
        )
        
        AppLovinMediationManager.init(
            context = this,
            config = appLovinConfig,
            onInitComplete = {
                Log.d("MainActivity", "AppLovin MAX initialized successfully")
            }
        )
    }
    



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdMobTestScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val adManager = remember { AdMobMediationManager.getInstance(context) }
    val appLovinManager = remember { AppLovinMediationManager.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // States
    var consentStatus by remember { mutableStateOf("Not checked") }
    var interstitialLoaded by remember { mutableStateOf(false) }
    var rewardedLoaded by remember { mutableStateOf(false) }
    var rewardedInterstitialLoaded by remember { mutableStateOf(false) }
    var userCoins by remember { mutableStateOf(0) }
    
    // Request consent on first launch
    LaunchedEffect(activity) {
        activity?.let { act ->
            // Set the current activity for AdMob operations
            adManager.setCurrentActivity(act)
            
            adManager.requestConsentInfoUpdate(
                underAgeOfConsent = false,
                debugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
                testDeviceHashedId = null,
                onConsentInfoUpdateSuccess = {
                    consentStatus = "Consent obtained"
                    Log.d("Consent", "Ready to show ads")
                },
                onConsentInfoUpdateFailure = { error: String ->
                    consentStatus = "Consent error: $error"
                    Log.e("Consent", error)
                }
            )
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AdMob Test Suite") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {


        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // AppLovin MAX Debugger button at the top
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "AppLovin MAX Tools",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    appLovinManager.showMediationDebugger()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onTertiary,
                                    contentColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Text("Open MAX Debugger")
                            }
                            
                            Button(
                                onClick = {
                                    appLovinManager.showCreativeDebugger()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onTertiary,
                                    contentColor = MaterialTheme.colorScheme.tertiary
                                )
                            ) {
                                Text("Creative Debugger")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Use MAX Debugger to verify mediation setup and test ads",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                        
                        // Show AppLovin initialization status
                        if (appLovinManager.isInitialized()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "✓ AppLovin MAX is initialized",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            // Helper functions section
            item {
                HelperFunctionsCard()
            }
            
            // Ads Helper Section - Main showcase
            item {
                AdsHelperCard()
            }
            
            // Splash Screen Test Section  
            item {
                SplashScreenTestCard()
            }
            
            // App Open Ad Test Section
            item {
                AppOpenAdTestCard()
            }
            
            // Consent status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Consent Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(consentStatus, style = MaterialTheme.typography.bodyMedium)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = {
                                adManager.resetConsent()
                                consentStatus = "Consent reset"
                            }
                        ) {
                            Text("Reset Consent")
                        }
                    }
                }
            }
            
            // Interstitial Ad
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Interstitial Ad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (interstitialLoaded) "Ready to show" else "Not loaded",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    adManager.loadInterstitialAd(
                                        onAdLoaded = {
                                            interstitialLoaded = true
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Interstitial loaded")
                                            }
                                        },
                                        onAdFailedToLoad = { error ->
                                            interstitialLoaded = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Failed: ${error.message}")
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Load")
                            }
                            
                            Button(
                                onClick = {
                                    activity?.let {
                                        if (adManager.showInterstitialAd(it)) {
                                            interstitialLoaded = false
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("No interstitial ad available")
                                            }
                                        }
                                    }
                                },
                                enabled = true, // Always enabled
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Show")
                            }
                        }
                    }
                }
            }
            
            // Rewarded Ad
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Rewarded Ad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Coins: $userCoins",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (rewardedLoaded) "Ready to show" else "Not loaded",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    adManager.loadRewardedAd(
                                        onAdLoaded = {
                                            rewardedLoaded = true
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Rewarded ad loaded")
                                            }
                                        },
                                        onAdFailedToLoad = { error ->
                                            rewardedLoaded = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Failed: ${error.message}")
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Load")
                            }
                            
                            Button(
                                onClick = {
                                    activity?.let {
                                        if (adManager.showRewardedAd(
                                            activity = it,
                                            onUserEarnedReward = { reward ->
                                                userCoins += reward.amount
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Earned ${reward.amount} ${reward.type}!"
                                                    )
                                                }
                                            }
                                        )) {
                                            rewardedLoaded = false
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("No rewarded ad available")
                                            }
                                        }
                                    }
                                },
                                enabled = true, // Always enabled
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Watch for 10 coins")
                            }
                        }
                    }
                }
            }
            
            // Rewarded Interstitial Ad
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Rewarded Interstitial Ad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (rewardedInterstitialLoaded) "Ready to show" else "Not loaded",
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    adManager.loadRewardedInterstitialAd(
                                        onAdLoaded = {
                                            rewardedInterstitialLoaded = true
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Rewarded interstitial loaded")
                                            }
                                        },
                                        onAdFailedToLoad = { error ->
                                            rewardedInterstitialLoaded = false
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Failed: ${error.message}")
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Load")
                            }
                            
                            Button(
                                onClick = {
                                    activity?.let {
                                        if (adManager.showRewardedInterstitialAd(
                                            activity = it,
                                            onUserEarnedReward = { reward ->
                                                userCoins += reward.amount
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Earned ${reward.amount} ${reward.type}!"
                                                    )
                                                }
                                            }
                                        )) {
                                            rewardedInterstitialLoaded = false
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("No rewarded interstitial ad available")
                                            }
                                        }
                                    }
                                },
                                enabled = true, // Always enabled
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Show")
                            }
                        }
                    }
                }
            }
            
            // Native Ad
            item {
                NativeAdCard()
            }
            
            // Banner Ads Section
            item {
                BannerAdsCard()
            }
            
            // MREC Ad Section  
            item {
                MRECAdCard()
            }
            
            // Ad configuration info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Test Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("All ads are using test ad units", style = MaterialTheme.typography.bodySmall)
                        Text("Banner: ${AdMobConfig.TEST_BANNER_AD_UNIT_ID}", style = MaterialTheme.typography.bodySmall)
                        Text("Interstitial: ${AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID}", style = MaterialTheme.typography.bodySmall)
                        Text("Rewarded: ${AdMobConfig.TEST_REWARDED_AD_UNIT_ID}", style = MaterialTheme.typography.bodySmall)
                        Text("Native: ${AdMobConfig.TEST_NATIVE_AD_UNIT_ID}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun AdMobBanner(
    modifier: Modifier = Modifier,
    isAdaptive: Boolean = true
) {
    // Get the effective banner ad unit ID from config
    val adUnitId = AdMobMediationManager.getConfig()?.getEffectiveBannerAdUnitId() ?: ""
    if (adUnitId.isEmpty()) {
        Log.e("AdMobBanner", "No banner ad unit ID configured")
        return
    }
    AdMobBanner(
        adUnitId = adUnitId,
        modifier = modifier,
        isAdaptive = isAdaptive
    )
}

@Composable
fun AdMobBanner(
    adUnitId: String,
    modifier: Modifier = Modifier,
    isAdaptive: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var bannerView: AdMobBannerView? by remember { mutableStateOf(null) }
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> bannerView?.resume()
                Lifecycle.Event.ON_PAUSE -> bannerView?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            bannerView?.destroy()
        }
    }
    
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            AdMobBannerView(ctx).apply {
                bannerView = this
                loadBanner(
                    adUnitId = adUnitId,
                    isAdaptive = isAdaptive,
                    onAdLoaded = {
                        Log.d("Banner", "Ad loaded")
                    },
                    onAdFailedToLoad = { error ->
                        Log.e("Banner", "Failed to load: ${error.message}")
                    }
                )
            }
        }
    )
}

@Composable
fun NativeAdCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isAdLoaded by remember { mutableStateOf(false) }
    var nativeAdHelper: AdMobNativeAdHelper? by remember { mutableStateOf(null) }
    var currentNativeAd by remember { mutableStateOf<com.google.android.gms.ads.nativead.NativeAd?>(null) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Native Ad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Native ads blend with your app's content",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (nativeAdHelper == null) {
                            nativeAdHelper = AdMobNativeAdHelper(context)
                        }
                        
                        nativeAdHelper?.loadNativeAd(
                            adUnitId = AdMobConfig.TEST_NATIVE_AD_UNIT_ID,
                            onAdLoaded = { ad ->
                                currentNativeAd = ad
                                isAdLoaded = true
                                Log.d("NativeAd", "Native ad loaded successfully")
                            },
                            onAdFailedToLoad = { error ->
                                isAdLoaded = false
                                Log.e("NativeAd", "Failed to load: ${error.message}")
                            }
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Load Native Ad")
                }
                
                Text(
                    text = if (isAdLoaded) "Loaded ✓" else "Not loaded",
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Display the native ad if loaded
            if (isAdLoaded && currentNativeAd != null) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = { ctx ->
                        nativeAdHelper?.createDefaultNativeAdView(currentNativeAd!!)
                            ?: android.widget.FrameLayout(ctx)
                    }
                )
            } else {
                // Placeholder when no ad is loaded
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Native ad will appear here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Click 'Load Native Ad' to load an ad",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            nativeAdHelper?.destroy()
        }
    }
}


@Composable
fun BannerAdsCard() {
    val context = LocalContext.current
    var selectedBannerType by remember { mutableStateOf(BannerAdHelper.BannerType.BANNER) }
    var isBannerLoaded by remember { mutableStateOf(false) }
    var bannerHelper: BannerAdHelper? by remember { mutableStateOf(null) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Banner Ads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Multiple banner formats available",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Banner type selector
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(BannerAdHelper.BannerType.values().toList()) { type ->
                    FilterChip(
                        selected = selectedBannerType == type,
                        onClick = { 
                            selectedBannerType = type
                            isBannerLoaded = false
                        },
                        label = { 
                            Text(
                                text = type.name.replace("_", " "),
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Show size info
            Text(
                text = BannerAdHelper(context).getBannerSizeInfo(selectedBannerType),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Load button
            Button(
                onClick = {
                    if (bannerHelper == null) {
                        bannerHelper = BannerAdHelper(context)
                    }
                    isBannerLoaded = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load ${selectedBannerType.name.replace("_", " ")} Banner")
            }
            
            // Banner display area
            if (isBannerLoaded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Container with specific sizing for adaptive banners
                val isAdaptive = selectedBannerType in listOf(
                    BannerAdHelper.BannerType.ADAPTIVE_BANNER,
                    BannerAdHelper.BannerType.INLINE_ADAPTIVE,
                    BannerAdHelper.BannerType.ANCHORED_ADAPTIVE
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (selectedBannerType == BannerAdHelper.BannerType.MEDIUM_RECTANGLE) {
                                Modifier.height(250.dp)
                            } else if (selectedBannerType == BannerAdHelper.BannerType.LEADERBOARD) {
                                Modifier.height(90.dp)
                            } else {
                                Modifier.wrapContentHeight()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                // Post to ensure layout is measured
                                post {
                                    bannerHelper?.createBannerView(
                                        adUnitId = AdMobConfig.TEST_BANNER_AD_UNIT_ID,
                                        bannerType = selectedBannerType,
                                        container = this,
                                        maxHeight = if (selectedBannerType == BannerAdHelper.BannerType.INLINE_ADAPTIVE) 150 else 0,
                                        onAdLoaded = {
                                            Log.d("BannerAds", "${selectedBannerType} loaded successfully")
                                        },
                                        onAdFailedToLoad = { error ->
                                            Log.e("BannerAds", "${selectedBannerType} failed: ${error.message}")
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MRECAdCard() {
    val context = LocalContext.current
    var isMRECLoaded by remember { mutableStateOf(false) }
    var mrecView: AdMobMRECView? by remember { mutableStateOf(null) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "MREC (Medium Rectangle) Ad",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "300x250 dp - Perfect for content breaks",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isMRECLoaded = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Load MREC")
                }
                
                if (isMRECLoaded) {
                    Button(
                        onClick = {
                            mrecView?.refreshAd()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh")
                    }
                }
            }
            
            // MREC display area
            if (isMRECLoaded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // MREC container with border for visibility
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            AdMobMRECView(ctx).apply {
                                mrecView = this
                                loadMREC(
                                    adUnitId = AdMobConfig.TEST_BANNER_AD_UNIT_ID, // MREC uses banner test ID
                                    onAdLoaded = {
                                        Log.d("MREC", "MREC ad loaded")
                                    },
                                    onAdFailedToLoad = { error ->
                                        Log.e("MREC", "Failed to load: ${error.message}")
                                    }
                                )
                            }
                        },
                        update = { view ->
                            // Handle lifecycle if needed
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "MREC ads are ideal for placement within content",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            mrecView?.destroy()
        }
    }
}

@Composable
fun AppOpenAdTestCard() {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "App Open Ad Test",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Test app open ads manually",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        AppOpenAdManager.getInstance()?.showAdManually()
                            ?: Log.e("AppOpenAdTest", "AppOpenAdManager not initialized")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Show App Open Ad")
                }
                
                Button(
                    onClick = {
                        AppOpenAdManager.getInstance()?.let { manager ->
                            manager.fetchAd(context)
                            Log.d("AppOpenAdTest", "Fetching new app open ad")
                        } ?: Log.e("AppOpenAdTest", "AppOpenAdManager not initialized")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Fetch Ad")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Note: Go to home and return to test automatic display",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun SplashScreenTestCard() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Splash Screen Test",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Test different splash screen configurations",
                style = MaterialTheme.typography.bodySmall
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        activity?.let { act ->
                            // Test with 2 second splash with progress
                            SimpleSplashHelper.Builder(act)
                                .setDuration(2000)
                                .showProgress(true)
                                .onComplete { 
                                    Log.d("SplashTest", "2 second splash completed")
                                }
                                .build()
                                .show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("2s Splash")
                }
                
                Button(
                    onClick = {
                        activity?.let { act ->
                            // Test with 5 second splash without progress
                            SimpleSplashHelper.Builder(act)
                                .setDuration(5000)
                                .showProgress(false)
                                .onComplete { 
                                    Log.d("SplashTest", "5 second splash completed")
                                    MzgsHelper.showToast(context, "Splash completed!")
                                }
                                .build()
                                .show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("5s No Progress")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    activity?.let { act ->
                        // Test 3 second splash with progress
                        SimpleSplashHelper.Builder(act)
                            .setDuration(3000)
                            .showProgress(true)
                            .onComplete { 
                                Log.d("SplashTest", "3 second splash completed")
                            }
                            .build()
                            .show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test 3s with Progress")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "Note: Splash screen already shown on app start (3s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun AdsHelperCard() {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // States for ad loading
    var interstitialLoaded by remember { mutableStateOf(false) }
    var rewardedLoaded by remember { mutableStateOf(false) }
    var rewardedInterstitialLoaded by remember { mutableStateOf(false) }
    var appOpenLoaded by remember { mutableStateOf(false) }
    var userCoins by remember { mutableStateOf(0) }
    
    // Load ads on initialization
    LaunchedEffect(Unit) {
        // Load interstitial ad
        AdMobMediationManager.loadInterstitialAd(
            onAdLoaded = {
                interstitialLoaded = true
                Log.d("AdsHelper", "Interstitial loaded")
            },
            onAdFailedToLoad = { error ->
                interstitialLoaded = false
                Log.e("AdsHelper", "Interstitial failed: ${error.message}")
            }
        )
        
        // Load rewarded ad
        AdMobMediationManager.loadRewardedAd(
            onAdLoaded = {
                rewardedLoaded = true
                Log.d("AdsHelper", "Rewarded ad loaded")
            },
            onAdFailedToLoad = { error ->
                rewardedLoaded = false
                Log.e("AdsHelper", "Rewarded ad failed: ${error.message}")
            }
        )
        
        // Load rewarded interstitial ad
        AdMobMediationManager.loadRewardedInterstitialAd(
            onAdLoaded = {
                rewardedInterstitialLoaded = true
                Log.d("AdsHelper", "Rewarded interstitial loaded")
            },
            onAdFailedToLoad = { error ->
                rewardedInterstitialLoaded = false
                Log.e("AdsHelper", "Rewarded interstitial failed: ${error.message}")
            }
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Ads Helper - Unified Ad System",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "All ads auto-loaded on init. Just click to show!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Interstitial Ad
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Interstitial Ad",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (Ads.isAnyInterstitialReady()) "Ready ✓" else "Loading...",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (Ads.isAnyInterstitialReady()) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            if (Ads.showInterstitial()) {
                                interstitialLoaded = false
                                // Reload for next time
                                AdMobMediationManager.loadInterstitialAd()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Interstitial not ready")
                                }
                            }
                        },
                        enabled = Ads.isAnyInterstitialReady()
                    ) {
                        Text("Show")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rewarded Ad
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Rewarded Ad",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Coins: $userCoins",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            if (Ads.isAnyRewardedAdReady()) "Ready ✓" else "Loading...",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (Ads.isAnyRewardedAdReady()) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            activity?.let { act ->
                                AdMobMediationManager.setCurrentActivity(act)
                                if (AdMobMediationManager.showRewardedAd(
                                    activity = act,
                                    onUserEarnedReward = { reward ->
                                        userCoins += reward.amount
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Earned ${reward.amount} ${reward.type}!"
                                            )
                                        }
                                    }
                                )) {
                                    rewardedLoaded = false
                                    // Reload for next time
                                    AdMobMediationManager.loadRewardedAd()
                                }
                            }
                        },
                        enabled = Ads.isAnyRewardedAdReady()
                    ) {
                        Text("Watch (+10)")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Rewarded Interstitial Ad
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Rewarded Interstitial",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (rewardedInterstitialLoaded) "Ready ✓" else "Loading...",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rewardedInterstitialLoaded) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            activity?.let { act ->
                                AdMobMediationManager.setCurrentActivity(act)
                                if (AdMobMediationManager.showRewardedInterstitialAd(
                                    activity = act,
                                    onUserEarnedReward = { reward ->
                                        userCoins += reward.amount
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Earned ${reward.amount} ${reward.type}!"
                                            )
                                        }
                                    }
                                )) {
                                    rewardedInterstitialLoaded = false
                                    // Reload for next time
                                    AdMobMediationManager.loadRewardedInterstitialAd()
                                }
                            }
                        },
                        enabled = rewardedInterstitialLoaded
                    ) {
                        Text("Show")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // App Open Ad
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "App Open Ad",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Shows on app resume",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            if (Ads.showAppOpenAd()) {
                                Log.d("AdsHelper", "App open ad shown")
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("App open ad not ready")
                                }
                            }
                        }
                    ) {
                        Text("Show")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Banner Ad
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Banner Ad",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Auto-displayed below",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Banner container
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                activity?.let { act ->
                                    Ads.showBanner(act, this, Ads.BannerSize.ADAPTIVE)
                                }
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // MREC Ad
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "MREC Ad (300x250)",
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // MREC container
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                activity?.let { act ->
                                    Ads.showMREC(act, this)
                                }
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Native Ad
            OutlinedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        "Native Ad",
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Native ad container
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
                                activity?.let { act ->
                                    Ads.showNativeAd(act, this)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HelperFunctionsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "MzgsHelper Functions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        MzgsHelper.showToast(context, "Hello from MzgsHelper!")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Show Toast")
                }
                
                Button(
                    onClick = {
                        val isNetworkAvailable = MzgsHelper.isNetworkAvailable(context)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Network: ${if (isNetworkAvailable) "Connected" else "Disconnected"}"
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Check Network")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            val appVersion = MzgsHelper.getAppVersion(context)
            val appVersionCode = MzgsHelper.getAppVersionCode(context)
            
            Text(
                "App Version: $appVersion (Code: $appVersionCode)",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdMobTestScreenPreview() {
    MzgsAndroidHelperTheme {
        AdMobTestScreen()
    }
}
}