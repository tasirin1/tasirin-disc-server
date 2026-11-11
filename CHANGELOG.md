# Changelog

Semua perubahan penting proyek ini dicatat di berkas ini.
Format mengikuti [Keep a Changelog](https://keepachangelog.com/).

## [Unreleased]

## [0.1.0] - 2026-11-11

### Added

- Rilis pertama Tasirin Disc Server: server Remote Disc Streaming yang
  kompatibel dengan PPSSPP, berdiri sendiri tanpa PPSSPP di perangkat server.
- Pemilih folder game via SAF (Storage Access Framework); memindai berkas
  `.iso`, `.cso`, `.chd`, `.pbp`, `.ppdmp` termasuk subfolder (maks 4 tingkat).
- Server HTTP peniru protokol PPSSPP: daftar game teks polos di `/`,
  `HEAD` untuk ukuran berkas, dan `GET` dengan `Range` bytes (206) —
  tanpa Range dibalas 418 persis seperti server bawaan PPSSPP.
- Layanan latar depan (foreground service) dengan notifikasi tetap;
  port bawaan 51234 dengan cadangan port berikutnya bila sibuk.
- Pendaftaran otomatis ke server penemuan PPSSPP (report.ppsspp.org) tiap
  ±60 detik agar "Browse games" bisa menemukan server ini; jalur manual
  IP + port tetap tersedia dan didokumentasikan di aplikasi.
- Tema terang biru-putih; dukungan Android 5.0 (API 21) ke atas.
- Build & rilis lewat GitHub Actions; APK rilis ditandatangani keystore
  tetap dari secret repo.
