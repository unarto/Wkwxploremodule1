// [Modul: :filemanager-ui] [Jalur Class]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/action/FileOperationActionDelegate.kt
// [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
package com.wakwau.xplore.filemanager.ui.action

import com.wakwau.xplore.core.storage.model.FileItem
import com.wakwau.xplore.core.storage.search.FileSearchQuery
import com.wakwau.xplore.filemanager.event.DualPaneEvent
import com.wakwau.xplore.filemanager.state.DualPaneState

interface FileOperationActionDelegate {
    fun requestCopy(state: DualPaneState, items: List<FileItem>, targetPath: String)
    fun requestMove(state: DualPaneState, items: List<FileItem>, targetPath: String)
    fun requestDelete(state: DualPaneState, items: List<FileItem>)
    fun requestRename(state: DualPaneState, item: FileItem, newName: String)
    fun requestSearch(query: FileSearchQuery)
    // [Modul: :filemanager-ui] [Jalur Class]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/action/FileOperationActionDelegate.kt
    // [Penjelasan]: Penyesuaian lokasi modul dan implementasi kontrak API
    fun dispatchEvent(event: DualPaneEvent)
}
