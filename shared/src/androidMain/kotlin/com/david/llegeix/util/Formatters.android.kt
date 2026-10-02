package com.david.llegeix.util

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun formatSize(bytes: Long): String =
    if (bytes <= 0L) "" else Formatter.formatShortFileSize(LocalContext.current, bytes)

actual fun formatModified(millis: Long): String =
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
