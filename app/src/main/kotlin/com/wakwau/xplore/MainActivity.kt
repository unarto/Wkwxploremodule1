// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/MainActivity.kt
// [Penjelasan]: Activity utama aplikasi yang menginisialisasi tema dan merender XploreRoot dari paket UI.

package com.wakwau.xplore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.wakwau.xplore.core.storage.preferences.AppThemeMode
import com.wakwau.xplore.core.utils.ui.theme.WKWXploreTheme
import com.wakwau.xplore.settings.ui.engine.SettingsViewModel
import com.wakwau.xplore.ui.XploreRoot

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val app = applicationContext as XploreApplication
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = app.appCompositionRoot.settingsViewModelFactory
            )
            val settingsState by settingsViewModel.settingsState.collectAsStateWithLifecycle()

            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (settingsState.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemDark
            }

            WKWXploreTheme(darkTheme = isDarkTheme) {
                XploreRoot(settingsViewModel = settingsViewModel)
            }
        }
    }
}
