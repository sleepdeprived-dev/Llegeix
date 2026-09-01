package com.david.catalanpdfreader.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Official Senyera colours. */
private val SenyeraGold = Color(0xFFFCDD09)
private val SenyeraRed = Color(0xFFDA121A)

/**
 * The Senyera, drawn rather than shipped as an asset so it stays crisp at any
 * size and needs no density buckets.
 *
 * Four red stripes on gold: the flag is nine equal horizontal bands, gold first
 * and last, in a 3:2 frame.
 */
@Composable
fun Senyera(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .size(width = 42.dp, height = 28.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(
                width = 1.dp,
                color = Color.Black.copy(alpha = 0.15f),
                shape = RoundedCornerShape(2.dp),
            )
            .semantics { contentDescription = "Senyera, the Catalan flag" },
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
    }
}
