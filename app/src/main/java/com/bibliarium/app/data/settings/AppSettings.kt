package com.bibliarium.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bibliarium_settings",
)

/**
 * Настройки приложения. Пока здесь только папки для поиска книг.
 *
 * Папок несколько, а не одна: с Android 11 система не даёт выбрать через SAF
 * корень внутренней памяти и папку Download, поэтому одной папкой обойтись
 * обычно не выходит.
 */
class AppSettings(context: Context) {

    private val appContext = context.applicationContext

    private val scanRootsKey = stringSetPreferencesKey("scan_root_uris")

    /** Одна папка из прошлой версии — подхватывается, чтобы не спрашивать заново. */
    private val legacyScanRootKey = stringPreferencesKey("scan_root_uri")

    val scanRoots: Flow<List<String>> =
        appContext.settingsDataStore.data.map { preferences ->
            val roots = preferences[scanRootsKey]
            if (!roots.isNullOrEmpty()) {
                roots.sorted()
            } else {
                listOfNotNull(preferences[legacyScanRootKey])
            }
        }

    suspend fun currentScanRoots(): List<String> = scanRoots.first()

    suspend fun addScanRoot(uri: String) {
        appContext.settingsDataStore.edit { preferences ->
            val current = preferences[scanRootsKey]
                ?: setOfNotNull(preferences[legacyScanRootKey])
            preferences[scanRootsKey] = current + uri
            preferences.remove(legacyScanRootKey)
        }
    }

    suspend fun removeScanRoot(uri: String) {
        appContext.settingsDataStore.edit { preferences ->
            val current = preferences[scanRootsKey]
                ?: setOfNotNull(preferences[legacyScanRootKey])
            preferences[scanRootsKey] = current - uri
            preferences.remove(legacyScanRootKey)
        }
    }
}
