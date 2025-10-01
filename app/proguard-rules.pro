# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keepattributes Signature

# Gson
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep our application classes
-keep class com.example.aiassistant.** { *; }

# Keep ViewBinding
-keep class * extends androidx.viewbinding.ViewBinding {
    public static * bind(...);
    public static * inflate(...);
}

# Keep data classes
-keepclassmembers class com.example.aiassistant.ChatMessage {
    *;
}

# Android support library
-dontwarn android.**
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Kotlin metadata
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-keep class org.jetbrains.annotations.** { *; }
