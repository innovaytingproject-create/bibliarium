package com.bibliarium.app.data.store

import android.net.Uri
import com.bibliarium.app.data.db.BookDao
import com.bibliarium.app.data.db.toDomain
import com.bibliarium.app.data.db.toEntity
import com.bibliarium.app.data.importer.BookImporter
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.ReadingStatus
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
            entity.readerPath?.let { path -> runCatching { File(path).delete() } }
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

    override suspend fun saveProgress(id: String, progress: Float, locator: String?) {
        withContext(io) {
            val status = when {
                progress >= FINISHED_THRESHOLD -> ReadingStatus.FINISHED
                else -> ReadingStatus.READING
            }
            bookDao.updateProgress(
                id = id,
                progress = progress.coerceIn(0f, 1f),
                locator = locator,
                status = status.name,
                lastOpenedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun markOpened(id: String) {
        withContext(io) {
            val entity = bookDao.findById(id) ?: return@withContext
            bookDao.updateProgress(
                id = id,
                progress = entity.progress,
                locator = entity.locator,
                status = if (entity.status == ReadingStatus.NOT_STARTED.name) {
                    ReadingStatus.READING.name
                } else {
                    entity.status
                },
                lastOpenedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun retryPreparation(id: String): Result<Unit> = withContext(io) {
        val entity = bookDao.findById(id)
            ?: return@withContext Result.failure(IllegalArgumentException("Книга $id не найдена"))

        val prepared = importer.prepareExisting(entity.id, entity.filePath, entity.format)
        bookDao.updateOpenState(
            id = entity.id,
            readerPath = prepared.readerPath,
            failure = prepared.failure?.name,
            detail = prepared.failureDetail,
        )
        if (prepared.failure == null) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(prepared.failureDetail ?: "Не получилось"))
        }
    }

    override suspend fun fingerprints(): Map<Long, Set<String>> = withContext(io) {
        // Книги, добавленные до появления отпечатков, досчитываем по сохранённому файлу.
        // Для обычных EPUB и FB2 он побайтово равен исходному, так что отпечаток совпадёт.
        // Исключение — книги, добавленные из .fb2.zip до этого этапа: в filesDir лежит
        // распакованный fb2, и повторный поиск предложит такой архив ещё раз.
        bookDao.missingFingerprints().forEach { row ->
            val fingerprint = importer.fileFingerprint(File(row.filePath)) ?: return@forEach
            bookDao.setFingerprint(row.id, fingerprint.sizeBytes, fingerprint.headHash)
        }

        bookDao.fingerprints()
            .groupBy({ it.fileSize }, { it.headHash })
            .mapValues { (_, hashes) -> hashes.toSet() }
    }

    private companion object {
        /** Ближе к концу докручивать нечего — считаем книгу прочитанной. */
        const val FINISHED_THRESHOLD = 0.99f
    }
}
