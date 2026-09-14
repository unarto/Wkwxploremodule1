// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/repository/StorageVolumeRepositoryImpl.kt
// [Penjelasan]: Repository volume application-scoped dengan receiver aktif hanya ketika flow memiliki collector.
package com.wakwau.xplore.core.storage.repository

import android.content.Context
import com.wakwau.xplore.core.storage.model.StorageVolumeItem
import com.wakwau.xplore.core.storage.model.StorageVolumeType
import com.wakwau.xplore.core.storage.provider.volume.ExternalVolumeProvider
import com.wakwau.xplore.core.storage.provider.volume.InternalVolumeProvider
import com.wakwau.xplore.core.storage.provider.volume.RootVolumeProvider
import com.wakwau.xplore.core.storage.provider.volume.SafVolumeProvider
import com.wakwau.xplore.core.storage.provider.volume.StorageVolumeChangeMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

class StorageVolumeRepositoryImpl internal constructor(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
    private val loadVolumes: suspend () -> List<StorageVolumeItem>
) : StorageVolumeRepository {
    constructor(
        context: Context,
        internalVolumeProvider: InternalVolumeProvider,
        externalVolumeProvider: ExternalVolumeProvider,
        rootVolumeProvider: RootVolumeProvider,
        safVolumeProvider: SafVolumeProvider,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    ) : this(
        context = context.applicationContext,
        ioDispatcher = ioDispatcher,
        loadVolumes = {
            buildVolumeList(
                internalVolumeProvider,
                externalVolumeProvider,
                rootVolumeProvider,
                safVolumeProvider
            )
        }
    )

    private val monitor = StorageVolumeChangeMonitor(context)
    private val repositoryJob = SupervisorJob()
    private val repositoryScope = CoroutineScope(repositoryJob + ioDispatcher)
    private val manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val volumes = merge(monitor.changes, manualRefresh)
        .mapLatest {
            currentCoroutineContext().ensureActive()
            loadVolumes()
        }
        .stateIn(
            scope = repositoryScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
            initialValue = emptyList()
        )

    override fun getVolumes(): Flow<List<StorageVolumeItem>> = volumes

    override suspend fun refreshVolumes() {
        currentCoroutineContext().ensureActive()
        manualRefresh.emit(Unit)
    }

    override fun close() {
        repositoryScope.cancel()
    }

    internal val lifecycleJob: Job
        get() = repositoryJob

    private companion object {
        suspend fun buildVolumeList(
            internalVolumeProvider: InternalVolumeProvider,
            externalVolumeProvider: ExternalVolumeProvider,
            rootVolumeProvider: RootVolumeProvider,
            safVolumeProvider: SafVolumeProvider
        ): List<StorageVolumeItem> {
            val volumes = mutableListOf<StorageVolumeItem>()
            val internalVolume = internalVolumeProvider.getInternalVolume()
            volumes += internalVolume
            volumes += externalVolumeProvider.getExternalVolumes(internalVolume.rootPath)
            volumes += rootVolumeProvider.getRootVolume()
            volumes += safVolumeProvider.getSafVolumes()
            return volumes.sortedWith(
                compareBy<StorageVolumeItem> { orderForType(it.type) }
                    .thenBy { it.createdAt }
            )
        }

        fun orderForType(type: StorageVolumeType): Int = when (type) {
            StorageVolumeType.PRIMARY_INTERNAL -> 1
            StorageVolumeType.SECONDARY_SDCARD -> 2
            StorageVolumeType.USB_OTG -> 3
            StorageVolumeType.ROOT -> 4
            StorageVolumeType.SAF_PROVIDER -> 5
            StorageVolumeType.UNKNOWN -> 6
        }
    }
}
