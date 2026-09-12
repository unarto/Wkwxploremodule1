// [Jalur Class/Modul]: settings-ui/src/main/kotlin/com/wakwau/xplore/settings/ui/model/SettingsItem.kt
// [Penjelasan]: Model data item pohon pengaturan untuk opsi tema, bahasa, mode akses sistem berkas, dan root read-only.
package com.wakwau.xplore.settings.ui.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.wakwau.xplore.core.storage.preferences.AppLanguage
import com.wakwau.xplore.core.storage.preferences.AppThemeMode
import com.wakwau.xplore.core.storage.preferences.FileSystemAccessMode

sealed class SettingsItem {
    data class Group(val id: String, @get:StringRes val titleRes: Int, val icon: ImageVector) : SettingsItem()
    data class ThemeOption(val mode: AppThemeMode, @get:StringRes val labelRes: Int, val isSelected: Boolean) : SettingsItem()
    data class LanguageOption(val language: AppLanguage, @get:StringRes val labelRes: Int, val isSelected: Boolean) : SettingsItem()
    data class FileSystemAccessOption(val mode: FileSystemAccessMode, @get:StringRes val labelRes: Int, val isSelected: Boolean) : SettingsItem()
    data class RootReadOnlyOption(@get:StringRes val labelRes: Int, val isChecked: Boolean) : SettingsItem()
}
