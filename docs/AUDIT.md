# Audit Fase 8

Auditor independen, tanpa mempercayai klaim di `PROGRESS.md`. Setiap butir bertanda **PASS**, **FAIL** (ditemukan dan diperbaiki, lihat kolom Akhir), atau **HW** (hanya bisa dibuktikan di perangkat fisik atau lingkungan yang tidak tersedia, dicatat di `BLOCKERS.md`). Kolom "Awal" adalah hasil audit sebelum perbaikan, "Akhir" sesudahnya. Tanggal: 2026-11-17.

## A. Bukti dasar (perintah nyata)

| Perintah | Hasil |
|---|---|
| `./gradlew clean` lalu `:domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease` (awal) | BUILD SUCCESSFUL. `:domain:test` 105 lulus, `:app:testDebugUnitTest` 119 lulus, 0 gagal. Lint: 1 temuan (`UnusedResources`, string `placeholder_coming_soon`, akibat temuan F2). |
| Release awal | `isMinifyEnabled = false`: R8 TIDAK aktif (temuan F5). |
| `pytest tools/tests` (venv baru) | 20 lulus. |
| `adb devices` / `emulator -list-avds` | `emulator-5554` terdaftar tetapi `adb shell` tidak menjawab (timeout 15 detik): proses qemu lama macet. Proses itu dibiarkan, jadi `connectedDebugAndroidTest` TIDAK dijalankan (butir ENV-01 di bawah). AVD `Vassel_Pixel3a_API34` ada. |
| Verifikasi akhir | Lihat bagian E. |

## B. Temuan (semua diperbaiki di Tahap C)

| ID | Temuan | Bukti awal | Perbaikan dan tes penjaga |
|---|---|---|---|
| F1 | **Integritas, balapan dan jam mundur.** Pembacaan yang dicap sebelum event custody tetapi disimpan sesudahnya (atau jam dinding diputar mundur) masuk ke `readings_digest` event itu saat diverifikasi ulang (`verify_report.py:198` memakai `ts <= ts event`), sehingga rantai yang jujur gagal diverifikasi. `RoomReadingRepository.append` tidak melihat event custody. | `Repositories.kt` (sebelum): `insert` langsung. Jendela milidetik antara `clock.nowUtcMs()` di `MonitorLoop.onSample` dan insert di dalam `LogReading`. | `ReadingRepository.append` mengembalikan pembacaan yang tersimpan, timestamp dinaikkan ke `ts event terakhir + 1` dalam satu transaksi Room (`Repositories.kt:59`, `Daos.kt:67`). Tidak ada data dibuang (membuang akan mematikan alarm saat jam diputar mundur); lompatan jam tetap terdeteksi oleh `ClockJumpDetector`. `LogReading` menganalisis pembacaan sebagaimana tersimpan (`UseCases.kt:81`). Tes: `CustodyFlowTest.readingAtOrBelowACustodyEventIsRestampedAboveItAndTheDigestStillMatches`, `UseCasesTest.logReadingAnalysesTheReadingAsTheRepositoryStoredIt`. |
| F2 | Kode mati: `PlaceholderScreen` tidak dipakai di mana pun (aturan 6.9). | `grep PlaceholderScreen` hanya di berkasnya sendiri; lint `UnusedResources`. | Berkas dan string dihapus. |
| F3 | String UI hardcoded `"°C"` di `SettingsScreen` dan `Components` (aturan 2). | grep `Text("`. | String `unit_celsius`. Tes: `RepoGuardsTest.composeAndNotificationCodeHaveNoHardcodedUserFacingText`. |
| F4 | Klaim berlebihan: label status `AMAN` menyiratkan vaksin aman, padahal aplikasi hanya tahu suhu di dalam rentang. | `strings.xml` `status_safe`. | Teks menjadi `DALAM RENTANG`. Tes: `RepoGuardsTest.safeStatusLabelDoesNotClaimTheVaccineIsSafe`. |
| F5 | Build rilis tanpa R8 dan tanpa shrink resource; tidak ada aturan keep untuk SQLCipher (JNI memanggil kelas Java via nama). | `app/build.gradle.kts` `isMinifyEnabled = false`. | `isMinifyEnabled = true`, `isShrinkResources = true`, `app/proguard-rules.pro`. `assembleRelease` hijau, `mapping.txt` menyimpan `net.zetetic.*` tanpa penggantian nama. Tidak ada signing config (APK `unsigned`). Tes: `RepoGuardsTest.releaseBuildRunsR8WithResourceShrinkingAndKeepsSqlCipher`. Perilaku SQLCipher pada APK rilis: HW-08. |
| F6 | Layar Detail menulis "Rantai custody valid" untuk perjalanan tanpa event (rantai kosong valid secara hampa). | `TripDetailScreen.kt` (sebelum). | String `detail_chain_empty` ("Belum ada event custody"). |
| F7 | `ReportActions` memakai `collectAsState` (tidak sadar siklus hidup) padahal layar lain memakai `collectAsStateWithLifecycle`. | `ReportActions.kt:35` (sebelum). | Diganti. |
| F8 | Tidak ada tes yang menjaga scope: izin manifest, komponen exported, larangan `!!`/`GlobalScope`/`runBlocking`, em dash, klaim WHO PQS. | Tidak ada tes. | `RepoGuardsTest` (7 tes). |

