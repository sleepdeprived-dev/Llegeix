package com.david.catalanpdfreader.data.source

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import com.david.catalanpdfreader.data.model.PdfDocument
import com.david.catalanpdfreader.data.model.PdfOrigin
import com.david.catalanpdfreader.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDFs the user picked individually from the document picker.
 *
 * This exists because folder walking cannot reach everything. A cloud provider
 * like Proton Drive exposes its files through a DocumentsProvider but has no
 * local directory tree to enumerate, and providers commonly decline
 * `ACTION_OPEN_DOCUMENT_TREE` entirely while still supporting single-document
 * picking. Picking files also lets the picker filter by MIME type, so the user
 * only ever sees PDFs — something the folder picker cannot do, since it is
 * choosing a directory rather than a file.
 *
 * As with folders, the system itself remembers the grants, so nothing is stored
 * here.
 */
class PickedFilePdfSource(private val context: Context) : PdfSource {

    private val resolver get() = context.contentResolver

    /** Individually granted documents, i.e. every persisted grant that is not a tree. */
    private fun grantedDocuments(): List<Uri> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && !DocumentsContract.isTreeUri(it.uri) }
            .map { it.uri }

    override fun isAvailable(): Boolean = grantedDocuments().isNotEmpty()

    /** Keep access to a picked document across reboots. */
    fun persistGrant(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.onFailure { Log.w(TAG, "Could not persist access to $uri", it) }
    }

    fun releaseGrant(uri: Uri) {
        runCatching {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override suspend fun findPdfs(): List<PdfDocument> = withContext(Dispatchers.IO) {
        grantedDocuments().mapNotNull { uri ->
            runCatchingCancellable { describe(uri) }
                .onFailure { Log.w(TAG, "Could not read $uri", it) }
                .getOrNull()
        }
    }

    /**
     * Read a picked document's metadata.
     *
     * Returns null when the provider no longer resolves it — a file deleted in
     * Proton Drive, or an account signed out — so a stale grant quietly drops
     * out of the library instead of breaking the whole scan.
     */
    private fun describe(uri: Uri): PdfDocument? {
        val projection = arrayOf(
            OpenableColumns.DISPLAY_NAME,
            OpenableColumns.SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null

            val name = cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: return@use null
            // A provider is free to report a vague MIME type, so the extension
            // is the backstop — same rule the other sources use.
            if (!isPdf(name, resolver.getType(uri))) return@use null

            PdfDocument(
                uriString = uri.toString(),
                displayName = name,
                sizeBytes = cursor.getLongOrZero(OpenableColumns.SIZE),
                lastModified = cursor.getLongOrZero(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                origin = PdfOrigin.PICKED_FILE,
                parentLabel = providerLabel(uri),
            )
        }
    }

    /**
     * A human name for wherever the file came from, e.g. "Proton Drive".
     *
     * Resolved from the authority's own app label, so the library shows the
     * source the user recognises instead of a content:// authority string.
     */
    private fun providerLabel(uri: Uri): String? {
        val authority = uri.authority ?: return null
        return runCatching {
            val packageManager = context.packageManager
            val provider = packageManager.resolveContentProvider(authority, 0) ?: return null
            provider.loadLabel(packageManager).toString().takeIf { it.isNotBlank() }
                ?: packageManager.getApplicationLabel(provider.applicationInfo).toString()
        }.getOrNull()
    }

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) null else getString(index)
    }

    private fun Cursor.getLongOrZero(column: String): Long {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) 0L else runCatching { getLong(index) }.getOrDefault(0L)
    }

    private companion object {
        const val TAG = "PickedFilePdfSource"
    }
}
