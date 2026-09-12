// [Jalur Class/Modul]: search-ui/build.gradle.kts
// [Penjelasan]: Modul :search-ui adalah modul presentasi Jetpack Compose untuk dialog dan antarmuka pencarian berkas.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.wakwau.xplore.search.ui"
    compileSdk = 36

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
    // [Jalur Class/Modul]: search-ui/build.gradle.kts
    // [Penjelasan]: Ketergantungan modul presentasi search terhadap :search, :core-ui, dan :core-storage-api.
    implementation(project(":search"))
    implementation(project(":core-utils-ui"))
    implementation(project(":core-storage-api"))
    
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
