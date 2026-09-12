pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // [Jalur Class/Modul]: /settings.gradle.kts
    // [Penjelasan]: Menambahkan repositori JitPack untuk resolusi pustaka libsu Topjohnwu.
    maven { url = java.net.URI("https://jitpack.io") }
  }
}

rootProject.name = "WKW Xplore"

include(":app")
include(":core-worker")
include(":core-utils")
include(":core-utils-ui")
include(":core-storage-api")
include(":core-storage")
include(":file-system")
include(":file-operations")
include(":file-operations-ui")
include(":file-operations-bridge")
include(":settings-ui")
include(":filemanager")
include(":filemanager-ui")
include(":treeview")
include(":search")
include(":search-ui")
