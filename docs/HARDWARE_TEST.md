# Uji perangkat (checklist)

Semua butir di bawah **belum dijalankan**: belum ada pengujian pada ponsel, sensor BLE, atau tag NFC fisik. Ini checklist untuk pemilik. Centang `[x]` hanya setelah mengamati hasilnya sendiri, tulis hasil nyata di baris "Hasil", lalu perbarui `docs/PROGRESS.md` (ubah `SELESAI-PERLU-UJI-PERANGKAT` menjadi `SELESAI-TERVERIFIKASI` hanya untuk butir yang benar-benar lulus) dan `docs/BLOCKERS.md`.

## 0. Persiapan (sekali, sekitar 30 menit)

Alat:
- Ponsel Android 8.0 atau lebih baru dengan NFC dan GPS. Ideal: satu ponsel Android 12+ dan satu Android 11 atau lebih lama.
- Sensor uji: ESP32 dengan sketsa [`tools/esp32-ess-sensor`](../tools/esp32-ess-sensor/README.md) (sinus 1 sampai 9 °C, periode 10 menit, notifikasi tiap 10 detik) atau sensor ESS komersial.
- 3 tag NTAG213/215/216 kosong, 1 tag asing (kartu transportasi atau tag berisi URL lain), 1 tag read-only bila ada.
- Komputer dengan JDK 17, Android SDK, `adb`, Python 3.

Perintah:
```
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:installDebug                     # build debug (ada simulator)
adb shell pm clear com.vassel.frostline         # mulai dari data bersih sebelum tiap sesi
pip install -r tools/requirements.txt           # untuk uji laporan
```
Catatan: `run-as` tidak bisa membaca database terenkripsi; amati lewat layar Beranda dan notifikasi. Pada build debug, entri "Simulasi: ..." muncul di daftar sensor; untuk uji di bawah pilih sensor nyata (`Frostline-ESS`) kecuali disebut lain. Interval log default 60 detik, ambang 2,0 sampai 8,0 °C, beku <= 0,0 °C.

Urutan yang disarankan: (1) bagian 1 butir 1 sampai 6, (2) bagian 2 butir 17 sampai 22, (3) bagian 3 butir 30 sampai 35, lalu sisanya. Butir 3 dan 10 butuh waktu panjang (1 jam dan semalam): mulai lebih dulu dan kerjakan hal lain.

Format hasil per butir: `Hasil: LULUS | GAGAL | SEBAGIAN, ponsel/OS, catatan`.


## 1. Sensor BLE dan layanan pemantauan (butir 1 sampai 16)

### 1. Pairing

- [ ] **Langkah:** 1. Nyalakan ESP32. 2. Buka Frostline, ketuk "Pilih sensor". 3. Beri izin Bluetooth (Android 12+) atau lokasi (Android 11 ke bawah). 4. Pilih `Frostline-ESS`.
- [ ] **Harus terjadi:** Sensor muncul dengan nama, RSSI, dan id (hash MAC 12 karakter). Setelah dipilih, Beranda menampilkan nama dan id. MAC tidak tampil di UI.
- Hasil: 

### 2. Mulai pemantauan

- [ ] **Langkah:** Beranda, "Mulai pemantauan", beri izin notifikasi dan Bluetooth bila diminta.
- [ ] **Harus terjadi:** Notifikasi persisten "Frostline memantau suhu". Dalam sekitar 1 menit: "Suhu terakhir X,X °C · sensor terhubung". Beranda menampilkan "Berjalan".
- Hasil: 

### 3. Pencatatan 1 jam, layar mati

- [ ] **Langkah:** Mulai pemantauan, matikan layar, biarkan 60 menit, nyalakan layar.
- [ ] **Harus terjadi:** Notifikasi masih ada. Suhu terakhir diperbarui (bukan nilai lama). Tidak ada alarm sensor hilang. Setelah laporan tersedia (Fase 7): sekitar 60 pembacaan tanpa celah.
- Hasil: 

### 4. Bluetooth dimatikan lalu dinyalakan

