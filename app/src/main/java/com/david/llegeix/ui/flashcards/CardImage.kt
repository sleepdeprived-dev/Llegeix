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
import androidx.compose.ui.graphics.ColorMatrix
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
 * a white square dropped onto the app. See [pictogramStyle] for how they are
 * made to belong to it, in the light theme and the dark.
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
    val style = pictogramStyle()
    Box(
        modifier = modifier.background(
            if (pictogram) style.paper else MaterialTheme.colorScheme.surfaceContainerHighest,
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
                colorFilter = if (pictogram) style.filter else null,
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
        val style = pictogramStyle()
        Box(
            modifier = frame
                .shadow(elevation = if (pictogram && style.isDark) 0.dp else 3.dp, shape = FrameShape)
                .clip(FrameShape)
                .background(if (pictogram) style.paper else MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            bitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    colorFilter = if (pictogram) style.filter else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (pictogram) PictogramInset * 2 else 0.dp),
                )
            }
        }
    }
}

/** How a pictogram is drawn: what it sits on, and what is done to its colours. */
class PictogramStyle(val paper: Color, val filter: ColorFilter, val isDark: Boolean)

/**
 * Making a pictogram belong to the theme.
 *
 * **Light theme.** The white is recoloured to a pale tint of the reader's own
 * accent, by multiplying the picture with it: white becomes the tint, black
 * stays black, and the colours in between shift by a shade. The square behind
 * is the same tint, so the pictogram reads as a card of the app's own.
 *
 * **Dark theme.** A pale square on a dark card is a light left on at night, and
 * no tint of white stops being bright. So the picture is turned over instead:
 * its lightness is inverted and its hue turned half a circle back again. White
 * becomes the dark of the card, the black outlines become light, and the
 * colours keep their hue — the red apple stays red and the leaves stay green,
 * where a plain inversion would make them cyan and pink. One colour matrix does
 * both: the luminance-preserving hue rotation, negated, plus white; and the
 * paper's own colour added, so the background lands on exactly the card's dark.
 */
@Composable
fun pictogramStyle(): PictogramStyle {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    return remember(scheme.primary, scheme.surfaceContainerLowest, isDark) {
        if (isDark) {
            val paper = scheme.surfaceContainerLowest
            PictogramStyle(paper = paper, filter = ColorFilter.colorMatrix(nightMatrix(paper)), isDark = true)
        } else {
            val tint = lerp(Color.White, scheme.primary, 0.07f)
            PictogramStyle(paper = tint, filter = ColorFilter.tint(tint, BlendMode.Multiply), isDark = false)
        }
    }
}

/**
 * Inversion with the hue kept, then made easy on the eyes.
 *
 * First c' = 255 − H·c, where H is the standard 180° hue rotation: its rows
 * each sum to one, so white goes to black and black to white. An inverted red
 * comes out a washed pink, though, and pure white lines are harsh at night; so
 * the result is then saturated and dimmed a little (a greyscale-preserving
 * matrix, so the black background stays black), and only after that is the
 * paper's own colour added, so the background lands on exactly the paper.
 */
private fun nightMatrix(paper: Color): ColorMatrix {
    // The inversion: linear part −H, offset 255.
    val h = arrayOf(
        floatArrayOf(-0.574f, 1.430f, 0.144f),
        floatArrayOf(0.426f, 0.430f, 0.144f),
        floatArrayOf(0.426f, 1.430f, -0.856f),
    )
    // Saturation s about Rec. 709 luminance, then brightness k.
    val s = NIGHT_SATURATION
    val k = NIGHT_BRIGHTNESS
    val lum = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
    val a = Array(3) { i -> FloatArray(3) { j -> k * ((1 - s) * lum[j] + if (i == j) s else 0f) } }
    // A·(−H·c + 255) = −(A·H)·c + A·255, and A·(255,255,255) is k·255 because
    // A keeps greys grey.
    val m = FloatArray(20)
    for (i in 0 until 3) {
        for (j in 0 until 3) {
            var sum = 0f
            for (n in 0 until 3) sum += a[i][n] * h[n][j]
            m[i * 5 + j] = -sum
        }
    }
    m[4] = k * 255f + paper.red * 255f
    m[9] = k * 255f + paper.green * 255f
    m[14] = k * 255f + paper.blue * 255f
    m[18] = 1f
    return ColorMatrix(m)
}

/** Enough to give an inverted red back its colour. */
private const val NIGHT_SATURATION = 1.45f

/** Lines a soft light grey rather than a glare of white. */
private const val NIGHT_BRIGHTNESS = 0.86f

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
