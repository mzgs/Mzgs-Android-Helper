package com.example.mzgsandroidhelper

import android.app.Activity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mzgsandroidhelper.ui.theme.MzgsAndroidHelperTheme
import com.google.android.gms.ads.AdSize
import com.mzgs.helper.Ads

class AdsExample : ComponentActivity() {

    private var lastEvent by mutableStateOf("Idle")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()


        setContent {
            MzgsAndroidHelperTheme {
                AdsExampleScreen(
                    activity = this,
                    lastEvent = lastEvent,
                    onEventUpdate = { lastEvent = it },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdsExampleScreen(
    activity: Activity,
    lastEvent: String,
    onEventUpdate: (String) -> Unit,
) {
    var networks by remember { mutableStateOf("applovin,admob") }
    var appliedNetworks by remember { mutableStateOf(networks) }
    var showBanner by remember { mutableStateOf(false) }
    var bannerKey by remember { mutableStateOf(0) }
    var showMrec by remember { mutableStateOf(false) }
    var mrecKey by remember { mutableStateOf(0) }
    var showNative by remember { mutableStateOf(false) }
    var nativeKey by remember { mutableStateOf(0) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ads Example") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Ads helper quick checks",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = networks,
                onValueChange = { networks = it },
                label = { Text("Networks order") },
                supportingText = { Text("Comma separated: applovin,admob") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    Ads.showInterstitial(activity, networks = networks) {
                        onEventUpdate("Interstitial closed")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show interstitial")
            }
            Button(
                onClick = {
                    appliedNetworks = networks
                    bannerKey += 1
                    showBanner = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show banner")
            }
            Button(
                onClick = {
                    appliedNetworks = networks
                    mrecKey += 1
                    showMrec = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show MREC")
            }
            Button(
                onClick = {
                    appliedNetworks = networks
                    nativeKey += 1
                    showNative = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show native")
            }

            Text(
                text = "Last event: $lastEvent",
                style = MaterialTheme.typography.bodyMedium
            )

            if (showBanner) {
                key(bannerKey) {
                    Ads.showBanner(
                        modifier = Modifier.fillMaxWidth(),
                        networks = appliedNetworks,
                        adSize = AdSize.BANNER,
                    ) { errorMessage ->
                        onEventUpdate("Banner failed: $errorMessage")
                    }
                }
            }
            if (showMrec) {
                key(mrecKey) {
                    Ads.showMrec(
                        modifier = Modifier.fillMaxWidth(),
                        networks = appliedNetworks,
                    ) { errorMessage ->
                        onEventUpdate("MREC failed: $errorMessage")
                    }
                }
            }
            if (showNative) {
                key(nativeKey) {
                    Ads.showNativeAd(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        networks = appliedNetworks,
                    ) { errorMessage ->
                        onEventUpdate("Native failed: $errorMessage")
                    }
                }
            }

        }
    }
}
