import java.security.MessageDigest
import java.security.KeyStore

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Key XOR yang SAMA persis dengan com.uwu.animex.security.Obf.KEY —
// dipakai supaya nilai yang disuntik lewat BuildConfig tetap dalam
// bentuk cipher text, bukan hash plaintext, konsisten dengan
// pendekatan string-encryption yang sudah ada.
val obfKey = byteArrayOf(0x4d, 0x69, 0x6b, 0x75, 0x21, 0x41, 0x58, 0x39)
fun obfEncode(plain: String): String {
    val bytes = plain.toByteArray(Charsets.UTF_8)
    val out = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor obfKey[i % obfKey.size].toInt()).toByte() }
    return java.util.Base64.getEncoder().encodeToString(out)
}

/**
 * Menghitung SHA-256 fingerprint dari certificate yang ada di dalam
 * signingConfig "release" (langsung dari file .jks/.keystore-nya),
 * lalu mengembalikannya sebagai cipher text (via [obfEncode]) yang
 * siap dipakai SecurityGuard. Kalau env var keystore belum di-set
 * (misal saat development lokal tanpa CI secrets), balikin string
 * kosong — SecurityGuard sudah menangani ini sebagai "skip check".
 */
fun releaseSignatureCipherOrBlank(): String {
    val storeFile = System.getenv("KEYSTORE_FILE")?.let(::file) ?: return ""
    val storePassword = System.getenv("KEYSTORE_PASSWORD") ?: return ""
    val keyAlias = System.getenv("KEY_ALIAS") ?: return ""
    if (!storeFile.exists()) return ""

    return runCatching {
        val ks = KeyStore.getInstance(if (storeFile.extension == "p12") "PKCS12" else "JKS")
        storeFile.inputStream().use { ks.load(it, storePassword.toCharArray()) }
        val cert = ks.getCertificate(keyAlias) ?: return ""
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        val hex = sha256.joinToString("") { "%02x".format(it) }
        obfEncode(hex)
    }.getOrDefault("")
}

android {
    namespace = "com.uwu.animex"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.uwu.animex"
        minSdk = 24
        targetSdk = 37
        versionCode = 7
        versionName = "1.0.6"

        // Otomatis terisi dari keystore rilis saat build — tidak
        // perlu hitung/encode manual lagi. Kosong di debug build.
        buildConfigField("String", "SIG_CIPHER", "\"${releaseSignatureCipherOrBlank()}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.10.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
}
