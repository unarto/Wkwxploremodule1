// [Jalur Class/Modul]: core-storage-api/src/main/kotlin/com/wakwau/xplore/core/storage/model/StorageVolumeType.kt
// [Penjelasan]: Enum representasi tipe storage volume (internal, sdcard, usb otg, root, saf).
package com.wakwau.xplore.core.storage.model

enum class StorageVolumeType {
    PRIMARY_INTERNAL,
    SECONDARY_SDCARD,
    USB_OTG,
    ROOT,
    SAF_PROVIDER,
    UNKNOWN
}
