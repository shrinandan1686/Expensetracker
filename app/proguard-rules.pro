# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ──────────────── Kotlin ────────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

# ──────────────── Hilt ────────────────
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# ──────────────── Room ────────────────
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# ──────────────── Retrofit + OkHttp ────────────────
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# ──────────────── Gson ────────────────
-keep class com.trackit.expense.data.remote.dto.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ──────────────── WorkManager ────────────────
-keep class androidx.work.** { *; }
-keep class com.trackit.expense.worker.** { *; }

# ──────────────── Coroutines ────────────────
-dontwarn kotlinx.coroutines.**
