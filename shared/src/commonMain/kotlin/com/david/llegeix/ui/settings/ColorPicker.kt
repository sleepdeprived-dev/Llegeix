package com.david.llegeix.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import com.david.llegeix.resources.*
import com.david.llegeix.ui.common.Space
import com.david.llegeix.ui.theme.hslColor
import com.david.llegeix.ui.theme.parseHex
import com.david.llegeix.ui.theme.toHex
import com.david.llegeix.ui.theme.toHsl
import org.jetbrains.compose.resources.stringResource

/**
 * Mix an accent colour by hand.
 *
 * Two sliders rather than the usual saturation-and-value square: a square is
 * fiddly to hit precisely and gives no sense of where you are, while a rainbow
 * strip for the hue and a light-to-dark strip for the shade are each one
 * unambiguous axis. The hex field is there for anyone who already knows the
 * code they want, and it is kept in step with the sliders in both directions.
 */
@Composable
fun ColorPickerDialog(
    initial: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val start = remember(initial) { Color(initial).toHsl() }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var lightness by remember { mutableFloatStateOf(start[2].coerceIn(0.15f, 0.85f)) }
    var saturation by remember { mutableFloatStateOf(start[1].coerceIn(0.3f, 1f)) }

    val colour = hslColor(hue, saturation, lightness)
    // Typing is tracked separately so a half-written code is not thrown away
    // mid-keystroke; the sliders only follow once it parses.
    var hexText by remember { mutableStateOf(colour.toHex()) }
    var editing by remember { mutableStateOf(false) }
    if (!editing) hexText = colour.toHex()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_accent_custom_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colour)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp),
                            ),
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { typed ->
                            editing = true
                            hexText = typed
                            parseHex(typed)?.let { parsed ->
                                val hsl = parsed.toHsl()
                                hue = hsl[0]
                                saturation = hsl[1]
                                lightness = hsl[2]
                            }
                        },
                        label = { Text(stringResource(Res.string.settings_accent_hex)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Space.lg),
                    )
                }

                GradientSlider(
                    label = stringResource(Res.string.settings_accent_hue),
                    value = hue / 360f,
                    brush = Brush.horizontalGradient(
                        (0..360 step 30).map { hslColor(it.toFloat(), 1f, 0.5f) },
                    ),
                    onValueChange = {
                        editing = false
                        hue = it * 360f
                    },
                    modifier = Modifier.padding(top = Space.xl),
                )

                GradientSlider(
                    label = stringResource(Res.string.settings_accent_shade),
                    value = (lightness - 0.15f) / 0.7f,
                    brush = Brush.horizontalGradient(
                        listOf(
                            hslColor(hue, saturation, 0.15f),
                            hslColor(hue, saturation, 0.5f),
                            hslColor(hue, saturation, 0.85f),
                        ),
                    ),
                    onValueChange = {
                        editing = false
                        lightness = 0.15f + it * 0.7f
                    },
                    modifier = Modifier.padding(top = Space.lg),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(colour.toArgb()) }) {
                Text(stringResource(Res.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

/**
 * A track painted with the colours it selects between.
 *
 * Hand-rolled rather than a Material Slider so the track can be the gradient
 * itself: on a colour picker the track is the information, and a plain bar with
 * a label would make the reader guess.
 */
@Composable
private fun GradientSlider(
    label: String,
    value: Float,
    brush: Brush,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableFloatStateOf(1f) }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .padding(top = Space.xs),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    },
            ) {
                width = size.width
                drawRect(brush = brush)
            }
            // The handle is drawn over the track rather than inside the Canvas
            // so it picks up the theme's outline colour.
            Box(
                modifier = Modifier
                    .offsetHandle(value)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
            )
        }
    }
}

/** Places the handle along the track, keeping it fully on screen at both ends. */
private fun Modifier.offsetHandle(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val travel = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
    val x = (fraction.coerceIn(0f, 1f) * travel).toInt()
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative(x, 0)
    }
}
