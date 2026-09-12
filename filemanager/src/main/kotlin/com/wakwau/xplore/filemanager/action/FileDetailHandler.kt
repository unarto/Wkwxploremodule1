// [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/action/FileDetailHandler.kt
// [Penjelasan]: Domain Action Handler untuk membaca metadata berkas dan mengoordinasikan kalkulasi checksum via ComputeFileChecksumUseCase tanpa direct UI coupling.
package com.wakwau.xplore.filemanager.action

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.usecase.GetFileDetailedMetadataUseCase
import com.wakwau.xplore.filemanager.usecase.ComputeFileChecksumUseCase
import kotlinx.coroutines.CancellationException

class FileDetailHandler(
    private val getFileDetailedMetadataUseCase: GetFileDetailedMetadataUseCase,
    private val computeFileChecksumUseCase: ComputeFileChecksumUseCase,
    private val dispatch: (DualPaneEvent) -> Unit
) {
    suspend fun loadDetails(item: FileItem) {
        dispatch(DualPaneEvent.FileDetailsLoadingStarted)
        try {
            val metadata = getFileDetailedMetadataUseCase.invoke(item.location)
            dispatch(DualPaneEvent.FileDetailsLoaded(metadata))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            dispatch(DualPaneEvent.FileDetailsFailed(errorMessage = e.message ?: "Failed to load file details"))
        }
    }

    suspend fun computeChecksum(item: FileItem) {
        dispatch(DualPaneEvent.ChecksumCalculationStarted)
        try {
            val checksum = computeFileChecksumUseCase(item.location)
            dispatch(DualPaneEvent.ChecksumCalculated(checksum))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            dispatch(DualPaneEvent.ChecksumCalculationFailed(errorMessage = e.message ?: "Failed to calculate checksum"))
        }
    }
}
