package com.david.llegeix.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The mark beside a menu item or an action row.
 *
 * These were colour emoji. Each one is a small picture drawn in a typeface the
 * app does not control, at a weight it does not control, in colours it does not
 * control — so five of them down one menu read as five little illustrations
 * competing with the words rather than as one list. A macOS menu makes the same
 * choice the other way: a single-colour glyph in the menu's own ink, there to
 * be recognised at the edge of vision and otherwise to stay out of the way.
 *
 * Slightly smaller than the 24dp an icon button gets, and in the muted ink, so
 * the item's text stays the thing being read.
 */
@Composable
fun MenuIcon(painter: Painter) {
    Icon(
        painter = painter,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(MenuIconSize),
    )
}

/** The same, for the icons that ship with Compose rather than as drawables. */
@Composable
fun MenuIcon(imageVector: ImageVector) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(MenuIconSize),
    )
}

private val MenuIconSize = 20.dp
