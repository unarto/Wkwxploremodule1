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
  // [Jalur Class/Modul]: file-operations/build.gradle.kts
  // [Penjelasan]: file-operations adalah Execution Engine yang bergantung pada :core dan abstraksi :core-storage-api murni.
  implementation(project(":core-utils"))
  implementation(project(":core-storage-api"))
  
  implementation(libs.kotlinx.coroutines.core)
  
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
