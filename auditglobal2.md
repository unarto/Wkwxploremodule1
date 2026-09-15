# Audit Global Source Code WKW Xplore

Tanggal audit: 2026-09-14
Scope: seluruh source production pada 15 modul yang terdaftar di repository, tidak termasuk `XFiles-1.3.1/`.
Acuan: `struktur.md`, `ownership.md`, dependency Gradle aktual, serta 275 file production yang ditemukan di bawah `*/src/main/`.

## Ringkasan Eksekutif

Audit menemukan **30 temuan terverifikasi**:

| Severity | Jumlah |
|---|---:|
| CRITICAL | 5 |
| HIGH | 8 |
| MEDIUM | 11 |
| LOW | 6 |
| **Total** | **30** |

Risiko terbesar berada pada lifecycle background operation, validasi move direktori, rollback overwrite, dan korelasi completion terhadap mutasi index. Kombinasi masalah tersebut dapat menghasilkan status sukses palsu, index salah, destination parsial, atau kehilangan source/destination lama.

## Metode dan Batasan

- Membaca aturan ownership dan dependency direction dari `struktur.md` dan `ownership.md`.
- Menginventarisasi semua source production, lalu memindai marker sementara, implementasi kosong, broad catch, coroutine scope, raw filesystem API, persistence API, hardcoded path/string, dan declaration yang tampak tidak memiliki call site.
- Semua kandidat dead/unused ditelusuri lagi dengan pencarian nama di seluruh repository. Callback framework dan API yang digunakan test tidak diklasifikasikan sebagai dead code.
- Jalur Copy/Move/Delete ditelusuri dari caller, background client/service, repository, backend Local/SAF/Root/Shizuku, sampai cleanup dan completion event.
- Jalur Create/Rename dan sinkronisasi search index ditelusuri sampai application orchestration dan persistence contract.
- Build dan test tidak mencapai tahap kompilasi karena environment menyediakan Gradle 8.14.4, sementara AGP 9.1.1 mensyaratkan Gradle 9.3.1. Tidak ada konfigurasi Gradle yang diubah.

---

## CRITICAL

### C-01 — Worker dapat mengirim `Completed` setelah cancellation atau exception

**Lokasi:** `core-worker/.../FileCopyService.kt`, `startOperation()` baris 113–184 dan `startResolvedOperation()` baris 191–231.

`isFailedOrCancelled` hanya diubah ketika sebuah `FileOperationResult.Failure/Cancelled` diterima dari flow. Jika coroutine sudah tidak aktif pada pemeriksaan loop, worker mengirim `Cancelled` lalu `break`, tetapi tidak mengubah flag. Jika repository melempar `CancellationException` atau exception lain, exception keluar menuju `finally` sementara flag masih `false`. Kedua `finally` kemudian mengirim `FileOperationResult.Completed(type)`.

**Dampak:** UI menerima Cancelled/exception lalu Completed; application dapat menampilkan sukses, refresh, dan menjalankan sinkronisasi index terhadap operasi yang tidak selesai. Pada Move/Delete batch, sebagian item dapat sudah berubah tetapi seluruh batch dianggap selesai.

### C-02 — Copy direktori dapat menganggap kegagalan listing sebagai direktori kosong

**Lokasi:**
- `file-system/.../local/LocalDirectoryOperationHelper.kt`, `copyDirectoryRecursively()` baris 132–164 (`listFiles() ?: return`).
- `file-system/.../root/RootDirectoryListingHelper.kt`, `copyDirectoryRecursively()` baris 141–163 (`listFiles() ?: return`).
- `file-system/.../saf/SafStreamTransferHelper.kt`, `copyDirectoryRecursively()` baris 77–111 (`listFiles() ?: emptyArray()`).
- `file-system/.../bridge/CrossFilesystemDirectoryTransferHelper.kt`, `listSourceChildren()` baris 105–119 (error SAF dikonversi menjadi array kosong).

Tidak ada perbedaan antara direktori kosong dan kegagalan membaca anak. Flow copy selesai normal setelah destination directory dibuat. Jalur Move hanya memeriksa keberadaan destination directory, lalu dapat melanjutkan penghapusan source.

**Dampak:** pada backend/provider yang mengizinkan penghapusan tree walau listing sebelumnya gagal, Move dapat menghapus source berisi data setelah hanya membuat destination kosong.

### C-03 — SAF Move memvalidasi parent URI, bukan file/direktori target

**Lokasi:** `file-system/.../saf/SafFileSystem.kt`, `move()` baris 194–218.

