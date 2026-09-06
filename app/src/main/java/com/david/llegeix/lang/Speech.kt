package com.david.llegeix.lang

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * The app's voice: a Catalan word, said out loud.
 *
 * Llegeix already prints a pronunciation, but IPA is a notation you have to
 * have learned, and the one thing a reader of a second language cannot get off
 * a page is what the word sounds like. This uses Android's own speech engine —
 * on the device, free, and no different in kind from the phone reading a
 * notification aloud. Nothing is sent anywhere.
 *
 * The whole design is about saying a word exactly once. Speech is the one thing
 * this app does that carries past the screen it was started on, so:
 *
 *  - it only ever happens from a press. Nothing auto-plays, so no state change
 *    and no recomposition can start a voice, and nothing can start one twice;
 *  - a new word flushes the old one instead of queueing behind it, so a run of
 *    quick presses is one voice at a time and never a backlog still talking a
 *    minute later;
 *  - pressing the word being said stops it;
 *  - a request made while the engine is still starting is held as one pending
 *    word rather than a queue, and dropped entirely if the engine takes so long
 *    that the reader has plainly moved on;
 *  - leaving the screen stops it.
 */
class Speech(context: Context) {

    /** Whether there is a Catalan voice on this device, and what to do if not. */
    enum class Status {
        /** The engine has not answered yet. Buttons show; presses are held. */
        STARTING,

        /** There is a Catalan voice and it can be used now. */
        READY,

        /**
         * The engine knows Catalan but does not have the data on the device.
         * This is a download the system offers, so the button stays and asks
         * for it rather than disappearing with no explanation.
         */
        MISSING_VOICE,

        /** No engine, or no Catalan at all. The button is not drawn. */
        UNAVAILABLE,
    }

    private val appContext = context.applicationContext

    private val _status = MutableStateFlow(Status.STARTING)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** The text currently being said, or null. Drives the button's own state. */
    private val _speaking = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = _speaking.asStateFlow()

    private val ids = AtomicLong(0)

    /** The utterance actually on air, so a flushed one cannot report over it. */
    @Volatile
    private var currentId: String? = null

    @Volatile
    private var pending: String? = null

    @Volatile
    private var pendingAt = 0L

    private val engine: TextToSpeech = TextToSpeech(appContext, ::onInit).apply {
        setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finished(utteranceId)

                @Deprecated("Kept because the base class still declares it abstract.")
                override fun onError(utteranceId: String?) = finished(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) =
                    finished(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) =
                    finished(utteranceId)
            },
        )
    }

    private fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            _status.value = Status.UNAVAILABLE
            return
        }
        _status.value = chooseCatalan()
        val waiting = pending
        pending = null
        // Only if the press is still recent enough to be the thing the reader
        // is waiting for. An engine that took five seconds to start has already
        // been given up on, and a word arriving out of a closed sheet is the
        // app talking to nobody.
        val fresh = SystemClock.elapsedRealtime() - pendingAt < PENDING_MAX_WAIT_MS
        if (waiting != null && fresh && _status.value == Status.READY) say(waiting)
    }

    /**
     * The best Catalan the engine has.
     *
     * Central Catalan is what the app transcribes and what most of its readers
     * are reading, so ca-ES is asked for first and plain Catalan is the
     * fallback for an engine that does not split it by country.
     */
    private fun chooseCatalan(): Status {
        var missingData = false
        for (locale in CATALAN) {
            val available = runCatching { engine.isLanguageAvailable(locale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            when {
                available >= TextToSpeech.LANG_AVAILABLE ->
                    if (runCatching { engine.setLanguage(locale) }
                            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED) >=
                        TextToSpeech.LANG_AVAILABLE
                    ) {
                        return Status.READY
                    }

                available == TextToSpeech.LANG_MISSING_DATA -> missingData = true
            }
        }
        return if (missingData) Status.MISSING_VOICE else Status.UNAVAILABLE
    }

    /**
     * Say [text], or stop if it is already being said.
     *
     * The second press stopping it is the whole of the "played once" promise
     * from the reader's side: whatever they do with the button, there is never
     * more than one voice, and it is always the one they last asked for.
     */
    fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_speaking.value == trimmed) {
            stop()
            return
        }
        when (_status.value) {
            Status.STARTING -> {
                pending = trimmed
                pendingAt = SystemClock.elapsedRealtime()
            }

            Status.READY -> say(trimmed)

            Status.MISSING_VOICE, Status.UNAVAILABLE -> Unit
        }
    }

    private fun say(text: String) {
        val id = "llegeix-" + ids.incrementAndGet()
        currentId = id
        _speaking.value = text
        val result = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) {
            currentId = null
            _speaking.value = null
        }
    }

    /** Silence, now: leaving the screen, or pressing the word being said. */
    fun stop() {
        pending = null
        currentId = null
        _speaking.value = null
        runCatching { engine.stop() }
    }

    /**
     * Hand the reader to the system's own "install voice data" screen.
     *
     * Only reachable from [Status.MISSING_VOICE], where the engine has said it
     * knows Catalan and merely lacks the files — which is a thing the reader can
     * fix in about four taps and could not possibly guess at otherwise.
     */
    fun requestVoice(from: Context) {
        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { from.startActivity(intent) }
    }

    /**
     * Only the utterance still on air may clear the marker.
     *
     * A flushed one reports its own stop *after* its replacement has started,
     * so without the check the new word would be marked finished before it had
     * said a syllable, and the button would go quiet with the phone still
     * talking.
     */
    private fun finished(utteranceId: String?) {
        if (utteranceId != null && utteranceId == currentId) {
            currentId = null
            _speaking.value = null
        }
    }

    /** For the process going away; the engine otherwise lives as long as the app. */
    fun shutdown() {
        stop()
        runCatching { engine.shutdown() }
    }

    private companion object {
        val CATALAN = listOf(Locale.forLanguageTag("ca-ES"), Locale.forLanguageTag("ca"))

        /**
         * How long a press waits for a cold engine before it is discarded.
         *
         * Long enough for a first start on a slow phone, short enough that the
         * word cannot arrive after the reader has closed the sheet and moved on.
         */
        const val PENDING_MAX_WAIT_MS = 4_000L
    }
}
