WKW Xplore — Module Ownership & Architecture Boundary

Dokumen ini menjadi acuan resmi ownership, responsibility, boundary, dan dependency direction untuk project WKW Xplore.

Dokumen ini mengikuti struktur modul aktual yang terdaftar di "settings.gradle.kts".

---

1. Module Inventory

Project WKW Xplore saat ini memiliki 16 modul:

:app
:core-worker
:core-utils
:core-utils-ui
:core-storage-api
:core-storage
:file-system
:file-operations
:file-operations-ui
:file-operations-bridge
:settings-ui
:filemanager
:filemanager-ui
:treeview
:search
:search-ui

Tidak boleh mengasumsikan adanya modul lain yang belum terdaftar di "settings.gradle.kts".

---

2. Module Ownership

":app"

Layer: Composition Root / Application

Ownership

- Application entry point.
- "Application".
- "Activity" entry point.
- Root navigation.
- Dependency Injection / Composition Root.
- Wiring implementation dengan contract/domain.
- Perakitan seluruh feature UI dan infrastructure.

Allowed

- Android framework.
- Compose application entry point.
- Dependency injection.
- Wiring dependency antar-modul.

Forbidden

- Business logic.
- File operation engine.
- Search algorithm.
- Raw filesystem I/O.
- Room DAO access sebagai business logic.
- Implementasi Local/SAF/Root/Shizuku filesystem.

":app" hanya merakit, bukan menjalankan domain logic.

---

3. Foundation Modules

":core-utils"

Layer: Foundation / Pure Kotlin

Ownership

Utilitas generik yang dapat digunakan lintas modul.

Contoh:

- Result wrapper.
- Error/helper types.
- Logger abstraction.
- Date/time formatting.
- Generic utility functions.

Allowed

- Pure Kotlin.
- Generic utilities.

Forbidden

- Compose.
- Android UI.
- FileSystem implementation.
- Room.
- MMKV-specific logic.
- Feature-specific business logic.

Dependency Rule

":core-utils" tidak boleh bergantung pada feature module.

---

":core-utils-ui"

Layer: Design System

Ownership

Komponen UI generik dan design system aplikasi.

Contoh:

- Theme.
- Colors.
- Typography.
- Generic Dialog.
- Generic UI components.
- Breadcrumb.
- Storage-related generic visual components.
- File icon renderer yang bersifat generik.

Allowed

- Jetpack Compose.
- UI resources.
- Dependency ke ":core-utils" jika diperlukan.

Forbidden

- File browsing logic.
- Search logic.
- File operation logic.
- Storage implementation.
- Room/MMKV access.
- Feature-specific business rules.

---

4. Storage Boundary

":core-storage-api"

Layer: Domain Contract / Storage Boundary

Modul ini merupakan abstraction boundary antara domain dan implementasi storage.

Ownership

Models

Contoh:

- "FileItem"
- "FileType"
- "FileMetadata"
- "StorageLocation"
- "StorageVolume"
- Operation result/progress models.
- Storage permission models.

Contracts

Contoh:

- "FileSystem"
- "FileRepository"
- "DirectoryRepository"
- "StorageRepository"
- Storage provider contracts.
- SAF permission contracts.
- Metadata reader contracts.
- Storage permission checker contracts.

Allowed

- Pure Kotlin contracts.
- Domain models.
- Interfaces.
- Enums/value objects.
- Immutable data classes.

Forbidden

- "java.io.File" sebagai implementasi I/O.
- Room.
- MMKV.
- "Context".
- "ContentResolver".
- Compose.
- Concrete filesystem implementation.
- Concrete database implementation.

Prinsip

Domain
   ↓
core-storage-api
   ↑
Implementations

"core-storage-api" mendefinisikan apa yang tersedia, bukan bagaimana storage bekerja.

---

":core-storage"

Layer: Persistence / Data

Ownership

Persistence terstruktur dan key-value.

Contoh:

- Room Database.
- DAO.
- Entity.
- Database implementation.
- MMKV preference implementation.
- Persistence repository implementation.
- Mapping persistence ↔ domain model.

Allowed

- Room.
- MMKV.
- Persistence implementation.
- Android persistence APIs jika memang diperlukan.

Forbidden

- Local filesystem engine.
- SAF filesystem engine.
- Root/SU filesystem engine.
- Shizuku filesystem engine.
- Compose UI.
- File browsing UI.

Prinsip

core-storage
    ↓
Persistence
    ├── Room
    └── MMKV

"core-storage" bukan pemilik physical File I/O.

---

5. Physical FileSystem

":file-system"

Layer: Infrastructure / Physical File I/O

Modul ini adalah pemilik implementasi akses filesystem nyata.

Ownership