Destination encoding memakai fragment `#targetName`, tetapi Move menghapus fragment menjadi `cleanDestPath` lalu me-resolve path tersebut. Untuk copy ke directory, hasil resolve adalah parent directory. Kondisi `destDoc.exists()` lulus karena parent ada, dan validasi ukuran file dilewati karena `destDoc.isFile` bernilai false. Source kemudian dihapus.

**Dampak:** kegagalan menghasilkan target yang benar atau target parsial dapat tetap diikuti penghapusan source.

### C-04 — Kegagalan overwrite dapat menghapus destination lama

**Lokasi:**
- `file-system/.../local/LocalStreamTransferHelper.kt` baris 22–70.
- `file-system/.../saf/SafStreamTransferHelper.kt` baris 16–73.
- `file-system/.../root/RootStreamTransferHelper.kt` baris 16–55.
- `file-system/.../shizuku/ShizukuFileTransferHandler.kt` baris 16–64.

Semua helper membuka/truncate target langsung. Pada error/cancellation, cleanup menghapus target tanpa membedakan target baru dengan target yang sudah ada sebelum operasi. SAF secara eksplisit memakai `findFile(displayName)` jika file sudah ada, lalu menghapus object yang sama bila transfer gagal.

**Dampak:** opsi overwrite yang gagal tidak hanya gagal menyalin source, tetapi juga dapat menghilangkan isi destination lama.

### C-05 — Validasi Move direktori lintas backend hanya membuktikan destination “ada”

**Lokasi:** `file-system/.../bridge/CrossFilesystemDirectoryTransferHelper.kt`, `validateTransferComplete()` baris 122–149; dipanggil sebelum `deleteSource()` oleh `CrossFilesystemTransferBridge.moveCross()` baris 86–106.

`expectedSize` dihitung untuk direktori, tetapi branch direktori hanya menguji `destSize < 0`. `getDestFileSize()` mengembalikan `0` untuk directory yang ada, tanpa memvalidasi child count, subtree, atau total byte. Source kemudian dihapus.

**Dampak:** destination directory parsial dianggap valid dan source tree dapat dihapus.

---

## HIGH

### H-01 — Pending mutation index tidak memiliki operation ID dan dapat tertukar

**Lokasi:** `app/.../AppOrchestratorViewModel.kt` baris 39–75 dan 98–166.

Hanya ada satu `pendingTransfers` dan satu `pendingDeletes`. Request baru menimpa request lama. Progress dispatcher dan `Completed` hanya membawa `BackgroundOperationType`, bukan identity/job ID. Selain itu service membatalkan job lama ketika start baru, sehingga event Cancelled/Completed dari job lama dapat membersihkan atau memakai metadata job baru.

**Dampak:** index destination/source yang salah dapat ditambah atau dihapus pada operasi berurutan/bersamaan.

### H-02 — Rename/Move index tidak atomik

**Lokasi:** `search/.../FileIndexSynchronizer.kt` baris 44–61.

Implementasi mengumpulkan destination, memanggil `removeByPrefix(old)`, lalu `syncBatch(new)`. Contract persistence sebenarnya sudah menyediakan operasi `syncRename`, `syncMove`, dan `replacePrefixIndex`, tetapi tidak digunakan untuk subtree ini. Jika batch insert gagal setelah remove sukses, index source hilang dan destination tidak lengkap.

**Dampak:** database index korup/stale setelah error storage atau database.

### H-03 — Rekursi dan materialisasi subtree index berisiko StackOverflow/OOM

**Lokasi:** `search/.../FileIndexSynchronizer.kt` baris 63–73.

`collectSubtree()` rekursif dan membentuk `listOf(root) + descendants` berulang kali. Tidak ada depth guard, batching streaming, cycle detection, atau bounded memory.

**Dampak:** tree besar/dalam dapat menyebabkan alokasi kuadratik, OOM, atau stack overflow setelah operasi fisik sebenarnya sukses.

### H-04 — `StorageVolumeRepositoryImpl` membocorkan receiver dan scope

**Lokasi:** `file-system/.../repository/StorageVolumeRepositoryImpl.kt` baris 21–42.

Constructor langsung membuat `CoroutineScope(ioDispatcher)` dan mendaftarkan `StorageVolumeBroadcastReceiver`, tetapi class/contract tidak menyediakan `close()`, unregister, atau pembatalan scope. Call site membuat repository sebagai singleton aplikasi, tetapi lifecycle ini tetap tidak eksplisit dan tidak aman untuk test/recreation.

**Dampak:** receiver/context dapat tertahan; coroutine tetap hidup; instance tambahan akan mendaftarkan receiver tambahan.

### H-05 — `:core-worker` mengambil alih operation orchestration

