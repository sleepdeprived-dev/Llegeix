package com.david.llegeix.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.settings.AppFlag
import com.david.llegeix.data.settings.ContinueShelf
import com.david.llegeix.data.settings.LibraryLayout
import com.david.llegeix.data.settings.SearchHistoryRepository
import com.david.llegeix.data.settings.SearchScope
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.model.LibrarySort
import com.david.llegeix.R
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.ui.common.UiText
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.data.db.dao.FolderWithCount
import com.david.llegeix.data.db.dao.ReadingProgress
import com.david.llegeix.data.db.dao.RecentDocument
import com.david.llegeix.data.db.entity.TagEntity
import com.david.llegeix.data.source.DocumentNames
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.data.source.FolderRules
import com.david.llegeix.data.source.PdfRepository
import com.david.llegeix.data.source.applyFolderRules
import com.david.llegeix.data.source.documentsIn
import com.david.llegeix.data.source.foldersIn
import com.david.llegeix.data.source.nearestLivePath
import com.david.llegeix.update.UpdateRepository
import com.david.llegeix.util.runCatchingCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
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
    private val searchHistory: SearchHistoryRepository,
    private val updates: UpdateRepository,
) : ViewModel() {

    /**
     * Whether a newer version of the app is waiting, for the dot on the gear.
     *
     * The library is where this belongs because the gear is here. Configuració
     * is a screen you go to when you already suspect there is something to do;
     * a mark on the way in is what tells somebody there is.
     */
    val updateWaiting: StateFlow<Boolean> = updates.updateWaiting

    /**
     * Ask, quietly, whether there is a new version — at most once a day.
     *
     * Called when the library comes to the foreground, alongside the check for
     * a permission granted while the app was away, because that is the one
     * moment the app is certainly being looked at. The repository holds the
     * throttle; this is only the trigger.
     */
    fun checkForUpdatesQuietly() = viewModelScope.launch { updates.checkQuietly() }

    /** Searches that found a document, offered back under an empty field. */
    val recentSearches: StateFlow<List<String>> =
        searchHistory.history(SearchScope.DOCUMENTS)

    fun onForgetSearches() = searchHistory.forget(SearchScope.DOCUMENTS)

    /** Drop one past search, from the cross on its row. */
    fun onForgetSearch(query: String) = searchHistory.forgetOne(SearchScope.DOCUMENTS, query)

    private var rememberJob: Job? = null

    /**
     * Keep a search once it has settled and actually found something.
     *
     * Waiting is what keeps the list clean. These fields filter as you type, so
     * every prefix of every word passes through them; recording immediately
     * would fill the history with "c", "ca", "car". A pause means the reader
     * stopped to look at the result, and a result means it was worth stopping
     * for.
     */
    private fun rememberSearchLater(query: String) {
        rememberJob?.cancel()
        if (query.isBlank()) return
        rememberJob = viewModelScope.launch {
            delay(SEARCH_SETTLE_MS)
            val state = _uiState.value
            if (state.query == query && state.documents.isNotEmpty()) {
                searchHistory.record(SearchScope.DOCUMENTS, query)
            }
        }
    }

    /** Which flag sits beside the app's name, from the easter egg on it. */
    val flag: StateFlow<AppFlag> = settings.settings
        .map { it.flag }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.current.flag)

    fun onFlagChosen(flag: AppFlag) = settings.setFlag(flag)

    /**
     * The names the reader has given PDFs, keyed by URI.
     *
     * Consulted by every row rather than baked into [PdfDocument], because the
     * documents come from storage — which knows nothing about any of this — and
     * a scan that had to join against the database would be a scan that could
     * not run before the database was ready.
     */
    val names: StateFlow<DocumentNames> = libraryData.observeDocumentNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DocumentNames.Empty)

    /** Call a PDF something else inside the app. A blank name gives the file's back. */
    fun onRenameDocument(document: PdfDocument, name: String) = viewModelScope.launch {
        libraryData.setDocumentName(document.uriString, document.displayName, name)
    }

    /**
     * Stop showing one folder of the library, and remember that it exists.
     *
     * One thing now, whether the folder is a granted source or a folder inside
     * one: a decision is written that this path does not reach the library, and
     * nothing else happens. The permission is kept.
     *
     * It used to release the grant when the folder *was* the source, which was
     * tidy and wrong. Releasing it means the app forgets the folder completely:
     * it disappears from the Sources sheet, so there is no tick box left to
     * change your mind with, and getting it back means finding it again in the
     * system picker. Switching it off instead leaves it in the list, unticked,
     * where ticking it brings its documents straight back — which is what
     * somebody who hid a folder for a fortnight actually wants.
     *
     * Giving a folder back for good is still possible, from the Sources sheet,
     * where the wording can explain what the difference is.
     */
    fun onHideFolder(path: String) = viewModelScope.launch {
        // Any decisions made underneath it go, so that ticking the folder again
        // shows all of it rather than all of it except the four subfolders
        // somebody unticked last month.
        libraryData.clearFolderRules(path)
        libraryData.setFolderRule(path, included = false)
    }

    /** Rows or covers. Shared with Recent so the app looks like one app. */
    val layout: StateFlow<LibraryLayout> = settings.settings
        .map { it.libraryLayout }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), settings.current.libraryLayout)

    fun onToggleLayout() = settings.setLibraryLayout(layout.value.toggled())

    /**
     * How much of the Continue reading shelf the library draws.
     *
     * A preference rather than remembered-per-visit state, because a shelf that
     * unfolded itself every time the app was launched would not be folded away
     * in any useful sense.
     */
    val continueShelf: StateFlow<ContinueShelf> = settings.settings
        .map { it.continueShelf }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            settings.current.continueShelf,
        )

    /** The chevron on the shelf's heading: folded away, or back again. */
    fun onToggleContinueShelf() =
        settings.setContinueShelf(continueShelf.value.toggled())

    /** Take the shelf off the library. Undone from the snackbar, or in Configuració. */
    fun onHideContinueShelf() = settings.setContinueShelf(ContinueShelf.HIDDEN)

    fun onContinueShelfChange(shelf: ContinueShelf) = settings.setContinueShelf(shelf)

    /**
     * How far through each opened document the reader got.
     *
     * Fed straight to the covers, so a shelf says which books are started and
     * how far without a single number being read.
     */
    val progress: StateFlow<Map<String, ReadingProgress>> = libraryData.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * Books started and not finished, most recently read first.
     *
     * The reason the reader opened the app, in the place they open it. Recent
     * used to be a tab of its own, which meant the answer to "carry on with
     * what I was reading" was one tap away from the screen that appears on
     * launch, and looked like a fourth way of listing the same documents.
     */
    val continueReading: StateFlow<List<RecentDocument>> =
        libraryData.observeRecent(CONTINUE_LIMIT)
            .map { recents -> recents.filter { it.progress.isInProgress } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Rename one, keeping its colour and every document it is on. */
    fun onRenameTag(tag: TagEntity, name: String) = viewModelScope.launch {
        libraryData.renameTag(tag.id, name, tag.colorArgb)
    }

    /**
     * Which folders inside the granted sources reach the library.
     *
     * Database-backed, so it is safe to collect from `init` — a Room flow has
     * nothing to emit until the query runs, unlike a StateFlow that would fire
     * during construction.
     */
    val folderRules: StateFlow<FolderRules> = libraryData.observeFolderRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FolderRules.Empty)

    /** Folders offered by the "move to folder" dialog, with what is in them. */
    val folders: StateFlow<List<FolderWithCount>> = libraryData.observeFoldersWithCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The folder each filed document is in, so the picker can tick it. */
    val folderIdByDocument: StateFlow<Map<String, Long>> =
        libraryData.observeFolderAssignments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * The same thing by name, for the library row's detail line.
     *
     * Filing a PDF used to leave no trace anywhere the reader could see it: the
     * folder screen knew, and the library — the screen they were looking at
     * when they filed it — did not.
     */
    val folderNameByDocument: StateFlow<Map<String, String>> =
        combine(libraryData.observeFolderAssignments(), libraryData.observeFolders()) {
            assignments, folders ->
            val byId = folders.associateBy { it.id }
            assignments.mapNotNull { (uri, id) -> byId[id]?.let { uri to it.name } }.toMap()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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

        // Hiding a folder should take effect straight away, without a rescan:
        // the documents are already in hand, only the filter has changed.
        viewModelScope.launch {
            folderRules.collect {
                _uiState.update { state -> state.withVisibleDocuments() }
            }
        }

        // And so should renaming one, for the same reason and one more: the
        // search now matches the reader's own names, so the visible list is a
        // function of them.
        viewModelScope.launch {
            names.collect {
                _uiState.update { state -> state.withVisibleDocuments() }
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

    /** Forget one document's place in the reading history. */
    fun onForgetRecent(uriString: String) = viewModelScope.launch {
        libraryData.removeFromRecent(uriString)
    }

    /**
     * Put a forgotten entry back, timestamp and page and all.
     *
     * The undo behind the snackbar. Holding a card on the Continue shelf takes
     * it off, which is the right gesture for a shelf that is otherwise all
     * tapping — but only because it can be taken back. Restored with its own
     * `viewedAt`, so undoing does not silently promote the book to the front of
     * the shelf it was just removed from.
     */
    fun onRestoreRecent(recent: RecentDocument) = viewModelScope.launch {
        libraryData.restoreRecent(
            uriString = recent.uriString,
            displayName = recent.displayName,
            pageIndex = recent.lastPageIndex,
            viewedAt = recent.viewedAt,
        )
    }

    fun onFolderRemoved(treeUri: Uri) {
        repository.removeFolder(treeUri)
        syncSources()
        refresh()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query).withVisibleDocuments() }
        rememberSearchLater(query)
    }

    fun onSortChange(sort: LibrarySort) {
        _uiState.update { it.copy(sort = sort).withVisibleDocuments() }
    }

    fun onViewChange(view: LibraryView) {
        // The open folder is kept rather than reset: looking at everything for
        // a moment and coming back should come back to where you were.
        _uiState.update { it.copy(view = view).withVisibleDocuments() }
    }

    /** Go into a folder. */
    fun onOpenFolder(path: String) {
        _uiState.update { it.copy(path = path).withVisibleDocuments() }
    }

    /**
     * Go to a folder above the current one, or to the top when [path] is null.
     *
     * Returns false when there was nowhere to go, which is what tells the
     * system back button to leave the screen instead of being swallowed.
     */
    fun onOpenPath(path: String?): Boolean {
        if (_uiState.value.path == null && path == null) return false
        _uiState.update { it.copy(path = path).withVisibleDocuments() }
        return true
    }

    /** One folder up, for the back button and the arrow beside the path. */
    fun onNavigateUp(): Boolean {
        val current = _uiState.value.path ?: return false
        return onOpenPath(current.substringBeforeLast('/', "").ifEmpty { null })
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

    /**
     * Work out what is on screen: which folders, which documents, in what order.
     *
     * The three views are three different questions and are answered
     * separately. Browsing asks what is in one folder; the flat views ask what
     * exists at all. A search overrides every one of them and looks everywhere,
     * because a name half-remembered is not a name you know the folder of.
     */
    private fun LibraryUiState.withVisibleDocuments(): LibraryUiState {
        val needle = query.trim()
        // Folders the reader has switched off in Fonts never reach the library,
        // whichever view or search is running over it.
        val allowed = applyFolderRules(allDocuments, folderRules.value)
        val searching = needle.isNotEmpty()
        // A rescan or a folder switched off can empty the folder being looked
        // at; land on the nearest one still holding something instead.
        val here = nearestLivePath(path, allowed)

        val visible = when {
            // The reader's own name is matched as well as the file's. A book
            // renamed "Petit Príncep" and then searched for by that name found
            // nothing, because the filter only ever saw the filename — which is
            // the name the reader had just decided they did not want to use.
            searching -> allowed.filter { document ->
                document.displayName.contains(needle, ignoreCase = true) ||
                    document.parentLabel?.contains(needle, ignoreCase = true) == true ||
                    names.value.customName(document.uriString)
                        ?.contains(needle, ignoreCase = true) == true
            }

            view == LibraryView.ALL -> allowed
            else -> documentsIn(here, allowed)
        }

        return copy(
            path = here,
            sources = sourcesOf(allDocuments, allowed),
            documents = visible.sortedWith(
                sort.comparator { document ->
                    // Already alphabetical from the DAO, so the first is the
                    // one the row displays.
                    tagsByDocument.value[document.uriString]?.firstOrNull()?.name
                },
            ),
            // Folders are always alphabetical, whatever the documents are
            // sorted by: a folder has no size and no date of its own, and a
            // place you are looking for should be where it was last time.
            folders = if (view == LibraryView.FOLDERS && !searching) {
                foldersIn(here, allowed)
            } else {
                emptyList()
            },
            totalFound = allowed.size,
        )
    }

    /**
     * The sources, counted from the documents rather than scanned for again.
     *
     * Every document already carries the path it was found at, so which source
     * it belongs to is a prefix test — and the library has just done the work of
     * deciding which of them the folder rules let through. Asking storage a
     * second question here would double the cost of every rescan to learn
     * something already in hand.
     */
    private fun LibraryUiState.sourcesOf(
        everything: List<PdfDocument>,
        allowed: List<PdfDocument>,
    ): List<LibrarySource> {
        if (everything.isEmpty() && grantedFolders.isEmpty()) return emptyList()
        val labels = grantedFolders.map { it.label }

        fun under(root: String, documents: List<PdfDocument>) = documents.count { document ->
            val path = document.parentLabel ?: return@count false
            path == root || path.startsWith("$root/")
        }

        val granted = grantedFolders.map { folder ->
            LibrarySource(
                label = folder.label,
                kind = LibrarySource.Kind.GRANTED,
                visibleCount = under(folder.label, allowed),
                totalCount = under(folder.label, everything),
            )
        }
        if (!deviceScanEnabled) return granted

        // Whatever the sweep found that no granted folder already covers. One
        // tile, not one per top-level folder: the sweep is a single permission,
        // and splitting it into eight tiles would bury the folders the reader
        // deliberately granted among the ones Android happened to have.
        fun loose(documents: List<PdfDocument>) = documents.count { document ->
            val path = document.parentLabel
            path == null || labels.none { path == it || path.startsWith("$it/") }
        }
        val device = LibrarySource(
            // No label: the sweep is not a folder, so the tile names it from
            // resources and there is nothing for the sheet to open to.
            label = "",
            kind = LibrarySource.Kind.DEVICE,
            visibleCount = loose(allowed),
            totalCount = loose(everything),
        )
        return granted + device
    }

    companion object {
        /**
         * How many started books the strip offers.
         *
         * Enough that the one you want is nearly always there, few enough that
         * the strip stays a shelf you glance along rather than a list.
         */
        private const val CONTINUE_LIMIT = 12

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LlegeixApp
                LibraryViewModel(
                    app.pdfRepository,
                    app.libraryDataRepository,
                    app.settingsRepository,
                    app.searchHistoryRepository,
                    app.updateRepository,
                )
            }
        }
    }
}

/** Long enough that a pause means the reader stopped to read the result. */
private const val SEARCH_SETTLE_MS = 1_200L
