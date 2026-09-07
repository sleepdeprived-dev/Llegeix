package com.david.llegeix.ui.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.exam.MarkPainter
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * A typed answer as an object on the page, rather than as the output of a form.
 *
 * Typing an answer used to be: tap the page, a dialog covers it, type into a
 * field with no idea how big the words will be or whether they will fit, press
 * a button, and find out. For a one-word gap on a printed page that was merely
 * poor. For writing an essay it was unusable — every paragraph was a trip
 * through a modal that hid the thing being written on.
 *
 * So the box lives on the page and behaves the way a text object behaves
 * everywhere else. It is put down where the finger went, it shows what is being
 * typed at the size and in the place it will actually be, and once it is there
 * it can be picked up and moved, turned, made bigger or smaller, and deleted.
 *
 * ### Where the geometry lives
 *
 * Everything is stored as fractions of the page, like every other mark, and
 * turned into pixels here against the size the page is currently drawn at. The
 * frame, the handles and the editor are all positioned from the same four
 * numbers the exporter will use, so what is on screen is what comes out.
 *
 * ### Why the text is not drawn here
 *
 * A committed box is drawn by [MarkPainter] on the canvas underneath, with the
 * same code that draws it into the exported PDF — that is the rule the whole
 * feature rests on. This composable draws the *chrome*, and during editing it
 * draws the text as well, because a live editor cannot be a canvas. The two
 * agree because they are given the same font, the same size and the same line
 * spacing; and the moment editing ends the painter takes over again, so the
 * version that lasts is always the painter's.
 */
