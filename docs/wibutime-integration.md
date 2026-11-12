# Integrasi Wibutime di AnimeX

Dokumen ini mencatat cakupan adapter Wibutime yang diselaraskan dengan perilaku API pada APK `Wibutime v1.6.8 (18)` dan dokumentasi API terlampir. Base REST API: `https://paivcxbhxyytczbpyzla.supabase.co/rest/v1/`.

## Endpoint publik yang dipakai

- `POST /rpc/get_home_feed_v3` untuk katalog home/hero; ini versi feed yang dipanggil APK aktif (bukan contoh v1 `get_home_feed`).
- `POST /rpc/get_trending_series` dan `POST /rpc/get_top_series_all_time`, keduanya dengan `p_limit`.
- `POST /rpc/random_series` dengan `p_limit` untuk pilihan anime acak.
- `POST /rpc/search_animes_cards` dengan `p_query` dan `p_limit`; detail tetap dibaca dari `GET /animes?slug=eq.<slug>`.
- `GET /episodes?anime_id=eq.<slug>` untuk episode beserta kualitas stream publik.
- `GET /schedules` dengan nested relation `animes(...)` agar metadata jadwal mengikuti shape yang dipakai APK.
- APK juga membaca `GET /episode_premium_streams?episode_id=eq.<id>` untuk stream premium; AnimeX belum meminta endpoint ini karena tidak memiliki sesi Firebase Wibutime dan pemanggilan publik dapat mengembalikan array kosong.

Permintaan Supabase memakai anon key publik serta header client Android Supabase. ResponseCache Wibutime memasukkan body RPC ke cache key supaya kueri pencarian berbeda tidak tertukar, dan memakai cache stale saat request publik gagal.

## Kategori beranda

Kategori AnimeIn dan Wibutime terpisah. Home Wibutime memakai field dari `get_home_feed_v3` (anime pilihan, update anime/film/donghua, dan judul tamat), serta RPC tren, top sepanjang masa, dan acak yang dipanggil aplikasi resmi. Urutan masing-masing provider disimpan terpisah; preferensi AnimeIn lama dimigrasikan tanpa perubahan.

## Batas autentikasi dan cakupan

APK Wibutime memasang Firebase ID token sebagai Bearer ketika pengguna memiliki sesi Firebase, selain anon key. AnimeX belum mempunyai sesi/login Firebase Wibutime, jadi implementasi ini hanya menyamakan endpoint publik. Favorit Wibutime, histori/progress tersinkron, komentar, reaksi, premium stream berizin, profil, dan fitur sosial yang memakai identitas/RLS belum dihubungkan; state lokal AnimeX tetap lokal. Tidak ada service-role key atau kredensial pengguna yang disimpan.
