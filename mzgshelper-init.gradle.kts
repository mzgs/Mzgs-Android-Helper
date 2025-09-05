// Init script to add required repositories
// This file should be applied in your project's settings.gradle.kts

allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Required for AppLovin mediation adapters
        maven { url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea") }
    }
}