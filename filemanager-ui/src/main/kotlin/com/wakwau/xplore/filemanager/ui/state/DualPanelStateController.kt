// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/state/DualPanelStateController.kt
// [Penjelasan]: Implementasi komponen logika & presentasi antar muka.
package com.wakwau.xplore.filemanager.ui.state
import com.wakwau.xplore.filemanager.state.PanelId

class DualPanelStateController(
    private val onPanelChange: (PanelId) -> Unit
) {
    var activePanelId: PanelId = PanelId.LEFT
        private set

    fun switchToLeft() {
        if (activePanelId != PanelId.LEFT) {
            activePanelId = PanelId.LEFT
            onPanelChange(activePanelId)
        }
    }

    fun switchToRight() {
        if (activePanelId != PanelId.RIGHT) {
            activePanelId = PanelId.RIGHT
            onPanelChange(activePanelId)
        }
    }

    fun togglePanel() {
        if (activePanelId == PanelId.LEFT) {
            switchToRight()
        } else {
            switchToLeft()
        }
    }
}
