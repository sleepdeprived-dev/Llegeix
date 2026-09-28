package com.david.llegeix.data.settings

import android.content.Context
import android.content.SharedPreferences

/** The phone's settings, in the SharedPreferences file they have always been in. */
fun SettingsRepository(context: Context): SettingsRepository = SettingsRepository(
    SharedPreferencesStore(
        context.applicationContext.getSharedPreferences(
            SettingsRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        ),
    ),
)

private class SharedPreferencesStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun clear() = prefs.edit().clear().apply()
}
