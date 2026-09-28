package com.david.llegeix.lang

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The Mac's voice: macOS's own Catalan (Montse), through `say`.
 *
 * Keeps the phone's promises in the same way: one voice at a time, a second
 * press on the word being said stops it, and a press made while the voices are
 * still being listed is held briefly as one pending word rather than a queue.
 */
actual class Speech {

    actual enum class Status { STARTING, READY, MISSING_VOICE, UNAVAILABLE }

    private val _status = MutableStateFlow(Status.STARTING)
    actual val status: StateFlow<Status> = _status.asStateFlow()

    private val _speaking = MutableStateFlow<String?>(null)
    actual val speaking: StateFlow<String?> = _speaking.asStateFlow()

    @Volatile private var voice: String? = null
    @Volatile private var current: Process? = null
    @Volatile private var pending: String? = null
    @Volatile private var pendingAt = 0L

    init {
        thread(name = "speech-voices", isDaemon = true) {
            voice = findCatalanVoice()
            // macOS can always download a voice in System Settings, so a Mac
            // without one is missing it rather than unable.
            _status.value = if (voice != null) Status.READY else Status.MISSING_VOICE
            val waiting = pending
            pending = null
            val fresh = System.nanoTime() - pendingAt < TimeUnit.MILLISECONDS.toNanos(PENDING_MAX_WAIT_MS)
            if (waiting != null && fresh && _status.value == Status.READY) say(waiting)
        }
    }

    actual fun speak(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        if (_speaking.value == trimmed) {
            stop()
            return
        }
        when (_status.value) {
            Status.STARTING -> {
                pending = trimmed
                pendingAt = System.nanoTime()
            }
            Status.READY -> say(trimmed)
            Status.MISSING_VOICE, Status.UNAVAILABLE -> Unit
        }
    }

    private fun say(text: String) {
        current?.destroy()
        val process = runCatching {
            ProcessBuilder("say", "-v", voice ?: return, text).redirectErrorStream(true).start()
        }.getOrNull()
        if (process == null) {
            _speaking.value = null
            return
        }
        current = process
        _speaking.value = text
        thread(name = "speech-wait", isDaemon = true) {
            process.waitFor()
            // Only the utterance still on air may clear the marker.
            if (current === process) {
                current = null
                _speaking.value = null
            }
        }
    }

    actual fun stop() {
        pending = null
        val process = current
        current = null
        _speaking.value = null
        process?.destroy()
    }

    /** System Settings → Accessibility → Spoken Content, where voices are added. */
    actual fun requestVoice() {
        runCatching {
            ProcessBuilder(
                "open",
                "x-apple.systempreferences:com.apple.preference.universalaccess?SpokenContent",
            ).start()
        }
    }

    actual fun shutdown() = stop()

    private companion object {
        /** As on the phone: long enough for a cold start, short enough to be current. */
        const val PENDING_MAX_WAIT_MS = 4_000L

        /**
         * The name of a Catalan voice, Central (ca_ES) preferred. `say -v ?`
         * lists one per line as "Name (Description) ca_ES  # sample".
         */
        fun findCatalanVoice(): String? {
            val listing = runCatching {
                val process = ProcessBuilder("say", "-v", "?").redirectErrorStream(true).start()
                process.inputStream.bufferedReader().readText().also { process.waitFor() }
            }.getOrNull() ?: return null
            val line = Regex("""^(.+?)\s+(ca_[A-Z]{2})\s+#""", RegexOption.MULTILINE)
            val voices = line.findAll(listing).map { it.groupValues[1].trim() to it.groupValues[2] }.toList()
            return (voices.firstOrNull { it.second == "ca_ES" } ?: voices.firstOrNull())?.first
        }
    }
}
