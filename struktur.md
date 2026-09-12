# Struktur Arsitektur WKW Xplore

## 1. Tujuan
WKW Xplore menggunakan arsitektur modular berbasis *bounded feature* dan *layered architecture*. Dokumen ini menjadi acuan resmi untuk struktur modul, tanggung jawab, *boundary*, dan *dependency direction*, sepenuhnya selaras dengan `ownership.md` sebagai *Source of Truth*.

Tujuan utama:
- SRP (Single Responsibility Principle) yang jelas.
- Dependency yang terkontrol dan searah.
- Tidak ada God Class.
- UI tidak mengakses implementasi *storage* / I/O secara langsung.
- *Operation engine* terpisah dari *storage implementation*.
- Pemisahan yang tegas antara Presentation, Domain, Contracts, dan Infrastructure.

---

## 2. Struktur Modul
Project WKW Xplore terdiri dari 16 modul resmi:

**Application**
- `:app`

**Foundation**
- `:core-utils`
- `:core-utils-ui`

**Storage & Infrastructure**
- `:core-storage-api`
- `:core-storage`
- `:file-system`

**Background Execution**
- `:core-worker`

**File Operations Engine**
- `:file-operations`
- `:file-operations-ui`
- `:file-operations-bridge`

**File Manager**
- `:filemanager`
- `:filemanager-ui`
- `:treeview`

**Search Engine**
- `:search`
- `:search-ui`

**Settings**
- `:settings-ui`

---

## 3. Prinsip Bounded Feature & Layering
Setiap fitur besar diisolasi menjadi pasangan modul Domain/Engine dan Presentation (UI).
Contoh:
- `:filemanager` dan `:filemanager-ui`
- `:search` dan `:search-ui`
- `:file-operations` dan `:file-operations-ui`

**Aturan Layering:**
1. **Domain/Engine** menangani *logic* (Pure Kotlin).
2. **Presentation (UI)** hanya menangani *state rendering* dan interaksi pengguna (Compose).
3. UI dilarang keras melakukan operasi I/O atau memiliki logika bisnis fitur.
4. Fitur (misal `:filemanager`) dilarang bergantung langsung pada fitur lain (misal `:search`). Shared logic harus melalui Foundation atau *Contracts*.
5. Dilarang membuat "Manager", "Service Locator", "GlobalCoordinator", atau "GlobalRegistry" sebagai solusi *dependency*.

---

## 4. Dependency Graph & Boundary

**Arah panah "→" berarti bergantung pada (bergantung ke arah bawah).**

```text
                         :app
                          │
             ┌────────────┼─────────────┬─────────────┐
             │            │             │             │
      :filemanager-ui  :search-ui  :settings-ui  :file-operations-ui
             │            │                           │
        :filemanager   :search                  :file-operations
             │            │                           │
             └────────────┼───────────────────────────┘
                          │
                  :core-storage-api
                          │
             ┌────────────┴────────────┐
             │                         │
       :core-storage             :file-system
```

**Rincian Boundary Kunci:**
- `:filemanager-ui` → `:filemanager`, `:treeview`, `:core-utils-ui`
- `:filemanager` → `:core-storage-api`, `:core-utils`
- `:search-ui` → `:search`, `:core-utils-ui`
- `:search` → `:core-storage-api`, `:core-utils`
- `:file-operations-ui` → `:file-operations`, `:core-utils-ui`
- `:file-operations` → `:core-storage-api`, `:core-utils`
- `:file-operations-bridge` → *operation contracts* (Boundary adapter)
- `:core-worker` → `:file-operations`, `:file-operations-bridge`
- `:core-storage` → `:core-storage-api`, `:core-utils`
- `:file-system` → `:core-storage-api`, `:core-utils`

---

## 5. Golden Dependency Rules (Aturan yang Dilarang)

1. **Rule 1 — UI tidak boleh bypass boundary**
   - UI → Domain/Contract → Implementation.
   - **Dilarang:** UI → `:core-storage` / `:file-system`.
2. **Rule 2 — Domain tidak boleh bergantung UI**
   - Modul berikut **dilarang** memiliki dependensi Compose/Android UI: `:filemanager`, `:file-operations`, `:search`, `:core-storage-api`, `:core-utils`.
3. **Rule 3 — Physical I/O hanya di infrastructure**
   - *Physical filesystem* (`java.io.File`, SAF, Root, Shizuku) murni HANYA dimiliki oleh `:file-system`.
4. **Rule 4 — Persistence hanya di `:core-storage`**
   - `Room`, `MMKV`, `DAO`, `Entity`, dan implementasi persistence hanya dimiliki `:core-storage`.
5. **Rule 5 — Worker bukan business engine**
   - `:core-worker` HANYA bertugas menjalankan *background job* dan memanggil UseCase. Dilarang mengambil alih business logic *Copy/Move/Delete*.
6. **Rule 6 — TreeView harus generic**
   - `:treeview` dilarang mengetahui detail `FileItem`, `StorageLocation`, pencarian, atau operasi berkas.
7. **Rule 7 — Feature UI tidak boleh mencampur domain**
   - `:search-ui` hanya untuk pencarian, `:filemanager-ui` hanya untuk file manager, dsb.

---

## 6. Tanggung Jawab Per Modul

### 6.1. Application Root
- **`:app` (Composition Root)**
  - Application & Activity entry point, Root navigation, DI (Dependency Injection) wiring.
  - **Dilarang:** Business logic, file operation engine, raw I/O, Room DAO access.

