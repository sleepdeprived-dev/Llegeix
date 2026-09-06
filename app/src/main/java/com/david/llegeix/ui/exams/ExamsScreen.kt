package com.david.llegeix.ui.exams

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.SnackbarHost
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.dao.ExamWithProgress
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.MenuIcon
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
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<ExamWithProgress?>(null) }
    var pendingDelete by remember { mutableStateOf<ExamWithProgress?>(null) }

    // OpenDocument rather than GetContent: the picker filters by type, shows
    // every provider the phone has including the cloud ones, and hands back a
    // URI that can be read straight through. Nothing is persisted from it — the
    // file is copied during the call and the grant is allowed to lapse.
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onPicked(uri) }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
