# Tangkapan layar (placeholder)

Folder ini sengaja kosong dari gambar. Tidak ada tangkapan layar rekaan di sini. Pemilik mengambilnya dari build nyata (debug dengan simulator boleh, tandai di keterangan) dan menaruh berkas PNG di sini dengan nama di bawah, lalu merujuknya dari README dan listing Play.

Cara ambil: `adb exec-out screencap -p > docs/screenshots/<nama>.png` (tema terang, bahasa Indonesia, tanpa data pribadi, koordinat lokasi sebaiknya dikaburkan).

| Berkas | Layar | Kondisi yang harus terlihat |
|---|---|---|
| `01-onboarding-disclaimer.png` | Onboarding, "Perlu diketahui" | Teks bukan pengganti logger bersertifikat WHO PQS |
| `02-home-in-range.png` | Beranda | Suhu besar, status "DALAM RENTANG", MKT, sensor terhubung |
| `03-home-excursion.png` | Beranda | Excursion panas atau risiko beku aktif, banner alarm |
| `04-scan-sensor.png` | Pilih sensor | Daftar sensor dengan nama, RSSI, id (tanpa MAC) |
| `05-start-trip.png` | Mulai perjalanan | Pilih kotak pendingin, ambang, interval |
| `06-handover-pickup.png` | Serah-terima | Tag terbaca, jenis PICKUP, suhu, lokasi, tombol "Catat event" |
| `07-write-tag.png` | Tulis Tag | Isian nama tag dan status penulisan berhasil |
| `08-trip-detail-chart.png` | Detail perjalanan | Grafik suhu dengan pita 2-8 °C, excursion, celah data |
| `09-trip-detail-timeline.png` | Detail perjalanan | Linimasa: excursion dan tiga event custody, rantai valid |
| `10-trip-list.png` | Perjalanan | Riwayat dengan tombol Laporan pada trip selesai |
| `11-share-sheet.png` | Lembar bagikan | Empat berkas laporan |
| `12-report-pdf-page1.png` | PDF laporan halaman 1 | Header, ambang, statistik, disclaimer |
| `13-report-pdf-integrity.png` | PDF laporan, blok integritas | content_hash, fingerprint, tabel custody |
| `14-verifier-pass.png` | Terminal | `verify_report.py` menampilkan `RESULT: PASS` |
| `15-verifier-fail.png` | Terminal | Satu karakter manifest diubah, `RESULT: FAIL` |
| `16-settings-thresholds.png` | Pengaturan | Ambang dan catatan "bukan standar klinis" |
| `17-battery-guide.png` | Panduan baterai | Petunjuk pengaturan baterai OEM |
| `18-db-error.png` | Layar galat database (opsional) | Pesan "Data tidak dihapus atau diubah" |
