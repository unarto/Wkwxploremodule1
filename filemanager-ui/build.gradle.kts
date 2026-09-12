plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.wakwau.xplore.filemanager.ui"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
  }
}

// [Jalur Class/Modul]: filemanager-ui/build.gradle.kts
// [Penjelasan]: Menghapus dependency berlebih project(":search") sesuai ownership.md dan filemanagerUI.md (Temuan 2.1). :filemanager-ui hanya bergantung pada :filemanager, :core-utils-ui, dan :treeview.
dependencies {
  implementation(project(":filemanager"))
  implementation(project(":core-utils-ui"))
  implementation(project(":treeview"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)

  debugImplementation(libs.androidx.compose.ui.tooling)
}
