# ---- Room ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ---- Gson (R8) ----
# https://github.com/google/gson/blob/main/Troubleshooting.md#r8-abstract-class
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

# Model + nested classes yang di-fromJson / toJson (jangan di-obfuscate)
-keep class com.uwu.animex.data.** { *; }
-keepclassmembers class com.uwu.animex.data.** { *; }

-keep class com.uwu.animex.sync.** { *; }
-keepclassmembers class com.uwu.animex.sync.** { *; }

-keep class com.uwu.animex.sync.providers.** { *; }
-keepclassmembers class com.uwu.animex.sync.providers.** { *; }

# Nested data class di dalam MALApi (ResponseToken, MalNode, dll.)
-keep class com.uwu.animex.sync.providers.MALApi$* { *; }
-keepclassmembers class com.uwu.animex.sync.providers.MALApi$* { *; }

# Enum yang di-serialize / valueOf
-keepclassmembers enum com.uwu.animex.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

# OkHttp / coroutines (hindari warning noise)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
