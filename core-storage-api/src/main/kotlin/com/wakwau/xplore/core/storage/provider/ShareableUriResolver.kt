// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/provider/ShareableUriResolver.kt
// [Penjelasan]: Kontrak interface abstraksi penyedia URI yang aman untuk dibagikan ke aplikasi eksternal (misal via Android FileProvider untuk berkas lokal atau Content URI untuk SAF) beserta pengecekan keberadaan berkas fisik tanpa mengekspos java.io.File atau raw physical I/O ke modul UI/Domain.
package com.wakwau.xplore.core.storage.provider

interface ShareableUriResolver {
    fun resolveShareableUri(filePath: String): String?
}