Local

com.wakwau.xplore.storage.local

- LocalFileSystem.
- Local file listing.
- Local file creation.
- Local delete.
- Local rename.
- Local stream access.

SAF

com.wakwau.xplore.storage.saf

- SafFileSystem.
- DocumentFile/ContentResolver integration.
- SAF URI resolution.
- Persisted SAF permission integration.

Root

com.wakwau.xplore.storage.root

- RootFileSystem.
- SU/libsu access.
- Root filesystem operations.

Shizuku

Implementasi filesystem berbasis Shizuku apabila digunakan oleh project.

Mapper

com.wakwau.xplore.storage.mapper

- OS filesystem object → domain "FileItem".
- Metadata → domain model.

Allowed

- "java.io.File".
- "FileInputStream".
- "FileOutputStream".
- "ContentResolver".
- "DocumentFile".
- Root/SU APIs.
- Shizuku APIs.
- Physical filesystem I/O.

Forbidden

- Compose UI.
- File manager screen.
- Search UI.
- Business UI logic.

Boundary

UI/domain tidak boleh mengetahui implementasi concrete filesystem.

Akses dilakukan melalui contract dari:

:core-storage-api

---

6. Background Execution

":core-worker"

Layer: Background Execution / Android Runtime

Ownership

Menjalankan pekerjaan file operation yang berlangsung lama.

Contoh:

- Foreground Service.
- WorkManager jika digunakan.
- Notification.
- Wake lock.
- Operation lifecycle.
- Progress dispatch.
- Background operation client.

Allowed

- Android Service.
- WorkManager.
- Notification.
- Coroutine/background execution.
- Memanggil domain UseCase.

Forbidden

- Menjadi pemilik business logic Copy/Move/Delete/Rename.
- Mengimplementasikan filesystem engine sendiri.
- Raw filesystem algorithm yang seharusnya berada di domain/infrastructure.

Prinsip

core-worker
     ↓
Domain UseCase
     ↓
Storage Contract
     ↓
Filesystem Implementation

Worker adalah executor host, bukan business engine.

---

7. File Operations

":file-operations"

Layer: Domain Logic

Pemilik seluruh business logic mutasi file.

Ownership

copy/
move/
delete/
rename/
cancel/
conflict/
batch/

Copy

CopyFilesUseCase

Move

MoveFilesUseCase

Delete

DeleteFilesUseCase

Rename

RenameFileUseCase

Cancel

CancelOperationUseCase

Conflict

DefaultConflictDetector
DefaultConflictResolver
DetectConflictsUseCase
ResolveTransferUseCase

Batch

BatchOperationCalculator
BatchOperationUseCase

Allowed

- Pure Kotlin.
- Business rules.
- UseCase.
- Conflict resolution.
- Transfer planning.
- Operation result handling.
- Dependency terhadap storage contracts.

Forbidden

- Compose.
- Android "Context".
- Android lifecycle.
- Direct "java.io.File" I/O.
- Room.
- MMKV.
- Concrete filesystem implementation.

Prinsip

":file-operations" menentukan apa yang harus dilakukan, bukan melakukan physical I/O secara langsung.

---

8. File Operations UI

":file-operations-ui"

Layer: Presentation

Ownership

UI khusus operasi mutasi file.

Contoh:

ConflictResolutionDialog
ProgressDialog

Allowed

- Compose.
- UI state rendering.
- User interaction.
- Callback/event.
- Operation progress presentation.

Forbidden

- Raw file I/O.
- Direct filesystem access.
- Conflict algorithm.
- Copy/Move/Delete business logic.
- Direct Room access.

---

9. File Operations Bridge

":file-operations-bridge"

Layer: Bridge / Adapter

Ownership

Boundary antara caller/presenter dan background operation executor.

Contoh:

FileOperationBridge
FileOperationBridgeAdapter

Allowed

- Contract.
- Adapter.
- Delegation.
- Event forwarding.
- Non-blocking operation triggering.

Forbidden

- Business logic.
- Filesystem implementation.
- UI.
- Copy/Move/Delete algorithm.

Bridge hanya menghubungkan, bukan mengerjakan operasi.

---

10. File Manager Domain

":filemanager"

Layer: Domain Logic / Pure Kotlin

Pemilik business logic untuk browsing dan navigasi file manager.

Ownership

Contoh:

DualPaneStateHolder
DualPaneReducer
DualPaneIntent/Event
ListDirectoryUseCase
GetStorageVolumesUseCase

Allowed

- Pure Kotlin.
- MVI state.
- Reducer.
- Navigation state.
- Directory browsing UseCase.
- Storage contract dependency.
- File operation/search domain dependency jika memang dibutuhkan oleh domain.

