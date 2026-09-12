// [Jalur Class/Modul]: file-operations-bridge/build.gradle.kts
// [Penjelasan]: Modul bridge murni Kotlin JVM sebagai adapter antara presenter/intent dengan eksekutor operasi tanpa business logic atau UI.
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
  implementation(project(":file-operations"))
  implementation(project(":core-storage-api"))
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
}