- [ ] **Langkah:** Saat berjalan, matikan Bluetooth ponsel 3 menit, lalu nyalakan.
- [ ] **Harus terjadi:** Beranda menampilkan "Bluetooth mati". Setelah dinyalakan, sensor tersambung ulang sendiri dalam maksimal sekitar 1 menit tanpa membuka aplikasi. Bila mati lebih dari 5 menit: alarm "Sensor tidak mengirim data", lalu notifikasi pulih saat data kembali.
- Hasil: 

### 5. Sensor dijauhkan

- [ ] **Langkah:** Bawa sensor keluar jangkauan (atau matikan ESP32) selama 7 menit, lalu dekatkan.
- [ ] **Harus terjadi:** Alarm bunyi dan getar "Sensor tidak mengirim data" sekitar 5 menit setelah data terakhir, hanya sekali. Saat kembali: notifikasi "Sensor kembali mengirim data", pencatatan berlanjut.
- Hasil: 

### 6. Alarm excursion

- [ ] **Langkah:** Dengan sinus ESP32 (melewati 8 °C dan 2 °C), amati satu siklus 10 menit.
- [ ] **Harus terjadi:** Satu alarm "Suhu terlalu panas" per excursion, satu pemberitahuan "kembali dalam rentang" saat berakhir, tidak berulang-ulang di sekitar batas. Alarm risiko beku hanya bila suhu <= 0 °C (ubah sketsa agar sinus turun ke -1 untuk menguji).
- Hasil: 

### 7. Force-stop aplikasi

- [ ] **Langkah:** Pengaturan Android, Info aplikasi, "Paksa berhenti" saat pemantauan berjalan.
- [ ] **Harus terjadi:** Pemantauan berhenti (ini perilaku Android: force-stop tidak memulai ulang layanan). Buka Frostline: Beranda menampilkan "Lanjutkan pemantauan" (perjalanan tetap ACTIVE). Ketuk: pencatatan berlanjut, excursion yang terbuka tidak menghasilkan alarm ganda.
- Hasil: 

### 8. Reboot ponsel

- [ ] **Langkah:** Saat berjalan, restart ponsel. Jangan buka aplikasi.
- [ ] **Harus terjadi:** Layanan tidak berjalan otomatis setelah boot (tidak ada receiver boot; disengaja). Buka Frostline dan ketuk "Lanjutkan pemantauan": lanjut tanpa kehilangan data sebelumnya. Celah selama ponsel mati tercatat sebagai gap.
- Hasil: 

### 9. Proses dimatikan sistem

- [ ] **Langkah:** Saat berjalan, buka aplikasi lain yang berat atau `adb shell am kill com.vassel.frostline` (hanya mematikan proses di latar belakang).
- [ ] **Harus terjadi:** Layanan START_STICKY dimulai ulang dan melanjutkan pencatatan, atau muncul notifikasi "Pemantauan tidak bisa berjalan" (Android 12+ bisa menolak mulai layanan dari latar belakang). Ketuk notifikasi, lalu "Lanjutkan pemantauan".
- Hasil: 

### 10. Doze semalam

- [ ] **Langkah:** Pasang sensor, mulai pemantauan jam 22.00, layar mati dan ponsel diam di meja (tidak mengisi daya) sampai pagi.
- [ ] **Harus terjadi:** Catat berapa pembacaan yang ada. Jika ada celah panjang, catat jam dan merek. Doze dapat menunda timer; notifikasi BLE membangunkan CPU, sehingga celah seharusnya kecil bila baterai tidak dibatasi. Ulangi dengan pengaturan baterai "Tidak dibatasi" dan tanpa.
- Hasil: 

### 11. Panduan baterai

- [ ] **Langkah:** Beranda, "Panduan baterai", "Buka pengaturan baterai".
- [ ] **Harus terjadi:** Layar pengaturan baterai sistem terbuka (tanpa dialog izin otomatis). Teks menyebut pengaturan OEM.
- Hasil: 

