# Firebase Analytics Setup Guide

## Important Configuration Required

To use Firebase Analytics in your Android app, you must add the `google-services.json` file from your Firebase project.

### Steps to Configure:

1. **Create a Firebase Project** (if you haven't already):
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Click "Create a project" or select an existing project
   - Follow the setup wizard

2. **Add Your Android App to Firebase**:
   - In the Firebase Console, click "Add app" and select Android
   - Enter your app's package name (must match your app's package name exactly)
   - Register the app

3. **Download google-services.json**:
   - After registering your app, download the `google-services.json` file
   - This file contains your project's Firebase configuration

4. **Add google-services.json to Your Project**:
   - Place the `google-services.json` file in:
     - **For the main app**: `/app/google-services.json`
     - **For the library module**: `/mzgshelper/google-services.json` (if needed)

5. **Verify Setup**:
   - Build your project
   - Run the app
   - Check Firebase Console to see if analytics events are being received

## Firebase Analytics Events Logged

The MzgsHelper library now automatically logs the following ad events:

### Event: `ad_load_event`
Logged when an ad loads successfully or fails to load.

**Parameters**:
- `ad_type`: Type of ad (banner, native, app_open, etc.)
- `ad_unit_id`: The ad unit identifier
- `ad_network`: Network serving the ad (admob, applovin)
- `status`: "success" or "failed"
- `error_message`: Error description (only for failures)
- `error_code`: Error code (only for failures)

### Event: `ad_impression`
Logged when an ad is displayed to the user.

**Parameters**:
- `ad_type`: Type of ad
- `ad_unit_id`: The ad unit identifier
- `ad_network`: Network serving the ad

### Event: `ad_click`
Logged when a user clicks on an ad.

**Parameters**:
- `ad_type`: Type of ad
- `ad_unit_id`: The ad unit identifier
- `ad_network`: Network serving the ad

### Event: `ad_revenue`
Logged when revenue data is available (optional).

**Parameters**:
- `ad_type`: Type of ad
- `ad_unit_id`: The ad unit identifier
- `revenue`: Revenue amount
- `currency`: Currency code (default: USD)
- `ad_network`: Network serving the ad

## Usage in Code

Initialize Firebase Analytics in your Application class or MainActivity:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase Analytics
        FirebaseAnalyticsManager.initialize(this)
    }
}
```

## Custom Event Logging

You can also log custom events using the FirebaseAnalyticsManager:

```kotlin
// Log a custom event
val params = Bundle().apply {
    putString("custom_param", "value")
    putInt("count", 42)
}
FirebaseAnalyticsManager.logEvent("custom_event_name", params)
```

## Troubleshooting

1. **No events showing in Firebase Console**:
   - Events may take up to 24 hours to appear in Firebase Console
   - Use DebugView for real-time event monitoring during development
   - Enable debug mode: `adb shell setprop debug.firebase.analytics.app <package_name>`

2. **Build errors**:
   - Ensure google-services.json is in the correct location
   - Verify package name matches exactly between app and Firebase project
   - Clean and rebuild the project

3. **Analytics not working**:
   - Check that Google Play Services is up to date on the device
   - Verify internet connectivity
   - Check logcat for Firebase-related errors

## Privacy and Compliance

Remember to:
- Update your app's privacy policy to mention Firebase Analytics usage
- Comply with GDPR, CCPA, and other privacy regulations
- Consider implementing user consent mechanisms if required