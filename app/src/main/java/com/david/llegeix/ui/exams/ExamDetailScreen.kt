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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.common.resolved
import com.david.llegeix.ui.folders.FolderNameDialog
import com.david.llegeix.util.formatModified

/**
 * One exam paper: what is attached to it, and every go the reader has had at it.
 *
 * The sittings are the point of the screen and sit under everything else, most
 * recent first. What is above them — the recordings, the answer sheet — is the
 * paper's own furniture, set up once and then left alone, so it is deliberately
 * quieter than the list it sits over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamDetailScreen(
    examId: Long,
    onBack: () -> Unit,
    onOpenAttempt: (attemptId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExamDetailViewModel = viewModel(factory = ExamDetailViewModel.factory(examId)),
) {
    val exam by viewModel.exam.collectAsStateWithLifecycle()
    val attempts by viewModel.attempts.collectAsStateWithLifecycle()
    val audio by viewModel.audio.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var renamingExam by remember { mutableStateOf(false) }
    var renamingAttempt by remember { mutableStateOf<AttemptSummary?>(null) }
    var deletingAttempt by remember { mutableStateOf<AttemptSummary?>(null) }

    val keyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onAnswerKeyPicked(uri) }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onAudioPicked(uri) }

    // A sitting just made is a sitting the reader means to start now.
    LaunchedEffect(started) {
        val attemptId = started ?: return@LaunchedEffect
        viewModel.onStartedHandled()
        onOpenAttempt(attemptId)
    }

    val messageText = message?.resolved()
    LaunchedEffect(messageText) {
        val text = messageText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                expandedHeight = Space.topBar,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = {
                    Text(
                        text = exam?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { renamingExam = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_rename),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::onStartAttempt,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.exams_new_attempt)) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            LazyColumn(contentPadding = PaddingValues(bottom = Space.huge)) {
                item {
                    PaperSetup(
                        pageCount = exam?.pageCount ?: 0,
                        hasAnswerKey = exam?.answerKeyFileName != null,
                        audio = audio,
                        onAttachKey = { keyPicker.launch(PDF_TYPES) },
                        onRemoveKey = viewModel::onRemoveAnswerKey,
                        onAddAudio = { audioPicker.launch(AUDIO_TYPES) },
                        onRemoveAudio = viewModel::onRemoveAudio,
                    )
                    HorizontalDivider()
                }

                item {
                    Text(
                        text = stringResource(R.string.exams_attempts_heading),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            start = Space.screen,
                            end = Space.screen,
                            top = Space.lg,
                            bottom = Space.sm,
                        ),
                    )
                }

                if (attempts.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.exams_no_attempts),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = Space.screen,
                                vertical = Space.md,
                            ),
                        )
                    }
                } else {
                    items(attempts, key = { it.attempt.id }) { summary ->
                        AttemptRow(
                            summary = summary,
                            pageCount = exam?.pageCount ?: 0,
                            onOpen = { onOpenAttempt(summary.attempt.id) },
                            onRename = { renamingAttempt = summary },
                            onDelete = { deletingAttempt = summary },
                            onToggleFinished = {
                                viewModel.onToggleFinished(
                                    summary.attempt.id,
                                    !summary.isFinished,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (renamingExam) {
        FolderNameDialog(
            title = stringResource(R.string.exams_rename_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = exam?.title.orEmpty(),
            onDismiss = { renamingExam = false },
            onConfirm = { name ->
                viewModel.onRenameExam(name)
                renamingExam = false
            },
        )
    }

    renamingAttempt?.let { summary ->
        FolderNameDialog(
            title = stringResource(R.string.exams_rename_attempt_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = summary.attempt.label,
            onDismiss = { renamingAttempt = null },
            onConfirm = { name ->
                viewModel.onRenameAttempt(summary.attempt.id, name)
                renamingAttempt = null
            },
        )
    }

    deletingAttempt?.let { summary ->
        AlertDialog(
            onDismissRequest = { deletingAttempt = null },
            title = { Text(stringResource(R.string.exams_delete_attempt_title)) },
            text = { Text(stringResource(R.string.exams_delete_attempt_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeleteAttempt(summary.attempt.id)
                        deletingAttempt = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingAttempt = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/**
 * The paper's own furniture: how long it is, its answer sheet, its recordings.
 *
 * Set up once and then rarely touched, so it is a quiet block above the list
 * rather than a screen of its own behind a menu — the reader should be able to
 * see at a glance whether this paper has its answers, without going looking.
 */
