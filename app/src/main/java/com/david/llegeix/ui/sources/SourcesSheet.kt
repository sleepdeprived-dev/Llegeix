package com.david.llegeix.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextButton
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.source.GrantedFolder
import com.david.llegeix.data.source.SourceFolder
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.AppBottomSheet
import com.david.llegeix.ui.common.Space
import com.david.llegeix.util.allFilesAccessIntents
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Where the library's documents come from, and which parts of it count.
 *
 * A sheet over the library rather than a screen of its own behind a menu. It
 * used to be an item in the overflow, which is the worst place for it: the
 * question "why is that book not here" is asked *of the library*, while looking
 * at the library, and the answer lived two taps away behind a word — Fonts —
 * that only means anything once you already know what it does. The strip of
 * source tiles at the top of the library is the way in now, and this is what
 * opens when one is pressed.
 *
 * A header says how much of the phone reaches the library; below it, the
 * folders the reader added, then the whole-phone sweep and what it found. Each
 * source is one card with a switch for all of it, that opens to show the
 * folders inside as a tree — indented, with guide lines, a tick at the end of
 * each. Unticking a folder hides it and everything below it; ticking one
 * inside an unticked folder brings just that one back. Nothing is deleted from
 * the phone by anything in here, which the wording is careful to keep saying.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The source to open expanded, if the reader arrived by pressing one.
     *
     * Pressing a particular tile and being shown a list of every source folded
     * shut is the sheet answering a different question from the one asked.
     */
    focusSource: String? = null,
    viewModel: SourcesViewModel = viewModel(factory = SourcesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    var removing by remember { mutableStateOf<GrantedFolder?>(null) }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(focusSource) {
        if (focusSource != null) viewModel.onExpand(focusSource)
    }

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

    AppBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        // A lazy list rather than a scrolling Column: a sheet's height is its
        // content's, so a Column grew the sheet and moved its anchors while a
        // scroll was in flight when a source was opened. The lazy list is the
        // scrolling container the sheet's own drag handling is written
        // against, and it recycles rows, which a phone with forty folders of
        // PDFs needs.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = Space.screen,
                end = Space.screen,
                bottom = Space.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "header") {
                val shown = state.groups.sumOf { it.visibleCount } +
                    if (state.deviceScanEnabled) state.deviceGroups.sumOf { it.visibleCount } else 0
                SheetHeader(
                    shown = shown,
                    isScanning = state.isScanning,
                )
            }

            if (state.groups.isNotEmpty()) {
                item(key = "granted-label") { SectionLabel(stringResource(Res.string.sources_section_granted)) }
            }

            // Keyed by position rather than by name: two granted folders can
            // genuinely be called the same thing — a phone has more than one
            // "Documents" — and a lazy list with two identical keys in it
            // throws.
            itemsIndexed(state.groups, key = { index, _ -> "granted-$index" }) { _, group ->
                SourceCard(
                    group = group,
                    expanded = state.expanded,
                    onToggleExpanded = viewModel::onToggleExpanded,
                    onSetVisible = viewModel::onSetVisible,
                    onSetSourceVisible = viewModel::onSetSourceVisible,
                    onRemove = { removing = group.folder },
                )
            }

            // The sweep and what it found, as one section of their own: the
            // same cards as the granted folders, but a different kind of
            // place, so a heading says whose they are.
            item(key = "device-label") { SectionLabel(stringResource(Res.string.sources_section_device)) }

            item(key = "device-scan") {
                DeviceScanCard(
                    permitted = state.deviceScanPermitted,
                    enabled = state.deviceScanEnabled,
                    count = state.deviceScanCount,
                    onOpenSettings = ::openDeviceScanSettings,
                    onSetEnabled = viewModel::onSetDeviceScan,
                )
            }

            if (state.deviceScanEnabled) {
                itemsIndexed(
                    state.deviceGroups,
                    key = { index, _ -> "found-$index" },
                ) { _, group ->
                    SourceCard(
                        group = group,
                        expanded = state.expanded,
                        onToggleExpanded = viewModel::onToggleExpanded,
                        onSetVisible = viewModel::onSetVisible,
                        onSetSourceVisible = viewModel::onSetSourceVisible,
                        // Nothing to hand back: the sweep is a permission, not
                        // a folder the app was given.
                        onRemove = null,
                    )
                }
            }

            // Where new sources come from, said rather than offered: the + in
            // the library adds them, and this sheet is only about which of
            // the places already given actually count.
            item(key = "add-hint") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = Space.lg, start = Space.xs, end = Space.xs),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(Res.string.sources_add_elsewhere),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
            }
        }
    }

    removing?.let { folder ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(Res.string.sources_remove_title, folder.label)) },
            // Two ways out, and the difference between them spelled out,
            // because they are not degrees of the same thing. Hiding is
            // reversible from this very list; forgetting means finding the
            // folder in the system picker again.
            text = { Text(stringResource(Res.string.sources_remove_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onSetSourceVisible(folder.label, false)
                        removing = null
                    },
                ) { Text(stringResource(Res.string.sources_hide_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.onForgetSource(folder)
                        removing = null
                    },
                ) { Text(stringResource(Res.string.sources_forget_confirm)) }
            },
        )
    }
}

