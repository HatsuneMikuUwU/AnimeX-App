<div align="center">

# AnimeX

**Klien Android modern untuk nonton anime — cepat, bersih, bisa offline, dan tersinkron ke MyAnimeList.**

![Versi](https://img.shields.io/github/v/release/HatsuneMikuUwU/AnimeX-App?style=for-the-badge&color=6750A4&label=versi)
![Total unduhan](https://img.shields.io/github/downloads/HatsuneMikuUwU/AnimeX-App/total?style=for-the-badge&color=6750A4&label=total%20unduhan)
![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/Material%203-Expressive-1C1B1F?style=for-the-badge&logo=materialdesign&logoColor=white)

UI **Material 3 Expressive** &nbsp;•&nbsp; Pemutar **Media3 ExoPlayer** &nbsp;•&nbsp; Sinkronisasi **MyAnimeList** &nbsp;•&nbsp; Karakter dari **AniList**

</div>

---

## Sorotan

| | |
| --- | --- |
| **Unduh episode** | Simpan episode ke folder pilihanmu dan tonton tanpa internet. Bisa di-pause, dilanjutkan, dan jalan di latar belakang. |
| **Lanjut nonton** | Progres tiap episode tersimpan, jadi selalu lanjut dari posisi terakhir. |
| **Sinkron MyAnimeList** | Login MAL, lihat list, edit status/skor/tanggal/catatan, dan progres nonton otomatis ke-update. |
| **Notif episode baru** | Nyalakan alert per anime, dikasih tau kalau episode barunya udah rilis. |
| **Explore lengkap** | Filter berdasarkan genre, studio, tipe, dan tahun (dengan musim). |
| **Tampilan dinamis** | Material You di Android 12+, tema terang/gelap otomatis. |

## Fitur

### Home
- Bagian: **Lanjut Nonton**, **Episode Baru**, **Sedang Hangat**, **Judul Baru**, **Jadwal Hari Ini**, **Jas Por Yu** (acak), **Paling Ditunggu**, dan **Populer**.
- Tiap bagian punya halaman "lihat semua" dengan pemuatan bertahap.
- Hapus judul dari Lanjut Nonton lewat dialog konfirmasi.
- Tarik ke bawah untuk refresh.

### Jadwal
- Jadwal rilis per hari (Senin–Minggu), otomatis membuka hari ini.

### Cari & Explore
- Kolom cari di bagian atas dengan **riwayat pencarian** (bisa dibersihkan sekaligus).
- Explore per **Kategori**, **Studio**, **Tahun**, dan **Tipe**, lengkap dengan halaman "lihat semua".
- Filter musim (Spring / Summer / Fall / Winter) khusus kategori tahun, plus filter genre.

### Detail anime
- Judul, sinopsis, studio, statistik (views & favorites), dan daftar episode dengan pemuatan bertahap.
- Tab **Info**, **Episode**, **Season**, dan **Karakter**.
- **Karakter** lengkap dengan peran (Utama / Pendukung / Figuran), diambil dari AniList.
- Atur status tonton, tandai favorit, dan aktifkan **notifikasi episode baru**.
- Tombol unduh di setiap episode, lengkap dengan status dan menu aksi.
- Tombol lanjut yang pintar: *Lanjut Episode N*, *Nonton lagi*, atau *Putar Episode N*.

### Pemutar
- **ExoPlayer** untuk link langsung dan HLS, **WebView** untuk server embed.
- Pilih server dan kualitas, mode layar penuh, dan kunci layar.
- Lanjut dari posisi terakhir, dan hitung mundur untuk **episode berikutnya** otomatis.
- Otomatis memutar file **offline** kalau episode sudah diunduh.

### Unduhan
- Pilih folder penyimpanan sekali lewat *Storage Access Framework*, tanpa izin storage tambahan.
- Pilih kualitas sebelum mengunduh.
- Mendukung file langsung (MP4) dan **HLS**, termasuk segmen terenkripsi **AES-128**.
- Antrean dengan **2 unduhan paralel**, plus pause, lanjut, coba lagi, batal, dan hapus file.
- Jalan sebagai foreground service dengan notifikasi progres dan tombol **pause semua**.
- Unduhan yang terhenti otomatis balik ke antrean saat aplikasi dibuka lagi; entri yang filenya sudah dihapus dibersihkan sendiri.
- Daftar unduhan dikelompokkan per anime, bisa dibuka-tutup.

### Bookmark
- Daftar anime per status: **Lagi Ditonton**, **Selesai**, **Ditunda**, **Dihentikan**, **Mau Ditonton**, ditambah **Favorite**.
- Kalau sudah login MAL, tab ini berubah jadi tab **MAL** yang menampilkan list MyAnimeList kamu, lengkap dengan pilihan urutan (terbaru diupdate, judul A–Z, skor, tanggal rilis, dll.).
- Anime di list MAL bisa langsung dicocokkan dan dibuka di sumber AnimeX.

### MyAnimeList
- Login lewat OAuth (PKCE) dengan deep link `animex://mal-auth`.
- Halaman **profil**: foto, tanggal lahir/gabung, rata-rata skor, total episode, hari nonton, jumlah nonton ulang, dan statistik status dalam grafik donat.
- **Editor entri**: status, skor, episode, tanggal mulai/selesai, tag, prioritas, nonton ulang (jumlah & nilai), dan catatan.
- **Sinkron otomatis**: saat episode ditonton sampai hampir habis, progres langsung dikirim ke MAL (bisa dimatikan).
- Kalau anime tidak ada di MAL, status tetap disimpan lokal.

### Notifikasi
- **Episode baru**: dicek berkala di latar belakang (WorkManager) untuk anime yang alert-nya aktif. Ketuk notifikasinya untuk langsung ke halaman detail.
- **Unduhan**: progres, pause semua, dan info kalau layanan unduhan dihentikan sistem.

### Tampilan
- Warna dinamis (Material You) di Android 12+, tema mengikuti sistem.
- Bottom navigation lima tab: **Home**, **Jadwal**, **Explore**, **Bookmark** (atau **MAL**), **Unduhan**.
- Transisi fade antar layar dan splash screen.
- Seluruh teks aplikasi memakai bahasa Indonesia casual.

## Data & penyimpanan

- Bookmark, riwayat tonton, progres, riwayat pencarian, dan alert episode disimpan **lokal** di perangkat pakai **Room**.
- Token MAL dan pengaturan disimpan di perangkat, tidak dikirim ke server lain.

## Teknologi

| Bagian | Yang dipakai |
| --- | --- |
| UI | Jetpack Compose, Material 3 (Expressive), Navigation Compose |
| Pemutar | Media3 ExoPlayer (+ HLS, UI) |
| Jaringan | OkHttp, Gson |
| Gambar | Coil 3 |
| Asinkron | Kotlin Coroutines |
| Database | Room (KSP) |
| Latar belakang | WorkManager |
| Penyimpanan file | DocumentFile (SAF) |

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
        │   ├── EpisodeCheckWorker.kt     # cek episode baru berkala
        │   ├── data/                     # Api, Models, Downloads, Bookmarks, History,
        │   │   │                         # Progress, SearchHistory, Characters,
        │   │   │                         # EpisodeAlerts, Mal, MalLibrary
        │   │   └── db/                   # Room: entity, DAO, database
        │   ├── sync/                     # akun & sinkronisasi (provider MAL)
        │   └── ui/                       # layar, komponen, tema (Theme, Color, Type)
        └── res/
```

## Konfigurasi (secrets)

Alamat API dan key MAL **tidak ditulis di kode**. Semuanya dibaca saat build dari environment variable, atau dari `AnimeX/local.properties` untuk build lokal (file ini sudah di-`.gitignore`).

| Environment variable | Key di `local.properties` | Isi |
| --- | --- | --- |
| `API_GATE_URL` | `api.gate` | URL gate untuk mengambil alamat API aktif (boleh kosong kalau `API_BASE_URL` diisi) |
| `API_BASE_URL` | `api.base` | URL dasar API (dipakai langsung, atau sebagai cadangan kalau gate kosong) |
| `MAL_KEY` | `mal.key` | Client ID aplikasi MyAnimeList |

Contoh `AnimeX/local.properties`:

```properties
api.gate=https://contoh-gate.example/
api.base=https://contoh-api.example/
mal.key=isi_client_id_mal
```

> Tanpa `API_GATE_URL` dan `API_BASE_URL`, aplikasi bisa dibuka tapi tidak akan bisa memuat data. Tanpa `MAL_KEY`, fitur login MAL tidak aktif.

## Build

> Proyek ini tidak menyertakan Gradle wrapper, jadi Gradle harus terpasang sendiri.

**Kebutuhan:** JDK, Android SDK, dan Gradle. Versi yang dipakai ada di `AnimeX/app/build.gradle.kts` dan `.github/workflows/build.yml`.

**Debug**

```bash
cd AnimeX
gradle assembleDebug
```

APK ada di `AnimeX/app/build/outputs/apk/debug/`.

**Release** — butuh keystore lewat environment variable:

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

<details>
<summary><b>Build lewat GitHub Actions</b></summary>

<br>

Workflow **Build APK** jalan otomatis saat push ke `main`/`master`, saat push tag `v*`, pada pull request, atau manual lewat **Run workflow**.

Sebelum pertama kali dipakai, tambahkan secrets di **Settings → Secrets and variables → Actions**:

| Secret | Isi |
| --- | --- |
| `API_GATE_URL` | URL gate API |
| `API_BASE_URL` | URL dasar API |
| `MAL_KEY` | Client ID MyAnimeList |
| `KEYSTORE_BASE64` | Isi file keystore yang di-encode base64 |
| `KEYSTORE_PASSWORD` | Password keystore |
| `KEY_ALIAS` | Alias key |
| `KEY_PASSWORD` | Password key |

Setelah build selesai, unduh APK dari **Artifacts** dengan nama `animex-release`.

**Rilis otomatis ke GitHub Releases**

Push tag yang diawali `v` dan workflow akan build lalu meng-upload semua APK (per-ABI dan universal) ke halaman **Releases**, lengkap dengan catatan rilis otomatis:

```bash
git tag v1.0.8
git push origin v1.0.8
```

Bisa juga lewat tombol **Run workflow**: isi kolom **Tag rilis** (misalnya `v1.0.9`), dan tag akan dibuat otomatis di commit terbaru branch yang dipilih. Kalau kolom itu dikosongkan, workflow hanya build dan upload ke Artifacts tanpa membuat rilis.

Pastikan angka tag sama dengan `appVersion` di `AnimeX/app/build.gradle.kts`. Kalau workflow ditolak saat membuat rilis, buka **Settings → Actions → General → Workflow permissions** lalu pilih **Read and write permissions**.

</details>

## Disclaimer

AnimeX adalah klien tidak resmi. Semua data dan konten berasal dari layanan pihak ketiga dan tetap menjadi milik pemiliknya masing-masing.
