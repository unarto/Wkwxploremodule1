# 📊 LAPORAN AUDIT KHUSUS MEKANISME MARK / UNMARK (`Mark.MD`)

## 📌 Ringkasan Masalah
- **Gejala:** Status *Mark* (tercentang) sering terlepas, hilang secara mendadak, atau mengalami desinkronisasi dengan ViewModel secara acak saat pengguna melakukan aksi (seperti mengeklik tombol Ubah Nama/Rename di sidebar atau menyentuh baris item), yang berujung pada tombol tidak merespons atau layar terlempar ke error *"Folder atau berkas tidak ditemukan"*.
- **Akar Masalah Utama:** 
  1. Adanya pembersihan paksa seleksi (*Premature Selection Wipe*) di `DualPaneReducer` (`selectedItemIds = emptySet()`) setiap kali event `DirectoryLoaded` terjadi.
  2. Jeda latensi asinkron (*Unsynchronized Coroutine Execution*) pada UI Composable saat proses *Mark* yang melibatkan auto-expand direktori sebelum mengirim `onSelectionChange`.
  3. Ketidaksesuaian kontrak data seleksi antara pengidentifikasi Path (`node.data.location.path`) dengan ID berkas (`item.id`) pada interaksi klik/long-click.
  4. Pembacaan *snapshot* seleksi yang tidak atomik di `FileManagerContent` melalui traversal cache pohon (`FileTreeEngine.getSelectedItems`) yang beradu cepat dengan operasi re-komposisi dan background reload.
- **Tingkat Keparahan:** **CRITICAL** (Merusak integritas multi-seleksi, operasi berkas, dan memicu kegagalan berantai pada modul I/O).

---

## 🔍 Detail Temuan & Alur Penyebab

### 1. Pemicu & Pembaruan State di UI (`:filemanager-ui`)

- **Premature Selection Wipe Akibat Klik Baris Berkas (`FileListItem.kt` & `DirectoryTreeView.kt`):**
  - Pada `FileListItem.kt` (baris 86-96), seluruh baris dibungkus oleh `combinedClickable(onClick = onClick, onLongClick = onLongClick)`. Area klik kotak centang (`FileListItemActionMenu`) hanya berukuran `28.dp` (`FileListItemDefaults.CheckBoxSize`), di bawah batas ergonomis aksesibilitas 48.dp.
  - Jika sentuhan meleset beberapa piksel di luar kotak centang, event `onClick` baris terpicu.
  - Di `DirectoryTreeView.kt` (baris 97-107), `onClick` langsung memanggil `onNavigate(node.data.location)` yang mendispatch `DualPaneEvent.OpenLocation`.
  - `OpenLocation` memicu `refreshHandler.loadDirectory()` di background coroutine.
  - Begitu pemuatan direktori selesai, `DualPaneReducer.kt` (baris 36) mengeksekusi:
    ```kotlin
    selectedItemIds = emptySet() // clear selection on load
    ```
    Akibatnya, seluruh berkas/folder yang sebelumnya sudah di-mark seketika terhapus (*unmark*) tanpa disadari oleh pengguna.

- **Jeda Latensi Asinkron & Race Condition pada Auto-Expand (`DirectoryTreeView.kt`):**
  - Logika klik centang pada `DirectoryTreeView.kt` (baris 113-126) dijalankan di dalam Coroutine UI lokal:
    ```kotlin
    onCheckToggle = {
        coroutineScope.launch {
            var shouldExpand = false
            val newSelection = treeSelectionHandler.nextSelection(node, panelState.selectedItemIds) {
                if (!engine.treeState.isExpanded(node)) {
                    shouldExpand = true
                }
            }
            if (shouldExpand) {
                treeAdapter.expandNode(panelState.id, node) // <--- SUSPEND FUNCTION (I/O)
            }
            onSelectionChange(newSelection) // <--- TERPENDAM SAMPAI I/O EXPAND SELESAI
        }
    }
    ```
  - Jika `shouldExpand == true`, fungsi `expandNode` mengeksekusi pemuatan anak direktori (`loadChildren`) dari disk.
  - Sebelum proses I/O selesai (bisa memakan waktu ratusan milidetik), `onSelectionChange(newSelection)` **belum terpanggil**.
  - Jika pengguna langsung mengeklik tombol aksi di sidebar (misal tombol "Ubah Nama"), ViewModel masih memegang state seleksi lama atau kosong.

- **Inkonsistensi Kontrak Identitas Item (`item.id` vs `location.path`):**
  - Pada `FileManagerContent.kt` (baris 213 & 217):
    ```kotlin
    onItemLongClick = { item ->
        onEvent(DualPaneEvent.SetSelectedItems(activePanel.id, setOf(item.id)))
        onEvent(DualPaneEvent.ShowFileDetails(item))
    }
    ```
    Event `SetSelectedItems` mengirimkan `item.id`.
  - Namun di `FileListItem.kt` (baris 94), `TreeSelectionHandler.kt` (baris 31), dan `FileTreeEngine.kt` (baris 193), evaluasi centang menggunakan `node.data.location.path`.
  - Pada hasil pencarian atau storage root di mana `id != location.path`, sistem gagal mengenali item sebagai item terpilih.

---

### 2. Sinkronisasi StateFlow di ViewModel (`:filemanager`)

