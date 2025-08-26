package com.mzgs.helper.applovin

import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
fun AppLovinMRECView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (MaxError) -> Unit = {},
    onAdClicked: () -> Unit = {},
    onAdExpanded: () -> Unit = {},
    onAdCollapsed: () -> Unit = {}
) {
    val context = LocalContext.current
    var mrecAd by remember { mutableStateOf<MaxAdView?>(null) }
    
    DisposableEffect(adUnitId) {
        onDispose {
            mrecAd?.let {
                Log.d("AppLovinMRECView", "Disposing MREC ad")
                it.stopAutoRefresh()
                it.destroy()
            }
        }
    }
    
    Box(
        modifier = modifier
            .width(300.dp)
            .height(250.dp)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .width(300.dp)
                .height(250.dp),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val helper = AppLovinBannerHelper(context)
                    mrecAd = helper.createMREC(
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
fun AppLovinMRECWithConfig(
    config: AppLovinConfig,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (MaxError) -> Unit = {},
    onAdClicked: () -> Unit = {},
    onAdExpanded: () -> Unit = {},
    onAdCollapsed: () -> Unit = {}
) {
    val adUnitId = config.getEffectiveMrecAdUnitId()
    
    if (adUnitId.isEmpty() || adUnitId == "YOUR_TEST_MREC_ID") {
        Log.w("AppLovinMRECWithConfig", "Invalid MREC ad unit ID")
        return
    }
    
    AppLovinMRECView(
        adUnitId = adUnitId,
        modifier = modifier,
        backgroundColor = backgroundColor,
        onAdLoaded = onAdLoaded,
        onAdFailedToLoad = onAdFailedToLoad,
        onAdClicked = onAdClicked,
        onAdExpanded = onAdExpanded,
        onAdCollapsed = onAdCollapsed
    )
}

@Composable
fun AppLovinInlineMREC(
    adUnitId: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    placement: String? = null,
    customData: String? = null,
    onAdLoaded: () -> Unit = {},
    onAdFailedToLoad: (MaxError) -> Unit = {},
    onAdClicked: () -> Unit = {}
) {
    val context = LocalContext.current
    var mrecAd by remember { mutableStateOf<MaxAdView?>(null) }
    
    DisposableEffect(adUnitId) {
        onDispose {
            mrecAd?.let { ad ->
                Log.d("AppLovinInlineMREC", "Disposing inline MREC ad")
                ad.stopAutoRefresh()
                ad.destroy()
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier
                .width(300.dp)
                .height(250.dp),
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    val helper = AppLovinBannerHelper(context)
                    val adView = helper.createMREC(
                        adUnitId = adUnitId,
                        container = this,
                        onAdLoaded = onAdLoaded
                    )
                    
                    adView?.let {
                        placement?.let { p -> helper.setPlacement(it, p) }
                        customData?.let { cd -> helper.setCustomData(it, cd) }
                    }
                    
                    mrecAd = adView
                }
            }
        )
    }
}

@Composable
fun AppLovinMRECGrid(
    adUnitIds: List<String>,
    columns: Int = 2,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onAdLoaded: (Int) -> Unit = {},
    onAdFailedToLoad: (Int, MaxError) -> Unit = { _, _ -> },
    onAdClicked: (Int) -> Unit = {}
) {
    val chunkedIds = adUnitIds.chunked(columns)
    
    Column(
        modifier = modifier
    ) {
        chunkedIds.forEachIndexed { rowIndex, rowIds ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowIds.forEachIndexed { colIndex, adUnitId ->
                    val index = rowIndex * columns + colIndex
                    AppLovinMRECView(
                        adUnitId = adUnitId,
                        modifier = Modifier.weight(1f),
                        backgroundColor = backgroundColor,
                        onAdLoaded = { onAdLoaded(index) },
                        onAdFailedToLoad = { error -> onAdFailedToLoad(index, error) },
                        onAdClicked = { onAdClicked(index) }
                    )
                }
                
                if (rowIds.size < columns) {
                    repeat(columns - rowIds.size) {
                        Spacer(
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}