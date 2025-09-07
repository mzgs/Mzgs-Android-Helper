# Consumer ProGuard rules for MzgsHelper library
# These rules are applied when the library is consumed by apps

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

# MzgsHelper library classes
-keep class com.mzgs.helper.** { *; }