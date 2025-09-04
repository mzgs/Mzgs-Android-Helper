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
        
        // Initialize WebView early to prevent JavaScript engine errors
        try {
            android.webkit.WebView(this).destroy()
        } catch (e: Exception) {
            Log.d("MainActivity", "WebView pre-initialization: ${e.message}")
        }

        FirebaseAnalyticsManager.initialize(this)
        Remote.init(this)
        Ads.init(this)


        val admobConfig = AdMobConfig(
            appId = "ca-app-pub-8689213949805403~6434330617",
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
            appLovinConfig  ,
            onFinish = {
                Log.d("MainActivity", "Splash and ad sequence completed")


            },
            onFinishAndApplovinReady = {


                p(  "AppLovin SDK initialized successfully")
                AppLovinMediationManager.loadInterstitialAd()

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
    val appLovinManager = remember { AppLovinMediationManager.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    
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
                                    // Call with activity directly
                                    activity?.let {
                                        AppLovinMediationManager.showMediationDebugger(it)
                                    } ?: Log.e("MainActivity", "Activity context not available")
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
                                    // Ensure we have the latest activity reference before showing debugger
                                    activity?.let {
                                        AppLovinMediationManager.getInstance(it)
                                        appLovinManager.showCreativeDebugger()
                                    } ?: Log.e("MainActivity", "Activity context not available")
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
            
            // Ads Helper Section - Main showcase
            item {
                AdsHelperCard()
            }
            
            // Splash Screen Test Section  
            item {
                SplashScreenTestCard()
            }
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
    LaunchedEffect(activity) {
        // Activity is now automatically tracked via lifecycle callbacks
        Log.d("AdsHelper", "Activity auto-tracked for AdMob")
        
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
                        }
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
                                // Activity is auto-tracked via lifecycle callbacks
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
                        }
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
                                // Activity is auto-tracked via lifecycle callbacks
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
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Rewarded interstitial not ready")
                                    }
                                }
                            }
                        }
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
                    Text(
                        "Banner Ad (Adaptive)",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Banner container - always visible
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .wrapContentHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    // Load banner immediately when view is created
                                    activity?.let { act ->
                                        // Activity is auto-tracked via lifecycle callbacks
                                        Log.d("AdsHelper", "Loading banner ad on create")
                                        Ads.showBanner(act, this, Ads.BannerSize.ADAPTIVE)
                                    }
                                }
                            }
                        )
                    }
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
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // MREC container - always visible
                    Card(
                        modifier = Modifier
                            .width(300.dp)
                            .height(250.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                FrameLayout(ctx).apply {
                                    layoutParams = FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT
                                    )
                                    // Load MREC immediately when view is created
                                    activity?.let { act ->
                                        // Activity is auto-tracked via lifecycle callbacks
                                        Log.d("AdsHelper", "Loading MREC ad on create")
                                        Ads.showMREC(act, this)
                                    }
                                }
                            }
                        )
                    }
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


@Preview(showBackground = true)
@Composable
fun AdMobTestScreenPreview() {
    MzgsAndroidHelperTheme {
        AdMobTestScreen()
    }
}
}