Forbidden

- Compose.
- Activity.
- Fragment.
- Lifecycle.
- Android UI.
- Concrete filesystem implementation.
- Raw physical I/O.

---

11. File Manager UI

":filemanager-ui"

Layer: Presentation

Pemilik seluruh UI utama file manager.

Ownership

Contoh:

DualPaneFileManagerScreen
FileManagerTopBar
FileItem rendering
SideActionBar
DirectoryTreeView integration

Allowed

- Compose.
- UI state collection.
- Rendering.
- User interaction.
- Callback/event dispatch.
- Dependency terhadap ":filemanager".
- Dependency terhadap reusable UI components.

Forbidden

- Raw file I/O.
- Direct ":core-storage" implementation access.
- Room DAO.
- Filesystem implementation.
- Copy/Move/Delete engine.
- Search algorithm.
- Settings domain logic.

Catatan

"DirectoryTreeView" boleh digunakan sebagai reusable component, tetapi implementasi TreeView generik tetap dimiliki ":treeview".

---

12. TreeView

":treeview"

Layer: Reusable UI Component

Ownership

Generic tree rendering.

Contoh:

TreeNode<T>
ComposeTreeView
TreeState

Allowed

- Compose.
- Generic tree state.
- Node rendering.
- Expand/collapse.
- Selection.
- Indentation.
- Branch guides.
- Animation.

Forbidden

- "FileItem".
- "StorageLocation".
- Search logic.
- File operation logic.
- Dependency ke ":filemanager".
- Dependency ke feature module.

Prinsip

treeview
    ↓
Generic T

TreeView tidak boleh tahu bahwa node yang dirender adalah file atau directory.

---

13. Search Domain

":search"

Layer: Domain Logic / Search Engine

Pemilik seluruh business logic pencarian.

Ownership

Contoh:

FileSearchServiceImpl
FileSystemSearchTraversal
SearchQueryFilterMatcher
FileIndexSynchronizer
SearchFilesUseCase
SearchUiState

Allowed

- Pure Kotlin.
- Search algorithm.
- Directory traversal abstraction.
- Query matching.
- Regex matching.
- Search filtering.
- Search result state.
- Search UseCase.
- Storage API contract.

Forbidden

- Compose.
- Search Dialog UI.
- Android UI.
- Direct storage implementation.
- Raw database access.
- Direct filesystem implementation.

Prinsip

":search" adalah engine pencarian, bukan UI pencarian.

---

14. Search UI

":search-ui"

Layer: Presentation

Pemilik UI pencarian.

Ownership

Contoh:

FileSearchDialog
SearchHistoryDialog

Allowed

- Compose.
- Search input.
- Filter controls.
- Search history UI.
- UI state rendering.
- Callback/event forwarding.
- Dependency terhadap ":search".

Forbidden

- Search algorithm.
- Directory traversal.
- Raw filesystem I/O.
- Direct Room DAO.
- Direct ":core-storage" implementation.
- Business logic pencarian.

---

15. Settings UI

":settings-ui"

Layer: Presentation

Pemilik UI pengaturan aplikasi.

Ownership

Contoh:

SettingsTreeScreen
SettingsTreeEngine

Fitur:

- Theme.
- Language.
- Filesystem access mode.
- Root read-only.
- Pengaturan aplikasi lainnya.

Allowed

- Compose.
- Settings UI state.
- User interaction.
- Callback/event.
- Preference contract/domain yang diperlukan.

Forbidden

- File browsing.
- Filesystem implementation.
- Raw file I/O.
- Direct storage implementation.
- File operation logic.

---

16. Dependency Direction

Arah panah "→" berarti bergantung pada.

Application

:app
 ├── :filemanager-ui
 ├── :file-operations-ui
 ├── :settings-ui
 ├── :search-ui
 └── :core-worker

":app" adalah Composition Root.

---

File Manager

:filemanager-ui
 ├── :filemanager
 ├── :treeview
 └── :core-utils-ui

:filemanager
 ├── :core-storage-api
 └── :core-utils

---

Search

:search-ui
 ├── :search
 └── :core-utils-ui

:search
 ├── :core-storage-api
 └── :core-utils

---

File Operations

:file-operations-ui
 ├── :file-operations
 └── :core-utils-ui

:file-operations
 ├── :core-storage-api
 └── :core-utils

:file-operations-bridge
 └── abstraction/operation contracts

---

Storage

:core-storage
 ├── :core-storage-api
 └── :core-utils

:file-system
 ├── :core-storage-api
 └── :core-utils

---

Background

:core-worker
 ├── :file-operations
 └── :file-operations-bridge

---

17. Golden Dependency Rules

Rule 1 — UI tidak boleh bypass boundary

