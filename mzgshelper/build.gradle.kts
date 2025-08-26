plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.gson)

    implementation(libs.androidx.lifecycle.process)

    // Google Play In-App Review API
    implementation(libs.review)
    implementation(libs.review.ktx)
    // AppLovin MAX SDK
    implementation("com.applovin:applovin-sdk:+")

    // Google AdMob Adapter for AppLovin MAX
    implementation("com.applovin.mediation:google-adapter:+")

    // Google Mobile Ads SDK (required by the adapter)
    implementation(libs.play.services.ads)

    // AdMob Mediation dependencies for fallback
    implementation(libs.facebook)
    implementation(libs.unity)
    implementation(libs.fyber)
    implementation(libs.applovin)

    // Unity Ads Adapter for AppLovin MAX
    implementation("com.applovin.mediation:unityads-adapter:+")

    // Fyber (Digital Turbine Exchange) Adapter for AppLovin MAX
    implementation("com.applovin.mediation:fyber-adapter:+")

    // Facebook/Meta Audience Network Adapter for AppLovin MAX
    implementation("com.applovin.mediation:facebook-adapter:+")

    // Google User Messaging Platform for consent management
    implementation(libs.user.messaging.platform)

    // Firebase Analytics
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

}