// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/component/FileManagerStorageHeader.kt
// [Penjelasan]: Komponen UI deklaratif murni untuk menampilkan header informasi storage dan kapasitas disk berdasarkan data state/model domain (StorageSpaceInfo/StorageVolumeItem/totalBytes/freeBytes) tanpa pemanggilan langsung StatFs atau Environment OS storage API di UI layer sesuai ownership.md.
package com.wakwau.xplore.filemanager.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.wakwau.xplore.core.storage.constant.StorageConstants
import com.wakwau.xplore.core.storage.model.StorageLocation
import com.wakwau.xplore.core.storage.model.StorageSpaceInfo
import com.wakwau.xplore.core.storage.model.StorageVolumeItem
import com.wakwau.xplore.core.storage.model.StorageVolumeType
import com.wakwau.xplore.core.utils.ui.components.StorageDiskBar
import com.wakwau.xplore.core.utils.formatter.ByteFormatter
import com.wakwau.xplore.filemanager.ui.R

@Composable
fun FileManagerStorageHeader(
    location: StorageLocation?,
    subFoldersCount: Int = 0,
    subFilesCount: Int = 0,
    modifier: Modifier = Modifier,
    totalBytes: Long = 0L,
    freeBytes: Long = 0L,
    spaceInfo: StorageSpaceInfo? = null,
    volume: StorageVolumeItem? = null,
    onToggleExpand: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val effectiveSpaceInfo = spaceInfo ?: volume?.spaceInfo
    val effectiveTotalBytes = if (totalBytes > 0L) totalBytes else (effectiveSpaceInfo?.totalBytes ?: 0L)
    val effectiveFreeBytes = if (freeBytes > 0L || totalBytes > 0L) freeBytes else (effectiveSpaceInfo?.freeBytes ?: 0L)
    val effectiveUsedBytes = (effectiveTotalBytes - effectiveFreeBytes).coerceAtLeast(0L)

    val isSdCard = location?.rootId == StorageConstants.SDCARD_STORAGE_ID || volume?.type == StorageVolumeType.SECONDARY_SDCARD
    val isRoot = location?.rootId == StorageConstants.ROOT_STORAGE_ID ||
        location?.rootId == StorageConstants.ROOT_LEGACY_ID ||
        volume?.type == StorageVolumeType.ROOT

    val diskName = volume?.name ?: when {
        isSdCard -> stringResource(R.string.label_sd_card)
        isRoot -> stringResource(R.string.label_root)
        else -> stringResource(R.string.label_internal_shared_storage)
    }

    val pathDisplay = location?.path?.ifEmpty { volume?.rootPath ?: StorageConstants.DEFAULT_PRIMARY_STORAGE_PATH }
        ?: (volume?.rootPath ?: StorageConstants.DEFAULT_PRIMARY_STORAGE_PATH)

    val freeSpaceText = if (effectiveTotalBytes > 0L) {
        ByteFormatter.format(effectiveFreeBytes)
    } else {
        "..."
    }

    val totalSpaceText = if (effectiveTotalBytes > 0L) {
        ByteFormatter.format(effectiveTotalBytes)
    } else {
        "..."
    }

    val usedPercentage = if (effectiveTotalBytes > 0L) {
        (effectiveUsedBytes.toFloat() / effectiveTotalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    StorageDiskBar(
        name = diskName,
        path = pathDisplay,
        subFoldersCount = subFoldersCount,
        subFilesCount = subFilesCount,
        freeSpaceText = freeSpaceText,
        totalSpaceText = totalSpaceText,
        usedPercentage = usedPercentage,
        isExternal = isSdCard,
        isSelected = true,
        isExpanded = true,
        onToggleExpand = onToggleExpand,
        onClick = onClick,
        modifier = modifier
    )
}




