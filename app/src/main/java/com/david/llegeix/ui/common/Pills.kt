package com.david.llegeix.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A row of pills that pick one thing between them.
 *
 * Material's own segmented buttons were what this replaces, and they were the
 * wrong shape for the job in two ways that cannot be styled out. They draw a
 * shared outline — a box divided by hairlines — which reads as a form control,
 * a thing to fill in; and every item is the same width whatever is in it, so a
 * two-word label and a pair of flags are given the same room and neither gets
 * what it needs.
 *
 * A pill group is a track with the chosen pill floating on it. The chosen one
 * is a solid object you can see is lifted off the surface behind it, the others
 * are simply not it, and there are no lines anywhere. It is the shape a phone
 * uses for "which of these", and it is the same control in the Saved tabs and
 * in the Flashcards direction switch, so learning it once is learning it.
 *
 * @param track the sunken groove the pills sit in. The default reads on a plain
 *   surface; a panel that already has a colour of its own should pass something
 *   of that colour, dimmed, so the groove looks cut into the panel rather than
 *   laid on it.
 */
@Composable
fun PillGroup(
    modifier: Modifier = Modifier,
    track: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    padding: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PillTrackCorner))
            .background(track)
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(padding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * One choice in a [PillGroup].
 *
 * The chosen pill takes [selectedContainer] and lifts very slightly — two
 * degrees of corner, no shadow — while the others stay flat and transparent.
 * The colour is animated because the pills are switched between often enough
 * that a hard cut reads as the screen being rebuilt.
 *
 * @param label what to say out loud. The visible content is often a flag or a
 *   glyph, which announces as nothing at all without this.
 */
@Composable
fun RowScope.Pill(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    selectedContainer: Color = MaterialTheme.colorScheme.primary,
    selectedContent: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    contentPadding: PaddingValues = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
    content: @Composable RowScope.() -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) selectedContainer else Color.Transparent,
        animationSpec = spring(),
        label = "pill container",
    )
    val onContainer by animateColorAsState(
        targetValue = if (selected) selectedContent else unselectedContent,
        animationSpec = spring(),
        label = "pill content",
    )
    val corner by animateDpAsState(
        targetValue = if (selected) PillCornerSelected else PillCorner,
        animationSpec = spring(),
        label = "pill corner",
    )
    val shape = RoundedCornerShape(corner)

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(container)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            .heightIn(min = PillMinHeight)
            .padding(contentPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides onContainer) {
            content()
        }
    }
}

/**
 * A number worn by a pill: how many things are behind it.
 *
 * Written as a mark of its own rather than as "Pages (2)". Brackets in a label
 * are a programmer's way of attaching a number to a word — they read as part of
 * the name, they make the label longer and harder to aim at, and three tabs
 * wearing them turn a row of names into a row of expressions. A small disc set
 * slightly apart is the same fact, said the way every other counted thing on a
 * phone says it.
 *
 * Nothing is drawn for zero. A tab with nothing behind it does not need a badge
 * saying so; the empty pane it opens onto says it properly, in words.
 */
@Composable
fun PillCount(
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    val content = LocalContentColor.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(PillCorner))
            .background(content.copy(alpha = if (selected) 0.22f else 0.12f))
            .padding(horizontal = 7.dp, vertical = 1.dp),
    ) {
        Text(
            text = if (count > PillCountCeiling) "$PillCountCeiling+" else "$count",
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = if (selected) 1f else 0.8f),
        )
    }
}

/** Above this a count stops being a number and becomes "a lot". */
private const val PillCountCeiling = 99

private val PillTrackCorner = 22.dp
private val PillCorner = 16.dp
private val PillCornerSelected = 18.dp
private val PillMinHeight = 38.dp
