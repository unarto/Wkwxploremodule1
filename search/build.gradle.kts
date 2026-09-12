// [Jalur Class/Modul]: search/build.gradle.kts
// [Penjelasan]: Modul :search adalah Feature Engine untuk logika pencarian dan sinkronisasi indeks, murni Kotlin JVM dan bergantung hanya pada foundation :core dan :core-storage-api.
plugins {
  alias(libs.plugins.kotlin.jvm)
}

java {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
  jvmToolchain(11)
}

dependencies {
  // [Jalur Class/Modul]: search/build.gradle.kts
  // [Penjelasan]: search adalah Feature Engine yang bergantung pada :core dan abstraksi :core-storage-api murni.
  implementation(project(":core-utils"))
  implementation(project(":core-storage-api"))
  
  implementation(libs.kotlinx.coroutines.core)
  
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
