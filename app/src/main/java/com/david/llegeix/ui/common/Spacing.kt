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
     * A 24dp icon with 12dp of air above and below. It was 40dp, which was
     * genuinely too tight once the duplicated status-bar inset that had been
     * inflating it was fixed — the bar finally measured what it claimed to, and
     * what it claimed to was cramped. This is the same height as an icon
     * button's touch target, so the bar is now exactly as tall as the things
     * inside it need, and no taller.
     */
    val topBar = 48.dp
}
