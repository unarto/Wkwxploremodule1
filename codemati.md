# Laporan Audit Kode Mati, Unused, dan Duplikat

## 1. Dead Code (Kode Mati)
- **Path File**: `filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/event/DualPaneEvent.kt`
  - **Elemen**: `DualPaneEvent.StorageVolumesLoading` dan `DualPaneEvent.StorageVolumesUpdated`
  - **Deskripsi**: Event ini tidak memiliki penanganan/state mutator di `DualPaneStateHolder` atau `AppOrchestratorViewModel`, sehingga pemanggilan event ini tidak akan berdampak (dead trigger) pada UI.

- **Path File**: `file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/batch/BatchOperationUseCase.kt`
  - **Elemen**: `BatchOperationUseCase` dan `BatchOperationCalculator`
  - **Deskripsi**: Kelas ini dimaksudkan untuk menghitung ukuran total sebelum proses batch (copy/move). Namun, pada implementasinya, `CopyFilesUseCase` dan `MoveFilesUseCase` memotong alur dan memanggil `BackgroundOperationClient` secara langsung tanpa memanggil kalkulator batch ini.

## 2. Unused Code (Kode / Import / Fungsi Tidak Terpakai)
- **Path File**: `core-storage/src/main/kotlin/com/wakwau/xplore/core/storage/preferences/AppPreferences.kt`
  - **Elemen**: `override fun getPreferencesState(): FilePreferencesState`
  - **Deskripsi**: Fungsi pemanggil state ini redundan karena komponen-komponen UI yang bereaksi dengan preferensi (seperti layout mode & show hidden files) sebagian besar telah berlangganan (subscribe) langsung ke `preferencesState` via `StateFlow.collectAsState()`.

- **Path File**: `file-system/src/main/kotlin/com/wakwau/xplore/core/storage/filesystem/bridge/CrossFilesystemSizeCalculator.kt`
  - **Elemen**: Berbagai metode kalkulasi ukuran spesifik (misal: `calculateLocalSize`, `calculateSafSize`, `calculateShizukuSize`).
  - **Deskripsi**: Kelas/metode kalkulator untuk lintas _file system_ belum di-invoke secara riil pada arsitektur UI saat ini, terutama tidak dimanfaatkan oleh `DualPaneEventHandler` atau orchestrator sebelum menyalin/memindahkan berkas.

## 3. Duplicate Code (Kode Duplikat)
- **Lokasi 1**: `core-utils/src/main/java/com/wakwau/xplore/core/utils/formatter/ByteFormatter.kt` (Fungsi `formatBytesShort()`)
- **Lokasi 2**: `file-operations-ui/src/main/kotlin/com/wakwau/xplore/fileoperations/ui/dialog/ProgressDialog.kt` (Fungsi `private fun formatBytes()`)
  - **Deskripsi**: Logika konversi representasi byte string (KB, MB, GB, TB) ditulis secara berulang. Seharusnya `ProgressDialog.kt` menggunakan abstraksi pemformatan terpusat dari modul `core-utils-ui` tanpa perlu menduplikasi logika matematika pembagian `1024.0` di dalam UI Composable.

- **Lokasi 1**: `app/src/main/kotlin/com/wakwau/xplore/orchestrator/fileops/CopyOperationOrchestrator.kt`
- **Lokasi 2**: `app/src/main/kotlin/com/wakwau/xplore/orchestrator/fileops/MoveOperationOrchestrator.kt`
  - **Deskripsi**: Kedua kelas orchestrator memiliki struktur logika penanganan konflik, *exception handling*, dan pemanggilan servis yang ~90% identik. Disarankan untuk mengekstraksi duplikasi tersebut menjadi sebuah `BaseFileOperationOrchestrator` guna menangani *state emission* secara terpusat (mengurangi duplikasi _boilerplate_ pada `OperationUiState.Running` dsb).
