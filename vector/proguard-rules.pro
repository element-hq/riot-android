# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Android\android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}


# Dont obfuscate
-dontobfuscate
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

# Dont warn for excluded modules
-dontwarn com.amplitude.api.*
-dontwarn com.google.appengine.api.urlfetch.*
-dontwarn com.google.firebase.analytics.connector.AnalyticsConnector

# WebRTC rules
-keep class org.webrtc.** { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Serializable
-keep class * implements java.io.Serializable { *; }

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule

# Jitsi (else callbacks are not called)
-keep class org.jitsi.meet.sdk.** { *; }
