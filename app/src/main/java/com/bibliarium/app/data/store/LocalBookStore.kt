package com.bibliarium.app.data.store

import android.net.Uri
import com.bibliarium.app.data.db.BookDao
import com.bibliarium.app.data.db.toDomain
import com.bibliarium.app.data.db.toEntity
import com.bibliarium.app.data.importer.BookImporter
import com.bibliarium.app.domain.Book
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Файлы книг — в filesDir/books, миниатюры — в filesDir/covers, метаданные — в Room.
 * Единственная реализация [BookStore] в первой версии.
 */
class LocalBookStore(
    private val bookDao: BookDao,
    private val importer: BookImporter,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BookStore {

    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun add(uri: Uri): Result<Book> = withContext(io) {
        importer.import(uri).onSuccess { book ->
            bookDao.insert(book.toEntity())
        }
    }

    override suspend fun delete(id: String) {
        withContext(io) {
            val entity = bookDao.findById(id) ?: return@withContext
            bookDao.deleteById(id)
            runCatching { File(entity.filePath).delete() }
            entity.coverPath?.let { path -> runCatching { File(path).delete() } }
        }
    }

    override suspend fun openContent(id: String): ByteArray = withContext(io) {
        val entity = bookDao.findById(id)
            ?: throw IllegalArgumentException("Книга $id не найдена")
        File(entity.filePath).readBytes()
    }

    override suspend fun get(id: String): Book? = withContext(io) {
        bookDao.findById(id)?.toDomain()
    }
}