## C. Audit sistematis

### C1. Keterlacakan (butir Wajib 1-9)

| ID | Butir | Awal | Akhir | Kode produksi | Test |
|---|---|---|---|---|---|
| T-01 | 1 Pairing BLE ESS | PASS | PASS | `data/ble/Ess.kt`, `BleEssSensorAdapter.kt`, `ui/screens/ScanScreen.kt` | `EssTest`, `BackoffTest`, `BlePermissionsTest`, `ViewModelsTest` |
| T-02 | 2 Pencatatan kontinu, service | PASS | PASS | `service/FrostlineMonitorService.kt:27` (START_STICKY, pemulihan dari DB), `MonitorLoop.kt` | `MonitorLoopTest` (12), `MonitorFlowTest` (androidTest, ditulis; dijalankan Fase 6) |
| T-03 | 3 MKT, excursion, alarm | PASS | PASS | `domain/engine/*`, `notification/AlarmNotifier.kt` | `MktCalculatorTest`, `ExcursionEngineTest`, `AuditScenarioTest` (baru) |
| T-04 | 4 NFC, custody | PASS | PASS | `data/nfc/*`, `HandoverController.kt`, `domain/usecase/UseCases.kt` | `NdefCodecTest`, `HandoverControllerTest`, `CustodyFlowTest`, `HashChainTest` |
| T-05 | 5 Room + SQLCipher, kunci | PASS | PASS | `data/db/*`, `data/security/*` | `DatabaseKeyManagerTest`, `TriggerTest`, `RoomRepositoriesTest`; androidTest `EncryptedDatabaseTest`, `KeystoreTest`, `MigrationTest` |
| T-06 | 6 Linimasa | PASS | PASS | `domain/timeline/TripTimeline.kt`, `TripDetailScreen.kt` | `TripTimelineTest` |
| T-07 | 7 PDF, manifest, CSV, verifikator | PASS | PASS | `domain/report/*`, `data/report/*`, `tools/verify_report.py` | `ReportManifestTest`, `CanonicalJsonTest`, `CsvExportTest`, `ManifestFixtureTest`, `ReportGeneratorTest`, `tools/tests/test_verify_report.py` (20) |
| T-08 | 8 Simulator | PASS | PASS | `app/src/debug/.../FakeSensorAdapter.kt`, `DebugSensorBackend.kt` | `MonitorLoopTest`, `ViewModelsTest` |
| T-09 | 9 Tulis tag NFC | PASS | PASS | `data/nfc/NfcWriter.kt`, `WriteTagScreen.kt` | `NdefCodecTest`, `FormsAndStepsTest` |
| T-10 | Tabel pemetaan `PROGRESS.md` | PASS | PASS | 67 nama test di tabel dicek satu per satu terhadap sumber: semuanya ada. | (skrip audit) |
| T-11 | Penjaga scope (izin, exported, larangan konstruk, em dash, klaim) | FAIL (F8) | PASS | - | `RepoGuardsTest` |

### C2. Aturan SPEC.md 5.1 sampai 5.3

