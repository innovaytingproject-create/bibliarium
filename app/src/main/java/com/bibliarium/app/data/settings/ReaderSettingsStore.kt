package com.bibliarium.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.adapter.pdfium.navigator.PdfiumPreferencesSerializer
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.shared.ExperimentalReadiumApi

private val Context.readerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bibliarium_reader",
)

/**
 * Настройки чтения. Хранятся строкой в том виде, в каком их сериализует сам
 * Readium: свою схему писать незачем, а его сериализатор переживает добавление
 * новых полей в будущих версиях.
 *
 * Настройки общие для всех книг одного формата, а не отдельные на книгу:
 * человек подбирает удобный шрифт один раз.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderSettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private val epubKey = stringPreferencesKey("epub_preferences")
    private val pdfKey = stringPreferencesKey("pdf_preferences")

    /**
     * Ночной режим PDF хранится отдельно: в PdfiumPreferences такого поля нет,
     * инверсию делает сам PDFView.
     */
    private val pdfNightKey = booleanPreferencesKey("pdf_night_mode")

    private val epubSerializer = EpubPreferencesSerializer()
    private val pdfSerializer = PdfiumPreferencesSerializer()

    val epubPreferences: Flow<EpubPreferences> =
        appContext.readerDataStore.data.map { preferences ->
            preferences[epubKey]
                ?.let { runCatching { epubSerializer.deserialize(it) }.getOrNull() }
                ?: EpubPreferences()
        }

    val pdfPreferences: Flow<PdfiumPreferences> =
        appContext.readerDataStore.data.map { preferences ->
            preferences[pdfKey]
                ?.let { runCatching { pdfSerializer.deserialize(it) }.getOrNull() }
                ?: PdfiumPreferences()
        }

    val pdfNightMode: Flow<Boolean> =
        appContext.readerDataStore.data.map { it[pdfNightKey] ?: false }

    suspend fun currentPdfNightMode(): Boolean = pdfNightMode.first()

    suspend fun savePdfNightMode(enabled: Boolean) {
        appContext.readerDataStore.edit { it[pdfNightKey] = enabled }
    }

    suspend fun currentEpubPreferences(): EpubPreferences = epubPreferences.first()

    suspend fun currentPdfPreferences(): PdfiumPreferences = pdfPreferences.first()

    suspend fun saveEpubPreferences(preferences: EpubPreferences) {
        appContext.readerDataStore.edit { it[epubKey] = epubSerializer.serialize(preferences) }
    }

    suspend fun savePdfPreferences(preferences: PdfiumPreferences) {
        appContext.readerDataStore.edit { it[pdfKey] = pdfSerializer.serialize(preferences) }
    }
}
