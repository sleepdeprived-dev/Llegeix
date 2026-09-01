package com.david.llegeix.ui.common

import androidx.compose.ui.unit.dp

/**
 * The app's spacing scale.
 *
 * Named rather than typed at each call site so that "how much room does this
 * need to breathe" is answered the same way everywhere. Llegeix is built for
 * readers who find cluttered interfaces hard going, so the steps are wider than
 * Material's 8dp grid would suggest and the screen margin is deliberately
 * generous.
 */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val huge = 48.dp

    /** Horizontal margin for page content. */
    val screen = 20.dp

    /** Vertical padding inside a tappable list row. */
    val row = 18.dp

    /**
     * Height of the app bar, against Material's default of 64dp.
     *
     * A 24dp icon with 8dp of air above and below. Going further would start
     * clipping the glyphs themselves: the icon buttons keep their full 48dp
     * touch target regardless — Compose reserves that independently of the
     * visual size — so this is as short as the bar can look without becoming
     * harder to hit than it looks.
     */
    val topBar = 40.dp
}