| ID | Aturan | Awal | Akhir | Kode | Test |
|---|---|---|---|---|---|
| R-01 | 5.1 rumus MKT, ΔH 83144, R 8,3144, bobot durasi | PASS | PASS | `MktCalculator.kt:14-31` | `constant5CGivesFive`, `mixedCaseMatchesHandCalculation`, `AuditScenarioTest` |
| R-02 | 5.1 MKT >= rata-rata berbobot | PASS | PASS | idem | `mktIsNeverBelowWeightedMeanRandomised` (250 kasus) |
| R-03 | 5.1 gap `max(3 x interval, 300 s)` tidak diberi bobot | PASS | PASS | `Weighting.kt:10` | `gapIntervalIsExcludedFromWeighting`, `AuditScenarioTest.aGapIsExcludedFromTheWeighting` |
| R-04 | 5.1 kosong dan satu data tanpa NaN | PASS | PASS | `MktCalculator.kt` (`weighted` fallback) | `emptyAndSingleReadingAreSafe` |
| R-05 | 5.2 buka setelah 2, start = pertama, tutup setelah 2 dengan histeresis 0,2, end = pertama kembali | PASS | PASS | `ExcursionEngine.kt:120,166` | `opensAfterTwoConsecutive...`, `closesAfterTwoReadingsInsideHysteresisBand...`, `AuditScenarioTest.heatThenColdEscalatingToFreezeMatchesAHandTrace` |
| R-06 | 5.2 FREEZE menaikkan record yang sama; HEAT ke COLD = dua record | PASS | PASS | `ExcursionEngine.kt` (`escalate`, cabang `type != null`) | `coldEscalatesToFreezeOnSameRecord`, `heatThenColdMakesTwoRecords` |
| R-07 | 5.2 SensorLost / SensorRecovered | PASS | PASS | `ExcursionEngine.onTick`, `onReading` | `sensorLostAndRecovered`, `lateReadingWithoutTickStillEmitsLostAndRecovered` |
| R-08 | 5.3 `ts_utc_ms` + `elapsed_ms`, deteksi lompatan jam | PASS | PASS | `ClockJumpDetector.kt`, `AndroidClock.kt:10` | `ClockJumpDetectorTest` (4) |
| R-09 | 5.3 kanonik `ts|temp_centi\n`, `readings_digest` | PASS | PASS | `Canonicalizer.kt` | `readingsDigestGolden`, `tempCentiRoundsHalfUpAndHandlesNegatives` |
| R-10 | 5.3 `readings_digest` benar untuk rantai jujur (semua jalur penulisan) | FAIL (F1) | PASS | `Repositories.kt:59` | `readingAtOrBelowACustodyEventIsRestamped...` |
| R-11 | 5.3 kanonik event, hash, tanda tangan | PASS | PASS | `Canonicalizer.kt`, `HashChain.kt`, `KeystoreSigner.kt` | `fullEventGolden`, `validChainPasses`, `changingAnySingleFieldOfAnyEventIsDetected` |
| R-12 | 5.3 PICKUP pertama, DELIVERY menutup | PASS | PASS | `ChainVerifier.kt`, `UseCases.kt` | `firstEventMustBePickup`, `nothingMayFollowDelivery`, `recordRejectsDeliveryAndEventsAfterCompletion` |
| R-13 | 5.3 tanpa UPDATE/DELETE custody, trigger, trip COMPLETED terkunci | PASS | PASS | `DatabaseTriggers.kt:13-20`, `Daos.kt` (hanya `@Insert` dan `@Query SELECT`) | `TriggerTest` (4), `custodyDaoHasNoUpdateOrDeleteMethods`, `EncryptedDatabaseTest.triggersAreActiveInTheEncryptedDatabase` |
| R-14 | 5.3 batas jujur di README (Threat model) | PASS | PASS | `README.md` bagian Threat model | `RepoGuardsTest` (klaim PQS) |

### C3. Scope