@Composable
fun ExamTextBox(
    mark: ExamMarkEntity,
    /** The page's drawn size in pixels, which is what fractions are measured against. */
    pageWidthPx: Float,
    pageHeightPx: Float,
    editing: Boolean,
    onStartEditing: () -> Unit,
    onFinishEditing: (text: String, heightFraction: Float) -> Unit,
    onMove: (x: Float, y: Float) -> Unit,
    onScale: (factor: Float) -> Unit,
    onRotate: (degrees: Float) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageWidthPx <= 0f || pageHeightPx <= 0f) return
    val density = LocalDensity.current

    // Measured rather than taken from the row: a box whose stored height is a
    // line out of date would draw a frame that does not fit its own words,
    // which is the clearest possible sign that an app is guessing.
    val heightFraction = remember(mark.text, mark.size, mark.width, pageWidthPx) {
        MarkPainter.textHeightFraction(mark, pageWidthPx, pageHeightPx)
    }

    val boxLeftPx = mark.x * pageWidthPx
    val boxTopPx = mark.y * pageHeightPx
    val boxWidthPx = mark.width * pageWidthPx
    val boxHeightPx = (heightFraction * pageHeightPx).coerceAtLeast(mark.size * pageWidthPx)

    // Live drag offsets, kept here rather than written to the database on every
    // pointer event: a drag produces one move per frame, and a round trip
    // through Room per frame is a box that lags behind the finger.
    var dragX by remember(mark.id) { mutableStateOf(0f) }
    var dragY by remember(mark.id) { mutableStateOf(0f) }
    var liveScale by remember(mark.id) { mutableStateOf(1f) }
    var liveRotation by remember(mark.id) { mutableStateOf(mark.rotation) }
    LaunchedEffect(mark.rotation) { liveRotation = mark.rotation }

    val currentMark by rememberUpdatedState(mark)
    // Half a handle, so each one straddles its corner instead of hanging off
    // it. In pixels because that is what `offset {}` works in.
    val handleInset = with(density) { (HandleSize / 2).toPx() }

    val boxWidthDp = with(density) { (boxWidthPx * liveScale).toDp() }
    val boxHeightDp = with(density) { (boxHeightPx * liveScale).toDp() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (boxLeftPx + dragX).roundToInt(),
                    (boxTopPx + dragY).roundToInt(),
                )
            }
            .width(boxWidthDp)
            // While typing the box grows with the words. Fixing its height to
            // what the *stored* text needs would mean the cursor walking out of
            // the bottom of the frame on the fourth line of an essay, with the
            // words still going in somewhere below the edge of the box.
            .then(
                if (editing) {
                    Modifier.heightIn(min = boxHeightDp)
                } else {
                    Modifier.height(boxHeightDp)
                },
            )
            .graphicsLayer { rotationZ = liveRotation },
    ) {
        // The frame. Dashed would be more conventional and is harder to see on
        // a scan; a hairline in the accent reads as "this is selected" against
        // both white paper and an inverted page.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (editing) {
                        Modifier.heightIn(min = boxHeightDp)
                    } else {
                        Modifier.height(boxHeightDp)
                    },
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(2.dp),
                )
                .then(
                    if (editing) {
                        // Only while typing. A tinted box under finished text
                        // would be a highlight the reader did not ask for.
                        Modifier.background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        )
                    } else {
                        Modifier
                    },
                )
                // Moving is dragging the box, which is the one gesture nobody
                // has to be taught. Tapping it opens the keyboard.
                .pointerInput(mark.id, editing) {
                    if (editing) return@pointerInput
                    detectTapGestures(onTap = { onStartEditing() })
                }
                .pointerInput(mark.id, editing) {
                    if (editing) return@pointerInput
                    detectDragGestures(
                        onDragEnd = {
                            onMove(
                                currentMark.x + dragX / pageWidthPx,
                                currentMark.y + dragY / pageHeightPx,
                            )
                            dragX = 0f
                            dragY = 0f
                        },
                    ) { change, amount ->
                        change.consume()
                        // Turned back out of the box's own frame before it is
                        // added to the offset. Pointer input runs inside the
                        // rotation and reports movement along the box's axes,
                        // while the offset is applied outside it, in the page's
                        // — so on a box turned 45° a drag straight up the
                        // screen would have sent it off diagonally.
                        val radians = Math.toRadians(liveRotation.toDouble())
                        val cosine = cos(radians).toFloat()
                        val sine = sin(radians).toFloat()
                        dragX += amount.x * cosine - amount.y * sine
                        dragY += amount.x * sine + amount.y * cosine
                    }
                },
        ) {
            if (editing) {
                TextEditor(
                    mark = mark,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    onFinish = onFinishEditing,
                )
            }
        }

        if (!editing) {
            // Three handles and no more, at the three corners a person reaches
            // for: take it away, turn it, make it bigger. The fourth corner is
            // deliberately empty so there is always somewhere to grab the box
            // itself.
            Handle(
                icon = Icons.Default.Close,
                label = stringResource(R.string.exam_text_delete),
                offsetPx = IntOffset(-handleInset.roundToInt(), -handleInset.roundToInt()),
                onClick = onDelete,
            )
            RotateHandle(
                label = stringResource(R.string.exam_text_rotate),
                offsetPx = IntOffset(
                    (boxWidthPx * liveScale - handleInset).roundToInt(),
                    -handleInset.roundToInt(),
                ),
                onRotate = { delta -> liveRotation += delta },
                onRotateEnd = { onRotate(liveRotation) },
                centreOf = {
                    // In the box's own coordinates, which is what the drag
                    // amounts are already in.
                    Pair(boxWidthPx * liveScale / 2f, boxHeightPx * liveScale / 2f)
                },
            )
            ScaleHandle(
                label = stringResource(R.string.exam_text_resize),
                offsetPx = IntOffset(
                    (boxWidthPx * liveScale - handleInset).roundToInt(),
                    (boxHeightPx * liveScale - handleInset).roundToInt(),
                ),
                diagonalPx = hypot(boxWidthPx, boxHeightPx),
                onScale = { liveScale = it },
                onScaleEnd = {
                    onScale(liveScale)
                    liveScale = 1f
                },
            )
        }
    }
}

/**
 * The field itself, sitting exactly where the words will be.
 *
 * The font is the platform's sans-serif at the mark's own size, which is what
 * [MarkPainter] draws with, so the text does not jump when the keyboard closes.
 * There is no decoration and no background of its own: the point is that this
 * looks like the finished thing, because it is going to be.
 */
