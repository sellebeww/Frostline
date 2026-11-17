# Progress

## Fase

| Fase | Status |
|---|---|
| 1 Scaffold | SELESAI-TERVERIFIKASI |
| 2 Domain | SELESAI-TERVERIFIKASI |
| 3 Persistensi | SELESAI-TERVERIFIKASI (StrongBox/TEE perangkat keras: PERLU-UJI-PERANGKAT) |
| 4 Sensor dan service | SELESAI-TERVERIFIKASI untuk logika dan simulator; BLE nyata PERLU-UJI-PERANGKAT |
| 5 NFC dan serah-terima | SELESAI-TERVERIFIKASI untuk logika; tag fisik PERLU-UJI-PERANGKAT |
| 6 UX dan layar | SELESAI-TERVERIFIKASI (alur penuh di emulator API 34 dengan simulator; perangkat nyata, TalkBack, font besar manual: PERLU-UJI-PERANGKAT) |
| 7 Laporan dan verifikator | SELESAI-TERVERIFIKASI untuk manifest, CSV, verifikator, pembuat laporan (tes JVM + pytest); render PDF asli dan lembar bagikan: PERLU-UJI-PERANGKAT (androidTest ditulis, belum dijalankan) |
| 8 Audit, perbaikan, dokumentasi final | SELESAI-TERVERIFIKASI untuk semua yang dapat dibuktikan tanpa perangkat; butir HW-01 sampai HW-08 dan ENV-01 (androidTest, emulator macet) tetap terbuka di `docs/BLOCKERS.md` |

## Butir Wajib

| # | Butir | Status | Lokasi |
|---|---|---|---|
| 1 | Pairing sensor BLE (ESS) | SELESAI-PERLU-UJI-PERANGKAT (parser, backoff, antrian GATT, pemindaian, layar pilih sensor dan penyimpanan terverifikasi dengan tes dan simulator di emulator; GATT/scan BLE asli belum pernah dijalankan) | `app/data/ble/*`, `ui/screens/ScanScreen.kt`, `data/db/RoomSensorRepository`, tes `EssTest`, `BackoffTest`, `BlePermissionsTest`, `MonitorFlowTest`; uji manual `docs/HARDWARE_TEST.md` |
| 2 | Pencatatan kontinu, foreground service | SELESAI-PERLU-UJI-PERANGKAT (service connectedDevice, throttle, watchdog, pemulihan setelah `kill -9` terverifikasi di emulator API 34 dengan simulator; BLE nyata, Doze, layar mati lama, OEM agresif belum) | `service/*`, tes `MonitorLoopTest` (13), `MonitorFlowTest`; uji manual `docs/HARDWARE_TEST.md` |
| 3 | MKT, excursion, alarm | SELESAI-TERVERIFIKASI (logika domain dan pemicu alarm lewat `MonitorLoopTest`; notifikasi `AlarmNotifier` diamati di emulator dengan simulator; bunyi/getar fisik belum) | `domain/engine`, `notification/AlarmNotifier.kt` |
| 4 | Serah-terima NFC, rantai custody | SELESAI-PERLU-UJI-PERANGKAT (codec NDEF, alur tap, lokasi, PICKUP/HANDOFF/DELIVERY, debounce, atomisitas, rantai lolos `ChainVerifier` terverifikasi dengan tes; membaca dan menulis tag fisik, GPS nyata belum pernah dijalankan; layar tanpa NFC tidak sempat dilihat di emulator) | `app/data/nfc/*`, `data/location/*`, `ui/screens/Handover*`, `WriteTag*`, tes `NdefCodecTest`, `HandoverControllerTest`, `LocationPolicyTest`, `CustodyFlowTest`; uji manual HARDWARE_TEST 17-29 |
| 5 | Room + SQLCipher, kunci Keystore | SELESAI-TERVERIFIKASI (emulator API 34, Keystore perangkat lunak; StrongBox nyata belum) | `app/data/db/*`, `app/data/security/*`, tes JVM `app/src/test/.../data`, androidTest `EncryptedDatabaseTest`, `KeystoreTest`, `MigrationTest` |
| 6 | Linimasa perjalanan | SELESAI-TERVERIFIKASI (domain `TripTimeline` diuji; layar dan grafik dijalankan di emulator lewat `MonitorFlowTest`) | `domain/timeline`, `ui/screens/TripDetailScreen.kt`, `ui/components/TemperatureChart.kt` |
| 7 | Laporan PDF, manifest, CSV, verifikator | SELESAI-PERLU-UJI-PERANGKAT (manifest, JSON kanonik, CSV, pembuat laporan dengan Room nyata, verifikator Python: terverifikasi dengan tes dan pytest, termasuk manifest buatan Kotlin yang lolos dan pemalsuan satu karakter yang gagal; `PdfReportRenderer` dan lembar bagikan belum pernah dijalankan: Robolectric tidak punya PdfDocument native dan emulator macet, `ReportInstrumentedTest` ditulis, belum dijalankan) | `domain/report/*`, `app/data/report/*`, `ui/screens/Report*`, `tools/verify_report.py`, tes `ReportManifestTest`, `CanonicalJsonTest`, `CsvExportTest`, `ReportDataTest`, `ManifestFixtureTest`, `ReportGeneratorTest`, `tools/tests/test_verify_report.py`; androidTest `ReportInstrumentedTest`; uji manual HARDWARE_TEST 30-36 |
| 8 | Simulator sensor (debug) | SELESAI-TERVERIFIKASI (source set debug; 6 skenario, kecepatan 10x) | `app/src/debug/...`, `MonitorLoopTest`, `MonitorFlowTest` |
| 9 | Layar tulis tag NFC | SELESAI-PERLU-UJI-PERANGKAT (validasi label dan encoding diuji; penulisan ke tag fisik tidak) | `ui/screens/WriteTagScreen.kt`, `data/nfc/NfcWriter.kt` |

