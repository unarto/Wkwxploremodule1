# Audit Ulang HIGH-01 s.d. HIGH-08

Tanggal verifikasi: 2026-09-14
Basis verifikasi: source production aktual pada commit `6fb5363` (`Rollback incomplete directory copies`).
Ruang lingkup: verifikasi statis/source; tidak ada source atau Gradle yang diubah oleh audit ini.

## Ringkasan status

| Finding | Status | Ringkasan |
|---|---|---|
| HIGH-01 | **PARTIAL** | Correlation ID mengalir end-to-end dan pending mutation keyed by ID, tetapi enqueue/registration tidak atomik sehingga terminal event yang sangat cepat dapat tiba sebelum metadata dimasukkan. Failure/cancel stale juga masih mengubah UI global. |
| HIGH-02 | **PASS** | Move/Rename menggunakan satu primitive repository yang diteruskan ke satu method DAO `@Transaction`; delete prefix dan seluruh batch insert berada dalam transaction yang sama. |
| HIGH-03 | **PASS** | Traversal index memakai DFS iteratif, batch 500, Flow, pemeriksaan cancellation, dan error listing fail-closed tanpa materialisasi seluruh subtree. |
| HIGH-04 | **PARTIAL** | Receiver sharing dan application context benar, register/unregister simetris per subscription, tetapi lifecycle application-scoped tidak pernah memanggil `close()` pada production composition root; kegagalan unregister juga meninggalkan marker registered. |
| HIGH-05 | **PASS** | Loop, dispatch Copy/Move/Delete, target resolution, resolved conflict, dan stop-on-failure berada di `:file-operations`; worker hanya parse transport, lifecycle, foreground gate, dispatch, dan satu executor call. |
| HIGH-06 | **PASS** | `:filemanager` tidak lagi bergantung pada `:file-operations`/`:search`; production hanya memakai contract search milik `:core-storage-api`. |
| HIGH-07 | **PASS** | `startForeground()` adalah gate langsung sebelum callback executor; failure menghasilkan satu terminal Failure berkorelasi dan cleanup non-cancellable. |
| HIGH-08 | **PARTIAL** | Semua jalur Copy directory memakai staging + publish/backup, tetapi backup destination dihapus sebelum validasi Move berikutnya. Jika validasi Move gagal setelah publish, destination lama tidak dapat direstore. Coverage test rollback juga baru membuktikan jalur Local. |

---

## HIGH-01 — Correlation ID

**Status: PARTIAL**

### Bukti flow aktual

1. `FileCopyServiceClient.enqueueOperation()` dan `enqueueResolvedOperation()` masing-masing membuat UUID, memasukkannya sebagai `KEY_OPERATION_ID` ke Intent, lalu mengembalikan ID yang sama.
2. `FileCopyService.onStartCommand()` membaca `KEY_OPERATION_ID` dan meneruskannya ke `launchOperation(operationId, request)`.
3. Progress diterbitkan sebagai `BackgroundOperationEvent(operationId, result)`; terminal event juga diterbitkan oleh `FileOperationTerminalEmitter` dengan ID yang sama.
4. Orchestrator menerima ID hasil enqueue melalui callback `onEnqueued` dan menyimpan metadata ke `PendingIndexMutations`, sebuah `MutableMap<String, PendingIndexMutation>`.
5. `AppOrchestratorViewModel.syncCompletedOperation()` memanggil `take(operationId, type)`. ID atau tipe yang tidak cocok menghasilkan `null`, sehingga completion stale tidak menjalankan index sync.
6. Failure/cancel memanggil `remove(event.operationId)`, sehingga pending entry lain tidak ikut terhapus.

### Interaksi/regression yang masih ada

