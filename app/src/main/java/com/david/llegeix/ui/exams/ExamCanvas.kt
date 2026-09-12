package com.david.llegeix.ui.exams

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.david.llegeix.data.db.entity.ExamMarkEntity
import com.david.llegeix.data.db.entity.MarkKind
import com.david.llegeix.data.exam.MarkGeometry
import com.david.llegeix.data.exam.MarkPainter
import com.david.llegeix.pdf.TickBox
import kotlinx.coroutines.flow.first

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
    /** The text box in hand, if one on this page is selected. */
    selectedText: ExamMarkEntity?,
    editingText: Boolean,
    /**
     * True when this page is blank paper being written on as a document.
     *
     * The text tool means two different things and this is which one. See
     * [com.david.llegeix.ui.exams.WorkspaceState.isDocument].
     */
    documentMode: Boolean,
    render: suspend (index: Int, widthPx: Int) -> Bitmap?,
    onStrokeFinished: (List<MarkGeometry.Point>) -> Unit,
    onCreateText: (x: Float, y: Float) -> Unit,
    onSelectText: (ExamMarkEntity?) -> Unit,
    onStartEditingText: () -> Unit,
    onDoneEditingText: () -> Unit,
    onTextChanged: (ExamMarkEntity, String, Float) -> Unit,
    onTextEdited: (ExamMarkEntity, String, Float) -> Unit,
    onTextMoved: (ExamMarkEntity, Float, Float) -> Unit,
    onTextScaled: (ExamMarkEntity, Float) -> Unit,
    onTextRotated: (ExamMarkEntity, Float) -> Unit,
    onDeleteText: (ExamMarkEntity) -> Unit,
    onTick: (x: Float, y: Float, existing: ExamMarkEntity?) -> Unit,
    onErase: (ExamMarkEntity) -> Unit,
    onZoomChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
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
        // The page's drawn size in pixels: the text boxes are positioned in
        // fractions of it, and there is nothing else on screen that knows it.
        var pageSize by remember { mutableStateOf(IntSize.Zero) }

        // Writing on the page is two different situations, and they are told
        // apart here because they want two different pages.
        //
        // A box on a printed page is typed into where it sits. The page keeps
        // the size it has and the only thing that needs to move is whatever the
        // keyboard is covering. A blank page in the text tool *is* the field:
        // it is being written as a document, so it is fitted to the width and
        // scrolled through — and it is like that from the moment the tool is
        // chosen, whether or not the cursor happens to be in it this instant.
        //
        // That last clause is the fix for the worst of how typing used to feel.
        // The page changed size, changed alignment and jumped its scroll at the
        // moment the cursor arrived, and undid all three when the keyboard went
        // down; so every tap into the page re-laid out the thing being written
        // on, and the words moved under the finger that was aiming at them. Now
        // picking the tool settles the layout, and the keyboard only changes how
        // much of it can be seen at once.
        val writing = documentMode && canWrite
        val editingBox = editingText && canWrite && !documentMode
        val scrolling = writing || editingBox

        // How big the sheet is drawn, and it is two different answers.
        //
        // Reading, it is fitted to the space there is — both ways, not just
        // across. Fitting the width alone meant that anything which made the
        // middle of the screen shorter, such as opening the recording, left a
        // page taller than the room it had; and since there was nothing to
        // scroll, the top and bottom of it were simply cut off. A page with its
        // first line missing and no way to get it back is worse than a smaller
        // page.
        //
        // Written on, it is fitted to the width and allowed to be taller than
        // the screen. A keyboard takes half the height, and a page shrunk to fit
        // what is left would put the writing at a size nobody can read; and
        // since the width is the only thing this answer depends on, a keyboard
        // coming up or going down cannot resize the page or re-wrap a line of
        // it. It stays the size it is and moves instead.
        val sheet = PageFit.of(
            roomWide = (maxWidth - PageMargin * 2).coerceAtLeast(0.dp),
            roomTall = (maxHeight - PageMargin * 2).coerceAtLeast(0.dp),
            ratio = rendered.width.toFloat() / rendered.height.toFloat(),
            fillWidth = scrolling,
        )

        // Room above and below the page to push it into, while typing.
        //
        // Without it there was nothing to scroll whenever the page happened to
        // fit — which is exactly the case with a floating keyboard, because a
        // keyboard in its own little window reports no inset at all. The screen
        // does not get shorter, the page is not too tall for it, and the keys
        // sit on top of the writing with no way to move either. Half a screen
        // of slack at each end means the page can always be pushed clear,
        // whatever kind of keyboard is up and wherever it happens to be.
        val slack = if (scrolling) maxHeight * TYPING_SLACK else 0.dp
        val slackPx = with(density) { slack.roundToPx() }
        val pageScroll = rememberScrollState()

        // What the zoom adds below the page.
        //
        // The magnification is a layer transform, so the layout still measures
        // the page at its unmagnified height and the scroll it sits in runs out
        // halfway down a magnified one — the bottom of a zoomed page could not
        // be reached at all while writing on it. Declared as padding under the
        // sheet, because that is the one place a scroll container will believe
        // the extra height exists.
        val overhang = if (scrolling) {
            (sheet.height * (zoom - 1f)).coerceAtLeast(0.dp)
        } else {
            0.dp
        }

        // How far a magnified page has been pushed, in pixels.
        //
        // Zoom alone was half a feature. It grows the page from its top edge,
        // so at five times life size everything below the first third of the
        // page was off the bottom of the screen with no way to reach it — you
        // could magnify a question and then not read the answer box under it.
        // Sideways had the same hole and it was worse, because the page grows
        // from its middle: the left margin of every line — which is where
        // writing starts — was off the edge of the screen the moment the page
        // was magnified. The same two fingers that magnified it now move it
        // both ways.
        var panX by remember { mutableFloatStateOf(0f) }
        var panY by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(zoom) { if (zoom <= 1f) { panX = 0f; panY = 0f } }
        val viewportPx = with(density) { maxHeight.toPx() }
        val viewportWidePx = with(density) { maxWidth.toPx() }
        val marginPx = with(density) { PageMargin.toPx() }
        val pageTallPx = with(density) { sheet.height.toPx() }
        val pageWidePx = with(density) { sheet.width.toPx() }

        // Two fingers, read fresh every time they are put down.
        //
        // Not as a lambda handed to the gesture modifier and captured there: a
        // pointer-input handler keyed on nothing outlives the composition that
        // started it, so anything it closed over is whatever it was when the
        // page first appeared. That is what this used to do, and the cost was
        // exactly the complaint that prompted this release — the handler
        // believed nobody was writing, so a two-finger push moved the page as
        // if it were merely zoomed, clamped to the page's own bottom edge, and
        // the last lines of an essay could not be pushed out from under the
        // keys at all.
        val panning by rememberUpdatedState<(Float, Float) -> Unit> { dx, dy ->
            // Sideways is the same in both modes: only as far as there is page
            // hidden past the edge of the screen, and the page is centred, so
            // half of that is hidden on each side.
            val hiddenWide = ((pageWidePx * zoom - viewportWidePx) / 2f).coerceAtLeast(0f)
            if (hiddenWide > 0f) panX = (panX + dx).coerceIn(-hiddenWide, hiddenWide)
            if (scrolling) {
                // Into the scroll, which is the thing that has the slack above
                // and below the page in it. A layer translation would stop at
                // the page's own edge and leave the bottom line under the keys.
                pageScroll.dispatchRawDelta(-dy)
            } else {
                // Only as far as there is page to see. A magnified page that
                // can be flung off the screen entirely is a page somebody has
                // to hunt for.
                val hidden = (pageTallPx * zoom - viewportPx).coerceAtLeast(0f)
                panY = (panY + dy).coerceIn(-hidden, 0f)
            }
        }

        // Where the cursor is, in the page's own pixels, as the editor last
        // measured it. States rather than parameters because they are written
        // on every keystroke and read by one effect, and nothing about the page
        // needs to be composed again when the cursor moves.
        val caretTop = remember { mutableFloatStateOf(Float.NaN) }
        val caretBottom = remember { mutableFloatStateOf(Float.NaN) }

        // Starting at the page's own top rather than in the slack above it.
        // After the first layout, because until the scroll container has been
        // measured its range is zero and a jump into it is silently clamped
        // back to the top — which would open every essay looking at half a
        // screen of empty desk.
        LaunchedEffect(scrolling) {
            if (!scrolling) return@LaunchedEffect
            // A page that is scrolled is never also translated: the two would
            // add up and the arithmetic that follows the cursor would be out by
            // however far the last pinch had pushed the page.
            panY = 0f
            // And the cursor's last position is forgotten, so that arriving on
            // a page to write on it cannot begin by scrolling to where some
            // other page's cursor happened to be.
            caretTop.floatValue = Float.NaN
            caretBottom.floatValue = Float.NaN
            snapshotFlow { pageScroll.maxValue }.first { it > 0 }
            pageScroll.scrollTo(slackPx)
        }

        // A box being typed into has no cursor of its own to report — the field
        // inside it is the box — so the box itself is what has to be kept in
        // view. Its top line is close enough to a cursor for this purpose.
        LaunchedEffect(editingBox, selectedText?.id, pageSize) {
            val box = selectedText?.takeIf { editingBox } ?: return@LaunchedEffect
            val tall = pageSize.height.toFloat()
            if (tall <= 0f) return@LaunchedEffect
            caretTop.floatValue = box.y * tall
            caretBottom.floatValue = (box.y + box.height) * tall
        }

        // Keeping the line being written in front of the reader.
        //
        // This is the whole difference between typing on a page and typing in a
        // text field. A field lives in a list that knows where its cursor is; a
        // page is a fixed sheet with a field pinned over part of it, and nothing
        // in that arrangement moves the sheet when the writing reaches the
        // bottom of the screen. So the editor says where the cursor is and the
        // page is scrolled to keep it in sight.
        //
        // The arithmetic is in [CaretFollow], which is where it is explained and
        // where it is tested. This is the plumbing: watch where the editor says
        // the cursor is, and move the page when it says so.
        LaunchedEffect(scrolling, slackPx, viewportPx, marginPx) {
            if (!scrolling) return@LaunchedEffect
            snapshotFlow { pageScroll.maxValue }.first { it > 0 }
            var placed = false
            snapshotFlow { caretTop.floatValue to caretBottom.floatValue }.collect { (top, bottom) ->
                if (top.isNaN()) return@collect
                val first = !placed
                placed = true
                val target = CaretFollow.scrollFor(
                    caretTopPx = top,
                    caretBottomPx = bottom,
                    pageTopPx = marginPx + slackPx,
                    zoom = currentZoom,
                    scrolledPx = pageScroll.value,
                    furthestPx = pageScroll.maxValue,
                    viewportPx = viewportPx,
                    lowestPx = slackPx,
                    alwaysPlace = first,
                ) ?: return@collect
                // The first one is a jump. Coming back to a half-written page
                // with the cursor ten lines down, an animation would be the app
                // scrolling through the reader's own essay at them; after that
                // the page is already where they are looking, and a move they
                // did not ask for should be slow enough to be read as a move.
                if (first) pageScroll.scrollTo(target) else pageScroll.animateScrollTo(target)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scrolling) Modifier.verticalScroll(pageScroll) else Modifier)
                // Two fingers move and magnify the page, one finger writes on
                // it. On the initial pass, which is the whole point: the thing
                // most in need of moving out from under a keyboard is a text
                // field, and a text field takes every one-finger drag over it
                // for its own cursor. Claiming multi-touch before the field
                // sees it is what makes the page movable while it is being
                // written on — and one-finger gestures are never touched, so
                // nothing about writing changes.
                .twoFingerZoomAndPan(
                    currentZoom = { currentZoom },
                    onZoomChanged = onZoomChanged,
                    onPan = { dx, dy -> panning(dx, dy) },
                ),
            contentAlignment = if (scrolling) Alignment.TopCenter else Alignment.Center,
        ) {
            // The paper as a sheet of paper: rounded, lifted off the ground
            // behind it, and edged with a hairline.
            //
            // The rounding and the shadow are cosmetic and the hairline is not.
            // A darkened page is very nearly the colour of the ground it sits
            // on, so without an edge there was no way to see where the sheet
            // stopped — which matters, because where the sheet stops is where
            // writing stops being saved.
            //
            // All of it rides on the same layer as the zoom so nothing can
            // drift out of register with the marks: one transform, applied
            // once, to the image and the canvas together.
            val cardShadow = with(density) { PageLift.toPx() }
            Box(
                modifier = Modifier
                    .padding(
                        start = PageMargin,
                        end = PageMargin,
                        top = PageMargin + slack,
                        bottom = PageMargin + slack + overhang,
                    )
                    .size(width = sheet.width, height = sheet.height)
                    .onSizeChanged { pageSize = it }
                    .graphicsLayer {
                        scaleX = currentZoom
                        scaleY = currentZoom
                        translationX = panX
                        translationY = panY
                        // Anchored at the top: a page's writing starts there, and a
                        // centre-anchored zoom throws you into the middle of it.
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        shadowElevation = cardShadow
                        shape = PageShape
                        clip = true
                    },
            ) {
                Image(
                    bitmap = rendered.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (darkPage) DarkPaper else Color.White),
                )

                // Which mark this page *is*, when it is blank paper being written
                // as a document. The same rule the ViewModel uses when it makes and
                // tidies these — see [documentMark] — so the field the reader types
                // into and the row the app saves into are never two different rows.
                val document = if (documentMode) marks.documentMark() else null

                // Where the box in hand is at this instant, shared by the
                // frame that is being dragged and the canvas that paints the
                // words inside it. Keyed on the box, so picking up a different
                // one starts from where that one actually is.
                val live = remember(selectedText?.id) {
                    TextBoxLive(selectedText?.rotation ?: 0f)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawingGestures(
                            // In document mode the field above owns every tap: the
                            // page is one text area, so there is nothing for a tap
                            // on the canvas to place.
                            enabled = canWrite && !documentMode,
                            tool = { currentTool },
                            wet = wet,
                            marks = { currentMarks },
                            onStrokeFinished = onStrokeFinished,
                            onCreateText = onCreateText,
                            onSelectText = onSelectText,
                            onTick = onTick,
                            onErase = onErase,
                            boxes = { currentBoxes },
                        ),
                ) {
                    // The found boxes, outlined faintly, and only while the tick
                    // tool is in hand. They are a hint about where a tap will land,
                    // not a decoration and not a claim that these are all of them.
                    if (tool == ExamTool.TICK) drawTickTargets(tickBoxes)

                    // Everything except whatever is in hand, which is drawn
                    // after this and from somewhere else.
                    drawMarks(
                        when {
                            // On blank paper the field is showing the words, so
                            // painting them underneath as well would double them.
                            // Only that one mark, though: anything else typed on
                            // this page is not in the field and has to be drawn, or
                            // it is simply gone from the reader's page.
                            document != null -> marks.filterNot { it.id == document.id }
                            selectedText != null ->
                                marks.filterNot { it.id == selectedText.id }
                            else -> marks
                        },
                    )

                    // The box in hand, painted where the finger has it rather
                    // than where the database still has it.
                    //
                    // This is what made dragging text feel wrong. The frame and
                    // the handles are composables and followed the finger; the
                    // words are painted here, from a row that is not written
                    // until the finger comes up — so an empty rectangle slid
                    // across the page and the answer stayed behind and then
                    // teleported. Reading the live geometry inside the draw
                    // means a drag costs this canvas a redraw and nothing else,
                    // and the words move with their own frame.
                    //
                    // Not while it is being typed into: then the field above is
                    // showing them and painting them here as well would double
                    // every letter.
                    // And never on blank paper, where the page is the field and
                    // the box in hand belongs to some other page: the branch
                    // above kept it in the list, so painting it here as well
                    // would draw it twice.
                    if (document == null && selectedText != null && !editingText) {
                        drawMark(live.preview(selectedText, size.width, size.height))
                    }

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

                // Above the canvas, so the field or the box's handles take the
                // touches rather than the page underneath.
                when {
                    document != null && canWrite && pageSize != IntSize.Zero -> ExamDocumentEditor(
                        mark = document,
                        pageWidthPx = pageSize.width.toFloat(),
                        pageHeightPx = pageSize.height.toFloat(),
                        focused = editingText,
                        onFocused = onStartEditingText,
                        onDone = onDoneEditingText,
                        onChanged = { text, height -> onTextChanged(document, text, height) },
                        onCaretMoved = { top, bottom ->
                            caretTop.floatValue = top
                            caretBottom.floatValue = bottom
                        },
                    )

                    selectedText != null && canWrite && pageSize != IntSize.Zero -> ExamTextBox(
                        mark = selectedText,
                        pageWidthPx = pageSize.width.toFloat(),
                        pageHeightPx = pageSize.height.toFloat(),
                        editing = editingText,
                        live = live,
                        onStartEditing = onStartEditingText,
                        onDoneEditing = onDoneEditingText,
                        onTextChanged = { text, height ->
                            onTextChanged(selectedText, text, height)
                        },
                        onFinishEditing = { text, height ->
                            onTextEdited(selectedText, text, height)
                        },
                        onMove = { x, y -> onTextMoved(selectedText, x, y) },
                        onScale = { factor -> onTextScaled(selectedText, factor) },
                        onRotate = { degrees -> onTextRotated(selectedText, degrees) },
                        onDelete = { onDeleteText(selectedText) },
                    )
                }

                // Last, so it draws over the page's own edge rather than under
                // it. It takes no touches, being a plain outline.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = PageShape,
                        ),
                )
            }
        }
    }
}

