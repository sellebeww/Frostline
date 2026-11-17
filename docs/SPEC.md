# Spesifikasi Frostline

Sumber kebenaran untuk tujuan, scope, keputusan terkunci, dan aturan domain.

## 1. Tujuan proyek

Frostline adalah aplikasi Android native untuk petugas imunisasi yang memantau suhu kotak pendingin vaksin (vaccine carrier / coolbox) pada rantai dingin last-mile.

- Membaca suhu dari sensor BLE murah berprofil terbuka (sensor-agnostik).
- Mencatat suhu terus-menerus, menghitung mean kinetic temperature (MKT) dan lama paparan di luar 2-8 °C, memberi alarm langsung.
- Mencatat serah-terima antar titik lewat sentuhan NFC, terikat ke lokasi, waktu, operator, dan data suhu.
- Menghasilkan laporan PDF dan manifest JSON yang tamper-evident (rantai hash append-only + tanda tangan kunci Android Keystore).
- Offline-first.

Posisi produk: alat bantu dan studi kelayakan. Frostline BUKAN pengganti logger bersertifikat WHO PQS dan tidak boleh diklaim demikian di UI, README, maupun laporan.

Tujuan portofolio: menunjukkan kedalaman platform Android (BLE, NFC, foreground service, enkripsi lokal, integritas data) dengan cerita dampak kesehatan yang jelas.

## 3. Scope lock

### Wajib (MVP, semua harus selesai)
1. Pairing satu sensor BLE (profil Environmental Sensing Service, GATT).
2. Pencatatan suhu kontinu dalam foreground service, tahan terhadap reconnect dan proses dimatikan sistem.
3. MKT, deteksi excursion (panas, dingin, risiko beku), alarm notifikasi, peringatan sensor hilang.
4. Serah-terima NFC dengan minimal dua tag, lokasi dan stempel waktu, rantai custody append-only bertanda tangan.
5. Penyimpanan lokal Room + SQLCipher, kunci tanda tangan ECDSA di Keystore.
6. Linimasa perjalanan (suhu, excursion, serah-terima dalam satu tampilan).
7. Laporan PDF bertanda tangan, manifest JSON, ekspor CSV, dan skrip verifikasi independen.
8. Mode simulator sensor (debug build) agar demo dan pengujian tidak butuh hardware.
9. Layar penulisan tag NFC (agar demo dengan dua tag bisa dibuat sendiri).

### Dilarang dikerjakan (di luar scope, jangan tambahkan walau terasa berguna)
Dashboard web, sinkronisasi Supabase, geofencing, layar peta, banyak sensor sekaligus, login atau akun backend, analytics atau telemetry, iklan, iOS, dukungan multi-bahasa selain Indonesia, klaim sertifikasi WHO PQS, fitur apa pun yang tidak ada di daftar Wajib. Arsitektur harus siap sync (UUID, timestamp UTC, append-only) tetapi sync itu sendiri tidak dibangun.

Jika menemukan ide di luar daftar Wajib, catat satu baris di `docs/BACKLOG.md` dan lanjutkan. Jangan dikerjakan.

## 4. Keputusan terkunci (jangan diubah)

| Hal | Keputusan |
|---|---|
| applicationId | `com.vassel.frostline` |
| minSdk | 26 |
| compileSdk / targetSdk | SDK stabil terbaru yang terpasang di environment, minimal 35. Catat nilainya di `docs/DECISIONS.md` |
| Bahasa / UI | Kotlin, Jetpack Compose, Material 3 |
| Arsitektur | MVVM + Clean Architecture; modul Gradle `:domain` (Kotlin/JVM murni, tanpa dependensi Android) dan `:app` |
| DI, async | Hilt, Coroutines, Flow, KSP (bukan kapt) |
| Dependensi | Version catalog `gradle/libs.versions.toml`. Verifikasi koordinat dan versi terbaru yang stabil dari sumber resmi sebelum dipakai; jangan menebak |
| DB | Room + SQLCipher (`net.zetetic:sqlcipher-android` via `SupportOpenHelperFactory`) |
| Kunci DB | Passphrase acak 32 byte, dibungkus AES-GCM dengan kunci Keystore, disimpan di file privat |
| Tanda tangan | ECDSA P-256, `SHA256withECDSA`, kunci non-exportable di Keystore, coba StrongBox lalu fallback TEE |
| Hash | SHA-256, hex huruf kecil |
| Profil sensor MVP | BLE Environmental Sensing Service `0x181A`, Temperature `0x2A6E` (sint16, satuan 0,01 °C, `0x8000` berarti tidak diketahui), Humidity `0x2A6F` (uint16, 0,01 %, opsional) |
| Foreground service | tipe `connectedDevice`. Lokasi hanya diambil saat NFC tap dengan layar aktif, jadi tidak perlu service tipe `location` |
| Interval log default | 60 detik, dapat diatur 10 sampai 600 detik |
| Threshold default | min 2,0 °C, max 8,0 °C, risiko beku <= 0,0 °C. Dapat diubah di `threshold_profiles`. Ini bukan standar klinis, tampilkan catatan itu di layar pengaturan |
| Tabel `carriers` | Diartikan sebagai kotak pendingin vaksin (id, nama, kapasitas opsional, catatan) |
| Tambahan skema | Kolom `nfc_tag_label` pada `custody_events` (ikut dalam kanonikalisasi) |
| Lisensi | MIT |