* **Race enqueue-versus-terminal:** client menjalankan `startForegroundService()` sebelum mengembalikan ID. Metadata baru ditaruh oleh callback setelah return. Service yang gagal sangat cepat (misalnya dependency/foreground startup gagal) dapat menerbitkan terminal event sebelum `pendingIndexMutations.put()`. Event itu diabaikan, kemudian callback dapat meninggalkan pending metadata tanpa terminal pasangan.
* `Completed` stale memang dihentikan oleh `return@collect`, tetapi stale `Failure`/`Cancelled` tetap mengubah `_operationState` global walaupun `remove(operationId)` mengembalikan `false`. Jadi korelasi melindungi index metadata, tetapi belum sepenuhnya melindungi presentation state dari terminal event stale.
* `FileCopyService` memiliki satu `currentOperationJob`; request baru membatalkan job lama. Ini mencegah eksekusi service overlap dalam satu instance, sementara map tetap mencegah metadata lama/baru saling overwrite. Namun cancellation API tidak menerima operation ID, sehingga ia membatalkan slot aktif, bukan operasi tertentu.

### Kesimpulan

Syarat inti perlindungan index dari ID yang tertukar telah ada, tetapi registration race dan stale failure/cancel UI berarti jaminan end-to-end belum penuh.

---

## HIGH-02 — Atomic Move/Rename index

**Status: PASS**

### Bukti transaction boundary

* `FileIndexSynchronizer.syncMoved()` mencari destination terlebih dahulu, mengecek cancellation, lalu memanggil `fileIndexRepository.replacePrefixIndexBatched(source.path, indexSubtreeBatches(destinationItem))`.
* `syncRenamed()` memakai primitive yang sama untuk old path dan subtree baru.
* `FileIndexRepositoryImpl` hanya memetakan setiap batch domain ke entity dan meneruskan Flow tersebut ke DAO.
* `FileIndexDao.replacePrefixIndexBatched()` diberi `@Transaction`. Di dalam satu boundary ini DAO:
  1. menghapus exact/prefix lama;
  2. menghapus prefix dengan slash;
  3. mengoleksi seluruh batch destination;
  4. memasukkan setiap batch.
* Exception, listing failure, insert failure, atau cancellation selama Flow collection menggagalkan method transaction, sehingga Room melakukan rollback terhadap delete dan batch insert yang sudah terjadi.

### Interaksi/regression

* HIGH-03 mempertahankan atomisitas: Flow traversal bersifat cold dan dikoleksi **di dalam** method DAO `@Transaction`, bukan dipersist lebih dulu di luar transaction.
* Destination root lookup terjadi sebelum transaction. Jika lookup gagal/cancel, old index belum disentuh.
* Tidak ditemukan jalur aktif lain di `FileIndexSynchronizer` untuk Move/Rename yang masih melakukan `removeByPrefix(old)` diikuti insert non-atomik.

---

## HIGH-03 — Bounded iterative index traversal

**Status: PASS**

### Bukti strategi traversal dan memory

* `indexSubtreeBatches()` menggunakan `ArrayDeque<FileItem>` sebagai DFS stack; tidak ada recursive call untuk subtree index.
* Hanya satu stack frontier dan satu `ArrayList` batch berkapasitas `INDEX_BATCH_SIZE = 500` yang dipertahankan. Saat mencapai 500, method emit snapshot batch lalu `clear()`.
* Mapping `FileItem -> FileIndexItem` dilakukan per item saat pop; tidak dibangun list kedua untuk seluruh subtree.
* Cancellation diperiksa pada setiap iterasi traversal dan sebelum setiap persistence batch.
* `DirectoryRepository.list()` Failure menjadi exception, Cancelled menjadi `CancellationException`, dan Completed dianggap state invalid. Tidak ada cabang skip/empty-success untuk listing error.

### Interaksi/regression

* Untuk Move/Rename, batch Flow dikoleksi dalam transaction HIGH-02, sehingga cancellation/listing failure di tengah traversal rollback old prefix.
* Untuk Copy/Create, batch memang dipersist incrementally tanpa transaction subtree penuh; ini tidak mengubah requirement atomic replacement Move/Rename.
* Frontier DFS masih dapat sebesar lebar satu directory yang dikembalikan `DirectoryRepository.list()`, tetapi seluruh descendant tree dan seluruh mapped index tree tidak dimaterialisasi. Batas batch persistence tetap 500.

---

## HIGH-04 — Storage volume lifecycle

**Status: PARTIAL**

### Bukti ownership dan registration path

