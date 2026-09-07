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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.ExamAudioEntity
import com.david.llegeix.data.db.entity.ExamPartEntity
import com.david.llegeix.ui.common.MenuIcon
import com.david.llegeix.ui.common.AppSnackbarHost
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
    val parts by viewModel.parts.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var renamingExam by remember { mutableStateOf(false) }
    var renamingAttempt by remember { mutableStateOf<AttemptSummary?>(null) }
    var deletingAttempt by remember { mutableStateOf<AttemptSummary?>(null) }
    var removingPart by remember { mutableStateOf<ExamPartEntity?>(null) }
    var renamingPart by remember { mutableStateOf<ExamPartEntity?>(null) }
    var renamingAudio by remember { mutableStateOf<ExamAudioEntity?>(null) }

    val keyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onAnswerKeyPicked(uri) }
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> uris.forEach(viewModel::onAudioPicked) }
    val partPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.onPartsPicked(uris) }

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
        snackbarHost = { AppSnackbarHost(snackbarHostState) },
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
                // The paper itself: the documents it is made of, in order.
                item { SectionHeading(stringResource(R.string.exams_documents_heading)) }
                itemsIndexed(parts, key = { _, part -> part.id }) { index, part ->
                    DocumentRow(
                        part = part,
                        index = index,
                        count = parts.size,
                        onRemove = { removingPart = part },
                        onRename = { renamingPart = part },
                        onMove = { delta -> viewModel.onMovePart(part.id, delta) },
                    )
                }
                item {
                    TextButton(
                        onClick = { partPicker.launch(PDF_TYPES) },
                        modifier = Modifier.padding(
                            start = Space.md,
                            top = Space.xs,
                            bottom = Space.sm,
                        ),
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.exams_add_file),
                            modifier = Modifier.padding(start = Space.sm),
                        )
                    }
                    HorizontalDivider()
                }

                // What comes with the paper, if anything does.
                item { SectionHeading(stringResource(R.string.exams_materials_heading)) }
                item {
                    val hasKey = exam?.answerKeyFileName != null
                    MaterialRow(
                        painter = painterResource(R.drawable.ic_key),
                        title = stringResource(
                            if (hasKey) R.string.exams_key_present else R.string.exams_key_none,
                        ),
                        summary = stringResource(R.string.exams_key_summary),
                        actionLabel = stringResource(
                            if (hasKey) R.string.action_replace else R.string.action_add,
                        ),
                        onAction = { keyPicker.launch(PDF_TYPES) },
                        content = if (!hasKey) {
                            null
                        } else {
                            {
                                AttachmentRow(
                                    label = stringResource(R.string.exams_key_present),
                                    icon = Icons.Default.Check,
                                    removeDescription = stringResource(R.string.exams_key_remove),
                                    onRemove = viewModel::onRemoveAnswerKey,
                                )
                            }
                        },
                    )
                }
                item {
                    MaterialRow(
                        painter = painterResource(R.drawable.ic_speaker),
                        title = if (audio.isEmpty()) {
                            stringResource(R.string.exams_audio_none)
                        } else {
                            pluralStringResource(
                                R.plurals.exams_recordings,
                                audio.size,
                                audio.size,
                            )
                        },
                        summary = stringResource(R.string.exams_audio_summary),
                        actionLabel = stringResource(R.string.action_add),
                        onAction = { audioPicker.launch(AUDIO_TYPES) },
                        content = if (audio.isEmpty()) {
                            null
                        } else {
                            {
                                audio.forEachIndexed { position, track ->
                                    AudioRow(
                                        track = track,
                                        position = position,
                                        onRename = { renamingAudio = track },
                                        onRemove = { viewModel.onRemoveAudio(track) },
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = Space.md))
                }

                item { SectionHeading(stringResource(R.string.exams_attempts_heading)) }

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
                            pageCount = parts.sumOf { it.pageCount },
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

    renamingPart?.let { part ->
        FolderNameDialog(
            title = stringResource(R.string.exams_rename_file_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = part.sourceName,
            onDismiss = { renamingPart = null },
            onConfirm = { name ->
                viewModel.onRenamePart(part.id, name)
                renamingPart = null
            },
        )
    }

    renamingAudio?.let { track ->
        FolderNameDialog(
            title = stringResource(R.string.exams_rename_audio_title),
            confirmLabel = stringResource(R.string.action_rename),
            initialName = track.displayName,
            onDismiss = { renamingAudio = null },
            onConfirm = { name ->
                viewModel.onRenameAudio(track.id, name)
                renamingAudio = null
            },
        )
    }

    removingPart?.let { part ->
        AlertDialog(
            onDismissRequest = { removingPart = null },
            title = { Text(stringResource(R.string.exams_remove_file_title)) },
            text = { Text(stringResource(R.string.exams_remove_file_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onRemovePart(part.id)
                        removingPart = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { removingPart = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
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

/** A heading over one section of the paper's screen. */
@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(
            start = Space.screen,
            end = Space.screen,
            top = Space.lg,
            bottom = Space.sm,
        ),
    )
}

/**
 * The documents this paper is made of.
 *
 * Shown even when there is only one, because that is the row that tells a
 * reader the paper *can* have more than one — and adding the second file is the
 * thing that was impossible before and is the point of this release. Removing
 * is offered only when there is something left to remove: a paper with no
 * documents is not a paper.
 */
@Composable
private fun DocumentRow(
    part: ExamPartEntity,
    index: Int,
    count: Int,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    onMove: (delta: Int) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Numbered, because the order is what decides where the pages run, and
        // a list of filenames alone does not say it has one.
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .wrapContentHeight(),
            textAlign = TextAlign.Center,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md),
        ) {
            Text(
                text = part.sourceName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.exams_pages, part.pageCount, part.pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Only where there is an order to change. The arrows decide how the
        // pages run, and reordering is safe for the reader's work: a mark
        // records the document it was made on, so moving a document changes
        // which page number a page answers to and nothing else.
        if (count > 1) {
            ReorderControl(
                canMoveUp = index > 0,
                canMoveDown = index < count - 1,
                upLabel = stringResource(R.string.exams_move_up, part.sourceName),
                downLabel = stringResource(R.string.exams_move_down, part.sourceName),
                onMove = onMove,
            )
        }
        // The menu is here even on a one-document paper, because renaming is
        // worth having on every one of them: an exam sample arrives called
        // something like "PROVA_C1_2019_comprensio.pdf", and that string is
        // what the workspace prints under every page of it.
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(
                        R.string.exams_file_actions,
                        part.sourceName,
                    ),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Edit) },
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = { menuOpen = false; onRename() },
                )
                // A paper with no documents is not a paper, so the last one
                // cannot be taken out.
                if (count > 1) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        leadingIcon = { MenuIcon(Icons.Default.Delete) },
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}

/**
 * One recording attached to the paper.
 *
 * This was a bare line of small text with a cross at the end of it — the same
 * row the answer sheet used, which is a single yes-or-no thing, borrowed for a
 * list that can run to a dozen tracks. A listening paper's recordings are
 * objects the reader chose and will pick between while sitting the exam, so
 * they get a row that says what they are: a numbered disc, the name at full
 * size, how long the track runs, and a menu.
 *
 * The length is the thing worth adding. "Which of these four is the long one"
 * is the question actually asked of this list, and it was previously only
 * answerable by playing them.
 */
@Composable
private fun AudioRow(
    track: ExamAudioEntity,
    position: Int,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${position + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.md),
        ) {
            Text(
                text = track.displayName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.durationMs?.let { millis ->
                    stringResource(R.string.exams_audio_length, trackLength(millis))
                } ?: stringResource(R.string.exams_audio_length_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painter = painterResource(R.drawable.ic_more),
                    contentDescription = stringResource(
                        R.string.exams_file_actions,
                        track.displayName,
                    ),
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Edit) },
                    text = { Text(stringResource(R.string.action_rename)) },
                    onClick = { menuOpen = false; onRename() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    leadingIcon = { MenuIcon(Icons.Default.Delete) },
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = { menuOpen = false; onRemove() },
                )
            }
        }
    }
}

/** "3:07", the length a listening track is measured in. */
private fun trackLength(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * The pair of buttons that moves a document up or down the paper.
 *
 * They were two loose icon buttons carrying the path bar's *arrow out of a
 * tray* glyph — one of them rotated 180° in code — sitting next to a third
 * button that removed the document. Three bare targets in a row, two of them
 * the same picture pointing opposite ways, on a row that already has a number,
 * a name and a page count on it.
 *
 * Now they are one object: a rounded pill in the row's own surface, holding two
 * plain chevrons with a hairline between them. That is the shape this control
 * has everywhere — it reads as "the up/down thing" before either arrow is
 * looked at — and, because the pill has an edge, the disabled state at the top
 * and bottom of the list is a dimmed chevron inside something rather than a
 * ghost floating in the row.
 *
 * A chevron rather than the tray arrow because a tray arrow means *out of
 * here*, which is what the path bar uses it for, and these mean *before this
 * one* and *after this one*.
 */
@Composable
private fun ReorderControl(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    upLabel: String,
    downLabel: String,
    onMove: (delta: Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        ReorderStep(
            icon = Icons.Default.KeyboardArrowUp,
            label = upLabel,
            enabled = canMoveUp,
            onClick = { onMove(-1) },
        )
        Box(
            modifier = Modifier
                .size(width = 1.dp, height = 18.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        ReorderStep(
            icon = Icons.Default.KeyboardArrowDown,
            label = downLabel,
            enabled = canMoveDown,
            onClick = { onMove(1) },
        )
    }
}

@Composable
private fun ReorderStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        // Narrower than the 48dp default so the pair reads as one control
        // rather than as two buttons that happen to touch. The row is 56dp
        // tall, so the height is still a comfortable target.
        modifier = Modifier.size(width = ReorderStepWidth, height = ReorderStepWidth),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            },
            modifier = Modifier.size(20.dp),
        )
    }
}

private val ReorderStepWidth = 40.dp

/**
 * One thing that can be attached to a paper: the answer sheet, or the audio.
 *
 * A proper two-line row rather than the loose chips this replaced. A chip says
 * only what pressing it does, so a chip row could not say whether a paper
 * *had* an answer sheet without a second chip contradicting the first — and the
 * one thing a reader wants from this screen at a glance is exactly that. The
 * row states what is there, what it is for, and offers the one action that
 * applies.
 */
@Composable
private fun MaterialRow(
    painter: Painter,
    title: String,
    summary: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.screen, vertical = Space.xs)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Space.md),
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
        content?.invoke()
    }
}

/** One attached file, listed under its material row with a way off. */
@Composable
private fun AttachmentRow(
    label: String,
    removeDescription: String,
    onRemove: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    painter: Painter? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            painter != null -> Icon(
                painter = painter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )

            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
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
                modifier = Modifier.size(16.dp),
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
