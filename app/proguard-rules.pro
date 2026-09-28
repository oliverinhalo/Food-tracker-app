# kotlinx.serialization keeps its serializers in companion/synthetic members that R8 cannot see
# are reachable from the generated descriptors.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.foodtracker.**$$serializer { *; }
-keepclassmembers class dev.foodtracker.** {
    *** Companion;
}
-keepclasseswithmembers class dev.foodtracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio ship references to optional platform classes.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# CameraX resolves some implementation classes reflectively via its extension config.
-keep class androidx.camera.camera2.Camera2Config { *; }
-keep class androidx.camera.core.impl.** { *; }
-dontwarn androidx.camera.**

# Hilt/Dagger generate code that R8 can shrink safely, but the generated entry points are only
# reached reflectively from the Application/Activity.
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.EarlyEntryPoint

# Our own serializable DTOs and domain types crossing the JSON boundary.
-keep class dev.foodtracker.data.recognition.gemini.** { *; }
-keep class dev.foodtracker.navigation.** { *; }
