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
import com.mzgs.helper.admob.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize AdMob
        val adManager = AdMobMediationManager.getInstance(this)
        adManager.initialize(
            testDeviceIds = listOf("YOUR_TEST_DEVICE_ID"),
            onInitComplete = {
                Log.d("MainActivity", "AdMob initialized")
            }
        )
        
        setContent {
            MzgsAndroidHelperTheme {
                AdMobTestScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdMobTestScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val adManager = remember { AdMobMediationManager.getInstance(context) }
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
            adManager.requestConsentInfo(
                activity = act,
                debugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
                onConsentReady = {
                    consentStatus = "Consent obtained"
                    Log.d("Consent", "Ready to show ads")
                },
                onConsentError = { error ->
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
            // Banner ad at bottom
            AdMobBanner(
                adUnitId = AdMobConfig.TEST_BANNER_AD_UNIT_ID,
                isAdaptive = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Helper functions section
            item {
                HelperFunctionsCard()
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
                                        adUnitId = AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID,
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
                                        }
                                    }
                                },
                                enabled = interstitialLoaded,
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
                                        adUnitId = AdMobConfig.TEST_REWARDED_AD_UNIT_ID,
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
                                        }
                                    }
                                },
                                enabled = rewardedLoaded,
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
                                        adUnitId = AdMobConfig.TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
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
                                        }
                                    }
                                },
                                enabled = rewardedInterstitialLoaded,
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