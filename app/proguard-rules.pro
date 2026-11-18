# Cyrus Gallery ProGuard Rules

# MSAL (Microsoft Authentication Library)
-keep class com.microsoft.identity.** { *; }
-keep interface com.microsoft.identity.** { *; }
-dontwarn com.microsoft.identity.**

# OkHttp / Okio
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**

# Hilt / Dagger
-dontwarn dagger.hilt.**
-dontwarn javax.annotation.**
