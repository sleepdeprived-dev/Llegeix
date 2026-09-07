package com.david.llegeix.ui.exams

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.db.entity.MarkKind
import com.david.llegeix.data.exam.MarkGeometry
import com.david.llegeix.data.exam.MarkPainter
import com.david.llegeix.pdf.TickBox
import com.david.llegeix.ui.common.Space

/**
 * One page of the paper, with everything written on it.
 *
 * The page is an image and the marks are drawn over it in a [Canvas]. Nothing
 * here writes to the PDF; the document underneath is opened read-only and stays
 * that way, which is the promise the whole feature rests on.
 *
 * ### Coordinates
 *
 * Marks are stored as fractions of the page and are multiplied up by the size
 * the page is currently drawn at. That conversion happens in exactly two
 * places — [MarkPainter] on the way out, and the gesture handlers on the way
 * in — so there is one definition of what a stored coordinate means.
 *
 * ### Zoom
 *
 * The zoom is a [graphicsLayer] on the box that holds both the image and the
 * canvas, so the two cannot drift apart. Pointer input sits *inside* that box:
 * Compose accounts for layer transforms when it routes touches, so a finger on
 * a magnified page reports the position it is over, not the position it would
 * have been over unmagnified. That is what lets somebody zoom into a small
 * answer box and write in it at a sensible size.
 */
@Composable
fun ExamPage(
    index: Int,
    zoom: Float,
    tool: ExamTool,
    inkColor: Int,
    highlightColor: Int,
    strokeWidth: Float,
    marks: List<ExamMarkEntity>,
    /** Boxes the detector found, for the tick tool to snap into. */
    tickBoxes: List<TickBox>,
    /**
     * Which document the page is coming from.
     *
     * Named in the render effect's keys so that flipping to the answer sheet
     * actually redraws. Without it the effect keys on the page number and the
     * width alone, neither of which moves when the reader flips — so the paper's
     * page stayed on screen with the answer sheet's page count under it.
     */
    renderKey: Any,
    /**
     * Draws the page as light-on-dark.
     *
     * Applied to the page image alone, never to the canvas of marks over it.
     * Inverting the marks too would mean the ink chosen in the toolbar and the
     * ink on the page were different colours, and the exported PDF — drawn from
     * the same marks onto white paper — would then not match what was on
     * screen, which is the one thing the export must never do.
     */
    darkPage: Boolean,
    canWrite: Boolean,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    onStrokeFinished: (List<MarkGeometry.Point>) -> Unit,
    onTapWithText: (x: Float, y: Float) -> Unit,
    onTick: (x: Float, y: Float, existing: ExamMarkEntity?) -> Unit,
    onErase: (ExamMarkEntity) -> Unit,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        var bitmap by remember(index, renderKey) { mutableStateOf<Bitmap?>(null) }

        LaunchedEffect(index, widthPx, renderKey) {
            bitmap = render(index, widthPx) ?: bitmap
        }

        val rendered = bitmap
        if (rendered == null) {
            CircularProgressIndicator()
            return@BoxWithConstraints
        }

        // Points of the stroke in progress, in fractions. Kept here rather than
        // in the ViewModel: a drag produces a point per frame, and routing each
        // one through a StateFlow would put the whole recomposition machinery
        // between the finger and the ink.
        val wet = remember { mutableStateListOf<MarkGeometry.Point>() }
        val currentZoom by rememberUpdatedState(zoom)
        val currentTool by rememberUpdatedState(tool)
        val currentMarks by rememberUpdatedState(marks)
        val currentBoxes by rememberUpdatedState(tickBoxes)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.sm, vertical = Space.sm)
                .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
                .graphicsLayer {
                    scaleX = currentZoom
                    scaleY = currentZoom
                    // Anchored at the top: a page's writing starts there, and a
                    // centre-anchored zoom throws you into the middle of it.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                },
        ) {
            Image(
                bitmap = rendered.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = if (darkPage) InvertPage else null,
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (darkPage) DarkPaper else Color.White),
            )

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pinchOnly(currentZoom = { currentZoom }, onZoomChanged = onZoomChanged)
                    .drawingGestures(
                        enabled = canWrite,
                        tool = { currentTool },
                        wet = wet,
                        marks = { currentMarks },
                        onStrokeFinished = onStrokeFinished,
                        onTapWithText = onTapWithText,
                        onTick = onTick,
                        onErase = onErase,
                        boxes = { currentBoxes },
                    ),
            ) {
                // The found boxes, outlined faintly, and only while the tick
                // tool is in hand. They are a hint about where a tap will land,
                // not a decoration and not a claim that these are all of them.
                if (tool == ExamTool.TICK) drawTickTargets(tickBoxes)

                drawMarks(marks)

                // The stroke under the finger, drawn in the ink it will be
                // saved in so that lifting the finger changes nothing visible.
                if (wet.size > 1) {
                    val isHighlight = tool == ExamTool.HIGHLIGHTER
                    drawWetStroke(
                        points = wet,
                        colorArgb = if (isHighlight) highlightColor else inkColor,
                        widthFraction = if (isHighlight) {
                            StrokeWidths.HIGHLIGHTER
                        } else {
                            strokeWidth
                        },
                        alpha = if (isHighlight) MarkPainter.HIGHLIGHT_ALPHA else 1f,
                    )
                }
            }
        }
    }
}