### 12. Ponsel merek agresif (Xiaomi, Oppo, Vivo, Realme, Huawei, Samsung)

- [ ] **Langkah:** Ulangi uji 3 dan 10 dengan pengaturan bawaan, lalu dengan "Tidak dibatasi", "Mulai otomatis", dan aplikasi dikunci di recent apps.
- [ ] **Harus terjadi:** Catat per merek dan versi OS: apakah layanan dimatikan dengan layar mati, dan pengaturan mana yang memperbaikinya. Hasil dipakai untuk memperbarui teks panduan baterai.
- Hasil: 

### 13. Sensor tanpa ESS

- [ ] **Langkah:** Ubah UUID layanan di sketsa ESP32 (lihat README sketsa), pilih sensor itu dan mulai.
- [ ] **Harus terjadi:** Beranda menampilkan pesan bahwa sensor tidak mendukung ESS. Aplikasi tidak crash dan tidak mencoba menyambung ulang tanpa henti.
- Hasil: 

### 14. Nilai tidak valid

- [ ] **Langkah:** Ubah sketsa agar mengirim 0x8000 selama 2 menit.
- [ ] **Harus terjadi:** Nilai itu diabaikan: tidak ada pembacaan baru dicatat, tidak ada suhu aneh (-327,68) di Beranda; setelah 5 menit alarm sensor hilang.
- Hasil: 

### 15. Izin ditolak

- [ ] **Langkah:** Tolak izin Bluetooth di pemindaian; tolak izin notifikasi (Android 13+).
- [ ] **Harus terjadi:** Pemindaian menampilkan pesan izin dan tombol "Beri izin", tanpa crash. Tanpa izin notifikasi, layanan tetap mencatat tetapi alarm tidak muncul sebagai notifikasi (peringatan ada di Beranda).
- Hasil: 

### 16. Android 11 ke bawah

- [ ] **Langkah:** Ulangi uji 1 dan 2 pada ponsel Android 8 sampai 11.
- [ ] **Harus terjadi:** Pemindaian memerlukan izin lokasi dan layanan lokasi aktif; setelah itu sama seperti Android 12+.
- Hasil: 


## 2. NFC dan serah-terima (butir 17 sampai 29)

### 17. Tulis dua tag

- [ ] **Langkah:** Tab "Tulis Tag", isi "Gudang Dinkes", "Tulis ke tag", tempelkan tag 1. Ulangi untuk "Puskesmas A" dengan tag 2.
- [ ] **Harus terjadi:** "Berhasil ditulis dan dibaca ulang". Tag 3 yang read-only: pesan "hanya-baca". Nama kosong atau lebih dari 32 karakter: pesan di bawah kolom, tidak menunggu tag. Tombol Batal mematikan mode baca.
- Hasil: 

### 18. Baca tag sendiri

- [ ] **Langkah:** Beranda, "Serah-terima", tempelkan tag 1.
- [ ] **Harus terjadi:** Muncul nama tag, 8 karakter id, jenis PICKUP, suhu terakhir, status lokasi ("Mencari lokasi…" lalu koordinat atau alasan). Tombol "Catat event" aktif setelah lokasi selesai (maks. 10 detik).
- Hasil: 

### 19. Urutan PICKUP, HANDOFF, DELIVERY

- [ ] **Langkah:** Catat tag 1 (PICKUP). Tap tag 2: jenis HANDOFF, catat. Tap tag 1 lagi lalu centang "tujuan akhir": DELIVERY, catat.
- [ ] **Harus terjadi:** Tiga event berurutan. Setelah DELIVERY: pesan perjalanan selesai, notifikasi pemantauan hilang, Beranda "Tidak berjalan". Tap tag lagi: "Tidak ada perjalanan aktif".
- Hasil: 

### 20. Tap cepat

- [ ] **Langkah:** Tempelkan dan angkat tag dua kali dalam kurang dari 3 detik.
- [ ] **Harus terjadi:** Hanya satu kandidat; tap kedua diabaikan. Setelah lebih dari 3 detik tap berikutnya diproses.
- Hasil: 

