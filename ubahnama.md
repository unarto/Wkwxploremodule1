# 📑 LAPORAN AUDIT READ-ONLY: INVESTIGASI BUG FITUR UBAH NAMA (INTERMITTENT / RACE CONDITION)

> **Status Audit:** SELESAI (READ-ONLY AUDIT)  
> **Modul Terdampak:** `:filemanager-ui`, `:file-operations-ui`, `:filemanager`, `:app`, `:core-storage`, `:file-system`  
> **Aturan Eksekusi:** AUDIT READ-ONLY — DILARANG MENGUBAH SOURCETREE KODE ATAU GRADLE.

---

## 1. 🔍 RINGKASAN EKSEKUTIF & AKAR MASALAH (ROOT CAUSE)

- **Tipe Bug:** Intermittent / Race Condition (Kadang-kadang muncul).
- **Akar Masalah:** Sinkronisasi state `selectedItems` dan pemanggilan reload direktori yang eksekusinya beradu cepat (*race condition*) antara Coroutine Thread IO dengan UI Re-composition.

Berdasarkan investigasi menyeluruh pada call-chain fitur Ubah Nama (*Rename*), pesan error:
> *"Folder atau berkas tidak ditemukan. (Mungkin Anda belum memberikan akses Root melalui Shizuku, atau belum mengizinkan akses penyimpanan.)"* (`R.string.err_not_found`)

tidak terjadi secara deterministik konstan pada setiap klik, melainkan muncul secara **intermittent / berkala** akibat tabrakan waktu eksekusi (*race condition*) pada tiga titik kritis:

1. **Race Condition Sinkronisasi Seleksi Item (`selectedItems` vs UI Dispatch):**
   - Di `DirectoryTreeView.kt`, perubahan centang item (`onCheckToggle`) diproses secara asinkron di dalam `coroutineScope.launch { ... onSelectionChange(newSelection) }`.
   - Di `FileManagerContent.kt`, saat tombol *SideAction.RENAME* diklik, UI membaca `treeAdapter.getSelectedItems(activePanel.id, activePanel.selectedItemIds)` secara sinkron pada frame re-komposisi tersebut.
   - Apabila pengguna mengeklik tombol "Ubah Nama" sesaat setelah mencentang atau setelah interaksi node (seperti auto-expand/collapse), state `selectedItemIds` pada ViewModel belum terkomit atau daftar anak di `treeState.roots` sedang dimutasi di background thread.
   - Akibatnya, `selectedItems` terbaca kosong (`size == 0`) atau merujuk ke snapshot node lama, sehingga dialog tidak muncul atau memicu re-evaluasi navigasi dengan path yang tidak sinkron.

2. **Race Condition Reload Direktori & Stale Node Path Query (Pemicu Error `err_not_found`):**
   - Saat operasi rename berhasil di `RenameOperationOrchestrator.kt`, terjadi pemanggilan ganda (*double concurrent refresh*):
     - `RenameOperationOrchestrator` memanggil `dispatch(DualPaneEvent.OperationSuccess)` yang ditangkap oleh `DualPaneViewModel` untuk memicu `dispatch(DualPaneEvent.Refresh)`.
     - `RenameOperationOrchestrator` juga langsung memanggil `dispatch(DualPaneEvent.Refresh)` secara bersamaan.
   - Dua coroutine `refreshExpandedNodes()` berjalan paralel pada `viewModelScope` dan `DualPaneStateHolder.scope`.
   - Di dalam `FileTreeEngine.refreshExpandedNodes()`, engine melakukan iterasi pada seluruh node yang berstatus `isExpanded`. Jika folder yang baru saja diubah namanya sebelumnya berada dalam status terbuka (*expanded*), objek `TreeNode<FileItem>` pada pohon masih menyimpan path lama (`node.data.location.path`).
   - Engine mengeksekusi `loadChildren(node)` -> `listDirectoryUseCase(node.data.location)` menggunakan **path lama yang sudah tidak ada lagi di filesystem** (karena telah berganti nama).
   - `listDirectoryUseCase` mengembalikan `FileOperationResult.Failure(FileOperationError.NOT_FOUND)`.
   - `FileTreeEngine` langsung menetapkan `_errorState.value = "NOT_FOUND"`.
   - Layer UI (`DirectoryTreeView.kt`) mengamati `errorState != null` dan mengganti seluruh pohon berkas dengan `FileListError(error = "NOT_FOUND")`, yang merender string:
     `R.string.err_not_found` (*"Folder atau berkas tidak ditemukan..."*).

