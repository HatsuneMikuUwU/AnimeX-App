# ---------------------------------------------------------------- Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------- Gson (reflection based)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson reads/writes these classes by reflection, and Envelope<T> is built through
# TypeToken.getParameterized(), which needs the class AND its generic signature intact.
# Narrowing this to keepclassmembers made R8 strip Envelope's type parameter at runtime
# ("X requires 0 type arguments, but got 1"), so keep the model packages whole.
-keep class com.uwu.animex.data.** { *; }
-keepclassmembers class com.uwu.animex.data.** { *; }

-keep class com.uwu.animex.sync.** { *; }
-keepclassmembers class com.uwu.animex.sync.** { *; }

-keep class com.uwu.animex.sync.providers.MALApi$* { *; }
-keepclassmembers class com.uwu.animex.sync.providers.MALApi$* { *; }

# Enums are persisted/parsed by constant name.
-keepclassmembers enum com.uwu.animex.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# ---------------------------------------------------------------- Hardening
# Release builds don't ship verbose/debug/info logging.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Readable crash reports without exposing original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------- Misc
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
