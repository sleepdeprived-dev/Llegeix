package com.david.llegeix.data.model

import androidx.annotation.StringRes
import com.david.llegeix.R

/** How the library list is ordered. */
enum class LibrarySort(@param:StringRes val labelRes: Int) {
    RECENT(R.string.library_sort_recent),
    NAME(R.string.library_sort_name),
    SIZE(R.string.library_sort_size),
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
