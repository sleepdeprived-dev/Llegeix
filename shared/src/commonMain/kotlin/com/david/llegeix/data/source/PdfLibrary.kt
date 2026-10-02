package com.david.llegeix.data.source

import com.david.llegeix.data.model.PdfDocument
import com.david.llegeix.data.model.PdfOrigin
import com.david.llegeix.platform.ContentRef

/**
 * Where the library's PDFs come from, for the screens that show and manage it.
 *
 * The same three ways in on both platforms. Folders the reader chose, kept
 * watched; files chosen one by one; and a sweep of everything, which on the
 * phone is Android's media index behind All Files Access and on the Mac is
 * Spotlight over the reader's home folder. [PdfRepository] on the phone,
 * DesktopPdfLibrary on the Mac.
 */
interface PdfLibrary {

    fun grantedFolders(): List<GrantedFolder>

    /** Whether the system currently allows the whole-device sweep at all. */
    fun isDeviceScanPermitted(): Boolean

    /** Whether the sweep is both permitted and switched on. */
    fun isDeviceScanEnabled(): Boolean

    fun addFolder(folder: ContentRef)

    fun removeFolder(treeUri: String)

    /** Keep a PDF picked file-by-file, including one from a cloud provider. */
    fun addPickedFile(file: ContentRef)

    /**
     * Files picked one by one and still kept. Counted as present even when the
     * scan could not read them — a cloud drive offline for a moment is not the
     * reader removing the file, and must not cost it its place in a collection.
     */
    fun pickedUris(): Set<String>

    /** Every PDF from every source, merged. */
    suspend fun loadLibrary(): LibrarySnapshot
}

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

/** A folder tree the user has granted us persistent read access to. */
data class GrantedFolder(
    /** The grant's handle: a tree Uri on the phone, a folder's file URI on the Mac. */
    val treeUri: String,
    val label: String,
)

/**
 * Collapse files seen by both sources, keeping the granted-folder copy.
 *
 * That preference matters: a SAF document URI keeps working after the user
 * revokes All Files Access, whereas the MediaStore URI for the same file stops
 * resolving. Keeping the more durable handle means turning the device scan off
 * later does not break documents the user has already opened.
 *
 * Top-level rather than a private method so it can be tested directly,
 * without standing up a Context.
 */
fun mergePdfDocuments(
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

/**
 * Providers are inconsistent about MIME types — plenty report
 * `application/octet-stream` for a perfectly good PDF — so the file extension
 * is checked as well.
 */
fun isPdf(displayName: String, mimeType: String?): Boolean =
    mimeType == "application/pdf" || displayName.endsWith(".pdf", ignoreCase = true)
