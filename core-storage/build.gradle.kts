// [Jalur Class/Modul]: /core-storage/build.gradle.kts
// [Penjelasan]: Menyelaraskan format compileSdk release(36) minorApiLevel=1 sesuai standar AGP dan mempertahankan dependensi Room, MMKV, Shizuku, serta DocumentFile.
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.wakwau.xplore.storage"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildFeatures {
    aidl = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  // [Jalur Class/Modul]: /core-storage/build.gradle.kts
  // [Penjelasan]: Menggunakan implementation scope untuk modul internal utilitas. Mempertahankan api untuk core-storage-api. Khusus MMKV dan Room.
  implementation(project(":core-utils"))
  api(project(":core-storage-api"))
  implementation(libs.mmkv)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.core.ktx)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

