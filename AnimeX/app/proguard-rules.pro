-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class coil3.network.** { *; }

-repackageclasses 'o'
-allowaccessmodification
-overloadaggressively

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

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute ''
