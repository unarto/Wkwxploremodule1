// [Jalur Class/Modul]: core-storage/src/main/kotlin/com/wakwau/xplore/core/storage/db/AppDatabase.kt
// [Penjelasan]: Room Database utama penyimpan skema FileIndexEntity.
package com.wakwau.xplore.core.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.wakwau.xplore.core.storage.db.dao.FileIndexDao
import com.wakwau.xplore.core.storage.db.entity.FileIndexEntity

@Database(
    entities = [
        FileIndexEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fileIndexDao(): FileIndexDao
}
