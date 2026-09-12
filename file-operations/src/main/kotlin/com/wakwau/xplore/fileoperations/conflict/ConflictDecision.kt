// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/ConflictDecision.kt
// [Penjelasan]: Keputusan pilihan resolusi benturan nama berkas/direktori dengan opsi applyToAll untuk menerapkan ke semua benturan berikutnya.
package com.wakwau.xplore.fileoperations.conflict

data class ConflictDecision(
    val choice: ConflictChoice,
    val applyToAll: Boolean = false
)
