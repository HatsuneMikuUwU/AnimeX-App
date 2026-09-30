import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(env: String, prop: String): String =
    (System.getenv(env) ?: localProps.getProperty(prop) ?: "").trim()

val appName = "AnimeX"
val appVersion = "1.0.9"
val appVersionCode = 10

android {
    namespace = "com.uwu.animex"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.uwu.animex"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "MAL_KEY", "\"${secret("MAL_KEY", "mal.key")}\"")
        buildConfigField("String", "API_GATE_URL", "\"${secret("API_GATE_URL", "api.gate")}\"")
        buildConfigField("String", "API_BASE_URL", "\"${secret("API_BASE_URL", "api.base")}\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
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

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = true
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

base {
    archivesName.set("$appName-$appVersion")
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.github.kyant0:backdrop:2.0.1")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.work:work-runtime-ktx:2.11.0")

    val room = "2.8.5"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")
}