### 21. Tag yang sama dua kali

- [ ] **Langkah:** Catat PICKUP dengan tag 1, lalu tap tag 1 lagi.
- [ ] **Harus terjadi:** Peringatan "tag ini sama dengan event sebelumnya" dan tombol "Ya, tetap catat".
- Hasil: 

### 22. Tag asing dan tag kosong

- [ ] **Langkah:** Tap kartu transportasi; tap tag kosong; tap tag berisi URL web.
- [ ] **Harus terjadi:** "Itu bukan tag Frostline" atau "Tag kosong". Tidak ada event tercatat.
- Hasil: 

### 23. Tag dikloning

- [ ] **Langkah:** Tulis tag 1 lalu salin isinya ke tag lain (aplikasi pembaca NFC pihak ketiga).
- [ ] **Harus terjadi:** Frostline menerima keduanya sebagai id yang sama. Ini batas yang diketahui (README Threat model), bukan bug: pastikan pengguna paham.
- Hasil: 

### 24. NFC mati

- [ ] **Langkah:** Matikan NFC di pengaturan Android, buka Serah-terima dan Tulis Tag.
- [ ] **Harus terjadi:** Pesan "NFC mati" dan tombol "Buka pengaturan NFC" yang membuka layar pengaturan NFC. Setelah dinyalakan dan kembali, tap berfungsi tanpa restart aplikasi.
- Hasil: 

### 25. Perangkat tanpa NFC

- [ ] **Langkah:** Jalankan di perangkat atau emulator tanpa NFC.
- [ ] **Harus terjadi:** Pesan "tidak punya NFC"; tanpa tombol pengaturan; pemantauan suhu tetap berjalan.
- Hasil: 

### 26. Izin lokasi ditolak

- [ ] **Langkah:** Cabut izin lokasi, tap tag, catat event.
- [ ] **Harus terjadi:** Pesan "izin lokasi ditolak"; event tetap tercatat tanpa lat/lon.
- Hasil: 

### 27. Lokasi dalam ruangan

- [ ] **Langkah:** Tap di dalam gedung tanpa GPS.
- [ ] **Harus terjadi:** Setelah paling lama 10 detik: lokasi perkiraan (bila ada lokasi di bawah 2 menit lalu) atau "waktu habis". Event tetap tercatat.
- Hasil: 

### 28. Layar mati dan reader mode

- [ ] **Langkah:** Buka Serah-terima, matikan layar, tap tag, nyalakan layar.
- [ ] **Harus terjadi:** Tidak ada event tercatat saat layar mati (mode baca hanya aktif saat layar terlihat).
- Hasil: 

### 29. Tap saat layar lain

- [ ] **Langkah:** Buka layar Beranda lalu tap tag.
- [ ] **Harus terjadi:** Aplikasi lain atau sistem yang menangani tag; Frostline tidak mencatat apa pun.
- Hasil: 


## 3. Laporan dan verifikator (butir 30 sampai 36)

### 30. Laporan dari detail

- [ ] **Langkah:** Selesaikan trip (DELIVERY), buka Riwayat, buka trip, "Buat dan bagikan laporan".
- [ ] **Harus terjadi:** Lembar bagikan terbuka dengan 4 berkas (PDF, manifest, dua CSV).
- Hasil: 

### 31. Laporan dari riwayat

- [ ] **Langkah:** Tombol "Laporan" pada kartu trip selesai di Riwayat.
- [ ] **Harus terjadi:** Sama dengan uji 30. Trip aktif tidak punya tombol itu.
- Hasil: 

### 32. Isi PDF

- [ ] **Langkah:** Buka PDF di pembaca PDF.
- [ ] **Harus terjadi:** A4, beberapa halaman bila perlu; header, ambang, statistik, grafik, tabel excursion dan custody, blok integritas, disclaimer, tanpa kata "bersertifikat" untuk Frostline. Grafik terputus pada celah data.
- Hasil: 

### 33. Verifikasi hasil asli

