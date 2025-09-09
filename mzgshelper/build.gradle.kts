plugins {
    id("com.android.library") version "8.11.1"
    id("org.jetbrains.kotlin.android") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("maven-publish")
}

android {
    namespace = "com.mzgs.helper"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    publishing {
        singleVariant("release") {
            // Disable sources jar to avoid issues with dependencies that don't provide sources
            // withSourcesJar()
            withJavadocJar()
        }
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.21"
    }
}


dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-process:2.9.3")
    
    // Material Design
    implementation("com.google.android.material:material:1.13.0")
    
    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    
    // Networking & Data
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("org.jsoup:jsoup:1.21.2")
    implementation("com.google.code.gson:gson:2.13.1")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    
    // Google Play Services
    implementation("com.google.android.play:review:2.0.2")
    implementation("com.google.android.play:review-ktx:2.0.2")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-analytics")
    
    // AdMob (exposed to consuming modules)
    api("com.google.android.gms:play-services-ads:24.6.0")
    
    // AppLovin MAX (downgraded to 13.3.1 for Google adapter compatibility)
    implementation("com.applovin:applovin-sdk:13.4.0")

    
    // AppLovin MAX Mediation Adapters
    implementation("com.applovin.mediation:google-adapter:24.5.0.0")
    implementation("com.applovin.mediation:unityads-adapter:4.16.1.0")
    implementation("com.applovin.mediation:facebook-adapter:6.20.0.0")
    implementation("com.applovin.mediation:fyber-adapter:8.3.8.0")
    implementation("com.applovin.mediation:vungle-adapter:7.5.1.0")
    
    // AdMob Standalone - No mediation adapters (using as fallback only)
    
    // Testing
    testImplementation("junit:junit:4.13.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                
                groupId = "com.github.mzgs"
                artifactId = "mzgshelper"
                version = "1.0.0"
                
                pom {
                    name.set("MzgsHelper")
                    description.set("Android helper library for AdMob and AppLovin integration")
                    url.set("https://github.com/mzgs/Mzgs-Android-Helper")
                    
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }
                    
                    developers {
                        developer {
                            id.set("mzgs")
                            name.set("Mustafa Zeynel Yazgan")
                        }
                    }
                    
                    scm {
                        connection.set("scm:git:github.com/mzgs/Mzgs-Android-Helper.git")
                        developerConnection.set("scm:git:ssh://github.com/mzgs/Mzgs-Android-Helper.git")
                        url.set("https://github.com/mzgs/Mzgs-Android-Helper/tree/main")
                    }
                }
            }
        }
    }
}