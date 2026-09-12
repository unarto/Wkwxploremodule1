// [Jalur Class/Modul]: core-worker/src/main/kotlin/com/wakwau/xplore/core/worker/service/FileOperationNotificationManager.kt
// [Penjelasan]: Manager isolasi untuk pembuatan channel dan notifikasi progres operasi berkas, menghapus tanggungan notifikasi dari Service utama.
package com.wakwau.xplore.core.worker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wakwau.xplore.core.worker.R

class FileOperationNotificationManager(private val context: Context) {
    
    val channelId = "file_operation_channel"
    val notificationId = 1234
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = context.getString(R.string.notification_channel_name)
            val channelDesc = context.getString(R.string.notification_channel_desc)
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = channelDesc
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun createNotification(content: String, progress: Int, max: Int): Notification {
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(context.getString(R.string.notification_title_processing))
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .build()
    }

    fun notifyProgress(notification: Notification) {
        manager.notify(notificationId, notification)
    }
}
