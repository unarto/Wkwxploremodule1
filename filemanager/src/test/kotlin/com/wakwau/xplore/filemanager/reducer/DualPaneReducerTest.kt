// [Jalur Class/Modul]: filemanager/src/test/kotlin/com/wakwau/xplore/filemanager/reducer/DualPaneReducerTest.kt
// [Penjelasan]: Pengujian unit untuk DualPaneReducer di modul domain :filemanager mencakup peralihan panel aktif, seleksi independen antar panel, dan transisi status operasi berkas.
package com.wakwau.xplore.filemanager.reducer

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.model.FileMetadata
import com.wakwau.xplore.core.storage.model.FileType
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState
import com.wakwau.xplore.filemanager.state.PanelId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualPaneReducerTest {

    private val reducer = DualPaneReducer()

    @Test
    fun `test active panel switching`() {
        val initial = DualPaneState(activePanelId = PanelId.LEFT)
        val switched = reducer.reduce(initial, DualPaneEvent.SetActivePanel(PanelId.RIGHT))
        assertEquals(PanelId.RIGHT, switched.activePanelId)
    }

    @Test
    fun `test selection LEFT does not affect RIGHT`() {
        val initial = DualPaneState()
        val updated = reducer.reduce(initial, DualPaneEvent.ToggleSelection(PanelId.LEFT, "file1"))
        
        assertTrue(updated.leftPanel.selectedItemIds.contains("file1"))
        assertTrue(updated.rightPanel.selectedItemIds.isEmpty())
    }

    @Test
    fun `test selection RIGHT does not affect LEFT`() {
        val initial = DualPaneState()
        val updated = reducer.reduce(initial, DualPaneEvent.ToggleSelection(PanelId.RIGHT, "file2"))
        
        assertTrue(updated.rightPanel.selectedItemIds.contains("file2"))
        assertTrue(updated.leftPanel.selectedItemIds.isEmpty())
    }

    @Test
    fun `test navigation LEFT does not affect RIGHT location`() {
        val initial = DualPaneState()
        val leftLoc = StorageLocation("/left/dir", "root1")
        val updated = reducer.reduce(initial, DualPaneEvent.OpenLocation(PanelId.LEFT, leftLoc))
        
        assertEquals(leftLoc, updated.leftPanel.currentLocation)
        assertEquals(null, updated.rightPanel.currentLocation)
    }

    @Test
    fun `test directory loaded updates items and resets loading`() {
        val initial = DualPaneState()
        val items = listOf(
            FileItem(
                id = "1",
                name = "test.txt",
                location = StorageLocation("/test.txt", "root1"),
                type = FileType.FILE,
                metadata = FileMetadata(
                    size = 100L,
                    modifiedTime = 0L,
                    createdTime = null,
                    isReadable = true,
                    isWritable = true,
                    isExecutable = false,
                    isHidden = false
                )
            )
        )
        // [Jalur Class/Modul]: filemanager/src/test/kotlin/com/wakwau/xplore/filemanager/reducer/DualPaneReducerTest.kt
        // [Penjelasan]: Menyesuaikan parameter DirectoryLoaded dengan menambahkan location sesuai kontrak event terkini.
        val location = StorageLocation("/test", "root1")
        val updated = reducer.reduce(initial, DualPaneEvent.DirectoryLoaded(PanelId.LEFT, location, items))
        
        assertEquals(items, updated.leftPanel.items)
        assertFalse(updated.leftPanel.isLoading)
        assertNull(updated.leftPanel.error)
    }

    @Test
    fun `test clear selection resets selected ids`() {
        val initial = DualPaneState()
        val selected = reducer.reduce(initial, DualPaneEvent.ToggleSelection(PanelId.LEFT, "file1"))
        val cleared = reducer.reduce(selected, DualPaneEvent.ClearSelection(PanelId.LEFT))
        
        assertTrue(cleared.leftPanel.selectedItemIds.isEmpty())
    }
}
