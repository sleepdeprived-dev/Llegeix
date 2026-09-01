package com.david.llegeix.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.david.llegeix.LlegeixApp
import com.david.llegeix.R

/**
 * The first page of a PDF, shown as its cover.
 *
 * Falls back to a lettered tile — never a blank box — because a scanned or
 * broken document should still look like a document in the list. The crossfade
 * matters more than it sounds: covers arrive at unpredictable moments while
 * scrolling, and popping them in makes a quiet list feel busy.
 */
@Composable
fun PdfCover(
    uriString: String,
    width: Dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
) {
    val context = LocalContext.current
    val thumbnails = remember(context) {
        (context.applicationContext as LlegeixApp).pdfThumbnails
    }
    val widthPx = with(LocalDensity.current) { width.roundToPx() }

    // Seeded from the cache so an already-rendered cover paints on the first
    // frame instead of fading in again every time the row scrolls back.
    val bitmap by produceState(
        initialValue = thumbnails.cached(uriString, widthPx),
        uriString,
        widthPx,
    ) {
        if (value == null) value = thumbnails.load(uriString, widthPx)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(cornerRadius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(targetState = bitmap, label = "cover") { rendered ->
            if (rendered != null) {
                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.pdf_badge),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Covers are drawn at the proportions of an unopened A4 page. */
val CoverAspectRatio: Float = 1f / 1.35f

/**
 * An emoji used as a menu item's leading icon.
 *
 * Menus in this app are lists of similar-length phrases, which are slow to scan
 * when every line looks the same. A glyph per action gives each one a shape you
 * can aim at without reading it.
 */
@Composable
fun MenuEmoji(emoji: String) {
    Text(text = emoji, fontSize = 18.sp)
}