## Audit Fase 8

`docs/AUDIT.md`: auditor independen, tiap butir PASS/FAIL dengan bukti. 8 temuan (F1 sampai F8) diperbaiki dengan tes penjaga: balapan `readings_digest` (F1, desain di DECISIONS 89), kode mati, string hardcoded, label "AMAN", R8 tidak aktif, rantai kosong berlabel valid, siklus hidup flow, tidak ada tes penjaga scope. Pemetaan 67 nama test di bagian "Pemetaan aturan" dicek satu per satu terhadap sumber: semuanya ada. Skenario MKT dan excursion dihitung terpisah di Python dan dijadikan `AuditScenarioTest`. Hasil verifikasi akhir ada di bagian "Verifikasi Fase 8".

## Verifikasi Fase 8

`./gradlew clean :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --no-build-cache`: BUILD SUCCESSFUL (5m 53s), 0 warning kompilasi. `:domain:test` 109 lulus (4 baru), `:app:testDebugUnitTest` 127 lulus (8 baru), lint 0 temuan. `pytest tools/tests`: 20 lulus. Release R8: `app-release-unsigned.apk` 9,6 MB, `mapping.txt` menyimpan `net.zetetic.database.sqlcipher.*`, tidak ada kelas simulator di dex, library native selaras 16 KB. `connectedDebugAndroidTest` TIDAK dijalankan (emulator macet, BLOCKERS ENV-01); androidTest hanya dikompilasi (`assembleDebugAndroidTest`).

## Verifikasi Fase 7

`./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --rerun-tasks --no-build-cache`: BUILD SUCCESSFUL, 0 warning kompilasi, lint 0 temuan. `:domain:test` 105 lulus (22 baru), `:app:testDebugUnitTest` 119 lulus (3 baru: `ReportGeneratorTest`). `pytest tools/tests`: 20 lulus. Verifikator pada manifest buatan Kotlin: `RESULT: PASS`; pada salinan dengan satu karakter diubah: `RESULT: FAIL`, kode keluar 1; pada fixture rantai Fase 2: PASS. `connectedDebugAndroidTest` TIDAK dijalankan: emulator lama tidak menjawab adb. `ReportInstrumentedTest` ditulis, belum dijalankan; PDF asli belum pernah dirender di sini.

## Verifikasi Fase 6

`:domain:test` 83, `:app:testDebugUnitTest` 116 (ViewModel dengan Turbine, komponen Compose lewat Robolectric), lint 0 temuan, assembleDebug hijau, `:app:connectedDebugAndroidTest` 14 lulus di emulator (termasuk alur penuh onboarding, trip, PICKUP/HANDOFF/DELIVERY simulasi, riwayat, rantai valid). Alarm heads-up dan tombol Buka diamati hanya lewat kode/tes, bunyi fisik belum.

## Environment (dicek 2026-11-16)

- `emulator -list-avds`: `Vassel_Pixel3a_API34`. Boot headless: `emulator -avd Vassel_Pixel3a_API34 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect &` (siap dalam ~2 menit), matikan dengan `adb emu kill`. `adb devices` tanpa emulator: kosong.
- Hasil verifikasi Fase 5 (`--rerun-tasks`): BUILD SUCCESSFUL, 0 warning, lint 0 temuan; `:domain:test` 77, `:app:testDebugUnitTest` 85, `:app:connectedDebugAndroidTest` 14 lulus (dijalankan sebelum perbaikan debounce konfirmasi; kode itu hanya VM UI) (migrasi 1 ke 2 diuji di emulator API 34).

