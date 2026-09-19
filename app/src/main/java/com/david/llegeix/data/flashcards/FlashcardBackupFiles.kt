package com.david.llegeix.data.flashcards

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A copy of the flashcards as a zip: [FlashcardBackup.JSON_NAME] and the
 * pictures, written to and read from wherever the reader chose.
 *
 * Plain `java.util.zip`, which the platform already carries.
 *
 * ### Reading a file somebody handed us
 *
 * A copy is a file from outside the app, and could be anything with a `.zip`
 * name on it. So reading is deliberately narrow:
 *
 *  - only two kinds of entry are looked at, the JSON and `images/<plain name>`;
 *    everything else in the archive is skipped unread;
 *  - no name from the archive is ever used as a path on the phone. Pictures are
 *    unpacked under names made up here, into a folder made for this one restore,
 *    and the archive's names are only used as keys to match cards to them;
 *  - every entry has a size limit, and so does the whole, so a small file that
 *    unpacks into gigabytes stops at the limit instead of filling the phone;
 *  - each picture is then put through [FlashcardImages.import], exactly as a
 *    photo from the picker is, so what reaches the cards folder is always a
 *    JPEG this app wrote itself.
 */
class FlashcardBackupFiles(context: Context) {

    private val appContext = context.applicationContext

    /** A copy opened up in the cache, until [discard] is called on it. */
    class Unpacked(
        val json: String,
        /** Archive name (`images/…`) to the unpacked file. */
        val images: Map<String, File>,
        internal val folder: File,
    )

    suspend fun write(uri: Uri, json: String, images: Map<String, File>) = withContext(Dispatchers.IO) {
        val out = appContext.contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("cannot write $uri")
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(FlashcardBackup.JSON_NAME))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((name, file) in images) {
                // A picture gone missing costs that card its picture, not the
                // whole copy.
                if (!file.isFile) continue
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Open the copy at [uri] into the cache.
     *
     * Throws [FlashcardBackup.UnreadableException] if there is no flashcards
     * JSON in it at all, and [IOException] if it cannot be read or is too big.
     */
    suspend fun read(uri: Uri): Unpacked = withContext(Dispatchers.IO) {
        val folder = File(appContext.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val input = appContext.contentResolver.openInputStream(uri)
                ?: throw IOException("cannot open $uri")
            var json: String? = null
            val images = LinkedHashMap<String, File>()
            var total = 0L
            var entries = 0
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) throw IOException("too many entries")
                    val name = entry.name
                    when {
                        entry.isDirectory -> Unit

                        name == FlashcardBackup.JSON_NAME -> {
                            val bytes = readAtMost(zip, MAX_JSON_BYTES)
                            total += bytes.size
                            json = bytes.toString(Charsets.UTF_8)
                        }

                        IMAGE_ENTRY.matches(name) && name !in images -> {
                            val file = File(folder, "${images.size}.img")
                            file.outputStream().use { out ->
                                total += copyAtMost(zip, out, MAX_IMAGE_BYTES)
                            }
                            images[name] = file
                        }
                    }
                    if (total > MAX_TOTAL_BYTES) throw IOException("copy too large")
                    zip.closeEntry()
                }
            }
            val text = json ?: throw FlashcardBackup.UnreadableException(
                FlashcardBackup.UnreadableException.Reason.NOT_A_BACKUP,
            )
            Unpacked(text, images, folder)
        } catch (error: Throwable) {
            folder.deleteRecursively()
            throw error
        }
    }

    /** Delete what [read] unpacked. */
    suspend fun discard(unpacked: Unpacked) = withContext(Dispatchers.IO) {
        runCatching { unpacked.folder.deleteRecursively() }
    }

    private fun readAtMost(input: InputStream, limit: Long): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        copyAtMost(input, buffer, limit)
        return buffer.toByteArray()
    }

    /** Copy, and fail rather than carry on past [limit] bytes. Returns how many were copied. */
    private fun copyAtMost(input: InputStream, output: OutputStream, limit: Long): Long {
        val chunk = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val read = input.read(chunk)
            if (read < 0) return copied
            copied += read
            if (copied > limit) throw IOException("entry too large")
            output.write(chunk, 0, read)
        }
    }

    private companion object {
        /**
         * A picture's name as this app writes them. Anything else under
         * `images/` — a subfolder, a `..`, a name with a slash — is ignored.
         */
        val IMAGE_ENTRY = Regex("""images/[A-Za-z0-9_-][A-Za-z0-9._-]{0,127}""")

        /** Tens of thousands of cards' worth of text. */
        const val MAX_JSON_BYTES = 32L * 1024 * 1024

        /** Far above the ~200 KB a stored card picture comes to. */
        const val MAX_IMAGE_BYTES = 16L * 1024 * 1024

        const val MAX_TOTAL_BYTES = 1024L * 1024 * 1024
        const val MAX_ENTRIES = 50_000
    }
}
