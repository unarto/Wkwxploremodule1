// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/provider/volume/StorageVolumeBroadcastReceiver.kt
// [Penjelasan]: BroadcastReceiver terisolasi untuk mendengarkan perubahan status media penyimpanan sistem (mount, unmount, eject) dan memicu callback penyegaran volume.
package com.wakwau.xplore.core.storage.provider.volume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.wakwau.xplore.core.storage.constant.StorageConstants

class StorageVolumeBroadcastReceiver(
    private val onVolumeChanged: () -> Unit
) : BroadcastReceiver() {
    private var registeredContext: Context? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        onVolumeChanged()
    }

    @Synchronized
    fun register(context: Context) {
        if (registeredContext != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addDataScheme(StorageConstants.FILE_SCHEME)
        }
        context.registerReceiver(this, filter)
        registeredContext = context
    }

    @Synchronized
    fun unregister() {
        val context = registeredContext ?: return
        context.unregisterReceiver(this)
        registeredContext = null
    }
}