/**
 * The top of the sheet: its name, how much of the phone reaches the library,
 * the one line on what unticking does, and a thin bar while a scan runs.
 */
@Composable
private fun SheetHeader(shown: Int, isScanning: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(bottom = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.primaryContainer),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.lg),
            ) {
                Text(
                    text = stringResource(Res.string.library_sources),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = pluralStringResource(Res.plurals.sources_shown_total, shown, shown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(Res.string.sources_explainer),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Space.md),
        )
        if (isScanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.md)
                    .clip(CircleShape),
            )
        }
    }
}

/**
 * One source: what it is, how much of it counts, and — opened — the folders
 * inside it as a tree.
 *
 * The header is a switch for the whole source, all or nothing, because that
 * is the fastest way to say "not this one at all"; the tree below is where the
 * finer answer lives, and the summary under the name says when only part of
 * it is shown. Pressing the header opens and closes it. Folded shut unless
 * someone opens it: a source is one line about a place, and only becomes
 * forty rows of folders when that is the question being asked.
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
    val scheme = MaterialTheme.colorScheme
    val root = group.folder.label
    val subfolders = (group.tree.size - 1).coerceAtLeast(0)
    val canOpen = subfolders > 0 || onRemove != null
    val isOpen = canOpen && root in expanded
    // On while any of it reaches the library. "Most of it, but not all" is
    // said in words under the name rather than by a third state of the switch.
    val isOn = if (group.totalCount == 0) group.rootVisible else group.visibleCount > 0
    val arrow by animateFloatAsState(if (isOpen) 90f else 0f, label = "source arrow")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainer),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (canOpen) Modifier.clickable { onToggleExpanded(root) } else Modifier)
                .padding(start = Space.md, end = Space.lg, top = Space.md, bottom = Space.md),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isOn) scheme.secondaryContainer else scheme.surfaceContainerHighest),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = null,
                    tint = if (isOn) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.md, end = Space.sm),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = root,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isOn) scheme.onSurface else scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (canOpen) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(
                                if (isOpen) Res.string.sources_hide_folders else Res.string.sources_show_folders,
                                root,
                            ),
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .size(20.dp)
                                .rotate(arrow),
                        )
                    }
                }
                Text(
                    text = sourceSummary(group, subfolders),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val includeLabel = stringResource(Res.string.sources_include, root)
            Switch(
                checked = isOn,
                onCheckedChange = { onSetSourceVisible(root, it) },
                // Named, because on its own a switch says "on or off" without
                // ever saying what of.
                modifier = Modifier.semantics { contentDescription = includeLabel },
            )
        }

        if (isOpen) {
            // Only the folders below the root, and only those the reader has
            // opened their way down to.
            val shown = group.tree.filter { it.depth > 0 && isReachable(it.path, root, expanded) }
            if (shown.isNotEmpty()) {
                HorizontalDivider(
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = Space.lg),
                )
                Column(modifier = Modifier.padding(vertical = Space.xs)) {
                    shown.forEach { folder ->
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
            if (onRemove != null) {
                HorizontalDivider(
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = Space.lg),
                )
                TextButton(
                    onClick = onRemove,
                    modifier = Modifier.padding(horizontal = Space.sm, vertical = Space.xs),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(Res.string.sources_remove_confirm),
                        modifier = Modifier.padding(start = Space.sm),
                    )
                }
            }
        }
    }
}

/** "48 PDF · 12 carpetes", or what is left of that once things are hidden. */
@Composable
private fun sourceSummary(group: SourceGroup, subfolders: Int): String {
    val documents = when {
        group.visibleCount == 0 && group.totalCount > 0 ->
            stringResource(Res.string.sources_none_shown)

        group.visibleCount < group.totalCount -> stringResource(
            Res.string.sources_count_partial,
            group.visibleCount,
            group.totalCount,
        )

        else -> pluralStringResource(
            Res.plurals.folders_pdf_count,
            group.totalCount,
            group.totalCount,
        )
    }
    if (subfolders == 0) return documents
    val folders = pluralStringResource(Res.plurals.sources_folder_count, subfolders, subfolders)
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

/** How far each level of the tree steps in. */
private val TreeIndent = 20.dp

/** The width of the arrow's slot, whose centre each guide line drops from. */
private val ArrowSlot = 32.dp

/** Where the tree starts inside the card. */
private val TreeStart = Space.sm

/**
 * One folder inside a source, as a line of a tree.
 *
 * Indented by depth, with a faint guide line dropping from each folder above
 * it, so which folder is inside which is seen rather than worked out from the
 * indentation alone. An arrow opens it when it has folders of its own; a
 * folder mark, its name and how many PDFs it holds follow; and the tick sits
 * at the end, where every tick in the tree lines up in one column.
 *
 * The whole row is the tick: the question it asks is "does this folder
 * count", and answering it should not need a small target. Opening a folder is
 * the separate, smaller gesture, so it keeps the arrow to itself. A hidden
 * folder is drawn faded, so what is left out shows at a glance.
 */
@Composable
private fun FolderRow(
    folder: SourceFolder,
    hasChildren: Boolean,
    isOpen: Boolean,
    onToggleExpanded: () -> Unit,
    onSetVisible: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val guide = scheme.outlineVariant
    val levels = folder.depth - 1
    val arrow by animateFloatAsState(if (isOpen) 90f else 0f, label = "folder arrow")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = folder.visible,
                role = Role.Checkbox,
                onValueChange = onSetVisible,
            )
            .drawBehind {
                val stroke = 1.dp.toPx()
                for (level in 0 until levels) {
                    val x = (TreeStart + TreeIndent * level + ArrowSlot / 2).toPx()
                    drawLine(guide, Offset(x, 0f), Offset(x, size.height), stroke)
                }
            }
            .heightIn(min = 48.dp)
            .padding(start = TreeStart + TreeIndent * levels, end = Space.sm),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ArrowSlot)) {
            if (hasChildren) {
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(ArrowSlot)) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(
                            if (isOpen) Res.string.sources_hide_folders else Res.string.sources_show_folders,
                            folder.name,
                        ),
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(arrow),
                    )
                }
            }
        }
        val fade = if (folder.visible) 1f else 0.5f
        Icon(
            painter = painterResource(R.drawable.ic_folder),
            contentDescription = null,
            tint = if (folder.visible) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .alpha(fade),
        )
        Text(
            text = folder.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (folder.decidedHere) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = scheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md)
                .alpha(fade),
        )
        Text(
            text = "${folder.total}",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = Space.sm)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = Space.sm, vertical = 2.dp)
                .alpha(fade),
        )
        Checkbox(checked = folder.visible, onCheckedChange = null, modifier = Modifier.padding(start = Space.xs))
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
    val scheme = MaterialTheme.colorScheme
    val isOn = permitted && enabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.surfaceContainer)
            .then(if (permitted) Modifier else Modifier.clickable(onClick = onOpenSettings))
            .padding(start = Space.md, end = Space.lg, top = Space.md, bottom = Space.md),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isOn) scheme.secondaryContainer else scheme.surfaceContainerHighest),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_device),
                contentDescription = null,
                tint = if (isOn) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md, end = Space.sm),
        ) {
            Text(
                text = stringResource(Res.string.action_scan_device),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = when {
                    !permitted -> stringResource(Res.string.library_device_scan_off)
                    enabled -> pluralStringResource(Res.plurals.folders_pdf_count, count, count)
                    else -> stringResource(Res.string.sources_scan_paused)
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (permitted) {
            Switch(checked = enabled, onCheckedChange = onSetEnabled)
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
            )
        }
    }
}

/** The quiet heading that says what the cards below it are. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Space.xs, top = Space.md, bottom = Space.xs),
    )
}
