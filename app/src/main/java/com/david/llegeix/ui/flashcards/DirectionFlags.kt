package com.david.llegeix.ui.flashcards

import androidx.annotation.DrawableRes
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.flashcards.StudyDirection
import com.david.llegeix.ui.common.Space

/**
 * A direction of study as two flags and an arrow: the side you are shown, then
 * the side you answer with — the Senyera and Romania's, one way or the other.
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

/** The meaning side of a card: Romania's flag. */
@Composable
private fun MeaningFlags(width: Dp) {
    Flag(MeaningLanguage.ROMANIAN.flagRes, width)
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

/** A language's flag. */
val MeaningLanguage.flagRes: Int
    @DrawableRes get() = when (this) {
        MeaningLanguage.ROMANIAN -> R.drawable.ic_flag_ro
    }

/** "Català → romanès", said in words, for screen readers. */
@Composable
fun directionName(direction: StudyDirection): String {
    val meaning = stringResource(R.string.lookup_target_romanian)
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
 * The title and two large tiles, and nothing else: the Senyera and Romania's
 * flag with an arrow between them, one way and the other. No words — the flags
 * say it faster — though each tile is still announced by name. The way used
 * last time wears the accent — its fill and an outline, no tick in the corner
 * to sit off-centre against the flags — so going again the same way is a tap
 * on the obvious tile.
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
    var chosen by remember { mutableStateOf<StudyDirection?>(null) }
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xxl)
                .navigationBarsPadding(),
        ) {
            Text(
                text = request.title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm, bottom = Space.xl),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                for (way in StudyDirection.entries) {
                    PairTile(
                        direction = way,
                        isSelected = way == (chosen ?: lastDirection),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (chosen == null) {
                                chosen = way
                                // The accent moves first, then the sheet
                                // closes, so the choice is seen being made.
                                scope.launch {
                                    delay(160)
                                    sheetState.hide()
                                }.invokeOnCompletion {
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
}

/** One way to practise: two big flags and an arrow on a rounded tile, in the accent when it is the one. */
@Composable
private fun PairTile(
    direction: StudyDirection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val fill by animateColorAsState(
        if (isSelected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        label = "pair fill",
    )
    val edge by animateColorAsState(
        if (isSelected) scheme.primary else Color.Transparent,
        label = "pair edge",
    )
    val name = directionName(direction)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(shape)
            .background(fill)
            .border(2.dp, edge, shape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = name
                selected = isSelected
            }
            .padding(vertical = Space.xxl, horizontal = Space.md),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        ) {
            DirectionFlags(direction = direction, flagWidth = 44.dp)
        }
    }
}
