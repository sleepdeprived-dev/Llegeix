package com.david.llegeix.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.david.llegeix.data.db.dao.BookmarkDao
import com.david.llegeix.data.db.dao.DocumentDao
import com.david.llegeix.data.db.dao.FlashcardDao
import com.david.llegeix.data.db.dao.FolderDao
import com.david.llegeix.data.db.dao.RecentlyViewedDao
import com.david.llegeix.data.db.dao.FolderRuleDao
import com.david.llegeix.data.db.dao.TagDao
import com.david.llegeix.data.db.dao.WordBookmarkDao
import com.david.llegeix.data.db.entity.BookmarkEntity
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.FlashcardCollectionEntity
import com.david.llegeix.data.db.entity.FlashcardDeckEntity
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.db.entity.FolderEntity
import com.david.llegeix.data.db.entity.RecentlyViewedEntity
import com.david.llegeix.data.db.entity.DocumentTagEntity
import com.david.llegeix.data.db.entity.FolderRuleEntity
import com.david.llegeix.data.db.entity.TagEntity
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.data.db.entity.LearnedWordEntity

@Database(
    entities = [
        FolderEntity::class,
        DocumentEntity::class,
        BookmarkEntity::class,
        RecentlyViewedEntity::class,
        WordBookmarkEntity::class,
        TagEntity::class,
        DocumentTagEntity::class,
        FolderRuleEntity::class,
        FlashcardCollectionEntity::class,
        FlashcardDeckEntity::class,
        FlashcardEntity::class,
        LearnedWordEntity::class,
    ],
    version = 23,
    exportSchema = true,
)
@ConstructedBy(LlegeixDatabaseConstructor::class)
abstract class LlegeixDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun documentDao(): DocumentDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentlyViewedDao(): RecentlyViewedDao
    abstract fun wordBookmarkDao(): WordBookmarkDao
    abstract fun tagDao(): TagDao
    abstract fun folderRuleDao(): FolderRuleDao
    abstract fun flashcardDao(): FlashcardDao

    /** Opened per platform: [build] on the phone, [openDesktop] on the Mac. */
    companion object
}

/** Room's generated constructor, which is what lets the Mac open it without reflection. */
@Suppress("KotlinNoActualForExpect")
expect object LlegeixDatabaseConstructor : RoomDatabaseConstructor<LlegeixDatabase> {
    override fun initialize(): LlegeixDatabase
}
