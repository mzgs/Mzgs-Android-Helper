package com.example.mzgsandroidhelper

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.FrameLayout

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
import com.mzgs.helper.MzgsHelper
import com.mzgs.helper.Remote
import com.mzgs.helper.SimpleSplashHelper
import com.mzgs.helper.analytics.FirebaseAnalyticsManager

import com.mzgs.helper.printLine
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
             }
        }

    }
}

