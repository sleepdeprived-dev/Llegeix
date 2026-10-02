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
