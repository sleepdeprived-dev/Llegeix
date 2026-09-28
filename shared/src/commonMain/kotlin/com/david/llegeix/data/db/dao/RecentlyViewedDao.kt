package com.david.llegeix.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.david.llegeix.data.db.entity.RecentlyViewedEntity
import kotlinx.coroutines.flow.Flow

/** A recently-viewed entry joined with the document it points at. */
data class RecentDocument(
    val uriString: String,
    val displayName: String,
    val viewedAt: Long,
    val lastPageIndex: Int,
    /** Null until the document has been opened once; see DocumentEntity. */
    val pageCount: Int?,
) {
    /**
     * The same progress the library draws elsewhere, derived rather than
     * repeated.
     *
     * Both this and [ReadingProgress] answer "how far in is this", and for a
     * while both worked it out for themselves — including, in both places, the
     * fraction at which a book counts as finished. Two copies of that number is
     * one copy too many: the Continue shelf and the bar on a cover would have
     * started disagreeing about which books are done the first time either was
     * tuned.
     */
    val progress: ReadingProgress
        get() = ReadingProgress(
            uriString = uriString,
            lastPageIndex = lastPageIndex,
            pageCount = pageCount,
            viewedAt = viewedAt,
        )
}

/**
 * How far through a document the reader has got.
 *
 * Every document that has been opened, not only the recent few, because the
 * library draws this on any row it has an answer for — a book you last touched
 * a month ago is exactly the one whose progress you have forgotten.
 */
data class ReadingProgress(
    val uriString: String,
    val lastPageIndex: Int,
    val pageCount: Int?,
    val viewedAt: Long,
) {
    /**
     * Progress as a fraction, or null when it cannot be known.
     *
     * Counted from the page you are *on*, so opening a book at page one reads
     * as a sliver rather than as nothing, and reaching the last page reads as
     * finished rather than as one page short of it.
     */
    val fraction: Float?
        get() {
            val total = pageCount ?: return null
            if (total <= 0) return null
            return ((lastPageIndex + 1).toFloat() / total).coerceIn(0f, 1f)
        }

    /** True once the reader is close enough to the end to call it read. */
    val isFinished: Boolean
        get() = (fraction ?: 0f) >= FINISHED_FRACTION

    /** Worth offering to resume: started, but not finished. */
    val isInProgress: Boolean
        get() = lastPageIndex > 0 && !isFinished

    private companion object {
        /**
         * Books end in acknowledgements, notes and an index, so demanding the
         * very last page before calling one finished would leave almost every
         * book the reader has actually finished sitting in "continue reading"
         * for ever.
         */
        const val FINISHED_FRACTION = 0.98f
    }
}

@Dao
interface RecentlyViewedDao {

    /** Upsert, so re-opening a document moves it up rather than adding a row. */
    @Upsert
    suspend fun record(entry: RecentlyViewedEntity)

    @Query(
        """
        SELECT d.uriString AS uriString,
               d.displayName AS displayName,
               r.viewedAt AS viewedAt,
               r.lastPageIndex AS lastPageIndex,
               d.pageCount AS pageCount
        FROM recently_viewed r
        INNER JOIN documents d ON d.uriString = r.documentUri
        ORDER BY r.viewedAt DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<RecentDocument>>

    @Query("SELECT * FROM recently_viewed WHERE documentUri = :uriString")
    suspend fun find(uriString: String): RecentlyViewedEntity?

    /**
     * Where the reader had got to in every document they have opened.
     *
     * Unlimited on purpose, unlike [observeRecent]: this feeds the bar drawn on
     * library rows and covers, and a book is no less half-read for having
     * dropped off the recent list.
     */
    @Query(
        """
        SELECT r.documentUri AS uriString,
               r.lastPageIndex AS lastPageIndex,
               d.pageCount AS pageCount,
               r.viewedAt AS viewedAt
        FROM recently_viewed r
        INNER JOIN documents d ON d.uriString = r.documentUri
        ORDER BY r.viewedAt DESC
        """,
    )
    fun observeProgress(): Flow<List<ReadingProgress>>

    @Query("DELETE FROM recently_viewed WHERE documentUri = :uriString")
    suspend fun remove(uriString: String)

    @Query("DELETE FROM recently_viewed")
    suspend fun clear()
}
