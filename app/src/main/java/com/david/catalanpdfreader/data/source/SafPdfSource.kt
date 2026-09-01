package com.david.catalanpdfreader.data.source

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.david.catalanpdfreader.data.model.PdfDocument
import com.david.catalanpdfreader.data.model.PdfOrigin
import com.david.catalanpdfreader.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** A folder tree the user has granted us persistent read access to. */
data class GrantedFolder(
    val treeUri: Uri,
    val label: String,
)

/**
 * Finds PDFs under folder trees the user picked with the SAF folder picker.
 *
 * This is the default discovery path and needs no manifest permission. Grants
 * are persisted by the system itself — [android.content.ContentResolver.getPersistedUriPermissions]
 * is the source of truth and survives reboot — so the app stores no state of
 * its own about which folders were chosen.
 */
class SafPdfSource(private val context: Context) : PdfSource {

    private val resolver get() = context.contentResolver

    /**
     * Every folder tree we currently hold persisted read access to.
     *
     * The tree-URI filter matters: persisted permissions also include individual
     * documents picked one at a time (see PickedFilePdfSource), and feeding one
     * of those to the tree APIs throws rather than returning nothing.
     */
    fun grantedFolders(): List<GrantedFolder> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
            .map { GrantedFolder(it.uri, labelForTree(it.uri)) }
            .sortedBy { it.label.lowercase() }

    override fun isAvailable(): Boolean = grantedFolders().isNotEmpty()

    /**
     * Convert the picker's result into a permanent grant. Safe to call with a
     * tree that is already granted.
     */
    fun persistGrant(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Some providers only offer read; asking for write as well would throw.
        runCatching { resolver.takePersistableUriPermission(treeUri, flags) }
            .onFailure {
                resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }

    /** Give a folder back. The documents under it stop being readable. */
    fun releaseGrant(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.releasePersistableUriPermission(treeUri, flags) }
    }

    override suspend fun findPdfs(): List<PdfDocument> = withContext(Dispatchers.IO) {
        grantedFolders().flatMap { folder ->
            // A grant can be revoked between listing and walking it, and a
            // provider (e.g. a disconnected USB drive) can simply be gone.
            runCatchingCancellable { walkTree(folder) }
                .onFailure { Log.w(TAG, "Could not walk ${folder.label}", it) }
                .getOrDefault(emptyList())
        }
    }

    /**
     * Breadth-first walk of one granted tree.
     *
     * Uses [DocumentsContract] directly rather than `DocumentFile`, which issues
     * a separate provider query per attribute per file and is dramatically
     * slower over a few hundred documents.
     */
    private suspend fun walkTree(folder: GrantedFolder): List<PdfDocument> {
        val treeUri = folder.treeUri
        val results = mutableListOf<PdfDocument>()
        val visited = mutableSetOf<String>()

        // Each queue entry is a directory: its document id and display path.
        val queue = ArrayDeque<Pair<String, String>>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        queue.add(rootId to folder.label)
        visited.add(rootId)

        var directoriesVisited = 0
        while (queue.isNotEmpty() && directoriesVisited < MAX_DIRECTORIES) {
            coroutineContext.ensureActive()
            val (parentId, parentPath) = queue.removeFirst()
            directoriesVisited++

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            resolver.query(childrenUri, CHILD_PROJECTION, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex) ?: continue
                    val name = cursor.getString(nameIndex) ?: continue
                    val mime = cursor.getString(mimeIndex).orEmpty()

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        // visited guards against providers that expose the same
                        // document under two parents, which would loop forever.
                        if (visited.add(documentId)) {
                            queue.add(documentId to "$parentPath/$name")
                        }
                    } else if (isPdf(name, mime)) {
                        results += PdfDocument(
                            uriString = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId).toString(),
                            displayName = name,
                            sizeBytes = cursor.getLongOrZero(sizeIndex),
                            lastModified = cursor.getLongOrZero(modifiedIndex),
                            origin = PdfOrigin.GRANTED_FOLDER,
                            parentLabel = parentPath,
                        )
                    }
                }
            }
        }
        if (queue.isNotEmpty()) {
            Log.w(TAG, "Stopped walking ${folder.label} at $MAX_DIRECTORIES directories")
        }
        return results
    }

    /** Best-effort display name for a tree, falling back to its document id. */
    private fun labelForTree(treeUri: Uri): String {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return treeUri.lastPathSegment.orEmpty()
        val documentUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        }.getOrNull() ?: return documentId.substringAfterLast(':')

        val name = runCatching {
            resolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()

        // ":" separates the volume from the path in a document id, e.g.
        // "primary:Documents/Catalan" -> "Documents/Catalan".
        return name ?: documentId.substringAfterLast(':').ifBlank { documentId }
    }

    private fun Cursor.getLongOrZero(index: Int): Long =
        if (isNull(index)) 0L else runCatching { getLong(index) }.getOrDefault(0L)

    private companion object {
        const val TAG = "SafPdfSource"

        /**
         * Depth is unbounded but total directory count is not: a user who grants
         * the whole internal volume should not be able to hang the scan.
         */
        const val MAX_DIRECTORIES = 5_000

        val CHILD_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
    }
}

/**
 * Providers are inconsistent about MIME types — plenty report
 * `application/octet-stream` for a perfectly good PDF — so the file extension
 * is checked as well.
 */
internal fun isPdf(displayName: String, mimeType: String?): Boolean =
    mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