3. **Premature Dismissal & IME Inset Flickering:**
   - Di `DualPaneViewModel.kt`, penanganan `DualPaneEvent.RenameItem` langsung mengeksekusi `_dialogUiState.value = FileDialogUiState.None` sebelum transaksi I/O selesai dikonfirmasi.
   - Di `AppDialog.kt`, default `DialogProperties(dismissOnClickOutside = true)` menyebabkan dialog rentan tertutup secara instan jika terjadi penyesuaian ukuran window akibat soft-keyboard (IME).

---

## 2. 🔬 BUKTI CALL-CHAIN & ANALISIS KODE SUMBER

### A. Jeda Latensi Pembacaan `selectedItems` di UI (`:filemanager-ui`)
Berkas: `filemanager-ui/.../screen/FileManagerContent.kt` & `DirectoryTreeView.kt`
```kotlin
// FileManagerContent.kt baris 63
val selectedItems = treeAdapter.getSelectedItems(activePanel.id, activePanel.selectedItemIds)

// Penanganan SideAction.RENAME
SideAction.RENAME -> {
    if (selectedItems.size == 1) {
        onEvent(DualPaneEvent.ShowRenameDialog(selectedItems.first()))
    }
}
```
**Analisis Celah:**
- `selectedItemIds` diperbarui via `DirectoryTreeView.kt`:
  ```kotlin
  onCheckToggle = {
      coroutineScope.launch {
          val newSelection = treeSelectionHandler.nextSelection(node, panelState.selectedItemIds) { ... }
          onSelectionChange(newSelection) // Dispatch ke ViewModel StateHolder
      }
  }
  ```
- Terdapat jeda asinkron antara peluncuran coroutine seleksi dengan pembacaan instan di `FileManagerContent.kt`.
- Jika `SideAction.RENAME` ditekan saat coroutine masih berjalan, `selectedItems.size` masih bernilai `0`. Tombol tidak merespons, dan jika terjadi *stale state*, node yang dikirim ke event `ShowRenameDialog` bisa membawa data item yang tidak valid.

---

### B. Double Refresh & Stale Node Listing (`:app` & `:filemanager-ui`)
Berkas: `app/.../orchestrator/fileops/RenameOperationOrchestrator.kt`
```kotlin
when (val result = renameFileUseCase(item.location, newName)) {
    is FileOperationResult.Success -> {
        dispatch(DualPaneEvent.OperationSuccess(FileOperationConstants.SUCCESS_RENAME))
        dispatch(DualPaneEvent.ClearSelection(panel.id))
        dispatch(DualPaneEvent.Refresh(panel.id)) // <--- Pemicu Refresh 1
    }
    ...
}
```
Berkas: `filemanager-ui/.../presentation/DualPaneViewModel.kt`
```kotlin
is DualPaneEvent.OperationSuccess -> {
    // <--- Pemicu Refresh 2
    dispatch(DualPaneEvent.Refresh(stateHolder.state.value.activePanelId))
}
```
Berkas: `filemanager-ui/.../tree/FileTreeEngine.kt`
```kotlin
suspend fun refreshExpandedNodes() {
    suspend fun refreshNode(node: TreeNode<FileItem>) {
        if (treeState.isExpanded(node) && node.data.type == FileType.DIRECTORY) {
            val previousExpandedIds = node.children.filter { it.isExpanded }.map { it.id }.toSet()
            loadChildren(node) // <--- MENGEKSEKUSI PATH LAMA JIKA FOLDER DI-RENAME!
            for (child in node.children) {
                if (previousExpandedIds.contains(child.id)) {
                    treeState.expand(child)
                    refreshNode(child)
                }
            }
        }
    }
    for (root in treeState.roots) {
        refreshNode(root)
    }
    treeState.forceRefresh()
}
```
**Bukti Terjadinya Bug Intermittent:**
1. Folder `/storage/emulated/0/Download/DataLama` di-expand oleh pengguna.
2. Pengguna mengubah nama folder menjadi `DataBaru`. Transaksi I/O filesystem berhasil (`File.renameTo()` sukses).
3. `Refresh` terpicu. `refreshExpandedNodes()` menelusuri pohon yang masih memegang objek node dengan `data.location.path = "/storage/emulated/0/Download/DataLama"`.
4. `loadChildren(node)` memanggil `listDirectoryUseCase` dengan path lama tersebut.
5. Karena path lama sudah lenyap di OS filesystem, hasilnya adalah `FileOperationResult.Failure(FileOperationError.NOT_FOUND)`.
6. Baris 150-152 di `FileTreeEngine.kt`:
   ```kotlin
   is FileOperationResult.Failure -> {
       _errorState.value = result.error.name // "NOT_FOUND"
   }
   ```