UI
 ↓
Domain / Contract
 ↓
Implementation

Bukan:

UI
 ↓
core-storage
 ↓
Room / filesystem

---

Rule 2 — Domain tidak boleh bergantung UI

Modul berikut tidak boleh memiliki dependency Compose/UI:

:filemanager
:file-operations
:search
:core-storage-api
:core-utils

---

Rule 3 — Physical I/O hanya di infrastructure

Physical filesystem I/O hanya dimiliki:

:file-system

Contoh:

- Local.
- SAF.
- Root.
- Shizuku.

---

Rule 4 — Persistence hanya di ":core-storage"

Room
MMKV
DAO
Entity
Persistence implementation

dimiliki ":core-storage".

---

Rule 5 — Worker bukan business engine

":core-worker" hanya menjalankan pekerjaan background dan memanggil domain UseCase.

---

Rule 6 — TreeView harus generic

":treeview" tidak boleh mengetahui:

FileItem
StorageLocation
FileRepository
Search
FileOperations

---

Rule 7 — Feature UI tidak boleh mencampur domain

search-ui       → presentation search
filemanager-ui  → presentation file manager
settings-ui     → presentation settings
file-operations-ui → presentation file operations

---

18. Package-by-Feature Rule

Untuk feature domain, gunakan package berdasarkan tanggung jawab.

":file-operations"

com.wakwau.xplore.fileoperations.copy
com.wakwau.xplore.fileoperations.move
com.wakwau.xplore.fileoperations.delete
com.wakwau.xplore.fileoperations.rename
com.wakwau.xplore.fileoperations.cancel
com.wakwau.xplore.fileoperations.conflict
com.wakwau.xplore.fileoperations.batch

":file-operations-ui"

com.wakwau.xplore.fileoperations.ui

":file-operations-bridge"

com.wakwau.xplore.fileoperations.bridge

":file-system"

com.wakwau.xplore.storage.local
com.wakwau.xplore.storage.saf
com.wakwau.xplore.storage.root
com.wakwau.xplore.storage.mapper

---

19. Refactoring Rule

Sebelum memindahkan class atau membuat module baru:

1. Audit usage.
2. Audit dependency.
3. Tentukan ownership.
4. Pastikan boundary.
5. Pindahkan class.
6. Perbaiki reference/import.
7. Build/test.
8. Verifikasi dependency graph.

Jangan memindahkan class hanya berdasarkan nama file.

---

20. Anti-God-Class Rule

Tidak boleh membuat satu class yang sekaligus menangani:

UI
+ State
+ Business Logic
+ Filesystem I/O
+ Database
+ Background Service
+ Navigation
+ Conflict Resolution

Jika satu class memiliki beberapa responsibility tersebut, lakukan audit SRP terlebih dahulu.

Jangan melakukan pemecahan class secara otomatis tanpa audit usage dan responsibility.

---

21. Gradle Protection

Gradle adalah bagian dari konfigurasi project dan terkunci secara default.

Jangan mengubah:

build.gradle
build.gradle.kts
settings.gradle
settings.gradle.kts
gradle.properties
libs.versions.toml
gradle/

kecuali perubahan Gradle diperintahkan secara eksplisit.

Audit, build, dan test Gradle diperbolehkan tanpa mengubah konfigurasi.

---

22. Source of Truth

Struktur modul harus selalu diverifikasi terhadap:

settings.gradle.kts

Ownership harus diverifikasi terhadap:

ownership.md

Sedangkan implementasi aktual harus diverifikasi terhadap:

source code
dependency graph
build/test result

Dokumentasi tidak boleh dianggap sebagai bukti bahwa implementasi sudah sesuai.

---

23. Status Saat Ini

Modul yang telah dinyatakan selesai dalam pembahasan sebelumnya:

:file-operations
:file-operations-ui
:file-operations-bridge
:settings-ui
:search
:search-ui

Status SELESAI berarti struktur/relokasi telah dikerjakan sebelumnya, bukan berarti otomatis bebas bug.

Setiap audit berikutnya tetap harus memeriksa source code aktual.

---

24. Prinsip Utama WKW Xplore

UI
 ↓
Domain / UseCase
 ↓
Contract / API
 ↓
Infrastructure

Dengan pemisahan:

Presentation
    ↓
Domain
    ↓
Contracts
    ↓
Infrastructure / Persistence

Tujuan utama:

- SRP.
- Dependency direction yang jelas.
- Tidak ada God Class.
- UI tidak mengetahui implementation detail.
- Domain tidak mengetahui UI.
- Physical I/O memiliki satu boundary.
- Persistence memiliki ownership yang jelas.
- Feature dapat diuji dan dikembangkan secara independen.
