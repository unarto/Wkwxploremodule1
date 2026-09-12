// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/preferences/AppLanguage.kt
// [Penjelasan]: Enum konfigurasi bahasa aplikasi yang didukung (System, Indonesian, English).
package com.wakwau.xplore.core.storage.preferences

enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "Sistem (Default)"),
    INDONESIAN("id", "Bahasa Indonesia"),
    ENGLISH("en", "English")
}
