package com.david.catalanpdfreader.ui.library

import com.david.catalanpdfreader.data.model.LibrarySort
import com.david.catalanpdfreader.data.model.PdfDocument
import com.david.catalanpdfreader.data.source.GrantedFolder

/** Which subset of the library is on screen. */
enum class LibraryFilter(val label: String) {
    ALL("All"),
    READ_LATER("Read later"),
}

data class LibraryUiState(
    val isScanning: Boolean = false,
    /** Already filtered by [query] and [filter], and ordered by [sort]. */
    val documents: List<PdfDocument> = emptyList(),
    /** Total found before filtering, so the UI can say "3 of 41". */
    val totalFound: Int = 0,
    val grantedFolders: List<GrantedFolder> = emptyList(),
    val deviceScanEnabled: Boolean = false,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val filter: LibraryFilter = LibraryFilter.ALL,
    /** URIs of documents flagged to read later, for the row toggles. */
    val readLaterUris: Set<String> = emptySet(),
    /** URIs of documents bookmarked as a whole. */
    val bookmarkedUris: Set<String> = emptySet(),
    /** False until the first scan finishes, to tell "empty" from "not looked yet". */
    val hasScanned: Boolean = false,
    val errorMessage: String? = null,
) {
    /** True when at least one discovery path is set up. */
    val hasAnySource: Boolean
        get() = grantedFolders.isNotEmpty() || deviceScanEnabled

    /** Sources exist, the scan ran, and it genuinely found nothing. */
    val isEmptyAfterScan: Boolean
        get() = hasAnySource && hasScanned && totalFound == 0 && !isScanning

    /** Documents exist but the search or filter excluded all of them. */
    val isFilteredToNothing: Boolean
        get() = totalFound > 0 && documents.isEmpty()

    fun isReadLater(document: PdfDocument): Boolean = document.uriString in readLaterUris

    fun isBookmarked(document: PdfDocument): Boolean = document.uriString in bookmarkedUris
}
