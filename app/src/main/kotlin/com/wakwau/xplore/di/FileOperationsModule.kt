// [Modul: :app] [Jalur Class]: app/src/main/kotlin/com/wakwau/xplore/di/FileOperationsModule.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.di

import com.wakwau.xplore.fileoperations.conflict.ConflictDetector
import com.wakwau.xplore.fileoperations.conflict.ConflictResolver
import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.fileoperations.client.BackgroundOperationClient
import com.wakwau.xplore.core.storage.repository.DirectoryRepository
import com.wakwau.xplore.core.storage.repository.FileRepository

import com.wakwau.xplore.fileoperations.cancel.CancelOperationUseCase
import com.wakwau.xplore.fileoperations.conflict.DefaultConflictDetector
import com.wakwau.xplore.fileoperations.conflict.DefaultConflictResolver
import com.wakwau.xplore.fileoperations.conflict.DetectConflictsUseCase
import com.wakwau.xplore.fileoperations.conflict.ResolveTransferUseCase
import com.wakwau.xplore.fileoperations.copy.CopyFilesUseCase
import com.wakwau.xplore.fileoperations.delete.DeleteFilesUseCase
import com.wakwau.xplore.fileoperations.move.MoveFilesUseCase
import com.wakwau.xplore.fileoperations.rename.RenameFileUseCase

class FileOperationsModule(
    private val directoryRepository: DirectoryRepository,
    private val detailedMetadataReader: DetailedMetadataReader,
    val fileRepository: FileRepository,
    private val backgroundOperationClient: BackgroundOperationClient
) {
    val conflictDetector: ConflictDetector by lazy {
        DefaultConflictDetector(
            directoryRepository = directoryRepository,
            detailedMetadataReader = detailedMetadataReader
        )
    }

    val conflictResolver: ConflictResolver by lazy {
        DefaultConflictResolver()
    }

    val copyFilesUseCase: CopyFilesUseCase by lazy {
        CopyFilesUseCase(backgroundOperationClient)
    }

    val moveFilesUseCase: MoveFilesUseCase by lazy {
        MoveFilesUseCase(backgroundOperationClient)
    }

    val deleteFilesUseCase: DeleteFilesUseCase by lazy {
        DeleteFilesUseCase(backgroundOperationClient)
    }

    val renameFileUseCase: RenameFileUseCase by lazy {
        RenameFileUseCase(fileRepository)
    }

    val cancelOperationUseCase: CancelOperationUseCase by lazy {
        CancelOperationUseCase(backgroundOperationClient)
    }

    val detectConflictsUseCase: DetectConflictsUseCase by lazy {
        DetectConflictsUseCase(conflictDetector)
    }

    val resolveTransferUseCase: ResolveTransferUseCase by lazy {
        // [Jalur Class/Modul]: app/src/main/java/com/wakwau/xplore/di/FileOperationsModule.kt
        // [Penjelasan]: Menyediakan ResolveTransferUseCase dengan injeksi DetailedMetadataReader untuk deteksi tipe node direktori/berkas yang akurat.
        ResolveTransferUseCase(conflictDetector, conflictResolver, detailedMetadataReader)
    }
}