## Pemetaan aturan SPEC.md 5.1-5.3 ke test (`:domain`)

| Aturan | Test |
|---|---|
| 5.1 konstan 5 °C = 5,0 | `MktCalculatorTest.constant5CGivesFive` |
| 5.1 MKT >= rata-rata berbobot (250 kasus, seed 42) | `MktCalculatorTest.mktIsNeverBelowWeightedMeanRandomised` |
| 5.1 kasus campuran hitung manual | `MktCalculatorTest.mixedCaseMatchesHandCalculation` |
| 5.1 kosong dan satu data tanpa NaN | `MktCalculatorTest.emptyAndSingleReadingAreSafe` |
| 5.1 gap dikecualikan, `gapThreshold` | `gapIntervalIsExcludedFromWeighting`, `gapThresholdIsMaxOfThreeIntervalsAndFiveMinutes`, `intervalExactlyAtThresholdIsStillWeighted` |
| 5.2 buka setelah 2 pembacaan, start = pertama | `ExcursionEngineTest.opensAfterTwoConsecutiveReadingsWithStartAtFirst` |
| 5.2 1 spike tidak membuka | `singleSpikeDoesNotOpen` |
| 5.2 tutup + histeresis 0,2, end = pertama kembali | `closesAfterTwoReadingsInsideHysteresisBandEndAtFirstReturn`, `readingInRangeButOutsideBandResetsReturnCount` |
| 5.2 tidak flapping | `doesNotFlapAroundTheBoundary` |
| 5.2 HEAT lalu COLD = dua record | `heatThenColdMakesTwoRecords`, `singleOppositeReadingDoesNotSwitch` |
| 5.2 COLD naik ke FREEZE (record sama), FreezeRisk | `coldEscalatesToFreezeOnSameRecord`, `openingDirectlyAsFreezeEmitsOpenedAndFreezeRisk`, `runOfColdThenFreezeBeforeOpeningUsesMostSevereType` |
| 5.2 peak dan durasi, total di luar rentang | `TripStatsCalculatorTest.aggregatesMinMaxMeanMktExcursionsAndGaps`, `UseCasesTest.logReadingPersistsAndOpensExcursion` |
| 5.2 SensorLost / Recovered, onTick(now) + Clock | `sensorLostAndRecovered`, `onTickDefaultsToInjectedClock`, `lateReadingWithoutTickStillEmitsLostAndRecovered`, `gapBreaksConsecutiveCountButKeepsOpenExcursion` |
| 5.2 rekonstruksi state = state live | `rebuiltStateEqualsLiveState`, `rebuildOfEmptyHistoryIsFreshEngine`, `UseCasesTest.newLogReadingInstanceResumesFromStoredHistory` |
| 5.3 `ts_utc_ms` vs `elapsed_ms`, lompatan jam | `ClockJumpDetectorTest` (4 test) |
| 5.3 baris `ts|temp_centi\n`, `readings_digest` | `CanonicalizerTest.readingsDigestGolden`, `emptyReadingsDigestIsSha256OfEmpty`, `tempCentiRoundsHalfUpAndHandlesNegatives` |
| 5.3 kanonik event persis + hash tetap (golden) | `CanonicalizerTest.fullEventGolden`, `absentValuesAreDashAndGenesisPrevIsZeros`, `coordinatesUseDotAndSevenDecimalsRegardlessOfLocale`, `separatorsInValuesAreEscapedSoLinesCannotBeInjected` |
| 5.3 hash + signature, rantai valid | `HashChainTest.validChainPasses`, `emptyAndInProgressChainsAreValid` |
| 5.3 ubah SATU field mana pun terdeteksi | `changingAnySingleFieldOfAnyEventIsDetected`, `mutatorsCoverEveryField`, `settingOptionalFieldsToNullIsDetected` |
| 5.3 tukar urutan, hapus tengah, sisip | `swappedOrderIsDetected`, `deletedMiddleEventIsDetected`, `insertedEventIsDetected` |
| 5.3 ganti signature, kunci lain | `swappedSignatureIsDetected`, `garbageSignatureIsDetectedWithoutThrowing`, `otherKeyIsDetected` |
| 5.3 PICKUP pertama, DELIVERY terakhir | `firstEventMustBePickup`, `pickupOnlyAsFirst`, `nothingMayFollowDelivery`, `mixedTripsAreDetected`, `appendEventEnforcesRules`, `UseCasesTest.recordRejectsDeliveryAndEventsAfterCompletion` |
| 5.3 fixture untuk verifier Python | `FixtureTest.committedFixtureMatchesCurrentFormatAndVerifies` |
| 5.3 DB trigger UPDATE/DELETE (blokir) | `TriggerTest.rawUpdateOfCustodyEventIsRejected`, `rawDeleteOfCustodyEventIsRejected`, `custodyDaoHasNoUpdateOrDeleteMethods`; di SQLCipher: `EncryptedDatabaseTest.triggersAreActiveInTheEncryptedDatabase` |
| 5.3 insert ke trip COMPLETED diblokir | `TriggerTest.insertingCustodyIntoACompletedTripIsRejected`, `insertingReadingsIntoACompletedTripIsRejected` |
| 5.3 seq/prev atomik, tanpa race | `CustodyFlowTest.parallelCustodyEventsGetUniqueSequentialSeqAndAValidChain`, `failureAfterTheDeliveryInsertRollsEverythingBack` |
| Keamanan kunci DB | `DatabaseKeyManagerTest` (8 tes), `EncryptedDatabaseTest` (6 tes), `SchemaAndConfigTest.backupIsDisabled...`, `productionSourcesDoNotLog` |

