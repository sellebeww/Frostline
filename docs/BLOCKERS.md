# Blockers

Masalah yang gagal diperbaiki setelah 3 percobaan berbeda, atau yang terbukti butuh perangkat fisik atau lingkungan yang tidak tersedia (akar masalah dan opsi). Sumber: audit Fase 8 (`docs/AUDIT.md`, bagian D). Semua butir HW dijalankan pemilik lewat `docs/HARDWARE_TEST.md`.

| ID | Butir | Akar masalah | Opsi atau langkah pemilik |
|---|---|---|---|
| ENV-01 | `connectedDebugAndroidTest` (14 tes, termasuk `ReportInstrumentedTest`) tidak dijalankan pada Fase 8 | `emulator-5554` sisa sesi lama macet (`adb shell getprop` timeout 15 detik, proses qemu hidup). Agen tidak diizinkan mematikan proses yang bukan miliknya. | Pemilik: `adb emu kill` (atau akhiri proses qemu), boot ulang AVD `Vassel_Pixel3a_API34`, lalu `./gradlew :app:connectedDebugAndroidTest` (HARDWARE_TEST butir 39). Terakhir lulus 14 tes di Fase 6; `ReportInstrumentedTest` belum pernah dijalankan. |
| HW-01 | GATT dan pemindaian BLE nyata, sketsa ESP32 | Tidak ada sensor atau ponsel BLE | HARDWARE_TEST 1 sampai 16 |
| HW-02 | Layanan di ponsel nyata: Doze, layar mati lama, OEM agresif | idem | HARDWARE_TEST 3, 10, 12 |
| HW-03 | Baca dan tulis tag NFC fisik | Tidak ada tag atau NFC | HARDWARE_TEST 17 sampai 29 |
| HW-04 | GPS nyata, lokasi dalam ruangan | Tidak ada GPS | HARDWARE_TEST 26, 27 |
| HW-05 | Kunci StrongBox atau TEE perangkat keras | Emulator memakai Keystore perangkat lunak | HARDWARE_TEST 40 |
| HW-06 | Render PDF asli dan lembar bagikan | Robolectric tidak punya `PdfDocument` native; `ReportInstrumentedTest` belum dijalankan | HARDWARE_TEST 30 sampai 36, 39 |
| HW-07 | TalkBack, font besar, rotasi nyata | Perlu perangkat | HARDWARE_TEST 38 |
| HW-08 | APK rilis (R8) di perangkat: SQLCipher, Hilt, Room | `assembleRelease` lulus dan `mapping.txt` menyimpan SQLCipher, tetapi APK belum dijalankan | HARDWARE_TEST 37 |
