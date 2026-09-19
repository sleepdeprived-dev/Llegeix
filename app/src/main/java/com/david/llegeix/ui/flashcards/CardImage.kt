package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.david.llegeix.LlegeixApp

/**
 * A card's picture, read off the main thread at about the size it is drawn.
 *
 * Holds its space with a quiet fill while the file is read, so a list of cards
 * does not jump as their pictures arrive one by one.
 *
 * ### Pictograms
 *
 * ARASAAC's pictograms are drawn black on white. Shown as they are, each one is
 * a white square dropped onto the app — glaring in the dark theme, and framed
 * by grey bars wherever the space is not square. Taking the white away is no
 * answer either: the outlines are black, and on a dark card they would vanish.
 * So the white is *recoloured* instead, to a pale tint of the reader's own
 * accent, by multiplying the picture with it. White becomes the tint, black
 * stays black, and the colours in between shift by a shade. The square behind
 * is filled with the same tint, so a pictogram reads as a card of the app's
 * own, in whichever colour the reader chose.
 *
 * @param maxEdge roughly how many pixels across it is drawn at. A thumbnail
 *   asks for a fraction of the stored picture, and gets it decoded that small.
 * @param pictogram draw it as a pictogram, as above.
 */
@Composable
fun CardImage(
    path: String,
    maxEdge: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    pictogram: Boolean = false,
) {
    val bitmap = rememberCardBitmap(path, maxEdge)
    val tint = pictogramTint()
    Box(
        modifier = modifier.background(
            if (pictogram) tint else MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                // A pictogram is always shown whole, with a little air: it is a
                // drawing of one thing, and cropping cuts the thing.
                contentScale = if (pictogram) ContentScale.Fit else contentScale,
                colorFilter = if (pictogram) ColorFilter.tint(tint, BlendMode.Multiply) else null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (pictogram) PictogramInset else 0.dp),
            )
        }
    }
}

/**
 * A picture shown large — on a study card, or in the card form — framed to its
 * own shape.
 *
 * The frame takes the picture's proportions and the largest size that fits the
 * space, rather than the space's proportions with the picture fitted inside: a
 * square pictogram in a wide box used to leave grey bars down both sides, which
 * read as a picture that did not fit. A soft shadow lifts it off the card.
 */
@Composable
fun FramedPicture(
    path: String,
    contentDescription: String?,
    pictogram: Boolean,
    modifier: Modifier = Modifier,
    maxEdge: Int = 1024,
) {
    val bitmap = rememberCardBitmap(path, maxEdge)
    val ratio = bitmap?.let { it.width.toFloat() / it.height }?.takeIf { it > 0f } ?: 1f
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // As large as fits, at the picture's own proportions.
        val byWidth = maxWidth / ratio <= maxHeight
        val frame = if (byWidth) Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = false)
        else Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
        val tint = pictogramTint()
        Box(
            modifier = frame
                .shadow(elevation = 3.dp, shape = FrameShape)
                .clip(FrameShape)
                .background(if (pictogram) tint else MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (pictogram) ColorFilter.tint(tint, BlendMode.Multiply) else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (pictogram) PictogramInset * 2 else 0.dp),
                )
            }
        }
    }
}

/**
 * The paper a pictogram is printed on: white warmed by a little of the accent.
 *
 * Kept pale in both themes, because the drawings are black-outlined; on a dark
 * theme it is a touch dimmer and a touch more coloured, so it sits on the card
 * as a lit tile rather than a hole cut in it.
 */
@Composable
fun pictogramTint(): Color {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    return remember(scheme.primary, isDark) {
        if (isDark) lerp(Color(0xFFEDEAF0), scheme.primary, 0.16f)
        else lerp(Color.White, scheme.primary, 0.07f)
    }
}

@Composable
private fun rememberCardBitmap(path: String, maxEdge: Int): ImageBitmap? {
    val context = LocalContext.current
    val flashcards = remember(context) {
        (context.applicationContext as LlegeixApp).flashcardRepository
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxEdge) {
        value = flashcards.loadImage(path, maxEdge)?.asImageBitmap()
    }
    return bitmap
}

private val FrameShape = RoundedCornerShape(20.dp)

private val PictogramInset: Dp = 4.dp
