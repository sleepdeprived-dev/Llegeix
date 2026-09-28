package com.david.llegeix.data.settings

/**
 * Where [SettingsRepository] keeps its values: the handful of calls it makes,
 * so each platform can put them in its own native store. Reads are synchronous
 * and writes are fire-and-forget, as SharedPreferences' apply() always was.
 */
interface SettingsStore {
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getInt(key: String, default: Int): Int
    fun putString(key: String, value: String)
    fun putBoolean(key: String, value: Boolean)
    fun putInt(key: String, value: Int)
    fun remove(key: String)
    fun clear()
}