/** The corner a sheet of paper gets, and how far it is lifted off the desk. */
private val PageShape = RoundedCornerShape(6.dp)
private val PageLift = 4.dp

/** The desk showing round the sheet. */
private val PageMargin = 12.dp

/**
 * How much empty desk there is above and below the page while it is typed on,
 * as a share of the screen. Enough that the page can always be pushed clear of
 * a keyboard sitting anywhere over it.
 */
private const val TYPING_SLACK = 0.5f

/** What an inverted page sits on while it is still loading. */
private val DarkPaper = Color(0xFF0E0E0E)

/**
 * Two fingers move and magnify the page. One finger is left entirely alone.
 *
 * The same reasoning as the reader's [com.david.llegeix.ui.reader.pinchToZoom]:
 * a helper that reports pan alongside zoom consumes one-finger drags too, and
 * here a one-finger drag is the pen. So this watches for a second finger and
 * does nothing at all until it arrives.
 *
 * It runs on [PointerEventPass.Initial], which goes outside-in, so it sees a
 * gesture before anything inside the page does. That is what makes it work over
 * a text field: a field takes every one-finger drag across it for its own
 * cursor and would take a two-finger one as well, and the page that most needs
 * moving out from under a keyboard is precisely the page with a field on it.
 * Nothing is consumed unless a second finger is down, so the pen, the eraser
 * and the cursor all behave exactly as they did.
 */
