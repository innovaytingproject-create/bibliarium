package com.bibliarium.app.data.store

import com.bibliarium.app.data.db.HighlightDao
import com.bibliarium.app.data.db.toDomain
import com.bibliarium.app.data.db.toEntity
import com.bibliarium.app.domain.Highlight
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalHighlightStore(
    private val highlightDao: HighlightDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : HighlightStore {

    override fun observeForBook(bookId: String): Flow<List<Highlight>> =
        highlightDao.observeForBook(bookId).map { list -> list.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Highlight>> =
        highlightDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeCountForBook(bookId: String): Flow<Int> =
        highlightDao.observeCountForBook(bookId)

    override suspend fun add(highlight: Highlight) {
        withContext(io) { highlightDao.insert(highlight.toEntity()) }
    }

    override suspend fun delete(id: String) {
        withContext(io) { highlightDao.deleteById(id) }
    }
}
