# ============================================================
# R8 FULL MODE HARDENING RULES — AnimeX
# ============================================================
# r8.fullMode=true is set in gradle.properties. Full mode is more
# aggressive (inlines across access-modifier boundaries, merges/
# repackages classes) — stronger obfuscation, but keep rules must
# be PRECISE rather than broad.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class coil3.network.** { *; }

# Repackage every obfuscated class into one flat namespace so the
# original package/module layout can't be reconstructed.
-repackageclasses 'o'
-allowaccessmodification
-overloadaggressively

# --- Gson model classes -------------------------------------
# No @SerializedName in Models.kt, so Gson matches JSON keys to
# Kotlin field names via reflection: field NAMES must survive,
# class names/methods do not. This is far narrower than the old
# blanket "-keep class com.uwu.animex.data.** { *; }", which left
# the whole data/network layer (including Api.kt business logic)
# fully readable in a decompiler.
-keepclassmembernames class com.uwu.animex.data.Envelope { <fields>; }
-keepclassmembernames class com.uwu.animex.data.Movie { <fields>; }
-keepclassmembernames class com.uwu.animex.data.Episode { <fields>; }
-keepclassmembernames class com.uwu.animex.data.Server { <fields>; }
-keepclassmembernames class com.uwu.animex.data.MovieListData { <fields>; }
-keepclassmembernames class com.uwu.animex.data.MovieDetailData { <fields>; }
-keepclassmembernames class com.uwu.animex.data.EpisodeListData { <fields>; }
-keepclassmembernames class com.uwu.animex.data.StreamData { <fields>; }
-keepclassmembernames class com.uwu.animex.data.Slider { <fields>; }
-keepclassmembernames class com.uwu.animex.data.HomeData { <fields>; }
-keepclassmembernames class com.uwu.animex.data.ExploreItem { <fields>; }
-keepclassmembernames class com.uwu.animex.data.ExploreData { <fields>; }

-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- Security layer -------------------------------------------
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Crash log obfuscation ---------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute ''
