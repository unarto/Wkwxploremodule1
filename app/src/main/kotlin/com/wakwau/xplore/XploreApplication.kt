// [Jalur Class/Modul]: app/src/main/kotlin/com/wakwau/xplore/XploreApplication.kt
// [Penjelasan]: Application entry-point yang mengimplementasikan FileOperationComponentProvider untuk menyuplai dependensi ke FileCopyService tanpa Service Locator.
package com.wakwau.xplore

import android.app.Application
import com.wakwau.xplore.core.storage.operation.FileOperationComponentProvider
import com.wakwau.xplore.core.storage.operation.FileOperationProgressDispatcher
import com.wakwau.xplore.core.storage.provider.ShareableUriResolver
import com.wakwau.xplore.core.storage.provider.ShareableUriResolverProvider
import com.wakwau.xplore.core.storage.repository.FileRepository
import com.wakwau.xplore.di.AppCompositionRoot
import com.wakwau.xplore.di.fileOperationProgressDispatcher

class XploreApplication : Application(), FileOperationComponentProvider, ShareableUriResolverProvider {
    lateinit var appCompositionRoot: AppCompositionRoot
        private set

    override val fileRepository: FileRepository
        get() = appCompositionRoot.storageModule.fileRepository

    override val operationProgressDispatcher: FileOperationProgressDispatcher
        get() = appCompositionRoot.fileOperationProgressDispatcher

    // [Jalur Class/Modul]: app/src/main/java/com/wakwau/xplore/XploreApplication.kt
    // [Penjelasan]: Menyediakan instance ShareableUriResolver dari StorageModule untuk resolusi URI aman di presenter/UI layer tanpa memaparkan physical file I/O langsung ke UI.
    override val shareableUriResolver: ShareableUriResolver
        get() = appCompositionRoot.storageModule.shareableUriResolver

    override fun onCreate() {
        super.onCreate()
        appCompositionRoot = AppCompositionRoot(this)
    }
}
