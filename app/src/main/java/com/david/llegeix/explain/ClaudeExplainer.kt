package com.david.llegeix.explain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** What came back from asking Claude about a word. */
sealed interface Explanation {
    data class Ready(val text: String) : Explanation
    /** No key configured yet; the UI points at Settings rather than erroring. */
    data object NeedsKey : Explanation
    data class Failed(val reason: String) : Explanation
}

/**
 * Asks Claude to explain a Catalan word or phrase in the sentence it appeared in.
 *
 * Called over plain HTTPS rather than through the Anthropic Java SDK. The SDK is
 * the documented default for Kotlin, but its core artifact alone is ~26 MB and
 * it pulls Jackson, kotlin-reflect and a JSON-schema generator — weight this app
 * cannot justify for one request shape, on a release build that does not run R8.
 * `HttpsURLConnection` and `org.json` are both already in Android, so this costs
 * nothing to ship.
 */
class ClaudeExplainer {

    suspend fun explain(
        apiKey: String,
        selection: String,
        context: String,
        documentTitle: String,
    ): Explanation {
        if (apiKey.isBlank()) return Explanation.NeedsKey

        return withContext(Dispatchers.IO) {
            try {
                request(apiKey, buildPrompt(selection, context, documentTitle))
            } catch (error: IOException) {
                Explanation.Failed(error.message.orEmpty())
            }
        }
    }

    /**
     * The prompt is deliberately narrow.
     *
     * The reader is mid-page and wants to keep reading, so this asks for a short
     * answer about *this* occurrence — what the word is doing in this sentence —
     * rather than a dictionary entry. It also names the register and any idiom,
     * which is the part a translation drops.
     */
    internal fun buildPrompt(selection: String, context: String, title: String): String =
        buildString {
            append("Explain the Catalan ")
            append(if (selection.trim().contains(' ')) "phrase" else "word")
            append(" \"").append(selection).append("\" as it is used here.\n\n")
            if (context.isNotBlank()) {
                append("Sentence: \"").append(context).append("\"\n")
            }
            if (title.isNotBlank()) {
                append("From: ").append(title).append("\n")
            }
        }

    private fun request(apiKey: String, prompt: String): Explanation {
        val body = buildBody(prompt)

        val connection = (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
            setRequestProperty("anthropic-beta", FALLBACK_BETA)
        }

        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            return if (status in 200..299) {
                parse(payload)
            } else {
                Explanation.Failed(errorMessage(status, payload))
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The request body, separated out so its shape can be asserted in a test.
     *
     * There is no honest way to verify a live call without spending someone's
     * money, so the next best thing is to pin the field names and values
     * against the documented API here.
     */
    internal fun buildBody(prompt: String): JSONObject = JSONObject().apply {
        put("model", MODEL)
        put("max_tokens", MAX_TOKENS)
        // Opus 5 can decline a request; "default" lets the API re-run it on a
        // suitable model server-side instead of handing back a refusal.
        put("fallbacks", "default")
        // A short gloss for someone mid-page: thinking stays on, which is the
        // default and avoids the tag-leaking that disabling it causes, but
        // effort is low because this is not a hard question.
        put("output_config", JSONObject().put("effort", "low"))
        put("system", SYSTEM_PROMPT)
        put(
            "messages",
            JSONArray().put(JSONObject().put("role", "user").put("content", prompt)),
        )
    }

    internal fun parse(payload: String): Explanation {
        val json = JSONObject(payload)

        // Check the stop reason before touching content: a declined request is
        // a successful HTTP 200 whose content array can be empty.
        if (json.optString("stop_reason") == "refusal") {
            return Explanation.Failed(REFUSED)
        }

        val content = json.optJSONArray("content") ?: return Explanation.Failed(EMPTY)
        val text = buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.trim()

        return if (text.isEmpty()) Explanation.Failed(EMPTY) else Explanation.Ready(text)
    }

    /** The API's own message when it has one; the status code otherwise. */
    internal fun errorMessage(status: Int, payload: String): String {
        val fromApi = runCatching {
            JSONObject(payload).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return when {
            !fromApi.isNullOrBlank() -> fromApi
            status == HttpURLConnection.HTTP_UNAUTHORIZED -> UNAUTHORIZED
            else -> "HTTP $status"
        }
    }

    internal companion object {
        const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        const val MODEL = "claude-opus-5"

        /** Short by design: this is a margin note, not an essay. */
        const val MAX_TOKENS = 700

        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 60_000

        const val SYSTEM_PROMPT =
            "You help an English speaker who is learning Catalan and is reading a " +
                "PDF. Explain the word or phrase they selected as it is used in the " +
                "sentence given: what it means there, its part of speech and any " +
                "grammar worth noticing, whether it is idiomatic, formal or " +
                "colloquial, and the literal sense if that differs from the " +
                "meaning. Three or four short sentences at most. Write in English, " +
                "quote Catalan in italics, and do not repeat a plain translation " +
                "they already have. If the sentence is too fragmentary to be sure, " +
                "say so rather than inventing a reading."

        const val REFUSED = "refusal"
        const val EMPTY = "empty"
        const val UNAUTHORIZED = "unauthorized"
    }
}
