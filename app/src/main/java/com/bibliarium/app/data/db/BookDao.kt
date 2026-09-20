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
}
