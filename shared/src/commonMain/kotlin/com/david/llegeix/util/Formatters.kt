package com.david.llegeix.util

import androidx.compose.runtime.Composable

/** "1,4 MB", localised. Blank when the provider reported no size. */
@Composable
expect fun formatSize(bytes: Long): String

/** "Ahir", "12 de març" — blank when the provider reported no timestamp. */
expect fun formatModified(millis: Long): String
