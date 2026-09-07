package com.david.llegeix.ui.exams

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.ExamWithProgress
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.AppSnackbarHost
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.folders.FolderNameDialog

/**
 * The exam papers the reader has brought in.
 *
 * A tab of its own rather than a corner of the library, because an exam is used
 * in a different way from a book: it is not read through, it is sat, and the
 * thing the reader wants on arriving is not "where was I" but "which paper, and
 * which go at it". Putting that behind a menu in the library would have made
 * the app's newest idea its most hidden one.
 *
 * Each row is a paper with its sittings counted under it. The paper itself is
 * never opened from here — only a sitting is — because there is no such thing
 * as reading an exam without answering it, and offering both would be offering
 * a choice that has no consequence.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    onOpenExam: (examId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamsViewModel = viewModel(factory = ExamsViewModel.Factory),
) {
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val pendingChoice by viewModel.pendingChoice.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<ExamWithProgress?>(null) }
    var pendingDelete by remember { mutableStateOf<ExamWithProgress?>(null) }

    // OpenMultipleDocuments rather than OpenDocument: a paper commonly arrives
    // as several files, and picking them one at a time meant reopening the
    // picker and finding the folder again for each one. The picker still filters
    // by type and still shows every provider including the cloud ones, and
    // nothing is persisted — the files are copied during the call and the grants
    // are allowed to lapse.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.onPicked(uris) }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        // The app shell's Scaffold has already inset this screen for the
        // status bar and the navigation bar; counting them a second time
        // put a dead band above the bottom bar and made every top bar
        // 24dp taller than it asks to be.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                title = { Text(stringResource(R.string.nav_exams)) },
            )
        },
        floatingActionButton = {
            if (exams.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(PDF_TYPES) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.exams_import)) },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // A determinate bar would be a lie: a copy from a cloud provider
            // reports no total to measure against. This says only that
            // something is happening, which is the honest amount.
            if (isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (exams.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.exams_empty_title),
                    body = stringResource(R.string.exams_empty_body),
                    icon = painterResource(R.drawable.ic_exam),
                    primaryAction = {
                        Button(onClick = { picker.launch(PDF_TYPES) }) {
                            Text(stringResource(R.string.exams_import))
                        }
                    },
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = Space.huge)) {
                    items(exams, key = { it.id }) { exam ->
                        ExamRow(
                            exam = exam,
                            onOpen = { onOpenExam(exam.id) },
                            onRename = { renaming = exam },
                            onDelete = { pendingDelete = exam },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { exam ->
        FolderNameDialog(
            title = stringResource(R.string.exams_rename_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = exam.title,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.onRename(exam.id, name)
                renaming = null
            },
        )
    }

    if (pendingChoice.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = viewModel::onChoiceDismissed,
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.exams_group_title,
                        pendingChoice.size,
                        pendingChoice.size,
                    ),
                )
            },
            text = { Text(stringResource(R.string.exams_group_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onGroupChosen(asOneExam = true) }) {
                    Text(stringResource(R.string.exams_group_one))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onGroupChosen(asOneExam = false) }) {
                    Text(stringResource(R.string.exams_group_separate))
                }
            },
        )
    }

    pendingDelete?.let { exam ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.exams_delete_title)) },
            // Says what is actually lost. Deleting a paper deletes the copy the
            // app made *and* every sitting of it, and a reader who has three
            // attempts behind them deserves to be told that before, not after.
            text = {
                Text(
                    if (exam.attemptCount == 0) {
                        stringResource(R.string.exams_delete_body_none)
                    } else {
                        pluralStringResource(
                            R.plurals.exams_delete_body,
                            exam.attemptCount,
                            exam.attemptCount,
                        )
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDelete(exam.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ExamRow(
    exam: ExamWithProgress,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A glyph rather than the paper's own front page.
        //
        // The covers were rendered from the PDF, which sounded right — it is
        // what the library does for a book — and looked wrong for a whole row
        // of them. An exam's first page is a title block on white: at 46dp it
        // is a grey smudge with a lighter grey smudge on it, so a column of
        // papers from the same course came out as a column of identical grey
        // smudges. The cover was doing none of the telling-apart it costs a
        // document open per row to draw.
        //
        // A page glyph in the muted ink says "this is a paper" at a glance,
        // costs nothing, matches the single-colour marks the rest of the app
        // uses, and leaves the *title* — which does tell them apart — as the
        // only thing on the row competing for attention.
        Box(
            modifier = Modifier
                .size(ExamTileSize)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(end = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_page),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(Space.lg))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exam.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = detailLine(exam),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(R.string.exams_more, exam.title),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename)) },
                    leadingIcon = { MenuIcon(Icons.Default.Edit) },
                    onClick = {
                        menuOpen = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { MenuIcon(Icons.Default.Delete) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
    }
}

/**
 * The tile beside a paper's name.
 *
 * The same 40dp disc-and-glyph the library gives a folder and a collection, so
 * the three read as three kinds of the same object rather than as three
 * unrelated list designs.
 */
private val ExamTileSize = 40.dp

/** "12 pages · 2 attempts · 3 recordings", with the parts that apply. */
@Composable
private fun detailLine(exam: ExamWithProgress): String {
    val parts = buildList {
        add(pluralStringResource(R.plurals.exams_pages, exam.pageCount, exam.pageCount))
        add(
            pluralStringResource(
                R.plurals.exams_attempts,
                exam.attemptCount,
                exam.attemptCount,
            ),
        )
        if (exam.partCount > 1) {
            add(pluralStringResource(R.plurals.exams_files, exam.partCount, exam.partCount))
        }
        if (exam.audioCount > 0) {
            add(
                pluralStringResource(
                    R.plurals.exams_recordings,
                    exam.audioCount,
                    exam.audioCount,
                ),
            )
        }
    }
    return parts.joinToString(" · ")
}

/**
 * What the picker will offer.
 *
 * `application/pdf` alone is not enough: providers that do not know a file's
 * type hand back `application/octet-stream`, and a paper the reader can see in
 * their files but cannot select reads as the app refusing it. Anything picked
 * that turns out not to be a PDF is caught on import, where it can be explained.
 */
internal val PDF_TYPES = arrayOf("application/pdf", "application/octet-stream")
