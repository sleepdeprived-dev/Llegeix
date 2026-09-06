package com.david.llegeix.ui.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R
import com.david.llegeix.lang.Speech

/** The app's one speech engine, shared so it is started at most once. */
@Composable
fun rememberSpeech(): Speech {
    val context = LocalContext.current
    return remember(context) { (context.applicationContext as LlegeixApp).speech }
}

/**
 * Say this word out loud.
 *
 * Beside the word rather than in the row of controls above it, because it is
 * the only button on these screens that acts on the word itself: the star keeps
 * it, the flags change the language, and this one is the word.
 *
 * It is not drawn at all where the device has no Catalan voice and no way to
 * get one. A permanently dead speaker is a promise the app cannot keep, and the
 * same reasoning already hides the contents button on a document that has no
 * contents. Where the voice is merely not downloaded yet the button stays and
 * asks for it, since that is four taps away rather than impossible.
 */
@Composable
fun PronounceButton(text: String, modifier: Modifier = Modifier) {
    val speech = rememberSpeech()
    val status by speech.status.collectAsStateWithLifecycle()
    val speaking by speech.speaking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val word = text.trim()

    // A word still being said after the sheet that asked for it has closed is
    // the app carrying on with nobody there, so leaving ends it — and so does
    // the word underneath the button changing, which is how the practice deck
    // moves on to the next card without the last one still talking over it.
    //
    // Only ever this button's own word, though. The engine is one object shared
    // by the whole app, and a button that stopped it unconditionally would
    // silence a word another button had started the moment it happened to be
    // disposed of.
    DisposableEffect(speech, word) {
        onDispose { if (speech.speaking.value == word) speech.stop() }
    }

    if (status == Speech.Status.UNAVAILABLE) return

    val isSaying = speaking == word
    IconButton(
        onClick = {
            if (status == Speech.Status.MISSING_VOICE) {
                speech.requestVoice(context)
            } else {
                speech.speak(word)
            }
        },
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_speaker),
            contentDescription = stringResource(
                when {
                    status == Speech.Status.MISSING_VOICE -> R.string.pronounce_get_voice
                    isSaying -> R.string.pronounce_stop
                    else -> R.string.pronounce
                },
            ),
            tint = if (isSaying) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
