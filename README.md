# AnimeX

Klien Android (Kotlin + Jetpack Compose + Media3) untuk API ANIMEIN v5.2.2.

Fitur: Terbaru / Hot / Populer / Episode Baru, pencarian, detail + daftar episode, pemutar (ExoPlayer untuk link direct, WebView untuk embed).

## Build di GitHub
1. Push repo ini (branch `main`). Project Gradle ada di folder `AnimeX/`.
2. Buka tab **Actions** → workflow **Build APK** (jalan otomatis saat push, atau **Run workflow**).
3. Unduh APK dari **Artifacts** → `animex-debug`.

Workflow memakai Gradle 8.9 langsung (tanpa gradle wrapper), JDK 17.

## Build lokal
`cd AnimeX && gradle assembleDebug` (Gradle 8.9+, JDK 17, Android SDK 34).
