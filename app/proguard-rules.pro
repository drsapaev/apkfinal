# ====================================
# ClinicSystemMobile — ProGuard / R8 keep rules
# ====================================
# E2.4 (M0): filled in keep-rules for all libraries that use reflection or
# code generation. Without these rules, release builds with minifyEnabled=true
# would strip adapters and factories, causing runtime crashes when Moshi tries
# to find a JsonAdapter, Retrofit tries to invoke an interface method, or Room
# tries to instantiate an entity.
# ====================================

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep annotations (needed for Moshi @JsonClass, Room @Entity, etc.)
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleAnnotations,RuntimeInvisibleParameterAnnotations,AnnotationDefault
-keepattributes Signature,InnerClasses,EnclosingMethod

# ====================================
# Moshi
# ====================================
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.JsonClass <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <methods>;
}
-keep class **JsonAdapter { *; }
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# ====================================
# Retrofit
# ====================================
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ====================================
# OkHttp
# ====================================
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ====================================
# Room
# ====================================
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**
-keep class androidx.room.RoomDatabase { *; }

# ====================================
# SQLCipher (net.zetetic:sqlcipher-android)
# ====================================
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.**
-keepclassmembers class * extends net.zetetic.database.sqlcipher.SQLiteOpenHelper {
    <init>(...);
}

# ====================================
# Coroutines
# ====================================
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class kotlin.coroutines.Continuation { *; }

# ====================================
# Compose
# ====================================
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# ====================================
# Hilt / Dagger (prepared for M2/E5.1)
# ====================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-dontwarn dagger.hilt.**

# ====================================
# Kotlin Metadata (needed for reflection)
# ====================================
-keepattributes KotlinMetadata
-keep class kotlin.Metadata { *; }

# ====================================
# AndroidX Security (EncryptedSharedPreferences)
# ====================================
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ====================================
# Firebase (kept disabled in M0/E1.7, but rules in place for future)
# ====================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ====================================
# Application-specific: keep DTOs and Entities
# ====================================
-keep class com.aistudio.clinicsystem.data.api.** { *; }
-keep class com.aistudio.clinicsystem.data.db.** { *; }
-keep class com.aistudio.clinicsystem.data.repository.** { *; }
