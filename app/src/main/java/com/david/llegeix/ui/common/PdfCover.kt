package com.david.llegeix.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.david.llegeix.LlegeixApp
import com.david.llegeix.resources.*
import org.jetbrains.compose.resources.stringResource

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
    /**
     * How far through the document the reader is, 0..1, or null if unknown.
     *
     * Drawn as a bar along the foot of the cover rather than as a number
     * beside the title. Progress is the one fact about a book that wants no
     * reading at all — a glance down a shelf should say which ones are started
     * and how far, and a column of percentages is a column to be read.
     */
    progress: Float? = null,
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
                        text = stringResource(Res.string.pdf_badge),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Over the cover, at its foot, on a track dark enough to read against
        // whatever the page underneath happens to be. A bar beside the cover
        // would cost the row a line of height on every document, including the
        // ones nobody has opened.
        if (progress != null && progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(CoverProgressHeight)
                    .background(Color.Black.copy(alpha = 0.35f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * Thickness of the bar across a cover.
 *
 * Deliberately the same on a 46dp list cover and a 150dp grid cover: it is a
 * mark, not a measurement, and scaling it with the cover would make the small
 * one invisible and the large one a stripe.
 */
private val CoverProgressHeight = 3.dp

/** Covers are drawn at the proportions of an unopened A4 page. */
val CoverAspectRatio: Float = 1f / 1.35f
