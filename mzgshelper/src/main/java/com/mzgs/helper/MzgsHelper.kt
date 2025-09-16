package com.mzgs.helper

import android.Manifest
import android.R
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log

import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.google.android.ump.ConsentDebugSettings
import com.mzgs.helper.admob.AdMobConfig
import com.mzgs.helper.admob.AdMobManager
import com.mzgs.helper.analytics.FirebaseAnalyticsManager
import com.mzgs.helper.applovin.AppLovinConfig
import com.mzgs.helper.applovin.AppLovinMediationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.play.core.review.ReviewManagerFactory
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

fun p(obj: Any) {
    Log.d("mzgslog", obj.toString())
    Log.d("mzgslog", "-------------------------------------------------------")
}

object MzgsHelper {
    
    private const val TAG = "MzgsHelper"
    private var weakContext: java.lang.ref.WeakReference<Context>? = null
    var restrictedCountries: List<String> = listOf(
        "UK", "US", "GB", "CN", "MX", "JP", "KR", "AR", "HK", "IN",
        "PK", "TR", "VN", "RU", "SG", "MO", "TW", "PY"
    )

    var isAllowedCountry = true
    var isDebug = false
    var debugNoAds = false
    var IPCountry: String? = null
    
    fun init(context: Context, debugMode: Boolean, skipAdsInDebug: Boolean = false) {
        weakContext = java.lang.ref.WeakReference(context.applicationContext)
        isDebug = debugMode
        
        // Only set debugNoAds if we're in debug mode
        debugNoAds = skipAdsInDebug && debugMode
        
        if (debugNoAds) {
            Log.d(TAG, "")
            Log.d(TAG, "|||------------------------------------------------------------|||")
            Log.d(TAG, "|||              DEBUG NO ADS MODE ENABLED                     |||")
            Log.d(TAG, "|||         All ads will be skipped in this session            |||")
            Log.d(TAG, "|||------------------------------------------------------------|||")
            Log.d(TAG, "")
        }
    }
    
