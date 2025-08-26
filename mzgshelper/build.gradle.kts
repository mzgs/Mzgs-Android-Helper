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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
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

    // Note: AdMob mediation adapters are configured through AppLovin MAX dashboard
    // These adapters are included via AppLovin MAX SDK automatically

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