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
     * It has been 40dp, then 48dp, and is now 56dp. 48 was the height of an
     * icon button's touch target and therefore the least the bar could measure
     * without cutting into one — but "no taller than it strictly needs" turned
     * out to read as cramped rather than as calm, with the title sitting hard
     * against the status bar above it. 56dp gives a 24dp icon 16dp of air on
     * each side, is the same height as the reader's find field so the two swap
     * without the screen jumping, and is still a step below Material's own.
     */
    val topBar = 56.dp
}
