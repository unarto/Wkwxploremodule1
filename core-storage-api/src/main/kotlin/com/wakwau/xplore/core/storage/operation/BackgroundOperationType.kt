// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/operation/BackgroundOperationClient.kt
// [Penjelasan]: Kontrak antarmuka klien operasi latar belakang untuk menangani operasi I/O (Copy, Move, Delete) dengan dukungan transfer ter-resolve (Conflict Resolution) dan pembatalan operasi tanpa pola Manager.
package com.wakwau.xplore.core.storage.operation



enum class BackgroundOperationType { COPY, MOVE, DELETE }


