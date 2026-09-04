package com.david.llegeix.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.david.llegeix.R

/**
 * The one search field in the app.
 *
 * There were three of them — the library's, the saved words', and the reader's
 * find bar — written separately and drifting apart: different heights,
 * different shapes, a clear button on two of them and not the third. Searching
 * is the same act wherever you do it, so it should be the same object, and
 * fixing it once should fix it everywhere.
 *
 * Compact rather than Material's default 56dp box with a floating label.
 * Search is something you reach for occasionally; it should never be the
 * tallest thing on the screen.
 */
@Composable
fun SearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Sits before the clear button — the reader puts its match count here. */
    trailing: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /**
     * Set where the field commits rather than filters as you type.
     *
     * The library and the saved words narrow a list on every keystroke, so
     * there is nothing for a keyboard key to do and they leave this null. The
     * dictionary has to be told when the word is finished, so it gets the
     * keyboard's search key.
     */
    onSubmit: (() -> Unit)? = null,
    /**
     * Called as the field takes and loses focus.
     *
     * What the recent searches hang off: they belong to the act of searching
     * rather than to the screen, and a screen whose field is empty most of the
     * time would otherwise wear them permanently.
     */
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        keyboardOptions = KeyboardOptions(
            imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(50),
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.invoke()
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.library_clear_search),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        modifier = modifier
            .heightIn(min = 42.dp)
            .then(
                if (onFocusChanged != null) {
                    Modifier.onFocusChanged { onFocusChanged(it.isFocused) }
                } else {
                    Modifier
                },
            )
            .then(
                if (focusRequester != null) {
                    Modifier.focusRequester(focusRequester)
                } else {
                    Modifier
                },
            ),
    )
}
