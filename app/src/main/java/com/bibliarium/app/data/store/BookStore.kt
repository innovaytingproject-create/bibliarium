package com.bibliarium.app.data.store

import android.net.Uri
import com.bibliarium.app.domain.Book
import kotlinx.coroutines.flow.Flow

/**
 * Граница хранения. Сейчас за ней [LocalBookStore] — файлы в filesDir и Room.
 * Когда появится сервер, рядом встанет RemoteBookStore, и остальной код не меняется.
 */
interface BookStore {

    fun observeBooks(): Flow<List<Book>>

    suspend fun add(uri: Uri): Result<Book>

    suspend fun delete(id: String)

    suspend fun openContent(id: String): ByteArray

    suspend fun get(id: String): Book?

    /**
     * Отпечатки всех книг библиотеки: размер -> набор хэшей начала файла.
     * По ним поиск по телефону понимает, что книга уже добавлена. Заодно
     * дозаполняет отпечатки записей, созданных до их появления.
     */
    suspend fun fingerprints(): Map<Long, Set<String>>
}
