package com.david.llegeix.ui.flashcards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.david.llegeix.LlegeixApp

/**
 * A card's picture, read off the main thread at about the size it is drawn.
 *
 * Holds its space with a quiet fill while the file is read, so a list of cards
 * does not jump as their pictures arrive one by one.
 *
 * @param maxEdge roughly how many pixels across it is drawn at. A thumbnail
 *   asks for a fraction of the stored picture, and gets it decoded that small.
 */
@Composable
fun CardImage(
    path: String,
    maxEdge: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val flashcards = remember(context) {
        (context.applicationContext as LlegeixApp).flashcardRepository
    }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, maxEdge) {
        value = flashcards.loadImage(path, maxEdge)?.asImageBitmap()
    }

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
