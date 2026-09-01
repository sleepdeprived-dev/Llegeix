package com.david.catalanpdfreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.david.catalanpdfreader.data.db.dao.BookmarkDao
import com.david.catalanpdfreader.data.db.dao.DocumentDao
import com.david.catalanpdfreader.data.db.dao.FolderDao
import com.david.catalanpdfreader.data.db.dao.RecentlyViewedDao
import com.david.catalanpdfreader.data.db.entity.BookmarkEntity
import com.david.catalanpdfreader.data.db.entity.DocumentEntity
import com.david.catalanpdfreader.data.db.entity.FolderEntity
import com.david.catalanpdfreader.data.db.entity.RecentlyViewedEntity

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        BookmarkEntity::class,
        RecentlyViewedEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class CatalanPdfDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao

    companion object {
        fun build(context: Context): CatalanPdfDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CatalanPdfDatabase::class.java,
                "catalan-pdf-reader.db",
            ).addMigrations(MIGRATION_1_2)
                .build()
    }
}
