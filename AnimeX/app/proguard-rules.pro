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

# Gson maps JSON by field name and builds objects through the no-arg constructor Kotlin generates
# for all-default data classes. Keep exactly those members for every model/persistence class
# (data.**, sync.**) instead of keeping whole classes: class and method names stay obfuscated.
-keepclassmembers class com.uwu.animex.data.** {
    <init>(...);
    <fields>;
}
-keepclassmembers class com.uwu.animex.sync.** {
    <init>(...);
    <fields>;
}

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
