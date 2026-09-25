package com.david.llegeix.data.flashcards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 * Every source is a public service with no account and no key: for pictograms
 * ARASAAC, published by the Government of Aragón, Global Symbols, an index over
 * three dozen other freely licensed symbol sets, and OpenMoji's open emoji; for
 * photographs Wikimedia Commons, Wikipedia's article pictures, and Openverse,
 * the openly licensed media index run by WordPress. A key shipped inside a sideloaded APK is a key everybody holding
 * the APK has — the same reason the update check reads a public repository — so
 * a service that needed one was never an option, and none of them is
 * given anything to identify anybody by.
 *
 * ### What leaves the phone
 *
 * The word being written on a card, and its English translation made on the
 * phone first, and nothing else: no identifier, no account, nothing about the
 * reader. This only happens while a card is open
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

    /**
     * Pictures of a word.
     *
     * @param catalan the word as it is on the card.
     * @param english its English, if one could be had. Photos need it; for
     *   pictograms it only widens the search, to the sets labelled in English.
     */
    suspend fun search(source: PictureSource, catalan: String, english: String?): Outcome =
        withContext(Dispatchers.IO) {
            when (source) {
                PictureSource.PICTOGRAMS -> searchPictograms(catalan, english)
                PictureSource.PHOTOS -> searchPhotos(catalan, english ?: catalan)
            }
        }

    /**
     * Pictograms from every shelf at once, ARASAAC's first.
     *
     * ARASAAC leads because it is the set this app was built around: it is
     * labelled in Catalan by people and its drawings are consistent with one
     * another. Global Symbols follows, an index over some three dozen other
     * freely licensed sets, worth having for exactly the words ARASAAC has not
     * drawn. Both are asked again in English when there is an English word,
     * since far more of their pictograms are labelled in English than in
     * Catalan, and OpenMoji's flat drawings come last.
     *
     * All asked in parallel and interleaved, so every shelf costs one wait and
     * the first rows of the grid are not all from one of them.
     */
    private suspend fun searchPictograms(catalan: String, english: String?): Outcome = coroutineScope {
        val sets = async { globalSymbolSets() }
        fun globalSymbols(word: String, language: String) = async {
            val names = sets.await()
            hitsFrom(PictureResults.globalSymbolsSearchUrl(word, language)) {
                // Global Symbols carries ARASAAC too, at other addresses, so the
                // duplicate check cannot see they are the same drawing. ARASAAC
                // is asked directly, so its copy here is left out.
                PictureResults.parseGlobalSymbols(it, names, query = word)
                    .filterNot { hit -> hit.credit.contains("ARASAAC") }
            }
        }
        val shelves = buildList {
            add(async { hitsFrom(PictureResults.arasaacSearchUrl(catalan), PictureResults::parseArasaac) })
            add(globalSymbols(catalan, "cat"))
            if (english != null) {
                add(async { hitsFrom(PictureResults.arasaacSearchUrl(english, "en"), PictureResults::parseArasaac) })
                add(globalSymbols(english, "eng"))
                add(async { openMoji()?.let { PictureResults.openMojiMatches(it, english) } })
            }
        }
        found(shelves.map { it.await() })
    }

    /**
     * Photos from every collection at once, Commons first.
     *
     * Commons is the largest by a wide margin and its files are categorised by
     * people, so it leads. Wikipedia's article pictures come next — the one
     * picture people chose to show what a word is — asked in Catalan with the
     * card's own word and in English with its translation. Openverse follows,
     * for Flickr and the others Commons does not have.
     */
    private suspend fun searchPhotos(catalan: String, english: String): Outcome = coroutineScope {
        val shelves = buildList {
            add(async { hitsFrom(PictureResults.commonsSearchUrl(english), PictureResults::parseCommons) })
            add(async { hitsFrom(PictureResults.wikipediaSearchUrl("en", english), PictureResults::parseWikipedia) })
            add(async { hitsFrom(PictureResults.wikipediaSearchUrl("ca", catalan), PictureResults::parseWikipedia) })
            for (page in 1..PictureResults.OPENVERSE_PAGES) {
                add(async {
                    hitsFrom(PictureResults.openverseSearchUrl(english, page), PictureResults::parseOpenverse)
                })
            }
        }
        found(shelves.map { it.await() })
    }

    /** Only every shelf being unreachable is being offline; one failing is one fewer shelf. */
    private fun found(shelves: List<List<PictureHit>?>): Outcome =
        if (shelves.all { it == null }) Outcome.Offline else Outcome.Found(merge(shelves.map { it.orEmpty() }))

    /** Null when the service could not be reached at all, as against having nothing. */
    private suspend fun hitsFrom(
        url: String,
        parse: (String) -> List<PictureHit>,
    ): List<PictureHit>? =
        try {
            parse(fetch(url, MAX_ANSWER_BYTES, acceptEmpty = true).toString(Charsets.UTF_8))
        } catch (error: IOException) {
            null
        }

    /**
     * One from each shelf in turn, then whatever is left of the longer ones,
     * with the same picture never offered twice.
     *
     * The duplicate check matters because the shelves overlap: Global Symbols
     * indexes ARASAAC, Openverse indexes Commons, and Wikipedia's pictures are
     * Commons files, so without it a grid could be half pairs of the same one.
     */
    private fun merge(shelves: List<List<PictureHit>>): List<PictureHit> {
        val seenUrls = HashSet<String>()
        val seenIds = HashSet<String>()
        return buildList {
            val rounds = shelves.maxOfOrNull { it.size } ?: 0
            for (index in 0 until rounds) {
                for (shelf in shelves) {
                    val hit = shelf.getOrNull(index) ?: continue
                    if (seenUrls.add(hit.fullUrl) and seenIds.add(hit.id)) add(hit)
                }
            }
        }
    }

    /** The OpenMoji catalogue, fetched once per run; null if it could not be. */
    private var openMojiIndex: List<PictureResults.OpenMoji>? = null

    private fun openMoji(): List<PictureResults.OpenMoji>? {
        openMojiIndex?.let { return it }
        val fetched = try {
            PictureResults.parseOpenMojiIndex(
                fetch(PictureResults.OPENMOJI_DATA_URL, MAX_CATALOGUE_BYTES).toString(Charsets.UTF_8),
            )
        } catch (error: IOException) {
            return null
        }
        if (fetched.isNotEmpty()) openMojiIndex = fetched
        return fetched
    }

    /**
     * What each Global Symbols set is called and published under, fetched once
     * per run of the app.
     *
     * A credit that named no set would be no credit at all — these are three
     * dozen collections under half a dozen licences — and the search itself
     * does not carry the names. It is one small request, it is shared by every
     * pictogram search afterwards, and failing it costs the credit rather than
     * the pictures.
     */
    private var symbolSets: Map<Int, String>? = null

    private suspend fun globalSymbolSets(): Map<Int, String> {
        symbolSets?.let { return it }
        val fetched = try {
            PictureResults.parseGlobalSymbolsSets(
                fetch(PictureResults.GLOBAL_SYMBOLS_SETS_URL, MAX_ANSWER_BYTES, acceptEmpty = true)
                    .toString(Charsets.UTF_8),
            )
        } catch (error: IOException) {
            emptyMap()
        }
        // Only a real answer is kept, so a request that failed on a dead
        // connection is tried again on the next search rather than leaving
        // every pictogram uncredited for the rest of the run.
        if (fetched.isNotEmpty()) symbolSets = fetched
        return fetched
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

    /**
     * At most this many thumbnails on their way at once.
     *
     * The grid now shows everything that was found, which can be a hundred
     * pictures and more, and every tile asks for its thumbnail the moment it
     * is drawn. Unthrottled that is a hundred connections opened together, to
     * servers that answer a crowd like that by refusing some of it.
     */
    private val thumbnailGate = Semaphore(THUMBNAILS_AT_ONCE)

    /** A thumbnail, or null if it could not be fetched; the row leaves a blank tile. */
    suspend fun thumbnail(url: String): Bitmap? {
        thumbnails.get(url)?.let { return it }
        return thumbnailGate.withPermit { withContext(Dispatchers.IO) {
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
        } }
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

        /** OpenMoji's catalogue is one file of about two megabytes. */
        const val MAX_CATALOGUE_BYTES = 6L * 1024 * 1024

        const val THUMBNAILS_AT_ONCE = 8

        /** A tile in the row is 88dp, about 260px on a dense screen. */
        const val THUMBNAIL_EDGE = 256
        /**
         * Room for a full grid of both kinds.
         *
         * Too small and flicking between Pictograms and Photos evicts the one
         * being returned to, so every tile reloads over the network as the
         * grid is scrolled back up. Now that the grid shows everything found
         * — often past a hundred pictures — it needs the room to match.
         */
        const val THUMBNAIL_CACHE_BYTES = 48 * 1024 * 1024
    }
}
