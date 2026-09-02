package com.david.llegeix.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.david.llegeix.data.source.LibraryFolder
import com.david.llegeix.data.model.LibrarySort
import com.david.llegeix.data.settings.LibraryLayout
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.HighlightColors
import com.david.llegeix.data.db.dao.DocumentTag
import com.david.llegeix.ui.common.TagPickerDialog
import com.david.llegeix.ui.common.Senyera
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.folders.FolderNameDialog
import com.david.llegeix.util.allFilesAccessIntents
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    onOpenDocument: (PdfDocument) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenSources: () -> Unit = {},
) {
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

    // Back goes up a folder before it leaves the library. Anything else makes
    // going three folders deep a thing you need a plan to get out of.
    BackHandler(enabled = state.isBrowsing && state.path != null) {
        viewModel.onNavigateUp()
    }

    // All Files Access is granted in system Settings, so the only reliable
    // signal that it changed is coming back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.syncSources()
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri -> if (treeUri != null) viewModel.onFolderPicked(treeUri) }

    // The result code is meaningless for the Settings screen; ON_RESUME above
    // is what actually picks up the new grant.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    val settingsUnavailable = stringResource(R.string.library_settings_unavailable)
    fun openAllFilesSettings() {
        val opened = allFilesAccessIntents(context).any { intent ->
            runCatching { settingsLauncher.launch(intent) }.isSuccess
        }
        if (!opened) {
            scope.launch { snackbarHostState.showSnackbar(settingsUnavailable) }
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Senyera()
                        Text(
                            text = stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = Space.md),
                        )
                    }
                },
                actions = {
                    // Two icons and no more. Rescanning is something you do
                    // when you know you have added a file, which is exactly the
                    // kind of thing that belongs behind the menu rather than
                    // permanently beside the app's name.
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                    LibraryMenu(
                        currentSort = state.sort,
                        layout = layout,
                        isScanning = state.isScanning,
                        onSortChange = viewModel::onSortChange,
                        onToggleLayout = viewModel::onToggleLayout,
                        onRescan = viewModel::refresh,
                        onOpenSources = onOpenSources,
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
                // A compact field rather than the default 56dp one with a
                // floating label. Search is a thing you reach for occasionally;
                // it should not be the tallest object above the library.
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.library_search),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(50),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onQueryChange("") },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(
                                        R.string.library_clear_search,
                                    ),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 42.dp)
                        .padding(horizontal = Space.screen)
                        // Dropped clear of the app bar rather than tucked under
                        // it. The bar, the field and the filters were three
                        // stacked objects in the top fifth of the screen with
                        // nothing between them.
                        .padding(top = Space.xxl, bottom = Space.lg),
                )

                LibraryViewRow(
                    current = state.view,
                    readLaterCount = state.readLaterUris.size,
                    onViewChange = viewModel::onViewChange,
                    modifier = Modifier.padding(bottom = Space.lg),
                )
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
                tagsByDocument = tagsByDocument,
                folderNameByDocument = folderNameByDocument,
                onOpenFolder = viewModel::onOpenFolder,
                onEditTags = { tagsFor = it },
                onAddFolder = { folderPicker.launch(null) },
                onScanDevice = ::openAllFilesSettings,
                onOpenDocument = onOpenDocument,
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
 * How the library is arranged: by folder, everything at once, or read-later.
 *
 * Chips rather than a menu item, because which of the three you are looking at
 * changes what the whole screen means, and a mode you cannot see is a mode you
 * have to remember.
 */
@Composable
private fun LibraryViewRow(
    current: LibraryView,
    readLaterCount: Int,
    onViewChange: (LibraryView) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        contentPadding = PaddingValues(horizontal = Space.screen),
    ) {
        items(LibraryView.entries) { view ->
            val name = stringResource(view.labelRes)
            val label = if (view == LibraryView.READ_LATER && readLaterCount > 0) {
                stringResource(R.string.library_filter_with_count, name, readLaterCount)
            } else {
                name
            }
            FilterChip(
                selected = view == current,
                onClick = { onViewChange(view) },
                label = { Text(label) },
            )
        }
    }
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
                contentDescription = stringResource(R.string.library_folder_up),
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
                    label = stringResource(R.string.library_path_root),
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
 */
