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
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.mzgs.helper.admob.AdMobConfig
import com.mzgs.helper.admob.AdMobMediationManager
import com.mzgs.helper.applovin.AppLovinConfig
import com.mzgs.helper.applovin.AppLovinMediationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

object MzgsHelper {
    
    private const val TAG = "MzgsHelper"
    
    enum class AdNetwork {
        ADMOB,
        APPLOVIN_MAX
    }
    
    fun showToast(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, duration).show()
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
    
    fun initializeAdMob(
        context: Context,
        config: AdMobConfig,
        onInitComplete: () -> Unit = {}
    ) {
        Log.d(TAG, "Initializing AdMob mediation")
        val manager = AdMobMediationManager.getInstance(context)
        manager.initialize(config, onInitComplete)
    }
    
    fun initializeAppLovinMAX(
        context: Context,
        config: AppLovinConfig,
        onInitComplete: () -> Unit = {}
    ) {
        Log.d(TAG, "Initializing AppLovin MAX mediation")
        val manager = AppLovinMediationManager.getInstance(context)
        manager.initialize(config, onInitComplete)
    }
    
    fun getAdMobManager(context: Context): AdMobMediationManager {
        return AdMobMediationManager.getInstance(context)
    }
    
    fun getAppLovinManager(context: Context): AppLovinMediationManager {
        return AppLovinMediationManager.getInstance(context)
    }
    
    fun initializeAdNetwork(
        context: Context,
        network: AdNetwork,
        adMobConfig: AdMobConfig? = null,
        appLovinConfig: AppLovinConfig? = null,
        onInitComplete: () -> Unit = {}
    ) {
        when (network) {
            AdNetwork.ADMOB -> {
                if (adMobConfig == null) {
                    Log.e(TAG, "AdMob config is required for AdMob initialization")
                    return
                }
                initializeAdMob(context, adMobConfig, onInitComplete)
            }
            AdNetwork.APPLOVIN_MAX -> {
                if (appLovinConfig == null) {
                    Log.e(TAG, "AppLovin config is required for AppLovin MAX initialization")
                    return
                }
                initializeAppLovinMAX(context, appLovinConfig, onInitComplete)
            }
        }
    }
    
    fun initializeBothNetworks(
        context: Context,
        adMobConfig: AdMobConfig,
        appLovinConfig: AppLovinConfig,
        onBothInitComplete: () -> Unit = {}
    ) {
        var adMobInitialized = false
        var appLovinInitialized = false
        
        fun checkBothInitialized() {
            if (adMobInitialized && appLovinInitialized) {
                Log.d(TAG, "Both ad networks initialized successfully")
                onBothInitComplete()
            }
        }
        
        initializeAdMob(context, adMobConfig) {
            adMobInitialized = true
            checkBothInitialized()
        }
        
        initializeAppLovinMAX(context, appLovinConfig) {
            appLovinInitialized = true
            checkBothInitialized()
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
     * Initialize with application context
     * Call this method in your Application class onCreate
     *
     * @param context The application context
     */
    fun init(context: Context) {
        applicationContext = context.applicationContext
    }

    /**
     * Initialize remote configuration by fetching from URL
     *
     * @param url The remote configuration URL
     */
    suspend fun initRemote(url: String = remoteConfigUrl) {
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
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    /**
     * Initialize remote configuration at app startup
     * Call this method from your Application class or main activity
     * This is a suspending function that will wait until remote config is loaded
     */
    suspend fun initializeAtAppStart() {
        initRemote()
    }

    // Helper functions that would need to be implemented elsewhere or added here
    private suspend fun makeRequest(url: String, timeoutMs: Int): String? {
        // Implementation would depend on your HTTP client
        // Example using simple URL connection:
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = timeoutMs
                connection.readTimeout = timeoutMs
                connection.connect()

                connection.getInputStream().bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                e.printStackTrace()
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

    // Remote config URL set to "1" as requested
    private val remoteConfigUrl: String = "https://raw.githubusercontent.com/mzgs/Android-Json-Data/refs/heads/master/nest.json"

    /**
     * Gets the application context
     * @return The application context, or null if not initialized
     */
    fun getApplicationContext(): Context? {
        return applicationContext
    }
}

