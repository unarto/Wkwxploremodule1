// [Modul: :app] [Jalur Class]: app/src/main/kotlin/com/wakwau/xplore/di/FileManagerUseCaseModule.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.di

import com.wakwau.xplore.core.storage.checksum.FileChecksumReader
import com.wakwau.xplore.core.storage.metadata.DetailedMetadataReader
import com.wakwau.xplore.core.storage.permission.SafPermissionHandler
import com.wakwau.xplore.core.storage.permission.StoragePermissionChecker
import com.wakwau.xplore.core.storage.preferences.AppPreferencesRepository
import com.wakwau.xplore.core.storage.repository.DirectoryRepository
import com.wakwau.xplore.core.storage.repository.StorageVolumeRepository
import com.wakwau.xplore.core.storage.search.FileSearchService
import com.wakwau.xplore.filemanager.factory.FileTreeItemFactory
import com.wakwau.xplore.filemanager.usecase.CheckStoragePermissionUseCase
import com.wakwau.xplore.filemanager.usecase.ComputeFileChecksumUseCase
import com.wakwau.xplore.filemanager.usecase.CreateDirectoryUseCase
import com.wakwau.xplore.filemanager.usecase.GetFileDetailedMetadataUseCase
import com.wakwau.xplore.filemanager.usecase.GetParentLocationUseCase
import com.wakwau.xplore.filemanager.usecase.GetStorageVolumesUseCase
import com.wakwau.xplore.filemanager.usecase.LinkStorageUseCase
import com.wakwau.xplore.filemanager.usecase.ListDirectoryUseCase
import com.wakwau.xplore.search.usecase.SearchFilesUseCase
import com.wakwau.xplore.filemanager.usecase.ToggleShowHiddenFilesUseCase
import com.wakwau.xplore.fileoperations.cancel.CancelOperationUseCase
import com.wakwau.xplore.fileoperations.conflict.DetectConflictsUseCase
import com.wakwau.xplore.fileoperations.conflict.ResolveTransferUseCase
import com.wakwau.xplore.fileoperations.copy.CopyFilesUseCase
import com.wakwau.xplore.fileoperations.delete.DeleteFilesUseCase
import com.wakwau.xplore.fileoperations.move.MoveFilesUseCase
import com.wakwau.xplore.fileoperations.rename.RenameFileUseCase

class FileManagerUseCaseModule(
    private val directoryRepository: DirectoryRepository,
    private val storageVolumeRepository: StorageVolumeRepository,
    private val storagePermissionChecker: StoragePermissionChecker,
    private val detailedMetadataReader: DetailedMetadataReader,
    private val appPreferencesRepository: AppPreferencesRepository,
    private val fileSearchService: FileSearchService,
    private val fileOperationsModule: FileOperationsModule,
    private val safPermissionHandler: SafPermissionHandler,
    private val fileChecksumReader: FileChecksumReader
) {
    val getStorageVolumesUseCase: GetStorageVolumesUseCase by lazy { GetStorageVolumesUseCase(storageVolumeRepository) }
    val checkStoragePermissionUseCase: CheckStoragePermissionUseCase by lazy { CheckStoragePermissionUseCase(storagePermissionChecker) }
    val getParentLocationUseCase: GetParentLocationUseCase by lazy { GetParentLocationUseCase() }
    val fileTreeItemFactory: FileTreeItemFactory by lazy { FileTreeItemFactory() }
    
    val listDirectoryUseCase: ListDirectoryUseCase by lazy { ListDirectoryUseCase(directoryRepository, appPreferencesRepository) }
    // [Modul: :app] [Jalur Class]: app/src/main/kotlin/com/wakwau/xplore/di/FileManagerUseCaseModule.kt
    // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
    val createDirectoryUseCase: CreateDirectoryUseCase by lazy { CreateDirectoryUseCase(fileOperationsModule.fileRepository) }
    val linkStorageUseCase: LinkStorageUseCase by lazy { LinkStorageUseCase(safPermissionHandler) }
    val getFileDetailedMetadataUseCase: GetFileDetailedMetadataUseCase by lazy { GetFileDetailedMetadataUseCase(detailedMetadataReader) }
    val toggleShowHiddenFilesUseCase: ToggleShowHiddenFilesUseCase by lazy { ToggleShowHiddenFilesUseCase(appPreferencesRepository) }
    val searchFilesUseCase: SearchFilesUseCase by lazy { SearchFilesUseCase(fileSearchService) }
    val computeFileChecksumUseCase: ComputeFileChecksumUseCase by lazy { ComputeFileChecksumUseCase(fileChecksumReader) }

    // [Jalur Class/Modul]: app/src/main/java/com/wakwau/xplore/di/FileManagerUseCaseModule.kt
    // [Penjelasan]: Delegasi operasi berkas langsung ke :file-operations Execution Engine.
    val copyFilesUseCase: CopyFilesUseCase get() = fileOperationsModule.copyFilesUseCase
    val moveFilesUseCase: MoveFilesUseCase get() = fileOperationsModule.moveFilesUseCase
    val deleteFilesUseCase: DeleteFilesUseCase get() = fileOperationsModule.deleteFilesUseCase
    val renameFileUseCase: RenameFileUseCase get() = fileOperationsModule.renameFileUseCase
    val cancelOperationUseCase: CancelOperationUseCase get() = fileOperationsModule.cancelOperationUseCase
    val detectConflictsUseCase: DetectConflictsUseCase get() = fileOperationsModule.detectConflictsUseCase
    val resolveTransferUseCase: ResolveTransferUseCase get() = fileOperationsModule.resolveTransferUseCase
}
