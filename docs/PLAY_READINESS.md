# Kesiapan Play internal testing

Checklist untuk pemilik. Tidak ada yang diunggah dan tidak ada kunci di repo. Status: `[x]` terbukti di audit (bukti disebut), `[ ]` belum atau harus dikerjakan pemilik.

## A. Build dan teknis

- [x] `applicationId` `com.vassel.frostline`, `versionCode` 1, `versionName` 0.1.0 (`app/build.gradle.kts`).
- [x] `minSdk` 26, `compileSdk`/`targetSdk` 37 (dicatat di `docs/DECISIONS.md`).
- [x] Build rilis memakai R8 dan shrink resource; aturan keep SQLCipher di `app/proguard-rules.pro`. `./gradlew :app:assembleRelease` hijau (audit 2026-11-17).
- [x] Library native selaras 16 KB: `zipalign -c -P 16` OK dan `PT_LOAD p_align = 16384` untuk `libsqlcipher.so` dan `libandroidx.graphics.path.so` di arm64-v8a dan x86_64 (diperiksa pada APK rilis).
- [x] Tanpa izin `INTERNET`, tanpa SDK analytics atau iklan; `allowBackup=false`.
- [ ] Jalankan APK rilis bertanda tangan di ponsel nyata (HARDWARE_TEST butir 37). R8 + SQLCipher + Hilt belum pernah dijalankan.
- [ ] Buat App Bundle: `./gradlew :app:bundleRelease` (belum dijalankan di audit; hanya `assembleRelease`).
- [ ] Buat kunci unggah (upload key) sendiri di mesin lokal, pakai Play App Signing. Jangan commit `*.jks`/`*.keystore` (sudah di `.gitignore`). Isi `signingConfigs` lewat `~/.gradle/gradle.properties` atau variabel lingkungan, bukan berkas di repo.
- [ ] Uji instal dari bundle (`bundletool`) pada ponsel arm64 dan, bila ada, perangkat 32-bit.
- [ ] Jalankan `connectedDebugAndroidTest` (HARDWARE_TEST butir 39).

## B. Konsol Play

- [ ] Buat aplikasi (nama, bahasa default Indonesia, aplikasi gratis, tanpa iklan).
- [ ] Kategori: Alat atau Kesehatan, **bukan** perangkat medis tersertifikasi. Deskripsi tidak boleh memuat "bersertifikat", "WHO PQS", "menjamin keamanan vaksin", "anti-manipulasi". Pakai: "alat bantu dan studi kelayakan".
- [ ] Deklarasi aplikasi kesehatan (Health apps declaration): pilih opsi yang jujur (pemantauan suhu, bukan diagnosis, bukan perangkat medis teregulasi), cantumkan disclaimer WHO PQS.
- [ ] Deklarasi izin sensitif: lokasi hanya saat tap NFC dengan layar aktif (foreground), jelaskan manfaatnya (mencatat tempat serah-terima).
- [ ] Deklarasi foreground service `connectedDevice` (jenis ini diminta dijelaskan dan didemonstrasikan): pemantauan suhu terus-menerus dari sensor BLE yang terpasang; siapkan video singkat (permintaan notifikasi persisten, sensor terhubung).
- [ ] Data safety: data lokasi dan suhu diproses di perangkat, tidak dikirim keluar, tidak dibagikan; enkripsi di perangkat; pengguna dapat meminta penghapusan dengan menghapus data aplikasi. Ekspor laporan hanya atas aksi pengguna lewat lembar bagikan Android.
- [ ] Kebijakan privasi: **belum ada**. Tulis satu halaman (apa yang disimpan, di mana, tidak ada pengiriman data, kontak pemilik) dan pasang di URL publik. Play mewajibkan URL ini untuk izin lokasi.
- [ ] Kuesioner peringkat konten dan target audiens (dewasa, tenaga kesehatan).
- [ ] Alamat kontak pengembang dan nama pemilik: Vassel Goleyu.

## C. Listing

- [ ] Ikon 512x512, feature graphic 1024x500, minimal 2 tangkapan layar ponsel asli (daftar di `docs/screenshots/README.md`; jangan pakai gambar rekaan).
- [ ] Deskripsi singkat dan panjang (Indonesia): fitur, batas jujur (tamper-evident bukan tamper-proof), kebutuhan perangkat (NFC, Bluetooth LE), disclaimer WHO PQS di paragraf pertama.
- [ ] Persyaratan fitur: BLE wajib (`uses-feature` required), NFC dan GPS opsional.

## D. Internal testing

- [ ] Unggah bundle ke jalur Internal testing, tambahkan penguji (alamat email), bagikan tautan opt-in.
- [ ] Penguji menjalankan `docs/HARDWARE_TEST.md` bagian 1 sampai 3 dengan build dari Play (bukan build debug; simulator tidak ada).
- [ ] Periksa pra-peluncuran Play (pre-launch report) dan perbaiki temuan crash atau aksesibilitas.
- [ ] Sebelum rilis lebih luas: semua butir `SELESAI-PERLU-UJI-PERANGKAT` di `docs/PROGRESS.md` sudah diuji atau dinyatakan terbuka di listing.
