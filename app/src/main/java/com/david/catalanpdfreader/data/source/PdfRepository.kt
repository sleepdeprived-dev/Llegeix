package com.david.catalanpdfreader.data.source

import android.content.Context
import android.net.Uri
import com.david.catalanpdfreader.data.model.PdfDocument
import com.david.catalanpdfreader.data.model.PdfOrigin
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** The result of one full discovery pass. */
data class LibrarySnapshot(
    val documents: List<PdfDocument> = emptyList(),
    val grantedFolders: List<GrantedFolder> = emptyList(),
    val deviceScanEnabled: Boolean = false,
)

/**
 * Picked files are merged on the folder side because they share the property
 * that matters: an explicitly granted URI outlives All Files Access, so it is
 * the handle worth keeping when the device sweep finds the same file.
 */

/**
 * Single entry point the UI uses to find PDFs, hiding the fact that there are
 * two discovery mechanisms behind it.
 *
 * Feature 3 will add a Room-backed layer alongside this for folders, bookmarks
 * and history; this class stays responsible only for what is physically on the
 * device.
 */
class PdfRepository(
    private val safSource: SafPdfSource,
    private val deviceSource: MediaStorePdfSource,
    private val pickedFileSource: PickedFilePdfSource,
) {

    constructor(context: Context) : this(
        SafPdfSource(context.applicationContext),
        MediaStorePdfSource(context.applicationContext),
        PickedFilePdfSource(context.applicationContext),
    )

    fun grantedFolders(): List<GrantedFolder> = safSource.grantedFolders()

    fun isDeviceScanEnabled(): Boolean = deviceSource.isAvailable()

    fun addFolder(treeUri: Uri) = safSource.persistGrant(treeUri)

    fun removeFolder(treeUri: Uri) = safSource.releaseGrant(treeUri)

    /** Keep a PDF picked file-by-file, including one from a cloud provider. */
    fun addPickedFile(uri: Uri) = pickedFileSource.persistGrant(uri)

    fun removePickedFile(uri: Uri) = pickedFileSource.releaseGrant(uri)

    /**
     * Run both sources and merge them. The two run concurrently because the
     * device sweep is by far the slower of the pair and there is no reason to
     * make the folder walk wait behind it.
     */
    suspend fun loadLibrary(): LibrarySnapshot = coroutineScope {
        val fromFolders = async { safSource.findPdfs() }
        val fromDevice = async { deviceSource.findPdfs() }
        val fromPicked = async { pickedFileSource.findPdfs() }

        val merged = mergePdfDocuments(
            fromFolders.await() + fromPicked.await(),
            fromDevice.await(),
        )
        LibrarySnapshot(
            documents = merged,
            grantedFolders = safSource.grantedFolders(),
            deviceScanEnabled = deviceSource.isAvailable(),
        )
    }
}

/**
 * Collapse files seen by both sources, keeping the granted-folder copy.
 *
 * That preference matters: a SAF document URI keeps working after the user
 * revokes All Files Access, whereas the MediaStore URI for the same file stops
 * resolving. Keeping the more durable handle means turning the device scan off
 * later does not break documents the user has already opened.
 *
 * Top-level and internal rather than a private method so it can be tested
 * directly, without standing up a Context.
 */
internal fun mergePdfDocuments(
    fromFolders: List<PdfDocument>,
    fromDevice: List<PdfDocument>,
): List<PdfDocument> {
    val byKey = LinkedHashMap<String, PdfDocument>(fromFolders.size + fromDevice.size)
    for (document in fromFolders) {
        byKey[document.dedupeKey] = document
    }
    for (document in fromDevice) {
        val existing = byKey[document.dedupeKey]
        if (existing == null || existing.origin != PdfOrigin.GRANTED_FOLDER) {
            byKey[document.dedupeKey] = document
        }
    }
    return byKey.values.toList()
}
