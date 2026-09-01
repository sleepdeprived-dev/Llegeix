package com.david.llegeix.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.david.llegeix.R

/** Official Senyera colours. */
private val SenyeraGold = Color(0xFFFCD116)
private val SenyeraRed = Color(0xFFDA121A)

/**
 * The Senyera, drawn rather than shipped as an asset so it stays crisp at any
 * size and needs no density buckets.
 *
 * Four red stripes on gold: the flag is nine equal horizontal bands, gold first
 * and last, in a 3:2 frame.
 *
 * Sized and shaped to sit next to the app's name rather than to be a flag on a
 * pole. It reads as a small mark beside a title, so it is cap-height rather than
 * the size of a button, more rounded than a real flag, and outlined with the
 * theme's own divider colour instead of a hard black hairline that would be the
 * darkest thing in the app bar.
 */
@Composable
fun Senyera(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.senyera_content_description)
    val outline = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        modifier = modifier
            .size(width = 27.dp, height = 18.dp)
            .clip(RoundedCornerShape(4.dp))
            .semantics { contentDescription = description },
    ) {
        drawRect(color = SenyeraGold, size = size)
        val band = size.height / 9f
        // Bands 1, 3, 5 and 7 of nine are red; the even bands stay gold.
        repeat(4) { stripe ->
            drawRect(
                color = SenyeraRed,
                topLeft = Offset(0f, band * (1 + 2 * stripe)),
                size = Size(size.width, band),
            )
        }
        // Drawn inside the clip so the rounded corners stay clean.
        drawRoundRect(
            color = outline,
            size = size,
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}
