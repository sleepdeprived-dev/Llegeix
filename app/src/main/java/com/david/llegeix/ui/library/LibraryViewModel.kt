package com.david.llegeix.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.settings.LibraryLayout
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.model.LibrarySort
import com.david.llegeix.R
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.entity.FolderEntity
import com.david.llegeix.data.db.entity.TagEntity
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.PdfRepository
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: PdfRepository,
    private val libraryData: LibraryDataRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Rows or covers. Shared with Recent so the app looks like one app. */
    val layout: StateFlow<LibraryLayout> = settings.settings
        .map { it.libraryLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.current.libraryLayout)

    fun onToggleLayout() = settings.setLibraryLayout(layout.value.toggled())

    /** Every tag that exists, for the picker. */
    val tags: StateFlow<List<TagEntity>> = libraryData.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Tags keyed by document, so a row can look its own up without a query. */
    val tagsByDocument: StateFlow<Map<String, List<DocumentTag>>> =
        libraryData.observeTagsByDocument()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onToggleTag(document: PdfDocument, tag: TagEntity) = viewModelScope.launch {
        libraryData.toggleTag(document.uriString, document.displayName, tag.id)
    }

    /** Makes the tag and puts it straight on the document that asked for it. */
    fun onCreateTag(document: PdfDocument, name: String, colorArgb: Int) =
        viewModelScope.launch {
            val tag = libraryData.createOrGetTag(name, colorArgb) ?: return@launch
            libraryData.toggleTag(document.uriString, document.displayName, tag.id)
        }

    fun onDeleteTag(tag: TagEntity) = viewModelScope.launch {
        libraryData.deleteTag(tag.id)
    }

    /** Recolour a tag that already exists, keeping its name and its documents. */
    fun onRecolourTag(tag: TagEntity, colorArgb: Int) = viewModelScope.launch {
        libraryData.renameTag(tag.id, tag.name, colorArgb)
    }

    /** Folders offered by the "move to folder" sheet. */
    val folders: StateFlow<List<FolderEntity>> = libraryData.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Bookmark and read-later flags live in the database while the document
        // list comes from storage, so they are collected separately and matched
        // up by URI when the visible list is computed.
        viewModelScope.launch {
            libraryData.observeReadLaterDocuments().collect { documents ->
                _uiState.update {
                    it.copy(readLaterUris = documents.map { d -> d.uriString }.toSet())
                        .withVisibleDocuments()
                }
            }
        }
        viewModelScope.launch {
            libraryData.observeBookmarkedDocuments().collect { documents ->
                _uiState.update {
                    it.copy(bookmarkedUris = documents.map { d -> d.uriString }.toSet())
                        .withVisibleDocuments()
                }
            }
        }
    }

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    /** Unfiltered scan result; the state holds the filtered view of this. */
    private var allDocuments: List<PdfDocument> = emptyList()

    private var scanJob: Job? = null

    init {
        // Under a tag sort the order depends on data outside the document list,
        // so a tag being added, removed or recoloured has to re-sort what is on
        // screen.
        //
        // This has to live below _uiState, not beside tagsByDocument where it
        // reads better. Kotlin runs initialisers in declaration order, and
        // viewModelScope uses Dispatchers.Main.immediate: a StateFlow emits its
        // current value the moment it is collected, so the collector ran during
        // construction and found _uiState still null. The Room-backed
        // collectors above get away with it only because a database flow has
        // nothing to emit yet.
        viewModelScope.launch {
            tagsByDocument.collect {
                if (_uiState.value.sort == LibrarySort.TAG) {
                    _uiState.update { state -> state.withVisibleDocuments() }
                }
            }
        }

        // Seed the sources directly rather than through syncSources(), which
        // would see the device grant "appear" and start a scan that the
        // refresh() below would immediately cancel.
        _uiState.update {
            it.copy(
                grantedFolders = repository.grantedFolders(),
                deviceScanEnabled = repository.isDeviceScanEnabled(),
            )
        }
        refresh()
    }

    /**
     * Re-read which sources are available without rescanning. Called when the
     * screen resumes, since All Files Access is granted in system Settings and
     * we only find out it changed by looking again on return.
     */
    fun syncSources() {
        val folders = repository.grantedFolders()
        val deviceScan = repository.isDeviceScanEnabled()
        val previous = _uiState.value
        _uiState.update {
            it.copy(grantedFolders = folders, deviceScanEnabled = deviceScan)
        }
        // A source appearing while we were away means the current list is stale.
        val gainedSource = deviceScan && !previous.deviceScanEnabled
        if (gainedSource) refresh()
    }

    fun refresh() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = true, errorMessage = null) }
        scanJob = viewModelScope.launch {
            runCatchingCancellable { repository.loadLibrary() }
                .onSuccess { snapshot ->
                    allDocuments = snapshot.documents
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            hasScanned = true,
                            grantedFolders = snapshot.grantedFolders,
                            deviceScanEnabled = snapshot.deviceScanEnabled,
                        ).withVisibleDocuments()
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            hasScanned = true,
                            errorMessage = UiText.ofMessageOr(
                                error.message,
                                R.string.library_scan_failed,
                            ),
                        )
                    }
                }
        }
    }

    fun onFolderPicked(treeUri: Uri) {
        repository.addFolder(treeUri)
        syncSources()
        refresh()
    }

    /** Files chosen from the PDF-only document picker, cloud sources included. */
    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        uris.forEach(repository::addPickedFile)
        syncSources()
        refresh()
    }

    fun onFolderRemoved(treeUri: Uri) {
        repository.removeFolder(treeUri)
        syncSources()
        refresh()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query).withVisibleDocuments() }
    }

    fun onSortChange(sort: LibrarySort) {
        _uiState.update { it.copy(sort = sort).withVisibleDocuments() }
    }

    fun onFilterChange(filter: LibraryFilter) {
        _uiState.update { it.copy(filter = filter).withVisibleDocuments() }
    }

    fun onToggleReadLater(document: PdfDocument) = viewModelScope.launch {
        libraryData.setDocumentReadLater(
            uriString = document.uriString,
            displayName = document.displayName,
            readLater = document.uriString !in _uiState.value.readLaterUris,
        )
    }

    fun onToggleBookmarked(document: PdfDocument) = viewModelScope.launch {
        libraryData.setDocumentBookmarked(
            uriString = document.uriString,
            displayName = document.displayName,
            bookmarked = document.uriString !in _uiState.value.bookmarkedUris,
        )
    }

    /**
     * File a document into a folder, creating its database row first — the
     * folder foreign key has nothing to point at until the document exists.
     */
    fun moveToFolder(document: PdfDocument, folderId: Long?) = viewModelScope.launch {
        libraryData.ensureDocument(document.uriString, document.displayName)
        libraryData.moveToFolder(document.uriString, folderId)
    }

    fun createFolderAndMove(document: PdfDocument, name: String) = viewModelScope.launch {
        val folderId = libraryData.createFolder(name)
        if (folderId == null) {
            _uiState.update {
                it.copy(errorMessage = UiText.of(R.string.folders_exists, name))
            }
            return@launch
        }
        libraryData.ensureDocument(document.uriString, document.displayName)
        libraryData.moveToFolder(document.uriString, folderId)
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Apply the current query and sort to [allDocuments]. */
    private fun LibraryUiState.withVisibleDocuments(): LibraryUiState {
        val needle = query.trim()
        val inFilter = when (filter) {
            LibraryFilter.ALL -> allDocuments
            LibraryFilter.READ_LATER -> allDocuments.filter { it.uriString in readLaterUris }
        }
        val matches = if (needle.isEmpty()) {
            inFilter
        } else {
            inFilter.filter {
                it.displayName.contains(needle, ignoreCase = true) ||
                    it.parentLabel?.contains(needle, ignoreCase = true) == true
            }
        }
        return copy(
            documents = matches.sortedWith(
                sort.comparator { document ->
                    // Already alphabetical from the DAO, so the first is the
                    // one the row displays.
                    tagsByDocument.value[document.uriString]?.firstOrNull()?.name
                },
            ),
            totalFound = allDocuments.size,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LlegeixApp
                LibraryViewModel(app.pdfRepository, app.libraryDataRepository, app.settingsRepository)
            }
        }
    }
}
