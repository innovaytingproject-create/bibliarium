package com.bibliarium.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bibliarium_settings",
)

/**
 * Настройки приложения. Пока здесь только корневая папка для поиска книг:
 * её выбирают один раз и больше не переспрашивают.
 */
class AppSettings(context: Context) {

    private val appContext = context.applicationContext

    private val scanRootKey = stringPreferencesKey("scan_root_uri")

    val scanRootUri: Flow<String?> =
        appContext.settingsDataStore.data.map { preferences -> preferences[scanRootKey] }

    suspend fun currentScanRootUri(): String? = scanRootUri.first()

    suspend fun setScanRootUri(uri: String) {
        appContext.settingsDataStore.edit { preferences -> preferences[scanRootKey] = uri }
    }
}