## Catatan pengembangan

- Gradle dengan JDK 17: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`. Verifikasi: `./gradlew :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` (dingin 3-9 menit; jalankan di background). Mesin 8 GB: heap Gradle 4g; jangan jalankan emulator dan build berat bersamaan (hentikan daemon dengan `./gradlew --stop` sebelum uji emulator).
- Emulator: `emulator -avd Vassel_Pixel3a_API34 -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect &`, lalu `./gradlew :app:connectedDebugAndroidTest` (13 tes). `adb root` tersedia untuk `kill -9` proses.
- Log Gradle ke folder scratchpad, bukan `/tmp` (dipakai bersama proses lain).
- Alur: `ui/screens` (Home, Scan, BatteryGuide) -> `service/FrostlineMonitorService` -> `MonitorLoop` -> domain `LogReading` (engine) -> Room. Sensor lewat `data/ble/SensorBackend` (debug: simulator `sim:*`, release: BLE). Keputusan: DECISIONS.md 46-57.
- `Trip.sensorId` dan `RoomSensorRepository` (sensor terpasang = `created_at` terbaru). Operator masih placeholder "operator" (Fase 5/8).
- Fase 5 menambah skema v2 (kolom `diagnostic`), `HandoverController`, `NdefCodec`, `FusedLocationProvider` (Play services), profil operator di Beranda. Fase 6 selesai (semua layar, banner alarm, linimasa). `MonitorFlowTest` perlu data aplikasi bersih (`adb shell pm clear com.vassel.frostline`) dan memberi semua izin lewat rule. `RecordCustodyEvent` dan `CompleteTrip` belum di-provide Hilt (menunggu `LocationProvider`). Perjalanan diselesaikan dengan tap DELIVERY (NFC); di emulator tanpa NFC pakai "Hapus data" untuk memulai ulang.
- Sketsa ESP32 `tools/esp32-ess-sensor/` belum diuji di hardware; uji manual di `docs/HARDWARE_TEST.md` belum dijalankan satu pun.
- Skema Room v1 (`app/schemas/`); fixture Python `domain/src/test/resources/fixtures`. Kunci tanda tangan hilang = rantai lama tak terverifikasi (cantumkan `keyFingerprint()` di laporan).
- Fase 7: `ReportGenerator` (app/data/report) menulis `filesDir/reports/<trip>/` (PDF, manifest, 2 CSV) lewat `ReportPdfWriter`; manifest kanonik di `domain/report` (DECISIONS 79-88). Verifikator: `pip install -r tools/requirements.txt`, `pytest tools/tests`; fixture Kotlin dibuat ulang dengan `FROSTLINE_UPDATE_FIXTURES=1 ./gradlew :domain:cleanTest :domain:test`. Jebakan: disk penuh membuat Gradle gagal ('No space left'), pakai `--no-build-cache`; emulator lama bisa macet (adb shell tidak menjawab), `ReportInstrumentedTest` belum pernah jalan.
- Fase 8 (audit): `docs/AUDIT.md`, tes penjaga `RepoGuardsTest`, `ReadingRepository.append` mengembalikan pembacaan tersimpan (cap waktu dinaikkan di atas event custody, DECISIONS 89). Rilis memakai R8 dan tidak ditandatangani (kunci milik pemilik, jangan commit). Jebakan: emulator lama bisa macet (`adb emu kill` lalu boot ulang); pakai venv di luar repo untuk pytest. Sisa pekerjaan ada di `docs/BLOCKERS.md` dan `docs/HARDWARE_TEST.md`.
- Belum ada git remote; CI belum pernah jalan (tanpa emulator, androidTest hanya lokal).
