package com.david.llegeix.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.dao.ReadingProgress
import com.david.llegeix.data.db.entity.DocumentEntity
import com.david.llegeix.data.db.entity.TagEntity
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

    /** So a collection's rows carry the same progress bars as the library's. */
    val progress: StateFlow<Map<String, ReadingProgress>> = libraryData.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * What the reader has named PDFs, and the tags that are on them.
     *
     * Held here so a collection's rows can carry the same hold-to-open menu the
     * library's do. A PDF is one object however it is reached, and a tag added
     * from a collection is the same tag the library will draw on the same row a
     * moment later.
     */
    val customNames: StateFlow<Map<String, String>> = libraryData.observeCustomNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val tags: StateFlow<List<TagEntity>> = libraryData.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagsByDocument: StateFlow<Map<String, List<DocumentTag>>> =
        libraryData.observeTagsByDocument()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onToggleTag(document: DocumentEntity, tag: TagEntity) = viewModelScope.launch {
        libraryData.toggleTag(document.uriString, document.displayName, tag.id)
    }

    fun onCreateTag(document: DocumentEntity, name: String, colorArgb: Int) =
        viewModelScope.launch {
            val tag = libraryData.createOrGetTag(name, colorArgb) ?: return@launch
            libraryData.toggleTag(document.uriString, document.displayName, tag.id)
        }

    fun onRecolourTag(tag: TagEntity, colorArgb: Int) = viewModelScope.launch {
        libraryData.renameTag(tag.id, tag.name, colorArgb)
    }

    fun onRenameTag(tag: TagEntity, name: String) = viewModelScope.launch {
        libraryData.renameTag(tag.id, name, tag.colorArgb)
    }

    fun onDeleteTag(tag: TagEntity) = viewModelScope.launch {
        libraryData.deleteTag(tag.id)
    }

    fun onRenameDocument(document: DocumentEntity, name: String) = viewModelScope.launch {
        libraryData.setDocumentName(document.uriString, document.displayName, name)
    }

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
