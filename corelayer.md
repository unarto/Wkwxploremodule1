# 📋 LAPORAN AUDIT CORE LAYER (`corelayer.md`)

## 1. RINGKASAN EKSEKUTIF
- **Total Modul Diaudit**: 5 (`:core-utils`, `:core-utils-ui`, `:core-storage-api`, `:core-storage`, `:core-worker`)
- **Total Berkas Teranalisis**: 80 berkas (termasuk file sumber Kotlin, AndroidManifest, resources XML, dan unit test).
- **Status Kesehatan Keseluruhan**: **BUTUH PERBAIKAN (MENDESAK DI BEBERAPA SEKTOR KRITIS)**
- **Ringkasan Temuan Kunci**:
  1. **Threading & Coroutine Safety (Tinggi)**: Pada `:core-worker`, `FileCopyService` menggunakan `Job()` alih-alih `SupervisorJob()`, menyebabkan kegagalan satu operasi mematikan seluruh siklus hidup background scope service secara permanen. Terdapat penelanan exception (`catch (_: Exception) {}`) saat aktivasi `startForeground`.
  2. **Spam & Kepatuhan Header (Sedang)**: Ditemukan 9 berkas tanpa header sama sekali, 9 berkas dengan format header usang/salah baris, dan total 18 spam header duplikat di dalam isi method/blok kode pada `FileIndexDao.kt`, `FileIndexRepositoryImpl.kt`, `StorageBackendClassifier.kt`, dan `FileIcon.kt`.
  3. **Alokasi Memori & GC Churn (Sedang)**: Pada `:core-utils`, `DateFormatter` dan `ByteFormatter` mengalokasikan instance `SimpleDateFormat`, `DecimalFormat`, dan `DecimalFormatSymbols` baru pada setiap pemanggilan fungsi tanpa caching atau reusable formatter.
  4. **SQL LIKE Wildcard Leak (Sedang)**: Query pencarian dan penghapusan hierarki pada `FileIndexDao.kt` tidak meng-escape karakter `%` dan `_` pada `locationPrefix`, berpotensi menyebabkan salah hapus atau salah tangkap path berkas.
  5. **Inkonsistensi Struktur Root Source (Rendah)**: Modul `:core-utils` dan `:core-utils-ui` menggunakan direktori `src/main/java/`, sedangkan modul `:core-storage-api`, `:core-storage`, dan `:core-worker` menggunakan `src/main/kotlin/`.

---

## 2. DETAIL AUDIT PER MODUL

### A. Modul `:core-utils`
- **Path Direktori**: `core-utils/src/main/java/com/wakwau/xplore/core/utils/`
- **Kesesuaian Paket & Struktur**: Sangat Bersih. Tidak ada lagi redundansi paket `utils.util`. Struktur folder terbagi menjadi `formatter/` dan `mime/`.
- **Kepatuhan Header (Baris 1 & 2)**: 100% Patuh pada baris 1 & 2 di ketiga berkas (`ByteFormatter.kt`, `DateFormatter.kt`, `MimeTypeDetector.kt`).
- **Performa & Alokasi Memori**:
  - `DateFormatter.kt`: `SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())` dan `SimpleDateFormat("dd/MM/yy", Locale.getDefault())` diinstansiasi ulang pada setiap eksekusi fungsi. Pada saat rendering daftar file yang panjang (ribuan baris pada dual-pane UI), ini menimbulkan alokasi memori berlebih dan GC pauses.
  - `ByteFormatter.kt`: `DecimalFormat("0.#", DecimalFormatSymbols(Locale.US))` dan `DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))` selalu diinstansiasi ulang per panggilan `formatBytesShort` dan `formatDetailed`.
- **Threading & Safety**: Fungsi-fungsi utilitas berbasis `object` stateless murni sehingga aman dari thread collision, namun `SimpleDateFormat` pada dasarnya tidak thread-safe jika nanti dijadikan instance bersama tanpa isolasi thread (perlu pertimbangan `ThreadLocal` atau `java.time.format.DateTimeFormatter`).
- **Temuan Spesifik**:
  - Di `MimeTypeDetector.kt` baris 69, terdapat `try { URLConnection.guessContentTypeFromName(fileName) } catch (t: Throwable) { null }` yang menangkap `Throwable` (seharusnya `Exception`).

---

