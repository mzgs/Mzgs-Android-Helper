package com.mzgs.helper

import android.Manifest
import android.app.Activity
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
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.google.android.play.core.review.ReviewManagerFactory
import java.net.URL
import java.util.*

fun p(obj: Any) {
    Log.d("mzgslog", obj.toString())
    Log.d("mzgslog", "-------------------------------------------------------")
}

fun printLine(message: Any? = null) {
    val line = "-------------------------------------------------------"
    Log.d("mzgslog", line)
    message?.toString()?.takeIf { it.isNotEmpty() }?.let { Log.d("mzgslog", it) }
    Log.d("mzgslog", line)
}

object MzgsHelper {
    
    private const val TAG = "MzgsHelper"
    private lateinit var applicationContext: Context
    private lateinit var weakActivity: java.lang.ref.WeakReference<Activity>
    private var lifecycleCallbacksRegistered = false
    private var firstActivityReadyDispatched = false
    private var firstActivityRef: java.lang.ref.WeakReference<Activity>? = null
    private val firstActivityReadyCallbacks = mutableListOf<(Activity) -> Unit>()
    private val firstActivityReadyLock = Any()
    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

        override fun onActivityStarted(activity: Activity) {
            updateActivity(activity)
        }

        override fun onActivityResumed(activity: Activity) {
            updateActivity(activity)
        }

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivityStopped(activity: Activity) {
            if (::weakActivity.isInitialized && weakActivity.get() == activity) {
                updateActivity(null)
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            if (::weakActivity.isInitialized && weakActivity.get() == activity) {
                updateActivity(null)
            }
        }
    }
    var restrictedCountries: List<String> = listOf(
        "UK", "US", "GB", "CN", "MX", "JP", "KR", "AR", "HK", "IN",
        "PK", "TR", "VN", "RU", "SG", "MO", "TW", "PY"
    )

    @Volatile
    private var initialized = false
    var isAllowedCountry = true
    @JvmField
    var debugNoAds = false
    private var debugNoAdsRequested: Boolean? = null
    var IPCountry: String? = null
    private var debugCountryOverride: String? = null
    
    fun init(context: Context) {
        applicationContext = context.applicationContext
        if (!lifecycleCallbacksRegistered) {
            val app = when (context) {
                is Application -> context
                else -> context.applicationContext as? Application
            }
            if (app != null) {
                app.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
                lifecycleCallbacksRegistered = true
            } else {
                Log.w(TAG, "Unable to register lifecycle callbacks; Application context not available")
            }
        }
        initialized = true
        debugNoAdsRequested?.let { applyDebugNoAds(it) }
    }

    fun init(
        context: Context,
        activity: Activity,
        skipAdsInDebug: Boolean = false,
        remoteConfigUrl: String = "https://raw.githubusercontent.com/mzgs/Android-Json-Data/refs/heads/master/nest.json"
    ) {
        init(context)
        updateActivity(activity)

        // Only set debugNoAds if we're in debug mode
        debugNoAdsRequested = skipAdsInDebug
        applyDebugNoAds(skipAdsInDebug)





    }
    
    fun isInitialized(): Boolean = initialized

    fun setDebugNoAds(enabled: Boolean) {
        debugNoAdsRequested = enabled
        if (!::applicationContext.isInitialized) {
            Log.w(TAG, "setDebugNoAds called before init; will be applied after init")
            return
        }
        applyDebugNoAds(enabled)
    }

