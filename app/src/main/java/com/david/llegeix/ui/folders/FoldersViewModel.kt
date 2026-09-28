package com.david.llegeix.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.dao.FolderWithCount
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A collection that has just been made, and is about to be filled. */
data class NewCollection(val id: Long, val name: String)

class FoldersViewModel(
    private val libraryData: LibraryDataRepository,
) : ViewModel() {

    val folders: StateFlow<List<FolderWithCount>> = libraryData.observeFoldersWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    /**
     * The collection just made, so the screen can walk straight into it.
     *
     * Making a collection and putting things in it is one job, and it used to
     * end with a new empty row in a list and no hint that filling it was the
     * next step — let alone where the step was. Now the app goes there and
     * opens the picker.
     */
    private val _created = MutableStateFlow<NewCollection?>(null)
    val created: StateFlow<NewCollection?> = _created.asStateFlow()

    fun createFolder(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        // Folder names are uniquely indexed; a null result means it was taken.
        val id = libraryData.createFolder(trimmed)
        if (id == null) {
            _message.value = UiText.of(Res.string.folders_exists, trimmed)
        } else {
            _created.value = NewCollection(id, trimmed)
        }
    }

    fun onCreatedHandled() {
        _created.value = null
    }

    fun renameFolder(folderId: Long, name: String) = viewModelScope.launch {
        libraryData.renameFolder(folderId, name)
    }

    fun deleteFolder(folderId: Long) = viewModelScope.launch {
        libraryData.deleteFolder(folderId)
    }

    /**
     * How many PDFs are bookmarked, shown as the automatic "Bookmarked"
     * collection at the top of the folder list.
     *
     * Derived rather than stored as a real folder: a document sits in at most
     * one folder, so materialising this as a folder row would silently pull
     * bookmarked PDFs out of whatever folder the user had filed them in.
     */
    val bookmarkedCount: StateFlow<Int> = libraryData.observeBookmarkedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * How many PDFs are set aside to read later, and how many have been opened.
     *
     * The other two automatic collections. They are derived for the same reason
     * "Bookmarked" is: read-later is a flag on the document and the history is
     * its own table, and materialising either as a real folder row would pull
     * every PDF in it out of whatever collection the reader had filed it in — a
     * document belongs to at most one folder.
     *
     * They used to live in the library: read-later as a third filter chip, the
     * history behind the overflow menu. Both were shelves the reader had put
     * something on rather than ways of arranging the library, which is exactly
     * what a collection is here, so they now sit where the shelves are.
     */
    val readLaterCount: StateFlow<Int> = libraryData.observeReadLaterDocuments()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val recentCount: StateFlow<Int> = libraryData.observeRecent()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setPinned(folderId: Long, pinned: Boolean) = viewModelScope.launch {
        libraryData.setFolderPinned(folderId, pinned)
    }

    fun setBookmarked(folderId: Long, bookmarked: Boolean) = viewModelScope.launch {
        libraryData.setFolderBookmarked(folderId, bookmarked)
    }

    fun setColor(folderId: Long, color: Int?) = viewModelScope.launch {
        libraryData.setFolderColor(folderId, color)
    }

    fun onMessageShown() {
        _message.value = null
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                FoldersViewModel(app.libraryDataRepository)
            }
        }
    }
}
