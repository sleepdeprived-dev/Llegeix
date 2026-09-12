package com.david.llegeix.ui.exams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import com.david.llegeix.R
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.exam.MarkPainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * Where a text box is *right now*, while a finger is on it.
 *
 * The box is two drawings of one thing. Its frame and handles are composables,
 * and its words are painted onto the canvas underneath by [MarkPainter] —
 * because the words have to be drawn by the code that will draw them into the
 * exported PDF, and a live editor cannot be a canvas. That split is fine while
 * nothing is moving and was the whole trouble while something was: a drag moved
 * the frame, which is local state a hundred times a second, and left the words
 * where the database still had them. The frame slid across the page and the
 * answer stayed behind until the finger came up.
 *
 * So the live geometry lives here instead, above both of them: the chrome
 * positions itself from it and the canvas paints the box from it, and they
 * cannot disagree. Held as float states, and written on every pointer event of
 * a drag, so that a move costs a layout and a redraw rather than a
 * recomposition — and no boxing of a java.lang.Float per frame on the one code
 * path where a collection pause is visible as the box stuttering behind the
 * finger.
 */
class TextBoxLive(rotation: Float) {
    /** How far the box has been dragged, in the page's own pixels. */
    var dragX by mutableFloatStateOf(0f)
    var dragY by mutableFloatStateOf(0f)

    /** What the corner handle has been dragged to, as a multiplier. */
    var liveScale by mutableFloatStateOf(1f)

    /** Where the turn handle has got to, in degrees. */
    var liveRotation by mutableFloatStateOf(rotation)

