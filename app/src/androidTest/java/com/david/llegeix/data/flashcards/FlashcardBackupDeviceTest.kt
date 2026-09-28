package com.david.llegeix.data.flashcards

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.david.llegeix.LlegeixApp
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.platform.ContentRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A picture comes in, a backup goes out and comes back, on the phone itself:
 * through the content resolver, the app's files and its real database.
 */
@RunWith(AndroidJUnit4::class)
class FlashcardBackupDeviceTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as LlegeixApp

    @Test
    fun aBackupRestoresCardsAndPictures() = runBlocking {
        val repository = app.flashcardRepository
        repository.eraseEverything()

        val photo = File(app.cacheDir, "photo.jpg")
        Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            .let { bitmap -> photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        val picture = repository.importImage(ContentRef(Uri.fromFile(photo)))
        assertTrue(picture.endsWith(".jpg"))

        repository.createDeck("Menjar")
        val deck = repository.observeDecks().first().single()
        repository.saveCard(FlashcardEntity(deckId = deck.id, catalan = "poma", romanian = "măr", imagePath = picture))

        val copy = File(app.cacheDir, "targetes.zip")
        assertEquals(1, repository.exportTo(ContentRef(Uri.fromFile(copy))))

        repository.eraseEverything()
        val result = repository.restoreFrom(ContentRef(Uri.fromFile(copy)))
        assertEquals(1, result.added)
        assertEquals(0, result.picturesLost)
        val card = repository.observeCards(repository.observeDecks().first().single().id).first().single()
        assertEquals("poma" to "măr", card.catalan to card.romanian)
        val loaded = repository.loadImage(card.imagePath!!, 200)!!
        assertEquals(40, loaded.width)

        repository.eraseEverything()
    }
}
