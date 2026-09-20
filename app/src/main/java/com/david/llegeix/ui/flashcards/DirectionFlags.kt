package com.david.llegeix.ui.flashcards

import androidx.annotation.DrawableRes
import com.david.llegeix.data.flashcards.MeaningLanguage
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.Composable
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
import com.david.llegeix.ui.common.Pill
import com.david.llegeix.ui.common.PillGroup
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
 * Which way round the cards are asked, as two pills.
 *
 * This replaces a pair of Material segmented buttons, which were the wrong
 * control twice over. They drew a shared outline divided by a hairline, which
 * is the shape of a form field rather than of a choice; and they insisted on
 * equal widths and a tick slot per item, so two flags and an arrow sat in a box
 * sized for a sentence. What was left looked like a setting somebody had to
 * fill in before they were allowed to practise.
 *
 * Pills say the same thing without any of that: a groove, and the chosen way
 * round sitting solid on it. The flags do the work — *Català → Romanès* is two
 * words and an arrow to read twice, and two flags are told apart without
 * reading at all — and the direction's full name goes to anybody who cannot see
 * them, through the pill's own label.
 */
@Composable
fun DirectionPills(
    selected: StudyDirection,
    language: MeaningLanguage,
    onSelect: (StudyDirection) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    PillGroup(modifier = modifier, track = track) {
        StudyDirection.entries.forEach { direction ->
            Pill(
                selected = direction == selected,
                onClick = { onSelect(direction) },
                label = directionName(direction, language),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = Space.sm, vertical = Space.sm),
            ) {
                DirectionFlags(direction = direction, language = language, flagWidth = 22.dp)
            }
        }
    }
}

/**
 * Which language the meanings are practised in: the two flags as pills, the
 * one in use lifted onto the accent and the other flat on the groove.
 *
 * The same control as the direction switch, one size smaller, because it is the
 * same kind of question asked about something smaller. It was two flags in a
 * grey capsule with the unchosen one faded, which said "off" about a language
 * rather than "not this one" — English does not stop existing because you are
 * practising in Romanian.
 */
@Composable
fun LanguageSwitch(
    selected: MeaningLanguage,
    onSelect: (MeaningLanguage) -> Unit,
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    PillGroup(modifier = modifier, track = track, padding = 3.dp) {
        MeaningLanguage.entries.forEach { language ->
            Pill(
                selected = language == selected,
                onClick = { onSelect(language) },
                label = stringResource(
                    when (language) {
                        MeaningLanguage.ROMANIAN -> R.string.lookup_target_romanian
                        MeaningLanguage.ENGLISH -> R.string.lookup_target_english
                    },
                ),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
            ) {
                Image(
                    painter = painterResource(language.flagRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 22.dp, height = 15.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}
