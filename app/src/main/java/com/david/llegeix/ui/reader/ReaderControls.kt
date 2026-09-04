package com.david.llegeix.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R
import com.david.llegeix.data.settings.PageTint
import com.david.llegeix.data.settings.ReadingMode
import com.david.llegeix.pdf.PdfOutlineEntry
import com.david.llegeix.ui.common.Space

/**
 * How far through the document you are, and the way to anywhere else in it.
 *
 * It began as a two-pixel hairline that only said where you were. That is worth
 * having — a line you have to do nothing to read is the difference between
 * knowing you are near the end and working it out — but it left the reader with
 * no way at all to reach page 180 of a 300-page book except to swipe there.
 * Now the same line is the control: drag it, or tap anywhere along it.
 *
 * The thumb only appears while it is being used. Sitting there permanently it
 * would be a second thing competing with the page for attention, on a screen
 * whose whole argument is that there should be as few of those as possible.
 */
@Composable
fun PageScrubber(
    page: Int,
    pageCount: Int,
    onGoToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return

    val haptics = LocalHapticFeedback.current
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    val settled = ((page + 1).toFloat() / pageCount).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = settled, label = "readingProgress")
    val fraction = if (isScrubbing) scrubFraction else animated

    // The page the scrub is currently over, which the bubble names. Counted
    // from the fraction rather than from the pager, because the pager is
    // deliberately not being driven until the finger lifts: turning three
    // hundred pages one at a time under a dragging thumb renders every one of
    // them and turns a scrub into a slideshow.
    val scrubPage = (scrubFraction * pageCount).toInt().coerceIn(0, pageCount - 1)

    fun fractionAt(x: Float): Float =
        if (widthPx <= 0f) 0f else (x / widthPx).coerceIn(0f, 1f)

    fun pageAt(x: Float): Int =
        (fractionAt(x) * pageCount).toInt().coerceIn(0, pageCount - 1)

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScrubberHeight)
                .background(MaterialTheme.colorScheme.surface)
                // Measured here rather than read inside the gesture handlers.
                // Those are keyed on the page count alone, deliberately, so they
                // are not torn down mid-drag — which also means they are not
                // restarted when the screen is rotated, and a width captured
                // inside one of them went on describing the old screen. Every
                // tap and every scrub then landed on the wrong page, in a way
                // that only ever showed up after a rotation.
                .onSizeChanged { widthPx = it.width.toFloat() }
                .pointerInput(pageCount) {
                    detectTapGestures { offset ->
                        onGoToPage(pageAt(offset.x))
                    }
                }
                .pointerInput(pageCount) {
                    var lastPage = -1
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            isScrubbing = true
                            scrubFraction = fractionAt(start.x)
                            lastPage = pageAt(start.x)
                        },
                        onDragEnd = {
                            isScrubbing = false
                            onGoToPage((scrubFraction * pageCount).toInt())
                        },
                        onDragCancel = { isScrubbing = false },
                    ) { change, _ ->
                        change.consume()
                        scrubFraction = fractionAt(change.position.x)
                        // A tick per page crossed, which is what makes a scrub
                        // feel like it is moving through something rather than
                        // sliding over glass. On a long document the pages go
                        // by faster than the ticks can be told apart, and it
                        // reads as texture, which is the right answer too.
                        val here = (scrubFraction * pageCount).toInt()
                        if (here != lastPage) {
                            lastPage = here
                            haptics.performHapticFeedback(
                                HapticFeedbackType.SegmentFrequentTick,
                            )
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(TrackHeight)
                    // Quiet on purpose while it is only reporting; full strength
                    // the moment it is being used as a control.
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isScrubbing) 1f else 0.45f,
                        ),
                    ),
            )
            if (isScrubbing) {
                val offsetX = with(LocalDensity.current) {
                    (widthPx * fraction).toDp() - ThumbSize / 2
                }
                Box(
                    modifier = Modifier
                        .offset(x = offsetX)
                        .size(ThumbSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        // Over the page rather than in the strip, because there is no room in
        // two pixels for a number and the number is only ever wanted for the
        // second the thumb is down.
        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = Space.lg)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.inverseSurface)
                    .padding(horizontal = Space.lg, vertical = Space.sm),
            ) {
                Text(
                    text = stringResource(
                        R.string.reader_page_position,
                        scrubPage + 1,
                        pageCount,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }
    }
}

private val ScrubberHeight = 20.dp
private val TrackHeight = 2.dp
private val ThumbSize = 14.dp

