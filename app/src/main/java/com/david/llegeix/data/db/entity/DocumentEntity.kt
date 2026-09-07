package com.david.llegeix.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A PDF the user has actually interacted with — opened, filed, or bookmarked.
 *
 * Deliberately not a mirror of everything on disk: discovery stays the job of
 * the storage sources, and a row appears here only when there is something to
 * remember about the file. The URI is the primary key, which is why
 * [com.david.llegeix.data.model.PdfDocument] carries its handle as a
 * String.
 *
 * A document belongs to at most one folder, matching how a filesystem behaves.
 * Deleting a folder leaves its documents in place, unfiled.
 */
@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("folderId")],
)
data class DocumentEntity(
    @PrimaryKey val uriString: String,
    val displayName: String,
    /**
     * What the reader decided to call this PDF, or null to use [displayName].
     *
     * A name the app keeps, not a rename on the phone. Llegeix reads other
     * apps' files through a read-only grant and has no business writing to
     * them, so "rename" here means "show me this name instead" — the file in
     * Downloads keeps whatever the website called it, and nothing else on the
     * device sees any change.
     */
    val customName: String? = null,
    val folderId: Long? = null,
    /**
     * The whole PDF is bookmarked, as distinct from bookmarking a page inside
     * it. This is what drives the automatic "Bookmarked" collection.
     */
    val isBookmarked: Boolean = false,
    /** Flagged to read later, surfaced as a filter on the library. */
    val isReadLater: Boolean = false,
    /** Default highlight colour for this PDF. Null means the app default. */
    val highlightColor: Int? = null,
    /**
     * How many pages the PDF has, learned the first time it is opened.
     *
     * Nullable because it cannot be known before then: discovery reads the
     * file's name and size from storage and never opens it, and opening every
     * PDF on the device to count its pages would make the library scan cost
     * seconds instead of milliseconds. Null therefore means "never opened",
     * which is exactly the case where there is no progress to show either.
     */
    val pageCount: Int? = null,
    val addedAt: Long = System.currentTimeMillis(),
)