## 5. Spesifikasi domain (wajib diikuti persis)

### 5.1 MKT
- `T_i` dalam Kelvin = °C + 273,15. `ΔH = 83,144 kJ/mol = 83144 J/mol` (koma di sini desimal Indonesia, bukan pemisah ribuan; pakai satuan J/mol di kode agar konsisten dengan R). `R = 8,3144 J/(mol·K)`.
- `MKT_K = (ΔH/R) / ( -ln( Σ(w_i · exp(-ΔH/(R·T_i))) / Σ w_i ) )`, lalu kurangi 273,15 untuk °C.
- Bobot `w_i` = durasi (detik) setiap pembacaan sampai pembacaan berikutnya. Interval lebih panjang dari `gapThreshold = max(3 x interval_log, 300 detik)` TIDAK diberi bobot dan dicatat sebagai gap.
- Test wajib: suhu konstan 5 °C menghasilkan 5,0 (toleransi 0,01); MKT >= rata-rata aritmetik berbobot; satu kasus campuran dengan nilai hasil hitung manual yang dituliskan di komentar test; input kosong dan satu pembacaan ditangani tanpa NaN.

### 5.2 Excursion
- Jenis: `HEAT` (> max), `COLD` (< min), `FREEZE` (<= freezeThreshold). `FREEZE` lebih parah dari `COLD` dan menaikkan excursion yang sama (severity maksimum, bukan record baru). Perpindahan HEAT ke COLD menutup yang lama dan membuka yang baru.
- Dibuka setelah 2 pembacaan berurutan di luar rentang; `start` = timestamp pembacaan pertama yang keluar.
- Ditutup setelah 2 pembacaan berurutan kembali di dalam rentang dengan histeresis 0,2 °C (di dalam `[min+0,2 ; max-0,2]`); `end` = timestamp pembacaan pertama yang kembali.
- Simpan `peak` (nilai ekstrem) dan durasi. Hitung total durasi di luar rentang per perjalanan.
- Event alarm: `ExcursionOpened`, `FreezeRisk`, `ExcursionClosed`, `SensorLost` (tanpa pembacaan > `gapThreshold`), `SensorRecovered`.

### 5.3 Integritas
- Waktu: simpan `ts_utc_ms` (jam dinding) DAN `elapsed_ms` (`SystemClock.elapsedRealtime`). Deteksi lompatan jam dinding dari selisih keduanya dan catat sebagai peringatan di laporan.
- Pembacaan dikanonikalkan sebagai baris `ts_utc_ms|temp_centi\n` (`temp_centi = round(temp_c * 100)` Int).
- `readings_digest` = SHA-256 atas gabungan baris itu, urut `ts`, untuk semua pembacaan perjalanan sampai `ts` event.
- Event custody dikanonikalkan sebagai teks UTF-8 baris `kunci=nilai` dengan urutan tetap dan `\n` sebagai pemisah:
  `v=1, trip, seq, type, tag, tag_label, ts, lat, lon, acc, user, temp_centi, excursion_open, readings_digest, prev`
  (nilai tidak ada ditulis `-`; lat/lon 7 desimal). `prev` event pertama = 64 karakter `0`.
- `hash = SHA256(kanonik)`, `signature = SHA256withECDSA(kanonik)`.
- Tipe event: `PICKUP` (wajib pertama), `HANDOFF`, `DELIVERY` (menutup perjalanan, setelahnya tidak ada event atau pembacaan baru).
- Tabel `custody_events` tidak boleh punya jalur UPDATE atau DELETE: tidak ada di DAO dan diblokir trigger SQLite (`RAISE(ABORT)`). Pembacaan baru ke perjalanan yang sudah `COMPLETED` juga diblokir trigger.
- Jujur soal batas: ini tamper-evident, bukan tamper-proof. Perangkat yang di-root masih bisa menulis ulang seluruh rantai sebelum ekspor. Tanda tangan membuktikan "dibuat oleh kunci perangkat ini", bukan identitas manusia. Tag NFC dapat dikloning. Semua batas ini dituliskan di README bagian Threat model.

