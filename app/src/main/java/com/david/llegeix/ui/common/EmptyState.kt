package com.david.llegeix.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * What a screen shows when it has nothing to show.
 *
 * Centred in the space available rather than stacked under the app bar: content
 * pinned to the top with a large void beneath reads as "something is missing"
 * instead of "there is nothing here yet". The optional actions are ordered, not
 * equal — one obvious next step, with anything else offered more quietly
 * underneath, so an empty screen asks for one decision rather than several.
 *
 * The disc behind the mark is in the reader's own accent rather than in grey.
 * An empty screen is the one place the app has nothing of the reader's to show,
 * which made it also the one place that looked like nobody's: a grey disc, grey
 * text and a grey glyph, in an app whose every other surface is tinted by a
 * colour they chose. It is a small thing and it changes what the screen says —
 * grey on grey reads as switched off, and this is not an error.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    primaryAction: (@Composable () -> Unit)? = null,
    secondaryAction: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.xxl)
            // Lifted slightly above the true centre: optically centred beats
            // measured centre once an app bar is taking up the top.
            .padding(bottom = Space.huge),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            // A strong heading and a quiet paragraph: the heading says what is
            // missing, and the paragraph what to do about it.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (icon != null) Space.xl else 0.dp),
        )

        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Capped so a long line breaks into a readable column rather than
            // running the full width of the screen.
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(top = Space.md),
        )

        if (primaryAction != null) {
            Box(modifier = Modifier.padding(top = Space.xxl)) { primaryAction() }
        }
        if (secondaryAction != null) {
            Box(modifier = Modifier.padding(top = Space.sm)) { secondaryAction() }
        }
    }
}
