package com.david.llegeix.data.db

import com.david.llegeix.data.db.entity.WordBookmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The Mac's database opens, keeps what is written to it, and still has it after a restart. */
class DesktopDatabaseTest {

    @get:Rule val folder = TemporaryFolder()

    @Test
    fun wordsSurviveReopening() = runBlocking {
        val file = folder.root.resolve("Llegeix/llegeix.db")

        val first = LlegeixDatabase.openDesktop(file)
        first.wordBookmarkDao().insert(
            WordBookmarkEntity(
                word = "finestra",
                translation = "fereastră",
                ipa = "fiˈnɛstɾə",
                context = "Obre la finestra, si us plau.",
                documentUri = null,
                displayName = null,
                pageIndex = 0,
                lineNumber = 0,
            ),
        )
        first.close()

        val second = LlegeixDatabase.openDesktop(file)
        val words = second.wordBookmarkDao().observeAll().first()
        second.close()

        assertEquals(listOf("finestra" to "fereastră"), words.map { it.word to it.translation })
    }
}
