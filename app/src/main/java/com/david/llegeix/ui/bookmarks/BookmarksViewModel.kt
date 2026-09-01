package com.david.llegeix.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.dao.FolderWithCount
import com.david.llegeix.data.db.dao.PageBookmark
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.data.source.LibraryDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The three things that can be bookmarked, kept deliberately distinct.
 *
 * A bookmarked *PDF* is a whole document the user wants to come back to; a
 * bookmarked *page* is a position inside one. Collapsing them into one list
 * would make "my bookmarked books" and "my bookmarked passages" the same
 * thing, which they are not.
 */
class BookmarksViewModel(
    private val libraryData: LibraryDataRepository,
) : ViewModel() {

    val bookmarkedDocuments: StateFlow<List<DocumentEntity>> =
        libraryData.observeBookmarkedDocuments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pageBookmarks: StateFlow<List<PageBookmark>> =
        libraryData.observePageBookmarks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Words and phrases saved while reading.
     *
     * This replaced bookmarked folders in the Bookmarks screen. A folder you
     * starred was a shortcut to a shelf; a word you saved is the thing this app
     * is for, and the two were competing for the same tab.
     */
    val savedWords: StateFlow<List<WordBookmarkEntity>> = libraryData.observeWordBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeWord(id: Long) = viewModelScope.launch { libraryData.removeWordBookmark(id) }


    fun removeDocumentBookmark(document: DocumentEntity) = viewModelScope.launch {
        libraryData.setDocumentBookmarked(document.uriString, document.displayName, false)
    }

    fun removePageBookmark(bookmarkId: Long) = viewModelScope.launch {
        libraryData.deletePageBookmark(bookmarkId)
    }


    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                BookmarksViewModel(app.libraryDataRepository)
            }
        }
    }
}
