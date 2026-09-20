package com.david.llegeix.ui.flashcards

import androidx.annotation.DrawableRes
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Which languages, and which way round, as one menu of four.
 *
 * This replaces two controls that sat across the top of the Flashcards tab: a
 * pair of pills for the direction and another pair for the language. They took
 * a third of the screen above the decks, they were the first thing on a tab
 * whose subject is the decks, and they were never two questions to begin with
 * — the pair is *Catalan and Romanian, this way round*, and setting half of it
 * at a time means passing through a combination nobody chose.
 *
 * Four rows, in the order somebody would say them: Catalan → Romanian, the way
 * back, then the same pair in English. Each row wears its own flags, so the
 * list is read by looking rather than by reading, and carries the full name for
 * anybody who cannot see them. The one in force is ticked.
 *
 * It lives in the app bar next to the backup button, which is where a phone
 * keeps the settings that belong to a whole screen — and which leaves the tab
 * itself to be what it is for.
 */
@Composable
fun StudyPairMenu(
    direction: StudyDirection,
    language: MeaningLanguage,
    onChoose: (StudyDirection, MeaningLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_language),
                contentDescription = stringResource(R.string.flashcards_choose_pair),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (meaning in MeaningLanguage.entries) {
                for (way in StudyDirection.entries) {
                    val chosen = way == direction && meaning == language
                    DropdownMenuItem(
                        leadingIcon = {
                            DirectionFlags(direction = way, language = meaning, flagWidth = 20.dp)
                        },
                        text = { Text(directionName(way, meaning)) },
                        trailingIcon = {
                            if (chosen) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        onClick = {
                            onChoose(way, meaning)
                            open = false
                        },
                    )
                }
            }
        }
    }
}