### 6.2. Foundation
- **`:core-utils` (Pure Kotlin)**
  - Utilitas generik (Result wrapper, logger, datetime). Dilarang bergantung pada modul fitur, Compose, atau I/O.
- **`:core-utils-ui` (Design System)**
  - Theme, Colors, Typography, komponen dialog generik. Dilarang berisi logika *file browsing*, *search*, atau *storage*.

### 6.3. Storage Boundary
- **`:core-storage-api` (Domain Contract)**
  - Kontrak (antarmuka) dan model (mis. `FileItem`, `StorageLocation`, `FileSystem`). Mendefinisikan **apa** yang tersedia, bukan **bagaimana**.
  - **Dilarang:** Implementasi filesystem konkrit, Room, MMKV, `Context`, Compose.
- **`:core-storage` (Persistence / Data)**
  - Implementasi database (Room) dan key-value (MMKV).
  - **Dilarang:** Mesin filesystem lokal, SAF, Root, Shizuku, UI.
- **`:file-system` (Physical File I/O)**
  - Pemilik implementasi akses nyata: Local (java.io.File), SAF (DocumentFile), Root (libsu), Shizuku. Mapper model OS ke Domain.
  - **Dilarang:** Compose UI, file manager screen, UI pencarian.

### 6.4. Background Execution
- **`:core-worker` (Android Runtime)**
  - Foreground Service, WorkManager, Notification, WakeLock, Progress dispatch.
  - **Dilarang:** Algoritma business logic mutasi, raw filesystem, implementasi storage.

### 6.5. File Operations Engine
- **`:file-operations` (Domain Logic)**
  - Business logic *Copy, Move, Delete, Rename, Cancel, Conflict Resolution, Batch Calculator*.
  - **Dilarang:** Compose, Android Context, `java.io.File`, Room, implementasi concrete filesystem.
- **`:file-operations-ui` (Presentation)**
  - UI operasi (`ConflictResolutionDialog`, `ProgressDialog`).
  - **Dilarang:** Raw file I/O, algoritma konflik, business logic mutasi.
- **`:file-operations-bridge` (Bridge/Adapter)**
  - Boundary antara caller/presenter dan eksekutor background.
  - **Dilarang:** Business logic, implementasi filesystem, UI.

### 6.6. File Manager Domain
- **`:filemanager` (Domain Logic / Pure Kotlin)**
  - Logic browsing, *dual pane state* (MVI), navigasi direktori.
  - **Dilarang:** Compose, Activity/Fragment, raw I/O, concrete storage.
- **`:filemanager-ui` (Presentation)**
  - Layar file manager, TopBar, list rendering, side action bar.
  - **Dilarang:** Raw I/O, Room DAO, operasi mutasi engine, pencarian engine.
- **`:treeview` (Reusable UI Component)**
  - *Generic tree rendering* (`TreeNode<T>`). Dilarang mengetahui objek domain penyimpanan.

### 6.7. Search Domain
- **`:search` (Domain Logic / Engine)**
  - Algoritma pencarian, traversal direktori, regex matching.
  - **Dilarang:** Compose, UI pencarian, raw database/filesystem access.
- **`:search-ui` (Presentation)**
  - Dialog pencarian, filter, *search history*. Dilarang berisi algoritma direktori traversal atau I/O.

### 6.8. Settings
- **`:settings-ui` (Presentation)**
  - Pengaturan tema, bahasa, akses *root*, mode filesystem. Dilarang melakukan *file browsing* atau manipulasi file langsung.

---

## 7. Aturan Pemindahan Class (Refactoring Rule)
Sebelum memindahkan *class* atau modul:
1. Audit *usage* (penggunaan).
2. Audit *dependency*.
3. Tentukan *ownership*.
4. Pastikan *boundary*.
5. Pindahkan *class*.
6. Perbaiki *reference/import*.
7. Build & Test.
8. Verifikasi *dependency graph*.
*(Jangan memindahkan class hanya berdasarkan nama file/package).*

---

## 8. Anti-God-Class Rule
Dilarang membuat satu *class* yang merangkap tanggung jawab lintas batas (misal: menangani UI + State + Business Logic + Filesystem I/O + Database sekaligus).
Hindari class semacam `GlobalManager`, `AppManager`, `GlobalCoordinator`, atau `ServiceLocator` yang menjebol enkapsulasi arsitektur. Lakukan pemisahan dengan prinsip **SRP** (Single Responsibility Principle) dan **Bounded Context**.

---

## 9. Gradle Protection
Konfigurasi Gradle (seperti `build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`) **terkunci** secara *default*. Perubahan Gradle hanya diizinkan jika diperintahkan secara eksplisit oleh pengguna.

---

## 10. Source of Truth
- Struktur Modul: Diverifikasi terhadap `settings.gradle.kts`.
- Aturan Boundary & Tanggung Jawab: Diverifikasi terhadap `ownership.md` (tercermin di `struktur.md` ini).
- Validasi Nyata: Diverifikasi terhadap *source code* aktual dan hasil *build*.

---

## 11. Prinsip Utama WKW Xplore

```text
     UI
      ↓
Domain / UseCase
      ↓
 Contract / API
      ↓
 Infrastructure
```

- **ONE FEATURE = DOMAIN / ENGINE + UI**
- **SRP:** Setiap modul dan class punya satu alasan untuk berubah.
- **Isolasi UI:** UI tidak tahu *implementation detail* dari *storage*.
- **Isolasi Domain:** Domain tidak tahu *Presentation/UI*.
- **Pusat Infrastruktur:** Physical I/O dan Persistence memiliki satu *boundary* yang jelas.
