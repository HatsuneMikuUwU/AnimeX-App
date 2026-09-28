<div align="center">

# AnimeX

**Klien Android modern untuk nonton anime — cepat, bersih, dan bisa offline.**

![Versi](https://img.shields.io/github/v/release/HatsuneMikuUwU/AnimeX-App?style=for-the-badge&color=6750A4&label=versi)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-Expressive-1C1B1F?style=for-the-badge&logo=materialdesign&logoColor=white)

Data dari **ANIMEIN API v5.2.2** &nbsp;•&nbsp; UI **Material 3 Expressive** &nbsp;•&nbsp; Pemutar **Media3 ExoPlayer**

</div>

---

## Sorotan

| | |
| --- | --- |
| **Unduh episode** | Simpan episode ke folder pilihanmu dan tonton tanpa internet. Bisa dijeda, dilanjutkan, dan berjalan di latar belakang. |
| **Lanjut nonton** | Progres tiap episode tersimpan, jadi selalu lanjut dari posisi terakhir. |
| **Bookmark & status tonton** | Kelola koleksi dengan status Sedang Ditonton, Selesai, Ditunda, Dihentikan, dan Ingin Ditonton. |
| **Jelajah lengkap** | Filter berdasarkan genre, studio, tipe, dan tahun (dengan musim). |
| **Tampilan dinamis** | Material You di Android 12+, tema terang/gelap otomatis. |

## Fitur

### Beranda
- Bagian: **Lanjut Nonton**, **Episode Baru**, **Sedang Hangat**, **Judul Baru**, **Jadwal Hari Ini**, **Jas Por Yu** (acak), **Paling Dinanti**, dan **Populer**.
- Tiap bagian punya halaman "lihat semua" dengan pemuatan bertahap.
- Tarik ke bawah untuk menyegarkan.

### Jadwal
- Jadwal rilis per hari, otomatis membuka hari ini.

### Cari
- Pencarian kata kunci dengan **riwayat pencarian** (maksimal 20, bisa dihapus semua sekaligus).
- Jelajah per **Kategori**, **Studio**, **Tahun**, dan **Tipe**, lengkap dengan halaman "lihat semua".
- Filter musim khusus untuk kategori tahun.
- Tarik ke bawah untuk menyegarkan.

### Detail anime
- Judul, sinopsis, studio, statistik, dan daftar episode dengan pemuatan bertahap.
- Atur status tonton dan tandai sebagai favorit.
- Tombol unduh di setiap episode, lengkap dengan status dan menu aksi.

### Pemutar
- **ExoPlayer** untuk link langsung dan HLS, **WebView** untuk server embed.
- Pilih server dan kualitas, mode layar penuh, dan kunci layar.
- Melanjutkan dari posisi terakhir.
- Otomatis memutar file **offline** jika episode sudah diunduh.

### Unduhan
- Pilih folder penyimpanan sekali lewat *Storage Access Framework*, tanpa izin storage tambahan.
- Pilih kualitas sebelum mengunduh.
- Mendukung file langsung (MP4) dan **HLS**, termasuk segmen terenkripsi **AES-128**.
- Antrean dengan **2 unduhan paralel**, plus jeda, lanjutkan, coba lagi, batalkan, dan hapus file.
- Berjalan sebagai foreground service dengan notifikasi progres dan tombol **jeda semua**.
- Unduhan yang terhenti otomatis kembali ke antrean saat aplikasi dibuka lagi; entri yang filenya sudah dihapus dibersihkan sendiri.

### Bookmark
- Daftar anime per status tonton, ditambah favorit.
- Bookmark, riwayat, progres, dan riwayat pencarian disimpan **lokal** di perangkat.

### Tampilan
- Warna dinamis (Material You) di Android 12+, tema mengikuti sistem.
- Bottom navigation lima tab: **Home**, **Jadwal**, **Cari**, **Bookmark**, **Unduhan**.
- Transisi fade antar layar dan splash screen.

## Teknologi

| Bagian | Yang dipakai |
| --- | --- |
| UI | Jetpack Compose, Material 3 (Expressive), Navigation Compose |
| Pemutar | Media3 ExoPlayer (+ HLS, UI) |
| Jaringan | OkHttp, Gson |
| Gambar | Coil 3 |
| Asinkron | Kotlin Coroutines |
| Penyimpanan | SharedPreferences, DocumentFile (SAF) |

Versi SDK dan versi aplikasi ada di `AnimeX/app/build.gradle.kts` &nbsp;•&nbsp; Paket: `com.uwu.animex`

## Struktur proyek

```
.
├── .github/
│   ├── dependabot.yml            # update dependensi otomatis
│   └── workflows/build.yml       # CI: build APK release
└── AnimeX/                       # proyek Gradle
    └── app/src/main/
        ├── kotlin/com/uwu/animex/
        │   ├── MainActivity.kt
        │   ├── AnimeDownloadService.kt   # foreground service unduhan
        │   ├── data/                     # Api, Models, Downloads, Bookmarks,
        │   │                             # History, Progress, SearchHistory
        │   └── ui/                       # layar, komponen, tema (Theme, Color, Type)
        └── res/
```

## Build

> Proyek ini tidak menyertakan Gradle wrapper, jadi Gradle harus terpasang sendiri.

**Kebutuhan:** JDK, Android SDK, dan Gradle. Versi yang dipakai ada di `AnimeX/app/build.gradle.kts` dan `.github/workflows/build.yml`.

**Debug**

```bash
cd AnimeX
gradle assembleDebug
```

APK ada di `AnimeX/app/build/outputs/apk/debug/`.

**Release** — memakai **R8 full mode** (minify + shrink resources) dan butuh keystore lewat environment variable:

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

Aturan R8 ada di `AnimeX/app/proguard-rules.pro`. Model Gson di package `data` sengaja di-keep (nama field harus cocok dengan JSON), jadi kalau menambah model baru di sana, tidak perlu menambah rule lagi. File `mapping.txt` untuk membaca stack trace ada di `AnimeX/app/build/outputs/mapping/release/`.

<details>
<summary><b>Build lewat GitHub Actions</b></summary>

<br>

Workflow **Build APK** berjalan otomatis saat push ke `main`/`master`, pada pull request, atau manual lewat **Run workflow**.

Sebelum pertama kali dipakai, tambahkan secrets di **Settings → Secrets and variables → Actions**:

| Secret | Isi |
| --- | --- |
| `KEYSTORE_BASE64` | Isi file keystore yang di-encode base64 |
| `KEYSTORE_PASSWORD` | Password keystore |
| `KEY_ALIAS` | Alias key |
| `KEY_PASSWORD` | Password key |

Setelah build selesai, unduh APK dari **Artifacts** dengan nama `animex-release`.

</details>

## Disclaimer

AnimeX adalah klien tidak resmi. Semua data dan konten berasal dari layanan ANIMEIN dan tetap menjadi milik pemiliknya masing-masing.
