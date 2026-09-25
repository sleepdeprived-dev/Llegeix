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
     * The app is in Catalan and only Catalan, whatever the phone's language.
     * The locale is still set here, not merely the strings: Catalan's plural
     * rules and its month names ("25 de setembre") come from the locale, and
     * an English phone would otherwise count and date in English.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale("ca"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settingsRepository = (application as LlegeixApp).settingsRepository

        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle()

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
