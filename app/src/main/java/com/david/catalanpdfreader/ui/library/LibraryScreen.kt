package com.david.catalanpdfreader.ui.library

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.catalanpdfreader.data.db.entity.FolderEntity
import com.david.catalanpdfreader.data.model.LibrarySort
import com.david.catalanpdfreader.data.model.PdfDocument
import com.david.catalanpdfreader.ui.common.Senyera
import com.david.catalanpdfreader.ui.folders.FolderNameDialog
import com.david.catalanpdfreader.util.allFilesAccessIntents
import kotlinx.coroutines.launch

private const val PDF_MIME_TYPE = "application/pdf"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    onOpenDocument: (PdfDocument) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val folders by viewModel.folders.collectAsState()
    var documentToFile by remember { mutableStateOf<PdfDocument?>(null) }
    var showNewFolderFor by remember { mutableStateOf<PdfDocument?>(null) }

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

    fun openAllFilesSettings() {
        val opened = allFilesAccessIntents(context).any { intent ->
            runCatching { settingsLauncher.launch(intent) }.isSuccess
        }
        if (!opened) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Could not open Settings. Grant \"All files access\" manually.",
                )
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
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
                            text = "Catalan PDF Reader",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refresh,
                        enabled = !state.isScanning,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rescan")
                    }
                    LibraryMenu(
                        currentSort = state.sort,
                        onSortChange = viewModel::onSortChange,
                        onAddFiles = { filePicker.launch(arrayOf(PDF_MIME_TYPE)) },
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (state.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            SourceButtons(
                folders = state.grantedFolders,
                deviceScanEnabled = state.deviceScanEnabled,
                onScanDevice = ::openAllFilesSettings,
                onAddFolder = { folderPicker.launch(null) },
                onRemoveFolder = { viewModel.onFolderRemoved(it.treeUri) },
                modifier = Modifier.padding(top = 12.dp),
            )

            if (state.totalFound > 0) {
                LibraryFilterRow(
                    current = state.filter,
                    readLaterCount = state.readLaterUris.size,
                    onFilterChange = viewModel::onFilterChange,
                    modifier = Modifier.padding(top = 12.dp),
                )

                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Search") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            LibraryBody(
                state = state,
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(LibraryFilter.entries) { filter ->
            val label = if (filter == LibraryFilter.READ_LATER && readLaterCount > 0) {
                "${filter.label} ($readLaterCount)"
            } else {
                filter.label
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
    onAddFolder: () -> Unit,
    onScanDevice: () -> Unit,
    onOpenDocument: (PdfDocument) -> Unit,
    onMoveToFolder: (PdfDocument) -> Unit,
    onToggleReadLater: (PdfDocument) -> Unit,
    onToggleBookmarked: (PdfDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !state.hasAnySource -> Box(modifier)

        state.isScanning && state.documents.isEmpty() -> Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }

        state.isEmptyAfterScan -> LibraryMessage(
            title = "No PDFs found",
            body = "Nothing with a .pdf extension turned up. PDFs in Downloads " +
                "cannot be reached by watching a folder — scan the whole device " +
                "instead, or add them individually from the menu.",
            modifier = modifier,
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!state.deviceScanEnabled) {
                        Button(onClick = onScanDevice) { Text("Scan whole device") }
                    }
                    OutlinedButton(onClick = onAddFolder) { Text("Add folder") }
                }
            },
        )

        state.isFilteredToNothing -> LibraryMessage(
            title = if (state.filter == LibraryFilter.READ_LATER) {
                "Nothing to read later"
            } else {
                "No matches"
            },
            body = if (state.filter == LibraryFilter.READ_LATER) {
                "Mark a PDF \"Read later\" from its menu and it will collect here."
            } else {
                "None of the ${state.totalFound} PDFs match \"${state.query}\"."
            },
            modifier = modifier,
        )

        else -> LazyColumn(modifier = modifier) {
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
                HorizontalDivider(
                    modifier = Modifier.padding(start = 72.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
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
        title = { Text("Move to folder") },
        text = {
            Column {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (folders.isEmpty()) {
                    Text(
                        text = "You have no folders yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                } else {
                    folders.forEach { folder ->
                        TextButton(onClick = { onChoose(folder.id) }) { Text(folder.name) }
                    }
                    TextButton(onClick = { onChoose(null) }) { Text("Remove from folder") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCreateNew) { Text("New folder…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Sort options plus the file picker, kept out of the main surface. */
@Composable
private fun LibraryMenu(
    currentSort: LibrarySort,
    onSortChange: (LibrarySort) -> Unit,
    onAddFiles: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Add PDFs directly…") },
                onClick = {
                    onAddFiles()
                    expanded = false
                },
            )
            HorizontalDivider()
            LibrarySort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
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
