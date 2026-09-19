package com.david.llegeix.ui.flashcards

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.key
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.flashcards.ImageSizing
import com.david.llegeix.data.flashcards.NextDue
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
                            DirectionFlags(
                                direction = state.direction,
                                flagWidth = 18.dp,
                                modifier = Modifier.padding(top = 2.dp),
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

                state.isEmpty && state.cardsInScope == 0 -> EmptyState(
                    title = stringResource(R.string.flashcards_study_no_cards_title),
                    body = stringResource(R.string.flashcards_study_no_cards_body),
                    icon = painterResource(R.drawable.ic_flashcards),
                    primaryAction = {
                        OutlinedButton(onClick = onBack) {
                            Text(stringResource(R.string.practice_done))
                        }
                    },
                )

                state.isEmpty -> NothingDue(
                    state = state,
                    onSwitchDirection = viewModel::onSwitchDirection,
                    onBack = onBack,
                )

                state.isFinished -> Finished(
                    state = state,
                    onAgain = { viewModel.deal() },
                    onDone = onBack,
                )

                // Keyed by the card, so each one arrives face up. Without it the
                // next card would inherit the last one's turn and swing back
                // round from its answer side — showing the new answer first.
                else -> state.current?.let { card -> key(card.id) {
                    StudyCard(
                        card = card,
                        direction = state.direction,
                        isRevealed = state.isRevealed,
                        onReveal = viewModel::onReveal,
                        onAnswer = viewModel::onAnswer,
                    )
                } }
            }
        }
    }
}

/**
 * Nothing is due this way round.
 *
 * Said with the two facts that make it useful rather than a dead end: when
 * the next card comes back, so "nothing due" is a time rather than a verdict,
 * and — when there is any — the work waiting the other way round, one press
 * away, because somebody who sat down to practise did not sit down to be told
 * to go away.
 */
@Composable
private fun NothingDue(
    state: StudyUiState,
    onSwitchDirection: () -> Unit,
    onBack: () -> Unit,
) {
    val nextDue = state.nextDueAt?.let { dueAt ->
        val wait = NextDue.waitUntil(dueAt, System.currentTimeMillis())
        pluralStringResource(
            when (wait.unit) {
                NextDue.Unit.MINUTES -> R.plurals.flashcards_next_due_minutes
                NextDue.Unit.HOURS -> R.plurals.flashcards_next_due_hours
                NextDue.Unit.DAYS -> R.plurals.flashcards_next_due_days
            },
            wait.amount,
            wait.amount,
        )
    }
    val canSwitch = state.otherDirectionDue > 0

    EmptyState(
        title = stringResource(R.string.flashcards_study_nothing_due_title),
        body = listOfNotNull(
            stringResource(R.string.flashcards_study_nothing_due_body),
            nextDue,
        ).joinToString(" "),
        icon = painterResource(R.drawable.ic_check_circle),
        primaryAction = if (canSwitch) {
            {
                Button(onClick = onSwitchDirection) {
                    Text(
                        stringResource(
                            R.string.flashcards_study_switch,
                            stringResource(directionLabel(state.otherDirection)),
                            state.otherDirectionDue,
                        ),
                    )
                }
            }
        } else {
            {
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.practice_done))
                }
            }
        },
        secondaryAction = if (canSwitch) {
            {
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.practice_done))
                }
            }
        } else {
            null
        },
    )
}

/** How a direction is named wherever it is chosen or shown. */
internal fun directionLabel(direction: StudyDirection): Int = when (direction) {
    StudyDirection.CATALAN_TO_ROMANIAN -> R.string.flashcards_direction_ca_ro
    StudyDirection.ROMANIAN_TO_CATALAN -> R.string.flashcards_direction_ro_ca
}

/**
 * One card, asked — and turned over when the reader has had a go.
 *
 * A real turn rather than the answer appearing underneath: a flashcard is a
 * thing with two sides, and the moment of turning it over is the moment of
 * finding out, so it should look like one. The back repeats the question
 * small at the top, because an answer read without its question is a fact
 * learned with nothing to hang it on.
 *
 * The whole card is the reveal, as in the practice deck: the gesture is "I
 * have had my go" and should not need aiming. The answers arrive at the
 * bottom, where a thumb is, into room kept for them so nothing jumps.
 */