@Composable
private fun TextEditor(
    mark: ExamMarkEntity,
    pageWidthPx: Float,
    pageHeightPx: Float,
    onFinish: (text: String, heightFraction: Float) -> Unit,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    var value by remember(mark.id) {
        mutableStateOf(
            TextFieldValue(
                text = mark.text.orEmpty(),
                // The cursor lands at the end, which is where somebody
                // reopening a box to add to it expects it.
                selection = androidx.compose.ui.text.TextRange(mark.text.orEmpty().length),
            ),
        )
    }
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(mark.id) { focusRequester.requestFocus() }

    // Committed when the editor leaves, whichever way it leaves — the Done
    // button, a tap elsewhere on the page, or the page being turned. A text box
    // that only saves through one exit is a text box that loses work through
    // the others.
    androidx.compose.runtime.DisposableEffect(mark.id) {
        onDispose {
            val text = currentValue.text
            onFinish(
                text,
                MarkPainter.textHeightFraction(
                    mark.copy(text = text),
                    pageWidthPx,
                    pageHeightPx,
                ),
            )
        }
    }

    val textSizeSp = with(density) { (mark.size * pageWidthPx).toSp() }

    BasicTextField(
        value = value,
        onValueChange = { value = it },
        textStyle = LocalTextStyle.current.copy(
            color = Color(mark.colorArgb),
            fontSize = textSizeSp,
            fontFamily = FontFamily.SansSerif,
            lineHeight = textSizeSp * MarkPainter.LINE_SPACING,
            textAlign = TextAlign.Start,
        ),
        cursorBrush = SolidColor(Color(mark.colorArgb)),
        decorationBox = { field ->
            Box {
                if (value.text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.exam_text_placeholder),
                        color = Color(mark.colorArgb).copy(alpha = 0.4f),
                        style = LocalTextStyle.current.copy(
                            fontSize = textSizeSp,
                            fontFamily = FontFamily.SansSerif,
                        ),
                    )
                }
                field()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

@Composable
private fun BoxScope.Handle(
    icon: ImageVector,
    label: String,
    offsetPx: IntOffset,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { offsetPx }
            .size(HandleSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .semantics { contentDescription = label },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(HandleGlyph),
        )
    }
}

@Composable
private fun BoxScope.RotateHandle(
    label: String,
    offsetPx: IntOffset,
    onRotate: (Float) -> Unit,
    onRotateEnd: () -> Unit,
    centreOf: () -> Pair<Float, Float>,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { offsetPx }
            .size(HandleSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onRotateEnd() },
                ) { change, amount ->
                    change.consume()
                    val (cx, cy) = centreOf()
                    // How far the handle is from the box's centre, and how much
                    // of this frame's movement went *round* it rather than
                    // towards or away from it. That tangential part over the
                    // radius is the angle turned.
                    //
                    // Measuring the finger's absolute angle instead would not
                    // work here, and the reason is worth writing down: the
                    // gesture runs inside the rotation it is driving, so the
                    // moment the box turns, a finger that has not moved reports
                    // an angle smaller by exactly the amount just applied. The
                    // two cancel and the box sticks. A small movement converted
                    // to a small turn has no such feedback in it.
                    val handleX = offsetPx.x + change.position.x - cx
                    val handleY = offsetPx.y + change.position.y - cy
                    val radius = hypot(handleX, handleY).coerceAtLeast(1f)
                    val tangential = (-handleY * amount.x + handleX * amount.y) / radius
                    onRotate(Math.toDegrees((tangential / radius).toDouble()).toFloat())
                }
            }
            .semantics { contentDescription = label },
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(HandleGlyph),
        )
    }
}

@Composable
private fun BoxScope.ScaleHandle(
    label: String,
    offsetPx: IntOffset,
    diagonalPx: Float,
    onScale: (Float) -> Unit,
    onScaleEnd: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset { offsetPx }
            .size(HandleSize)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(diagonalPx) {
                var travelled = 0f
                detectDragGestures(
                    onDragEnd = { travelled = 0f; onScaleEnd() },
                    onDragCancel = { travelled = 0f },
                ) { change, amount ->
                    change.consume()
                    // Along the diagonal, so dragging out from the corner grows
                    // the box and dragging back into it shrinks it, whichever
                    // of the two directions the finger favours.
                    travelled += (amount.x + amount.y) / 2f
                    val span = diagonalPx.coerceAtLeast(1f)
                    onScale((1f + travelled * 2f / span).coerceIn(0.2f, 5f))
                }
            }
            .semantics { contentDescription = label },
    ) {
        Text(
            text = "⇲",
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * Handles are 28dp, which is under the 48dp a button would get.
 *
 * On purpose: they sit on the corners of something the reader is looking at,
 * and a 48dp disc on each corner of a one-line answer box would be bigger than
 * the answer. They are only ever on screen when a box is deliberately selected,
 * which is the case the minimum exists to protect — nobody hits one by
 * accident, because nothing is drawn until something has been chosen.
 */
private val HandleSize = 28.dp
private val HandleGlyph = 16.dp
