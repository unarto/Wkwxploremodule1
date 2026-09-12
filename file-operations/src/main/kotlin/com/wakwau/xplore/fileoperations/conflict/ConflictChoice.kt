// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/conflict/ConflictChoice.kt
// [Penjelasan]: Pilihan strategi penyelesaian benturan nama berkas atau direktori saat operasi salin/pindah.
package com.wakwau.xplore.fileoperations.conflict

enum class ConflictChoice {
    SKIP,
    OVERWRITE,
    RENAME
}
