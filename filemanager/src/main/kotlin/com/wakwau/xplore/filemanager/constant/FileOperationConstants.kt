// [Jalur Class/Modul]: filemanager/src/main/kotlin/com/wakwau/xplore/filemanager/constant/FileOperationConstants.kt
// [Penjelasan]: Implementasi komponen logika & presentasi antar muka.
package com.wakwau.xplore.filemanager.constant

object FileOperationConstants {
    const val OPERATION_COPY: Int = 1
    const val OPERATION_MOVE: Int = 2
    const val OPERATION_DELETE: Int = 3
    const val OPERATION_RENAME: Int = 4
    const val OPERATION_CREATE_DIR: Int = 5
    
    const val SUCCESS_COPY: Int = 101
    const val SUCCESS_MOVE: Int = 102
    const val SUCCESS_DELETE: Int = 103
    const val SUCCESS_RENAME: Int = 104
    const val SUCCESS_CREATE_DIR: Int = 105
}
