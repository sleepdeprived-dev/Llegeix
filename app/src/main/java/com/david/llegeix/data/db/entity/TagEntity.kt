package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A label the reader can put on any PDF, with a colour of their choosing.
 *
 * Distinct from a folder: a document lives in exactly one folder, but can carry
 * as many tags as it likes. That is the whole point — "grammar" and "read on the
 * train" are both true of the same book.
 */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Which tags are on which document.
 *
 * A composite primary key rather than a synthetic one: the pair *is* the fact,
 * and it makes tagging the same document twice impossible rather than merely
 * unlikely.
 */
@Entity(
    tableName = "document_tags",
    primaryKeys = ["documentUri", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["uriString"],
            childColumns = ["documentUri"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            // Deleting a tag unfiles it everywhere rather than leaving orphans.
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId"), Index("documentUri")],
)
data class DocumentTagEntity(
    val documentUri: String,
    val tagId: Long,
)
