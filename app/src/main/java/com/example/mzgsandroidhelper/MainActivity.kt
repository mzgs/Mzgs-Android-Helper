package com.example.mzgsandroidhelper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mzgsandroidhelper.ui.theme.MzgsAndroidHelperTheme
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isSplashComplete = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()



        FirebaseAnalyticsManager.initialize()

//        MzgsHelper.onActivityReady { activity ->
//            lifecycleScope.launch {
//                SimpleSplashHelper.showSplash(activity)
//                Remote.initSync()
//                MzgsHelper.setIPCountry()
//                Ads.init()
//
//                // Splash duration and OnComplete
//                SimpleSplashHelper
//                    .setDuration(Remote.getLong("splash_time", 10_000))
//                    .setOnComplete {
//                        // Show interstitial ad
//                        val adResult = Ads.showInterstitialWithResult(
//                            onAdClosed = {
//                                MzgsHelper.restrictedCountries = listOf("UK", "US", "GB", "CN", "MX", "JP", "KR", "AR", "HK", "IN", "PK", "TR", "VN", "RU", "SG", "MO", "TW", "PY","BR")
//                                MzgsHelper.setRestrictedCountriesFromRemoteConfig()
//                                MzgsHelper.setIsAllowedCountry()
//
//                                isSplashComplete.value = true
//
//                                Ads.initAppLovinMax(appLovinConfig) {
//                                    Ads.loadApplovinMaxInterstitial()
//                                }
//
//                            }
//                        )
//                        FirebaseAnalyticsManager.logEvent(
//                            if (adResult.success) "splash_ad_shown" else "splash_ad_failed",
//                            Bundle().apply {
//                                putString("ad_network", adResult.network ?: "unknown")
//                            }
//                        )
//                    }
//
//
//                AdMobManager.showUmpConsent(forceDebugConsentInEea = true) {
//
//                    Ads.initAdMob(admobConfig) {
//                        printLine("admob init success")
//                        Ads.loadAdmobInterstitial()
//                        SimpleSplashHelper.startProgress()
//
//                    }
//
//                }
//
//
//            }
//        }




        setContent {
            MzgsAndroidHelperTheme {
                MainExampleScreen()
            }
        }

    }
}

private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-8689213949805403/4964803980"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainExampleScreen() {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Mzgs Helper Example") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Interstitial Demo",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Tap to show the preloaded interstitial.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = {
                    val currentActivity = activity
                    if (currentActivity == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Activity not available")
                        }
                        return@Button
                    }
                    val ad = InterstitialAdPreloader.pollAd(INTERSTITIAL_AD_UNIT_ID)
                    if (ad == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Interstitial not ready yet")
                        }
                    } else {
                        ad.show(currentActivity)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Interstitial")
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
