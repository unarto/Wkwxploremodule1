// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/provider/ShareableUriResolverProvider.kt
// [Penjelasan]: Antarmuka penyedia dependensi ShareableUriResolver untuk diimplementasikan oleh Application context atau DI provider tanpa Service Locator global.
package com.wakwau.xplore.core.storage.provider

interface ShareableUriResolverProvider {
    val shareableUriResolver: ShareableUriResolver
}
