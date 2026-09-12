// [Jalur Class/Modul]: /file-system/build.gradle.kts
// [Penjelasan]: Modul baru khusus untuk operasi FileSystem fisik (Local, SAF, Root/SU, Shizuku) sesuai restrukturisasi arsitektur.
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.wakwau.xplore.filesystem"
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
  implementation(project(":core-utils"))
  api(project(":core-storage-api"))
  
  implementation(libs.shizuku.api)
  implementation(libs.shizuku.provider)
  implementation(libs.libsu.core)
  implementation(libs.libsu.io)
  implementation(libs.libsu.nio)
  
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.documentfile)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