/**
 * The document's own table of contents.
 *
 * The most book-like thing the reader was missing. A PDF built from anything
 * but a scanner carries one, PDFium has always been able to read it, and until
 * now the app simply never asked — which meant the only way to reach chapter
 * nine of a novel was to know its page number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(
    outline: List<PdfOutlineEntry>,
    currentPage: Int,
    onGoToPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()

    // Opened at where you are, not at the beginning. A contents that always
    // starts at chapter one is a contents you have to scroll through to find
    // out where you already were.
    LaunchedEffect(outline, currentPage) {
        val here = outline.indexOfLast { it.pageIndex <= currentPage }
        if (here > 0) listState.scrollToItem(here)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.reader_contents),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = Space.screen)
                    .padding(bottom = Space.md),
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = Space.xxl),
            ) {
                items(outline, key = { it.title + "@" + it.pageIndex + "#" + it.depth }) { entry ->
                    // The section you are in is marked, so the sheet answers
                    // "where am I" as well as "where can I go".
                    val isHere = entry == outline.lastOrNull { it.pageIndex <= currentPage }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onGoToPage(entry.pageIndex)
                                onDismiss()
                            }
                            .padding(
                                start = Space.screen + (Space.lg * entry.depth),
                                end = Space.screen,
                            )
                            .padding(vertical = Space.md),
                    ) {
                        Text(
                            text = entry.title,
                            style = if (entry.depth == 0) {
                                MaterialTheme.typography.bodyLarge
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            fontWeight = if (isHere) FontWeight.SemiBold else null,
                            color = if (isHere) {
                                MaterialTheme.colorScheme.primary
                            } else if (entry.depth == 0) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${entry.pageIndex + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Space.md),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Everything about how the page is drawn, in one place.
 *
 * The bar used to carry these as separate buttons — zoom, invert — beside find
 * and the bookmark, which left a book's title about half the width of the
 * screen and still had nowhere to put the two settings that matter most on a
 * phone: trimming the margins, and scrolling instead of paging. They are
 * settings rather than actions, they are changed rarely and together, and a
 * sheet is where settings belong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySheet(
    tint: PageTint,
    cropMargins: Boolean,
    readingMode: ReadingMode,
    markSavedWords: Boolean,
    zoom: Float,
    onTintChosen: (PageTint) -> Unit,
    onToggleCrop: () -> Unit,
    onReadingModeChosen: (ReadingMode) -> Unit,
    onToggleMarkSavedWords: () -> Unit,
    onZoomChosen: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Space.screen)
                .padding(bottom = Space.xxl),
        ) {
            Text(
                text = stringResource(R.string.reader_display),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Space.lg),
            )

            SheetLabel(stringResource(R.string.reader_tint))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PageTint.entries.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        selected = candidate == tint,
                        onClick = { onTintChosen(candidate) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = PageTint.entries.size,
                        ),
                    ) {
                        Text(stringResource(candidate.labelRes))
                    }
                }
            }

            SheetLabel(
                text = stringResource(R.string.reader_mode),
                modifier = Modifier.padding(top = Space.xl),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ReadingMode.entries.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        selected = candidate == readingMode,
                        onClick = { onReadingModeChosen(candidate) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ReadingMode.entries.size,
                        ),
                    ) {
                        Text(stringResource(candidate.labelRes))
                    }
                }
            }

            SheetRow(
                title = stringResource(R.string.reader_crop),
                summary = stringResource(R.string.reader_crop_summary),
                checked = cropMargins,
                onToggle = onToggleCrop,
            )

            SheetRow(
                title = stringResource(R.string.reader_mark_saved),
                summary = stringResource(R.string.reader_mark_saved_summary),
                checked = markSavedWords,
                onToggle = onToggleMarkSavedWords,
            )

            SheetLabel(
                text = stringResource(R.string.reader_zoom),
                modifier = Modifier.padding(top = Space.xl),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                modifier = Modifier.padding(top = Space.sm),
            ) {
                ZoomSteps.forEach { step ->
                    ZoomChip(
                        step = step,
                        selected = kotlin.math.abs(step - zoom) < 0.01f,
                        onClick = { onZoomChosen(step) },
                    )
                }
            }
        }
    }
}

/** The zoom levels the sheet offers, matching the reader's own steps. */
private val ZoomSteps = listOf(1f, 1.5f, 2f, 3f)

@Composable
private fun ZoomChip(step: Float, selected: Boolean, onClick: () -> Unit) {
    val label = if (step % 1f == 0f) "${step.toInt()}×" else "$step×"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.sm),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** One switched setting in the sheet, with the whole row as the target. */
@Composable
private fun SheetRow(
    title: String,
    summary: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xl)
            .clickable(onClick = onToggle),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Space.xs),
            )
        }
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun SheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = Space.sm),
    )
}