@Composable
private fun StudyCard(
    card: FlashcardEntity,
    direction: StudyDirection,
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    val turn by animateFloatAsState(
        targetValue = if (isRevealed) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "cardTurn",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Space.screen)
            .padding(bottom = Space.xl),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = Space.xl)
                .graphicsLayer {
                    rotationY = turn
                    cameraDistance = 14f * density
                }
                .shadow(elevation = 3.dp, shape = CardShape)
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(enabled = !isRevealed, onClick = onReveal),
        ) {
            if (turn <= 90f) {
                Face {
                    when (direction) {
                        StudyDirection.CATALAN_TO_ROMANIAN -> CatalanSide(card, asAnswer = false)
                        StudyDirection.ROMANIAN_TO_CATALAN -> MeaningSide(card, asAnswer = false)
                    }
                    Text(
                        text = stringResource(R.string.practice_tap_to_reveal),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Space.xxl),
                    )
                }
            } else {
                // Drawn turned round once more, so the back reads the right way.
                Face(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                    Text(
                        text = when (direction) {
                            StudyDirection.CATALAN_TO_ROMANIAN -> card.catalan
                            StudyDirection.ROMANIAN_TO_CATALAN -> card.romanian
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    HorizontalDivider(
                        modifier = Modifier
                            .padding(vertical = Space.lg)
                            .fillMaxWidth(0.3f),
                    )
                    when (direction) {
                        StudyDirection.CATALAN_TO_ROMANIAN -> MeaningSide(card, asAnswer = true)
                        StudyDirection.ROMANIAN_TO_CATALAN -> CatalanSide(card, asAnswer = true)
                    }
                }
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
                        modifier = Modifier
                            .weight(1f)
                            .height(AnswerButtonHeight),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.practice_again_soon),
                            modifier = Modifier.padding(start = Space.sm),
                        )
                    }
                    Button(
                        onClick = { onAnswer(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(AnswerButtonHeight),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(
                            stringResource(R.string.practice_knew_it),
                            modifier = Modifier.padding(start = Space.sm),
                        )
                    }
                }
            }
        }
    }
}

/** One side of the card: centred, and scrollable for a long answer on a small phone. */
@Composable
private fun Face(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * The end of a session: how it went, and which words to look at again.
 *
 * The score is the headline, as a fraction rather than a percentage — "4 of 5"
 * is what the reader just did, "80%" is arithmetic done on it. The missed words
 * are listed by name, because "you missed one" is the least useful sentence a
 * study app can say and "you missed *cotxe*" is the most.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Finished(state: StudyUiState, onAgain: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xxl, vertical = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.practice_finished_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.practice_position, state.correct, state.cards.size),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Space.sm),
        )
        Text(
            text = stringResource(
                R.string.practice_finished_body,
                state.correct,
                state.cards.size,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.md),
        )

        if (state.missed.isEmpty()) {
            Text(
                text = stringResource(R.string.flashcards_study_perfect),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Space.xl),
            )
        } else {
            Text(
                text = stringResource(R.string.flashcards_study_missed),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Space.xl, bottom = Space.sm),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                state.missed.forEach { card ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = Space.md, vertical = Space.sm),
                    ) {
                        Text(card.catalan, style = MaterialTheme.typography.titleSmall)
                        Text(
                            card.romanian,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Button(
            onClick = onAgain,
            modifier = Modifier.padding(top = Space.xxl),
        ) { Text(stringResource(R.string.practice_again)) }
        OutlinedButton(
            onClick = onDone,
            modifier = Modifier.padding(top = Space.sm),
        ) { Text(stringResource(R.string.practice_done)) }
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
private val AnswerRowHeight = 72.dp

private val AnswerButtonHeight = 56.dp

private val CardShape = RoundedCornerShape(28.dp)

/** Tall enough to read a photograph, short enough to leave the words on screen. */
private val PictureHeight = 200.dp