7. `DirectoryTreeView.kt` menangkap `errorState != null`:
   ```kotlin
   if (errorState != null) {
       FileListError(
           error = errorState ?: "", // Memetakan "NOT_FOUND" -> R.string.err_not_found
           onRetry = { ... }
       )
   }
   ```
8. Inilah alasan kuat mengapa layar tiba-tiba terlempar ke pesan:  
   *"Folder atau berkas tidak ditemukan. (Mungkin Anda belum memberikan akses Root melalui Shizuku, atau belum mengizinkan akses penyimpanan.)"*.

---

### C. Pemutusan Dialog Prematur di ViewModel
Berkas: `filemanager-ui/.../presentation/DualPaneViewModel.kt`
```kotlin
is DualPaneEvent.RenameItem -> {
    _dialogUiState.value = FileDialogUiState.None // <--- PREMATURE DISMISS
    actionDelegate?.requestRename(state.value, event.item, event.newName)
}
```
State dialog langsung dihancurkan sebelum `requestRename` selesai. Jika terjadi latensi IO atau error, dialog tidak memiliki kesempatan menampilkan validasi inline, dan jika crash/error terjadi di layer IO, pengguna langsung dilempar ke error screen tanpa feedback yang wajar.

---

## 3. 📋 MATRIKS REKOMENDASI PERBAIKAN PRESISI

| No | Komponen / Modul | Masalah yang Terjadi | Solusi Presisi Berdasarkan Arsitektur |
|---|---|---|---|
| 1 | **FileTreeEngine** (`:filemanager-ui`) | `refreshExpandedNodes()` men-query path lama dari folder yang telah di-rename sehingga memicu `NOT_FOUND`. | Pada saat rename berhasil, perbarui data node atau refresh parent node direktori induknya saja (`refreshNodeByPath(parentPath)`), bukan men-query ulang node anak yang path-nya sudah usang. Jangan set global `_errorState` jika kegagalan hanya terjadi pada satu node anak yang sedang refresh. |
| 2 | **RenameOperationOrchestrator** (`:app`) | Pemicuan double refresh simultan (`OperationSuccess` dan `Refresh`) memicu tabrakan coroutine pada tree engine. | Hapus pemicuan `Refresh` manual kedua dari `RenameOperationOrchestrator` dan percayakan siklus sinkronisasi pada handler `OperationSuccess` terpusat. |
| 3 | **FileManagerContent & SideActionBar** (`:filemanager-ui`) | Jeda asinkron pembacaan `selectedItems` menyebabkan klik tombol rename kadang-kadang tidak merespons atau membawa node stale. | Sinkronkan pengambilan item tunggal terpilih langsung dari `selectedPath` aktif atau validasi atomik pada state holder sebelum membuka dialog. |
| 4 | **DualPaneViewModel** (`:filemanager-ui`) | `_dialogUiState` di-reset ke `None` seketika saat event `RenameItem` dikirim. | Pertahankan state dialog sampai menerima konfirmasi `OperationSuccess` atau `OperationFailed` agar dialog tidak tertutup mendadak. |
| 5 | **LocalDirectoryOperationHelper** (`:file-system`) | `File.renameTo()` di Android 10+ gagal secara silent tanpa pengecekan izin Scoped Storage. | Tambahkan verifikasi `storagePermissionChecker.hasAllFilesAccess()` sebelum operasi rename dilakukan, konsisten dengan `createDirectory()`. |

---

> **Pernyataan Penutup:**  
> Audit telah diperbarui dengan bukti teknis komprehensif mengenai kondisi pacu (*race condition*) dan pemanggilan reload direktori stale yang menyebabkan error intermittent *"Folder atau berkas tidak ditemukan"*. Tidak ada kode sourcetree yang dimodifikasi. Laporan siap ditinjau pengguna.
