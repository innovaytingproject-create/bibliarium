package com.bibliarium.app.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Размер и хэш начала файла — этого хватает, чтобы узнать уже добавленную книгу. */
data class FingerprintRow(
    val fileSize: Long,
    val headHash: String,
)

/** Книга, добавленная до появления отпечатков: хэш нужно досчитать. */
data class MissingFingerprintRow(
    val id: String,
    val filePath: String,
)

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    /**
     * Постраничный источник для полки: список из 300+ книг не должен
     * целиком оказываться в памяти (раздел 7 ТЗ).
     */
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun pagingSource(): PagingSource<Int, BookEntity>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findById(id: String): BookEntity?

    @Query("SELECT COUNT(*) FROM books")
    fun observeCount(): Flow<Int>

    @Query("SELECT fileSize, headHash FROM books WHERE headHash IS NOT NULL")
    suspend fun fingerprints(): List<FingerprintRow>

    @Query("SELECT id, filePath FROM books WHERE headHash IS NULL")
    suspend fun missingFingerprints(): List<MissingFingerprintRow>

    @Query("UPDATE books SET fileSize = :size, headHash = :hash WHERE id = :id")
    suspend fun setFingerprint(id: String, size: Long, hash: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        UPDATE books
        SET progress = :progress, locator = :locator, status = :status, lastOpenedAt = :lastOpenedAt
        WHERE id = :id
        """,
    )
    suspend fun updateProgress(
        id: String,
        progress: Float,
        locator: String?,
        status: String,
        lastOpenedAt: Long,
    )

    @Query(
        """
        UPDATE books
        SET readerPath = :readerPath, openFailure = :failure, openFailureDetail = :detail
        WHERE id = :id
        """,
    )
    suspend fun updateOpenState(
        id: String,
        readerPath: String?,
        failure: String?,
        detail: String?,
    )

    @Query("UPDATE books SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: String, favorite: Boolean)

    /** Ручная правка карточки: после неё пересканирование запись не трогает. */
    @Query(
        """
        UPDATE books
        SET title = :title, author = :author, editedByUser = 1
        WHERE id = :id
        """,
    )
    suspend fun updateTitleAndAuthor(id: String, title: String, author: String?)

    @Query("UPDATE books SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: String, description: String?)

    @Query("UPDATE books SET customCoverPath = :path WHERE id = :id")
    suspend fun updateCustomCover(id: String, path: String?)

    /** Что перечитывать при обновлении метаданных: всё, кроме правленного руками. */
    @Query("SELECT * FROM books WHERE editedByUser = 0")
    suspend fun notEditedByUser(): List<BookEntity>

    @Query(
        """
        UPDATE books
        SET title = :title, author = :author, description = :description, genre = :genre
        WHERE id = :id AND editedByUser = 0
        """,
    )
    suspend fun refreshMetadata(
        id: String,
        title: String,
        author: String?,
        description: String?,
        genre: String?,
    )
}
