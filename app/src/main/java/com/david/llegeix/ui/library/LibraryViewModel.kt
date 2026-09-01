package com.david.llegeix.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.model.LibrarySort
import com.david.llegeix.R
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.data.db.entity.FolderEntity
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.PdfRepository
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: PdfRepository,
    private val libraryData: LibraryDataRepository,
) : ViewModel() {

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
            documents = matches.sortedWith(sort.comparator()),
            totalFound = allDocuments.size,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LlegeixApp
                LibraryViewModel(app.pdfRepository, app.libraryDataRepository)
            }
        }
    }
}
