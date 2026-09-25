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
import androidx.compose.material.icons.filled.Refresh
import com.david.llegeix.data.flashcards.MeaningLanguage
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
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
import com.david.llegeix.data.flashcards.PictureResults
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.data.flashcards.StudyScope
import com.david.llegeix.ui.common.EmptyState
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
    scope: StudyScope,
    direction: StudyDirection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudyViewModel = viewModel(
        key = "study-$scope-$direction",
        factory = StudyViewModel.factory(scope, direction),
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
                                text = state.scopeName
                                    ?: stringResource(
                                        if (state.isWeakReview) R.string.flashcards_weak_title else R.string.flashcards_all_decks,
                                    ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                DirectionFlags(
                                    direction = state.direction,
                                    flagWidth = 18.dp,
                                )
                                // Said, because it behaves differently: nothing
                                // answered here moves a card's schedule.
                                if (state.isExtra) {
                                    Text(
                                        text = stringResource(R.string.flashcards_extra_label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = Space.sm),
                                    )
                                }
                            }
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

                state.isFinished -> Finished(
                    state = state,
                    onRepeat = viewModel::onRepeat,
                    onTurnRound = viewModel::onTurnRound,
                    onDone = onBack,
                )

                // Keyed by the card, so each one arrives face up. Without it the
                // next card would inherit the last one's turn and swing back
                // round from its answer side — showing the new answer first.
                else -> state.current?.let { card -> key(card.id) {
                    StudyCard(
                        card = card,
                        direction = state.direction,
                        deckName = state.deckNames[card.deckId],
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
 * One card, asked — and turned over when the reader has had a go.
 *
 * Every face is laid out the same way, top to bottom, so the eye always knows
 * where to look: where the card is from, then the picture, then the question,
 * a short rule, and the answer. The Catalan always carries its pronunciation
 * and speaker in one pill right under it; the meanings are always the English
 * and the Romanian, each behind its flag. The picture shows the meaning, so it
 * is on the front only when the meaning is the question — anywhere else it
 * would give the answer away.
 *
 * A real turn rather than the answer appearing underneath: the moment of
 * turning the card over is the moment of finding out, so it looks like one.
 * The whole card is the reveal; the answers arrive at the bottom, where a
 * thumb is, into room kept for them so nothing jumps.
 */
@Composable
private fun StudyCard(
    card: FlashcardEntity,
    direction: StudyDirection,
    /** The deck the card is from, when the session mixes decks. */
    deckName: String?,
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onAnswer: (Boolean) -> Unit,
) {
    val turn by animateFloatAsState(
        targetValue = if (isRevealed) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "cardTurn",
    )
    val scheme = MaterialTheme.colorScheme
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
                .padding(top = Space.lg)
                .graphicsLayer {
                    rotationY = turn
                    cameraDistance = 14f * density
                }
                .shadow(elevation = 8.dp, shape = CardShape, spotColor = scheme.primary.copy(alpha = 0.25f))
                .clip(CardShape)
                .background(Brush.verticalGradient(listOf(scheme.surfaceContainerLowest, scheme.surfaceContainer)))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), CardShape)
                .clickable(enabled = !isRevealed, onClick = onReveal),
        ) {
            val front = turn <= 90f
            Face(
                deckName = deckName,
                modifier = if (front) Modifier else Modifier.graphicsLayer { rotationY = 180f },
                hint = if (front) stringResource(R.string.practice_tap_to_reveal) else null,
            ) {
                val showPicture = !front || direction == StudyDirection.MEANING_TO_CATALAN
                if (showPicture) {
                    card.imagePath?.let { path ->
                        FramedPicture(
                            path = path,
                            contentDescription = null,
                            kind = PictureResults.kindOf(card.imageCredit),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (front) PictureHeight else PictureHeightSmall),
                        )
                        Spacer(Modifier.height(Space.xl))
                    }
                }
                when {
                    front && direction == StudyDirection.CATALAN_TO_MEANING ->
                        CatalanBlock(card, big = true)

                    front -> MeaningBlock(card, big = true)

                    direction == StudyDirection.CATALAN_TO_MEANING -> {
                        CatalanBlock(card, big = false)
                        AnswerRule()
                        MeaningBlock(card, big = true, answer = true)
                    }

                    else -> {
                        MeaningBlock(card, big = false)
                        AnswerRule()
                        CatalanBlock(card, big = true, answer = true)
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

/**
 * One side of the card: the deck it is from pinned to the top, the content
 * centred in what is left, and — on the front — the hint pinned to the
 * bottom. Scrolls, for a long answer on a small phone.
 */
@Composable
private fun Face(
    deckName: String?,
    hint: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxSize().padding(Space.xl)) {
        if (deckName != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = Space.md, vertical = 6.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cards),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = deckName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
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
private fun Finished(
    state: StudyUiState,
    onRepeat: () -> Unit,
    onTurnRound: () -> Unit,
    onDone: () -> Unit,
) {
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
            text = if (state.isExtra) {
                stringResource(R.string.flashcards_extra_finished_body)
            } else {
                stringResource(R.string.practice_finished_body, state.correct, state.cards.size)
            },
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
                            listOfNotNull(
                                MeaningLanguage.ENGLISH.meaningOf(card),
                                MeaningLanguage.ROMANIAN.meaningOf(card),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // The first round asks whether to go the other way round now: having
        // recognised the Catalan, producing it is the natural next step, and
        // the other way about. Asked once — the turned-round round ends as
        // any other.
        if (!state.isTurnedRound) {
            TurnRoundOffer(
                direction = state.direction.other(),
                onTurnRound = onTurnRound,
                modifier = Modifier.padding(top = Space.xxl),
            )
        }

        // Going over the same cards again, straight away, is the other thing
        // the end of a session should make easy: repetition is how they stick.
        if (state.isTurnedRound) {
            Button(
                onClick = onRepeat,
                modifier = Modifier.padding(top = Space.xxl),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.flashcards_repeat_these),
                    modifier = Modifier.padding(start = Space.sm),
                )
            }
        } else {
            OutlinedButton(
                onClick = onRepeat,
                modifier = Modifier.padding(top = Space.md),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.flashcards_repeat_these),
                    modifier = Modifier.padding(start = Space.sm),
                )
            }
        }
        TextButton(
            onClick = onDone,
            modifier = Modifier.padding(top = Space.sm),
        ) { Text(stringResource(R.string.practice_done)) }
    }
}

/**
 * "Now the other way round?" — a card of its own at the end of a round, with
 * the new direction drawn as flags so what is being offered is seen, not read.
 */
@Composable
private fun TurnRoundOffer(
    direction: StudyDirection,
    onTurnRound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(scheme.secondaryContainer)
            .padding(Space.lg),
    ) {
        Text(
            text = stringResource(R.string.flashcards_turn_round_title),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
        CompositionLocalProvider(LocalContentColor provides scheme.onSecondaryContainer) {
            DirectionFlags(
                direction = direction,
                flagWidth = 28.dp,
                modifier = Modifier.padding(top = Space.md),
            )
        }
        Text(
            text = directionName(direction),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.sm),
        )
        Button(
            onClick = onTurnRound,
            modifier = Modifier.padding(top = Space.md),
        ) {
            Icon(painterResource(R.drawable.ic_swap), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.flashcards_turn_round_action),
                modifier = Modifier.padding(start = Space.sm),
            )
        }
    }
}

