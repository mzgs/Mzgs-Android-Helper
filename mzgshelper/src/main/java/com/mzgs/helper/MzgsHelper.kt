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