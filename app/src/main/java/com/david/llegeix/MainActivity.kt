package com.david.llegeix

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.david.llegeix.data.settings.SettingsRepository
import com.david.llegeix.ui.navigation.AppNavigation
import com.david.llegeix.ui.theme.LlegeixTheme
import com.david.llegeix.util.withAppLocale

class MainActivity : ComponentActivity() {

    /**
     * The language is applied here rather than in the composition because
     * resources are resolved against the activity's base context: wrapping it
     * before anything is inflated means the very first frame is already in the
     * chosen language.
     */
    override fun attachBaseContext(newBase: Context) {
        val language = SettingsRepository.storedLanguage(newBase)
        super.attachBaseContext(newBase.withAppLocale(language.tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = (application as LlegeixApp).settingsRepository
        // What attachBaseContext actually applied. Anything else means the user
        // has just picked a different language and the activity has to be
        // rebuilt against it.
        val attachedLanguage = settingsRepository.current.language

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()

            LaunchedEffect(settings.language) {
                if (settings.language != attachedLanguage) recreate()
            }

            // Theme and accent need no restart: they are plain composition state.
            LlegeixTheme(
                themeMode = settings.themeMode,
                accent = settings.accent,
                customAccent = settings.customAccent,
            ) {
                AppNavigation()
            }
        }
    }
}
