package com.david.llegeix.ui.flashcards

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.david.llegeix.data.flashcards.StudyScope
import com.david.llegeix.ui.common.AppBottomSheet
import kotlinx.coroutines.launch
import com.david.llegeix.data.flashcards.MeaningLanguage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.ui.common.Space

/**
 * A direction of study as two flags and an arrow: the side you are shown, then
 * the side you answer with.
 *
 * Flags rather than "Català → Romanès". Two language names and an arrow is a
 * sentence to read, twice, to tell two buttons apart; two flags are told apart
 * at a glance, which is the same reason the reader's lookup panel picks its
 * translation language with flags. The words are kept for anyone who cannot
 * see the flags: the whole mark is announced as the direction's full name.
 */
@Composable
fun DirectionFlags(
    direction: StudyDirection,
    modifier: Modifier = Modifier,
    language: MeaningLanguage = MeaningLanguage.Default,
    flagWidth: Dp = 24.dp,
) {
    val meaning = language.flagRes
    val (from, to) = when (direction) {
        StudyDirection.CATALAN_TO_MEANING -> R.drawable.ic_flag_ca to meaning
        StudyDirection.MEANING_TO_CATALAN -> meaning to R.drawable.ic_flag_ca
    }
    val name = directionName(direction, language)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = name },
    ) {
        Flag(from, flagWidth)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(flagWidth * 0.66f),
        )
        Flag(to, flagWidth)
    }
}

@Composable
private fun Flag(@DrawableRes res: Int, width: Dp) {
    val shape = RoundedCornerShape(width / 8)
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = Modifier
            .size(width = width, height = width * 2 / 3)
            .clip(shape)
            // A hairline so the gold of the Senyera does not melt into a light
            // surface, nor the blue of Romania's into a dark one.
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    )
}

/** A language's flag: Romania's, or the United Kingdom's for English. */
val MeaningLanguage.flagRes: Int
    @DrawableRes get() = when (this) {
        MeaningLanguage.ROMANIAN -> R.drawable.ic_flag_ro
        MeaningLanguage.ENGLISH -> R.drawable.ic_flag_uk
    }

/** "Catalan → Romanian", said in words, for screen readers and buttons. */
@Composable
fun directionName(direction: StudyDirection, language: MeaningLanguage): String {
    val other = stringResource(
        when (language) {
            MeaningLanguage.ROMANIAN -> R.string.lookup_target_romanian
            MeaningLanguage.ENGLISH -> R.string.lookup_target_english
        },
    )
    return when (direction) {
        StudyDirection.CATALAN_TO_MEANING -> stringResource(R.string.flashcards_direction_from_catalan, other)
        StudyDirection.MEANING_TO_CATALAN -> stringResource(R.string.flashcards_direction_to_catalan, other)
    }
}

/**
 * What pressing play is about to start, waiting on which way round.
 *
 * @param title what is being practised, said as the sheet's heading.
 * @param hasEnglish whether any of its cards has an English meaning; without
 *   one the two English rows would each lead to an empty session.
 */
data class PlayRequest(
    val scope: StudyScope,
    val title: String,
    val hasEnglish: Boolean,
)

/**
 * Which languages, and which way round — asked every time play is pressed.
 *
 * It was a menu in the app bar, set once and then silently in force for every
 * session after, which meant the one choice that changes what a session *is*
 * was made somewhere other than where the session was started, and was easy
 * to forget had been made at all. Now play asks, and the answer is one tap:
 * four rows, in the order somebody would say them — Catalan → Romanian, the way
 * back, then the same pair in English — each wearing its flags at either end so
 * the list is read by looking. The pair used last time is lit, so going again
 * the same way is a tap on the obvious row.
 *
 * The English rows are left out when nothing being practised has an English
 * meaning, rather than shown and leading nowhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayPairSheet(
    request: PlayRequest,
    lastDirection: StudyDirection,
    lastLanguage: MeaningLanguage,
    onDismiss: () -> Unit,
    onChoose: (StudyDirection, MeaningLanguage) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(false) }
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Space.xl)
                .navigationBarsPadding(),
        ) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Space.screen),
            )
            Text(
                text = stringResource(R.string.flashcards_choose_pair),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .padding(top = Space.xs, bottom = Space.md),
            )
            val languages = MeaningLanguage.entries.filter {
                it != MeaningLanguage.ENGLISH || request.hasEnglish
            }
            for (meaning in languages) {
                for (way in StudyDirection.entries) {
                    PairChoice(
                        direction = way,
                        language = meaning,
                        isLast = way == lastDirection && meaning == lastLanguage,
                        onClick = {
                            if (!chosen) {
                                chosen = true
                                // Closed first, so the session opens on a
                                // screen that has finished moving.
                                scope.launch { sheetState.hide() }.invokeOnCompletion {
                                    onDismiss()
                                    onChoose(way, meaning)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** One way to practise: flag, "Catalan → Romanian", flag, and a tick for last time's. */
@Composable
private fun PairChoice(
    direction: StudyDirection,
    language: MeaningLanguage,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val (from, to) = when (direction) {
        StudyDirection.CATALAN_TO_MEANING -> R.drawable.ic_flag_ca to language.flagRes
        StudyDirection.MEANING_TO_CATALAN -> language.flagRes to R.drawable.ic_flag_ca
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isLast) scheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 60.dp)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        Flag(from, 28.dp)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (isLast) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Space.sm)
                .size(18.dp),
        )
        Flag(to, 28.dp)
        Text(
            text = directionName(direction, language),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isLast) scheme.onSecondaryContainer else scheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.lg),
        )
        if (isLast) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = scheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