private fun Modifier.twoFingerZoomAndPan(
    currentZoom: () -> Float,
    onZoomChanged: (Float) -> Unit,
    /** How far the two fingers moved together, across and down. */
    onPan: (Float, Float) -> Unit,
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val pinch = event.calculateZoom()
                if (pinch != 1f) {
                    onZoomChanged(
                        (currentZoom() * pinch).coerceIn(
                            ExamWorkspaceViewModel.MIN_ZOOM,
                            ExamWorkspaceViewModel.MAX_ZOOM,
                        ),
                    )
                }
                val pan = event.calculatePan()
                if (pan.x != 0f || pan.y != 0f) onPan(pan.x, pan.y)
                event.changes.forEach { if (it.pressed) it.consume() }
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
    onCreateText: (x: Float, y: Float) -> Unit,
    onSelectText: (ExamMarkEntity?) -> Unit,
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
                // A tap on a box that is already there picks it up; a tap on
                // bare paper puts a new one down. Both are what a tap means in
                // every drawing program, and neither needs a mode.
                ExamTool.TEXT -> {
                    val existing = marks().textAt(x, y)
                    if (existing != null) onSelectText(existing) else onCreateText(x, y)
                }
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

/**
 * The topmost text box under a point.
 *
 * Padded like the eraser's test, and for the same reason: an empty or one-word
 * box is a small target, and a tap that misses it silently makes a second box
 * on top of the first.
 */
private fun List<ExamMarkEntity>.textAt(x: Float, y: Float): ExamMarkEntity? =
    lastOrNull { mark ->
        mark.kind == MarkKind.TEXT.name &&
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

/** One mark, for the box being moved: the same painter, without a list. */
private fun DrawScope.drawMark(mark: ExamMarkEntity) {
    drawIntoCanvas { canvas ->
        MarkPainter.draw(canvas.nativeCanvas, mark, size.width, size.height)
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