- **Pembersihan Seleksi Tak Bersyarat pada Pure Reducer (`DualPaneReducer.kt`):**
  - Reducer menyetel `selectedItemIds = emptySet()` pada penanganan `DualPaneEvent.DirectoryLoaded`.
  - Padahal dalam arsitektur pohon (*tree view*), pemuatan cabang anak suatu folder tidak seharusnya membatalkan seleksi berkas di cabang pohon lainnya.
  - Hal ini menyebabkan setiap kali terjadi ekspansi node atau auto-refresh, StateFlow `selectedItemIds` direset menjadi kosong.

- **Desinkronisasi State Akibat Double Scope Execution:**
  - Perubahan seleksi di UI (`DirectoryTreeView`) diluncurkan pada `androidx.compose.runtime.rememberCoroutineScope`.
  - Pemrosesan event dan side effects di ViewModel diluncurkan pada `DualPaneStateHolder.scope` dan `viewModelScope`.
  - Tidak ada penguncian (*mutex*) atau penjaminan urutan (FIFO) antara event mutasi seleksi (`SetSelectedItems`) dengan event pemuatan direktori (`OpenLocation` / `Refresh`).

- **Aggressive ClearSelection pada File Operations Orchestrator (`app/.../orchestrator`):**
  - Pada `RenameOperationOrchestrator.kt`, `ClearSelection(panel.id)` dipanggil tidak hanya saat operasi sukses, tetapi juga dipanggil saat `OperationFailed` dan `OperationCancelled`.
  - Hal ini menghilangkan penandaan pengguna bahkan ketika operasi gagal atau dibatalkan, memaksa pengguna mencentang ulang dari awal.

---

### 3. Penyebab Error Acak pada Fitur Akses Berkas (Contoh Kasus: Ubah Nama)

- **Pembacaan Ulang Terputus di SideActionBar Handler (`FileManagerContent.kt`):**
  - Saat pengguna mengeklik tombol aksi di sidebar:
    ```kotlin
    val selectedItems = treeAdapter.getSelectedItems(activePanel.id, activePanel.selectedItemIds)
    ...
    SideAction.RENAME -> {
        if (selectedItems.size == 1) {
            onEvent(DualPaneEvent.ShowRenameDialog(selectedItems.first()))
        }
    }
    ```
  - `handleSideAction` tidak menerima payload item yang sedang aktif secara eksplisit dari UI, melainkan memanggil ulang `treeAdapter.getSelectedItems(..., activePanel.selectedItemIds)`.
  - Jika pada saat tombol diklik, `activePanel.selectedItemIds` baru saja direset oleh `DirectoryLoaded` (akibat sentuhan navigasi sebelumnya) atau coroutine `onCheckToggle` masih tertahan di `expandNode`, maka `selectedItems` bernilai kosong (`size == 0`).
  - Akibatnya:
    1. Blok `if (selectedItems.size == 1)` bernilai `false`, dan dialog **tidak pernah muncul**.
    2. Jika seleksi berisi path lama yang sudah berpindah/terhapus, fungsi `getSelectedItems` di `FileTreeEngine` gagal menemukan node dalam `treeState.roots`, mengembalikan item usang (*stale item*), yang saat diproses oleh UseCase melempar `FileOperationError.NOT_FOUND`, memicu error *"Folder atau berkas tidak ditemukan"*.

---

## 🛠️ Rekomendasi Langkah Perbaikan (Rencana)

1. **Pisahkan Siklus Seleksi dari Navigasi Direktori di Reducer:**
   - Hapus pembersihan paksa `selectedItemIds = emptySet()` dari `DualPaneEvent.DirectoryLoaded` di `DualPaneReducer.kt`.
   - Seleksi hanya boleh dibersihkan secara eksplisit melalui `DualPaneEvent.ClearSelection` saat pengguna sengaja meng-uncheck semua atau saat operasi berkas (pindah/hapus) benar-benar telah selesai dengan sukses.

2. **Perbarui State Seleksi Secara Atomik Sebelum Operasi Asinkron:**
   - Di `DirectoryTreeView.kt`, ubah alur `onCheckToggle` agar memanggil `onSelectionChange(newSelection)` **sebelum** menjalankan operasi `expandNode`, atau pisahkan eksekusi expand ke coroutine terpisah agar pembaruan state seleksi tidak terblokir oleh disk I/O.

3. **Standardisasi Pengidentifikasi Seleksi (Single Source of Identity):**
   - Samakan seluruh referensi penandaan (*selection key*) menggunakan `item.location.path` secara seragam di seluruh layer (`onItemLongClick`, `onIconClick`, `TreeSelectionHandler`, `FileListItem`, dan `DualPaneReducer`).

4. **Kirimkan Objek Target Eksplisit pada Aksi Sidebar:**
   - Pada `FileManagerContent.kt`, jika `selectedItems` kosong namun pengguna sedang memfokuskan baris tertentu (`selectedPath`), gunakan fallback ke item yang sedang aktif terfokus atau berikan visual feedback (Toast/Snackbar) yang jelas jika tidak ada item yang ditandai, alih-alih silent failure.

5. **Perluas Touch Target Kotak Centang:**
   - Tingkatkan area sentuh interaktif kotak centang di `FileListItemActionMenu.kt` menjadi minimal 48dp (menggunakan `Modifier.minimumInteractiveComponentSize()`) untuk mencegah kesalahan sentuh (*misclick*) yang memicu `onClick` baris secara tidak sengaja.

---
🛑 STATUS: AUDIT SELESAI - MENUNGGU INSTRUKSI EKSEKUSI PERBAIKAN DARI PENGGUNA
