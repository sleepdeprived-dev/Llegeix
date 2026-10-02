package com.david.llegeix.data.model

import android.net.Uri
import androidx.core.net.toUri

/** The document's handle as the Android Uri the phone's providers and renderer take. */
val PdfDocument.uri: Uri get() = uriString.toUri()
