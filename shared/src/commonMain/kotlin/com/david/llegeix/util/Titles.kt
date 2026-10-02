package com.david.llegeix.util

/**
 * Filename to display title: "El Petit Princep.pdf" becomes "El Petit Princep".
 *
 * Kept in one place because the database stores the real filename — the column
 * is called displayName and should mean it — while every screen shows the
 * trimmed form.
 */
fun pdfTitle(displayName: String): String =
    displayName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { displayName }
