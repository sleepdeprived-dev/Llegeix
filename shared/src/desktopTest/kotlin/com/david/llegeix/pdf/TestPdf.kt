package com.david.llegeix.pdf

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

/**
 * A small PDF written byte by byte, so the tests know exactly what is on it.
 *
 * Page 1 is A4 portrait with three lines of Catalan in 24 pt Helvetica, the
 * first line's baseline 760 pt up from the bottom and 72 pt in. Page 2 is A4
 * landscape with one short line in the middle and wide blank margins all
 * round, for the crop. The outline names both pages.
 */
object TestPdf {

    const val LINE_1 = "La finestra del menjador"
    const val LINE_2 = "Ell parlava amb intel·ligència"
    const val LINE_3 = "La finestra era oberta"
    const val MIDDLE = "Al mig de la pàgina"

    fun write(file: File): File {
        val winAnsi = Charset.forName("windows-1252")
        fun page1() = """
            BT /F1 24 Tf 72 760 Td ($LINE_1) Tj 0 -32 Td ($LINE_2) Tj 0 -32 Td ($LINE_3) Tj ET
        """.trimIndent()
        fun page2() = "BT /F1 24 Tf 320 290 Td ($MIDDLE) Tj ET"

        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R /Outlines 7 0 R >>",
            "<< /Type /Pages /Kids [3 0 R 5 0 R] /Count 2 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 9 0 R >> >> >>",
            stream(page1()),
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Contents 6 0 R /Resources << /Font << /F1 9 0 R >> >> >>",
            stream(page2()),
            "<< /Type /Outlines /First 8 0 R /Last 10 0 R /Count 2 >>",
            "<< /Title (Primer capítol) /Parent 7 0 R /Next 10 0 R /Dest [3 0 R /Fit] >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
            "<< /Title (Segon) /Parent 7 0 R /Prev 8 0 R /Dest [5 0 R /Fit] >>",
        )

        val out = ByteArrayOutputStream()
        fun put(text: String) = out.write(text.toByteArray(winAnsi))
        put("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, body ->
            out.size().also { put("${index + 1} 0 obj\n$body\nendobj\n") }
        }
        val xref = out.size()
        put("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { put("%010d 00000 n \n".format(it)) }
        put("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        file.writeBytes(out.toByteArray())
        return file
    }

    private fun stream(content: String): String =
        "<< /Length ${content.toByteArray(Charset.forName("windows-1252")).size} >>\nstream\n$content\nendstream"
}
