package com.david.llegeix.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A top bar's title, with the mark the tab is known by beside it.
 *
 * The bottom bar draws an icon against every destination, and until this the app
 * then dropped it the moment you arrived: the Dictionary was a book at the
 * bottom of the screen and four bare words at the top of it. Repeating the icon
 * is not decoration — it is the answer to "where am I", given in the same symbol
 * that was pressed to get here, so arriving somewhere confirms the press rather
 * than merely following it. The library has always done this with the flag; this
 * is that arrangement, written once, for the tabs that had nothing.
 *
 * The glyph is monochrome and takes the bar's own ink, like every other mark in
 * this app, so a title reads as a title rather than as a word with a picture
 * stuck to it. It is a little smaller than the type it sits beside, for the same
 * reason: the word is the thing being read.
 */
@Composable
fun ScreenTitle(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            painter = painterResource(icon),
            // Named by the title beside it; announcing both would read the
            // screen's name out twice.
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = Modifier.size(TitleIconSize),
        )
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Space.md),
        )
    }
}

/** Set against the app bar's title type rather than against an icon button. */
private val TitleIconSize = 22.dp
