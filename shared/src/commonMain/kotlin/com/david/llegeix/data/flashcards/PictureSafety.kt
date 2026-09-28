package com.david.llegeix.data.flashcards

import java.text.Normalizer
import java.util.Locale

/**
 * Keeping adult and graphic pictures out of the photo suggestions.
 *
 * Openverse already leaves out what it knows to be mature, and the app asks
 * it to. But that flag is only as good as the people who set it on each photo,
 * and a flag that is missing lets a picture through. So the app adds two
 * checks of its own, in layers:
 *
 *  1. a search for a word on the list is never sent at all;
 *  2. every photo that comes back is checked by its title and tags, and dropped
 *     if either carries a word on the list — even when nothing flagged it.
 *
 * This makes an unsuitable photo very unlikely; it cannot make it impossible,
 * because no filter over a public photo index can. That is why pictograms are
 * the default and photos are only searched when the reader asks for them:
 * ARASAAC's pictograms are drawn and curated by one team, and the ones it marks
 * as sexual or violent are filtered out separately.
 *
 * Words are matched from their start, so one entry covers its forms — *nude*
 * also stops *nudes* and *nudity* — without matching the middle of an innocent
 * word: *Sussex* is not stopped by *sex*, which is matched only as a whole word
 * so that *sextant* goes through too. The list is also checked against the
 * vocabulary it sits next to: *breasts* is not on it, because a flashcard for
 * *pit de pollastre* wants photos of chicken breasts, and *gol* is not, because
 * in Catalan it is a goal.
 */
object PictureSafety {

    /** Would searching for [text] be refused? */
    fun isBlockedQuery(text: String): Boolean = mentionsBlocked(text)

    /** Should a photo with this title and these tags be left out? */
    fun isBlockedPhoto(title: String, tags: List<String>): Boolean =
        mentionsBlocked(title) || tags.any(::mentionsBlocked)

    private fun mentionsBlocked(text: String): Boolean {
        if (text.isBlank()) return false
        val folded = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(MARKS, "")
        return BLOCKED.containsMatchIn(folded)
    }

    private val MARKS = Regex("\\p{Mn}+")

    /**
     * Stems, matched at the start of a word. English first, since that is what
     * photos are searched and tagged in; then the Catalan, Romanian and Spanish
     * a query or a tag can also arrive in.
     */
    private val STEMS = listOf(
        // Sexual content.
        "sex ", "sexe ", "sexy", "porn", "xxx", "nsfw", "nude", "nudity", "nudist", "naked",
        "topless", "bottomless", "erotic", "erotica", "fetish", "bdsm", "bondage",
        "lingerie", "stripper", "striptease", "strip club", "escort", "hentai",
        "orgasm", "masturbat", "genital", "penis", "vagina", "vulva", "nipple",
        "boob", "buttocks", "playboy", "onlyfans", "camgirl", "kinky",
        "lewd", "obscene", "adult content", "adult film", "sensual",
        // Graphic violence and death.
        "gore", "gory", "corpse", "cadaver", "dead body", "beheading", "decapitat",
        "mutilat", "dismember", "torture", "execution", "lynching", "massacre",
        "suicide", "self harm", "self-harm", "autopsy", "bloodbath",
        // Catalan.
        "nu ", "nua ", "nus ", "nues ", "despulla", "eroti", "pornograf", "sexual",
        "cadaver", "tortura",
        // Romanian.
        "nud", "goala", "erotic", "pornograf", "sexual", "cadavru", "tortura",
        // Spanish.
        "desnud", "erotic", "pornograf", "sexual", "cadaver", "tortura",
    ).distinct()

    private val BLOCKED = Regex(
        STEMS.joinToString("|", prefix = "\\b(?:", postfix = ")") { stem ->
            // A stem written with a trailing space is a whole word ("nu ", "sex "):
            // short words that are innocent as the start of longer ones.
            if (stem.endsWith(' ')) Regex.escape(stem.trim()) + "\\b" else Regex.escape(stem)
        },
    )
}
