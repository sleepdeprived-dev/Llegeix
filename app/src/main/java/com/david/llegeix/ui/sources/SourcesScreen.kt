package com.david.llegeix.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.source.GrantedFolder
import com.david.llegeix.data.source.SourceFolder
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.allFilesAccessIntents

private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Where the library's documents come from, and which parts of it count.
 *
 * This replaced a bottom sheet. A sheet was the right size for "add a folder"
 * and much too small for the question this screen actually answers: granting
 * one folder can bring in a hundred others, and deciding which of those belong
 * in a reading library needs room to look at them.
 *
 * Each source lists the folders found inside it, indented, with a checkbox
 * apiece. Unticking a folder hides it and everything below it; ticking one
 * inside an unticked folder brings just that one back. Nothing is deleted from
 * the phone by anything on this screen, which the wording is careful to keep
 * saying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = viewModel(factory = SourcesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    var removing by remember { mutableStateOf<GrantedFolder?>(null) }
    val context = LocalContext.current

    // The result code is meaningless here; the rescan on return is what picks
    // up a grant that was just given.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refresh() }

    fun openDeviceScanSettings() {
        allFilesAccessIntents(context).any { intent ->
            runCatching { settingsLauncher.launch(intent) }.isSuccess
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::onFolderPicked) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.onFilesPicked(uris) }

    Scaffold(
        // The app shell has already inset this screen; counting the status and
        // navigation bars twice is what used to leave a dead band above the
        // bottom bar and a top bar taller than it asks to be.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                title = { Text(stringResource(R.string.library_sources)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.reader_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (state.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Space.screen,
                    end = Space.screen,
                    top = Space.lg,
                    bottom = Space.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.sources_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Space.sm),
                    )
                }

                items(state.groups, key = { it.folder.treeUri.toString() }) { group ->
                    SourceCard(
                        group = group,
                        expanded = state.expanded,
                        onToggleExpanded = viewModel::onToggleExpanded,
                        onSetVisible = viewModel::onSetVisible,
                        onRemove = { removing = group.folder },
                    )
                }

                item {
                    DeviceScanCard(
                        permitted = state.deviceScanPermitted,
                        enabled = state.deviceScanEnabled,
                        count = state.deviceScanCount,
                        onOpenSettings = ::openDeviceScanSettings,
                        onSetEnabled = viewModel::onSetDeviceScan,
                        modifier = Modifier.padding(top = Space.sm),
                    )
                }

                if (state.deviceScanEnabled) {
                    items(state.deviceGroups, key = { "device:" + it.folder.label }) { group ->
                        SourceCard(
                            group = group,
                            expanded = state.expanded,
                            onToggleExpanded = viewModel::onToggleExpanded,
                            onSetVisible = viewModel::onSetVisible,
                            // Nothing to hand back: the sweep is a permission,
                            // not a folder the app was given.
                            onRemove = null,
                        )
                    }
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        modifier = Modifier.padding(top = Space.sm),
                    ) {
                        TextButton(onClick = { folderPicker.launch(null) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.action_add_folder),
                                modifier = Modifier.padding(start = Space.sm),
                            )
                        }
                        TextButton(
                            onClick = { filePicker.launch(arrayOf(PDF_MIME_TYPE)) },
                        ) {
                            Text(stringResource(R.string.library_add_files))
                        }
                    }
                }
            }
        }
    }

    removing?.let { folder ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.sources_remove_title, folder.label)) },
            text = { Text(stringResource(R.string.sources_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onRemoveSource(folder)
                        removing = null
                    },
                ) { Text(stringResource(R.string.sources_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** One granted source: what it is, how much of it counts, and what is inside. */
@Composable
private fun SourceCard(
    group: SourceGroup,
    expanded: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onSetVisible: (String, Boolean) -> Unit,
    /** Null for a source that cannot be given back, such as the device sweep. */
    onRemove: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = Space.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Space.sm, end = Space.sm),
        ) {
            // The root gets a box of its own so the awkward case works: untick
            // the whole source, then tick back the one folder inside it that is
            // worth reading. Without this, keeping one folder out of forty
            // means unticking thirty-nine.
            Checkbox(
                checked = group.rootVisible,
                onCheckedChange = { onSetVisible(group.folder.label, it) },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.folder.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (group.visibleCount == group.totalCount) {
                        stringResource(R.string.sources_count, group.totalCount)
                    } else {
                        stringResource(
                            R.string.sources_count_partial,
                            group.visibleCount,
                            group.totalCount,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(
                            R.string.source_stop_watching,
                            group.folder.label,
                        ),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Only the folders below the root, and only those the reader has opened
        // their way down to. A source with sixty folders should not arrive as
        // sixty rows.
        val visibleRows = group.tree.filter { folder ->
            folder.depth > 0 && isReachable(folder.path, group.folder.label, expanded)
        }
        if (group.tree.size > 1) {
            visibleRows.forEach { folder ->
                FolderRow(
                    folder = folder,
                    hasChildren = group.tree.any { it.path.startsWith("${folder.path}/") },
                    isOpen = folder.path in expanded,
                    onToggleExpanded = { onToggleExpanded(folder.path) },
                    onSetVisible = { onSetVisible(folder.path, it) },
                )
            }
        }
    }
}

/** A folder is listed once every folder above it has been opened. */
private fun isReachable(path: String, root: String, expanded: Set<String>): Boolean {
    var parent = path.substringBeforeLast('/', "")
    while (parent.isNotEmpty() && parent != root) {
        if (parent !in expanded) return false
        parent = parent.substringBeforeLast('/', "")
    }
    return true
}

@Composable
private fun FolderRow(
    folder: SourceFolder,
    hasChildren: Boolean,
    isOpen: Boolean,
    onToggleExpanded: () -> Unit,
    onSetVisible: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasChildren, onClick = onToggleExpanded)
            .padding(
                // Indented by depth, so the shape of the folder tree is the
                // thing you see first.
                start = Space.md + (Space.lg * (folder.depth - 1)),
                end = Space.lg,
                top = Space.xs,
                bottom = Space.xs,
            ),
    ) {
        if (hasChildren) {
            Icon(
                imageVector = if (isOpen) {
                    Icons.Default.KeyboardArrowDown
                } else {
                    Icons.Default.KeyboardArrowRight
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Spacer(modifier = Modifier.width(18.dp))
        }
        Checkbox(
            checked = folder.visible,
            onCheckedChange = onSetVisible,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (folder.decidedHere) FontWeight.Medium else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (folder.visible) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            text = folder.total.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The whole-device sweep, which is a source like any other.
 *
 * Two states, and they are genuinely different questions. Without the
 * permission there is nothing to switch, so the row leads to the system screen
 * that grants it. With the permission there is, so it becomes a switch —
 * because Android will not let an app hand a special permission back, and
 * "erase everything" has to be able to stop the sweep somehow.
 */
@Composable
private fun DeviceScanCard(
    permitted: Boolean,
    enabled: Boolean,
    count: Int,
    onOpenSettings: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(if (permitted) Modifier else Modifier.clickable(onClick = onOpenSettings))
            .padding(start = Space.lg, end = if (permitted) Space.md else Space.lg)
            .padding(vertical = Space.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.action_scan_device),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when {
                    !permitted -> stringResource(R.string.library_device_scan_off)
                    enabled -> stringResource(R.string.sources_count, count)
                    else -> stringResource(R.string.sources_scan_paused)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (permitted) {
            Switch(checked = enabled, onCheckedChange = onSetEnabled)
        } else {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