| ID | Butir | Awal | Akhir | Bukti |
|---|---|---|---|---|
| S-01 | Tidak ada fitur terlarang (dashboard web, Supabase, geofencing, peta, multi-sensor, akun, analytics, iklan, iOS, multi-bahasa) | PASS | PASS | `git ls-files` dan navigasi (`FrostlineScaffold.kt:78-104`): Onboarding, Beranda, Mulai Trip, Riwayat, Detail, Tulis Tag, Pengaturan, Serah-terima, Pilih Sensor, Panduan Baterai. Layar pendukung (Onboarding, Riwayat, Pengaturan ambang, Panduan Baterai) melayani butir 2, 3, 6, 7 dan keputusan terkunci (profil ambang, catatan bukan standar klinis). Tidak ada `res/values-*`. |
| S-02 | Dependensi hanya yang dipakai | PASS | PASS | Semua alias `libs.*` di `libs.versions.toml` dipakai di `build.gradle.kts`; `gms.location` dipakai `FusedLocationProvider` (lokasi saat tap, butir 4; fallback tanpa GMS di `BACKLOG.md`). Tidak ada Firebase, analytics, atau jaringan (tidak ada izin `INTERNET`). |
| S-03 | Tidak ada kode mati, TODO tanpa BACKLOG | FAIL (F2) | PASS | grep `TODO|FIXME` kosong; `PlaceholderScreen` dihapus; lint `UnusedResources` 0. |
| S-04 | Izin tepat dan minimal | PASS | PASS | `AndroidManifest.xml`: `BLUETOOTH`, `BLUETOOTH_ADMIN` `maxSdkVersion=30` (baris 16,19); `BLUETOOTH_SCAN` `neverForLocation`; lokasi untuk tap NFC (semua versi) dan pemindaian API <= 30; tanpa `INTERNET`, tanpa lokasi latar belakang. Tes: `RepoGuardsTest.manifestRequestsExactlyTheExpectedPermissions...`. |
| S-05 | Simulator tidak ada di rilis | PASS | PASS | Kelas simulator hanya di `app/src/debug`. Pemeriksaan APK rilis: bagian E. |

### C4. Keamanan

| ID | Butir | Awal | Akhir | Bukti |
|---|---|---|---|---|
| K-01 | `allowBackup=false`, `dataExtractionRules` mengecualikan database, file, sharedpref | PASS | PASS | `AndroidManifest.xml:42`; `data_extraction_rules.xml`; `SchemaAndConfigTest.backupIsDisabledAndExtractionRulesExcludeDatabaseAndFiles` |
| K-02 | Hanya `MainActivity` exported; service dan FileProvider tidak | PASS | PASS | `AndroidManifest.xml:52`; `RepoGuardsTest` menghitung `exported="true"` = 1; FileProvider hanya `reports/` (`file_paths.xml`) |
| K-03 | Tidak ada log sensitif | PASS | PASS | grep `Log\.|Timber|println|printStackTrace` di `src/main`, `src/debug`, `domain/src/main`: kosong; `SchemaAndConfigTest.productionSourcesDoNotLog` |
| K-04 | Tidak ada secret di riwayat git | PASS | PASS | `git log --all -p -G'PRIVATE KEY|storePassword|keyPassword|AIza|api_key|password ='`: kosong; tidak ada `.jks/.keystore/local.properties/.apk` di riwayat; 10 commit, satu penulis, tanpa `Co-Authored-By`/footer/URL sesi, tanpa em dash |
| K-05 | Kunci tanda tangan tidak bisa diekspor | PASS | PASS | `KeystoreSigner.kt:70` (`PURPOSE_SIGN` saja, `AndroidKeyStore`), StrongBox lalu TEE (`:73`); StrongBox nyata: HW-05 |
| K-06 | Error path tidak menimpa data | PASS | PASS | `DatabaseKeyManager.kt:13,43`: kunci baru dibuat hanya bila tidak ada file kunci DAN tidak ada database; selainnya dilaporkan. Tidak ada `fallbackToDestructiveMigration` (grep kosong). Penulisan kunci atomik (`:79`). Tes: `DatabaseKeyManagerTest` (8) |
| K-07 | Trigger immutabilitas aktif, tanpa jalur UPDATE/DELETE | PASS | PASS | lihat R-13 |
| K-08 | Pembuatan laporan tidak membocorkan MAC | PASS | PASS | `ReportData.sensorMacHash` saja (`UseCases.kt`); `ReportManifest` tidak memuat alamat |
| K-09 | Verifikator Python mandiri menolak pemalsuan | PASS | PASS | `tools/tests/test_verify_report.py`: 20 lulus termasuk pemalsuan satu karakter |

