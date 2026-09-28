package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bookmarked page within a document.
 *
 * The unique index on (document, page) makes bookmarking a toggle rather than
 * something that can pile up duplicates on the same page.
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["uriString"],
            childColumns = ["documentUri"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("documentUri"),
        Index(value = ["documentUri", "pageIndex"], unique = true),
    ],
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentUri: String,
    val pageIndex: Int,
    val label: String? = null,
    /** Overrides the document's colour when set. */
    val highlightColor: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