**Lokasi:** `core-worker/.../FileCopyService.kt` baris 112–231.

Worker melakukan loop batch, menentukan target path, memilih Copy vs Move vs Delete, menetapkan failure policy, dan menghentikan batch. Ini adalah operation-engine behavior yang menurut `struktur.md`/`ownership.md` dimiliki `:file-operations`; worker semestinya hanya menjalankan contract executor dan lifecycle foreground.

**Dampak:** business rule terduplikasi/tersebar dan sulit diuji tanpa Android Service; behavior foreground dan domain dapat divergen.

### H-06 — Dependency horizontal `:filemanager` → `:file-operations` dan `:search`

**Lokasi:** `filemanager/build.gradle.kts` baris 15–20 serta event/state/use-case yang mengekspos tipe kedua feature.

Dependency aktual berisi `implementation(project(":file-operations"))` dan `api(project(":search"))`, bertentangan dengan graph resmi yang menetapkan `:filemanager → :core-storage-api, :core-utils` dan melarang feature bergantung langsung pada feature lain.

**Dampak:** boundary feature bocor, `:filemanager-ui` memperoleh search transitif, dan perubahan feature lain memaksa recompilation/coupling filemanager.

### H-07 — Kegagalan `startForeground()` ditelan lalu operasi tetap berjalan

**Lokasi:** `core-worker/.../FileCopyService.kt` baris 77–91.

Exception hanya di-log, kemudian parsing dan eksekusi operasi dilanjutkan. Pada Android yang mewajibkan foreground service aktif tepat waktu, proses dapat dibunuh saat mutasi sedang berlangsung.

**Dampak:** operasi terputus, destination parsial, dan status terminal tidak dapat diandalkan.

### H-08 — Copy direktori ke destination existing tidak memiliki rollback transaksi subtree

**Lokasi:** helper rekursif Local/SAF/Root/Shizuku dan `CrossFilesystemTransferBridge.copyDirectoryCrossRecursively()` baris 147–182.

Cleanup hanya diterapkan per file. Directory dan child yang sudah selesai tetap tertinggal jika child berikutnya gagal/cancel. Tidak ada manifest item yang dibuat operasi ini atau rollback yang menjaga pre-existing subtree.

**Dampak:** destination menjadi campuran data lama, data baru, dan partial batch; retry/overwrite dapat menghasilkan state ambigu.

---

## MEDIUM

### M-01 — Search index saat traversal hanya menyimpan item yang cocok query

**Lokasi:** `search/.../FileSystemSearchTraversal.kt` baris 31–69.

`indexSyncBatch.add(item)` berada di dalam `if (filterMatcher.matches(item, query))`. Karena itu pencarian “jpg” tidak mengindeks item lain yang telah ikut ditraversal.

**Dampak:** isi index bergantung pada query terakhir dan bukan representasi filesystem yang konsisten.

### M-02 — Error listing selama search traversal diabaikan

**Lokasi:** `search/.../FileSystemSearchTraversal.kt` baris 37–62.

Hanya `FileOperationResult.Success` yang ditangani. Failure, Cancelled, dan Completed dilewati tanpa error/cancellation event.

**Dampak:** hasil pencarian parsial dilaporkan seolah traversal selesai normal.

### M-03 — Constructor kompatibilitas membuat implementasi `FileRepository` palsu di production

**Lokasi:** `filemanager/.../usecase/CreateDirectoryUseCase.kt` baris 12–30.

Constructor `DirectoryRepository` membungkusnya dalam anonymous `FileRepository`; empat method lain hanya melempar `UnsupportedOperationException`. Call site production saat ini memakai `FileRepository`, sehingga constructor ini bukan kebutuhan runtime aktual.

**Dampak:** fake/no-op production memperbesar surface API dan dapat crash bila object lolos ke penggunaan selain create.

### M-04 — Input Intent malformed dapat menjatuhkan service sebelum failure dikirim

**Lokasi:** `core-worker/.../FileCopyService.kt` baris 70–103 dan `FileOperationIntentParser.kt`.

`BackgroundOperationType.valueOf(typeStr)` dan parsing JSON dilakukan di luar error boundary operasi. Nilai enum/JSON invalid melempar sebelum dispatcher menerima Failure dan sebelum cleanup lifecycle yang konsisten.

**Dampak:** service crash/no terminal result; UI dapat tertahan pada Running.

### M-05 — Application singleton menyimpan callback ViewModel tanpa detach

**Lokasi:** `AppOrchestratorViewModel.setExternalDispatch()` baris 87–95 dan `FileManagerPresentationModule.createViewModelFactory()`.

