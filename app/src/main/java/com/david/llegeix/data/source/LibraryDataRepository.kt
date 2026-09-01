package com.david.llegeix.data.source

import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.dao.FolderWithCount
import com.david.llegeix.data.db.dao.PageBookmark
import com.david.llegeix.data.db.dao.RecentDocument
import com.david.llegeix.data.db.entity.BookmarkEntity
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.FolderEntity
import com.david.llegeix.data.db.entity.RecentlyViewedEntity
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app *remembers* about PDFs: folders, bookmarks, colours and
 * reading history.
 *
 * Kept separate from [PdfRepository], which only answers "what is on the
 * device". The split matters because the two have different lifetimes — a file
 * can disappear from storage while its bookmarks remain, and a document can be
 * on disk with nothing recorded about it at all.
 */
class LibraryDataRepository(private val database: LlegeixDatabase) {

    private val folders = database.folderDao()
    private val documents = database.documentDao()
    private val bookmarks = database.bookmarkDao()
    private val recents = database.recentlyViewedDao()

    // --- Folders -----------------------------------------------------------

    fun observeFolders(): Flow<List<FolderEntity>> = folders.observeFolders()

    fun observeFoldersWithCounts(): Flow<List<FolderWithCount>> =
        folders.observeFoldersWithCounts()

    /** Returns null when a folder with that name already exists. */
    suspend fun createFolder(name: String): Long? =
        folders.insert(FolderEntity(name = name.trim()))
            .takeIf { it != -1L }

    suspend fun renameFolder(folderId: Long, name: String) =
        folders.rename(folderId, name.trim())

    /** Documents in the folder survive; they simply become unfiled. */
    suspend fun deleteFolder(folderId: Long) = folders.delete(folderId)

    suspend fun setFolderPinned(folderId: Long, pinned: Boolean) =
        folders.setPinned(folderId, pinned)

    suspend fun setFolderBookmarked(folderId: Long, bookmarked: Boolean) =
        folders.setBookmarked(folderId, bookmarked)

    suspend fun setFolderColor(folderId: Long, color: Int?) =
        folders.setColor(folderId, color)

    // --- Documents ---------------------------------------------------------

    fun observeDocument(uriString: String): Flow<DocumentEntity?> =
        documents.observe(uriString)

    fun observeDocumentsInFolder(folderId: Long): Flow<List<DocumentEntity>> =
        documents.observeInFolder(folderId)

    fun observeUnfiledDocuments(): Flow<List<DocumentEntity>> = documents.observeUnfiled()

    /**
     * Make sure the document has a row before anything references it.
     *
     * Every foreign key in the schema points here, so this must run before a
     * bookmark or a recently-viewed entry is written, or the insert fails.
     */
    suspend fun ensureDocument(uriString: String, displayName: String) {
        documents.insertIfAbsent(
            DocumentEntity(uriString = uriString, displayName = displayName),
        )
    }

    suspend fun moveToFolder(uriString: String, folderId: Long?) =
        documents.setFolder(uriString, folderId)

    suspend fun setDocumentHighlightColor(uriString: String, color: Int?) =
        documents.setHighlightColor(uriString, color)

    // --- Whole-document bookmarks and read-later -----------------------------

    /**
     * Documents bookmarked in their entirety, which is what the automatic
     * "Bookmarked" collection shows.
     */
    fun observeBookmarkedDocuments(): Flow<List<DocumentEntity>> =
        documents.observeBookmarked()

    fun observeBookmarkedCount(): Flow<Int> = documents.observeBookmarkedCount()

    fun observeReadLaterDocuments(): Flow<List<DocumentEntity>> = documents.observeReadLater()

    suspend fun setDocumentBookmarked(
        uriString: String,
        displayName: String,
        bookmarked: Boolean,
    ) {
        ensureDocument(uriString, displayName)
        documents.setBookmarked(uriString, bookmarked)
    }

    suspend fun setDocumentReadLater(
        uriString: String,
        displayName: String,
        readLater: Boolean,
    ) {
        ensureDocument(uriString, displayName)
        documents.setReadLater(uriString, readLater)
    }

    // --- Bookmarks ---------------------------------------------------------

    fun observeBookmarks(uriString: String): Flow<List<BookmarkEntity>> =
        bookmarks.observeForDocument(uriString)

    fun observeAllBookmarks(): Flow<List<BookmarkEntity>> = bookmarks.observeAll()

    /** Every bookmarked page, with the name of the document it belongs to. */
    fun observePageBookmarks(): Flow<List<PageBookmark>> = bookmarks.observePageBookmarks()

    suspend fun deletePageBookmark(bookmarkId: Long) = bookmarks.deleteById(bookmarkId)

    /**
     * Add a bookmark on this page, or remove the one already there.
     * Returns true when a bookmark now exists on the page.
     */
    suspend fun toggleBookmark(
        uriString: String,
        displayName: String,
        pageIndex: Int,
        color: Int?,
    ): Boolean {
        ensureDocument(uriString, displayName)
        return if (bookmarks.findAt(uriString, pageIndex) != null) {
            bookmarks.deleteAt(uriString, pageIndex)
            false
        } else {
            bookmarks.insert(
                BookmarkEntity(
                    documentUri = uriString,
                    pageIndex = pageIndex,
                    highlightColor = color,
                ),
            )
            true
        }
    }

    suspend fun setBookmarkColor(bookmarkId: Long, color: Int?) =
        bookmarks.setHighlightColor(bookmarkId, color)

    suspend fun setBookmarkLabel(bookmarkId: Long, label: String?) =
        bookmarks.setLabel(bookmarkId, label?.trim()?.takeIf { it.isNotEmpty() })

    // --- Recently viewed ---------------------------------------------------

    fun observeRecent(limit: Int = DEFAULT_RECENT_LIMIT): Flow<List<RecentDocument>> =
        recents.observeRecent(limit)

    /** Record a visit, and remember how far into the document it got. */
    suspend fun recordView(uriString: String, displayName: String, pageIndex: Int) {
        ensureDocument(uriString, displayName)
        recents.record(
            RecentlyViewedEntity(documentUri = uriString, lastPageIndex = pageIndex),
        )
    }

    /** The page the reader should resume on, or 0 if this is a first visit. */
    suspend fun lastReadPage(uriString: String): Int =
        recents.find(uriString)?.lastPageIndex ?: 0

    suspend fun removeFromRecent(uriString: String) = recents.remove(uriString)

    suspend fun clearRecent() = recents.clear()

    private companion object {
        const val DEFAULT_RECENT_LIMIT = 20
    }
}
