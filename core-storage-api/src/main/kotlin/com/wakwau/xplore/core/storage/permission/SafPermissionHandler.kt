// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/permission/SafPermissionHandler.kt
// [Penjelasan]: Interface untuk mengelola (add/remove/check) persistable URI permission SAF secara dinamis.
package com.wakwau.xplore.core.storage.permission

interface SafPermissionHandler {
    fun takePersistableUriPermission(uriString: String)
    fun releasePersistableUriPermission(uriString: String)
    fun hasPersistedPermission(uriOrPath: String): Boolean
}

