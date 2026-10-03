package com.david.llegeix.pdf

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import com.david.llegeix.platform.bundledProgram
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

actual fun pageTextRecognizer(): PageTextRecognizer = VisionRecognizer()

/**
 * Apple's Vision, through llegeix-ocr (tools/macos/build-ocr.sh): the page goes
 * in as a PNG, the lines come back as JSON. On the Mac, offline, free, as ML
 * Kit is on the phone.
 *
 * The first page read after the Mac starts waits while Vision loads its
 * models; every page after that takes well under a second.
 */
private class VisionRecognizer : PageTextRecognizer {

    override suspend fun recognise(image: ImageBitmap): List<RecognisedLine> = withContext(Dispatchers.IO) {
        val png = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
            ?: throw IOException("The page could not be encoded")
        val process = ProcessBuilder(helper().path)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        try {
            process.outputStream.use { it.write(png) }
            val output = process.inputStream.readBytes().decodeToString()
            if (!process.waitFor(TIMEOUT_S, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw IOException("Vision could not read the page")
            }
            parseLines(output)
        } finally {
            process.destroy()
        }
    }

    override fun close() = Unit

    private fun helper(): File = bundledProgram("llegeix-ocr", "llegeix.ocr")
        ?: throw IOException("llegeix-ocr was not found: run tools/macos/build-ocr.sh")

    private companion object {
        /** The first read of a run loads Vision's models, which can take half a minute. */
        const val TIMEOUT_S = 90L
    }
}

/** llegeix-ocr's JSON as lines and words. */
internal fun parseLines(json: String): List<RecognisedLine> {
    val lines = JSONArray(json)
    return (0 until lines.length()).map { index ->
        val line = lines.getJSONObject(index)
        val words = line.getJSONArray("words")
        RecognisedLine(
            text = withMiddleDots(line.getString("text")),
            box = rectOf(line.getJSONObject("box")),
            words = (0 until words.length()).map { w ->
                val word = words.getJSONObject(w)
                RecognisedWord(withMiddleDots(word.getString("text")), rectOf(word.getJSONObject("box")))
            },
        )
    }
}

private fun rectOf(box: JSONObject): Rect {
    val x = box.getDouble("x").toFloat()
    val y = box.getDouble("y").toFloat()
    return Rect(x, y, x + box.getDouble("w").toFloat(), y + box.getDouble("h").toFloat())
}

/**
 * Catalan's geminate l·l, which Vision — reading in French or Spanish, having
 * no Catalan of its own — takes for a hyphen or a full stop. Left as it reads
 * it, "intel-ligència" is a word no dictionary knows.
 */
internal fun withMiddleDots(text: String): String = GEMINATE.replace(text, "$1·$2")

private val GEMINATE = Regex("([lL])[-.‐‑•∙⋅]([lL])")
