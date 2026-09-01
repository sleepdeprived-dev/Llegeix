package com.david.catalanpdfreader.data.model

/** How the library list is ordered. */
enum class LibrarySort(val label: String) {
    RECENT("Recently modified"),
    NAME("Name"),
    SIZE("Size"),
    ;

    fun comparator(): Comparator<PdfDocument> = when (this) {
        RECENT -> compareByDescending<PdfDocument> { it.lastModified }
            .thenBy { it.displayName.lowercase() }

        NAME -> compareBy<PdfDocument> { it.displayName.lowercase() }
            .thenByDescending { it.lastModified }

        SIZE -> compareByDescending<PdfDocument> { it.sizeBytes }
            .thenBy { it.displayName.lowercase() }
    }
}