@Composable
private fun LibraryFolderRow(
    folder: LibraryFolder,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    pluralStringResource(
                        R.plurals.folders_pdf_count,
                        folder.documentCount,
                        folder.documentCount,
                    ),
                    if (folder.folderCount > 0) {
                        pluralStringResource(
                            R.plurals.sources_folder_count,
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
    tagsByDocument: Map<String, List<DocumentTag>>,
    /** The user's folder each PDF is filed in, for the row's detail line. */
    folderNameByDocument: Map<String, String>,
    onOpenFolder: (String) -> Unit,
    onEditTags: (PdfDocument) -> Unit,
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    onOpenDocument: (PdfDocument) -> Unit,
    onMoveToFolder: (PdfDocument) -> Unit,
    onToggleReadLater: (PdfDocument) -> Unit,
    onToggleBookmarked: (PdfDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        // Nothing is set up yet. One obvious way in, with the narrower option
        // offered underneath rather than beside it.
        !state.hasAnySource -> EmptyState(
            title = stringResource(R.string.library_welcome_title),
            body = stringResource(R.string.library_welcome_body),
            icon = painterResource(R.drawable.ic_library),
            modifier = modifier,
            primaryAction = {
                Button(onClick = onScanDevice) {
                    Text(stringResource(R.string.action_scan_device))
                }
            },
            secondaryAction = {
                TextButton(onClick = onAddFolder) {
                    Text(stringResource(R.string.action_add_folder))
                }
            },
        )

        state.isScanning && state.documents.isEmpty() && state.folders.isEmpty() -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        state.isEmptyAfterScan -> EmptyState(
            title = stringResource(R.string.library_empty_title),
            body = stringResource(R.string.library_empty_body),
            icon = painterResource(R.drawable.ic_library),
            modifier = modifier,
            primaryAction = if (!state.deviceScanEnabled) {
                { Button(onClick = onScanDevice) {
                    Text(stringResource(R.string.action_scan_device))
                } }
            } else {
                { Button(onClick = onAddFolder) {
                    Text(stringResource(R.string.action_add_folder))
                } }
            },
        )

        state.isFilteredToNothing -> EmptyState(
            title = if (state.view == LibraryView.READ_LATER) {
                stringResource(R.string.library_read_later_empty_title)
            } else {
                stringResource(R.string.library_no_matches_title)
            },
            body = if (state.view == LibraryView.READ_LATER) {
                stringResource(R.string.library_read_later_empty_body)
            } else {
                stringResource(R.string.library_no_matches_body, state.totalFound, state.query)
            },
            modifier = modifier,
        )

        layout == LibraryLayout.GRID -> LazyVerticalGrid(
            // Adaptive rather than a fixed count, so a wide screen or landscape
            // fits more covers instead of stretching two of them.
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = modifier,
            contentPadding = PaddingValues(
                start = Space.md,
                end = Space.md,
                bottom = Space.lg,
            ),
        ) {
            // Folders keep the full width even in the grid: they are not
            // covers, and a folder shaped like a book is a folder you open by
            // mistake.
            items(
                state.folders,
                key = { "folder:" + it.path },
                span = { GridItemSpan(maxLineSpan) },
            ) { folder ->
                LibraryFolderRow(folder = folder, onClick = { onOpenFolder(folder.path) })
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
                    isBookmarked = state.isBookmarked(document),
                    isReadLater = state.isReadLater(document),
                    tags = tagsByDocument[document.uriString].orEmpty(),
                    onClick = { onOpenDocument(document) },
                    onMoveToFolder = { onMoveToFolder(document) },
                    onToggleReadLater = { onToggleReadLater(document) },
                    onToggleBookmarked = { onToggleBookmarked(document) },
                    onEditTags = { onEditTags(document) },
                )
            }
        }

        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Space.lg),
        ) {
            items(state.folders, key = { "folder:" + it.path }) { folder ->
                LibraryFolderRow(folder = folder, onClick = { onOpenFolder(folder.path) })
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
                    isBookmarked = state.isBookmarked(document),
                    isReadLater = state.isReadLater(document),
                    tags = tagsByDocument[document.uriString].orEmpty(),
                    folderName = folderNameByDocument[document.uriString],
                    showLocation = !state.isBrowsing,
                    onClick = { onOpenDocument(document) },
                    onMoveToFolder = { onMoveToFolder(document) },
                    onToggleReadLater = { onToggleReadLater(document) },
                    onToggleBookmarked = { onToggleBookmarked(document) },
                    onEditTags = { onEditTags(document) },
                )
            }
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
        title = { Text(stringResource(R.string.library_move_to_folder)) },
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
                        text = stringResource(R.string.library_no_folders_yet),
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
                Text(stringResource(R.string.library_new_folder_ellipsis))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
                text = folder?.name ?: stringResource(R.string.library_no_folder),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (folder != null) {
                Text(
                    text = if (folder.documentCount == 0) {
                        stringResource(R.string.folders_empty_count)
                    } else {
                        pluralStringResource(
                            R.plurals.folders_pdf_count,
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
 * Sort order, how the library is drawn, and the way into source management.
 *
 * Adding folders and turning on the device-wide scan used to sit permanently
 * above the list. They are things you do once and then forget, so they have
 * moved behind this menu and into
 * [com.david.llegeix.ui.sources.SourcesScreen], leaving the library itself as
 * just the documents.
 *
 * The menu is read top to bottom as three questions getting smaller: where the
 * documents come from, how they are drawn, and in what order. The divider is
 * there because the sort options are a set of alternatives and the items above
 * them are not, and a list where everything looks equally clickable is a list
 * you have to read twice.
 */
@Composable
private fun LibraryMenu(
    currentSort: LibrarySort,
    layout: LibraryLayout,
    isScanning: Boolean,
    onSortChange: (LibrarySort) -> Unit,
    onToggleLayout: () -> Unit,
    onRescan: () -> Unit,
    onOpenSources: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.action_more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                leadingIcon = { MenuIcon(painterResource(R.drawable.ic_folder)) },
                text = { Text(stringResource(R.string.library_sources_open)) },
                onClick = {
                    onOpenSources()
                    expanded = false
                },
            )
            DropdownMenuItem(
                leadingIcon = {
                    MenuIcon(
                        painterResource(
                            if (layout == LibraryLayout.GRID) {
                                R.drawable.ic_list
                            } else {
                                R.drawable.ic_grid
                            },
                        ),
                    )
                },
                text = {
                    Text(
                        stringResource(
                            if (layout == LibraryLayout.GRID) {
                                R.string.library_view_list
                            } else {
                                R.string.library_view_grid
                            },
                        ),
                    )
                },
                onClick = {
                    onToggleLayout()
                    expanded = false
                },
            )
            DropdownMenuItem(
                leadingIcon = { MenuIcon(Icons.Default.Refresh) },
                text = { Text(stringResource(R.string.library_rescan)) },
                // A scan already running is the one case where the menu would
                // otherwise accept a tap and do nothing with it.
                enabled = !isScanning,
                onClick = {
                    onRescan()
                    expanded = false
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = Space.xs))
            Text(
                text = stringResource(R.string.library_sort_by),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            )
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.labelRes)) },
                    leadingIcon = { RadioButton(selected = sort == currentSort, onClick = null) },
                    onClick = {
                        onSortChange(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}
