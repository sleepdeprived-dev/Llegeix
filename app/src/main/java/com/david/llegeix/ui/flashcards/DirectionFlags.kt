package com.david.llegeix.ui.flashcards

import androidx.annotation.DrawableRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Arrangement
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
 * A direction of study as flags and an arrow: the side you are shown, then the
 * side you answer with.
 *
 * The meaning side is always both of the card's meanings — English and
 * Romanian together — so it is drawn as both flags, touching, as one side.
 * The words are kept for anyone who cannot see the flags: the whole mark is
 * announced as the direction's full name.
 */
@Composable
fun DirectionFlags(
    direction: StudyDirection,
    modifier: Modifier = Modifier,
    flagWidth: Dp = 24.dp,
) {
    val name = directionName(direction)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = name },
    ) {
        when (direction) {
            StudyDirection.CATALAN_TO_MEANING -> Flag(R.drawable.ic_flag_ca, flagWidth)
            StudyDirection.MEANING_TO_CATALAN -> MeaningFlags(flagWidth)
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 6.dp)
                .size(flagWidth * 0.66f),
        )
        when (direction) {
            StudyDirection.CATALAN_TO_MEANING -> MeaningFlags(flagWidth)
            StudyDirection.MEANING_TO_CATALAN -> Flag(R.drawable.ic_flag_ca, flagWidth)
        }
    }
}

/** The English and Romanian flags side by side: the meaning side of a card. */
@Composable
private fun MeaningFlags(width: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(width / 8)) {
        Flag(MeaningLanguage.ENGLISH.flagRes, width)
        Flag(MeaningLanguage.ROMANIAN.flagRes, width)
    }
}

@Composable
fun Flag(@DrawableRes res: Int, width: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width / 8)
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier
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

/** "Catalan → English · Romanian", said in words, for screen readers and buttons. */
@Composable
fun directionName(direction: StudyDirection): String {
    val meaning = stringResource(R.string.lookup_target_english) + " · " +
        stringResource(R.string.lookup_target_romanian)
    return when (direction) {
        StudyDirection.CATALAN_TO_MEANING -> stringResource(R.string.flashcards_direction_from_catalan, meaning)
        StudyDirection.MEANING_TO_CATALAN -> stringResource(R.string.flashcards_direction_to_catalan, meaning)
    }
}

/**
 * What pressing play is about to start, waiting on which way round.
 *
 * @param title what is being practised, said as the sheet's heading.
 */
data class PlayRequest(
    val scope: StudyScope,
    val title: String,
)

/**
 * Which way round — asked every time play is pressed.
 *
 * Two rows: the Catalan shown and its English and Romanian as the answer, or
 * the other way. Both meanings are always shown together, so there is no
 * language to choose, only a direction. The way used last time is lit, so
 * going again the same way is a tap on the obvious row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayPairSheet(
    request: PlayRequest,
    lastDirection: StudyDirection,
    onDismiss: () -> Unit,
    onChoose: (StudyDirection) -> Unit,
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
            for (way in StudyDirection.entries) {
                PairChoice(
                    direction = way,
                    isLast = way == lastDirection,
                    onClick = {
                        if (!chosen) {
                            chosen = true
                            // Closed first, so the session opens on a screen
                            // that has finished moving.
                            scope.launch { sheetState.hide() }.invokeOnCompletion {
                                onDismiss()
                                onChoose(way)
                            }
                        }
                    },
                )
            }
        }
    }
}

/** One way to practise, as its flags and its name, with a tick for last time's. */
@Composable
private fun PairChoice(
    direction: StudyDirection,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isLast) scheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.md, vertical = Space.sm),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (isLast) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        ) {
            DirectionFlags(direction = direction, flagWidth = 26.dp)
        }
        Text(
            text = directionName(direction),
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
