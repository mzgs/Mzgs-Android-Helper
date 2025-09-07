# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# AppLovin SDK ProGuard Rules
-dontwarn com.applovin.**
-keep class com.applovin.** { *; }
-keep class com.applovin.sdk.** { *; }
-keep class com.applovin.sdk.AppLovinSdkSettings { *; }
-keepclassmembers class com.applovin.sdk.AppLovinSdkSettings {
    <init>(...);
    *;
}

# AppLovin Mediation Adapter Rules
-keep class com.google.ads.mediation.applovin.** { *; }
-keep class com.applovin.mediation.** { *; }
-keep class com.applovin.mediation.rtb.** { *; }

# Google Mobile Ads SDK Rules
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# Keep all AdMob mediation adapters
-keep class com.google.ads.mediation.** { *; }

# Unity Ads
-keep class com.unity3d.ads.** { *; }
-keep class com.unity3d.services.** { *; }

# Vungle/Liftoff
-keep class com.vungle.** { *; }
-dontwarn com.vungle.**

# Mintegral
-keep class com.mbridge.** { *; }
-dontwarn com.mbridge.**

# Facebook/Meta
-keep class com.facebook.ads.** { *; }
-dontwarn com.facebook.ads.**

# Fyber
-keep class com.fyber.** { *; }
-dontwarn com.fyber.**

# General rules for reflection and annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}