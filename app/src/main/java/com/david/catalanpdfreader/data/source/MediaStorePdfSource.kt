package com.david.catalanpdfreader.data.source

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.david.catalanpdfreader.data.model.PdfDocument
import com.david.catalanpdfreader.data.model.PdfOrigin
import com.david.catalanpdfreader.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Sweeps the whole device for PDFs via MediaStore.
 *
 * This is the optional path. On API 30+ a non-media file type like PDF is only
 * visible in MediaStore to an app holding All Files Access — without it the
 * query still succeeds but returns nothing but files this app itself created,
 * which is why [isAvailable] gates the source rather than letting it silently
 * return an empty list.
 *
 * Note also that the granular READ_MEDIA_* permissions introduced in Android 13
 * are irrelevant here: they cover images, video and audio only, and grant no
 * access to documents.
 */
class MediaStorePdfSource(private val context: Context) : PdfSource {

    override fun isAvailable(): Boolean = Environment.isExternalStorageManager()

    override suspend fun findPdfs(): List<PdfDocument> = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext emptyList()

        // Covers removable volumes (SD cards, USB) as well as internal storage;
        // querying only VOLUME_EXTERNAL_PRIMARY would miss them.
        val volumes = runCatching { MediaStore.getExternalVolumeNames(context) }
            .getOrDefault(setOf(MediaStore.VOLUME_EXTERNAL_PRIMARY))

        volumes.flatMap { volume ->
            runCatchingCancellable { queryVolume(volume) }
                .onFailure { Log.w(TAG, "Could not query volume $volume", it) }
                .getOrDefault(emptyList())
        }
    }

    private suspend fun queryVolume(volumeName: String): List<PdfDocument> {
        val collection = MediaStore.Files.getContentUri(volumeName)
        val results = mutableListOf<PdfDocument>()

        context.contentResolver.query(
            collection,
            PROJECTION,
            SELECTION,
            SELECTION_ARGS,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                coroutineContext.ensureActive()
                val name = cursor.getString(nameIndex) ?: continue
                // The LIKE half of the selection is deliberately loose; re-check
                // so "notes.pdf.bak" and friends do not slip through.
                if (!isPdf(name, cursor.getString(mimeIndex))) continue

                results += PdfDocument(
                    uriString = ContentUris.withAppendedId(collection, cursor.getLong(idIndex)).toString(),
                    displayName = name,
                    sizeBytes = cursor.getLongOrZero(sizeIndex),
                    // MediaStore reports DATE_MODIFIED in seconds, unlike SAF.
                    lastModified = cursor.getLongOrZero(modifiedIndex) * 1_000L,
                    origin = PdfOrigin.DEVICE_SCAN,
                    parentLabel = cursor.getString(pathIndex)?.trimEnd('/'),
                )
            }
        }
        return results
    }

    private fun Cursor.getLongOrZero(index: Int): Long =
        if (isNull(index)) 0L else runCatching { getLong(index) }.getOrDefault(0L)

    private companion object {
        const val TAG = "MediaStorePdfSource"

        val PROJECTION = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.RELATIVE_PATH,
        )

        val SELECTION =
            "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR " +
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"

        val SELECTION_ARGS = arrayOf("application/pdf", "%.pdf")
    }
}
