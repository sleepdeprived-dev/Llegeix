package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * When a document was last opened, and where the reader had got to.
 *
 * One row per document rather than an append-only history: a "recently viewed"
 * list wants each PDF once, at its latest timestamp, and keeping every visit
 * would mean de-duplicating on every read. [lastPageIndex] rides along here
 * because it is written at exactly the same moments, and it lets the reader
 * resume where it left off.
 */
@Entity(
    tableName = "recently_viewed",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["uriString"],
            childColumns = ["documentUri"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecentlyViewedEntity(
    @PrimaryKey val documentUri: String,
    val viewedAt: Long = System.currentTimeMillis(),
    val lastPageIndex: Int = 0,
)
