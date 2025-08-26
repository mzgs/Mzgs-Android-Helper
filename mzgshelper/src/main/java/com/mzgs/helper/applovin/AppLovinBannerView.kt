package com.mzgs.helper.applovin

import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.applovin.mediation.MaxError
import com.applovin.mediation.ads.MaxAdView

@Composable
fun AppLovinBannerView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (MaxError) -> Unit = {},
    onAdClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    var bannerAd by remember { mutableStateOf<MaxAdView?>(null) }
    
    DisposableEffect(adUnitId) {
        onDispose {
            bannerAd?.let {
                Log.d("AppLovinBannerView", "Disposing banner ad")
                it.stopAutoRefresh()
                it.destroy()
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .width(320.dp)
                .height(50.dp),
            factory = { context ->
                FrameLayout(context).apply {
                    val helper = AppLovinBannerHelper(context)
                    bannerAd = helper.createStandardBanner(
                        adUnitId = adUnitId,
                        container = this,
                        onAdLoaded = onAdLoaded
                    )
                }
            }
        )
    }
}

@Composable
fun AppLovinAdaptiveBannerView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (MaxError) -> Unit = {},
    onAdClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    var bannerAd by remember { mutableStateOf<MaxAdView?>(null) }
    
    DisposableEffect(adUnitId) {
        onDispose {
            bannerAd?.let {
                Log.d("AppLovinAdaptiveBanner", "Disposing adaptive banner ad")
                it.stopAutoRefresh()
                it.destroy()
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                FrameLayout(context).apply {
                    val helper = AppLovinBannerHelper(context)
                    bannerAd = helper.createAdaptiveBanner(
                        adUnitId = adUnitId,
                        container = this,
                        onAdLoaded = onAdLoaded,
                        onAdFailedToLoad = onAdFailedToLoad,
                        onAdClicked = onAdClicked
                    )
                }
            }
        )
    }
}

@Composable
fun AppLovinLeaderBannerView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (MaxError) -> Unit = {},
    onAdClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    var bannerAd by remember { mutableStateOf<MaxAdView?>(null) }
    
    DisposableEffect(adUnitId) {
        onDispose {
            bannerAd?.let {
                Log.d("AppLovinLeaderBanner", "Disposing leader banner ad")
                it.stopAutoRefresh()
                it.destroy()
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .width(728.dp)
                .height(90.dp),
            factory = { context ->
                FrameLayout(context).apply {
                    val helper = AppLovinBannerHelper(context)
                    bannerAd = helper.createLeaderBanner(
                        adUnitId = adUnitId,
                        container = this,
                        onAdLoaded = onAdLoaded
                    )
                }
            }
        )
    }
}

@Composable
fun AppLovinBannerWithConfig(
    config: AppLovinConfig,
    modifier: Modifier = Modifier,
    bannerType: AppLovinBannerHelper.BannerType = AppLovinBannerHelper.BannerType.BANNER,
    backgroundColor: Color = Color.Transparent,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (MaxError) -> Unit = {},
    onAdClicked: () -> Unit = {}
) {
    val adUnitId = when (bannerType) {
        AppLovinBannerHelper.BannerType.BANNER -> config.getEffectiveBannerAdUnitId()
        AppLovinBannerHelper.BannerType.MREC -> config.getEffectiveMrecAdUnitId()
        AppLovinBannerHelper.BannerType.LEADER -> config.getEffectiveBannerAdUnitId()
    }
    
    if (adUnitId.isEmpty() || adUnitId == "YOUR_TEST_BANNER_ID") {
        Log.w("AppLovinBannerWithConfig", "Invalid ad unit ID")
        return
    }
    
    when (bannerType) {
        AppLovinBannerHelper.BannerType.BANNER -> {
            AppLovinBannerView(
                adUnitId = adUnitId,
                modifier = modifier,
                backgroundColor = backgroundColor,
                onAdLoaded = onAdLoaded,
                onAdFailedToLoad = onAdFailedToLoad,
                onAdClicked = onAdClicked
            )
        }
        AppLovinBannerHelper.BannerType.MREC -> {
            AppLovinMRECView(
                adUnitId = adUnitId,
                modifier = modifier,
                backgroundColor = backgroundColor,
                onAdLoaded = onAdLoaded,
                onAdFailedToLoad = onAdFailedToLoad,
                onAdClicked = onAdClicked
            )
        }
        AppLovinBannerHelper.BannerType.LEADER -> {
            AppLovinLeaderBannerView(
                adUnitId = adUnitId,
                modifier = modifier,
                backgroundColor = backgroundColor,
                onAdLoaded = onAdLoaded,
                onAdFailedToLoad = onAdFailedToLoad,
                onAdClicked = onAdClicked
            )
        }
    }
}