# Consumer ProGuard rules for MzgsHelper library
# These rules are applied when the library is consumed by apps

# AppLovin SDK ProGuard Rules
-dontwarn com.applovin.**
-keep class com.applovin.** { *; }
-keep class com.applovin.sdk.** { *; }

# Keep AppLovinSdkSettings with all constructors and methods accessible
-keep public class com.applovin.sdk.AppLovinSdkSettings {
    public <init>(...);
    public *;
}
-keepclassmembers class com.applovin.sdk.AppLovinSdkSettings {
    public <init>(...);
    public *;
}

# Keep AppLovinSdk and related classes accessible
-keep public class com.applovin.sdk.AppLovinSdk {
    public *;
}
-keep public class com.applovin.sdk.AppLovinSdkConfiguration {
    public *;
}

# AppLovin Mediation Adapter Rules
-keep class com.google.ads.mediation.applovin.** { *; }
-keep class com.applovin.mediation.** { *; }
-keep class com.applovin.mediation.rtb.** { *; }

# Keep AppLovin wrapper classes used by mediation
-keep public class com.google.ads.mediation.applovin.AppLovinSdkWrapper { *; }
-keep public class com.google.ads.mediation.applovin.AppLovinInitializer { *; }
-keep public class com.google.ads.mediation.applovin.AppLovinMediationAdapter { *; }

# Keep AppLovin Ad classes
-keep public class com.applovin.adview.** { *; }
-keep public class com.applovin.impl.** { *; }

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