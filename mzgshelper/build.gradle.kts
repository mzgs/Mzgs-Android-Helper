plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mzgs.helper"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.21"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Compose dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.gson)
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation(libs.androidx.lifecycle.process)

    // Google Play In-App Review API
    implementation(libs.review)
    implementation(libs.review.ktx)
    // AppLovin MAX SDK - Latest stable version
    implementation(libs.applovin.sdk)

    // Google AdMob Adapter for AppLovin MAX
    implementation(libs.google.adapter)

    // Google Mobile Ads SDK (required by the adapter)
    implementation(libs.play.services.ads)
    
    // AdMob Mediation Adapters
    // Unity Ads adapter for AdMob mediation
    implementation("com.google.ads.mediation:unity:4.16.1.0")
    
    // AppLovin adapter for AdMob mediation  
    implementation("com.google.ads.mediation:applovin:13.0.1.0")
    
    // Mintegral adapter for AdMob mediation
    implementation("com.google.ads.mediation:mintegral:16.9.91.1")
    
    // Liftoff (Vungle) adapter for AdMob mediation
    implementation("com.google.ads.mediation:vungle:7.4.2.0")

    // Unity Ads Adapter for AppLovin MAX
    implementation("com.applovin.mediation:unityads-adapter:4.16.1.0")

    // Facebook/Meta Audience Network Adapter for AppLovin MAX
    implementation("com.applovin.mediation:facebook-adapter:6.20.0.0")

    implementation("com.applovin.mediation:fyber-adapter:+")
    implementation("com.applovin.mediation:vungle-adapter:+")

    // Google User Messaging Platform for consent management
    implementation(libs.user.messaging.platform)

    // Firebase Analytics
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

}