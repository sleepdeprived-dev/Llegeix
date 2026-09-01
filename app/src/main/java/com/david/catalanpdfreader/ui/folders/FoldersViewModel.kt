package com.david.catalanpdfreader.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.catalanpdfreader.CatalanPdfReaderApp
import com.david.catalanpdfreader.data.db.dao.FolderWithCount
import com.david.catalanpdfreader.data.source.LibraryDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FoldersViewModel(
    private val libraryData: LibraryDataRepository,
) : ViewModel() {

    val folders: StateFlow<List<FolderWithCount>> = libraryData.observeFoldersWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun createFolder(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@launch
        // Folder names are uniquely indexed; a null result means it was taken.
        if (libraryData.createFolder(trimmed) == null) {
            _message.value = "A folder called \"$trimmed\" already exists"
        }
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
                    as CatalanPdfReaderApp
                FoldersViewModel(app.libraryDataRepository)
            }
        }
    }
}
