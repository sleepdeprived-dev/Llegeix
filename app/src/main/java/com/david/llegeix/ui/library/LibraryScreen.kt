package com.david.llegeix.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.PillGroup
import com.david.llegeix.ui.common.Pill
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.FolderWithCount
import com.david.llegeix.data.db.dao.ReadingProgress
import com.david.llegeix.data.db.dao.RecentDocument
import com.david.llegeix.data.source.DocumentNames
import com.david.llegeix.data.source.LibraryFolder
import com.david.llegeix.data.model.LibrarySort
import com.david.llegeix.data.settings.ContinueShelf
import com.david.llegeix.data.settings.LibraryLayout
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.ui.common.AppBottomSheet
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.SearchField
import com.david.llegeix.ui.common.RecentSearches
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.ui.common.TagPickerDialog
import com.david.llegeix.ui.common.FlagChoiceDialog
import com.david.llegeix.ui.common.Senyera
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.folders.FolderNameDialog
import com.david.llegeix.ui.sources.SourcesSheet
import com.david.llegeix.util.allFilesAccessIntents
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.launch

/** What the file picker will offer, so a photo cannot be added as a book. */
private const val PDF_MIME_TYPE = "application/pdf"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    onOpenDocument: (PdfDocument) -> Unit = {},
    /**
     * Opening something by handle rather than by document.
     *
     * The Continue shelf is built from the reading history, which knows a URI
     * and a name and nothing else about the file — no size, no date, no origin
     * — so it cannot hand back a whole [PdfDocument] and should not have to
     * invent one to be allowed to open a book.
     */
    onOpenReading: (uriString: String, title: String) -> Unit = { _, _ -> },
    /** Opens a document with its find bar already up, carrying [query]. */
    onFindInDocument: (uriString: String, title: String, query: String) -> Unit =
        { _, _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
) {
    // Whether somebody is searching, as opposed to the field merely being
    // empty — which, on the library, is nearly all of the time.
    var isSearchFocused by remember { mutableStateOf(false) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsState()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    var documentToFile by remember { mutableStateOf<PdfDocument?>(null) }
    var showNewFolderFor by remember { mutableStateOf<PdfDocument?>(null) }
    var tagsFor by remember { mutableStateOf<PdfDocument?>(null) }
    val allTags by viewModel.tags.collectAsStateWithLifecycle()
    val tagsByDocument by viewModel.tagsByDocument.collectAsStateWithLifecycle()
    val folderIdByDocument by viewModel.folderIdByDocument.collectAsStateWithLifecycle()
    val folderNameByDocument by viewModel.folderNameByDocument.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val continueReading by viewModel.continueReading.collectAsStateWithLifecycle()
    // Which source the sheet was raised about, if it is up at all. A source of
    // "" means it was raised without one in mind.
    var sourcesSheetFor by remember { mutableStateOf<String?>(null) }
    var showAddDocuments by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PdfDocument?>(null) }
    var hidingFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var choosingFlag by remember { mutableStateOf(false) }
    var forgettingRecent by remember { mutableStateOf<RecentDocument?>(null) }
    val names by viewModel.names.collectAsStateWithLifecycle()
    val flag by viewModel.flag.collectAsStateWithLifecycle()
    val continueShelf by viewModel.continueShelf.collectAsStateWithLifecycle()
    val updateWaiting by viewModel.updateWaiting.collectAsStateWithLifecycle()

    // Back goes up a folder before it leaves the library. Anything else makes
    // going three folders deep a thing you need a plan to get out of.
    BackHandler(enabled = state.isBrowsing && state.path != null) {
        viewModel.onNavigateUp()
    }

    // All Files Access is granted in system Settings, so the only reliable
    // signal that it changed is coming back to the foreground. The update
    // check rides along here for the same reason and no other: this is the
    // moment the app is certainly being looked at, so it is the one moment a
    // network request nobody pressed a button for is defensible. The
    // repository refuses to make it more than once a day.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.syncSources()
        viewModel.checkForUpdatesQuietly()
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri -> if (treeUri != null) viewModel.onFolderPicked(treeUri) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.onFilesPicked(uris) }

    // The result code is meaningless for the Settings screen; ON_RESUME above
    // is what actually picks up the new grant.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    val settingsUnavailable = stringResource(Res.string.library_settings_unavailable)
    fun openAllFilesSettings() {
        val opened = allFilesAccessIntents(context).any { intent ->
            runCatching { settingsLauncher.launch(intent) }.isSuccess
        }
        if (!opened) {
            scope.launch { snackbarHostState.showSnackbar(settingsUnavailable) }
        }
    }

    val recentRemoved = stringResource(Res.string.recent_removed)
    val undoLabel = stringResource(Res.string.action_undo)

    val errorMessage = state.errorMessage?.resolved()
    LaunchedEffect(errorMessage) {
        val message = errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onErrorShown()
    }

    Scaffold(
        // The app shell's Scaffold has already inset this screen for the
        // status bar and the navigation bar; counting them a second time
        // put a dead band above the bottom bar and made every top bar
        // 24dp taller than it asks to be.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        // The way documents get in, in words, in the place a phone puts its main
        // action. It was a 40dp plus disc at the end of the sources row: the
        // smallest target on the screen, unlabelled, for the one thing nobody
        // can use this app without doing. Not drawn over either of the empty
        // states, which already put a full-width button saying the same thing in
        // the middle of the screen — one screen should never offer the same
        // action twice.
        floatingActionButton = {
            if (state.hasAnySource && !state.isEmptyAfterScan) {
                FloatingActionButton(onClick = { showAddDocuments = true }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(Res.string.library_add_documents),
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Senyera(flag = flag, onClick = { choosingFlag = true })
                        Text(
                            text = stringResource(Res.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = Space.md),
                        )
                    }
                },
                actions = {
                    // Three icons, no menu. There used to be a three-dot menu
                    // here holding the layout and the sort order, and it was
                    // wrong twice over: a menu hides which setting is in force,
                    // so the only way to find out how the library was sorted
                    // was to open it, and a decision about the screen you are
                    // looking at belongs on that screen. Rescanning came out of
                    // it first, then the layout came out to sit here, and the
                    // sort order became a chip in the list that says what it
                    // is. Nothing was left behind worth keeping.
                    IconButton(onClick = viewModel::refresh, enabled = !state.isScanning) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(Res.string.library_rescan),
                        )
                    }
                    LayoutButton(layout = layout, onClick = viewModel::onToggleLayout)
                    SettingsButton(
                        updateWaiting = updateWaiting,
                        onClick = onOpenSettings,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (state.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            // Search and filters appear only once there is something to sift
            // through; on a small library they would be pure noise.
            if (state.totalFound > 0) {
                SearchField(
                    query = state.query,
                    placeholder = stringResource(Res.string.library_search),
                    onQueryChange = viewModel::onQueryChange,
                    onFocusChanged = { isSearchFocused = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.screen)
                        // Clear of the app bar, but no longer the generous drop
                        // it needed when the filters sat under it too: the
                        // chips have gone into the list, so the field and the
                        // bar are the only two objects left up here.
                        .padding(top = Space.lg, bottom = Space.sm),
                )

                // Only while somebody is actually searching: an empty field
                // is the library's resting state, and the last few words are an
                // offer made during a search rather than a fixture of the
                // screen. Once there is a query on screen the last one is
                // history in the unhelpful sense, so it goes then too.
                if (state.query.isBlank() && isSearchFocused) {
                    RecentSearches(
                        history = recentSearches,
                        onPick = viewModel::onQueryChange,
                        onClear = viewModel::onForgetSearches,
                        onRemove = viewModel::onForgetSearch,
                        modifier = Modifier.padding(bottom = Space.md),
                    )
                }

            }

            if (state.isBrowsing && state.path != null) {
                PathBar(
                    path = state.path.orEmpty(),
                    onUp = { viewModel.onNavigateUp() },
                    onOpenPath = { viewModel.onOpenPath(it) },
                    modifier = Modifier.padding(bottom = Space.md),
                )
            }

            LibraryBody(
                state = state,
                layout = layout,
                continueShelf = continueShelf,
                tagsByDocument = tagsByDocument,
                names = names,
                folderNameByDocument = folderNameByDocument,
                progress = progress,
                continueReading = continueReading,
                onViewChange = viewModel::onViewChange,
                onOpenSort = { showSort = true },
                onOpenSources = { sourcesSheetFor = "" },
                onContinue = { recent -> onOpenReading(recent.uriString, recent.displayName) },
                onForgetRecent = { recent -> forgettingRecent = recent },
                onSeeHistory = onOpenHistory,
                onToggleContinue = viewModel::onToggleContinueShelf,
                onOpenFolder = viewModel::onOpenFolder,
                onHideFolder = { hidingFolder = it },
                onEditTags = { tagsFor = it },
                onRename = { renaming = it },
                onAddDocuments = { showAddDocuments = true },
                // Opened by handle rather than by document, so the reader's own
                // name for a PDF is the one on the reader's app bar. Everything
                // else about the document is already on the screen behind it.
                onOpenDocument = { document ->
                    val named = names.customName(document.uriString)
                    if (named == null) {
                        onOpenDocument(document)
                    } else {
                        onOpenReading(document.uriString, named)
                    }
                },
                onFindInDocument = { document ->
                    onFindInDocument(document.uriString, document.displayName, state.query)
                },
                onMoveToFolder = { documentToFile = it },
                onToggleReadLater = viewModel::onToggleReadLater,
                onToggleBookmarked = viewModel::onToggleBookmarked,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    tagsFor?.let { document ->
        val selected = tagsByDocument[document.uriString].orEmpty().map { it.id }.toSet()
        TagPickerDialog(
            documentTitle = document.title,
            allTags = allTags,
            selectedIds = selected,
            onToggle = { viewModel.onToggleTag(document, it) },
            onCreate = { name, color -> viewModel.onCreateTag(document, name, color) },
            onRecolour = viewModel::onRecolourTag,
            onDelete = viewModel::onDeleteTag,
            onDismiss = { tagsFor = null },
            onRename = viewModel::onRenameTag,
        )
    }

    documentToFile?.let { document ->
        MoveToFolderDialog(
            document = document,
            folders = folders,
            currentFolderId = folderIdByDocument[document.uriString],
            onDismiss = { documentToFile = null },
            onChoose = { folderId ->
                viewModel.moveToFolder(document, folderId)
                documentToFile = null
            },
            onCreateNew = {
                documentToFile = null
                showNewFolderFor = document
            },
        )
    }

    sourcesSheetFor?.let { focus ->
        SourcesSheet(
            onDismiss = { sourcesSheetFor = null },
            focusSource = focus.takeIf { it.isNotEmpty() },
        )
    }

    if (showAddDocuments) {
        AddDocumentsSheet(
            deviceScanEnabled = state.deviceScanEnabled,
            onDismiss = { showAddDocuments = false },
            onAddFolder = {
                showAddDocuments = false
                folderPicker.launch(null)
            },
            onAddFiles = {
                showAddDocuments = false
                filePicker.launch(arrayOf(PDF_MIME_TYPE))
            },
            onScanDevice = {
                showAddDocuments = false
                openAllFilesSettings()
            },
        )
    }

    if (showSort) {
        SortSheet(
            current = state.sort,
            onChoose = {
                viewModel.onSortChange(it)
                showSort = false
            },
            onDismiss = { showSort = false },
        )
    }

    renaming?.let { document ->
        DocumentNameDialog(
            current = names.titleFor(document.uriString, document.displayName),
            hasCustomName = names.isRenamed(document.uriString),
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.onRenameDocument(document, name)
                renaming = null
            },
        )
    }

    hidingFolder?.let { folder ->
        AlertDialog(
            onDismissRequest = { hidingFolder = null },
            title = { Text(stringResource(Res.string.library_folder_hide_title, folder.name)) },
            text = { Text(stringResource(Res.string.library_folder_hide_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onHideFolder(folder.path)
                        hidingFolder = null
                    },
                ) { Text(stringResource(Res.string.library_folder_hide_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { hidingFolder = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // Asked rather than done. Holding a card on this shelf used to forget it on
    // the spot, with an undo in a snackbar — which is the right shape for an
    // action somebody meant, and the wrong one for a gesture nobody was aiming
    // for. A shelf whose whole purpose is being tapped without looking is a
    // shelf where a long press is very often a tap that lingered, and the undo
    // only helps the reader who noticed the message before it went.
    forgettingRecent?.let { recent ->
        AlertDialog(
            onDismissRequest = { forgettingRecent = null },
            title = { Text(stringResource(Res.string.recent_forget_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.recent_forget_body,
                        names.titleFor(recent.uriString, recent.displayName),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        forgettingRecent = null
                        viewModel.onForgetRecent(recent.uriString)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = recentRemoved,
                                actionLabel = undoLabel,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.onRestoreRecent(recent)
                            }
                        }
                    },
                ) { Text(stringResource(Res.string.recent_forget_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { forgettingRecent = null }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    if (choosingFlag) {
        FlagChoiceDialog(
            current = flag,
            onChoose = viewModel::onFlagChosen,
            onDismiss = { choosingFlag = false },
        )
    }

    showNewFolderFor?.let { document ->
        FolderNameDialog(
            onDismiss = { showNewFolderFor = null },
            onConfirm = { name ->
                viewModel.createFolderAndMove(document, name)
                showNewFolderFor = null
            },
        )
    }
}

/**
 * The two questions about how the library is drawn, answered on the library.
 *
 * On the left, whether you are walking the folders or looking at everything at
 * once: chips rather than a menu item, because which of the two you are in
 * changes what the whole screen means, and a mode you cannot see is a mode you
 * have to remember. Read-later was a third chip and has become a collection,
 * which is what it always was.
 *
 * On the right, the order the documents are in — and this is the part that is
 * new. Sorting lived in a three-dot menu, which meant the answer to "why is this
 * list in this order" was invisible until the menu was opened, and the order is
 * not a rare setting: it is the difference between finding the thing you saved
 * this morning and scrolling past two hundred books. The chip carries the short
 * name of the order in force, so the screen says how it is sorted whether or not
 * anybody asks, and pressing it opens the sheet where the choice is made.
 */
@Composable
private fun LibraryControlsRow(
    current: LibraryView,
    sort: LibrarySort,
    onViewChange: (LibraryView) -> Unit,
    onOpenSort: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen),
    ) {
        // The same pill control as every other "which of these" in the app,
        // rather than two outlined filter chips.
        PillGroup {
            LibraryView.entries.forEach { view ->
                val name = stringResource(view.labelRes)
                Pill(
                    selected = view == current,
                    onClick = { onViewChange(view) },
                    label = name,
                ) {
                    Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        Box(modifier = Modifier.weight(1f))
        // "Sort by" rather than the order's own name: at the end of this row a
        // chip saying *Recents* reads as a filter switched on, not as the
        // button that chooses an order. A soft tonal chip with no outline.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onOpenSort)
                .heightIn(min = 40.dp)
                .padding(horizontal = Space.lg),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sort),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(Res.string.library_sort_by),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/**
 * The order the library is in, chosen where there is room to say what each one
 * does.
 *
 * A sheet rather than the dropdown this used to be. A dropdown menu gives a line
 * of text per option and no more, so four orders arrived as four bare words —
 * "Tag" tells you nothing about what sorting by tag does to a list, and "Size"
 * does not say which end the big ones go. Here each has a sentence under it, and
 * the targets are rows rather than menu items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: LibrarySort,
    onChoose: (LibrarySort) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Space.xl),
        ) {
            Text(
                text = stringResource(Res.string.library_sort_by),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .padding(bottom = Space.sm),
            )
            LibrarySort.entries.forEach { sort ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = sort == current,
                            role = Role.RadioButton,
                            onClick = { onChoose(sort) },
                        )
                        .padding(horizontal = Space.lg, vertical = Space.md),
                ) {
                    RadioButton(selected = sort == current, onClick = null)
                    Column(modifier = Modifier.padding(start = Space.md)) {
                        Text(
                            text = stringResource(sort.labelRes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(sort.summaryRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Rows or covers, as one icon that shows the other one.
 *
 * A toggle rather than a pair of options, because there are only two and either
 * of them is one press from the other. The icon is the layout it switches *to*,
 * which is the convention every phone gallery uses and the only one that works
 * on a single button.
 */
@Composable
private fun LayoutButton(layout: LibraryLayout, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(
                if (layout == LibraryLayout.GRID) R.drawable.ic_list else R.drawable.ic_grid,
            ),
            contentDescription = stringResource(
                if (layout == LibraryLayout.GRID) {
                    Res.string.library_view_list
                } else {
                    Res.string.library_view_grid
                },
            ),
        )
    }
}

/**
 * The gear, with a dot on it when there is a newer version of the app.
 *
 * The dot is the whole of the update feature's presence outside Configuració,
 * and it is on the gear rather than anywhere else because that is where the
 * thing to do about it lives: a mark on the door to the room the answer is in.
 * It is drawn as a disc rather than as a badge with a number, because there is
 * only ever one newer version and counting it would be counting to one.
 */
@Composable
private fun SettingsButton(updateWaiting: Boolean, onClick: () -> Unit) {
    val waitingLabel = stringResource(Res.string.settings_update_waiting)
    val settingsLabel = stringResource(Res.string.settings_title)
    Box {
        IconButton(onClick = onClick) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = if (updateWaiting) {
                    "$settingsLabel · $waitingLabel"
                } else {
                    settingsLabel
                },
            )
        }
        if (updateWaiting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(UpdateDotSize)
                    // Ringed in the bar's own colour so the dot reads as a dot
                    // rather than as a corner of the gear, whatever the icon
                    // happens to be doing underneath it.
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}

/** Small enough to be a mark, big enough to be seen. */
private val UpdateDotSize = 11.dp

/**
 * Rename a PDF, in the app only.
 *
 * The sentence under the field is the point of the dialog. "Rename" on a phone
 * means renaming the file, and this does not — Llegeix holds a read-only grant
 * on somebody else's file and has no business writing to it — so the dialog
 * says which of the two it is before the reader finds out by looking in their
 * file manager.
 */
@Composable
private fun DocumentNameDialog(
    current: String,
    hasCustomName: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.document_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.document_rename_label)) },
                    singleLine = true,
                )
                Text(
                    text = stringResource(Res.string.document_rename_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.md),
                )
                // Only once there is something to undo. On a PDF still called
                // what the file is called, this would be a button that does
                // nothing, phrased as though it did.
                if (hasCustomName) {
                    TextButton(
                        onClick = { onConfirm("") },
                        modifier = Modifier.padding(top = Space.sm),
                    ) { Text(stringResource(Res.string.document_rename_reset)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

/**
 * Where you are, and the way back out.
 *
 * The arrow and the trail say the same thing twice on purpose: the arrow is
 * the target you hit without thinking, and the trail is the answer to "how did
 * I get here" that means you never have to reconstruct it. It scrolls sideways
 * rather than wrapping, so a deep path costs the list no height, and it keeps
 * the end of the path in view because that is the part you are in.
 */
@Composable
private fun PathBar(
    path: String,
    onUp: () -> Unit,
    onOpenPath: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val segments = path.split('/')
    val listState = rememberLazyListState()
    LaunchedEffect(path) {
        if (segments.isNotEmpty()) listState.scrollToItem(segments.lastIndex)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Space.md, end = Space.screen),
    ) {
        IconButton(onClick = onUp) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_up),
                contentDescription = stringResource(Res.string.library_folder_up),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        LazyRow(
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            item {
                PathSegment(
                    label = stringResource(Res.string.library_path_root),
                    isCurrent = false,
                    onClick = { onOpenPath(null) },
                )
            }
            items(segments.size) { index ->
                val isCurrent = index == segments.lastIndex
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    PathSegment(
                        label = segments[index],
                        isCurrent = isCurrent,
                        onClick = {
                            if (!isCurrent) onOpenPath(segments.take(index + 1).joinToString("/"))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PathSegment(label: String, isCurrent: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (isCurrent) FontWeight.Medium else null,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (isCurrent) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

/**
 * One folder in the library, as a row you go into.
 *
 * The same tinted disc the Folders screen uses, so a folder looks like a
 * folder wherever the app draws one. The count is everything below it rather
 * than what is directly inside: a closed folder is being asked whether it is
 * worth opening.
 *
 * Holding it offers to stop showing it. That answer used to be reachable only
 * from the Sources sheet, three steps from the folder it is about — and "I do
 * not want this folder in my library" is a thought people have while looking at
 * the folder in their library. It is a hold rather than a button because it is
 * the rarer of the two things to do with a folder by a wide margin, and it is a
 * dialog rather than an instant because the wording is doing real work: nothing
 * here deletes anything from the phone.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryFolderRow(
    folder: LibraryFolder,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = Space.screen, end = Space.md)
            .padding(vertical = Space.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_folder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        ) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    pluralStringResource(
                        Res.plurals.folders_pdf_count,
                        folder.documentCount,
                        folder.documentCount,
                    ),
                    if (folder.folderCount > 0) {
                        pluralStringResource(
                            Res.plurals.sources_folder_count,
                            folder.folderCount,
                            folder.folderCount,
                        )
                    } else {
                        null
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LibraryBody(
    state: LibraryUiState,
    layout: LibraryLayout,
    continueShelf: ContinueShelf,
    tagsByDocument: Map<String, List<DocumentTag>>,
    /** What every PDF is called. See [com.david.llegeix.data.source.DocumentNames]. */
    names: DocumentNames,
    /** The user's folder each PDF is filed in, for the row's detail line. */
    folderNameByDocument: Map<String, String>,
    /** How far through each opened document the reader is, keyed by URI. */
    progress: Map<String, ReadingProgress>,
    continueReading: List<RecentDocument>,
    onViewChange: (LibraryView) -> Unit,
    onOpenSort: () -> Unit,
    onOpenSources: () -> Unit,
    onContinue: (RecentDocument) -> Unit,
    onForgetRecent: (RecentDocument) -> Unit,
    onSeeHistory: () -> Unit,
    onToggleContinue: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onHideFolder: (LibraryFolder) -> Unit,
    onEditTags: (PdfDocument) -> Unit,
    onRename: (PdfDocument) -> Unit,
    onAddDocuments: () -> Unit,
    onOpenDocument: (PdfDocument) -> Unit,
    onFindInDocument: (PdfDocument) -> Unit,
    onMoveToFolder: (PdfDocument) -> Unit,
    onToggleReadLater: (PdfDocument) -> Unit,
    onToggleBookmarked: (PdfDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // Nothing is set up yet, so there is exactly one thing to do and one
        // button that does it. It used to be two — a folder, with the
        // whole-device sweep offered quietly underneath — which was the right
        // order of preference and the wrong number of decisions for somebody
        // who has just opened the app for the first time and does not yet know
        // what either of them means. The button opens the same sheet the rest of
        // the app opens, where the three ways in are listed with a line each
        // saying what they do, folder first.
        !state.hasAnySource -> EmptyState(
            title = stringResource(Res.string.library_welcome_title),
            body = stringResource(Res.string.library_welcome_body),
            icon = painterResource(R.drawable.ic_library),
            modifier = modifier,
            primaryAction = {
                Button(onClick = onAddDocuments) {
                    Text(stringResource(Res.string.library_add_documents))
                }
            },
        )

        state.isScanning && state.documents.isEmpty() && state.folders.isEmpty() -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        // The shelves stay above both of the "nothing here" states, and they
        // are most of the answer in each. An empty library whose sources row
        // reads "0 of 48 PDFs shown" has diagnosed itself, and a library
        // filtered down to nothing needs the chips to still be reachable, or
        // the only way out of the empty screen is the back button.
        state.isEmptyAfterScan -> Column(modifier = modifier) {
            LibraryShelves(
                state = state,
                shelf = continueShelf,
                names = names,
                continueReading = continueReading,
                onViewChange = onViewChange,
                onOpenSources = onOpenSources,
                onContinue = onContinue,
                onForgetRecent = onForgetRecent,
                onSeeHistory = onSeeHistory,
                onToggleContinue = onToggleContinue,
                onOpenSort = onOpenSort,
            )
            EmptyState(
                title = stringResource(Res.string.library_empty_title),
                body = stringResource(Res.string.library_empty_body),
                icon = painterResource(R.drawable.ic_library),
                // Weighted rather than filling: the shelves above have already
                // taken height, and an empty state that insists on the whole
                // screen would centre itself half off the bottom of it.
                modifier = Modifier.weight(1f),
                // The same one button as everywhere else, rather than guessing
                // which of the two ways in this reader needs next. The sheet
                // knows which ones are still available and offers those.
                primaryAction = {
                    Button(onClick = onAddDocuments) {
                        Text(stringResource(Res.string.library_add_documents))
                    }
                },
            )
        }

        state.isFilteredToNothing -> Column(modifier = modifier) {
            LibraryShelves(
                state = state,
                shelf = continueShelf,
                names = names,
                continueReading = continueReading,
                onViewChange = onViewChange,
                onOpenSources = onOpenSources,
                onContinue = onContinue,
                onForgetRecent = onForgetRecent,
                onSeeHistory = onSeeHistory,
                onToggleContinue = onToggleContinue,
                onOpenSort = onOpenSort,
            )
            EmptyState(
                title = stringResource(Res.string.library_no_matches_title),
                body = stringResource(
                    Res.string.library_no_matches_body,
                    state.totalFound,
                    state.query,
                ),
                modifier = Modifier.weight(1f),
            )
        }

        layout == LibraryLayout.GRID -> LazyVerticalGrid(
            // Adaptive rather than a fixed count, so a wide screen or landscape
            // fits more covers instead of stretching two of them.
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = modifier,
            contentPadding = PaddingValues(
                start = Space.md,
                end = Space.md,
                // Room for the Add button to float over nothing. Without it the
                // last row of covers sits under it and cannot be read or
                // pressed.
                bottom = FabClearance,
            ),
        ) {
            // The shelves scroll away with the library rather than being pinned
            // above it. Pinned, they would have made the top third of the screen
            // permanent furniture on a screen that is mostly meant to be a list.
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryShelves(
                    state = state,
                    shelf = continueShelf,
                    names = names,
                    continueReading = continueReading,
                    onViewChange = onViewChange,
                    onOpenSources = onOpenSources,
                    onContinue = onContinue,
                    onForgetRecent = onForgetRecent,
                    onSeeHistory = onSeeHistory,
                    onToggleContinue = onToggleContinue,
                    onOpenSort = onOpenSort,
                )
            }
            // Folders keep the full width even in the grid: they are not
            // covers, and a folder shaped like a book is a folder you open by
            // mistake.
            items(
                state.folders,
                key = { "folder:" + it.path },
                span = { GridItemSpan(maxLineSpan) },
            ) { folder ->
                LibraryFolderRow(
                    folder = folder,
                    onClick = { onOpenFolder(folder.path) },
                    onLongClick = { onHideFolder(folder) },
                )
            }
            if (state.folders.isNotEmpty() && state.documents.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = Space.screen)
                            .padding(top = Space.sm, bottom = Space.md),
                    )
                }
            }
            items(state.documents, key = { it.uriString }) { document ->
                DocumentCell(
                    document = document,
                    title = names.titleFor(document.uriString, document.displayName),
                    isBookmarked = state.isBookmarked(document),
                    isReadLater = state.isReadLater(document),
                    tags = tagsByDocument[document.uriString].orEmpty(),
                    progress = progress[document.uriString],
                    onClick = { onOpenDocument(document) },
                    onRename = { onRename(document) },
                    onMoveToFolder = { onMoveToFolder(document) },
                    onToggleReadLater = { onToggleReadLater(document) },
                    onToggleBookmarked = { onToggleBookmarked(document) },
                    onEditTags = { onEditTags(document) },
                    onSearchInside = { onFindInDocument(document) },
                )
            }
        }

        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = FabClearance),
        ) {
            item {
                LibraryShelves(
                    state = state,
                    shelf = continueShelf,
                    names = names,
                    continueReading = continueReading,
                    onViewChange = onViewChange,
                    onOpenSources = onOpenSources,
                    onContinue = onContinue,
                    onForgetRecent = onForgetRecent,
                    onSeeHistory = onSeeHistory,
                    onToggleContinue = onToggleContinue,
                    onOpenSort = onOpenSort,
                )
            }
            items(state.folders, key = { "folder:" + it.path }) { folder ->
                LibraryFolderRow(
                    folder = folder,
                    onClick = { onOpenFolder(folder.path) },
                    onLongClick = { onHideFolder(folder) },
                )
            }
            // The one rule this screen draws, and it earns it: above the line
            // are places to go, below it are things to read.
            if (state.folders.isNotEmpty() && state.documents.isNotEmpty()) {
                item {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = Space.screen)
                            .padding(top = Space.sm, bottom = Space.xs),
                    )
                }
            }
            items(state.documents, key = { it.uriString }) { document ->
                DocumentRow(
                    document = document,
                    title = names.titleFor(document.uriString, document.displayName),
                    isBookmarked = state.isBookmarked(document),
                    isReadLater = state.isReadLater(document),
                    tags = tagsByDocument[document.uriString].orEmpty(),
                    progress = progress[document.uriString],
                    folderName = folderNameByDocument[document.uriString],
                    showLocation = !state.isBrowsing,
                    onClick = { onOpenDocument(document) },
                    onRename = { onRename(document) },
                    onMoveToFolder = { onMoveToFolder(document) },
                    onToggleReadLater = { onToggleReadLater(document) },
                    onToggleBookmarked = { onToggleBookmarked(document) },
                    onEditTags = { onEditTags(document) },
                    onSearchInside = { onFindInDocument(document) },
                )
            }
        }
    }
}

/**
 * What sits above the library: what to carry on with, where it all comes from,
 * and how it is being shown.
 *
 * All three are drawn only at the top of the library and only when they have
 * something to say. Inside a folder the question on screen is what is in this
 * folder, and a shelf of unrelated part-read books is an answer to a question
 * nobody asked there.
 */
@Composable
private fun LibraryShelves(
    state: LibraryUiState,
    shelf: ContinueShelf,
    names: DocumentNames,
    continueReading: List<RecentDocument>,
    onViewChange: (LibraryView) -> Unit,
    onOpenSort: () -> Unit,
    onOpenSources: () -> Unit,
    onContinue: (RecentDocument) -> Unit,
    onForgetRecent: (RecentDocument) -> Unit,
    onSeeHistory: () -> Unit,
    onToggleContinue: () -> Unit,
) {
    val showsContinue = continueReading.isNotEmpty() &&
        state.path == null &&
        state.query.isBlank()
    val showsControls = state.totalFound > 0
    // Nothing to say, and therefore no height taken. Without this the header is
    // a bare gap above the first document on every screen that has none of the
    // three to draw.
    if (!state.showsSources && !showsContinue && !showsControls) return

    Column(modifier = Modifier.padding(bottom = Space.md)) {
        if (showsContinue) {
            ContinueReadingRow(
                entries = continueReading,
                names = names,
                collapsed = shelf == ContinueShelf.COLLAPSED,
                onOpen = onContinue,
                onForget = onForgetRecent,
                onSeeAll = onSeeHistory,
                onToggleCollapsed = onToggleContinue,
                modifier = Modifier.padding(top = Space.md),
            )
        }
        if (state.showsSources) {
            SourcesRow(
                sourceCount = state.sources.size,
                visibleCount = state.sourcesVisibleCount,
                totalCount = state.sourcesTotalCount,
                onOpenSources = onOpenSources,
                // Room on both sides of it. The shelf above and the list below
                // are both long runs of similar-looking rows, and a card with
                // eight points of air around it reads as a third thing rather
                // than as the seam between the other two.
                modifier = Modifier.padding(top = if (showsContinue) Space.xl else Space.md),
            )
        }
        // The controls scroll with the library rather than sitting under the
        // search field for ever. Between the bar, the field, the chips and the
        // path there were five rows of furniture above the first document; the
        // field is the only one worth keeping on screen at all times, because
        // it is the only one somebody reaches for without having scrolled back
        // to the top first.
        if (showsControls) {
            LibraryControlsRow(
                current = state.view,
                sort = state.sort,
                onViewChange = onViewChange,
                onOpenSort = onOpenSort,
                modifier = Modifier.padding(
                    top = if (showsContinue || state.showsSources) Space.xl else 0.dp,
                    bottom = Space.sm,
                ),
            )
        }
    }
}

/**
 * The one place documents get into this app, whichever button was pressed.
 *
 * The welcome screen, the empty library and the button at the bottom right all
 * open this, and that is the point: there was a version of this app where the
 * first screen offered a folder and a whole-device sweep, the empty state
 * offered one of the two depending on what was already on, and a plus disc at
 * the top offered a third pair in a sheet. Three surfaces, overlapping, none of
 * them the complete answer. This one lists every way in, in the order they
 * should be preferred, and each says what it actually does — "a folder and
 * everything inside it" against "single PDFs" is the distinction people get
 * wrong, and it costs one line to answer in advance.
 *
 * Folder first, deliberately. The whole-device sweep is last because it means
 * leaving the app for Android's All Files Access screen — the broadest permission
 * the system has — and it is not offered at all once it is already on. Picking a
 * folder grants nothing beyond that folder, happens in a dialog, and is enough
 * for most people for ever.
 *
 * There is no *manage sources* row on it. There was, under a rule, and it was
 * the one thing in the sheet that did not add anything: a reader who pressed
 * *Add documents* had already said what they wanted, and the card at the top of
 * the library says *Sources* and opens exactly that. One sheet, one job.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDocumentsSheet(
    deviceScanEnabled: Boolean,
    onDismiss: () -> Unit,
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    onScanDevice: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = Space.xl),
        ) {
            Text(
                text = stringResource(Res.string.library_add_documents),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            // The sentence that stops the whole sheet being frightening: none of
            // these three moves, copies or changes anybody's files.
            Text(
                text = stringResource(Res.string.sources_add_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .padding(top = Space.xs, bottom = Space.md),
            )
            AddDocumentsOption(
                icon = painterResource(R.drawable.ic_folder),
                title = stringResource(Res.string.action_add_folder),
                body = stringResource(Res.string.sources_add_folder_body),
                onClick = onAddFolder,
            )
            AddDocumentsOption(
                icon = painterResource(R.drawable.ic_file),
                title = stringResource(Res.string.library_add_files),
                body = stringResource(Res.string.sources_add_files_body),
                onClick = onAddFiles,
            )
            if (!deviceScanEnabled) {
                AddDocumentsOption(
                    icon = painterResource(R.drawable.ic_device),
                    title = stringResource(Res.string.action_scan_device),
                    body = stringResource(Res.string.sources_add_device_body),
                    onClick = onScanDevice,
                )
            }
        }
    }
}

/**
 * One way in, as a row you could read out loud.
 *
 * The icon sits in a tinted disc, the same 40dp disc the sources card gets at the
 * top of the library, because a bare glyph beside two lines of text reads as
 * decoration and a disc reads as an object.
 */
@Composable
private fun AddDocumentsOption(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.screen, vertical = Space.md),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.padding(start = Space.lg)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * Picks the folder a PDF should be filed into, or unfiles it.
 *
 * A list of bare text buttons before, which said nothing about which folder the
 * document was already in, nothing about what was in each folder, and lost the
 * colours the reader had chosen on the Folders screen. Filing is a choice
 * between things you made, so this shows them the way you made them, ticks the
 * one already in force, and puts "no folder" among the options instead of
 * hiding it as a separate verb.
 */
@Composable
private fun MoveToFolderDialog(
    document: PdfDocument,
    folders: List<FolderWithCount>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onChoose: (Long?) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.library_move_to_folder)) },
        text = {
            Column {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.library_no_folders_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Space.lg),
                    )
                } else {
                    Column(
                        // Capped and scrollable: a reader with twenty folders
                        // should still be able to reach the buttons underneath.
                        modifier = Modifier
                            .padding(top = Space.md)
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        FolderChoiceRow(
                            folder = null,
                            selected = currentFolderId == null,
                            onClick = { onChoose(null) },
                        )
                        folders.forEach { folder ->
                            FolderChoiceRow(
                                folder = folder,
                                selected = folder.id == currentFolderId,
                                onClick = { onChoose(folder.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) {
                Text(stringResource(Res.string.library_new_folder_ellipsis))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

/**
 * One line in the folder picker. A null [folder] is the "no folder" choice.
 *
 * The swatch keeps its column whether or not there is a colour to put in it, so
 * every name in the list starts at the same place. A ragged left edge is the
 * quickest way to make four short words look like a form to be worked out.
 */
@Composable
private fun FolderChoiceRow(
    folder: FolderWithCount?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 52.dp)
            .padding(end = Space.sm),
    ) {
        RadioButton(selected = selected, onClick = null)
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (folder != null) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            folder.colorArgb?.let { HighlightColors.compose(it) }
                                ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        ),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md),
        ) {
            Text(
                text = folder?.name ?: stringResource(Res.string.library_no_folder),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (folder != null) {
                Text(
                    text = if (folder.documentCount == 0) {
                        stringResource(Res.string.folders_empty_count)
                    } else {
                        pluralStringResource(
                            Res.plurals.folders_pdf_count,
                            folder.documentCount,
                            folder.documentCount,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * How much room the Add button needs under the last document.
 *
 * A 56dp extended button with its own 16dp margin, and a little over: the
 * bottom of a list should not stop exactly where a floating control starts, or
 * the last row looks like it has been cut off rather than left clear.
 */
private val FabClearance = 88.dp
