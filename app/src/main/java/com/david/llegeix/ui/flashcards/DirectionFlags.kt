package com.david.llegeix.ui.flashcards

import androidx.annotation.DrawableRes
import com.david.llegeix.data.flashcards.MeaningLanguage
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
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
 * Which language the meanings are practised in: two small flags, the one in
 * use lit and the other faded — the same switch the reader's lookup panel uses
 * to pick a translation language, so it is already known by sight.
 */
@Composable
fun LanguageSwitch(
    selected: MeaningLanguage,
    onSelect: (MeaningLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeaningLanguage.entries.forEach { language ->
            val isSelected = language == selected
            val name = stringResource(
                when (language) {
                    MeaningLanguage.ROMANIAN -> R.string.lookup_target_romanian
                    MeaningLanguage.ENGLISH -> R.string.lookup_target_english
                },
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(language) }
                    .semantics { contentDescription = name }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Image(
                    painter = painterResource(language.flagRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 21.dp, height = 14.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .alpha(if (isSelected) 1f else 0.45f),
                )
            }
        }
    }
}