    /**
     * The row as it would be saved if the finger came up now.
     *
     * The same arithmetic the ViewModel does when it commits a move or a
     * resize, so what the painter draws mid-gesture is what the database will
     * hold when the gesture ends and nothing jumps as the two swap over.
     */
    fun preview(
        mark: ExamMarkEntity,
        pageWidthPx: Float,
        pageHeightPx: Float,
    ): ExamMarkEntity = mark.copy(
        x = mark.x + dragX / pageWidthPx,
        y = mark.y + dragY / pageHeightPx,
        width = mark.width * liveScale,
        height = mark.height * liveScale,
        size = mark.size * liveScale,
        rotation = liveRotation,
    )
}

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
    /** Where the box has been dragged to, shared with the canvas underneath. */
    live: TextBoxLive,
    onStartEditing: () -> Unit,
    /** Called when the field gives up focus, which is how a person says "done". */
    onDoneEditing: () -> Unit,
    /** Called while typing, a moment after it stops. */
    onTextChanged: (text: String, heightFraction: Float) -> Unit,
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

    // The live geometry is [live], not local state, because the words under the
    // frame are painted on the canvas below and have to move with it. See
    // [TextBoxLive].
    LaunchedEffect(mark.rotation) { live.liveRotation = mark.rotation }

    val currentMark by rememberUpdatedState(mark)

    // The row as it was when the finger lifted, while the change it made is on
    // its way to the database and back.
    //
    // The live offsets used to be zeroed the instant the gesture ended, which
    // put the box back where it started for the frame or two the write took to
    // come round through Room — so every move and every resize finished with a
    // flinch. They are let go when the row returns changed instead. The timeout
    // is the other half of that: a move clamped to the edge of the page comes
    // back *unchanged*, and an offset waiting for a change that will never
    // arrive would leave the box drawn somewhere it is not.
    var settlingFrom by remember(mark.id) { mutableStateOf<ExamMarkEntity?>(null) }
    LaunchedEffect(settlingFrom) {
        val before = settlingFrom ?: return@LaunchedEffect
        withTimeoutOrNull(SETTLE_MS) {
            snapshotFlow { currentMark }.first { it != before }
        }
        live.dragX = 0f
        live.dragY = 0f
        live.liveScale = 1f
        settlingFrom = null
    }
    // Half a handle, so each one straddles its corner instead of hanging off
    // it. In pixels because that is what `offset {}` works in.
    val handleInset = with(density) { (HandleSize / 2).toPx() }

    val boxWidthDp = with(density) { (boxWidthPx * live.liveScale).toDp() }
    val boxHeightDp = with(density) { (boxHeightPx * live.liveScale).toDp() }

    // The box, plus half a handle of margin on every side.
    //
    // The margin is what makes the thing usable. A one-word answer is a strip
    // of page nine pixels tall, and nine pixels is not something a finger can
    // pick up: dragging it was a matter of hitting a hairline, and missing put
    // a stroke on the page instead. The margin is invisible, it is where the
    // handles sit — they straddle the corners, so half of each was outside the
    // box and outside anything that could be touched — and it is part of what
    // the drag responds to, so what you grab is the box and the air around it.
    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    (boxLeftPx + live.dragX - handleInset).roundToInt(),
                    (boxTopPx + live.dragY - handleInset).roundToInt(),
                )
            }
            .width(boxWidthDp + HandleSize)
            // While typing the box grows with the words. Fixing its height to
            // what the *stored* text needs would mean the cursor walking out of
            // the bottom of the frame on the fourth line of an essay, with the
            // words still going in somewhere below the edge of the box.
            .then(
                if (editing) {
                    Modifier.heightIn(min = boxHeightDp + HandleSize)
                } else {
                    Modifier.height(boxHeightDp + HandleSize)
                },
            )
            .graphicsLayer { rotationZ = live.liveRotation }
            // Moving is dragging the box, which is the one gesture nobody has
            // to be taught, and tapping it opens the keyboard. Both from one
            // handler rather than a tap detector stacked on a drag detector:
            // the two negotiate over who owns the finger through the
            // consumption rules, and what came out of that negotiation was a
            // box that could be tapped and could not be moved. Here the finger
            // decides, and it decides by going somewhere: past the touch slop
            // it is a drag, lifted before that it is a tap.
            .then(
                if (editing) {
                    Modifier
                } else {
                    Modifier.pointerInput(mark.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val slop = viewConfiguration.touchSlop
                            var travelled = 0f
                            var moving = false
                            var lifted = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes
                                    .firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    lifted = true
                                    break
                                }
                                val amount = change.positionChange()
                                travelled += amount.getDistance()
                                if (!moving && travelled > slop) moving = true
                                if (moving) {
                                    change.consume()
                                    // Turned back out of the box's own frame
                                    // before it is added to the offset. Pointer
                                    // input runs inside the rotation and reports
                                    // movement along the box's axes, while the
                                    // offset is applied outside it, in the
                                    // page's — so on a box turned 45° a drag
                                    // straight up the screen would have sent it
                                    // off diagonally.
                                    val radians = Math.toRadians(live.liveRotation.toDouble())
                                    val cosine = cos(radians).toFloat()
                                    val sine = sin(radians).toFloat()
                                    live.dragX += amount.x * cosine - amount.y * sine
                                    live.dragY += amount.x * sine + amount.y * cosine
                                }
                            }
                            when {
                                moving -> {
                                    settlingFrom = currentMark
                                    onMove(
                                        currentMark.x + live.dragX / pageWidthPx,
                                        currentMark.y + live.dragY / pageHeightPx,
                                    )
                                }
                                lifted -> onStartEditing()
                            }
                        }
                    }
                },
            ),
    ) {
        // The frame. Dashed would be more conventional and is harder to see on
        // a scan; a hairline in the accent reads as "this is selected" against
        // both white paper and an inverted page.
        Box(
            modifier = Modifier
                .padding(HandleSize / 2)
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
                ),
        ) {
            if (editing) {
                TextEditor(
                    mark = mark,
                    onDone = onDoneEditing,
                    widthDp = boxWidthDp,
                    pageWidthPx = pageWidthPx,
                    pageHeightPx = pageHeightPx,
                    onChanged = onTextChanged,
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
                offsetPx = IntOffset.Zero,
                onClick = onDelete,
            )
            RotateHandle(
                label = stringResource(R.string.exam_text_rotate),
                offsetPx = IntOffset((boxWidthPx * live.liveScale).roundToInt(), 0),
                onRotate = { delta -> live.liveRotation += delta },
                onRotateEnd = { onRotate(live.liveRotation) },
                centreOf = {
                    // In the box's own coordinates, which is what the drag
                    // amounts are already in — and the box now starts half a
                    // handle in from the root's corner.
                    Pair(
                        handleInset + boxWidthPx * live.liveScale / 2f,
                        handleInset + boxHeightPx * live.liveScale / 2f,
                    )
                },
            )
            ScaleHandle(
                label = stringResource(R.string.exam_text_resize),
                offsetPx = IntOffset(
                    (boxWidthPx * live.liveScale).roundToInt(),
                    (boxHeightPx * live.liveScale).roundToInt(),
                ),
                diagonalPx = hypot(boxWidthPx, boxHeightPx),
                onScale = { live.liveScale = it },
                onScaleEnd = {
                    settlingFrom = currentMark
                    onScale(live.liveScale)
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
    onDone: () -> Unit,
    /**
     * The box's width, handed down rather than filled to.
     *
     * `fillMaxWidth` through two nested boxes and a decoration slot leaves the
     * field's measurement depending on what every one of them does with its
     * constraints, and a text field that ends up measured narrow does not fail
     * loudly — it wraps after a couple of characters and looks like a bug in
     * the typing. One number, passed in, is one thing that can be wrong.
     */
    widthDp: Dp,
    pageWidthPx: Float,
    pageHeightPx: Float,
    onChanged: (text: String, heightFraction: Float) -> Unit,
    onFinish: (text: String, heightFraction: Float) -> Unit,
) {
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    // The field reports "not focused" once before it is given focus, so the
    // first report is ignored; without that the editor closes itself the
    // instant it opens.
    var hasBeenFocused by remember(mark.id) { mutableStateOf(false) }
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

    // Saved a moment after typing stops, not when the editor closes. Closing
    // was the only trigger and it was the wrong one: the commonest way for this
    // to leave the composition is the reader leaving the workspace, and the
    // ViewModel's scope is cancelled in the same breath — so the write was
    // launched into a dead scope and the answer was simply lost. Every mark
    // made with a pen has always been saved as the finger lifts; this is the
    // same promise for the keyboard.
    LaunchedEffect(value.text, mark.id) {
        if (value.text == mark.text.orEmpty()) return@LaunchedEffect
        delay(SAVE_AFTER_MS)
        onChanged(value.text, heightOf(mark, value.text, pageWidthPx, pageHeightPx))
    }

    // And once more on the way out, for the last few characters typed inside
    // the pause above.
    androidx.compose.runtime.DisposableEffect(mark.id) {
        onDispose {
            val text = currentValue.text
            onFinish(text, heightOf(mark, text, pageWidthPx, pageHeightPx))
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
        keyboardOptions = PROSE,
        cursorBrush = SolidColor(Color(mark.colorArgb)),
        modifier = Modifier
            .width(widthDp)
            .focusRequester(focusRequester)
            // Closing the keyboard is how somebody says they have finished
            // typing, and until this the box stayed in edit mode afterwards
            // with no frame, no handles and no way back to them short of
            // turning the page.
            .onFocusChanged { focus ->
                if (focus.isFocused) {
                    hasBeenFocused = true
                } else if (hasBeenFocused) {
                    onDone()
                }
            },
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

/**
 * How long a finished gesture holds its position while the row catches up.
 *
 * A Room update and the flow that reports it back take a handful of
 * milliseconds; this is generous enough that the box never flickers and short
 * enough that a change which was clamped away, and so never arrives, is not
 * left drawn out of place long enough to notice.
 */
private const val SETTLE_MS = 400L

/**
 * The whole of a blank page, as one field.
 *
 * This is the other thing the text tool does, and it is a different thing
 * rather than the box with different numbers in it. On a printed page a typed
 * answer has to go in a particular place, so it is an object with a position, a
 * width and a frame. A blank page has no particular place: the page is the
 * answer. Giving it the box treatment is what made writing an essay
 * unbearable — a column a fraction of the page wide, with a frame round it and
 * handles on its corners, that had to be aimed at before a word could be typed.
 *
 * So: no frame, no handles, no selection. The writing area of the page is a
 * field, tapping anywhere in it starts typing, and the words run from the left
 * margin to the right one and down the page. It saves as it goes, like
 * everything else in a sitting.
 */
@Composable
fun ExamDocumentEditor(
    mark: ExamMarkEntity,
    pageWidthPx: Float,
    pageHeightPx: Float,
    /** True once the reader has asked to write, which is what raises the keyboard. */
    focused: Boolean,
    onFocused: () -> Unit,
    onDone: () -> Unit,
    onChanged: (text: String, heightFraction: Float) -> Unit,
    /**
     * Where the cursor has got to, in pixels down the page.
     *
     * Reported rather than acted on here, because the thing that has to move is
     * the page, and the page is somebody else's composable. See
     * [ExamPage]'s caret-following effect for what is done with it.
     */
    onCaretMoved: (topPx: Float, bottomPx: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageWidthPx <= 0f || pageHeightPx <= 0f) return
    val density = LocalDensity.current
    val focusRequester = remember { FocusRequester() }
    var hasBeenFocused by remember(mark.id) { mutableStateOf(false) }

    var value by remember(mark.id) {
        mutableStateOf(
            TextFieldValue(
                text = mark.text.orEmpty(),
                selection = androidx.compose.ui.text.TextRange(mark.text.orEmpty().length),
            ),
        )
    }
    val currentValue by rememberUpdatedState(value)

    LaunchedEffect(mark.id, focused) {
        if (focused) runCatching { focusRequester.requestFocus() }
    }

    // The field's own measurement of itself, kept so that the cursor can be
    // turned into a position on the page. Moving the cursor does not re-measure
    // the text, and typing does not move it by a knowable amount, so both have
    // to be watched: the layout for where the lines ended up, the selection for
    // which of them the cursor is on.
    //
    // Only while the field actually holds focus, and that is the real signal
    // rather than [focused], which is the app's *intention* that it should. A
    // page with no cursor in it is a page being read, and reporting a cursor
    // from it would scroll somebody who merely picked the text tool down to
    // wherever they last stopped writing.
    var layout by remember(mark.id) { mutableStateOf<TextLayoutResult?>(null) }
    var hasFocus by remember(mark.id) { mutableStateOf(false) }
    LaunchedEffect(layout, value.selection, hasFocus, mark.y, pageHeightPx) {
        if (!hasFocus) {
            // Said out loud rather than left to go stale. Once the keyboard is
            // down there is no cursor to keep in view, and the page should stay
            // exactly where the reader left it rather than move to where the
            // cursor used to be.
            onCaretMoved(Float.NaN, Float.NaN)
            return@LaunchedEffect
        }
        val measured = layout ?: return@LaunchedEffect
        // Against the layout's own text rather than the field's. A keystroke
        // moves the cursor a frame before the text is measured again, and
        // asking a stale layout about an offset past its end throws.
        val offset = value.selection.start
            .coerceIn(0, measured.layoutInput.text.length)
        val caret = runCatching { measured.getCursorRect(offset) }.getOrNull()
            ?: return@LaunchedEffect
        val top = mark.y * pageHeightPx
        onCaretMoved(top + caret.top, top + caret.bottom)
    }

    LaunchedEffect(value.text, mark.id) {
        if (value.text == mark.text.orEmpty()) return@LaunchedEffect
        delay(SAVE_AFTER_MS)
        onChanged(value.text, heightOf(mark, value.text, pageWidthPx, pageHeightPx))
    }

    androidx.compose.runtime.DisposableEffect(mark.id) {
        onDispose {
            val text = currentValue.text
            onChanged(text, heightOf(mark, text, pageWidthPx, pageHeightPx))
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
        keyboardOptions = PROSE,
        onTextLayout = { layout = it },
        cursorBrush = SolidColor(Color(mark.colorArgb)),
        modifier = modifier
            // The page's writing area, straight from the mark, so what is typed
            // sits exactly where the exporter will draw it.
            .offset {
                IntOffset(
                    (mark.x * pageWidthPx).roundToInt(),
                    (mark.y * pageHeightPx).roundToInt(),
                )
            }
            .width(with(density) { (mark.width * pageWidthPx).toDp() })
            .height(with(density) { ((1f - 2 * mark.y) * pageHeightPx).toDp() })
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                hasFocus = focus.isFocused
                if (focus.isFocused) hasBeenFocused = true else if (hasBeenFocused) onDone()
            }
            .pointerInput(mark.id) {
                detectTapGestures { onFocused() }
            },
    )
}

/**
 * How the keyboard behaves in both editors.
 *
 * Sentences are capitalised, which is what every other place a person writes
 * prose on a phone does and what makes typing a paragraph here feel like typing
 * a paragraph anywhere else. Without it the first word of every sentence in an
 * essay had to be shifted by hand, which is the kind of small constant friction
 * that makes a text field feel wrong without anybody being able to say why.
 */
private val PROSE = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

/** The height a piece of text needs, measured by the code that will draw it. */
private fun heightOf(
    mark: ExamMarkEntity,
    text: String,
    pageWidthPx: Float,
    pageHeightPx: Float,
): Float = MarkPainter.textHeightFraction(mark.copy(text = text), pageWidthPx, pageHeightPx)

/**
 * How long typing has to stop before what is on screen is written down.
 *
 * Short enough that nothing is lost to a phone call or a back button, long
 * enough that holding a key down is not a database write per character.
 */
private const val SAVE_AFTER_MS = 400L
