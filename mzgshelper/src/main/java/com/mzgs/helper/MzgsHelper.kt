package com.mzgs.helper

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity

import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.google.android.ump.ConsentDebugSettings
import com.mzgs.helper.admob.AdMobConfig
import com.mzgs.helper.admob.AdMobMediationManager
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import com.mzgs.helper.applovin.AppLovinConfig
import com.mzgs.helper.applovin.AppLovinMediationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

fun p(obj: Any) {
    Log.d("mzgs", obj.toString())
    Log.d("mzgs", "-------------------------------------------------------")
}

object MzgsHelper {
    
    private const val TAG = "MzgsHelper"
    
    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
    }

    fun initSplashWithAdmobShow(
        activity: ComponentActivity, 
        admobConfig: AdMobConfig,
        onFinish: (() -> Unit)? = null
    ){

// Initialize Simple Splash Screen with progress
        val splash = SimpleSplashHelper.Builder(activity)
            .setDuration(Remote.getLong("splash_time", 10000))
            .setRotateLogo(true)
            .showProgress(true)
            .onComplete {
                Log.d("MainActivity", "Splash screen completed")

                // Show interstitial ad if ready
                if (AdMobMediationManager.isInterstitialReady()) {
                    Log.d("MainActivity", "Showing interstitial ad after splash")
                    AdMobMediationManager.showInterstitialAd(
                        onAdDismissed = {
                            Log.d("MainActivity", "Interstitial ad dismissed")
                            onFinish?.invoke()
                        }
                    )
                } else {
                    Log.d("MainActivity", "Interstitial ad not ready after splash")
                    FirebaseAnalyticsManager.logEvent("onstart_ad_not_ready")
                    // Call onFinish when ad is not loaded
                    onFinish?.invoke()
                }
            }
            .build()

        // Start splash screen normally
        splash.pause()
        splash.show()


        Ads.initAdMob(
            config = admobConfig,
            onInitComplete = {
                Log.d("MainActivity", "AdMob initialized")

                // Request consent info update
                // Check if app is in debug mode for consent testing
                val isDebugMode = MzgsHelper.isDebugMode(activity)
                AdMobMediationManager.requestConsentInfoUpdate(
                    underAgeOfConsent = false,
                    debugGeography = if (isDebugMode && admobConfig.debugRequireConsentAlways)
                        ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA else null,
                    testDeviceHashedId = if (isDebugMode && admobConfig.debugRequireConsentAlways)
                        "TEST-DEVICE-HASHED-ID" else null,
                    onConsentInfoUpdateSuccess = {
                        Log.d("MainActivity", "Consent info updated")

                        // Check if we can show ads (either personalized or non-personalized)
                        val canShowPersonalized = AdMobMediationManager.canShowAds()
                        val canShowNonPersonalized =
                            AdMobMediationManager.canShowNonPersonalizedAds()

                        Log.d(
                            "MainActivity",
                            "Can show personalized: $canShowPersonalized, non-personalized: $canShowNonPersonalized"
                        )

                        // Check if consent form needs to be shown
                        if (AdMobMediationManager.isConsentFormAvailable()) {
                            Log.d("MainActivity", "Showing consent form")
                            AdMobMediationManager.showConsentForm(
                                activity = activity,
                                onConsentFormDismissed = { formError ->
                                    if (formError != null) {
                                        Log.e(
                                            "MainActivity",
                                            "Consent form error: ${formError.message}"
                                        )
                                    }
                                    Log.d("MainActivity", "Consent form dismissed")

                                    // After consent form is dismissed, load interstitial ad
                                    // We can load ads even if only non-personalized ads are allowed
                                    if (AdMobMediationManager.canShowAds() || AdMobMediationManager.canShowNonPersonalizedAds()) {
                                        Log.d(
                                            "MainActivity",
                                            "Loading interstitial ad after consent"
                                        )
                                        AdMobMediationManager.loadInterstitialAd()
                                    }

                                    // Resume splash screen
                                    splash.resume()
                                }
                            )
                        } else {
                            // No consent form needed
                            // Load ads if we can show any type (personalized or non-personalized)
                            if (canShowPersonalized || canShowNonPersonalized) {
                                Log.d(
                                    "MainActivity",
                                    "No consent form needed, loading interstitial ad"
                                )
                                AdMobMediationManager.loadInterstitialAd()
                            } else {
                                Log.d("MainActivity", "Cannot show any ads yet")
                            }

                            // Resume splash screen
                            splash.resume()
                        }
                    },
                    onConsentInfoUpdateFailure = { error ->
                        Log.e("MainActivity", "Failed to update consent info: $error")

                        // Even if consent update fails, try to load ad if non-personalized ads are allowed
                        if (AdMobMediationManager.canShowNonPersonalizedAds()) {
                            Log.d(
                                "MainActivity",
                                "Loading non-personalized ad after consent failure"
                            )
                            AdMobMediationManager.loadInterstitialAd()
                        }
                        splash.resume()
                    }
                )
            })

    }

    
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    
    fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {

            "Unknown"
        }
    }
    
    fun getAppVersionCode(context: Context): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {

            -1L
        }
    }
    
    /**
     * Checks if the application is running in debug mode.
     * 
     * @param context The application context
     * @return true if the app is debuggable (debug build), false otherwise (release build)
     * 
     * This is useful for:
     * - Showing debug information only in development
     * - Disabling ads during development
     * - Enabling verbose logging in debug builds
     * - Any debug-only functionality
     * 
     * Note: This checks the FLAG_DEBUGGABLE flag which is automatically set by Android
     * based on the build type (debug vs release) in your build.gradle
     */
    fun isDebugMode(context: Context): Boolean {
        return try {
            val applicationInfo = context.applicationInfo
            (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking debug mode", e)
            false
        }
    }
    

}



