# Zampa ProGuard Rules

# ── Atributos generales ──
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# ── Firebase ──
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── Modelos de datos (Firestore serialización) ──
-keep class com.sozolab.zampa.data.model.** { *; }

# ── Hilt (DI) ──
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-dontwarn dagger.hilt.**

# ── Kotlin coroutines ──
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ── Kotlin ──
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Jetpack Compose ──
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Coil (image loading) ──
-keep class coil.** { *; }
-dontwarn coil.**

# ── Navigation Compose ──
-keep class androidx.navigation.** { *; }

# ── Google Play Services / Auth ──
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }
