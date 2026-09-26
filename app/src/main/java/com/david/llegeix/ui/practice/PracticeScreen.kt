package com.david.llegeix.ui.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.WordBookmarkEntity
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.PronounceButton
import com.david.llegeix.ui.common.Space

/**
 * The saved words, one at a time, with something asked of the reader.
 *
 * Deliberately the plainest possible card: the Catalan, and then — once the
 * reader has actually tried to remember — what it means. Two buttons, no
 * grading scale, no timer. Everything fancier than this is a way of asking the
 * reader to score their own recall on a scale they have no calibration for,
 * which is how study apps end up feeling like admin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
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
                    title = { Text(stringResource(R.string.practice_title)) },
                    actions = {
                        if (state.cards.isNotEmpty() && !state.isFinished) {
                            Text(
                                text = stringResource(
                                    R.string.practice_position,
                                    state.index + 1,
                                    state.cards.size,
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = Space.lg),
                            )
                        }
                    },
                )
                // How much of the session is behind you, which is the one thing
                // that makes a deck feel finite rather than endless.
                if (state.cards.isNotEmpty()) {
                    val progress by animateFloatAsState(
                        targetValue = state.progress,
                        label = "practiceProgress",
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.isEmpty -> EmptyState(
                    title = if (state.savedTotal == 0) {
                        stringResource(R.string.practice_none_saved_title)
                    } else {
                        stringResource(R.string.practice_all_done_title)
                    },
                    body = if (state.savedTotal == 0) {
                        stringResource(R.string.practice_none_saved_body)
                    } else {
                        stringResource(R.string.practice_all_done_body)
                    },
                    icon = painterResource(R.drawable.ic_cards),
                )

                state.isFinished -> EmptyState(
                    title = stringResource(R.string.practice_finished_title),
                    body = stringResource(
                        R.string.practice_finished_body,
                        state.correct,
                        state.cards.size,
                    ),
                    icon = painterResource(R.drawable.ic_check_circle),
                    primaryAction = {
                        Button(onClick = { viewModel.deal() }) {
                            Text(stringResource(R.string.practice_again))
                        }
                    },
                    secondaryAction = {
                        OutlinedButton(onClick = onBack) {
                            Text(stringResource(R.string.practice_done))
                        }
                    },
                )

                else -> state.current?.let { card ->
                    Card(
                        card = card,
                        isRevealed = state.isRevealed,
                        onReveal = viewModel::onReveal,
                        onAnswer = viewModel::onAnswer,
                    )
                }
            }
        }
    }
}

/**
 * One word, asked.
 *
 * The whole card is the reveal, not a button under it: the gesture is "I have
 * had my go", and it should not require aiming. Once it is over, the two
 * answers arrive where the card was not — at the bottom, where a thumb is —
 * so nothing has to be reached across the answer to press.
 */
@Composable
private fun Card(
    card: WordBookmarkEntity,
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.xl),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = Space.xl)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(enabled = !isRevealed, onClick = onReveal)
                .verticalScroll(rememberScrollState())
                .padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = card.word,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            if (!card.ipa.isNullOrBlank()) {
                Text(
                    text = "[${card.ipa}]",
                    fontFamily = com.david.llegeix.ui.theme.IpaFont,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Space.sm),
                )
            }
            // Hearing the word is a second way of being asked about it, and the
            // one that matches how the word will actually turn up. The button
            // takes its own press, so reaching for it never counts as the tap
            // that gives the answer away.
            PronounceButton(text = card.word)

            // Kept back until it has been asked for, and then given the weight
            // of an answer. A card that shows both halves at once is a list.
            AnimatedVisibility(visible = isRevealed, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(Space.xl))
                    Text(
                        text = card.translation.orEmpty(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    // What it meant in the line it was taken from, when that
                    // was something the word alone does not say. This is the
                    // reason the app bothered to keep the sentence.
                    card.senseTranslation?.takeIf { it.isNotBlank() }?.let { sense ->
                        Text(
                            text = stringResource(R.string.practice_here, sense),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = Space.md),
                        )
                    }
                    card.context?.takeIf { it.isNotBlank() && it != card.word }?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = Space.xl),
                        )
                    }
                }
            }

            if (!isRevealed) {
                Text(
                    text = stringResource(R.string.practice_tap_to_reveal),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Space.xxl),
                )
            }
        }

        Box(modifier = Modifier.heightIn(min = AnswerRowHeight)) {
            if (isRevealed) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.md),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Space.lg),
                ) {
                    OutlinedButton(
                        onClick = { onAnswer(false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.practice_again_soon))
                    }
                    Button(
                        onClick = { onAnswer(true) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.practice_knew_it))
                    }
                }
            }
        }
    }
}

/**
 * Room kept for the two answers whether or not they are showing.
 *
 * Without it the card grows the moment it is revealed, so the word the reader
 * is looking at jumps upward at exactly the moment they are reading the answer
 * underneath it.
 */
private val AnswerRowHeight = 68.dp
