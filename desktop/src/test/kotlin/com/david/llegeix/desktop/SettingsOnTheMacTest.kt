package com.david.llegeix.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.david.llegeix.ui.settings.SettingsScreen
import com.david.llegeix.ui.theme.LlegeixTheme
import org.junit.Test

/**
 * Configuració on the Mac: the version it is, a real check against GitHub's
 * releases (this build is the newest, so the answer is that it is up to date,
 * unless the network says otherwise), and the privacy policy in the Mac's
 * words — Bergamot and Vision, not ML Kit and Android's installer.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsOnTheMacTest {

    @Test
    fun versionUpdatesAndPrivacy() = MacTestSession.run {
        runComposeUiTest {
            // A window on screen is resumed; the test's stand-in has to be told.
            val lifecycle = object : LifecycleOwner {
                val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
                override val lifecycle: Lifecycle get() = registry
            }
            setContent {
                CompositionLocalProvider(LocalLifecycleOwner provides lifecycle) {
                    LlegeixTheme { SettingsScreen(onBack = {}) }
                }
            }
            settleUntil { onAllNodesWithText("Configuració").fetchSemanticsNodes().isNotEmpty() }
            screenshot("settings-1")

            onAllNodesWithText("Comprova si hi ha actualitzacions")[0].performScrollTo().performClick()
            settleUntil {
                listOf("Tens la versió més nova.", "No s'ha pogut arribar", "massa comprovacions").any { answer ->
                    onAllNodes(hasText(answer, substring = true)).fetchSemanticsNodes().isNotEmpty()
                }
            }
            onAllNodes(hasText("Versió 4.4.6", substring = true))[0].performScrollTo()
            screenshot("settings-2-update")

            onAllNodesWithText("Política de privadesa")[0].performScrollTo().performClick()
            settleUntil { onAllNodes(hasText("Bergamot", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            check(onAllNodes(hasText("ML Kit", substring = true)).fetchSemanticsNodes().isEmpty())
            screenshot("settings-3-privacy")
        }
    }
}
