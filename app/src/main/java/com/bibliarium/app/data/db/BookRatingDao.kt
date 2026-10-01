package com.bibliarium.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookRatingDao {

    @Query("SELECT * FROM book_ratings WHERE bookId = :bookId")
    fun observe(bookId: String): Flow<BookRatingEntity?>

    @Query("SELECT * FROM book_ratings WHERE bookId = :bookId")
    suspend fun find(bookId: String): BookRatingEntity?

    @Query("SELECT * FROM book_ratings")
    fun observeAll(): Flow<List<BookRatingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(rating: BookRatingEntity)

    @Query("UPDATE book_ratings SET surveyShown = 1 WHERE bookId = :bookId")
    suspend fun markSurveyShown(bookId: String)
}
