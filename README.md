# Tasirin Disc Server

Server **Remote Disc Streaming** untuk PPSSPP yang berdiri sendiri — perangkat
server **tidak perlu memasang PPSSPP**. Cukup aplikasi ini: pilih folder berisi
game PSP, mulai server, lalu perangkat lain yang menjalankan PPSSPP bisa main
langsung lewat WiFi. Berkas ISO **tidak disalin**; yang mengalir lewat jaringan
hanya potongan data disc yang sedang dibaca game (streaming), dan game tetap
diemulasi di perangkat yang main.

- Android **5.0 (API 21) ke atas**
- Tema terang biru-putih
- Format yang dibagikan: `.iso`, `.cso`, `.chd`, `.pbp`, `.ppdmp` (termasuk di
  subfolder, sampai 4 tingkat)

## Cara pakai

Di perangkat server (aplikasi ini):

1. Ketuk **Pilih Folder Game**, arahkan ke folder berisi ISO/CSO/CHD PSP-mu.
2. Ketuk **Mulai Server**. Alamat server muncul di kartu status,
   misalnya `http://192.168.0.10:51234/`.
3. Biarkan aplikasi ini terbuka — server berjalan sebagai layanan latar depan
   (ada notifikasi tetap selama server hidup).

Di perangkat yang main (PPSSPP):

1. Pastikan satu WiFi/LAN yang sama dengan perangkat server.
2. Buka **Settings → Tools → Remote Disc Streaming → Browse games**.
   Server ini mendaftarkan diri ke server penemuan PPSSPP seperti server
   bawaan PPSSPP, jadi biasanya langsung ketemu.
3. Bila tidak ketemu: di layar Remote Disc Streaming PPSSPP buka **Settings**,
   aktifkan konfigurasi manual, lalu isi alamat IP dan port persis seperti di
   kartu status aplikasi ini.
4. Biar loading enteng: **Settings → System → I/O timing method → Host**.

Catatan jujur:

- Fitur Remote Disc Streaming di PPSSPP sendiri dinyatakan "belum sepenuhnya
  pulus" oleh dokumentasinya — loading bisa lebih lambat daripada berkas
  lokal, dan perangkat yang main tetap memakai sedikit ruang untuk cache.
- Save game tersimpan di perangkat yang main, bukan di server.
- Server ini hanya melayani jaringan lokal. Jangan buka port-nya ke internet.

## Unduh

APK tersedia di halaman **Releases** repo ini (`disc-server-vX.Y.Z-release.apk`),
ditandatangani dengan keystore rilis tetap sehingga versi berikutnya bisa
langsung menimpa pemasangan lama.

## Bangun (build)

Build hanya lewat GitHub Actions (`.github/workflows/build.yml`) — tidak ada
build lokal. Push ke `main` menghasilkan APK sebagai artifact; push tag
`vX.Y.Z` (harus sama dengan `versionName` di `app/build.gradle`) menerbitkan
GitHub Release. Keystore rilis berasal dari secret `ANDROID_KEYSTORE_BASE64`
(+ `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) dan tidak pernah masuk
repo.

Toolchain: Gradle 8.14.3, AGP 8.13.2, Kotlin 2.3.21, compileSdk/targetSdk 36,
minSdk 21.

## Protokol

Aplikasi ini meniru perilaku server Remote Disc Streaming bawaan PPSSPP
(mengacu `Core/WebServer.cpp` di proyek PPSSPP, tanpa menyalin kodenya):

- `GET /` dan `GET /<folder>/` → daftar isi teks polos: baris pertama `/`,
  lalu satu nama ter-encode-persen per baris (folder berakhiran `/`).
- `HEAD /<berkas>` → `200` + `Content-Length` + `Accept-Ranges: bytes`.
- `GET /<berkas>` wajib membawa header `Range: bytes=A-B` → `206` +
  `Content-Range`; tanpa Range → `418`; Range rusak → `400`; di luar ukuran
  berkas → `416`.
- Port bawaan `51234`; bila sibuk, dicoba port berikutnya (maksimal +19).
- Pendaftaran penemuan: `GET http://report.ppsspp.org/match/update?local=<IP LAN>&port=<port>`
  berkala ±60 detik selama server berjalan.

## Lisensi

MIT — lihat `LICENSE`.
