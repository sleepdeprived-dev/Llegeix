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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import com.david.llegeix.data.flashcards.PictureKind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.david.llegeix.platform.Services

/**
 * A card's picture, read off the main thread at about the size it is drawn.
 *
 * Holds its space with a quiet fill while the file is read, so a list of cards
 * does not jump as their pictures arrive one by one.
 *
 * ### Pictures as they are
 *
 * A picture is shown exactly as it was stored, background and all. The white
 * paper under a pictogram used to be cut away and a colour of the theme put in
 * its place, and on real pictures that did more harm than good: anything
 * white or pale inside the drawing — a plate, a cloud, the whites of an eye —
 * went with the paper. A pictogram now keeps its own white square.
 *
 * @param maxEdge roughly how many pixels across it is drawn at. A thumbnail
 *   asks for a fraction of the stored picture, and gets it decoded that small.
 * An emoji is drawn with nothing behind it at all, whole and as large as its
 * frame allows, since it is a transparent picture made to stand on anything.
 *
 * @param kind how to draw it; see [PictureKind].
 */
@Composable
fun CardImage(
    path: String,
    maxEdge: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    kind: PictureKind = PictureKind.PHOTO,
) {
    val bitmap = rememberCardBitmap(path, maxEdge)
    val whole = kind != PictureKind.PHOTO
    Box(
        modifier = modifier.background(backgroundFor(kind)),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                // A pictogram is always shown whole, with a little air: it is a
                // drawing of one thing, and cropping cuts the thing.
                contentScale = if (whole) ContentScale.Fit else contentScale,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (kind == PictureKind.PICTOGRAM) PictogramInset else 0.dp),
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
    kind: PictureKind,
    modifier: Modifier = Modifier,
    maxEdge: Int = 1024,
) {
    val bitmap = rememberCardBitmap(path, maxEdge)
    if (kind == PictureKind.EMOJI) {
        // No frame, no shadow, no paper: the emoji itself, as big as fits.
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        return
    }
    val ratio = bitmap?.let { it.width.toFloat() / it.height }?.takeIf { it > 0f } ?: 1f
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // As large as fits, at the picture's own proportions.
        val byWidth = maxWidth / ratio <= maxHeight
        val frame = if (byWidth) {
            Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = false)
        } else {
            Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = true)
        }
        Box(
            modifier = frame
                .shadow(elevation = 3.dp, shape = FrameShape)
                .clip(FrameShape)
                .background(backgroundFor(kind)),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (kind == PictureKind.PICTOGRAM) PictogramInset * 2 else 0.dp),
                )
            }
        }
    }
}

/**
 * The white a pictogram was drawn on, filled in around it too, so a picture
 * narrower than its frame is framed in its own paper rather than in grey.
 */
val PictogramPaper = Color.White

@Composable
private fun backgroundFor(kind: PictureKind): Color = when (kind) {
    PictureKind.PICTOGRAM -> PictogramPaper
    PictureKind.EMOJI -> Color.Transparent
    PictureKind.PHOTO -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
private fun rememberCardBitmap(path: String, maxEdge: Int): ImageBitmap? {
    val flashcards = remember { Services.app.flashcardRepository }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxEdge) {
        value = flashcards.loadImage(path, maxEdge)
    }
    return bitmap
}

private val FrameShape = RoundedCornerShape(20.dp)

private val PictogramInset: Dp = 4.dp