- [ ] **Langkah:** Jalankan verifikator pada berkas asli.
- [ ] **Harus terjadi:** `RESULT: PASS`, kode keluar 0.
- Hasil: 

### 34. Pemalsuan

- [ ] **Langkah:** Ubah satu karakter di manifest, atau satu byte PDF atau CSV.
- [ ] **Harus terjadi:** `RESULT: FAIL`, kode keluar 1.
- Hasil: 

### 35. Pembuatan ulang

- [ ] **Langkah:** Bagikan laporan yang sama dua kali.
- [ ] **Harus terjadi:** `content_hash` sama; hash PDF dan tanda tangan boleh berbeda.
- Hasil: 

### 36. Aplikasi tujuan

- [ ] **Langkah:** Kirim ke Gmail, WhatsApp, Drive.
- [ ] **Harus terjadi:** Lampiran terbaca; berkas tetap di penyimpanan privat aplikasi.
- Hasil: 


## 4. Tambahan dari audit Fase 8

### 37. APK rilis (R8)
- [ ] **Langkah:** `./gradlew :app:assembleRelease`, tanda tangani APK dengan kunci uji lokal milik Anda (jangan commit), `adb install`. Buka aplikasi, selesaikan onboarding, pilih sensor nyata, mulai perjalanan, catat PICKUP dan DELIVERY dengan tag, buat laporan.
- [ ] **Harus terjadi:** tidak crash; database terbuka (SQLCipher jalan setelah R8); Hilt dan Room berfungsi; tidak ada entri "Simulasi" di daftar sensor; laporan terbentuk.
- Hasil: 

### 38. Rotasi layar, font besar, TalkBack
- [ ] **Langkah:** saat pemantauan berjalan putar layar di tiap layar utama; set ukuran font sistem ke terbesar; nyalakan TalkBack dan telusuri Beranda, Serah-terima, Detail perjalanan.
- [ ] **Harus terjadi:** tidak ada state hilang (isian ambang, kandidat tag), tidak ada teks terpotong yang menyembunyikan tombol, TalkBack membaca status suhu, grafik, dan tombol dengan deskripsi yang masuk akal.
- Hasil: 

### 39. androidTest di perangkat
- [ ] **Langkah:** `adb shell pm clear com.vassel.frostline`, lalu `./gradlew :app:connectedDebugAndroidTest` (emulator atau ponsel; semua izin diberikan oleh rule tes).
- [ ] **Harus terjadi:** semua tes lulus, termasuk `ReportInstrumentedTest` (PDF asli, hash manifest) yang belum pernah dijalankan. Jika emulator macet: `adb emu kill`, lalu boot ulang (`emulator -avd Vassel_Pixel3a_API34 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect`).
- Hasil: 

### 40. Kunci perangkat keras
- [ ] **Langkah:** di ponsel dengan StrongBox (mis. Pixel 3 ke atas), jalankan `KeystoreTest` (bagian butir 39) dan catat hasilnya; buat laporan lalu jalankan verifikator.
- [ ] **Harus terjadi:** tanda tangan terbentuk dan lolos verifikator; catat apakah kunci StrongBox-backed atau TEE.
- Hasil: 

### 41. Jam dinding diubah (temuan F1 audit)
- [ ] **Langkah:** saat perjalanan aktif dan setelah PICKUP, ubah jam ponsel mundur 1 jam (pengaturan tanggal, nonaktifkan waktu otomatis), tunggu 3 pembacaan, kembalikan jam. Selesaikan dengan DELIVERY, buat laporan, jalankan verifikator.
- [ ] **Harus terjadi:** pencatatan tidak berhenti dan alarm tetap bekerja; laporan memuat peringatan lompatan jam; verifikator `RESULT: PASS` (digest pembacaan tetap cocok).
- Hasil: 

## Ringkasan hasil

| Bagian | Dijalankan pada | Lulus | Gagal | Catatan |
|---|---|---|---|---|
| 1 Sensor dan layanan | | | | |
| 2 NFC | | | | |
| 3 Laporan | | | | |
| 4 Tambahan | | | | |
