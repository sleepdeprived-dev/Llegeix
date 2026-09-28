package com.david.llegeix.data.flashcards

import androidx.compose.ui.graphics.toPixelMap
import com.david.llegeix.data.db.LlegeixDatabase
import com.david.llegeix.data.db.entity.FlashcardEntity
import com.david.llegeix.data.db.openDesktop
import com.david.llegeix.data.settings.javaPrefsStore
import com.david.llegeix.platform.ContentRef
import com.david.llegeix.platform.DesktopAppFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.prefs.Preferences

/** Card pictures and backups on the Mac, in a scratch data folder. */
class DesktopFlashcardsTest {

    @get:Rule val folder = TemporaryFolder()

    private lateinit var database: LlegeixDatabase
    private lateinit var images: FlashcardImages
    private lateinit var repository: FlashcardRepository
    private val prefsNode = Preferences.userRoot().node("com/david/llegeix/test-${System.nanoTime()}")

    @Before
    fun setUp() {
        System.setProperty("llegeix.data", folder.root.path)
        val files = DesktopAppFiles(folder.root)
        database = LlegeixDatabase.openDesktop(File(folder.root, "llegeix.db"))
        images = FlashcardImages(files)
        repository = FlashcardRepository(database, images, FlashcardBackupFiles(files), FlashcardPrefs(javaPrefsStore(prefsNode)))
    }

    @After
    fun tearDown() {
        database.close()
        prefsNode.removeNode()
        System.clearProperty("llegeix.data")
    }

    private fun fixture(name: String): ContentRef =
        ContentRef(File(folder.root, name).apply { writeBytes(DesktopFlashcardsTest::class.java.getResource("/pictures/$name")!!.readBytes()) })

    @Test
    fun aSidewaysPhotoArrivesUpright() = runBlocking {
        val path = images.import(fixture("sideways.jpg"))
        assertTrue(path.endsWith(".jpg"))
        val picture = images.load(path, 1000)!!
        // 40 × 20 stored on its side: upright it is 20 wide and 40 tall, with
        // what was the left (red) now at the top.
        assertEquals(20, picture.width)
        assertEquals(40, picture.height)
        val top = picture.toPixelMap()[10, 5]
        val bottom = picture.toPixelMap()[10, 34]
        assertTrue("top is red: $top", top.red > 0.8f && top.blue < 0.2f)
        assertTrue("bottom is blue: $bottom", bottom.blue > 0.8f && bottom.red < 0.2f)
    }

    @Test
    fun aCutOutKeepsItsTransparency() = runBlocking {
        val path = images.import(fixture("cutout.png"))
        assertTrue(path.endsWith(".png"))
        val picture = images.load(path, 1000)!!
        assertEquals(0f, picture.toPixelMap()[2, 2].alpha)
        assertEquals(1f, picture.toPixelMap()[15, 15].alpha)
    }

    @Test
    fun loadingShrinksToWhatIsAskedFor() = runBlocking {
        val path = images.import(fixture("cutout.png"))
        val small = images.load(path, 10)!!
        assertTrue(small.width <= 15)
    }

    @Test
    fun aBackupRestoresCardsAndPictures() = runBlocking {
        repository.createDeck("Menjar")
        val deck = repository.observeDecks().first().single()
        val picture = images.import(fixture("sideways.jpg"))
        repository.saveCard(FlashcardEntity(deckId = deck.id, catalan = "poma", romanian = "măr", imagePath = picture))

        val copy = File(folder.root, "targetes.zip")
        assertEquals(1, repository.exportTo(ContentRef(copy)))

        repository.eraseEverything()
        assertTrue(repository.observeDecks().first().isEmpty())

        val result = repository.restoreFrom(ContentRef(copy))
        assertEquals(1, result.added)
        assertEquals(0, result.picturesLost)
        val restoredDeck = repository.observeDecks().first().single()
        val card = repository.observeCards(restoredDeck.id).first().single()
        assertEquals("poma" to "măr", card.catalan to card.romanian)
        assertNotNull(repository.loadImage(card.imagePath!!, 200))
    }
}
