// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/preferences/SettingsState.kt
// [Penjelasan]: Model data immutable preferensi aplikasi (tema, bahasa, mode akses sistem berkas, dan flag root read-only).
package com.wakwau.xplore.core.storage.preferences

data class SettingsState(
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val fileSystemAccessMode: FileSystemAccessMode = FileSystemAccessMode.NORMAL,
    val isRootReadOnly: Boolean = true
)

