-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

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

-keep class com.uwu.animex.data.** { *; }
-keepclassmembers class com.uwu.animex.data.** { *; }

-keep class com.uwu.animex.sync.** { *; }
-keepclassmembers class com.uwu.animex.sync.** { *; }

-keep class com.uwu.animex.sync.providers.** { *; }
-keepclassmembers class com.uwu.animex.sync.providers.** { *; }

-keep class com.uwu.animex.sync.providers.MALApi$* { *; }
-keepclassmembers class com.uwu.animex.sync.providers.MALApi$* { *; }

-keepclassmembers enum com.uwu.animex.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ── Security: do not log / keep BuildConfig secrets readable in stack traces less easily
-keepclassmembers class com.uwu.animex.BuildConfig {
    public static final java.lang.String MAL_KEY;
    public static final java.lang.String API_GATE_URL;
    public static final java.lang.String API_BASE_URL;
}

# Repository layer
-keep class com.uwu.animex.data.repository.** { *; }
-keep class com.uwu.animex.di.** { *; }