### C5. Konkurensi dan siklus hidup

| ID | Butir | Awal | Akhir | Bukti |
|---|---|---|---|---|
| L-01 | Lingkup coroutine: tanpa `GlobalScope`/`runBlocking`; service punya `SupervisorJob` yang dibatalkan di `onDestroy` | PASS | PASS | `FrostlineMonitorService.kt:21,54`; `RepoGuardsTest.productionCodeHasNoNonNullAssertions...` |
| L-02 | Watchdog dan pembacaan tidak berlomba pada engine | PASS | PASS | `LogReading` memakai `Mutex` (`UseCases.kt:75`); `MonitorLoop` memanggil lewat objek yang sama |
| L-03 | Seq custody tanpa race | PASS | PASS | seq/prev dibaca di dalam transaksi yang sama dengan insert; `CustodyFlowTest.parallelCustodyEventsGetUniqueSequentialSeqAndAValidChain` (25 paralel) |
| L-04 | Urutan pembacaan vs event custody | FAIL (F1) | PASS | lihat F1 |
| L-05 | GATT ditutup: `awaitClose` menutup sesi, idempoten, callback lambat diabaikan | PASS | PASS | `BleEssSensorAdapter.kt:92,109`; perilaku GATT nyata: HW-01 |
| L-06 | Service dimatikan sistem: START_STICKY, intent null, state dibangun ulang dari DB, kegagalan `startForeground` ditangani | PASS | PASS | `FrostlineMonitorService.kt:27-50`, `ExcursionEngine.rebuild`; `rebuiltStateEqualsLiveState`; `kill -9` terverifikasi di emulator pada Fase 6 (tidak diulang: ENV-01) |
| L-07 | Rotasi layar: state di `ViewModel`/`rememberSaveable`; tanpa `configChanges` | PASS | PASS | tinjauan kode (`MainActivity.kt`, 22 pemakaian `collectAsStateWithLifecycle`/`rememberSaveable`/`SavedStateHandle`); perilaku nyata: HW-07 |
| L-08 | Reader mode NFC hanya saat layar terlihat | PASS | PASS | `NfcReader.kt:49` (`awaitClose` mematikan reader mode); HW-03 |

### C6. Domain (hitung manual terpisah dari tes yang ada)

Skenario dihitung dengan skrip Python mandiri (tanpa kode proyek) dan dijadikan `AuditScenarioTest`:

| ID | Skenario | Hasil hitung | Awal | Akhir |
|---|---|---|---|---|
| D-01 | MKT 4, 6, 9, 5, 3 °C tiap 60 s (3 °C tanpa bobot) | MKT 6,220956 °C, rata-rata 6,0 | PASS | PASS |
| D-02 | Sama, lalu celah 400 s dan dua pembacaan 5 °C | MKT 5,990815 °C, rata-rata 5,8 (celah dikecualikan) | PASS | PASS |
| D-03 | 12 pembacaan 5,0 8,5 9,1 7,9 7,7 7,0 1,5 1,9 -0,5 2,1 2,3 5,0 | HEAT [60 s, 240 s] puncak 9,1; satu record COLD naik ke FREEZE [360 s, 600 s] puncak -0,5; 5 event alarm berurutan; total di luar rentang 420 s | PASS | PASS |

### C7. UI

| ID | Butir | Awal | Akhir | Bukti |
|---|---|---|---|---|
| U-01 | Tanpa string hardcoded | FAIL (F3) | PASS | `RepoGuardsTest.composeAndNotification...`; lint `UnusedResources` 0 |
| U-02 | Aksesibilitas | PASS | PASS | ikon dekoratif `contentDescription = null` selalu bersama teks; grafik dan suhu punya deskripsi semantik (`Components.kt:132`, `TemperatureChart.kt:71`); `ContrastTest`; TalkBack dan font besar: HW-07 |
| U-03 | State kosong dan error tiap layar | PASS | PASS | Riwayat `trips_empty`; Pilih sensor `scan_empty` dan masalah izin/Bluetooth; Detail `detail_missing`/`db_loading`; Serah-terima NFC mati/tanpa NFC/tag asing/lokasi gagal; Database `db_error_*`; Pengaturan `settings_error_*`; Grafik `chart_empty`; Beranda `home_mkt_none` |
| U-04 | Rantai kosong tidak ditulis "valid" | FAIL (F6) | PASS | `TripDetailScreen.kt` |
| U-05 | Observasi layar memakai siklus hidup | FAIL (F7) | PASS | `ReportActions.kt:35` |

