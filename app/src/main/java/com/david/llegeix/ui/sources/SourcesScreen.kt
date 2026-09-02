package com.david.llegeix.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.source.GrantedFolder
import com.david.llegeix.data.source.SourceFolder
import com.david.llegeix.ui.common.MenuIcon
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
 * Each source is one card that says what it holds — "48 PDFs · 12 folders" —
 * and opens to show the folders inside it, each with a tick. Unticking a folder
 * hides it and everything below it; ticking one inside an unticked folder
 * brings just that one back. Nothing is deleted from the phone by anything on
 * this screen, which the wording is careful to keep saying.
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
        // The progress bar floats over the list rather than sitting above it:
        // as a row of its own it pushed every card down four pixels the moment
        // a rescan started, which is a whole screen twitching for no reason.
        Box(modifier = Modifier.padding(innerPadding)) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = Space.screen,
                    end = Space.screen,
                    top = Space.lg,
                    bottom = Space.xxl,
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
                        onSetSourceVisible = viewModel::onSetSourceVisible,
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

                if (state.deviceScanEnabled && state.deviceGroups.isNotEmpty()) {
                    item {
                        // Without this the sweep's folders look like more
                        // granted sources: the same card, in the same column,
                        // meaning something quite different. One quiet line
                        // says whose they are.
                        SectionLabel(stringResource(R.string.sources_found_by_scan))
                    }
                }

                if (state.deviceScanEnabled) {
                    items(state.deviceGroups, key = { "device:" + it.folder.label }) { group ->
                        SourceCard(
                            group = group,
                            expanded = state.expanded,
                            onToggleExpanded = viewModel::onToggleExpanded,
                            onSetVisible = viewModel::onSetVisible,
                            onSetSourceVisible = viewModel::onSetSourceVisible,
                            // Nothing to hand back: the sweep is a permission,
                            // not a folder the app was given.
                            onRemove = null,
                        )
                    }
                }

                item {
                    AddSourceCard(
                        onAddFolder = { folderPicker.launch(null) },
                        onAddFiles = { filePicker.launch(arrayOf(PDF_MIME_TYPE)) },
                        modifier = Modifier.padding(top = Space.xl),
                    )
                }
            }

            if (state.isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
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

/**
 * One granted source: what it is, how much of it counts, and what is inside.
 *
 * Folded shut unless someone opens it. A source is one line about a place —
 * "Documents, 48 PDFs in 12 folders" — and only becomes forty rows of folders
 * when that is the question being asked.
 */
@Composable
private fun SourceCard(
    group: SourceGroup,
    expanded: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onSetVisible: (String, Boolean) -> Unit,
    onSetSourceVisible: (String, Boolean) -> Unit,
    /** Null for a source that cannot be given back, such as the device sweep. */
    onRemove: (() -> Unit)?,
) {
    val root = group.folder.label
    val subfolders = (group.tree.size - 1).coerceAtLeast(0)
    val isOpen = root in expanded
    // Three states, because two cannot say "most of this source, but not all".
    // Without the middle one, a library quietly missing a folder looks exactly
    // like a library showing everything.
    val checkedState = when {
        group.totalCount == 0 -> if (group.rootVisible) {
            ToggleableState.On
        } else {
            ToggleableState.Off
        }
        group.visibleCount == group.totalCount -> ToggleableState.On
        group.visibleCount == 0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = Space.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (subfolders > 0) {
                        Modifier.clickable { onToggleExpanded(root) }
                    } else {
                        Modifier
                    },
                )
                .padding(start = Space.sm, end = Space.sm, top = Space.xs, bottom = Space.xs),
        ) {
            // The root box is all-or-nothing on purpose: it is the fastest way
            // to say "not this one at all", and the folders below it are where
            // the finer answer lives.
            val includeLabel = stringResource(R.string.sources_include, root)
            TriStateCheckbox(
                state = checkedState,
                onClick = {
                    onSetSourceVisible(root, checkedState != ToggleableState.On)
                },
                // Named, because on its own a tick box in a row of its own says
                // "on or off" without ever saying what of.
                modifier = Modifier.semantics { contentDescription = includeLabel },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.folder.label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = sourceSummary(group, subfolders),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (subfolders > 0) {
                Icon(
                    imageVector = if (isOpen) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = stringResource(
                        if (isOpen) R.string.sources_hide_folders else R.string.sources_show_folders,
                        root,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.sm),
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
        if (isOpen) {
            group.tree
                .filter { it.depth > 0 && isReachable(it.path, root, expanded) }
                .forEach { folder ->
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

/** "48 PDFs · 12 folders", or what is left of that once things are hidden. */
@Composable
private fun sourceSummary(group: SourceGroup, subfolders: Int): String {
    val documents = when {
        group.visibleCount == 0 && group.totalCount > 0 ->
            stringResource(R.string.sources_none_shown)

        group.visibleCount < group.totalCount -> stringResource(
            R.string.sources_count_partial,
            group.visibleCount,
            group.totalCount,
        )

        else -> pluralStringResource(
            R.plurals.folders_pdf_count,
            group.totalCount,
            group.totalCount,
        )
    }
    if (subfolders == 0) return documents
    val folders = pluralStringResource(R.plurals.sources_folder_count, subfolders, subfolders)
    return "$documents · $folders"
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

/**
 * One folder inside a source.
 *
 * The whole row is the tick, not just the little box: the question the row asks
 * is "does this folder count", and answering it should not require hitting an
 * 18dp target. Opening a folder to see what is under it is the separate,
 * smaller gesture, so it keeps the arrow to itself.
 */
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
            .toggleable(
                value = folder.visible,
                role = Role.Checkbox,
                onValueChange = onSetVisible,
            )
            .heightIn(min = 44.dp)
            .padding(
                // Indented by depth, so the shape of the folder tree is the
                // thing you see first.
                start = Space.xs + (Space.lg * (folder.depth - 1)),
                end = Space.lg,
            ),
    ) {
        if (hasChildren) {
            IconButton(onClick = onToggleExpanded, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (isOpen) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = stringResource(
                        if (isOpen) R.string.sources_hide_folders else R.string.sources_show_folders,
                        folder.name,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.width(28.dp))
        }
        Checkbox(checked = folder.visible, onCheckedChange = null)
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
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.sm),
        )
        Text(
            text = pluralStringResource(
                R.plurals.folders_pdf_count,
                folder.total,
                folder.total,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Space.sm),
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
                    enabled -> pluralStringResource(R.plurals.folders_pdf_count, count, count)
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
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The two ways to bring in something new.
 *
 * They were a pair of text buttons at the foot of the list, which read as an
 * afterthought and sat wherever the last source happened to end. As a card with
 * a heading they are the one thing on this screen that is clearly an action
 * rather than a setting, and each says what it is actually for — "a folder and
 * everything inside it" against "single PDFs" is the distinction people get
 * wrong, and it costs one line to answer it in advance.
 */
@Composable
private fun AddSourceCard(
    onAddFolder: () -> Unit,
    onAddFiles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.sources_add_title))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = Space.sm),
        ) {
            AddSourceRow(
                icon = painterResource(R.drawable.ic_folder),
                title = stringResource(R.string.action_add_folder),
                body = stringResource(R.string.sources_add_folder_body),
                onClick = onAddFolder,
            )
            AddSourceRow(
                icon = painterResource(R.drawable.ic_file),
                title = stringResource(R.string.library_add_files),
                body = stringResource(R.string.sources_add_files_body),
                onClick = onAddFiles,
            )
        }
    }
}

/** The quiet heading that says what the cards below it are. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.xs, top = Space.sm, bottom = Space.xs),
    )
}

@Composable
private fun AddSourceRow(
    icon: Painter,
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
    ) {
        MenuIcon(icon)
        Column(modifier = Modifier.padding(start = Space.lg)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
