package com.bibliarium.app.data.store

import com.bibliarium.app.data.db.BookRatingDao
import com.bibliarium.app.data.db.toDomain
import com.bibliarium.app.data.db.toEntity
import com.bibliarium.app.domain.BookRating
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Та же граница, что у [BookStore], но для оценок. Отдельное хранилище,
 * а не поле книги: у оценки своя жизнь — опрос, пересчёт среднего, заметка.
 */
interface RatingStore {

    fun observe(bookId: String): Flow<BookRating?>

    fun observeAll(): Flow<Map<String, BookRating>>

    suspend fun get(bookId: String): BookRating?

    /** Ответы опроса: среднее пересчитывается по заполненным. */
    suspend fun save(rating: BookRating)

    /** Оценка, поставленная руками в карточке: пишется прямо в среднее. */
    suspend fun setOverall(bookId: String, overall: Float)

    /** Опрос показан — больше сам не всплывает. */
    suspend fun markSurveyShown(bookId: String)
}

class LocalRatingStore(
    private val dao: BookRatingDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : RatingStore {

    override fun observe(bookId: String): Flow<BookRating?> =
        dao.observe(bookId).map { it?.toDomain() }

    override fun observeAll(): Flow<Map<String, BookRating>> =
        dao.observeAll().map { list -> list.associate { it.bookId to it.toDomain() } }

    override suspend fun get(bookId: String): BookRating? = withContext(io) {
        dao.find(bookId)?.toDomain()
    }

    override suspend fun save(rating: BookRating) = withContext(io) {
        val previous = dao.find(rating.bookId)?.toDomain()
        dao.save(
            rating.copy(
                overall = BookRating.averageOf(rating),
                ratedAt = rating.ratedAt ?: System.currentTimeMillis(),
                // Отметку о показанном опросе сохраняем: она про показ,
                // а не про ответы.
                surveyShown = rating.surveyShown || previous?.surveyShown == true,
            ).toEntity(),
        )
    }

    override suspend fun setOverall(bookId: String, overall: Float) = withContext(io) {
        val previous = dao.find(bookId)?.toDomain() ?: BookRating(bookId = bookId)
        dao.save(
            previous.copy(
                overall = overall,
                ratedAt = System.currentTimeMillis(),
            ).toEntity(),
        )
    }

    override suspend fun markSurveyShown(bookId: String) = withContext(io) {
        val previous = dao.find(bookId)?.toDomain()
        if (previous == null) {
            dao.save(BookRating(bookId = bookId, surveyShown = true).toEntity())
        } else {
            dao.markSurveyShown(bookId)
        }
    }
}
