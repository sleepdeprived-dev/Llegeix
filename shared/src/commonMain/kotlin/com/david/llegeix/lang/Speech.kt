package com.david.llegeix.lang

import kotlinx.coroutines.flow.StateFlow

/**
 * Saying a Catalan word out loud, once, on request.
 *
 * The phone speaks through Android's text-to-speech and the Mac through its own
 * system voices; both keep the same promises (see the phone's for the detail):
 * there is never more than one voice, it is always the word last asked for,
 * pressing the word being said stops it, and leaving the screen stops it.
 */
expect class Speech {

    /** Whether there is a Catalan voice, and what to do if not. */
    enum class Status {
        /** The engine has not answered yet. Buttons show; presses are held. */
        STARTING,

        /** There is a Catalan voice and it can be used now. */
        READY,

        /** The system can get a Catalan voice but does not have it yet. */
        MISSING_VOICE,

        /** No engine, or no Catalan at all. The button is not drawn. */
        UNAVAILABLE,
    }

    val status: StateFlow<Status>

    /** The text currently being said, or null. Drives the button's own state. */
    val speaking: StateFlow<String?>

    /** Say [text], or stop if it is already being said. */
    fun speak(text: String)

    /** Silence, now. */
    fun stop()

    /** Take the reader to where the system installs a Catalan voice. */
    fun requestVoice()

    /** For the process going away. */
    fun shutdown()
}