### B. Modul `:core-utils-ui`
- **Path Direktori**: `core-utils-ui/src/main/java/com/wakwau/xplore/core/utils/ui/`
- **Kesesuaian Paket & Struktur**: Sesuai. Komponen UI, theme, dan delegasi formatter terkelola dengan rapi.
- **Kepatuhan Header (Baris 1 & 2)**:
  - **Hilang Total**: 5 file theme (`Type.kt`, `Theme.kt`, `Color.kt`, `XPloreColorScheme.kt`) dan 1 file komponen (`FileIcon.kt`) tidak memiliki header baris 1 & 2 sama sekali.
  - **Salah Posisi**: `StorageDiskBar.kt` menempatkan header di baris 3 & 4 setelah deklarasi package.
  - **Spam Header**: `FileIcon.kt` memiliki komentar header duplikat yang disisipkan di baris 16, baris 34, dan baris 151 di tengah-tengah kode Composable.
- **Komponen UI & Lifecycle**:
  - `FileIcon.kt`: Parameter `extension: String = ""` didefinisikan pada composable `FileIcon` namun tidak pernah digunakan dalam logika render ikon.
  - `StorageDiskBar.kt`: Menggunakan persentase float (0.0f - 1.0f) yang membutuhkan konversi hati-hati terhadap data model storage space.
- **Temuan Spesifik**:
  - File theme belum memiliki dokumentasi standardisasi warna dan tipografi sesuai aturan header proyek.

---

### C. Modul `:core-storage-api`
- **Path Direktori**: `core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/`
- **Kesesuaian Paket & Struktur**: Bersih, terorganisir ke dalam sub-paket (`constant`, `filesystem`, `model`, `operation`, `permission`, `preferences`, `provider`, `repository`, `search`, `metadata`, `checksum`). Namun, menggunakan folder `src/main/kotlin/` (berbeda basis dengan `:core-utils` yang menggunakan `src/main/java/`).
- **Kepatuhan Header (Baris 1 & 2)**:
  - **Hilang Total**: `StorageSpaceInfo.kt`, `StorageLocation.kt`, dan `FileOperationResult.kt` tidak memiliki header pada baris 1 & 2.
  - **Salah Posisi**: `FilePreferencesState.kt`, `AppLanguage.kt`, dan `FileOperationProgress.kt` meletakkan komentar header di baris 3 & 4 (setelah deklarasi `package`).
  - **Spam Header**: `StorageBackendClassifier.kt` memiliki duplikasi header komentar di baris 13-14 sebelum deklarasi fungsi `classify`.
- **Desain Kontrak / Interface**:
  - Kontrak antarmuka file system (`FileSystemContract`, `SafFileSystemContract`, `LocalFileSystemContract`, dll.) terdefinisi dengan prinsip segregasi yang sangat baik.
  - Pada `FileOperationResult.kt`, varian `object Cancelled` sebaiknya dimutakhirkan menjadi `data object Cancelled` (standar Kotlin modern).
- **Path & Type Safety**:
  - `StorageLocation.kt`: Model data primitif tanpa mekanisme sanitasi string path (rentan terhadap variasi separator Windows vs Unix, trailing slash ganda, atau path kosong).
  - `StorageBackendClassifier.kt`: Metode `isAndroidDataOrObb` mengasumsikan segment path tertentu, namun memerlukan pengujian menyeluruh terhadap path case-insensitive pada berbagai versi Android ROM OEM.
- **Temuan Spesifik**:
  - `DetailedMetadataReader.kt` dan `FileChecksumReader.kt` mendefinisikan kontrak I/O; implementasinya harus menjamin penutupan stream (`.use {}`).

---

### D. Modul `:core-storage`
- **Path Direktori**: `core-storage/src/main/kotlin/com/wakwau/xplore/core/storage/`
- **Kesesuaian Paket & Struktur**: Sesuai modularitas Room dan MMKV.
- **Kepatuhan Header (Baris 1 & 2)**:
  - **Hilang Total**: `DatabaseProvider.kt` tidak memiliki header pada baris 1 & 2.
  - **Format Usang**: `AppPreferences.kt`, `AppDatabase.kt`, `FileIndexDao.kt`, dan `FileIndexEntity.kt` menggunakan format lama `// [Jalur Class]: ...` tanpa mencantumkan modul dan path file lengkap.
  - **Spam Header Ekstrem**:
    - `FileIndexDao.kt`: Memuat **7 blok spam header** di atas fungsi `@Transaction` (baris 53, 62, 72, 82, 94, 106, 114).
    - `FileIndexRepositoryImpl.kt`: Memuat **7 blok spam header** di atas fungsi override transaksi (baris 41, 47, 53, 63, 69, 75, 81).
