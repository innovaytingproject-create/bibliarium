package com.bibliarium.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ImportQueueEntity>)

    @Query("SELECT * FROM import_queue WHERE batchId = :batchId ORDER BY createdAt")
    fun observeBatch(batchId: String): Flow<List<ImportQueueEntity>>

    /**
     * Воркер берёт задания по одному: так пачка любого размера переживает
     * убийство процесса — после перезапуска продолжится с того же места.
     */
    @Query(
        "SELECT * FROM import_queue WHERE batchId = :batchId AND status = 'PENDING' " +
            "ORDER BY createdAt LIMIT 1",
    )
    suspend fun nextPending(batchId: String): ImportQueueEntity?

    @Query("UPDATE import_queue SET status = :status, failure = :failure WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, failure: String?)

    @Query("DELETE FROM import_queue WHERE batchId = :batchId")
    suspend fun clearBatch(batchId: String)

    @Query("DELETE FROM import_queue WHERE status <> 'PENDING' AND createdAt < :before")
    suspend fun pruneFinishedBefore(before: Long)
}
