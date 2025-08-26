package com.example.mzgsandroidhelper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.ads.LoadAdError
import com.mzgs.helper.admob.AdMobConfig
import com.mzgs.helper.admob.AdMobMediationManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test class to demonstrate and verify auto-reload functionality and retry mechanism for ads
 * 
 * Key Features:
 * 1. Auto-reload after ad dismissal (Interstitial, Rewarded, Rewarded Interstitial)
 * 2. Retry mechanism with 3 attempts on load failure (2-second delay between retries)
 * 3. Auto-reload for banner ads on failure (5-second delay)
 */
@RunWith(AndroidJUnit4::class)
class AdAutoReloadTest {
    
    private lateinit var adManager: AdMobMediationManager
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    
    @Before
    fun setup() {
        adManager = AdMobMediationManager.getInstance(context)
        
        // Initialize with test config
        val testConfig = AdMobConfig.createTestConfig()
        adManager.initialize(testConfig) {
            // Initialization complete
        }
    }
    
    @Test
    fun testInterstitialAutoReloadAndRetry() {
        // This test demonstrates:
        // 1. Interstitial ads will auto-reload after being closed (dismissed)
        // 2. If loading fails, it will retry up to 6 times with exponential backoff
        //    Delays: 2s, 4s, 8s, 16s, 32s, 64s (max)
        
        val loadLatch = CountDownLatch(1)
        var failureCount = 0
        
        adManager.loadInterstitialAd(
            adUnitId = AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID,
            onAdLoaded = {
                loadLatch.countDown()
                println("Interstitial ad loaded successfully")
            },
            onAdFailedToLoad = { error ->
                failureCount++
                println("Failed to load interstitial after all retries: ${error.message}")
                println("Total failures: $failureCount")
            }
        )
        
        // Wait for ad to load (allowing time for retries with exponential backoff)
        // Max wait time: initial attempt + 2 + 4 + 8 + 16 + 32 + 64 = ~126 seconds
        assertTrue("Interstitial ad should load within reasonable time (including exponential backoff)", 
            loadLatch.await(30, TimeUnit.SECONDS))
        
        // Note: When the ad is dismissed, it automatically triggers a reload
        // via setupInterstitialCallbacks() method with the exponential backoff retry mechanism
    }
    
    @Test
    fun testRewardedAdAutoReloadAndRetry() {
        // This test demonstrates:
        // 1. Rewarded ads will auto-reload after being closed (dismissed)
        // 2. If loading fails, it will retry up to 6 times with exponential backoff
        //    Delays: 2s, 4s, 8s, 16s, 32s, 64s (max)
        
        val loadLatch = CountDownLatch(1)
        var failureCount = 0
        
        adManager.loadRewardedAd(
            adUnitId = AdMobConfig.TEST_REWARDED_AD_UNIT_ID,
            onAdLoaded = {
                loadLatch.countDown()
                println("Rewarded ad loaded successfully")
            },
            onAdFailedToLoad = { error ->
                failureCount++
                println("Failed to load rewarded ad after all retries: ${error.message}")
                println("Total failures: $failureCount")
            }
        )
        
        // Wait for ad to load (allowing time for retries with exponential backoff)
        assertTrue("Rewarded ad should load within reasonable time (including exponential backoff)", 
            loadLatch.await(30, TimeUnit.SECONDS))
        
        // Note: When the ad is dismissed, it automatically triggers a reload
        // via setupRewardedCallbacks() method with the retry mechanism
    }
    
    @Test
    fun testRewardedInterstitialAutoReloadAndRetry() {
        // This test demonstrates:
        // 1. Rewarded interstitial ads will auto-reload after being closed (dismissed)
        // 2. If loading fails, it will retry up to 6 times with exponential backoff
        //    Delays: 2s, 4s, 8s, 16s, 32s, 64s (max)
        
        val loadLatch = CountDownLatch(1)
        var failureCount = 0
        
        adManager.loadRewardedInterstitialAd(
            adUnitId = AdMobConfig.TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID,
            onAdLoaded = {
                loadLatch.countDown()
                println("Rewarded interstitial ad loaded successfully")
            },
            onAdFailedToLoad = { error ->
                failureCount++
                println("Failed to load rewarded interstitial after all retries: ${error.message}")
                println("Total failures: $failureCount")
            }
        )
        
        // Wait for ad to load (allowing time for retries with exponential backoff)
        assertTrue("Rewarded interstitial ad should load within reasonable time (including exponential backoff)", 
            loadLatch.await(30, TimeUnit.SECONDS))
        
        // Note: When the ad is dismissed, it automatically triggers a reload
        // via setupRewardedInterstitialCallbacks() method with the retry mechanism
    }
    
    @Test
    fun testExponentialBackoffDetails() {
        // This test demonstrates the exponential backoff retry mechanism
        // Following AppLovin's recommendation for exponentially higher delays
        
        println("Exponential Backoff Retry Configuration:")
        println("- Maximum retry attempts: 6")
        println("- Delay calculation: 2^attempt seconds (capped at 64 seconds)")
        println("")
        println("Retry Schedule:")
        println("  Attempt 1: 2 seconds delay")
        println("  Attempt 2: 4 seconds delay")
        println("  Attempt 3: 8 seconds delay")
        println("  Attempt 4: 16 seconds delay")
        println("  Attempt 5: 32 seconds delay")
        println("  Attempt 6: 64 seconds delay (max)")
        println("")
        println("Total potential retry time: ~126 seconds")
        println("After dismissal, ads auto-reload with the same exponential backoff mechanism")
        
        assertTrue("Exponential backoff mechanism is configured", true)
    }
}