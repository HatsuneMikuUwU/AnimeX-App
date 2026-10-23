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

# Gson maps JSON by field name, so keep fields (and no-arg constructors, so Kotlin
# default values survive R8 full mode) of every class under data/sync. Methods and
# unused classes can still be shrunk and obfuscated.
-keepclassmembers class com.uwu.animex.data.** {
    <fields>;
    <init>();
}
-keepclassmembers class com.uwu.animex.sync.** {
    <fields>;
    <init>();
}
-keep class com.uwu.animex.data.model.** { *; }

-keepclassmembers enum com.uwu.animex.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

-keep class com.uwu.animex.notifications.subscription.SubscriptionStore { *; }