    fun showUmpConsent(
        forceDebugConsentInEea: Boolean = false,
        onComplete: () -> Unit = {}
    ) {
        val activity = runCatching { getActivity() }.getOrNull()
        if (activity == null) {
            Log.e(TAG, "No current activity available to show UMP consent form")
            onComplete()
            return
        }

        val context = activity.applicationContext
        val consentInformation = UserMessagingPlatform.getConsentInformation(context)
        val paramsBuilder = ConsentRequestParameters.Builder()

        // Enable debug consent flow even outside EEA if requested in debug builds.
        if (isDebug() && forceDebugConsentInEea) {
            val debugSettingsBuilder = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            paramsBuilder.setConsentDebugSettings(debugSettingsBuilder.build())
        }

        val params = paramsBuilder.build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                Log.d(
                    TAG,
                    "UMP consent info updated: status=${consentInformation.consentStatus}, formAvailable=${consentInformation.isConsentFormAvailable}"
                )
                if (
                    consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED &&
                    consentInformation.isConsentFormAvailable
                ) {
                    loadAndShowConsentForm(activity, consentInformation, onComplete)
                } else {
                    Log.d(TAG, "UMP consent form not required (status=${consentInformation.consentStatus})")
                    onComplete()
                }
            },
            { requestError ->
                Log.e(TAG, "Failed to update UMP consent info: ${requestError.message}")
                onComplete()
            }
        )
    }

    private fun loadAndShowConsentForm(
        activity: Activity,
        consentInformation: ConsentInformation,
        onComplete: () -> Unit,
        attempt: Int = 0
    ) {
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is not in a valid state to show consent form")
            onComplete()
            return
        }

        UserMessagingPlatform.loadConsentForm(
            activity,
            { consentForm ->
                if (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED) {
                    consentForm.show(activity) { formError ->
                        if (formError != null) {
                            Log.e(TAG, "UMP consent form error: ${formError.message}")
                        }
                        // Retry once if consent is still required after dismissal.
                        if (
                            consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED &&
                            attempt < 1
                        ) {
                            loadAndShowConsentForm(activity, consentInformation, onComplete, attempt + 1)
                        } else {
                            onComplete()
                        }
                    }
                } else {
                    onComplete()
                }
            },
            { loadError ->
                Log.e(TAG, "Failed to load UMP consent form: ${loadError.message}")
                onComplete()
            }
        )
    }

    private fun applyDebugNoAds(enabled: Boolean) {
        debugNoAds = enabled && isDebug()
        if (debugNoAds) {
            Log.d(TAG, "")
            Log.d(TAG, "|||------------------------------------------------------------|||")
            Log.d(TAG, "|||              DEBUG NO ADS MODE ENABLED                     |||")
            Log.d(TAG, "|||         All ads will be skipped in this session            |||")
            Log.d(TAG, "|||------------------------------------------------------------|||")
            Log.d(TAG, "")
        }
    }
    
    fun getContext(): Context {
        check(::applicationContext.isInitialized) {
            "MzgsHelper.init(context) or MzgsHelper.init(context, activity, ...) must be called before getContext()"
        }
        return applicationContext
    }

    fun getActivity(): Activity {
        if (!::weakActivity.isInitialized) {
            throw IllegalStateException(
                "MzgsHelper.init(context, activity, ...) or MzgsHelper.updateActivity(activity) must be called before getActivity()"
            )
        }
        return weakActivity.get()
            ?: throw IllegalStateException(
                "MzgsHelper.init(context, activity, ...) must be called with a live activity before accessing getActivity()"
            )
    }

    fun updateActivity(activity: Activity?) {
        if (!::weakActivity.isInitialized) {
            if (activity != null) {
                weakActivity = java.lang.ref.WeakReference(activity)
                dispatchFirstActivityReady(activity)
            }
            return
        }

        if (activity != null) {
            weakActivity = java.lang.ref.WeakReference(activity)
            dispatchFirstActivityReady(activity)
        } else {
            weakActivity.clear()
        }
    }

    fun onActivityReady(callback: (Activity) -> Unit) {
        var activityToUse: Activity? = null
        var registered = false

        synchronized(firstActivityReadyLock) {
            if (!firstActivityReadyDispatched) {
                firstActivityReadyCallbacks.add(callback)
                registered = true
            } else {
                activityToUse = firstActivityRef?.get()
                if (activityToUse == null && ::weakActivity.isInitialized) {
                    activityToUse = weakActivity.get()
                }
            }
        }

        if (registered) {
            return
        }

        activityToUse?.let { callback(it) }
            ?: Log.w(TAG, "onActivityReady called after first activity finished; no activity available")
    }

    private fun dispatchFirstActivityReady(activity: Activity) {
        val callbacks = synchronized(firstActivityReadyLock) {
            if (firstActivityReadyDispatched) {
                emptyList()
            } else {
                firstActivityReadyDispatched = true
                firstActivityRef = java.lang.ref.WeakReference(activity)
                if (firstActivityReadyCallbacks.isEmpty()) {
                    emptyList()
                } else {
                    val pending = firstActivityReadyCallbacks.toList()
                    firstActivityReadyCallbacks.clear()
                    pending
                }
            }
        }

        if (callbacks.isEmpty()) {
            return
        }

        callbacks.forEach { callback ->
            try {
                callback(activity)
            } catch (error: Throwable) {
                Log.e(TAG, "onActivityReady callback failed", error)
            }
        }
    }

    fun isDebug(): Boolean {
        val ctx = getContext()
        val flags = ctx.applicationInfo.flags
        val isDebuggable = (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val isTestOnly = (flags and ApplicationInfo.FLAG_TEST_ONLY) != 0
        return isDebuggable || isTestOnly
    }
    
    fun showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(getContext(), message, duration).show()
    }


    fun setDebugCountry(country: String = "TR") {
        if (!isDebug()) {
            Log.d("LibHelper", "setDebugCountry ignored because app is not in debug mode")
            return
        }

        val trimmedCountry = country.trim()
        if (trimmedCountry.isEmpty()) {
            Log.w("LibHelper", "setDebugCountry called with blank country, ignoring request")
            return
        }

        val normalizedCountry = trimmedCountry.uppercase(Locale.ROOT)
        debugCountryOverride = normalizedCountry
        IPCountry = normalizedCountry
        Log.d("LibHelper", "Debug country override set to $normalizedCountry")
    }

    fun getPhoneCountry(): List<String> {
        if (isDebug()) {
            debugCountryOverride?.let { override ->
                Log.d("LibHelper", "Using debug country override for phone country: $override")
                return listOf(override)
            }
        }

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
                IPCountry = "X"
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
        if (debugAllow != null && isDebug()) {
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
        val context = getContext()
        return try {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (!hasPermission) {
                Log.w(TAG, "ACCESS_NETWORK_STATE not granted, skipping check and assuming network available")
                return true
            }

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: SecurityException) {
            Log.w(TAG, "ACCESS_NETWORK_STATE not available, assuming network available: ${e.message}")
            true
        }
    }

    
    fun getAppVersion(): String {
        val context = getContext()
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
        val context = getContext()
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
     * @param context Context to use. Defaults to the one stored in MzgsHelper.
     * @param url Optional remote configuration URL (if provided, will fetch config asynchronously)
     */
    fun init(context: Context = MzgsHelper.getContext(), url: String? = "https://raw.githubusercontent.com/mzgs/Android-Json-Data/refs/heads/master/nest.json") {
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
     * Synchronous init that suspends until remote config is fetched (or fails).
     * Use this when you need to await completion before proceeding.
     */
    suspend fun initSync(  url: String? = "https://raw.githubusercontent.com/mzgs/Android-Json-Data/refs/heads/master/nest.json") {
        applicationContext = MzgsHelper.getContext()
        app = JSONObject()

        url?.takeIf { it.isNotEmpty() }?.let { configUrl ->
            if (MzgsHelper.isNetworkAvailable()) {
                fetchRemoteConfig(configUrl)
            } else {
                Log.w("Remote", "Network not available, using default values")
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
        return applicationContext ?: MzgsHelper.getContext()
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
