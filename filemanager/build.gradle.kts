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
  api(project(":core-storage-api"))
  api(project(":core-utils"))
  implementation(project(":file-operations"))
  // [Jalur Class/Modul]: filemanager/build.gradle.kts
  // [Penjelasan]: Menggunakan api(project(":search")) karena DualPaneState pada :filemanager mengekspos SearchUiState ke consumer (:filemanager-ui), sehingga :filemanager-ui tidak perlu deklarasi direct dependency ke :search.
  api(project(":search"))
  
  implementation(libs.kotlinx.coroutines.core)
  
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
