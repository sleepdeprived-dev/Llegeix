package com.david.llegeix.ui.common

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Scrolling that simply stops at the end.
 *
 * Android's stretch overscroll takes the whole list into a render effect and
 * squashes it while a finger keeps pulling. Inside a bottom sheet that is a
 * second thing deforming at the same time as the sheet's own drag handling is
 * deciding whether the gesture belongs to the list or to the sheet, and the two
 * fight: the contents of a long sheet — the document's contents list most of
 * all — judder and flash as the stretch is applied, handed over, and applied
 * again a frame later.
 *
 * Turning it off inside sheets is not a workaround for the flicker so much as
 * the right behaviour for them. A sheet is already a surface that answers to
 * being dragged; an edge that also bounces is a second answer to the same
 * gesture. Reaching the bottom of a list in a sheet should do nothing at all,
 * which is what this makes it do.
 *
 * The page itself keeps its overscroll: there the stretch is the only thing
 * that says a document has ended.
 */
@Composable
fun WithoutOverscroll(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalOverscrollFactory provides null, content = content)
}

/**
 * The app's bottom sheet: Material's, with the edge bounce turned off inside
 * it.
 *
 * Every sheet in Llegeix goes through this rather than calling
 * [ModalBottomSheet] directly, so that none of them can be added later without
 * the fix — which is how the contents list came to judder in the first place.
 * Everything else about it is Material's own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
    ) {
        val column = this
        WithoutOverscroll { column.content() }
    }
}