Application-level orchestrator menyimpan method reference `DualPaneViewModel::dispatch`. Tidak ada clear ketika DualPaneViewModel dihapus. Reference baru hanya mengganti yang lama jika factory membuat instance berikutnya.

**Dampak:** ViewModel/screen lama dapat tertahan setelah navigation; background event dapat dikirim ke state lama.

### M-06 — Satu completion memicu refresh berulang dan concurrent

**Lokasi:** `AppOrchestratorViewModel` baris 64–66 serta `DualPaneViewModel` baris 76–101.

App mengirim Refresh LEFT dan RIGHT setelah Completed. Listener DualPaneViewModel juga mengirim Refresh active panel untuk setiap OperationSuccess. Setiap Refresh meluncurkan directory load dan tree refresh sendiri.

**Dampak:** I/O duplikat dan race “last completion wins” pada state/tree, terutama directory besar.

### M-07 — UI menghitung parent filesystem path

**Lokasi:** `filemanager-ui/.../screen/FileManagerContent.kt` baris 73–104.

Composable mendefinisikan `getParentPath`, memotong string dengan `/`, membuat `StorageLocation`, dan memilih fallback root ID. Logic ini salah untuk SAF URI/fragment dan merupakan navigation/storage business logic di UI.

**Dampak:** Create Folder dapat diarahkan ke parent salah pada backend non-local; boundary UI/domain dilanggar.

### M-08 — Notification text dan diagnostic logging hardcoded di production

**Lokasi:** `core-worker/.../FileCopyService.kt` baris 78, 134–138, dan 210–214.

Teks `"Memproses Berkas..."` tidak memakai Android resource. Log `COPY_REAL_EXEC` memakai level error untuk alur normal dan mencetak source/destination path pengguna.

**Dampak:** tidak terlokalisasi dan path sensitif bocor ke logcat.

### M-09 — Refresh volume tidak memiliki isolation/error state

**Lokasi:** `file-system/.../repository/StorageVolumeRepositoryImpl.kt` baris 30–79.

Scope tidak memakai `SupervisorJob` eksplisit dan `refreshVolumes()` memanggil semua provider tanpa isolasi. Satu provider gagal membuat refresh gagal total; tidak ada error channel dan volume sebelumnya tetap diam-diam.

**Dampak:** satu provider Root/SAF bermasalah dapat menghentikan pembaruan seluruh daftar volume.

### M-10 — Broad `Throwable` catch menyembunyikan error serius

**Lokasi contoh:** `file-system/.../provider/StorageSpaceReader.kt`, `InternalStoragePathResolver.kt`, `ShareableUriResolverImpl.kt`, serta `filemanager-ui/.../detail/AppIntentResolver.kt`.

Beberapa utility menangkap `Throwable` dan mengembalikan null/fallback tanpa membedakan VM `Error`, cancellation, security failure, dan input invalid. Call site tidak mendapat alasan kegagalan.

**Dampak:** OOM/linkage error atau kegagalan konfigurasi dapat disamarkan sebagai “data tidak tersedia”.

### M-11 — Search cancellation dapat didispatch lebih dari sekali

**Lokasi:** `app/.../orchestrator/search/SearchOperationOrchestrator.kt` baris 28–57.

Operator `catch` mengirim `SearchCancelled` lalu rethrow `CancellationException`; outer catch mengirim event yang sama lagi. `cancelSearch()` juga langsung mengirim event setelah cancel job.

**Dampak:** reducer/UI menerima event terminal duplikat dan dapat melakukan cleanup/animation berulang.

---

## LOW

### L-01 — Modul `:file-operations-bridge` kosong

**Lokasi:** `file-operations-bridge/` hanya memiliki `build.gradle.kts`; tidak ada source production maupun test.

Modul masih menjadi dependency `:app` dan `:core-worker`, tetapi tidak menyediakan adapter/contract apa pun.

**Dampak:** build graph dan dokumentasi memberi kesan boundary tersedia padahal implementasinya tidak ada.

### L-02 — Utility production tanpa call site

**Lokasi:**
- `core-utils/.../ByteFormatter.kt`: `formatBytesShort()`.
- `core-utils/.../MimeTypeDetector.kt`: `isTextOrCode()`.
- `core-utils/.../DateFormatter.kt`: `formatTime()` hanya dipakai test.

Pencarian seluruh source production tidak menemukan caller.

**Dampak:** API surface dan maintenance bertambah tanpa kebutuhan production.

### L-03 — `StorageFailure` menjadi hierarchy error kedua yang tidak digunakan

**Lokasi:** `core-storage-api/.../model/StorageFailure.kt`.

Tidak ada production caller; operasi aktual memakai `FileOperationError` + `FileOperationResult`.

