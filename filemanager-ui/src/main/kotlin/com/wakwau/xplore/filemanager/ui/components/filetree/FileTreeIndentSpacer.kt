// [Jalur Class/Modul]: filemanager-ui/src/main/kotlin/com/wakwau/xplore/filemanager/ui/components/filetree/FileTreeIndentSpacer.kt
// [Penjelasan]: Komponen pengatur garis dan spasi indentasi visual hierarki folder untuk node pohon berkas.
package com.wakwau.xplore.filemanager.ui.components.filetree

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wakwau.xplore.treeview.component.TreeBranchGuide

@Composable
fun FileTreeIndentSpacer(
    depth: Int,
    isLastChild: Boolean,
    ancestorHasNextSibling: List<Boolean>,
    branchColor: Color,
    indentWidth: Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (depth > 0) {
            TreeBranchGuide(
                depth = depth,
                isLastChild = isLastChild,
                ancestorHasNextSibling = ancestorHasNextSibling,
                branchColor = branchColor,
                indentWidth = indentWidth
            )
        } else {
            Spacer(modifier = Modifier.width(0.dp))
        }
    }
}
