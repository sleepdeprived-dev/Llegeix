package com.david.llegeix.platform

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The helper programs the Mac app carries, found and made runnable — the case
 * behind "the translator is not installed" in an app whose disk image had
 * dropped its helpers' executable bit.
 */
class BundledProgramsTest {

    @get:Rule val folder = TemporaryFolder()

    private val resources by lazy { folder.newFolder("resources") }
    private val data by lazy { folder.newFolder("data") }

    @After
    fun tearDown() {
        System.clearProperty("compose.application.resources.dir")
        System.clearProperty("llegeix.data")
    }

    private fun bundle(name: String, executable: Boolean): File =
        File(resources, name).apply {
            writeText("#!/bin/sh\necho ok\n")
            setExecutable(executable, false)
            System.setProperty("compose.application.resources.dir", resources.path)
            System.setProperty("llegeix.data", data.path)
        }

    @Test
    fun aRunnableHelperIsUsedWhereItIs() {
        val helper = bundle("ajudant", executable = true)
        assertEquals(helper, bundledProgram("ajudant", "llegeix.test.none"))
    }

    @Test
    fun aHelperThatCannotRunIsCopiedSomewhereItCan() {
        bundle("ajudant", executable = false)
        val program = bundledProgram("ajudant", "llegeix.test.none")!!
        assertEquals(File(data, "programs/ajudant"), program)
        assertTrue(program.canExecute())
        val process = ProcessBuilder(program.path).start()
        assertEquals("ok", process.inputStream.bufferedReader().readText().trim())
        assertEquals(0, process.waitFor())
    }

    @Test
    fun noHelperIsNoProgram() {
        bundle("altre", executable = true)
        assertNull(bundledProgram("ajudant", "llegeix.test.none"))
    }
}
