package com.wakwau.xplore.core.storage.provider.volume

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class StorageVolumeChangeMonitor(context: Context) {
    internal val applicationContext: Context = context.applicationContext

    val changes: Flow<Unit> = callbackFlow {
        val receiver = StorageVolumeBroadcastReceiver { trySend(Unit) }
        receiver.register(applicationContext)
        trySend(Unit)
        awaitClose { receiver.unregister() }
    }
}
