package com.david.llegeix.ui.flashcards

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.flashcards.ImageSizing
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.ui.common.EmptyState
import com.david.llegeix.ui.common.IpaLine
import com.david.llegeix.ui.common.PronounceButton
import com.david.llegeix.ui.common.Space

/**
 * The cards of a deck, one at a time, asked one way round.
 *
 * The same plain card as the saved-words practice, for the same reasons: one
 * side, then — once the reader has had a go — the other, and two answers with
 * no grading scale. What is on each side depends on the direction, and so does
 * where the speaker and the picture go: the speaker is the Catalan and the
 * picture is the meaning, so each travels with its own side and is never on
 * the question side when it would give the answer away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    deckId: Long?,
    direction: StudyDirection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudyViewModel = viewModel(
        key = "study-$deckId-$direction",
        factory = StudyViewModel.factory(deckId, direction),
    ),
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
                    title = {
                        Column {
                            Text(
                                text = state.deckName
                                    ?: stringResource(R.string.flashcards_all_decks),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(directionLabel(state.direction)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    },
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
                if (state.cards.isNotEmpty()) {
                    val progress by animateFloatAsState(
                        targetValue = state.progress,
                        label = "studyProgress",
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
                    title = stringResource(
                        if (state.cardsInScope == 0) {
                            R.string.flashcards_study_no_cards_title
                        } else {
                            R.string.flashcards_study_nothing_due_title
                        },
                    ),
                    body = stringResource(
                        if (state.cardsInScope == 0) {
                            R.string.flashcards_study_no_cards_body
                        } else {
                            R.string.flashcards_study_nothing_due_body
                        },
                    ),
                    icon = painterResource(R.drawable.ic_flashcards),
                    primaryAction = {
                        OutlinedButton(onClick = onBack) {
                            Text(stringResource(R.string.practice_done))
                        }
                    },
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
                    StudyCard(
                        card = card,
                        direction = state.direction,
                        isRevealed = state.isRevealed,
                        onReveal = viewModel::onReveal,
                        onAnswer = viewModel::onAnswer,
                    )
                }
            }
        }
    }
}

/** How a direction is named wherever it is chosen or shown. */
internal fun directionLabel(direction: StudyDirection): Int = when (direction) {
    StudyDirection.CATALAN_TO_ROMANIAN -> R.string.flashcards_direction_ca_ro
    StudyDirection.ROMANIAN_TO_CATALAN -> R.string.flashcards_direction_ro_ca
}

/**
 * One card, asked.
 *
 * The whole card is the reveal, as in the practice deck: the gesture is "I have
 * had my go" and should not need aiming. The answers arrive at the bottom where
 * a thumb is, into room that was kept for them, so nothing jumps.
 */
@Composable
private fun StudyCard(
    card: FlashcardEntity,
    direction: StudyDirection,
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
            when (direction) {
                StudyDirection.CATALAN_TO_ROMANIAN -> {
                    CatalanSide(card, asAnswer = false)
                    Answer(isRevealed) {
                        MeaningSide(card, asAnswer = true)
                    }
                }

                StudyDirection.ROMANIAN_TO_CATALAN -> {
                    MeaningSide(card, asAnswer = false)
                    Answer(isRevealed) {
                        CatalanSide(card, asAnswer = true)
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

/** The second half of the card: kept back until asked for, then given room. */
@Composable
private fun Answer(isRevealed: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = isRevealed, enter = fadeIn(), exit = fadeOut()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(Space.xl))
            content()
        }
    }
}

/**
 * The Catalan, with how it sounds.
 *
 * The pronunciation and the speaker live on this side whichever way round the
 * card is asked, because both of them *are* the Catalan: shown on the question
 * side of a Romanian → Catalan card they would be the answer, given away.
 */
@Composable
private fun CatalanSide(card: FlashcardEntity, asAnswer: Boolean) {
    Text(
        text = card.catalan,
        style = if (asAnswer) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.headlineLarge
        },
        color = if (asAnswer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
    card.ipa?.takeIf { it.isNotBlank() }?.let { ipa ->
        IpaLine(
            ipa = ipa,
            isApproximate = card.ipaApproximate,
            modifier = Modifier.padding(top = Space.sm),
        )
    }
    // Takes its own press, so reaching for it never counts as the tap that
    // turns the card over.
    PronounceButton(text = card.catalan)
}

/**
 * The Romanian, with the picture.
 *
 * The picture shows the meaning, so it goes with the meaning: a clue on the
 * question side of Romanian → Catalan, and part of the answer the other way.
 */
@Composable
private fun MeaningSide(card: FlashcardEntity, asAnswer: Boolean) {
    card.imagePath?.let { path ->
        CardImage(
            path = path,
            maxEdge = ImageSizing.MAX_EDGE,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(PictureHeight)
                .clip(RoundedCornerShape(16.dp)),
        )
        Spacer(modifier = Modifier.height(Space.lg))
    }
    Text(
        text = card.romanian,
        style = if (asAnswer) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.headlineLarge
        },
        color = if (asAnswer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
    )
}

/** Room kept for the two answers whether or not they are showing, so nothing jumps. */
private val AnswerRowHeight = 68.dp

/** Tall enough to read a photograph, short enough to leave the words on screen. */
private val PictureHeight = 200.dp
