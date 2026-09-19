package com.david.llegeix.ui.flashcards

import androidx.annotation.DrawableRes
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
    flagWidth: Dp = 24.dp,
) {
    val (from, to) = when (direction) {
        StudyDirection.CATALAN_TO_ROMANIAN -> R.drawable.ic_flag_ca to R.drawable.ic_flag_ro
        StudyDirection.ROMANIAN_TO_CATALAN -> R.drawable.ic_flag_ro to R.drawable.ic_flag_ca
    }
    val name = stringResource(directionLabel(direction))
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
