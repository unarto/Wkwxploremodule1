// [Jalur Class/Modul]: file-operations/src/main/kotlin/com/wakwau/xplore/fileoperations/cancel/CancelOperationUseCase.kt
// [Penjelasan]: UseCase pembatalan operasi berkas yang sedang berjalan di modul file-operations via BackgroundOperationClient dalam package cancel sesuai ownership.md yang bergantung pada kontrak BackgroundOperationClient di domain :file-operations sesuai DIP.
package com.wakwau.xplore.fileoperations.cancel

import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient

class CancelOperationUseCase(
    private val backgroundOperationClient: BackgroundOperationClient
) {
    fun invoke() {
        backgroundOperationClient.cancelOperation()
    }
}