/**
 * Remote Configuration Helper
 * Manages remote configuration settings fetched from a server
 */
object Remote {
    private var app: JSONObject? = null
    private var applicationContext: Context? = null
    
    // Default restricted countries list


    /**
     * Initialize with application context and optionally fetch remote config
     * Call this method in your Application class onCreate or Activity onCreate
     *
     * @param context The application context
     * @param url Optional remote configuration URL (if provided, will fetch config asynchronously)
     */
    fun init(context: Context, url: String? = "https://raw.githubusercontent.com/mzgs/Android-Json-Data/refs/heads/master/nest.json") {
        applicationContext = context.applicationContext
        
        // Initialize with empty config immediately (will use default values from getter functions)
        app = JSONObject()
        
        // Try to fetch remote config asynchronously
        url?.takeIf { it.isNotEmpty() }?.let { configUrl ->
            CoroutineScope(Dispatchers.IO).launch { 
                // Check network connectivity before attempting to fetch
                if (isNetworkAvailable(context)) {
                    fetchRemoteConfig(configUrl)
                } else {
                    Log.w("Remote", "Network not available, using default values")
                }
            }
        }
    }
    
    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } catch (e: Exception) {
            Log.e("Remote", "Error checking network availability", e)
            false
        }
    }
    
    /**
     * Fetch remote configuration from URL
     * This is now a private method called internally by init
     */
    private suspend fun fetchRemoteConfig(url: String) {
        try {
            val response = withContext(Dispatchers.IO) {
                makeRequest(url, 10000)
            }

            response?.let { data ->
                val jsonObj = jsonDecode(data)
                jsonObj?.let { json ->
                    app = json.optJSONObject(getPackageName())

                    if (app == null || app?.length() == 0) {
                        app = JSONObject()
                        Log.i("Remote", "No config found for package, using defaults")
                    } else {
                        Log.i("Remote", "Successfully fetched remote config")
                    }
                }
            } ?: run {
                Log.w("Remote", "Empty response from remote config, using defaults")
            }
        } catch (e: Exception) {
            Log.w("Remote", "Failed to fetch remote config, using defaults: ${e.message}")
            // Keep the already initialized empty JSONObject, which will use defaults
        }
    }


    /**
     * Get boolean value from remote config
     */
    fun getBool(key: String, default: Boolean = false): Boolean {
        return if (app?.has(key) == true) app!!.optBoolean(key, default) else default
    }

    /**
     * Get integer value from remote config
     */
    fun getInt(key: String, default: Int = 0): Int {
        return if (app?.has(key) == true) app!!.optInt(key, default) else default
    }

    /**
     * Get string value from remote config
     */
    fun getString(key: String, default: String = ""): String {
        return if (app?.has(key) == true) app!!.optString(key, default) else default
    }

    /**
     * Get double value from remote config
     */
    fun getDouble(key: String, default: Double = 0.0): Double {
        return if (app?.has(key) == true) app!!.optDouble(key, default) else default
    }

    /**
     * Get long value from remote config
     */
    fun getLong(key: String, default: Long = 0L): Long {
        return if (app?.has(key) == true) app!!.optLong(key, default) else default
    }

    /**
     * Get string array from remote config
     */
    fun getStringArray(key: String, default: List<String> = listOf()): List<String> {
        if (app?.has(key) == true) {
            val jsonArray = app!!.optJSONArray(key)
            if (jsonArray != null) {
                val result = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    jsonArray.optString(i)?.let { result.add(it) }
                }
                return result
            }
        }
        return default
    }

    /**
     * Get integer array from remote config
     */
    fun getIntArray(key: String, default: List<Int> = listOf()): List<Int> {
        if (app?.has(key) == true) {
            val jsonArray = app!!.optJSONArray(key)
            if (jsonArray != null) {
                val result = mutableListOf<Int>()
                for (i in 0 until jsonArray.length()) {
                    result.add(jsonArray.optInt(i))
                }
                return result
            }
        }
        return default
    }



    /**
     * Set a value in the remote config
     */
    fun setValue(key: String, value: Any) {
        try {
            app?.put(key, value)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    // Helper functions that would need to be implemented elsewhere or added here
    private suspend fun makeRequest(url: String, timeoutMs: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.connect()
                
                connection.getInputStream().bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e("Remote", "Request failed: ${e.message}")
                null
            }
        }
    }

    private fun jsonDecode(data: String): JSONObject? {
        return try {
            JSONObject(data)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getPackageName(): String {
        return applicationContext?.packageName ?: ""
    }



    /**
     * Gets the application context
     * @return The application context, or null if not initialized
     */
    fun getApplicationContext(): Context? {
        return applicationContext
    }
}