/**
 * The Catalan: the word, then one pill with its pronunciation and speaker.
 *
 * @param big the question on the front, or the answer on the back; small is
 *   the question again above the answer, so the answer is read with it.
 * @param answer drawn in the accent, as the thing just found out.
 */
@Composable
private fun CatalanBlock(card: FlashcardEntity, big: Boolean, answer: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = card.catalan,
        style = if (big) {
            MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold)
        } else {
            MaterialTheme.typography.titleLarge
        },
        color = when {
            answer -> scheme.primary
            big -> scheme.onSurface
            else -> scheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = if (big) Space.md else Space.sm)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .padding(start = if (card.ipa.isNullOrBlank()) Space.xs else Space.lg, end = Space.xs),
    ) {
        card.ipa?.takeIf { it.isNotBlank() }?.let { ipa ->
            Text(
                text = "[$ipa]",
                style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            if (card.ipaApproximate) {
                Text(
                    text = stringResource(R.string.lookup_ipa_approximate),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Space.xs),
                )
            }
        }
        // Takes its own press, so reaching for it never counts as the tap
        // that turns the card over.
        PronounceButton(text = card.catalan)
    }
}

/**
 * Both of a card's meanings, each behind its own flag: the English, when the
 * card has one, then the Romanian. Always both, so one session teaches a word
 * in the two languages at once.
 */
@Composable
private fun MeaningBlock(card: FlashcardEntity, big: Boolean, answer: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (big) Space.sm else Space.xs),
    ) {
        for (language in listOf(MeaningLanguage.ENGLISH, MeaningLanguage.ROMANIAN)) {
            val meaning = language.meaningOf(card) ?: continue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Flag(language.flagRes, if (big) 26.dp else 18.dp)
                Text(
                    text = meaning,
                    style = if (big) {
                        MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Medium)
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = when {
                        answer -> scheme.primary
                        big -> scheme.onSurface
                        else -> scheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = Space.md),
                )
            }
        }
    }
}

/** A short rule between the question and the answer on the back of a card. */
@Composable
private fun AnswerRule() {
    Box(
        modifier = Modifier
            .padding(vertical = Space.lg)
            .size(width = 48.dp, height = 3.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    )
}

/** Room kept for the two answers whether or not they are showing, so nothing jumps. */
private val AnswerRowHeight = 72.dp

private val AnswerButtonHeight = 56.dp

private val CardShape = RoundedCornerShape(28.dp)

/** Tall enough to read a photograph, short enough to leave the words on screen. */
private val PictureHeight = 220.dp

/** On the back, where the words underneath matter more. */
private val PictureHeightSmall = 170.dp
