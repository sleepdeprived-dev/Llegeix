package com.david.llegeix.data.settings

import com.david.llegeix.platform.desktopPrefsRoot
import java.util.prefs.Preferences

/**
 * The Mac's settings, in the user's own preferences: on macOS, a plist under
 * ~/Library/Preferences, which is where a Mac app's settings belong.
 */
fun desktopSettingsRepository(): SettingsRepository = SettingsRepository(
    javaPrefsStore(desktopPrefsRoot.node(SettingsRepository.PREFS_NAME)),
)

internal fun javaPrefsStore(node: Preferences): SettingsStore = PreferencesStore(node)

private class PreferencesStore(private val node: Preferences) : SettingsStore {
    override fun getString(key: String, default: String?): String? = node.get(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = node.getBoolean(key, default)
    override fun getInt(key: String, default: Int): Int = node.getInt(key, default)
    override fun putString(key: String, value: String) = node.put(key, value).also { node.flush() }
    override fun putBoolean(key: String, value: Boolean) = node.putBoolean(key, value).also { node.flush() }
    override fun putInt(key: String, value: Int) = node.putInt(key, value).also { node.flush() }
    override fun remove(key: String) = node.remove(key).also { node.flush() }
    override fun clear() = node.clear().also { node.flush() }
}