/**
 * A plain negative of the page: black type on white paper becomes white type on
 * black.
 *
 * The same matrix the reader uses on its own pages, and deliberately the same
 * blunt one: it keeps a diagram legible as well as a paragraph, which is what
 * an exam paper is usually half made of.
 */
private val InvertPage = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** What an inverted page sits on while it is still loading. */
private val DarkPaper = Color(0xFF0E0E0E)

/**
 * Pinch to zoom, and let every other gesture through.
 *
 * The same reasoning as the reader's [com.david.llegeix.ui.reader.pinchToZoom]:
 * a helper that reports pan alongside zoom consumes one-finger drags too, and
 * here a one-finger drag is the pen. Two fingers zoom; one finger writes.
 */
private fun Modifier.pinchOnly(
    currentZoom: () -> Float,
    onZoomChanged: (Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val change = event.calculateZoom()
                if (change != 1f) {
                    onZoomChanged(
                        (currentZoom() * change).coerceIn(
                            ExamWorkspaceViewModel.MIN_ZOOM,
                            ExamWorkspaceViewModel.MAX_ZOOM,
                        ),
                    )
                    event.changes.forEach { if (it.pressed) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

/**
 * What one finger does, according to the tool.
 *
 * Keyed on nothing, because the handlers read the tool through a lambda rather
 * than capturing it. A gesture detector outlives the composition that started
 * it, so a captured tool goes stale the moment the reader picks another one —
 * the same trap the reader's zoom handlers document at length.
 */
private fun Modifier.drawingGestures(
    enabled: Boolean,
    tool: () -> ExamTool,
    wet: SnapshotStateList<MarkGeometry.Point>,
    marks: () -> List<ExamMarkEntity>,
    onStrokeFinished: (List<MarkGeometry.Point>) -> Unit,
    onTapWithText: (x: Float, y: Float) -> Unit,
    onTick: (x: Float, y: Float, existing: ExamMarkEntity?) -> Unit,
    onErase: (ExamMarkEntity) -> Unit,
    boxes: () -> List<TickBox>,
): Modifier = this
    .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures { offset ->
            val x = offset.x / size.width
            val y = offset.y / size.height
            when (tool()) {
                ExamTool.TEXT -> onTapWithText(x, y)
                ExamTool.TICK -> {
                    // Snap into a found box when the tap is in one, so a tick
                    // sits squarely rather than wherever the finger landed. A
                    // tap outside every box is taken at its word.
                    val box = boxes().firstOrNull { x >= it.left && x <= it.right &&
                        y >= it.top && y <= it.bottom }
                    val tickX = box?.centerX ?: x
                    val tickY = box?.centerY ?: y
                    onTick(tickX, tickY, marks().tickAt(tickX, tickY))
                }
                ExamTool.ERASER -> marks().hitTest(x, y)?.let(onErase)
                else -> Unit
            }
        }
    }
    .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val drawing = tool() == ExamTool.PEN || tool() == ExamTool.HIGHLIGHTER
            val erasing = tool() == ExamTool.ERASER
            if (!drawing && !erasing) return@awaitEachGesture

            if (drawing) {
                wet.clear()
                wet += MarkGeometry.Point(down.position.x / size.width, down.position.y / size.height)
                down.consume()
            }

            var multiTouch = false
            // Marks already rubbed out during this one drag. Without it the
            // eraser fires a delete per pointer event for as long as the finger
            // is over a stroke — dozens of coroutines to remove one mark.
            val erased = HashSet<Long>()
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    // A second finger means the reader meant to zoom, not to
                    // write. The stroke so far is abandoned rather than saved
                    // as a stray mark under the pinch.
                    multiTouch = true
                    wet.clear()
                    break
                }
                val change = event.changes.firstOrNull { it.pressed } ?: break
                val x = change.position.x / size.width
                val y = change.position.y / size.height
                if (drawing) {
                    wet += MarkGeometry.Point(x, y)
                    change.consume()
                } else if (erasing) {
                    // The eraser rubs out as it is dragged across, which is how
                    // an eraser behaves.
                    marks().hitTest(x, y)?.let { mark ->
                        if (erased.add(mark.id)) onErase(mark)
                    }
                    change.consume()
                }
            } while (event.changes.any { it.pressed })

            if (drawing && !multiTouch && wet.size > 1) {
                onStrokeFinished(wet.toList())
            }
            wet.clear()
        }
    }

