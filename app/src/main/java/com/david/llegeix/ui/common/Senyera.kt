package com.david.llegeix.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.settings.AppFlag
import kotlin.math.cos
import kotlin.math.sin

/** Official Senyera colours. */
private val SenyeraGold = Color(0xFFFCD116)
private val SenyeraRed = Color(0xFFDA121A)

/** The Estelada's triangle and star, in the blue version everybody means. */
private val EsteladaBlue = Color(0xFF0072C6)
private val EsteladaStar = Color(0xFFFFFFFF)

/**
 * The flag beside the app's name, drawn rather than shipped as an asset so it
 * stays crisp at any size and needs no density buckets.
 *
 * The Senyera is nine equal horizontal bands, gold first and last, in a 3:2
 * frame. The Estelada is the same flag with a blue triangle at the hoist and a
 * white five-pointed star in it.
 *
 * It used to be 27×18dp with a grey hairline round it, which is a flag the size
 * of a piece of punctuation. It is now a proper mark: half again as big, on the
 * app bar's own rounded-rectangle language, lifted off the bar by a small
 * shadow, and edged in white rather than in the theme's divider colour. That is
 * what "matches the rest of the app" means here — everything else on these
 * screens is a rounded surface with a little elevation under it, and the flag
 * was the one thing drawn like a diagram.
 *
 * Pressing it asks which flag it should be. There is nothing about this in
 * Configuració on purpose: the question only makes sense while looking at the
 * flag, and a preference nobody can find until they poke the thing it is about
 * is exactly what an easter egg is.
 */
@Composable
fun Senyera(
    modifier: Modifier = Modifier,
    flag: AppFlag = AppFlag.SENYERA,
    onClick: (() -> Unit)? = null,
) {
    val description = stringResource(
        when (flag) {
            AppFlag.SENYERA -> R.string.senyera_content_description
            AppFlag.ESTELADA -> R.string.estelada_content_description
        },
    )

    Box(
        modifier = modifier
            .size(width = FlagWidth, height = FlagHeight)
            .shadow(FlagElevation, FlagShape, clip = false)
            .clip(FlagShape)
            .background(SenyeraGold)
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .semantics {
                contentDescription = description
                if (onClick != null) role = Role.Button
            },
    ) {
        FlagCanvas(flag = flag, modifier = Modifier.matchParentSize())
        // A white hairline inside the edge. It is what stops a small rectangle
        // of flat colour reading as a sticker, and it is drawn over the canvas
        // rather than under it so the rounded corners stay clean.
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(1.dp, Color.White.copy(alpha = 0.28f), FlagShape),
        )
    }
}

/** The flag itself, without the chrome around it. Shared with the picker. */
@Composable
private fun FlagCanvas(flag: AppFlag, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
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
        if (flag != AppFlag.ESTELADA) return@Canvas

        // The triangle reaches from the hoist to a point on the centre line.
        // Half the flag's width is the proportion the real thing uses.
        val apex = size.width * 0.52f
        drawPath(
            path = Path().apply {
                moveTo(0f, 0f)
                lineTo(apex, size.height / 2f)
                lineTo(0f, size.height)
                close()
            },
            color = EsteladaBlue,
        )
        drawPath(
            path = starPath(
                centreX = apex * 0.36f,
                centreY = size.height / 2f,
                radius = size.height * 0.20f,
            ),
            color = EsteladaStar,
        )
    }
}

/**
 * A five-pointed star, point upwards.
 *
 * Built from the ten alternating radii rather than from five crossing lines, so
 * the shape can be filled: a star drawn as strokes has no inside for a fill to
 * find. The inner radius is the golden ratio's, which is what makes a
 * five-pointed star look like the one on a flag rather than like a splat.
 */
private fun starPath(centreX: Float, centreY: Float, radius: Float): Path {
    val inner = radius * 0.382f
    return Path().apply {
        repeat(10) { index ->
            val r = if (index % 2 == 0) radius else inner
            // Starting at -90° puts a point at the top.
            val angle = Math.toRadians((index * 36.0) - 90.0)
            val x = centreX + r * cos(angle).toFloat()
            val y = centreY + r * sin(angle).toFloat()
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * Which flag, asked by pressing the flag.
 *
 * Both are drawn at the size they will actually appear at, because the choice
 * is entirely about which one you would rather see and a list of two names
 * would be the one presentation that does not show it.
 */
@Composable
fun FlagChoiceDialog(
    current: AppFlag,
    onChoose: (AppFlag) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.flag_choose_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                AppFlag.entries.forEach { candidate ->
                    FlagChoice(
                        flag = candidate,
                        selected = candidate == current,
                        onClick = { onChoose(candidate) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun FlagChoice(flag: AppFlag, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(Space.md),
    ) {
        Box(
            modifier = Modifier
                .size(width = ChoiceWidth, height = ChoiceHeight)
                .clip(FlagShape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = FlagShape,
                ),
        ) {
            FlagCanvas(flag = flag, modifier = Modifier.size(ChoiceWidth, ChoiceHeight))
        }
        Text(
            text = stringResource(flag.labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = Space.sm),
        )
    }
}

/**
 * Bigger than the 27×18dp it was, and still in the flag's own 3:2.
 *
 * A mark next to a title has to be large enough to be a picture of something.
 * At cap height it was a coloured smudge that could have been anything; at 36dp
 * across, the four stripes are four stripes and the Estelada's star is a star.
 */
private val FlagWidth = 36.dp
private val FlagHeight = 24.dp
private val FlagShape = RoundedCornerShape(6.dp)
private val FlagElevation = 2.dp

private val ChoiceWidth = 84.dp
private val ChoiceHeight = 56.dp