* Public constructor mengubah context menjadi `context.applicationContext`; `StorageVolumeChangeMonitor` juga menyimpan `context.applicationContext`, sehingga Activity context tidak disimpan.
* `StorageVolumeChangeMonitor.changes` memakai `callbackFlow`: receiver dibuat/register saat upstream dikoleksi dan `awaitClose { receiver.unregister() }` menyediakan cleanup simetris.
* Repository memakai satu `stateIn(repositoryScope, SharingStarted.WhileSubscribed(0), ...)`. Banyak downstream collector berbagi satu upstream subscription, sehingga tidak membuat receiver per collector.
* Ketika collector terakhir berhenti, `WhileSubscribed(0)` membatalkan upstream callbackFlow dan menjalankan unregister. Re-subscribe membuat receiver baru setelah lifecycle subscription lama selesai, bukan menumpuk receiver lama.
* `mapLatest` membatalkan refresh lama ketika event baru masuk. `close()` membatalkan repository scope/job dan dengan demikian upstream receiver/refresh.

### Gap production lifecycle

* `StorageModule` membuat repository sebagai application-scoped lazy singleton, tetapi tidak ditemukan production call yang memanggil `StorageVolumeRepository.close()`. Ownership proses menjelaskan umur panjangnya, namun cleanup eksplisit pada lifecycle owner belum diwiring.
* `StorageVolumeBroadcastReceiver.unregister()` mengosongkan `registeredContext` hanya setelah `context.unregisterReceiver(this)` berhasil. Jika unregister melempar, exception tidak ditelan (baik untuk observability), tetapi marker/context tetap tersimpan dan tidak ada retry/fallback cleanup. Jadi requirement “error tidak menyembunyikan leak” terpenuhi secara fail-visible, tetapi state leak belum dipulihkan.

### Kesimpulan

Multiple collectors dan short-lived context sudah aman; lifecycle cleanup production masih bergantung pada process death atau hilangnya subscriber, bukan owner yang secara eksplisit menutup singleton.

---

## HIGH-05 — Ownership operation orchestration

**Status: PASS**

### Logic owner aktual

`FileOperationExecutor` di module `:file-operations` memiliki:

* pemilihan resolved vs ordinary request;
* validasi destination untuk non-Delete;
* loop multi-source;
* dispatch Delete versus transfer;
* target path resolution (termasuk SAF fragment);
* dispatch Copy versus Move;
* resolved conflict `SKIP` handling;
* stop-on-first Failure/Cancelled policy.

### Responsibility worker setelah refactor

`FileCopyService` hanya:

* membaca Intent/operation ID dan deserialize request transport;
* mengelola Service, notification, foreground startup, cancellation scope, dan cleanup;
* membuat satu `FileOperationExecutor` dan memanggil `execute(request)` sekali;
* meneruskan progress/terminal ke dispatcher berkorelasi.

`FileOperationIntentParser` masih melakukan loop JSON, tetapi loop tersebut hanya deserialisasi transport, bukan business execution. Tidak ditemukan loop source atau Copy/Move/Delete selection lama yang aktif di service.

### Interaksi/regression

* HIGH-01 tetap dipertahankan karena worker membungkus semua progress executor dengan operation ID.
* HIGH-07 tetap dipertahankan karena satu executor call hanya berada dalam callback yang dibuka setelah foreground startup sukses.
* Cancellation dari coroutine worker diteruskan ke executor/repository dan akhirnya terminal emitter; tidak ada catch di executor yang mengubah `CancellationException` menjadi success.

---

## HIGH-06 — Boundary `:filemanager`

**Status: PASS**

### Dependency dan import aktual

* `filemanager/build.gradle.kts` hanya menyatakan `api(:core-storage-api)`, `api(:core-utils)`, coroutines, dan test dependencies.
* Tidak ada dependency `:file-operations` atau `:search`.
* Pencarian production terhadap `filemanager/src/main` tidak menemukan import/package `com.wakwau.xplore.fileoperations` atau `com.wakwau.xplore.search`.
* `DualPaneEvent` masih memakai `FileSearchQuery` dan `SearchTargetType`, tetapi keduanya adalah contract di package `com.wakwau.xplore.core.storage.search` milik `:core-storage-api`, bukan implementasi feature `:search`.

### Interaksi/regression