@Composable
private fun PaperSetup(
    pageCount: Int,
    hasAnswerKey: Boolean,
    audio: List<ExamAudioEntity>,
    onAttachKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onAddAudio: () -> Unit,
    onRemoveAudio: (ExamAudioEntity) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.md)) {
        Text(
            text = pluralStringResource(R.plurals.exams_pages, pageCount, pageCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Only the two *adding* actions are chips. A chip that changed from
        // "Add answer sheet" to "Answer sheet added" and then removed the sheet
        // when pressed would be a button whose label describes a state rather
        // than what pressing it does — which is how somebody deletes their
        // answer key by tapping what they read as a tick. What is attached is
        // listed underneath instead, each with its own X, exactly like the
        // recordings.
        Row(
            modifier = Modifier.padding(top = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!hasAnswerKey) {
                AssistChip(
                    onClick = onAttachKey,
                    label = { Text(stringResource(R.string.exams_key_attach)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
            Box(modifier = Modifier.padding(start = if (hasAnswerKey) 0.dp else Space.sm)) {
                AssistChip(
                    onClick = onAddAudio,
                    label = { Text(stringResource(R.string.exams_audio_add)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_speaker),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        if (hasAnswerKey) {
            AttachmentRow(
                label = stringResource(R.string.exams_key_present),
                icon = Icons.Default.Check,
                removeDescription = stringResource(R.string.exams_key_remove),
                onRemove = onRemoveKey,
            )
        }

        audio.forEach { track ->
            AttachmentRow(
                label = track.displayName,
                painter = painterResource(R.drawable.ic_speaker),
                removeDescription = stringResource(
                    R.string.exams_audio_remove,
                    track.displayName,
                ),
                onRemove = { onRemoveAudio(track) },
            )
        }
    }
}

/**
 * One thing attached to the paper, with the way to take it off again.
 *
 * Shared by the answer sheet and the recordings so that removing either is the
 * same gesture in the same place. They are different kinds of file but the same
 * kind of decision.
 */
@Composable
private fun AttachmentRow(
    label: String,
    removeDescription: String,
    onRemove: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            painter != null -> Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.sm),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = removeDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AttemptRow(
    summary: AttemptSummary,
    pageCount: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleFinished: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = Space.screen, top = Space.row, bottom = Space.row),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A finished sitting is marked rather than moved. The reader's own list
        // of past attempts is a history, and reordering it by state would break
        // the one thing it is for, which is seeing progress in order.
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (summary.isFinished) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                ),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md),
        ) {
            Text(
                text = stringResource(R.string.exams_attempt_label, summary.attempt.label),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(
                    formatModified(summary.attempt.startedAt).takeIf { it.isNotEmpty() },
                    stringResource(
                        R.string.exams_attempt_progress,
                        summary.markedPages,
                        pageCount,
                    ),
                    stringResource(R.string.exams_attempt_finished)
                        .takeIf { summary.isFinished },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(
                        R.string.exams_more,
                        summary.attempt.label,
                    ),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (summary.isFinished) {
                                    R.string.exams_mark_unfinished
                                } else {
                                    R.string.exams_mark_finished
                                },
                            ),
                        )
                    },
                    leadingIcon = { MenuIcon(Icons.Default.Check) },
                    onClick = {
                        menuOpen = false
                        onToggleFinished()
                    },
                )
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
 * What the recording picker will offer.
 *
 * Every audio type, plus the two containers providers routinely mislabel. An
 * exam's listening track is very often an m4a or an ogg handed over as
 * `application/octet-stream`, and a file the reader can see but cannot pick
 * reads as the app refusing it.
 *
 * (The wildcard is deliberately not written out above: Kotlin nests block
 * comments, so a slash-star inside this one would swallow the rest of the file.)
 */
private val AUDIO_TYPES = arrayOf("audio/*", "application/ogg", "application/octet-stream")
