package com.david.llegeix.ui.sources

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.source.FolderRules
import com.david.llegeix.data.source.GrantedFolder
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.PdfRepository
import com.david.llegeix.data.source.SourceFolder
import com.david.llegeix.data.source.folderTreeUnder
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One granted source and the folders found inside it. */
data class SourceGroup(
    val folder: GrantedFolder,
    val tree: List<SourceFolder>,
    /** Documents currently reaching the library from this source. */
    val visibleCount: Int,
    val totalCount: Int,
    /** Whether the source's own root is switched on. */
    val rootVisible: Boolean,
)

data class SourcesUiState(
    val isScanning: Boolean = true,
    val groups: List<SourceGroup> = emptyList(),
    /** Android allows the sweep at all. */
    val deviceScanPermitted: Boolean = false,
    /** The sweep is permitted and switched on. */
    val deviceScanEnabled: Boolean = false,
    /** Found by the device sweep rather than under any granted folder. */
    val deviceScanCount: Int = 0,
    /**
     * The sweep's own folders, grouped by the top-level folder they sit in.
     *
     * The sweep is one permission but it is not one place: a phone has PDFs in
     * Download, in Documents, and in whatever a messaging app calls its folder.
     * Being able to say "not that one" about each of them is the same request
     * as being able to say it about a granted folder.
     */
    val deviceGroups: List<SourceGroup> = emptyList(),
    /** Paths whose subfolders are shown, so a deep source opens quietly. */
    val expanded: Set<String> = emptySet(),
)

/**
 * The state behind the sources screen.
 *
 * It rescans on open rather than sharing the library's scan. The two screens
 * ask different questions — the library wants the documents it may show, this
 * one wants every folder that exists including the ones currently switched off
 * — and a scan is cheap enough that keeping them independent is worth more than
 * the saved work.
 */
class SourcesViewModel(
    private val repository: PdfRepository,
    private val libraryData: LibraryDataRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SourcesUiState())
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    private var documents: List<PdfDocument> = emptyList()
    private var rules: FolderRules = FolderRules.Empty
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            libraryData.observeFolderRules().collect { current ->
                rules = current
                rebuild()
            }
        }
        refresh()
    }

    fun refresh() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = true) }
        scanJob = viewModelScope.launch {
            runCatchingCancellable { repository.loadLibrary() }
                .onSuccess { snapshot ->
                    documents = snapshot.documents
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            deviceScanPermitted = repository.isDeviceScanPermitted(),
                            deviceScanEnabled = snapshot.deviceScanEnabled,
                        )
                    }
                    rebuild(snapshot.grantedFolders)
                }
                .onFailure { _uiState.update { state -> state.copy(isScanning = false) } }
        }
    }

    /** Show or hide a folder's own subfolders. */
    fun onToggleExpanded(path: String) {
        _uiState.update { state ->
            state.copy(
                expanded = if (path in state.expanded) {
                    state.expanded - path
                } else {
                    state.expanded + path
                },
            )
        }
    }

    /**
     * Switch one folder on or off.
     *
     * Turning a folder back to what it would have been anyway drops the
     * decision instead of storing the opposite one, so the table only ever
     * holds decisions that are actually doing something.
     */
    fun onSetVisible(path: String, visible: Boolean) = viewModelScope.launch {
        val inherited = FolderRules(rules.decisions() - path).allows(path)
        if (inherited == visible) {
            libraryData.clearFolderRule(path)
        } else {
            libraryData.setFolderRule(path, visible)
        }
    }

    /**
     * Give a source back.
     *
     * The grant is released and the decisions made inside it are forgotten,
     * because keeping them would silently reapply to a folder added again
     * later, which is a decision about a source that no longer exists.
     */
    fun onRemoveSource(folder: GrantedFolder) = viewModelScope.launch {
        repository.removeFolder(folder.treeUri)
        libraryData.clearFolderRules(folder.label)
        refresh()
    }

    /** Switch the whole-device sweep on or off without touching the permission. */
    fun onSetDeviceScan(enabled: Boolean) {
        settings.setDeviceScanOptOut(!enabled)
        refresh()
    }

    fun onFolderPicked(treeUri: Uri) {
        repository.addFolder(treeUri)
        refresh()
    }

    fun onFilesPicked(uris: List<Uri>) {
        uris.forEach(repository::addPickedFile)
        refresh()
    }

    private fun rebuild(folders: List<GrantedFolder>? = null) {
        val granted = folders ?: _uiState.value.groups.map { it.folder }
        val groups = granted.map { folder ->
            val tree = folderTreeUnder(folder.label, documents, rules)
            SourceGroup(
                folder = folder,
                tree = tree,
                visibleCount = countVisible(folder.label),
                totalCount = tree.firstOrNull { it.path == folder.label }?.total ?: 0,
                rootVisible = rules.allows(folder.label),
            )
        }
        val underGranted = granted.map { it.label }
        val loose = documents.filter { document ->
            val path = document.parentLabel
            path == null || underGranted.none { path == it || path.startsWith("$it/") }
        }
        // One group per top-level folder the sweep found something in.
        val deviceGroups = loose.mapNotNull { it.parentLabel?.substringBefore('/') }
            .distinct()
            .sortedBy { it.lowercase() }
            .map { root ->
                val tree = folderTreeUnder(root, loose, rules)
                SourceGroup(
                    folder = GrantedFolder(Uri.EMPTY, root),
                    tree = tree,
                    visibleCount = loose.count { document ->
                        val path = document.parentLabel ?: return@count false
                        (path == root || path.startsWith("$root/")) && rules.allows(path)
                    },
                    totalCount = tree.firstOrNull { it.path == root }?.total ?: 0,
                    rootVisible = rules.allows(root),
                )
            }
        _uiState.update { state ->
            state.copy(
                groups = groups,
                deviceScanCount = loose.size,
                deviceGroups = deviceGroups,
            )
        }
    }

    private fun countVisible(root: String): Int = documents.count { document ->
        val path = document.parentLabel ?: return@count false
        (path == root || path.startsWith("$root/")) && rules.allows(path)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as LlegeixApp
                SourcesViewModel(
                    app.pdfRepository,
                    app.libraryDataRepository,
                    app.settingsRepository,
                )
            }
        }
    }
}
