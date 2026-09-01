package com.david.llegeix.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.source.LibraryDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderDetailViewModel(
    private val libraryData: LibraryDataRepository,
    folderId: Long,
) : ViewModel() {

    val documents: StateFlow<List<DocumentEntity>> =
        libraryData.observeDocumentsInFolder(folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Unfile a document; it stays on disk and in the library. */
    fun removeFromFolder(uriString: String) = viewModelScope.launch {
        libraryData.moveToFolder(uriString, null)
    }

    companion object {
        fun factory(folderId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                FolderDetailViewModel(app.libraryDataRepository, folderId)
            }
        }
    }
}
