# AnimeX

Klien Android untuk menonton anime, dibuat dengan Kotlin dan Jetpack Compose. AnimeX mengambil data dari API ANIMEIN v5.2.2 dan memakai tampilan Material 3 Expressive.

## Fitur

**Beranda**
- Lanjut Nonton, Episode Baru, Sedang Hangat, Judul Baru, Jadwal Hari ini, Jas Por Yu (acak), Paling Dinanti, dan Populer.
- Tiap bagian punya halaman "lihat semua" dengan pemuatan bertahap.

**Jadwal**
- Jadwal rilis per hari, otomatis membuka hari ini.

**Cari**
- Pencarian berdasarkan kata kunci.
- Jelajah dan filter berdasarkan genre, studio, tipe, dan tahun (khusus tahun ada filter musim).

**Detail anime**
- Info judul, sinopsis, studio, statistik, dan daftar episode dengan pemuatan bertahap.
- Bookmark dengan status tonton: Sedang Ditonton, Selesai, Ditunda, Dihentikan, dan Ingin Ditonton.
- Tombol favorit.

**Pemutar**
- ExoPlayer untuk link langsung dan HLS, WebView untuk server embed.
- Pilihan server dan kualitas, layar penuh, kunci layar, dan melanjutkan dari posisi terakhir.

**Bookmark**
- Daftar anime per status tonton beserta favorit.
- Bookmark, riwayat tonton, dan progres episode disimpan lokal di perangkat.

**Tampilan**
- Warna dinamis (Material You) di Android 12 ke atas, tema terang dan gelap mengikuti sistem.
- Bottom navigation, transisi shared element, dan splash screen.

## Teknologi

| Bagian | Yang dipakai |
| --- | --- |
| UI | Jetpack Compose, Material 3 (Expressive), Navigation Compose |
| Pemutar | Media3 ExoPlayer (+ HLS, UI) |
| Jaringan | OkHttp, Gson |
| Gambar | Coil 3 |
| Asinkron | Kotlin Coroutines |

Konfigurasi SDK: `minSdk 24`, `targetSdk 34`, `compileSdk 37`. Paket aplikasi: `com.uwu.animex`.

## Struktur proyek

```
.
├── .github/workflows/build.yml   # CI: build APK release
└── AnimeX/                       # proyek Gradle
    └── app/src/main/
        ├── kotlin/com/uwu/animex/
        │   ├── MainActivity.kt
        │   ├── data/             # Api, Models, Bookmarks, History, Progress
        │   └── ui/               # layar, komponen, tema (Theme, Color, Type)
        └── res/
```

## Build

Proyek ini tidak menyertakan Gradle wrapper, jadi butuh Gradle terpasang.

Kebutuhan: JDK 17, Android SDK (platform 37), dan Gradle 9.6.0 (versi yang dipakai CI).

Build debug:

```bash
cd AnimeX
gradle assembleDebug
```

APK ada di `AnimeX/app/build/outputs/apk/debug/`.

Build release memerlukan keystore lewat environment variable berikut:

| Variable | Isi |
| --- | --- |
| `KEYSTORE_FILE` | Path file keystore |
| `KEYSTORE_PASSWORD` | Password keystore |
| `KEY_ALIAS` | Alias key |
| `KEY_PASSWORD` | Password key |

```bash
cd AnimeX
gradle assembleRelease
```

## Build lewat GitHub Actions

Workflow **Build APK** berjalan otomatis saat push ke `main`/`master`, pada pull request, atau manual lewat **Run workflow**.

Sebelum pertama kali dipakai, tambahkan secrets berikut di **Settings → Secrets and variables → Actions**:

- `KEYSTORE_BASE64`: isi file keystore yang di-encode base64
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Setelah build selesai, unduh APK dari **Artifacts** dengan nama `animex-release`.

## Catatan

AnimeX adalah klien tidak resmi. Semua data dan konten berasal dari layanan ANIMEIN dan tetap menjadi milik pemiliknya masing-masing.
