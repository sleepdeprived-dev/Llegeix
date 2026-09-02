package com.david.llegeix.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.data.source.LibraryDataRepository
import com.david.llegeix.pdf.PdfThumbnails
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Puts the app back to the state it was in before it was ever opened.
 *
 * There is a reason this exists rather than pointing at Android's own "clear
 * storage", and it was worth measuring rather than guessing. Clearing storage
 * *does* empty the database and hand back the folder grants — that much was
 * checked on a device. What it does not touch is **all-files access**, because
 * that is a permission rather than data. So the app comes back, still allowed
 * to sweep the whole phone, does exactly that, and finds every PDF again. The
 * library looks untouched even though nothing of it was kept.
 *
 * This wipe therefore does the one thing the system button cannot: it stops the
 * app using that permission. An app cannot revoke a special permission for
 * itself, so instead the sweep is switched off in the app's own settings and
 * stays off until the reader turns it back on in Fonts. That is the difference
 * between a library that empties and a library that empties for two seconds.
 *
 * The downloaded translation models go too. They are by far the largest thing
 * the app puts on disk, and nothing else offers a way to remove them.
 */
class DataEraser(
    private val context: Context,
    private val libraryData: LibraryDataRepository,
    private val settings: SettingsRepository,
    private val thumbnails: PdfThumbnails,
) {

    /**
     * Erase everything, in the order that leaves nothing behind on a failure.
     *
     * Grants go first: if anything later throws, the app has at least stopped
     * being able to see the reader's files, which is the half of this that
     * matters most.
     */
    suspend fun eraseEverything() = withContext(Dispatchers.IO) {
        releaseAllUriPermissions()
        runCatching { deleteTranslationModels() }
        runCatching { libraryData.eraseEverything() }
        thumbnails.clear()
        runCatching { context.cacheDir.deleteRecursively() }
        settings.resetToDefaults()
        // Last, and after the reset so it is not undone by it: without this the
        // whole-device sweep refills the library seconds after it was emptied.
        settings.setDeviceScanOptOut(true)
    }

    /**
     * Hand back every folder and file the reader ever granted.
     *
     * Both kinds are released the same way; the flags a grant was taken with
     * are not recorded anywhere, so read-and-write is tried first and read
     * alone after it.
     */
    private fun releaseAllUriPermissions() {
        val resolver = context.contentResolver
        val readWrite = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        for (permission in resolver.persistedUriPermissions) {
            releaseQuietly(resolver, permission.uri, readWrite)
        }
        // A second pass: a grant taken read-only is not released by a call that
        // names the write flag as well.
        for (permission in resolver.persistedUriPermissions) {
            releaseQuietly(resolver, permission.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun releaseQuietly(resolver: ContentResolver, uri: android.net.Uri, flags: Int) {
        runCatching { resolver.releasePersistableUriPermission(uri, flags) }
    }

    /** Delete the on-device translation models, tens of megabytes apiece. */
    private suspend fun deleteTranslationModels() {
        val manager = RemoteModelManager.getInstance()
        val models = manager.getDownloadedModels(TranslateRemoteModel::class.java).await()
        for (model in models) {
            runCatching { manager.deleteDownloadedModel(model).await() }
        }
    }
}

/** Bridges a Play Services [com.google.android.gms.tasks.Task] to a coroutine. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            val error = task.exception
            if (error != null) {
                continuation.cancel(error)
            } else {
                @Suppress("UNCHECKED_CAST")
                continuation.resume(task.result as T)
            }
        }
    }
