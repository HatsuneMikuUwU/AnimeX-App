# ─────────────────────────────────────────────────────────────
# AnimeX — aturan R8 (full mode, default di AGP 9)
# Full mode tidak lagi menjaga konstruktor default dan membuang
# info generic, jadi semua yang diakses lewat reflection (Gson)
# harus di-keep secara eksplisit.
# ─────────────────────────────────────────────────────────────

# Atribut yang dibutuhkan Gson (generic type) dan anotasi
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Stack trace tetap terbaca (deobfuscate pakai mapping.txt)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Gson ─────────────────────────────────────────────────────
# Model API dan data lokal (Movie, Episode, Server, BookmarkEntry,
# Downloads.Item/Meta, Progress.Watch, dst.) di-parse lewat reflection.
# Nama field harus sama dengan key JSON, dan konstruktor no-arg dijaga
# supaya nilai default Kotlin tetap terisi.
-keepclassmembers class com.uwu.animex.data.** {
    <init>();
    <fields>;
}

# Enum (WatchStatus, Downloads.Status) disimpan sebagai nama konstanta
-keepclassmembers enum com.uwu.animex.data.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# TypeToken dipakai untuk Envelope<T>, LinkedHashMap<String, BookmarkEntry>, dll.
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Coil ─────────────────────────────────────────────────────
-keep class coil3.network.** { *; }

# ── Warning yang aman diabaikan ──────────────────────────────
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
