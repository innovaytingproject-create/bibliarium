package com.bibliarium.app.data.store

import com.bibliarium.app.domain.Highlight
import kotlinx.coroutines.flow.Flow

/** Та же граница, что и у [BookStore], но для выделений. */
interface HighlightStore {

    fun observeForBook(bookId: String): Flow<List<Highlight>>

    fun observeAll(): Flow<List<Highlight>>

    fun observeCountForBook(bookId: String): Flow<Int>

    suspend fun add(highlight: Highlight)

    suspend fun delete(id: String)
}
