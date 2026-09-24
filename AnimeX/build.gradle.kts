buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        // AGP 9 membawa KGP 2.2.10 (built-in Kotlin); naikkan ke versi terbaru.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20")
    }
}

plugins {
    id("com.android.application") version "9.4.0" apply false
    // Compose compiler plugin: versinya harus sama dengan versi Kotlin.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
