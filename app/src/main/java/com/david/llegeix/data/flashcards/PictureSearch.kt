package com.david.llegeix.data.flashcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * Every source is a public service with no account and no key: ARASAAC's
 * pictograms, published by the Government of Aragón, and for photos both
 * Wikimedia Commons and Openverse, the openly licensed media index run by
 * WordPress. A key shipped inside a sideloaded APK is a key everybody holding
 * the APK has — the same reason the update check reads a public repository — so
 * a service that needed one was never an option, and none of the three is
 * given anything to identify anybody by.
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

    suspend fun search(source: PictureSource, query: String): Outcome =
        withContext(Dispatchers.IO) {
            when (source) {
                PictureSource.PICTOGRAMS -> searchPictograms(query)
                PictureSource.PHOTOS -> searchPhotos(query)
            }
        }

    private suspend fun searchPictograms(query: String): Outcome {
        val body = try {
            fetch(PictureResults.arasaacSearchUrl(query), MAX_ANSWER_BYTES, acceptEmpty = true)
        } catch (error: IOException) {
            return Outcome.Offline
        }
        return Outcome.Found(PictureResults.parseArasaac(body.toString(Charsets.UTF_8)))
    }

    /**
     * Photos from two collections at once, Commons first.
     *
     * Openverse alone was too thin to be much use: an ordinary noun comes back
     * from it with a couple of hundred candidates, and once the safety check
     * has had them the grid is often three photographs, none of them of the
     * thing. Commons is the larger collection by a wide margin and its files
     * are categorised by people, so it leads; Openverse follows, because it
     * indexes Flickr and others that Commons does not have at all.
     *
     * Interleaved rather than concatenated, so a grid of twelve is not eleven
     * from one and one from the other — and asked in parallel, so two services
     * cost one wait. Either failing is not a failure: only both being
     * unreachable is being offline.
     */
    private suspend fun searchPhotos(query: String): Outcome = coroutineScope {
        val commons = async { photosFrom(PictureResults.commonsSearchUrl(query), PictureResults::parseCommons) }
        val openverse = async {
            photosFrom(PictureResults.openverseSearchUrl(query), PictureResults::parseOpenverse)
        }
        val first = commons.await()
        val second = openverse.await()
        if (first == null && second == null) return@coroutineScope Outcome.Offline
        Outcome.Found(interleave(first.orEmpty(), second.orEmpty()).take(PictureResults.LIMIT))
    }

    /** Null when the service could not be reached at all, as against having nothing. */
    private suspend fun photosFrom(url: String, parse: (String) -> List<PictureHit>): List<PictureHit>? =
        try {
            parse(fetch(url, MAX_ANSWER_BYTES, acceptEmpty = true).toString(Charsets.UTF_8))
        } catch (error: IOException) {
            null
        }

    /** One from each in turn, then whatever is left of the longer one. */
    private fun interleave(first: List<PictureHit>, second: List<PictureHit>): List<PictureHit> =
        buildList {
            val rounds = maxOf(first.size, second.size)
            for (index in 0 until rounds) {
                first.getOrNull(index)?.let(::add)
                second.getOrNull(index)?.let(::add)
            }
        }

    /**
     * One word in the three languages a card holds, as ARASAAC's people wrote
     * them; any of the three may be missing.
     */
    data class Labels(val catalan: String?, val romanian: String?, val english: String?)

    /** Looked up once per word per run, since several fields ask. */
    private val labels = LruCache<String, Labels>(300)

    /**
     * The labels of the pictogram labelled with exactly [word] in [from] —
     * "ca", "ro" or "en" — or null when there is none or ARASAAC cannot be
     * reached, in which case the caller asks the translator instead.
     */
    suspend fun labels(word: String, from: String): Labels? {
        val trimmed = word.trim().lowercase()
        if (trimmed.isEmpty()) return null
        val key = "$from:$trimmed"
        labels.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val id = runCatching {
                ArasaacWords.firstExactId(
                    fetch(ArasaacWords.exactSearchUrl(trimmed, from), MAX_ANSWER_BYTES, acceptEmpty = true)
                        .toString(Charsets.UTF_8),
                    trimmed,
                )
            }.getOrNull() ?: return@withContext null
            fun label(language: String): String? = if (language == from) {
                word.trim()
            } else {
                runCatching {
                    ArasaacWords.firstKeyword(
                        fetch(ArasaacWords.pictogramUrl(language, id), MAX_ANSWER_BYTES)
                            .toString(Charsets.UTF_8),
                    )
                }.getOrNull()
            }
            Labels(catalan = label("ca"), romanian = label("ro"), english = label("en"))
                .takeIf { listOfNotNull(it.catalan, it.romanian, it.english).size > 1 }
                ?.also { labels.put(key, it) }
        }
    }

    /** The Romanian and English of a Catalan word; see [labels]. */
    suspend fun meanings(word: String): Labels? = labels(word, from = "ca")

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
