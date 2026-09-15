package com.wakwau.xplore.core.storage.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ContextWrapper
import com.wakwau.xplore.core.storage.model.StorageVolumeItem
import com.wakwau.xplore.core.storage.model.StorageVolumeType
import com.wakwau.xplore.core.storage.provider.volume.StorageVolumeChangeMonitor
import com.wakwau.xplore.core.storage.provider.volume.StorageVolumeBroadcastReceiver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageVolumeRepositoryImplTest {
    @Test
    fun concurrentCollectors_registerReceiverOnlyOnce() = runTest {
        val context = RecordingContext()
        val repository = repository(context)

        val first = launch(start = CoroutineStart.UNDISPATCHED) { repository.getVolumes().collect { } }
        val second = launch(start = CoroutineStart.UNDISPATCHED) { repository.getVolumes().collect { } }
        runCurrent()

        assertEquals(1, context.registerCount)
        first.cancelAndJoin()
        second.cancelAndJoin()
        advanceUntilIdle()
        assertEquals(1, context.unregisterCount)
        repository.close()
    }

    @Test
    fun collectorCompletion_unregistersReceiver() = runTest {
        val context = RecordingContext()
        val repository = repository(context)

        val collector = launch { repository.getVolumes().collect { } }
        runCurrent()
        collector.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(1, context.registerCount)
        assertEquals(1, context.unregisterCount)
        repository.close()
    }

    @Test
    fun resubscribe_doesNotAccumulateReceivers() = runTest {
        val context = RecordingContext()
        val repository = repository(context)

        val first = launch { repository.getVolumes().collect { } }
        runCurrent()
        first.cancelAndJoin()
        advanceUntilIdle()
        val second = launch { repository.getVolumes().collect { } }
        runCurrent()
        second.cancelAndJoin()
        advanceUntilIdle()

        assertEquals(2, context.registerCount)
        assertEquals(2, context.unregisterCount)
        assertFalse(context.receiverRegistered)
        repository.close()
    }

    @Test
    fun close_cancelsRefreshAndUnregistersReceiver() = runTest {
        val context = RecordingContext()
        val refreshStarted = CompletableDeferred<Unit>()
        var refreshCancelled = false
        val repository = StorageVolumeRepositoryImpl(
            context,
            StandardTestDispatcher(testScheduler)
        ) {
            refreshStarted.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                refreshCancelled = true
            }
        }
        val collector = launch { repository.getVolumes().collect { } }
        advanceUntilIdle()
        refreshStarted.await()

        repository.close()
        advanceUntilIdle()

        assertTrue(refreshCancelled)
        assertFalse(repository.lifecycleJob.isActive)
        assertEquals(1, context.unregisterCount)
        collector.cancelAndJoin()
    }

    @Test
    fun monitorRetainsApplicationContextNotActivityContext() {
        val application = RecordingContext()
        val activity = RecordingContext(application)

        val monitor = StorageVolumeChangeMonitor(activity)

        assertSame(application, monitor.applicationContext)
    }

    @Test
    fun unregisterFailure_isPropagatedAndRegistrationStateIsRetained() {
        val context = RecordingContext().apply { failUnregister = true }
        val receiver = StorageVolumeBroadcastReceiver { }
        receiver.register(context)

        val first = runCatching { receiver.unregister() }
        val second = runCatching { receiver.unregister() }

        assertTrue(first.isFailure)
        assertTrue(second.isFailure)
        assertEquals(2, context.unregisterAttempts)
    }

    private fun TestScope.repository(context: Context) = StorageVolumeRepositoryImpl(
        context,
        StandardTestDispatcher(testScheduler)
    ) { listOf(testVolume) }

    private class RecordingContext(
        private val application: Context? = null
    ) : ContextWrapper(null) {
        var registerCount = 0
        var unregisterCount = 0
        var receiverRegistered = false
        var failUnregister = false
        var unregisterAttempts = 0
        private var receiver: BroadcastReceiver? = null

        override fun getApplicationContext(): Context = application ?: this

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
            check(!receiverRegistered)
            registerCount++
            receiverRegistered = true
            this.receiver = receiver
            return null
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            check(receiverRegistered && receiver === this.receiver)
            unregisterAttempts++
            if (failUnregister) error("forced unregister failure")
            unregisterCount++
            receiverRegistered = false
            this.receiver = null
        }
    }

    private companion object {
        val testVolume = StorageVolumeItem(
            id = "primary",
            name = "Internal",
            rootPath = "/storage/emulated/0",
            type = StorageVolumeType.PRIMARY_INTERNAL,
            isReadOnly = false,
            spaceInfo = null
        )
    }
}
