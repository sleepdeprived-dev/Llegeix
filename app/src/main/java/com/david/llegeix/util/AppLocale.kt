package com.david.llegeix.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * A copy of this context whose resources resolve in [languageTag].
 *
 * The app carries its own language preference rather than following the device,
 * so every `Context` the UI reads strings from has to be wrapped — see
 * `MainActivity.attachBaseContext`.
 *
 * [Locale.setDefault] is set alongside the configuration because the platform
 * formatters the app uses — `DateUtils.getRelativeTimeSpanString` for "Yesterday",
 * `Formatter.formatShortFileSize` for "1.4 MB" — read the process default rather
 * than a context, and would otherwise stay in the device's language.
 */
fun Context.withAppLocale(languageTag: String): Context {
    val locale = Locale.forLanguageTag(languageTag)
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(configuration)
}
