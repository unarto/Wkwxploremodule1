# 📊 LAPORAN AUDIT ALUR IZIN PENYIMPANAN (`izinpenyimpanan.md`)

## 📌 Ringkasan Masalah
- **Gejala:** Dialog/pemicu izin penyimpanan (*Storage Permission Prompt*) tidak muncul otomatis saat aplikasi membutuhkan akses penuh atau mengalami kegagalan akses (Akses Ditolak). 
- **Tingkat Keparahan:** **CRITICAL** (Menyebabkan aplikasi gagal total berfungsi di instalasi baru atau perangkat yang belum memberikan izin secara manual melalui Pengaturan sistem).

## 🔍 Detail Temuan & Alur Penyebab
1. **Deklarasi Manifest & Android Compatibility (`AndroidManifest.xml`):**
   - Di `app/src/main/AndroidManifest.xml`, permission `MANAGE_EXTERNAL_STORAGE` (untuk API 30+) serta `READ_EXTERNAL_STORAGE` & `WRITE_EXTERNAL_STORAGE` sudah dideklarasikan.
   - **TEMUAN KRITIS:** Atribut `android:requestLegacyExternalStorage="true"` **TIDAK ADA** pada tag `<application>`. Ini menyebabkan aplikasi pada Android 10 (API 29) tidak memiliki hak akses penyimpanan lokal (non-SAF) karena Android 10 akan mengisolasi akses penyimpanan secara ketat meskipun izin READ/WRITE_EXTERNAL_STORAGE telah diberikan. Android 10 berada di zona abu-abu karena belum mengenal MANAGE_EXTERNAL_STORAGE.

2. **Layer Kontrak & Infrastruktur Checker (`:core-storage-api` & `:file-system`):**
   - Pada `StoragePermissionCheckerImpl.kt`, implementasi metode `hasAllFilesAccess()` sudah benar: mendeteksi `Environment.isExternalStorageManager()` untuk Android 11+ (API 30+) dan `ContextCompat.checkSelfPermission` untuk versi di bawahnya.
   - Pengecekan status izin di backend berjalan sebagaimana mestinya, namun layer ini sebatas *"checker"* (pengecek pasif) dan tidak berwenang (dan tidak bisa) memicu UI request secara langsung.

3. **Layer Presentation & Intent Handler (`:filemanager-ui` & `:app`):**
   - **State Diabaikan:** `DualPaneState` memiliki atribut `hasPermission`, yang berhasil diperbarui melalui event `DualPaneEvent.PermissionStatusUpdated`. Namun, di UI layer (`DualPaneFileManagerScreen.kt` dan `XploreRoot.kt`), state boolean ini sama sekali **TIDAK DIGUNAKAN** untuk membuat percabangan (*branching*) tata letak. Aplikasi bersikeras memuat list direktori kosong jika belum diizinkan.
   - **Komponen UI Yatim Piatu (Orphaned Code):** Telah dibuat Composable UI `PermissionScreen.kt` dan class pemicu Intent `PermissionIntentHelper.kt` di modul `:filemanager-ui`, tetapi keduanya **TIDAK PERNAH DIPANGGIL** di seluruh basis kode. `NavHost` pada `XploreRoot.kt` bahkan tidak memuat destinasi rute `AppRoute.Permission`.
   - **Tanggapan Error Pasif:** Ketika I/O backend (seperti Rename/Create Folder) melempar `ACCESS_DENIED`, handler di `XploreRoot.kt` (melalui `OperationUiState.Failure`) hanya menelan pesan error tersebut dan menampilkannya sebatas *Toast* atau Dialog Teks polos, tanpa menyediakan aksi/tombol untuk meminta izin (*Grant Permission*).

## 🛠️ Rekomendasi Langkah Perbaikan (Rencana)
- **Tambahkan Legacy Flag:** Tambahkan atribut `android:requestLegacyExternalStorage="true"` di `AndroidManifest.xml` untuk memulihkan akses file di perangkat Android 10 (API 29).
- **Integrasi PermissionScreen ke UI Root:** Modifikasi `XploreRoot.kt` agar melakukan evaluasi. Jika `dualPaneState.hasPermission == false`, secara dinamis render komponen `PermissionScreen` sebagai penutup/tampilan utama.
- **Sambungkan Peluncur Intent (Intent Launcher):** Hubungkan callback tombol pada `PermissionScreen` ke `PermissionIntentHelper.requestStorageAccess` (atau daftarkan `ActivityResultLauncher`) untuk meluncurkan antarmuka sistem `Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`.
- **Auto-Refresh Lifecycle:** Pasang *Lifecycle Event Observer* (seperti `Lifecycle.Event.ON_RESUME`) di `XploreRoot.kt` atau `DualPaneFileManagerScreen` untuk secara otomatis mengeksekusi `dualPaneViewModel.checkPermission()` sesaat setelah pengguna kembali dari layar Pengaturan sistem, sehingga UI otomatis merefresh daftar file.

---
🛑 STATUS: AUDIT SELESAI - MENUNGGU INSTRUKSI EKSEKUSI PERBAIKAN