    private fun getContext(): Context? {
        return weakContext?.get()
    }
    
    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        getContext()?.let {
            Toast.makeText(it, message, duration).show()
        }
    }



    fun initSplashWithInterstitialShow(
        activity: android.app.Activity, 
        admobConfig: AdMobConfig,
        appLovinConfig: AppLovinConfig,
        defaultSplashTime: Long = 10000,
        loadApplovinMaxInterstitial: Boolean = true,
        onSplashCompleteAdClosed: (() -> Unit)? = null,
        onCompleteWithAdsReady: (() -> Unit)? = null
    ) {
        // Early return if ads are disabled in debug mode
        if (debugNoAds) {
            Log.d(TAG, "Skipping ads initialization and display (debugNoAds mode)")
            
            // Just show splash and finish without any ad initialization
            val splash = SimpleSplashHelper.Builder(activity)
                .setDuration(Remote.getLong("splash_time", defaultSplashTime))
                .setRotateLogo(true)
                .showProgress(true)
                .onComplete {
                    Log.d(TAG, "Splash completed (no ads in debug mode)")
                    onSplashCompleteAdClosed?.invoke()
                    onCompleteWithAdsReady?.invoke()
                }
                .build()
            splash.show()
            return
        }

        // State tracking variables
        var isSplashFinished = false
        var isApplovinReady = false
        var isOnSplashCompleteAdClosedCalled = false
        var hasCalledCompleteWithAdsReady = false
        
        fun checkAndCallCompleteWithAdsReady() {
            if (isSplashFinished && isApplovinReady && isOnSplashCompleteAdClosedCalled && !hasCalledCompleteWithAdsReady) {
                hasCalledCompleteWithAdsReady = true
                onCompleteWithAdsReady?.invoke()
            }
        }
        
        // Helper to initialize AppLovin (avoid duplication)
        fun initializeAppLovin() {
            Ads.initAppLovinMax(appLovinConfig) {
                Log.d(TAG, "AppLovin MAX initialized")
                if (loadApplovinMaxInterstitial) {
                    AppLovinMediationManager.loadInterstitialAd()
                }

                // Mark AppLovin as ready
                isApplovinReady = true
                checkAndCallCompleteWithAdsReady()
            }
        }

        // Initialize splash screen with progress
        val splash = SimpleSplashHelper.Builder(activity)
            .setDuration(Remote.getLong("splash_time", defaultSplashTime))
            .setRotateLogo(true)
            .showProgress(true)
            .onComplete {
                Log.d(TAG, "Splash screen completed")
                
                // Mark splash as finished
                isSplashFinished = true
                checkAndCallCompleteWithAdsReady()

                // Show interstitial ad
                val adResult = Ads.showInterstitialWithResult(
                    onAdClosed = {
                        Log.d(TAG, "Interstitial ad dismissed")
                        onSplashCompleteAdClosed?.invoke()
                        // Mark onSplashCompleteAdClosed as called
                        isOnSplashCompleteAdClosedCalled = true
                        checkAndCallCompleteWithAdsReady()
                    }
                )
                
                // Log Firebase event for splash ad showing
                if (adResult.success) {
                    FirebaseAnalyticsManager.logEvent("splash_ad_shown", Bundle().apply {
                        putString("ad_type", "interstitial")
                        putString("placement", "splash_screen")
                        putString("ad_network", adResult.network ?: "unknown")
                        putBoolean("success", true)
                    })
                    Log.d(TAG, "Firebase event logged: splash_ad_shown with network: ${adResult.network}")
                } else {
                    FirebaseAnalyticsManager.logEvent("splash_ad_failed", Bundle().apply {
                        putString("ad_type", "interstitial")
                        putString("placement", "splash_screen")
                        putString("reason", "no_ad_available")
                        putBoolean("success", false)
                    })
                    Log.d(TAG, "Firebase event logged: splash_ad_failed")
                }
            }
            .build()

        // Start splash screen paused (will resume after consent)
        splash.pause()
        splash.show()

        // Initialize AdMob with consent handling
        Ads.initAdMob(
            config = admobConfig,
            onInitComplete = {
                Log.d(TAG, "AdMob initialized")

                // Request consent info update
                AdMobManager.requestConsentInfoUpdate(
                    underAgeOfConsent = false,
                    debugGeography = if (isDebug && admobConfig.debugRequireConsentAlways)
                        ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA else null,
                    testDeviceHashedId = if (isDebug && admobConfig.debugRequireConsentAlways)
                        "TEST-DEVICE-HASHED-ID" else null,
                    onConsentInfoUpdateSuccess = {
                        Log.d(TAG, "Consent info updated")
                        
                        // Check if consent form needs to be shown
                        if (AdMobManager.isConsentFormAvailable()) {
                            Log.d(TAG, "Showing consent form")
                            AdMobManager.showConsentForm(
                                activity = activity,
                                onConsentFormDismissed = { formError ->
                                    if (formError != null) {
                                        Log.e(TAG, "Consent form error: ${formError.message}")
                                    }
                                    Log.d(TAG, "Consent form dismissed")

                                    // Load interstitial (method checks consent internally)
                                    AdMobManager.loadInterstitialAd()

                                    initializeAppLovin()

                                    // Resume splash screen
                                    splash.resume()
                                }
                            )
                        } else {
                            // No consent form needed - load ad (method checks consent internally)
                            AdMobManager.loadInterstitialAd()
                            
                            // Initialize AppLovin when no consent form needed
                            initializeAppLovin()

                            // Resume splash screen
                            splash.resume()
                        }
                    },
                    onConsentInfoUpdateFailure = { error ->
                        Log.e(TAG, "Failed to update consent info: $error")

                        // Try loading ad on consent failure (method checks consent internally)
                        AdMobManager.loadInterstitialAd()
                        
                        // Initialize AppLovin even on consent failure
                        initializeAppLovin()
                        
                        splash.resume()
                    }
                )
            })

    }

    fun getPhoneCountry(): List<String> {
        val countries = mutableListOf<String>()
        val context = getContext()
        try {
            val telephonyManager = context?.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            telephonyManager?.let {
                // Add SIM country code if available
                val simCountry = it.simCountryIso?.uppercase(Locale.ROOT)
                if (!simCountry.isNullOrEmpty()) {
                    countries.add(simCountry)
                    Log.d("LibHelper", "SIM country: $simCountry")
                }

                // Add network country code if available
                val networkCountry = it.networkCountryIso?.uppercase(Locale.ROOT)
                if (!networkCountry.isNullOrEmpty() && !countries.contains(networkCountry)) {
                    countries.add(networkCountry)
                    Log.d("LibHelper", "Network country: $networkCountry")
                }
            }

            // Add system locale country (from phone settings)
            val localeCountry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context?.resources?.configuration?.locales[0]?.country
            } else {
                @Suppress("DEPRECATION")
                context?.resources?.configuration?.locale?.country
            }
            if (!localeCountry.isNullOrEmpty()) {
                val upperLocale = localeCountry.uppercase(Locale.ROOT)
                if (!countries.contains(upperLocale)) {
                    countries.add(upperLocale)
                    Log.d("LibHelper", "Locale country from settings: $upperLocale")
                }
            }
        } catch (e: Exception) {
            Log.e("LibHelper", "Error getting phone countries", e)
        }

        Log.d("LibHelper", "All detected countries: ${countries.joinToString(", ")}")
        return countries
    }

    /**
     * Set country using IP geolocation (async, non-blocking)
     */
    fun setIPCountry() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://ipinfo.io/json")
                val connection = url.openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 9000

                val response = connection.getInputStream().bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                IPCountry = json.optString("country")?.uppercase(Locale.ROOT) ?: "US"
                Log.d("LibHelper", "IP country set to: $IPCountry")
            } catch (e: Exception) {
                Log.e("LibHelper", "Error getting country from IP, defaulting to US", e)
                IPCountry = "US"
            }
        }
    }

    /**
     * Check if the current country is allowed based on phone countries and IP country
     * @param debugAllow Optional parameter to override the restriction check in debug mode
     *                   - null: normal behavior (default)
     *                   - true: force allow in debug mode
     *                   - false: force restrict in debug mode
     */
    fun setIsAllowedCountry(debugAllow: Boolean? = null) {
        // Handle debug override if provided and we're in debug mode
        if (debugAllow != null && isDebug) {
            isAllowedCountry = debugAllow
            Log.d("LibHelper", "Debug mode with debugAllow=$debugAllow, setting isAllowedCountry to $debugAllow")
            return
        }

        // Check if only free music mode is enabled
        if (Remote.getBool("only_free_music", false)) {
            val allowedCountries = Remote.getStringArray("allowed_countries", emptyList())
            
            // Get all phone countries
            val phoneCountries = getPhoneCountry()
            
            // Check if any phone country is in the allowed list
            val isInAllowedCountry = phoneCountries.any { country ->
                allowedCountries.contains(country)
            } || (IPCountry?.let { allowedCountries.contains(it) } ?: false)
            
            MzgsHelper.isAllowedCountry = isInAllowedCountry
            Log.d("LibHelper", "Only free music mode enabled, isAllowedCountry: $isInAllowedCountry")
            return
        }
        
        // Get all phone countries
        val phoneCountries = getPhoneCountry()
        
        // Check if any phone country is in the restricted list
        val phoneCountryRestricted = phoneCountries.any { country ->
            restrictedCountries.contains(country)
        }
        
        // Check if IP country is in the restricted list
        val ipCountryRestricted = IPCountry?.let { country ->
            restrictedCountries.contains(country)
        } ?: false
        
        // Set isAllowedCountry to false if any country is restricted
        isAllowedCountry = !(phoneCountryRestricted || ipCountryRestricted)
        
        Log.d("LibHelper", "Phone countries: $phoneCountries")
        Log.d("LibHelper", "IP country: $IPCountry")
        Log.d("LibHelper", "Is allowed country: $isAllowedCountry")
    }

    /**
     * Set restricted countries from remote configuration
     */
    fun setRestrictedCountriesFromRemoteConfig() {
        try {
            val remoteCountries = Remote.getStringArray("restricted_countries", restrictedCountries)
            restrictedCountries = remoteCountries.map { it.uppercase(Locale.ROOT) }
            Log.d("LibHelper", "Restricted countries set from remote config: $restrictedCountries")
        } catch (e: Exception) {
            Log.e("LibHelper", "Error setting restricted countries from remote config, keeping default list", e)
        }
    }

    
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isNetworkAvailable(): Boolean {
        val context = getContext() ?: return false
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    
    fun getAppVersion(): String {
        val context = getContext() ?: return "Unknown"
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
    
    fun getAppVersionCode(): Long {
        val context = getContext() ?: return -1L
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

    fun isSafe(): Boolean {
        return try {

            MzgsHelper.getAppVersion().toDouble() <= Remote.getDouble("safe", 0.9)
        } catch (e: Exception) {
            e.printStackTrace()
            true // Default to safe if there's an error
        }
    }
    
    /**
     * Shows the native in-app rating dialog using Google Play In-App Review API
     * 
     * @param activity The activity from which to show the review dialog
     * @param rateName The name used for tracking rating events, defaults to "rate"
     * @param showAtCounts Optional array of counts at which to show the rating dialog
     */
    fun showInappRate(activity: android.app.Activity, rateName: String = "rate", showAtCounts: List<Int> = emptyList()) {
        if (showAtCounts.isNotEmpty()) {
            val ac = ActionCounter.increaseGet(rateName)
            if (!showAtCounts.contains(ac)) {
                return
            }
        }
        
        try {
            val manager = ReviewManagerFactory.create(activity)
            val request = manager.requestReviewFlow()
            
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo = task.result
                    
                    val flow = manager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener { _ ->
                        Log.d("LibHelper", "In-app review flow completed")
                    }
                } else {
                    Log.e("LibHelper", "Error requesting in-app review: ${task.exception}")
                }
            }
            
            ActionCounter.increase("${rateName}_inapp_rate_showed")
        } catch (e: Exception) {
            Log.e("LibHelper", "Error showing in-app review dialog", e)
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
                if (MzgsHelper.isNetworkAvailable()) {
                    fetchRemoteConfig(configUrl)
                } else {
                    Log.w("Remote", "Network not available, using default values")
                }
            }
        }
    }




    /**
     * Fetch remote configuration from URL
     * This is now a private method called internally by init
     */
    private suspend fun fetchRemoteConfig(url: String) {
        try {
            val response = withContext(Dispatchers.IO) {
                makeRequest(url, 30000)
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




object Pref {
    private val sharedPreferences by lazy {
        Remote.getApplicationContext()?.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
    }

    fun <T> get(key: String, defaultValue: T): T {
        val prefs = sharedPreferences ?: return defaultValue

        @Suppress("UNCHECKED_CAST")
        return when (defaultValue) {
            is String -> prefs.getString(key, defaultValue) as T
            is Int -> prefs.getInt(key, defaultValue) as T
            is Boolean -> prefs.getBoolean(key, defaultValue) as T
            is Float -> prefs.getFloat(key, defaultValue) as T
            is Long -> prefs.getLong(key, defaultValue) as T
            is Double -> getDouble(key, defaultValue) as T
            else -> defaultValue
        }
    }

    fun <T> set(key: String, value: T?) {
        val prefs = sharedPreferences ?: return
        val editor = prefs.edit()

        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Long -> editor.putLong(key, value)
            is Double -> editor.putString(key, value.toString())
            else -> return
        }

        editor.apply()
    }

    fun remove(key: String) {
        sharedPreferences?.edit()?.remove(key)?.apply()
    }

    fun exists(key: String): Boolean {
        return sharedPreferences?.contains(key) ?: false
    }

    fun clearAll() {
        sharedPreferences?.edit()?.clear()?.apply()
    }

    fun getString(key: String, defaultValue: String): String {
        return sharedPreferences?.getString(key, defaultValue) ?: defaultValue
    }

    fun getBool(key: String, defaultValue: Boolean): Boolean {
        return if (exists(key)) sharedPreferences!!.getBoolean(key, false) else defaultValue
    }

    fun getFloat(key: String, defaultValue: Float): Float {
        return if (exists(key)) sharedPreferences!!.getFloat(key, 0f) else defaultValue
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return if (exists(key)) sharedPreferences!!.getInt(key, 0) else defaultValue
    }

    fun getDouble(key: String, defaultValue: Double): Double {
        return if (exists(key)) {
            try {
                sharedPreferences!!.getString(key, null)?.toDouble() ?: defaultValue
            } catch (e: NumberFormatException) {
                defaultValue
            }
        } else {
            defaultValue
        }
    }
}

object ActionCounter {
    private val analyticData = mutableMapOf<String, Int>()

    fun initAnalyticData() {
        keys().forEach { analyticData[it] = get(it) }
    }

    fun increase(key: String, parameters: Map<String, Any> = emptyMap()) {
        val newValue = get(key) + 1
        Pref.set(key, newValue)
        analyticData[key] = newValue
        val keyList = keys().toMutableList()
        if (!keyList.contains(key)) {
            keyList.add(key)
            saveKeys(keyList)
        }
    }

    fun increaseGet(key: String): Int {
        increase(key)
        return get(key)
    }

    fun keys(): List<String> {
        val keyListJson = Pref.getString("keyList", "")
        if (keyListJson.isEmpty()) return emptyList()

        return try {
            val jsonArray = JSONObject("{\"keys\":$keyListJson}").getJSONArray("keys")
            val result = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                result.add(jsonArray.getString(i))
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun saveKeys(keyList: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            keyList.forEach { jsonArray.put(it) }
            Pref.set("keyList", jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun get(key: String): Int {
        return Pref.getInt(key, 0)
    }
}