### C8. Kejujuran klaim

| ID | Butir | Awal | Akhir | Bukti |
|---|---|---|---|---|
| H-01 | Disclaimer WHO PQS di UI, PDF, README | PASS | PASS | `strings.xml` `home_disclaimer`, `report_pdf_subtitle`, `report_disclaimer_1`; `RepoGuardsTest.everyStringMentioningWhoPqsDeniesBeingAReplacement` |
| H-02 | Tidak menyiratkan tamper-proof | PASS | PASS | `report_disclaimer_2` ("tamper-evident, bukan tamper-proof"); README Threat model |
| H-03 | Label status tidak menyiratkan vaksin aman | FAIL (F4) | PASS | `status_safe` = `DALAM RENTANG` |
| H-04 | Tidak ada klaim perilaku hardware yang belum terbukti | PASS | PASS | `PROGRESS.md` memakai status `SELESAI-PERLU-UJI-PERANGKAT`; README bagian Status |
| H-05 | Build rilis memakai R8 | FAIL (F5) | PASS | `app/build.gradle.kts`, `RepoGuardsTest` |

## D. Butir yang tidak bisa dibuktikan tanpa perangkat atau lingkungan (dicatat di `BLOCKERS.md`)

| ID | Butir | Alasan |
|---|---|---|
| HW-01 | GATT dan pemindaian BLE nyata, ESP32 | tidak ada sensor atau ponsel |
| HW-02 | Service di ponsel nyata: Doze, layar mati lama, OEM agresif | idem |
| HW-03 | Baca dan tulis tag NFC fisik, reader mode saat layar mati | tidak ada tag |
| HW-04 | GPS nyata dan lokasi dalam ruangan | idem |
| HW-05 | Kunci StrongBox/TEE perangkat keras | emulator memakai Keystore perangkat lunak |
| HW-06 | Render PDF asli (`PdfReportRenderer`) dan lembar bagikan | Robolectric tanpa `PdfDocument` native; `ReportInstrumentedTest` belum dijalankan |
| HW-07 | TalkBack, font besar, rotasi nyata | perlu perangkat |
| HW-08 | APK rilis (R8) dengan SQLCipher dan Hilt di perangkat | build rilis lulus, tetapi belum dijalankan |
| ENV-01 | `connectedDebugAndroidTest` (14 tes) pada audit ini | emulator macet dan perlu dimatikan pemilik; dijalankan terakhir pada Fase 6 |

## E. Verifikasi akhir

`./gradlew clean :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest --no-build-cache`: BUILD SUCCESSFUL (5m 53s). `:domain:test` 109 lulus, `:app:testDebugUnitTest` 127 lulus, 0 gagal, lint 0 temuan, 0 warning kompilasi. `pytest tools/tests`: 20 lulus.

Rilis: `app-release-unsigned.apk` (R8 dan shrink resource aktif). `mapping.txt` memuat `net.zetetic.database.sqlcipher.SQLiteConnection` tanpa penggantian nama; `strings classes*.dex | grep -ci "FakeSensorAdapter|SimScenarios|DebugSensorBackend|Simulasi"` = 0 (simulator tidak ada di rilis, S-05). Library native selaras 16 KB (`zipalign -c -P 16` OK, `PT_LOAD p_align = 16384`).

`connectedDebugAndroidTest`: TIDAK dijalankan (ENV-01). androidTest hanya dikompilasi.

## F. Ringkasan hitungan

Butir bertanda (bagian C1 sampai C8): 60. Awal: 51 PASS, 9 FAIL (8 temuan F1 sampai F8; F1 menyentuh dua butir, R-10 dan L-04). Akhir: 60 PASS, 0 FAIL. Butir yang tidak dapat dibuktikan tanpa perangkat atau lingkungan (bagian D): 8 HW dan 1 ENV, semuanya di `BLOCKERS.md`.
