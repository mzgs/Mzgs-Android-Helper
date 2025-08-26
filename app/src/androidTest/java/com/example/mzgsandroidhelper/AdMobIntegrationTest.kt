package com.example.mzgsandroidhelper

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mzgs.helper.MzgsHelper
import com.mzgs.helper.admob.AdMobConfig
import com.mzgs.helper.admob.AdMobMediationManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test for AdMob integration
 * Tests the AdMob components and helper functions
 */
@RunWith(AndroidJUnit4::class)
class AdMobIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    private lateinit var adManager: AdMobMediationManager
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        adManager = AdMobMediationManager.getInstance(context)
    }

    @Test
    fun testAdMobInitialization() {
        // Test that AdMob can be initialized
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var initComplete = false
        
        adManager.initialize(
            testDeviceIds = listOf("TEST_DEVICE_ID"),
            onInitComplete = {
                initComplete = true
            }
        )
        
        // Wait for initialization (in real tests you'd use IdlingResource)
        Thread.sleep(2000)
        
        assertTrue("AdMob should be initialized", initComplete)
    }

    @Test
    fun testConsentStatusDisplay() {
        // Check if consent status card is displayed
        composeTestRule.onNodeWithText("Consent Status")
            .assertIsDisplayed()
        
        // Check if reset consent button exists
        composeTestRule.onNodeWithText("Reset Consent")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testInterstitialAdSection() {
        // Check interstitial ad section
        composeTestRule.onNodeWithText("Interstitial Ad")
            .assertIsDisplayed()
        
        // Check load button exists and is enabled
        composeTestRule.onAllNodesWithText("Load")
            .filterToOne(hasAnyAncestor(hasText("Interstitial Ad")))
            .assertIsDisplayed()
            .assertHasClickAction()
        
        // Check show button exists but is disabled initially
        composeTestRule.onAllNodesWithText("Show")
            .filterToOne(hasAnyAncestor(hasText("Interstitial Ad")))
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun testRewardedAdSection() {
        // Check rewarded ad section
        composeTestRule.onNodeWithText("Rewarded Ad")
            .assertIsDisplayed()
        
        // Check coins display
        composeTestRule.onNodeWithText("Coins: 0")
            .assertIsDisplayed()
        
        // Check watch button exists but is disabled initially
        composeTestRule.onNodeWithText("Watch for 10 coins")
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun testBannerAdPresence() {
        // Banner ad should be in the bottom bar
        // Since it's an AndroidView, we can't directly test it
        // but we can verify the scaffold structure exists
        composeTestRule.onNodeWithTag("Scaffold", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun testHelperFunctions() {
        // Test MzgsHelper functions section
        composeTestRule.onNodeWithText("MzgsHelper Functions")
            .assertIsDisplayed()
        
        // Test Show Toast button
        composeTestRule.onNodeWithText("Show Toast")
            .assertIsDisplayed()
            .assertHasClickAction()
        
        // Test Check Network button
        composeTestRule.onNodeWithText("Check Network")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun testNetworkAvailability() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // This will depend on device state, but we can verify it doesn't crash
        val isAvailable = MzgsHelper.isNetworkAvailable(context)
        
        // Should return true or false without throwing
        assertNotNull(isAvailable)
    }

    @Test
    fun testAppVersionRetrieval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        val version = MzgsHelper.getAppVersion(context)
        val versionCode = MzgsHelper.getAppVersionCode(context)
        
        assertNotNull(version)
        assertTrue(versionCode >= 0)
    }

    @Test
    fun testTestAdUnitIds() {
        // Verify test ad unit IDs are properly configured
        assertEquals(
            "ca-app-pub-3940256099942544/6300978111",
            AdMobConfig.TEST_BANNER_AD_UNIT_ID
        )
        assertEquals(
            "ca-app-pub-3940256099942544/1033173712",
            AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID
        )
        assertEquals(
            "ca-app-pub-3940256099942544/5224354917",
            AdMobConfig.TEST_REWARDED_AD_UNIT_ID
        )
        assertEquals(
            "ca-app-pub-3940256099942544/5354046379",
            AdMobConfig.TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID
        )
        assertEquals(
            "ca-app-pub-3940256099942544/2247696110",
            AdMobConfig.TEST_NATIVE_AD_UNIT_ID
        )
    }

    @Test
    fun testAdMobConfigCreation() {
        val config = AdMobConfig(
            bannerAdUnitId = "test_banner",
            interstitialAdUnitId = "test_interstitial",
            rewardedAdUnitId = "test_rewarded",
            enableTestMode = true
        )
        
        // When test mode is enabled, should return test IDs
        assertEquals(
            AdMobConfig.TEST_BANNER_AD_UNIT_ID,
            config.getEffectiveBannerAdUnitId()
        )
        assertEquals(
            AdMobConfig.TEST_INTERSTITIAL_AD_UNIT_ID,
            config.getEffectiveInterstitialAdUnitId()
        )
    }

    @Test
    fun testNativeAdSection() {
        // Scroll to find Native Ad section
        composeTestRule.onNodeWithText("Native Ad")
            .assertExists()
        
        composeTestRule.onNodeWithText("Native ads blend with your app's content")
            .assertExists()
    }

    @Test
    fun testConfigurationInfoSection() {
        // Scroll to bottom to find configuration section
        composeTestRule.onNodeWithText("Test Configuration")
            .assertExists()
        
        composeTestRule.onNodeWithText("All ads are using test ad units")
            .assertExists()
    }

    @Test
    fun testLoadInterstitialButton() {
        // Test clicking load button for interstitial
        composeTestRule.onAllNodesWithText("Load")
            .filterToOne(hasAnyAncestor(hasText("Interstitial Ad")))
            .performClick()
        
        // In a real test, you'd wait for the ad to load
        // For now, just verify the click doesn't crash
    }

    @Test
    fun testResetConsentButton() {
        // Test reset consent functionality
        composeTestRule.onNodeWithText("Reset Consent")
            .performClick()
        
        // Verify consent status changes
        composeTestRule.waitForIdle()
        
        // The status should update (exact text depends on implementation)
        composeTestRule.onNodeWithText("Consent reset")
            .assertExists()
    }
}