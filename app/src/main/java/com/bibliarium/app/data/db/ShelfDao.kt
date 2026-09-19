package com.bibliarium.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfDao {

    @Query("SELECT * FROM shelves ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<ShelfEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shelf: ShelfEntity)

    @Query("DELETE FROM shelves WHERE id = :id")
    suspend fun deleteById(id: String)
}