/** The topmost mark under a point, which is the one a person means. */
private fun List<ExamMarkEntity>.hitTest(x: Float, y: Float): ExamMarkEntity? =
    lastOrNull { mark ->
        // Padded, because a hairline stroke is almost impossible to hit exactly
        // and an eraser that misses reads as an eraser that does not work.
        x >= mark.x - HIT_PADDING && x <= mark.x + mark.width + HIT_PADDING &&
            y >= mark.y - HIT_PADDING && y <= mark.y + mark.height + HIT_PADDING
    }

private fun List<ExamMarkEntity>.tickAt(x: Float, y: Float): ExamMarkEntity? =
    lastOrNull { mark ->
        mark.kind == MarkKind.TICK.name &&
            x >= mark.x - HIT_PADDING && x <= mark.x + mark.width + HIT_PADDING &&
            y >= mark.y - HIT_PADDING && y <= mark.y + mark.height + HIT_PADDING
    }

/**
 * The boxes the detector found, outlined so a tap has somewhere to aim.
 *
 * Faint on purpose. They are the app's guess about the page, drawn over
 * somebody else's document, and a confident outline around a shape that is not
 * actually a checkbox would be the app asserting something it does not know.
 */
private fun DrawScope.drawTickTargets(boxes: List<TickBox>) {
    boxes.forEach { box ->
        drawRect(
            color = Color(TICK_TARGET_COLOR),
            topLeft = Offset(box.left * size.width, box.top * size.height),
            size = androidx.compose.ui.geometry.Size(
                (box.right - box.left) * size.width,
                (box.bottom - box.top) * size.height,
            ),
            alpha = TICK_TARGET_ALPHA,
        )
    }
}

/**
 * Draw the stored marks, and the one still under the finger.
 *
 * Both go through [MarkPainter] on the native canvas underneath rather than
 * through Compose's own drawing. That is deliberate: the exported PDF is drawn
 * by the same object onto the canvas [android.graphics.pdf.PdfDocument] hands
 * out, so what the reader sees here and what comes out of the export cannot
 * drift apart. An export that does not match the screen is one the reader would
 * only catch after the exam.
 */
private fun DrawScope.drawMarks(marks: List<ExamMarkEntity>) {
    drawIntoCanvas { canvas ->
        MarkPainter.draw(canvas.nativeCanvas, marks, size.width, size.height)
    }
}

/** The stroke in progress, in the ink it is about to be saved in. */
private fun DrawScope.drawWetStroke(
    points: List<MarkGeometry.Point>,
    colorArgb: Int,
    widthFraction: Float,
    alpha: Float,
) {
    drawIntoCanvas { canvas ->
        MarkPainter.stroke(
            canvas = canvas.nativeCanvas,
            points = points,
            colorArgb = colorArgb,
            widthFraction = widthFraction,
            alpha = alpha,
            width = size.width,
            height = size.height,
        )
    }
}

/**
 * How much slack a tap is given when looking for the mark under it, as a
 * fraction of the page.
 *
 * A pen stroke can be two pixels wide. Requiring a hit inside its literal
 * bounding box would make the eraser feel broken on exactly the marks it is
 * most needed for.
 */
private const val HIT_PADDING = 0.012f

/** The wash over a found tick box. The theme's primary, softened right down. */
private const val TICK_TARGET_COLOR = 0xFF1A4FD6.toInt()
private const val TICK_TARGET_ALPHA = 0.16f