**Dampak:** duplicate source of truth untuk error semantics.

### L-04 — Event memakai field dummy agar menjadi data class

**Lokasi:** `filemanager/.../event/DualPaneEvent.kt` baris 88, `ClearSearchHistory(val dummy: Boolean = true)`.

Nilai tidak pernah dibaca dan event secara semantik tidak membawa data.

**Dampak:** placeholder API yang membingungkan; seharusnya event tanpa payload.

### L-05 — Branch `Completed` no-op/semu pada API sinkron

**Lokasi:** `FileTreeEngine.loadChildren()`, `CreateDirectoryOperationHandler`, `RenameOperationOrchestrator`, dan `FileIndexSynchronizer.collectSubtree()`.

Contract sinkron list/create/rename secara nyata menghasilkan Success/Failure/Cancelled; beberapa caller menerima `Completed` sebagai sukses atau no-op walau tidak membawa payload yang dibutuhkan.

**Dampak:** state terminal ambigu dan dapat menghasilkan success tanpa data jika implementation baru mengembalikan Completed.

### L-06 — Error tree tersedia tetapi tidak terhubung ke production UI

**Lokasi:** `TreeNavigationAdapter.getErrorState()` serta `FileTreeEngine.errorState`.

`getErrorState()` tidak memiliki caller production; error load child disimpan oleh engine, tetapi tidak diobservasi screen melalui adapter. Hanya state internal yang berubah.

**Dampak:** error expand tree dapat terlihat sebagai node tidak merespons tanpa pesan/retry.

---

## Validasi Boundary Ringkas

### Sesuai boundary

- Tidak ditemukan `java.io.File`, `java.nio.file`, `DocumentFile`, atau libsu pada domain `:filemanager`, `:file-operations`, dan `:search`.
- Tidak ditemukan Room/MMKV/DAO implementation di luar `:core-storage`; instantiation di `:app` adalah composition-root wiring.
- Tidak ditemukan Compose/ViewModel di `:core-storage-api`, `:core-utils`, `:file-operations`, atau `:search`.
- `:treeview` tidak mengimpor `FileItem`, `StorageLocation`, filesystem, search, atau file-operations.

### Melanggar boundary

- `:filemanager` bergantung horizontal pada `:file-operations` dan `:search` (H-06).
- `:core-worker` mengandung batch/operation orchestration yang seharusnya berada di operation engine (H-05).
- `FileManagerContent` melakukan perhitungan navigation/storage path (M-07).

## Build dan Test

### `gradle testDebugUnitTest`

Status: **NOT RUN sampai tahap test / environment blocked**.

Output utama:

```text
$ gradle testDebugUnitTest
Calculating task graph as no cached configuration is available for tasks: testDebugUnitTest

FAILURE: Build failed with an exception.

* Where:
Build file '/workspace/Wkwxploremodule1/app/build.gradle.kts' line: 1

* What went wrong:
An exception occurred applying plugin request [id: 'com.android.application', version: '9.1.1']
> Failed to apply plugin 'com.android.internal.version-check'.
   > Minimum supported Gradle version is 9.3.1. Current version is 8.14.4.

BUILD FAILED in 1s
```

### `gradle assembleDebug`

Status: **NOT RUN sampai tahap compile / environment blocked**.

Output utama:

```text
$ gradle assembleDebug
Calculating task graph as no cached configuration is available for tasks: assembleDebug

FAILURE: Build failed with an exception.

* Where:
Build file '/workspace/Wkwxploremodule1/app/build.gradle.kts' line: 1

* What went wrong:
An exception occurred applying plugin request [id: 'com.android.application', version: '9.1.1']
> Failed to apply plugin 'com.android.internal.version-check'.
   > Minimum supported Gradle version is 9.3.1. Current version is 8.14.4.

BUILD FAILED in 1s
```

## Prioritas Tindak Lanjut yang Disarankan

1. Perbaiki terminal-state worker (C-01) sebelum perubahan lain karena ini memicu success/index sync palsu.
2. Jadikan copy/move directory fail-closed ketika listing tidak dapat dibaca dan validasi subtree sebelum delete source (C-02, C-03, C-05).
3. Gunakan staging/backup semantics untuk overwrite agar destination lama tidak hilang ketika transfer gagal (C-04).
4. Tambahkan correlation ID pada request/progress/completion sebelum memperbaiki pending index race (H-01).
5. Perbaiki atomicity dan memory behavior sinkronisasi index (H-02, H-03).

Dokumen ini hanya laporan audit. Tidak ada source production, test, atau Gradle yang diperbaiki sebagai bagian dari audit ini.
