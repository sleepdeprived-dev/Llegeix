package com.david.llegeix.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.FolderEntity
import com.david.llegeix.data.model.LibrarySort
import com.david.llegeix.data.settings.LibraryLayout
import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuEmoji
import com.david.llegeix.ui.common.Senyera
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.folders.FolderNameDialog
import com.david.llegeix.util.allFilesAccessIntents
import kotlinx.coroutines.launch

private const val PDF_MIME_TYPE = "application/pdf"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    onOpenDocument: (PdfDocument) -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsState()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    var documentToFile by remember { mutableStateOf<PdfDocument?>(null) }
    var showNewFolderFor by remember { mutableStateOf<PdfDocument?>(null) }
    var showSources by remember { mutableStateOf(false) }

    // All Files Access is granted in system Settings, so the only reliable
    // signal that it changed is coming back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.syncSources()
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri -> if (treeUri != null) viewModel.onFolderPicked(treeUri) }

    // Filtered to PDFs, so nothing else is offered. Still the only route to a
    // file in Downloads, which Android refuses to grant as a watched folder.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.onFilesPicked(uris) }

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
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
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
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isScanning,
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.library_rescan),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                    LibraryMenu(
                        currentSort = state.sort,
                        layout = layout,
                        onSortChange = viewModel::onSortChange,
                        onToggleLayout = viewModel::onToggleLayout,
                        onOpenSources = { showSources = true },
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
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(stringResource(R.string.library_search)) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = stringResource(
                                        R.string.library_clear_search,
                                    ),
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.screen)
                        .padding(top = Space.sm, bottom = Space.md),
                )

                LibraryFilterRow(
                    current = state.filter,
                    readLaterCount = state.readLaterUris.size,
                    onFilterChange = viewModel::onFilterChange,
                    modifier = Modifier.padding(bottom = Space.sm),
                )
            }

            LibraryBody(
                state = state,
                layout = layout,
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

    if (showSources) {
        SourcesSheet(
            folders = state.grantedFolders,
            deviceScanEnabled = state.deviceScanEnabled,
            onDismiss = { showSources = false },
            onScanDevice = ::openAllFilesSettings,
            onAddFolder = { folderPicker.launch(null) },
            onAddFiles = { filePicker.launch(arrayOf(PDF_MIME_TYPE)) },
            onRemoveFolder = { viewModel.onFolderRemoved(it.treeUri) },
        )
    }

    documentToFile?.let { document ->
        MoveToFolderDialog(
            document = document,
            folders = folders,
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

/** "All" / "Read later", the quick way into the read-later shelf. */
@Composable
private fun LibraryFilterRow(
    current: LibraryFilter,
    readLaterCount: Int,
    onFilterChange: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        contentPadding = PaddingValues(horizontal = Space.screen),
    ) {
        items(LibraryFilter.entries) { filter ->
            val name = stringResource(filter.labelRes)
            val label = if (filter == LibraryFilter.READ_LATER && readLaterCount > 0) {
                stringResource(R.string.library_filter_with_count, name, readLaterCount)
            } else {
                name
            }
            FilterChip(
                selected = filter == current,
                onClick = { onFilterChange(filter) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun LibraryBody(
    state: LibraryUiState,
    layout: LibraryLayout,
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

        state.isScanning && state.documents.isEmpty() -> Box(
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
            title = if (state.filter == LibraryFilter.READ_LATER) {
                stringResource(R.string.library_read_later_empty_title)
            } else {
                stringResource(R.string.library_no_matches_title)
            },
            body = if (state.filter == LibraryFilter.READ_LATER) {
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
                bottom = Space.xxl,
            ),
        ) {
            items(state.documents, key = { it.uriString }) { document ->
                DocumentCell(
                    document = document,
                    isBookmarked = state.isBookmarked(document),
                    isReadLater = state.isReadLater(document),
                    onClick = { onOpenDocument(document) },
                    onMoveToFolder = { onMoveToFolder(document) },
                    onToggleReadLater = { onToggleReadLater(document) },
                    onToggleBookmarked = { onToggleBookmarked(document) },
                )
            }
        }

        else -> LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(bottom = Space.xxl),
        ) {
            items(state.documents, key = { it.uriString }) { document ->
                DocumentRow(
                    document = document,
                    isBookmarked = state.isBookmarked(document),
                    isReadLater = state.isReadLater(document),
                    onClick = { onOpenDocument(document) },
                    onMoveToFolder = { onMoveToFolder(document) },
                    onToggleReadLater = { onToggleReadLater(document) },
                    onToggleBookmarked = { onToggleBookmarked(document) },
                )
            }
        }
    }
}

/** Picks the folder a PDF should be filed into, or unfiles it. */
@Composable
private fun MoveToFolderDialog(
    document: PdfDocument,
    folders: List<FolderEntity>,
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
                )
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_no_folders_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = Space.lg),
                    )
                } else {
                    Column(modifier = Modifier.padding(top = Space.sm)) {
                        folders.forEach { folder ->
                            TextButton(onClick = { onChoose(folder.id) }) { Text(folder.name) }
                        }
                        TextButton(onClick = { onChoose(null) }) {
                            Text(stringResource(R.string.library_remove_from_folder))
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
 * Sort order plus the way into source management.
 *
 * Adding folders and turning on the device-wide scan used to sit permanently
 * above the list. They are things you do once and then forget, so they have
 * moved behind this menu and into [SourcesSheet], leaving the library itself as
 * just the documents.
 */
@Composable
private fun LibraryMenu(
    currentSort: LibrarySort,
    layout: LibraryLayout,
    onSortChange: (LibrarySort) -> Unit,
    onToggleLayout: () -> Unit,
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
                leadingIcon = { MenuEmoji("📂") },
                text = { Text(stringResource(R.string.library_sources_open)) },
                onClick = {
                    onOpenSources()
                    expanded = false
                },
            )
            DropdownMenuItem(
                leadingIcon = { MenuEmoji(if (layout == LibraryLayout.GRID) "☰" else "🔳") },
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
