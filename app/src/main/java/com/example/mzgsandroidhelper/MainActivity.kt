package com.example.mzgsandroidhelper

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardItem
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mzgsandroidhelper.ui.theme.MzgsAndroidHelperTheme
import com.mzgs.helper.Ads
import com.mzgs.helper.MzgsHelper
import com.mzgs.helper.Remote
import com.mzgs.helper.SimpleSplashHelper
import com.mzgs.helper.admob.AdMobConfig
import com.mzgs.helper.admob.AdMobManager
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import com.mzgs.helper.applovin.AppLovinConfig
import com.mzgs.helper.applovin.AppLovinMediationManager
import com.mzgs.helper.printLine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isSplashComplete = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        val admobConfig = AdMobConfig(
            appId = "ca-app-pub-8689213949805403~6434330617",
            bannerAdUnitId = "",
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            rewardedInterstitialAdUnitId = "",
            nativeAdUnitId = "",
            mrecAdUnitId = "",
            appOpenAdUnitId = "",
            enableAppOpenAd = true,
            enableTestMode = true,
            testDeviceIds = listOf("3d6496d1-4784-4b96-bf5e-2d61200765de"),
            showAdsInDebug = true,
            showInterstitialsInDebug = true,
            bannerAutoRefreshSeconds = 30,
            showAppOpenAdInDebug = true,
            showBannersInDebug = true,
            showNativeAdsInDebug = true,
            showRewardedAdsInDebug = true

        )


        val appLovinConfig = AppLovinConfig(
            sdkKey = "sTOrf_0s7y7dzVqfTPRR0Ck_synT0Xrs0DgfChVKedyc7nGgAi6BwrAnnxEoT3dTHJ7T0dpfFmGNXX3hE9u9",
            bannerAdUnitId = "",
            interstitialAdUnitId = "",
            rewardedAdUnitId = "",
            mrecAdUnitId = "",
            nativeAdUnitId = "",
            bannerAutoRefreshSeconds = 30,
            enableTestMode = true,
            verboseLogging = true,
            creativeDebuggerEnabled = true,
            showAdsInDebug = true,
            testDeviceAdvertisingIds = listOf("ebd59ada-3c0f-4d4a-bb8a-1e8966dee95f")
        )


        MzgsHelper.init(this, this, skipAdsInDebug = false)
        FirebaseAnalyticsManager.initialize()

        lifecycleScope.launch {
            SimpleSplashHelper.showSplash(MzgsHelper.getActivity())
            Remote.initSync()
            MzgsHelper.setIPCountry()
            Ads.init()

            // Splash duration and OnComplete
            SimpleSplashHelper
                .setDuration(Remote.getLong("splash_time", 10_000))
                .setOnComplete {

                    // Show interstitial ad
                    val adResult = Ads.showInterstitialWithResult(
                        onAdClosed = {
                            MzgsHelper.restrictedCountries = listOf("UK", "US", "GB", "CN", "MX", "JP", "KR", "AR", "HK", "IN", "PK", "TR", "VN", "RU", "SG", "MO", "TW", "PY","BR")
                            MzgsHelper.setRestrictedCountriesFromRemoteConfig()
                            MzgsHelper.setIsAllowedCountry()

                            isSplashComplete.value = true

                            Ads.initAppLovinMax(appLovinConfig) {
                                Ads.loadApplovinMaxInterstitial()
                            }

                        }
                    )
                    FirebaseAnalyticsManager.logEvent(
                        if (adResult.success) "splash_ad_shown" else "splash_ad_failed",
                        Bundle().apply {
                            putString("ad_network", adResult.network ?: "unknown")
                        }
                    )
                }


            AdMobManager.showUmpConsent(forceDebugConsentInEea = true) {

                Ads.initAdMob(admobConfig) {
                    printLine("admob init success")
                    Ads.loadAdmobInterstitial()
                    SimpleSplashHelper.startProgress()

                }

            }


        }




        setContent {
            MzgsAndroidHelperTheme {
                AdMobTestScreen(isSplashComplete = isSplashComplete.value)
            }
        }

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdMobTestScreen(isSplashComplete: Boolean = false) {
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
                AdsHelperCard(
                    isFullyInitialized = isSplashComplete,
                    snackbarHostState = snackbarHostState
                )
            }

            // Splash Screen Test Section  
            item {
                SplashScreenTestCard()
            }

            // Search Section with Ads
            item {
                SearchWithAdsCard()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchWithAdsCard() {
    var searchQuery by remember { mutableStateOf("") }
    var showResults by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Search with Native Ads",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Search Input Field
            TextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    showResults = it.isNotEmpty()
                },
                placeholder = { Text("Search for products...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                singleLine = true
            )

            // Search Results
            if (showResults) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Search Results for: \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Display 5 dummy results with ad as 2nd item
                for (index in 0..4) {
                    when (index) {
                        1 -> {
                            // Show Adaptive Banner as 2nd item
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                // Adaptive Banner Container
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
                                                // Load adaptive banner ad
                                                Ads.showBanner(this, Ads.BannerSize.LARGE_BANNER)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        else -> {
                            // Regular search result item
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
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
                                            "Product ${index + 1}: ${searchQuery}",
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            "Great product matching your search",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "$${(20 + index * 10)}.99",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            Log.d("Search", "View product ${index + 1}")
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    ) {
                                        Text("View")
                                    }
                                }
                            }
                        }
                    }
                }
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
                            SimpleSplashHelper.showSplash(act)
                            SimpleSplashHelper
                                .setDuration(2000)
                                .setShowProgress(true)
                                .setOnComplete {
                                    Log.d("SplashTest", "2 second splash completed")
                                }
                            SimpleSplashHelper.startProgress()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("2s Splash")
                }

                Button(
                    onClick = {
                        activity?.let { act ->
                            SimpleSplashHelper.showSplash(act)
                            SimpleSplashHelper
                                .setDuration(5000)
                                .setShowProgress(false)
                                .setOnComplete {
                                    Log.d("SplashTest", "5 second splash completed")
                                    MzgsHelper.showToast("Splash completed!")
                                }
                            SimpleSplashHelper.startProgress()
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
                        SimpleSplashHelper.showSplash(act)
                        SimpleSplashHelper
                            .setDuration(3000)
                            .setShowProgress(true)
                            .setOnComplete {
                                Log.d("SplashTest", "3 second splash completed")
                            }
                        SimpleSplashHelper.startProgress()
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
fun AdsHelperCard(
    isFullyInitialized: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // States for ad loading
    var interstitialLoaded by remember { mutableStateOf(false) }
    var rewardedLoaded by remember { mutableStateOf(false) }
    var rewardedInterstitialLoaded by remember { mutableStateOf(false) }
    var appOpenLoaded by remember { mutableStateOf(false) }
    var userCoins by remember { mutableStateOf(0) }
    var hasRequestedMrec by remember { mutableStateOf(false) }

    // Load ads on initialization
    LaunchedEffect(activity) {
        // Activity is now automatically tracked via lifecycle callbacks
        Log.d("AdsHelper", "Activity auto-tracked for AdMob")


        // Load rewarded interstitial ad
        AdMobManager.loadRewardedInterstitialAd(
            onAdLoaded = {
                rewardedInterstitialLoaded = true
                Log.d("AdsHelper", "Rewarded interstitial loaded")
            },
            onAdFailedToLoad = { error: LoadAdError ->
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

            // Interstitial with Cycle
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
                            "Interstitial Cycle Test",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Shows ad every 3 clicks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            // Call the cycle method - shows ad every 3 clicks
                            Ads.showInterstitialWithCycle("testButton", 3)
                            scope.launch {
                                snackbarHostState.showSnackbar("Button clicked! Ad will show every 3rd click")
                            }
                        }
                    ) {
                        Text("Test Cycle")
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
                                if (Ads.showRewardedAd(
                                        onUserEarnedReward = { type, amount ->
                                            userCoins += amount
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Earned $amount $type!"
                                                )
                                            }
                                        }
                                    )
                                ) {
                                    rewardedLoaded = false
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
                                if (AdMobManager.showRewardedInterstitialAd(
                                        activity = act,
                                        onUserEarnedReward = { reward: RewardItem ->
                                            userCoins += reward.amount
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    "Earned ${reward.amount} ${reward.type}!"
                                                )
                                            }
                                        }
                                    )
                                ) {
                                    rewardedInterstitialLoaded = false
                                    // Reload for next time
                                    AdMobManager.loadRewardedInterstitialAd()
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
                                }
                            },
                            update = { frameLayout ->
                                // Load banner only after splash is complete
                                if (isFullyInitialized) {
                                    Log.d("AdsHelper", "Loading banner ad after fully initialized")
                                    Ads.showBanner(frameLayout, Ads.BannerSize.ADAPTIVE)
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
                                }
                            },
                            update = { frameLayout ->
                                if (isFullyInitialized && !hasRequestedMrec) {
                                    Log.d("AdsHelper", "Loading MREC ad after initialization")
                                    hasRequestedMrec = Ads.showMREC(frameLayout)
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
                                Ads.showNativeAd(this)
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
