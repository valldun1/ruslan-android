# ProGuard rules for Ruslan Agent

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Android classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep ViewBinding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(android.view.LayoutInflater);
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Keep Termux-related classes
-keep class ru.valldun.ruslan.** { *; }

# Keep JSON and HTTP — used at runtime by ChatActivity/Health checks
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }
-dontwarn org.json.**

# Keep Java HTTP/Networking
-keep class java.net.** { *; }
-dontwarn java.net.**

# Keep Kotlin serialization (if added later)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Chaquopy Python native library
-keep class com.chaquo.python.** { *; }
-keep class com.chaquo.python.android.** { *; }
-dontwarn com.chaquo.python.**

# Keep Chaquopy JNI entries
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Kotlin runtime
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Remove logging in release (keep errors for crash reporting)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
