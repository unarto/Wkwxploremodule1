package com.wakwau.xplore.filemanager.state

import com.wakwau.xplore.core.storage.model.StorageVolumeItem
import com.wakwau.xplore.core.storage.permission.StoragePermissionType

// [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/state/DualPaneState.kt
// [Penjelasan]: State domain immutable panel ganda dengan integrasi state rincian metadata berkas, dialog input berkas, perizinan penyimpanan, dan storage volumes tanpa horizontal coupling ke :file-operations-ui atau :search.
data class DualPaneState(
    val leftPanel: PanelState = PanelState(id = PanelId.LEFT),
    val rightPanel: PanelState = PanelState(id = PanelId.RIGHT),
    val activePanelId: PanelId = PanelId.LEFT,
    val fileDetailState: FileDetailState = FileDetailState(),
    val hasPermission: Boolean = false,
    val requiredPermissionType: StoragePermissionType = StoragePermissionType.READ_WRITE_STORAGE,
    val storageVolumes: List<StorageVolumeItem> = emptyList(),
) {
    val activePanel: PanelState
        get() = if (activePanelId == PanelId.LEFT) leftPanel else rightPanel

    val inactivePanel: PanelState
        get() = if (activePanelId == PanelId.LEFT) rightPanel else leftPanel
}
