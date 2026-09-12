// [Jalur Class/Modul]: file-system/src/main/kotlin/com/wakwau/xplore/core/storage/shizuku/ShizukuConnectionClient.kt
// [Penjelasan]: Klien untuk mengelola siklus hidup koneksi IPC Shizuku (linkToDeath, re-connect otomatis, synchronization, timeout) tanpa pola Manager.
package com.wakwau.xplore.core.storage.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

internal class ShizukuConnectionClient(
    private val packageName: String
) {
    private var serviceDeferred: CompletableDeferred<IPrivilegedFileService?>? = null
    private var currentService: IPrivilegedFileService? = null
    private var currentConnection: ServiceConnection? = null

    private val deathRecipient = IBinder.DeathRecipient {
        cleanupStaleConnection()
    }

    private fun cleanupStaleConnection() {
        val s: IPrivilegedFileService?
        val c: ServiceConnection?
        
        synchronized(this) {
            s = currentService
            c = currentConnection
            
            currentService = null
            currentConnection = null
            serviceDeferred = null
        }

        if (s != null) {
            try {
                s.asBinder().unlinkToDeath(deathRecipient, 0)
            } catch (e: Exception) {
                // Ignore exception on unlink
            }
        }
        
        if (c != null) {
            try {
                val args = Shizuku.UserServiceArgs(
                    ComponentName(packageName, PrivilegedFileService::class.java.name)
                ).daemon(false).processNameSuffix(ShizukuIpcConstants.PROCESS_NAME_SUFFIX)
                
                Shizuku.unbindUserService(args, c, true)
            } catch (e: Exception) {
                // Ignore unbind exceptions
            }
        }
    }

    suspend fun getServiceWithRetry(maxRetries: Int = ShizukuIpcConstants.MAX_RECONNECT_ATTEMPTS): IPrivilegedFileService? {
        var attempts = 0
        while (attempts < maxRetries) {
            val service = getService()
            if (service != null && service.asBinder().isBinderAlive) {
                return service
            }
            attempts++
            if (attempts < maxRetries) {
                delay(ShizukuIpcConstants.RECONNECT_DELAY_MS)
            }
        }
        return null
    }

    private suspend fun getService(): IPrivilegedFileService? {
        if (!Shizuku.pingBinder()) {
            return null
        }

        val deferredToAwait = synchronized(this) {
            val existingService = currentService
            if (existingService != null && existingService.asBinder().isBinderAlive) {
                return existingService
            }

            cleanupStaleConnection()

            var deferred = serviceDeferred
            if (deferred == null) {
                deferred = CompletableDeferred()
                serviceDeferred = deferred

                val args = Shizuku.UserServiceArgs(
                    ComponentName(packageName, PrivilegedFileService::class.java.name)
                ).daemon(false).processNameSuffix(ShizukuIpcConstants.PROCESS_NAME_SUFFIX)

                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                        if (binder != null) {
                            try {
                                binder.linkToDeath(deathRecipient, 0)
                                val s = IPrivilegedFileService.Stub.asInterface(binder)
                                synchronized(this@ShizukuConnectionClient) {
                                    currentService = s
                                }
                                deferred.complete(s)
                            } catch (e: Exception) {
                                deferred.complete(null)
                            }
                        } else {
                            deferred.complete(null)
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        cleanupStaleConnection()
                    }
                }
                currentConnection = connection

                try {
                    Shizuku.bindUserService(args, connection)
                } catch (e: Exception) {
                    deferred.complete(null)
                }
            }
            deferred
        }

        return try {
            withTimeout(ShizukuIpcConstants.BIND_TIMEOUT_MS) {
                deferredToAwait.await()
            }
        } catch (e: TimeoutCancellationException) {
            cleanupStaleConnection()
            null
        }
    }
}