- **Database & Persistence (Room & MMKV)**:
  - `FileIndexDao.kt`: Query SQL `filePath LIKE :locationPrefix || '%'` rentan karena tidak meng-escape karakter `%` dan `_`. Bila direktori bernama `project_folder`, karakter `_` akan dianggap sebagai wildcard single character dalam SQL LIKE.
  - `DatabaseProvider.kt`: Menggunakan `.fallbackToDestructiveMigration()`. Aman untuk file cache index sementara, namun perlu diberi anotasi atau dokumentasi arsitektur agar tidak menimbulkan data loss yang tidak diinginkan.
  - `FileIndexMapper.kt`: Mengimpor library yang tidak pernah digunakan (`FileItem`, `FileType`, `MimeTypeDetector`, `java.util.Locale`).
  - `AppDatabase.kt`: Memiliki unused imports (`Context`, `Room`, `StorageConstants`).
- **Threading & Coroutine Dispatchers**:
  - `AppPreferences.kt`: Method `suspend fun setSortOrder`, `setThemeMode`, dll. melakukan operasi sinkronus MMKV C++ `mmkv.encode()` langsung di thread pemanggil tanpa membungkusnya dengan `withContext(Dispatchers.IO)`.
- **Temuan Spesifik**:
  - `StorageErrorMapperImpl.kt`: Pemetaan `Throwable` ke domain error tidak mendeteksi `CancellationException`. Jika `CancellationException` tidak dilempar ulang oleh caller, cancellation flow pada coroutine bisa terganggu.

---

### E. Modul `:core-worker`
- **Path Direktori**: `core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/`
- **Kesesuaian Paket & Struktur**: Sangat terpisah dari UI layer. Pemisahan tanggung jawab antara `FileCopyService`, `FileOperationIntentParser`, `FileOperationNotificationManager`, dan `FileCopyServiceClient` berjalan dengan baik.
- **Kepatuhan Header (Baris 1 & 2)**: Semua berkas di modul ini sudah memiliki header baris 1 & 2 dengan benar.
- **Service & WorkManager**:
  - `FileCopyService.kt` (**KRITIS**):
    ```kotlin
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    ```
    Menggunakan `Job()` biasa alih-alih `SupervisorJob()`. Jika satu tugas transfer file mengalami unhandled exception atau dibatalkan secara abnormal, seluruh `serviceScope` akan berstatus *Cancelled* dan service tidak dapat lagi meluncurkan coroutine baru selama proses hidupnya.
  - `FileCopyService.kt` (**KRITIS**): Di baris 81 terdapat blok penelanan exception:
    ```kotlin
    try {
        startForeground(...)
    } catch (_: Exception) {}
    ```
    Pada Android 12+ (API 31+), kegagalan memulai foreground service memicu `ForegroundServiceStartNotAllowedException`. Menelan exception ini tanpa penanganan fallback akan membiarkan service berjalan di background dan dibunuh paksa oleh OS (ANR / Crash).
  - `FileCopyService.kt`: Pada fungsi `createTargetLocation`, destinasi bertipe SAF (`content://`) hanya dikembalikan mentah tanpa pengikatan nama berkas baru atau pembuatan dokumen anak, sehingga transfer berkas ke SAF dapat kehilangan nama/sub-target berkas.
  - `FileCopyService.kt`: Perhitungan persentase byte `(p.bytesWritten.toFloat() / p.totalBytes)` berpotensi kehilangan presisi pada berkas berukuran gigabyte karena mantissa `Float` 32-bit (24-bit precision).
- **Exception Handling & Stream Safety**:
  - Delegasi progress di `DefaultFileOperationProgressDispatcher.kt` menggunakan `MutableSharedFlow` dengan buffer 64 yang aman dan tidak memblokir IO worker.

---

## 3. MATRIKS TEMUAN & DAFTAR FILE BERMASALAH

