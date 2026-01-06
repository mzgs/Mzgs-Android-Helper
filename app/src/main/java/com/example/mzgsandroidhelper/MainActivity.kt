package com.example.mzgsandroidhelper

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.lifecycleScope
import com.example.mzgsandroidhelper.ui.theme.MzgsAndroidHelperTheme
import com.google.android.gms.ads.AdSize
import com.mzgs.helper.AdmobConfig
import com.mzgs.helper.AdmobDebug
import com.mzgs.helper.AdmobMediation
import com.mzgs.helper.Ads
import com.mzgs.helper.ApplovinMaxConfig
import com.mzgs.helper.ApplovinMaxDebug
import com.mzgs.helper.ApplovinMaxMediation
import com.mzgs.helper.MzgsHelper
import com.mzgs.helper.Pref
import com.mzgs.helper.Remote
import com.mzgs.helper.SimpleSplashHelper
import com.mzgs.helper.printLine
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var isSplashComplete = mutableStateOf(false)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()



        lifecycleScope.launch {
            val activity = this@MainActivity
            SimpleSplashHelper.showSplash(activity)
            App.waitForRemoteInit()
            MzgsHelper.initAllowedCountry(activity)

            val splashDuration = if (MzgsHelper.isDebug(activity))  500 else Remote.getLong("splash_time", 11_000)
            SimpleSplashHelper.setDuration(splashDuration)
            SimpleSplashHelper.startProgress(activity)
            SimpleSplashHelper.setOnComplete {

                Ads.showInterstitial(activity){
                    isSplashComplete.value = true
                }


            }

        } // end launch




        setContent {
            MzgsAndroidHelperTheme {
                MainExampleScreen(isSplashComplete = isSplashComplete.value)
            }
        }

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainExampleScreen(isSplashComplete: Boolean) {
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
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    val currentActivity = activity
                    if (currentActivity == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Activity not available")
                        }
                        return@Button
                    }
                    currentActivity.startActivity(
                        Intent(currentActivity, ExampleActivity::class.java)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open example activity")
            }
            Button(
                onClick = {
                    val currentActivity = activity
                    if (currentActivity == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Activity not available")
                        }
                        return@Button
                    }
                    currentActivity.startActivity(
                        Intent(currentActivity, AdsExample::class.java)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open ads example")
            }

            Text(
                text = "AdMob",
                style = MaterialTheme.typography.headlineMedium
            )

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
                    AdmobMediation.showInterstitial(currentActivity)

                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Interstitial")
            }

            Text(
                text = "Rewarded + App Open",
                style = MaterialTheme.typography.headlineSmall
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
                    AdmobMediation.showReward(
                        currentActivity,
                        onRewarded = { type, amount ->
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Rewarded: $amount $type")
                            }
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show Rewarded")
            }
            Button(
                onClick = {
                    val currentActivity = activity
                    if (currentActivity == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Activity not available")
                        }
                        return@Button
                    }
                    AdmobMediation.showAppOpenAd(currentActivity)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show App Open")
            }

            Text(
                text = "AdMob Banner",
                style = MaterialTheme.typography.headlineSmall
            )
            if (isSplashComplete) {
                AdmobMediation.showBanner(
                    modifier = Modifier.fillMaxWidth()
                ) { errorMessage ->
                    printLine("AdMob banner ad failed: $errorMessage")
                }
            }

            Text(
                text = "AdMob MREC",
                style = MaterialTheme.typography.headlineSmall
            )
            if (isSplashComplete) {
                AdmobMediation.showMrec(
                    modifier = Modifier.size(300.dp, 250.dp),
                )
            }

            Text(
                text = "AdMob Native",
                style = MaterialTheme.typography.headlineSmall
            )
            if (isSplashComplete) {
                AdmobMediation.showNativeAd(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) { errorMessage ->
                    printLine("AdMob native ad failed: $errorMessage")
                }
            }

            Text(
                text = "AppLovin MAX",
                style = MaterialTheme.typography.headlineMedium
            )
            Button(
                onClick = {
                    ApplovinMaxMediation.showMediationDebugger(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open MAX Debugger")
            }
            Button(
                onClick = {
                    val currentActivity = activity
                    if (currentActivity == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Activity not available")
                        }
                        return@Button
                    }
                    ApplovinMaxMediation.showInterstitial(currentActivity)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show MAX Interstitial")
            }

            Text(
                text = "MAX Banner",
                style = MaterialTheme.typography.headlineSmall
            )
            if (isSplashComplete) {
                ApplovinMaxMediation.showBanner(
                    modifier = Modifier.fillMaxWidth()
                ) { errorMessage ->
                    printLine("MAX banner ad failed: $errorMessage")
                }
            }

            Text(
                text = "MAX MREC",
                style = MaterialTheme.typography.headlineSmall
            )
            if (isSplashComplete) {
                ApplovinMaxMediation.showMrec(
                    modifier = Modifier.size(300.dp, 250.dp),
                ) { errorMessage ->
                    printLine("MAX MREC ad failed: $errorMessage")
                }
            }

            Text(
                text = "MAX Native",
                style = MaterialTheme.typography.headlineSmall
            )
            if (isSplashComplete) {
                ApplovinMaxMediation.showNativeAd(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) { errorMessage ->
                    printLine("MAX native ad failed: $errorMessage")
                }
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
