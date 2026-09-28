package com.david.llegeix.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The app's snackbar, in the app's own colours.
 *
 * Material's default draws a snackbar on `inverseSurface`, which is deliberate
 * and, in a reading app, wrong: in dark mode "inverse" means a bright white
 * panel, and the whole point of dark mode here is that nothing large and white
 * appears while somebody is reading in the dark. A message about a book being
 * removed from a list is not worth being dazzled for.
 *
 * So it is drawn as what it is — a raised surface of the current theme, with
 * the theme's own ink on it and the accent on its action. Light in light mode,
 * dark in dark mode, black on AMOLED.
 *
 * One of these rather than a copy of the same three colour arguments per screen:
 * the app has snackbars on the library, the reader, the history and the saved
 * words, and a themed snackbar on three of them is a bug on the fourth.
 */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionContentColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            // A shadow rather than a tonal lift, because the container colour is
            // already close to what is behind it: without an edge the snackbar
            // reads as part of the screen rather than as something over it.
            shape = MaterialTheme.shapes.medium,
            actionOnNewLine = false,
        )
    }
}

/** Kept for the one caller that positions its host itself. */
val SnackbarEdgeInset = 8.dp