| No | Modul | File | Kategori Masalah | Deskripsi Masalah | Prioritas | Status |
|---|---|---|---|---|---|---|
| 1 | `:core-worker` | `service/FileCopyService.kt` | Coroutine & Stability | Menggunakan `Job()` bukan `SupervisorJob()`. Satu kegagalan membatalkan scope service secara permanen. | **Tinggi** | **SELESAI** |
| 2 | `:core-worker` | `service/FileCopyService.kt` | Lifecycle & Stability | `catch (_: Exception) {}` menelan error `startForeground`, rentan ANR/terminasi OS pada Android 12+. | **Tinggi** | **SELESAI** |
| 3 | `:core-worker` | `service/FileCopyService.kt` | Data Safety & Logic | `createTargetLocation` tidak memformat URI SAF untuk berkas tujuan (mengembalikan URI direktori secara mentah). | **Tinggi** | **SELESAI** |
| 4 | `:core-storage` | `db/dao/FileIndexDao.kt` | SQL / Data Safety | `LIKE :locationPrefix || '%'` tanpa escape karakter wildcard (`_` dan `%`), berisiko false match query & delete. | **Sedang** | **SELESAI** |
| 5 | `:core-storage` | `db/dao/FileIndexDao.kt` | Kebersihan Kode | 7 blok spam header duplikat di dalam interface DAO (baris 53, 62, 72, 82, 94, 106, 114). | **Sedang** | **SELESAI** |
| 6 | `:core-storage` | `db/repository/FileIndexRepositoryImpl.kt` | Kebersihan Kode | 7 blok spam header duplikat di atas fungsi override repository (baris 41, 47, 53, 63, 69, 75, 81). | **Sedang** | **SELESAI** |
| 7 | `:core-utils` | `formatter/DateFormatter.kt` | Performa & GC | Alokasi ulang `SimpleDateFormat` dan `Locale` pada setiap pemanggilan method pemformatan. | **Sedang** | **SELESAI** |
| 8 | `:core-utils` | `formatter/ByteFormatter.kt` | Performa & GC | Alokasi ulang `DecimalFormat` dan `DecimalFormatSymbols` pada setiap pemanggilan method pemformatan. | **Sedang** | **SELESAI** |
| 9 | `:core-utils-ui` | `components/FileIcon.kt` | Kebersihan Kode | Header baris 1 & 2 hilang, dan terdapat 3 blok spam header di dalam body Composable (baris 16, 34, 151). | **Sedang** | **SELESAI** |
| 10 | `:core-utils-ui` | `theme/Type.kt` | Kepatuhan Header | Header baris 1 & 2 tidak ada (langsung deklarasi package). | **Sedang** | **SELESAI** |
| 11 | `:core-utils-ui` | `theme/Theme.kt` | Kepatuhan Header | Header baris 1 & 2 tidak ada (langsung deklarasi package). | **Sedang** | **SELESAI** |
| 12 | `:core-utils-ui` | `theme/Color.kt` | Kepatuhan Header | Header baris 1 & 2 tidak ada (langsung deklarasi package). | **Sedang** | **SELESAI** |
| 13 | `:core-utils-ui` | `theme/XPloreColorScheme.kt` | Kepatuhan Header | Header baris 1 & 2 tidak ada (langsung deklarasi package). | **Sedang** | **SELESAI** |
| 14 | `:core-storage-api` | `model/StorageSpaceInfo.kt` | Kepatuhan Header | Header baris 1 & 2 tidak ada (langsung deklarasi package). | **Sedang** | **SELESAI** |
| 15 | `:core-storage-api` | `model/StorageLocation.kt` | Kepatuhan Header | Header baris 1 & 2 tidak ada (langsung deklarasi package). | **Sedang** | **SELESAI** |
| 16 | `:core-storage-api` | `operation/FileOperationResult.kt` | Kepatuhan Header | Header baris 1 & 2 tidak ada (langsung deklarasi package). | **Sedang** | **SELESAI** |
| 17 | `:core-storage` | `db/DatabaseProvider.kt` | Kepatuhan Header | Header baris 1 & 2 tidak ada (langsung deklarasi package). | **Sedang** | **SELESAI** |
| 18 | `:core-utils-ui` | `components/StorageDiskBar.kt` | Kepatuhan Header | Format header salah posisi (terletak di baris 3 & 4 setelah package). | **Rendah** | **SELESAI** |
| 19 | `:core-storage-api` | `preferences/FilePreferencesState.kt` | Kepatuhan Header | Format header salah posisi (terletak di baris 3 & 4 setelah package). | **Rendah** | **SELESAI** |
| 20 | `:core-storage-api` | `preferences/AppLanguage.kt` | Kepatuhan Header | Format header salah posisi (terletak di baris 3 & 4 setelah package). | **Rendah** | **SELESAI** |
| 21 | `:core-storage-api` | `operation/FileOperationProgress.kt` | Kepatuhan Header | Format header salah posisi (terletak di baris 3 & 4 setelah package). | **Rendah** | **SELESAI** |
| 22 | `:core-storage-api` | `filesystem/StorageBackendClassifier.kt` | Kebersihan Kode | Terdapat spam header di baris 13-14 di atas fungsi classify. | **Rendah** | **SELESAI** |
| 23 | `:core-storage` | `preferences/AppPreferences.kt` | Kepatuhan Header | Format lama `// [Jalur Class]: ...` tanpa `/Modul` dan path lengkap. | **Rendah** | **SELESAI** |
| 24 | `:core-storage` | `db/AppDatabase.kt` | Kepatuhan Header & Lint | Format header lama dan unused imports (`Context`, `Room`, `StorageConstants`). | **Rendah** | **SELESAI** |
| 25 | `:core-storage` | `db/entity/FileIndexEntity.kt` | Kepatuhan Header | Format header lama `// [Jalur Class]: ...`. | **Rendah** | **SELESAI** |
| 26 | `:core-storage` | `db/mapper/FileIndexMapper.kt` | Lint & Kebersihan | Unused imports (`FileItem`, `FileType`, `MimeTypeDetector`, `Locale`). | **Rendah** | **SELESAI** |
| 27 | `:core-storage` | `preferences/AppPreferences.kt` | Threading | MMKV sinkronus write dipanggil dari coroutine tanpa `withContext(Dispatchers.IO)`. | **Rendah** | **SELESAI** |
| 28 | `:core-worker` | `service/FileCopyService.kt` | Precision | Pembagian persentase transfer byte menggunakan `Float` alih-alih `Double`. | **Rendah** | **SELESAI** |
| 29 | Seluruh Modul | Modul `:core-*` | Struktur Direktori | Inkonsistensi `src/main/java/` (pada utils) vs `src/main/kotlin/` (pada storage & worker). | **Rendah** | TERTUNDA |

