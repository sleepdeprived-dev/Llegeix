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

/** The phone's recent searches, in the SharedPreferences file they have always been in. */
fun SearchHistoryRepository(context: Context): SearchHistoryRepository = SearchHistoryRepository(
    SharedPreferencesStore(
        context.applicationContext.getSharedPreferences(
            SearchHistoryRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        ),
    ),
)

/** SharedPreferences as a [SettingsStore], writing with apply() as the app always has. */
internal class SharedPreferencesStore(private val prefs: SharedPreferences) : SettingsStore {
    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    override fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun clear() = prefs.edit().clear().apply()
}
