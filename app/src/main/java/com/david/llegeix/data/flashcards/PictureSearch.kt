package com.david.llegeix.data.flashcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Pictures for a word, found on the internet.
 *
 * ### Free, and asked for nothing
 *
 * Both sources are public services with no account and no key: ARASAAC's
 * pictograms, published by the Government of Aragón, and Openverse, the
 * openly licensed media index run by WordPress. A key shipped inside a
 * sideloaded APK is a key everybody holding the APK has — the same reason the
 * update check reads a public repository — so a service that needed one was
 * never an option.
 *
 * ### What leaves the phone
 *
 * The word being written on a card, and nothing else: no identifier, no
 * account, nothing about the reader. For photos it is the English translation
 * of the word, made on the phone first. This only happens while a card is open
 * in the form, and a picture only reaches a card when the reader taps it.
 */
class PictureSearch(context: Context) {

    private val appContext = context.applicationContext

    /** How a search went, told apart as far as the form can say something useful. */
    sealed interface Outcome {
        data class Found(val hits: List<PictureHit>) : Outcome

        /** No network, or the service did not answer. */
        data object Offline : Outcome
    }

    /** Decoded thumbnails, so flicking between the two sources does not refetch them. */
    private val thumbnails = object : LruCache<String, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun search(source: PictureSource, query: String): Outcome = withContext(Dispatchers.IO) {
        val url = when (source) {
            PictureSource.PICTOGRAMS -> PictureResults.arasaacSearchUrl(query)
            PictureSource.PHOTOS -> PictureResults.openverseSearchUrl(query)
        }
        val body = try {
            fetch(url, MAX_ANSWER_BYTES, acceptEmpty = true)
        } catch (error: IOException) {
            return@withContext Outcome.Offline
        }
        val text = body.toString(Charsets.UTF_8)
        Outcome.Found(
            when (source) {
                PictureSource.PICTOGRAMS -> PictureResults.parseArasaac(text)
                PictureSource.PHOTOS -> PictureResults.parseOpenverse(text)
            },
        )
    }

    /** A word's meanings as ARASAAC's people wrote them; either may be missing. */
    data class Meanings(val romanian: String?, val english: String?)

    /** Looked up once per word per run, since both meaning fields ask. */
    private val meanings = LruCache<String, Meanings>(200)

    /**
     * The Romanian and English labels of the pictogram labelled with exactly
     * [word] in Catalan, or null when there is none or ARASAAC cannot be
     * reached — in which case the caller asks the translator instead.
     */
    suspend fun meanings(word: String): Meanings? {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return null
        meanings.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val id = runCatching {
                ArasaacWords.firstExactId(
                    fetch(ArasaacWords.exactSearchUrl(key), MAX_ANSWER_BYTES, acceptEmpty = true)
                        .toString(Charsets.UTF_8),
                    key,
                )
            }.getOrNull() ?: return@withContext null
            fun label(language: String): String? = runCatching {
                ArasaacWords.firstKeyword(
                    fetch(ArasaacWords.pictogramUrl(language, id), MAX_ANSWER_BYTES).toString(Charsets.UTF_8),
                )
            }.getOrNull()
            Meanings(romanian = label("ro"), english = label("en"))
                .takeIf { it.romanian != null || it.english != null }
                ?.also { meanings.put(key, it) }
        }
    }

    /** A thumbnail, or null if it could not be fetched; the row leaves a blank tile. */
    suspend fun thumbnail(url: String): Bitmap? {
        thumbnails.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = runCatching { fetch(url, MAX_THUMBNAIL_BYTES) }.getOrNull()
                ?: return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return@withContext null
            val options = BitmapFactory.Options().apply {
                inSampleSize = ImageSizing.sampleSize(bounds.outWidth, bounds.outHeight, THUMBNAIL_EDGE)
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?.also { thumbnails.put(url, it) }
        }
    }

    /**
     * The chosen picture, downloaded into the cache.
     *
     * The caller brings it in with [FlashcardImages.import] — shrunk, turned
     * the right way up and saved as a JPEG the app wrote itself, exactly like a
     * photo from the phone — and deletes this file afterwards.
     */
    suspend fun download(hit: PictureHit): File = withContext(Dispatchers.IO) {
        val bytes = try {
            fetch(hit.fullUrl, MAX_PICTURE_BYTES)
        } catch (error: IOException) {
            // The full-size photo lives on somebody else's server and is the
            // part most likely to have gone; the thumbnail is Openverse's own.
            if (hit.thumbnailUrl == hit.fullUrl) throw error
            fetch(hit.thumbnailUrl, MAX_PICTURE_BYTES)
        }
        File(appContext.cacheDir, "picture-${UUID.randomUUID()}").apply { writeBytes(bytes) }
    }

    private fun fetch(url: String, limit: Long, acceptEmpty: Boolean = false): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            // ARASAAC answers a word it has no pictogram for with 404 and an
            // empty body, which is "nothing found" rather than a failure.
            if (code == HttpURLConnection.HTTP_NOT_FOUND && acceptEmpty) return "[]".toByteArray()
            if (code !in 200..299) throw IOException("HTTP $code for $url")
            return connection.inputStream.use { readAtMost(it, limit) }
        } finally {
            connection.disconnect()
        }
    }

    /** Read a reply, refusing one longer than [limit] rather than trusting its length. */
    private fun readAtMost(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("reply too large")
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val USER_AGENT = "Llegeix"
        const val TIMEOUT_MS = 12_000

        /** A page of search results is tens of kilobytes. */
        const val MAX_ANSWER_BYTES = 2L * 1024 * 1024
        const val MAX_THUMBNAIL_BYTES = 2L * 1024 * 1024
        const val MAX_PICTURE_BYTES = 16L * 1024 * 1024

        /** A tile in the row is 88dp, about 260px on a dense screen. */
        const val THUMBNAIL_EDGE = 256
        const val THUMBNAIL_CACHE_BYTES = 8 * 1024 * 1024
    }
}
