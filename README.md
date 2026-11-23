# TOOLS SHIZUKU

Template aplikasi Android dengan dashboard Shizuku dan build APK otomatis melalui GitHub Actions.

## Fitur
- Status koneksi Shizuku dan tombol permintaan izin.
- Apply preferensi refresh rate (target default 300 Hz, bisa diubah 60–300 Hz).
- Apply skala animasi Android 0.5x.
- Reset/Restore nilai refresh rate dan animasi yang tersimpan sebelum Apply pertama.
- Preset sensitivitas X=15 dan Y=30 serta target FPS=60 ditampilkan sebagai profil saja.

## Batasan penting
- Refresh rate aktual dibatasi kemampuan layar, driver, dan kebijakan sistem. Menulis target 300 Hz tidak membuat layar 60/90/120 Hz menjadi 300 Hz.
- Touch sampling 300 Hz hanya indikator target. Android tidak menyediakan perintah universal yang aman untuk memaksa touch sampling rate.
- Sensitivitas X/Y dan FPS game tidak diubah oleh aplikasi ini; nilainya hanya preset karena tiap game punya konfigurasi sendiri.
- Shizuku harus sudah terpasang dan berjalan. Pengguna harus menyetujui izin dari dialog Shizuku.
- Restore mengembalikan nilai yang dibaca aplikasi sebelum Apply pertama. Jika pengaturan diubah oleh aplikasi lain setelah itu, Restore tetap mengembalikan snapshot awal.

## Build di GitHub
1. Buat repository GitHub baru.
2. Upload seluruh isi folder proyek ini ke repository (jangan upload folder luarnya sebagai satu folder bersarang).
3. Buka tab **Actions** dan jalankan workflow **Build APK**, atau push ke branch `main`/`master`.
4. Setelah selesai, buka hasil workflow dan unduh artifact `TOOLS-SHIZUKU-debug-apk`.
5. Ekstrak artifact ZIP untuk mendapatkan `app-debug.apk`.

## Build lokal
Gunakan Android Studio dengan JDK 17 dan Android SDK 35, lalu jalankan task `assembleDebug`.

## Dependency Shizuku
Menggunakan Shizuku API `13.1.5` dari Maven Central. Dokumentasi: https://github.com/RikkaApps/Shizuku-API
