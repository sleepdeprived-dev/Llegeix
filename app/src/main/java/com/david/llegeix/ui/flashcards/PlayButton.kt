package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable

/**
 * The one way into practice, wherever practice is offered.
 *
 * A circle with a play mark in it and nothing else — never a number, never a
 * word, never a rounded rectangle that is nearly a circle. It used to be a
 * tonal button that grew a count when something was due and shrank back when
 * nothing was, so the same control was three different shapes down one screen
 * and the reader's thumb had to find it again each time. A number on a button
 * is also the wrong place for a number: it makes pressing the button look like
 * it will practise exactly that many cards, which is only true until a card
 * falls due while the screen is open.
 *
 * What is due belongs in the list, not on the button. What the button is for
 * never changes, so neither does the button.
 *
 * @param prominent the one on the tab's own header, which starts a session over
 *   everything. Solid, in the accent. The ones on the rows are tonal, so a
 *   column of them does not read as a column of alarms.
 */
@Composable
fun PlayButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    size: Dp = PlayButtonSize,
) {
    val container = if (prominent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (prominent) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(container)
            .clickable(
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            // The circle carries the label, through onClickLabel, so the mark
            // inside it says nothing and is not announced twice.
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * Large enough to be a comfortable target on its own, small enough that a
 * column of deck rows does not turn into a column of buttons.
 */
private val PlayButtonSize = 44.dp