Browsing, navigation, dan dual-pane state tetap berada di `:filemanager`; app-level orchestrator menjadi composition boundary untuk operation/search implementation. Tidak ditemukan duplicate handler/use case yang dipindah ke UI atau filemanager.

---

## HIGH-07 — Foreground startup gate

**Status: PASS**

### Exact execution gate

1. `FileCopyService.launchOperation()` membangun request/notification/executor lalu memanggil `ForegroundOperationExecutionGate.execute()`.
2. Gate memanggil `startForeground()` terlebih dahulu.
3. Callback `operation`—satu-satunya tempat `executor.execute(request)` dipanggil—baru diteruskan ke terminal emitter setelah `startForeground()` return sukses.
4. Exception startup memanggil logger dan menjalankan terminal emitter dengan `Failure(UNKNOWN)`; cabang tersebut langsung `return`, sehingga executor tidak dijalankan.
5. `finally` menjalankan cleanup di `NonCancellable`, memanggil `stopForeground(true)` dan `stopSelf()`.

### Terminal/cancellation semantics

* `FileOperationTerminalEmitter` menghasilkan tepat satu terminal result dari satu invocation: Completed untuk `null`, returned Failure/Cancelled bila diberikan executor, Cancelled untuk thrown `CancellationException`, atau Failure untuk Exception lain.
* Terminal event selalu membawa operation ID yang diterima gate.
* Cancellation diterbitkan dalam `NonCancellable` lalu exception cancellation dilempar ulang.
* Tidak ada jalur startup-failure yang memanggil terminal emitter dua kali.

### Interaksi/regression

HIGH-05 tidak rusak: gate hanya infrastructure lifecycle dan tidak mengambil alih business dispatch. HIGH-01 tidak rusak: ID yang sama diteruskan ke terminal event.

---

## HIGH-08 — Transactional directory copy rollback

**Status: PARTIAL**

### Jalur Copy yang diverifikasi

* **Local:** `LocalFileSystem.copy()` memanggil `LocalDirectoryOperationHelper.copyDirectoryTransactionally()` untuk directory.
* **SAF:** `SafFileSystem.copy()` memanggil `SafStreamTransferHelper.copyDirectoryTransactionally()`.
* **Root:** `RootFileSystem.copy()` memanggil `RootDirectoryListingHelper.copyDirectoryTransactionally()`.
* **Shizuku:** `SafShizukuFileSystem.copy()` memanggil `ShizukuFileTransferHandler.copyDirectoryTransactionally()`.
* **Cross-filesystem:** `CrossFilesystemTransferBridge.copyCross()` membuat `DirectoryStaging` berdasarkan destination backend, menyalin children ke staging, memeriksa cancellation, lalu publish.

### Rollback/publish semantics yang benar

* Setiap jalur membuat nama staging unik (`.wkw-<UUID>.tmp`) dan menyalin seluruh subtree ke staging, sehingga failure sebelum publish tidak mengubah destination final.
* Catch menghapus hanya staging milik operasi. Existing destination tidak menjadi target cleanup pada copy/traversal failure.
* Existing destination dipindahkan ke backup sebelum staging dipublish; publish failure menghapus failed publication dan mencoba rename backup kembali.
* Cleanup failure ditambahkan sebagai suppressed exception di Local, SAF, Root, Shizuku, dan cross path; primary failure/cancellation dilempar ulang.
* Listing source Local/SAF/Root dan cross path fail-closed. Cancellation checks ada selama child traversal dan sebelum publish.

### Gap interaction dengan Move/C-04/C-05

