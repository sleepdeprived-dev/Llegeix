package com.david.llegeix.util

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter

/** "1.4 MB", localised. Blank when the provider reported no size. */
fun formatSize(context: Context, bytes: Long): String =
    if (bytes <= 0L) "" else Formatter.formatShortFileSize(context, bytes)

/** "Yesterday", "12 Mar" — blank when the provider reported no timestamp. */
fun formatModified(millis: Long): String =
    if (millis <= 0L) {
        ""
    } else {
        DateUtils.getRelativeTimeSpanString(
            millis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
    }

/**
 * Filename to display title: "El Petit Princep.pdf" becomes "El Petit Princep".
 *
 * Kept in one place because the database stores the real filename — the column
 * is called displayName and should mean it — while every screen shows the
 * trimmed form.
 */
fun pdfTitle(displayName: String): String =
    displayName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { displayName }
