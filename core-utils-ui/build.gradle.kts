// [Jalur Class/Modul]: core-ui/build.gradle.kts
// [Penjelasan]: Menambahkan dependensi activity-compose, lifecycle-runtime-compose, dan testImplementation(libs.junit) untuk mendukung implementasi permission request helper di Compose UI.
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
}

android {
  // [Jalur Class/Modul]: core-utils-ui/build.gradle.kts
  // [Penjelasan]: Menyesuaikan namespace Android library menjadi com.wakwau.xplore.core.utils.ui sesuai nama modul baru :core-utils-ui.
  namespace = "com.wakwau.xplore.core.utils.ui"
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

dependencies {
  // [Jalur Class/Modul]: core-utils-ui/build.gradle.kts
  // [Penjelasan]: Menggunakan api(project(":core-utils")) agar tipe data dan utilitas presentasi dasar (seperti FileCategory, ByteFormatter, DateFormatter) terekspos secara publik ke konsumen layer presentasi (mis. :search-ui, :filemanager-ui).
  api(project(":core-utils"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.core.ktx)
  testImplementation(libs.junit)
  debugImplementation(libs.androidx.compose.ui.tooling)
}