* Publish helper menghapus backup destination **sebelum** `copy()` selesai kembali ke caller.
* Pada cross-filesystem Move, subtree validation (`validateTransferComplete`) baru dilakukan setelah `copyCross()` selesai dan backup sudah dihapus. Bila manifest collection/validation gagal atau dibatalkan, source memang tidak dihapus, tetapi destination lama yang dioverwrite tidak dapat direstore.
* Catch Move untuk directory sengaja tidak memanggil `rollbackDestination`, agar tidak menghapus pre-existing destination secara buta. Namun setelah publish sukses, yang tersisa adalah subtree baru; tidak ada transaction handle untuk membedakan/restore old destination saat validation gagal.
* Same-backend fallback Move Local/Root/Shizuku hanya memeriksa `destination.exists()` untuk directory sebelum delete source. SAF memakai `SafMoveTargetValidator`, tetapi state directory hanya membuktikan existence/type, bukan manifest lengkap. Cross path memiliki manifest lengkap, namun tidak mempertahankan backup sampai manifest commit.
* Test rollback yang ditambahkan hanya menguji filesystem Local. Tidak ada test production-equivalent untuk SAF, Root, Shizuku, semua pasangan cross-backend, cleanup failure, atau Move-validation-after-publish rollback.

### Kesimpulan

Transactional Copy failure/cancel sebelum publish telah diperbaiki untuk semua backend, tetapi transaksi tidak mencakup fase validasi Move. Karena itu interaksi HIGH-08 dengan C-04/C-05 belum sepenuhnya aman dan statusnya **PARTIAL**, bukan PASS.

---

## Duplicate path / legacy path audit

* Tidak ditemukan orchestration business Copy/Move/Delete lama di `FileCopyService`; hanya JSON parsing loop yang tersisa di worker.
* Tidak ditemukan non-atomic `remove old prefix -> insert destination` untuk Move/Rename di `FileIndexSynchronizer`.
* Tidak ditemukan direct recursive index traversal lama; `indexSubtreeBatches` adalah jalur aktif tunggal untuk subtree sync.
* Tidak ditemukan direct `:filemanager -> :file-operations/:search` dependency atau import implementation.
* Recursive directory-copy helpers masih ada dan aktif **di bawah transactional staging wrapper** untuk menyalin isi staging. Mereka bukan duplicate final-destination path, tetapi masih menggunakan call stack recursive untuk filesystem tree yang sangat dalam.
* Per-file temporary/backup helpers masih aktif untuk files di dalam staging. Ini berlapis tetapi tidak berkonflik: file atomicity melindungi child write, sedangkan directory staging melindungi subtree visibility.

## Source deletion gates

* Cross Move: copy selesai -> cancellation check -> full manifest validation untuk directory/file-size validation -> cancellation check -> `deleteSource(source)`.
* Same-backend Local/Root/Shizuku fallback Move: transactional copy -> destination existence (dan size hanya untuk file) -> delete source. Directory subtree validation penuh belum ada.
* SAF Move: transactional copy -> cancellation check -> `SafMoveTargetValidator` existence/type/size state -> cancellation check -> delete source. Directory subtree completeness belum dibuktikan.
* Atomic rename fast path Local/Root/Shizuku tidak memakai copy/delete fallback; keberhasilan dinilai dari rename dan observed source/destination state.

## Hasil test/build aktual

Command yang dijalankan:

```text
gradle :file-system:testDebugUnitTest --tests 'com.wakwau.xplore.core.storage.filesystem.local.LocalDirectoryOperationHelperTest'
```

Hasil: **PENDING (environment/toolchain)**. Gradle berhenti saat configuration karena Android Gradle Plugin `9.1.1` memerlukan Gradle minimal `9.3.1`, sedangkan environment menyediakan Gradle `8.14.4`. Tidak ada unit test, compile, atau build task yang sempat dieksekusi.

Static checks yang tetap dapat dilakukan:

* source call-path inspection untuk semua HIGH-01..HIGH-08;
* repository-wide `rg` untuk duplicate orchestration, imports, dependencies, transaction primitive, source deletion, staging, dan cleanup;
* `git diff --check` untuk laporan audit;
* konfirmasi bahwa audit hanya membuat `audit_high_recheck.md` dan tidak mengubah production source/Gradle.

## Verdict akhir

Perbaikan **HIGH-02, HIGH-03, HIGH-05, HIGH-06, dan HIGH-07** konsisten dengan source production aktual. **HIGH-01, HIGH-04, dan HIGH-08** masih **PARTIAL** karena gap nyata yang dijelaskan di atas. Tidak ada finding HIGH yang dinilai FAIL total, tetapi HIGH-08 memiliki regression/interaksi paling serius: old destination backup tidak hidup sampai Move validation berhasil.
