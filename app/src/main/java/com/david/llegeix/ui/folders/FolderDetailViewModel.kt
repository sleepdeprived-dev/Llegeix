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
import com.david.llegeix.data.source.DocumentNames
import com.david.llegeix.data.flashcards.CardSearch
import com.david.llegeix.data.source.LibraryDataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FolderDetailViewModel(
    private val libraryData: LibraryDataRepository,
    folderId: Long,
) : ViewModel() {

    val documents: StateFlow<List<DocumentEntity>> =
        libraryData.observeDocumentsInFolder(folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun onQueryChange(query: String) {
        _query.value = query
    }

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
    val names: StateFlow<DocumentNames> = libraryData.observeDocumentNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentNames.Empty)

    val tags: StateFlow<List<TagEntity>> = libraryData.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagsByDocument: StateFlow<Map<String, List<DocumentTag>>> =
        libraryData.observeTagsByDocument()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The collection, narrowed by what has been typed.
     *
     * Matched against what the reader calls a document as well as against the
     * file's own name, because those are often not the same thing at all here:
     * renaming a PDF is most of the point of collections, and a search that
     * only knew `PROVA_C1_2019_comprensio_lectora_v2.pdf` would be searching a
     * list nobody is looking at. Accents are ignored for the same reason the
     * flashcards ignore them — the half-remembered word is typed on whichever
     * keyboard is up.
     */
    val shown: StateFlow<List<DocumentEntity>> =
        combine(documents, _query, names) { documents, query, names ->
            val needle = CardSearch.fold(query)
            if (needle.isEmpty()) {
                documents
            } else {
                documents.filter { document ->
                    val title = names.titleFor(document.uriString, document.displayName)
                    CardSearch.fold(title).contains(needle) ||
                        CardSearch.fold(document.displayName).contains(needle)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
