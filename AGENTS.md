# AGENTS.md — Tasirin Disc Server

Aturan kerja untuk semua agen/kontributor di repo ini. Lingkup: seluruh tree repo ini.

## 1. Prinsip Utama

- JANGAN install Android SDK / build APK secara lokal. Build SELALU di GitHub Actions
  (`.github/workflows/build.yml`). Lokal cukup edit kode + validasi XML/sintaks ringan.
- JANGAN pernah commit secret/keystore. Keystore rilis hanya ada sebagai GitHub Secret
  (`ANDROID_KEYSTORE_BASE64` + `KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`) dan di
  mesin kerja owner di luar repo; CI men-decode-nya ke `app/release.p12` (gitignored).
- Aplikasi ini adalah server protokol Remote Disc Streaming PPSSPP. Acuan perilaku =
  `Core/WebServer.cpp` dan `UI/RemoteISOScreen.cpp` di repo hrydgard/ppsspp.
  Tiru PERILAKU protokolnya saja; jangan salin kode PPSSPP (beda lisensi).

## 2. Aturan Produk (keputusan owner, jangan diubah diam-diam)

- `minSdk 21` (Android 5.0+). Jangan naikkan tanpa perintah eksplisit owner.
- Tema tetap terang biru-putih (lihat `res/values/colors.xml`).
- Bahasa UI: Indonesia. Komentar kode: Indonesia.
- Perangkat server TIDAK boleh mensyaratkan PPSSPP terpasang — itu tujuan repo ini.
- Port bawaan 51234 (port Remote Disc PPSSPP); fallback +1 sampai +19.
- Tanpa dependency baru tanpa kebutuhan nyata. Saat ini hanya
  `androidx.documentfile` (untuk SAF).

## 3. Protokol (jangan sampai regresi)

- `GET /` / `GET /<folder>/` → teks polos: baris pertama `/`, lalu nama
  ter-encode-persen per baris; folder berakhiran `/`. Hanya berkas
  `.iso/.cso/.chd/.pbp/.ppdmp` + folder yang dicantumkan.
- `HEAD /<berkas>` → 200 + Content-Length + `Accept-Ranges: bytes`.
- `GET /<berkas>` → wajib `Range: bytes=A-B` (kedua angka wajib ada) → 206 +
  `Content-Range`. Tanpa Range → 418. Format rusak → 400. Di luar berkas → 416.
  Ukuran berkas ≤0 → 404 (meniru PPSSPP).
- Jalur wajib aman: segmen di-decode lalu dicocokkan ke pohon folder SAF —
  tidak ada akses berkas di luar folder yang dipilih pengguna.
- Pendaftaran penemuan ke `report.ppsspp.org/match/update` bersifat usaha
  terbaik; kegagalannya tidak boleh menggagalkan server (jalur manual tetap ada).

## 4. Alur Kerja

- Mode audit = baca-saja + satu issue GitHub per temuan terverifikasi
  (`file:baris`, dampak, skenario, level Kritikal/Tinggi/Sedang/Rendah).
- Mode perbaiki = maraton: satu temuan → satu commit akar masalah → push;
  build CI gagal didahulukan. Satu maraton = satu versi rilis di akhir.
- Setiap perubahan perilaku WAJIB entri `CHANGELOG.md` (Keep a Changelog,
  seksi `[Unreleased]`).
- Rilis: bump `versionCode`/`versionName` di `app/build.gradle`, pindahkan
  entri `[Unreleased]` ke seksi versi baru, commit, push tag `vX.Y.Z`.
  Angka patch TIDAK mencapai 1000 (sesudah `0.9.999` → `0.10.0`, dst.).
- Toolchain dikunci: Gradle 8.14.3, AGP 8.13.2, Kotlin 2.3.21,
  compileSdk/targetSdk 36. Naik versi toolchain = keputusan owner.

## 5. Validasi Sebelum Push

- `git diff --check` bersih.
- Semua XML valid (parse dengan parser XML apa pun).
- Tidak ada berkas `release.p12`/keystore/secret ikut ter-commit
  (`git status` dibaca sebelum commit).