---

## 4. REKOMENDASI RENCANA AKSI (ACTION PLAN)

### 🛑 CATATAN GUARDRAIL:
Sesuai aturan eksekutif **READ-ONLY MODE**, rencana aksi di bawah ini **HANYA BERUPA USULAN** dan **TIDAK AKAN DIJALANKAN** sampai pengguna memberikan instruksi dan persetujuan tertulis secara eksplisit.

- **Fase 1: Penanganan Isu Kritis (Stabilitas & Coroutine Safety)**:
  1. Perbaiki `serviceJob = SupervisorJob()` pada `FileCopyService.kt` agar crash satu operasi tidak melumpuhkan service scope.
  2. Tangani exception foreground service dengan logging eksplisit dan fallback yang aman alih-alih empty catch block.
  3. Perbaiki resolusi target path SAF pada `FileCopyService.createTargetLocation`.

- **Fase 2: Perbaikan SQL Safety & Optimasi Performa**:
  1. Tambahkan escape utility untuk SQL `LIKE` query di `FileIndexDao.kt` guna mencegah penafsiran wildcard pada karakter `_` dan `%`.
  2. Terapkan reusable thread-safe formatter atau cache pattern pada `DateFormatter.kt` dan `ByteFormatter.kt` guna memangkas GC churn saat scrolling list.
  3. Ganti pembagian byte ke `Double` di `FileCopyService.kt` untuk mencegah rounding error pada file besar (> 16 MB).

- **Fase 3: Pembersihan Spam Header & Kepatuhan Baris 1 & 2**:
  1. Bersihkan seluruh 18 spam header di dalam body method pada `FileIndexDao.kt`, `FileIndexRepositoryImpl.kt`, `StorageBackendClassifier.kt`, dan `FileIcon.kt`.
  2. Tambahkan header resmi baris 1 & 2 pada 9 berkas yang belum memiliki header (`Theme.kt`, `Color.kt`, `Type.kt`, `XPloreColorScheme.kt`, `FileIcon.kt`, `StorageSpaceInfo.kt`, `StorageLocation.kt`, `FileOperationResult.kt`, `DatabaseProvider.kt`).
  3. Benahi penempatan dan format header pada 9 berkas lainnya yang posisinya terbalik atau menggunakan format lama.
  4. Bersihkan unused imports di `FileIndexMapper.kt` dan `AppDatabase.kt`.
