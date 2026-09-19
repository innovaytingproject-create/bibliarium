package com.bibliarium.app.data.importer

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.bibliarium.app.data.db.ImportQueueDao
import com.bibliarium.app.data.db.ImportQueueEntity
import com.bibliarium.app.domain.FoundBook
import com.bibliarium.app.work.ImportWorker
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ImportStatus {
    PENDING,
    DONE,
    FAILED,
}

/** Файл, который не удалось добавить, и почему. */
data class FailedImport(
    val uri: String,
    val name: String,
    val failure: ImportFailure?,
)

data class BatchProgress(
    val total: Int,
    val done: Int,
    val failed: Int,
    val pending: Int,
    val failures: List<FailedImport>,
) {
    val processed: Int get() = done + failed
    val finished: Boolean get() = total > 0 && pending == 0
}

/**
 * Пакетный импорт. Очередь лежит в базе, а сам импорт делает WorkManager,
 * поэтому он не прекращается, когда приложение свернули, и переживает
 * убийство процесса: воркер просто продолжит с первой невыполненной записи.
 */
class BatchImporter(
    context: Context,
    private val queueDao: ImportQueueDao,
) {

    private val appContext = context.applicationContext

    suspend fun enqueue(books: List<FoundBook>): String {
        val batchId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        queueDao.pruneFinishedBefore(now - RETENTION_MILLIS)
        queueDao.insertAll(
            books.mapIndexed { index, book ->
                ImportQueueEntity(
                    id = UUID.randomUUID().toString(),
                    batchId = batchId,
                    uri = book.uri,
                    displayName = book.title ?: book.fileName,
                    sizeBytes = book.sizeBytes,
                    status = ImportStatus.PENDING.name,
                    failure = null,
                    // Сдвиг на индекс задаёт порядок внутри пачки.
                    createdAt = now + index,
                )
            },
        )

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(workDataOf(ImportWorker.KEY_BATCH_ID to batchId))
                .build(),
        )

        return batchId
    }

    fun observe(batchId: String): Flow<BatchProgress> =
        queueDao.observeBatch(batchId).map { rows ->
            BatchProgress(
                total = rows.size,
                done = rows.count { it.status == ImportStatus.DONE.name },
                failed = rows.count { it.status == ImportStatus.FAILED.name },
                pending = rows.count { it.status == ImportStatus.PENDING.name },
                failures = rows
                    .filter { it.status == ImportStatus.FAILED.name }
                    .map { row ->
                        FailedImport(
                            uri = row.uri,
                            name = row.displayName,
                            failure = row.failure?.let { value ->
                                runCatching { ImportFailure.valueOf(value) }.getOrNull()
                            },
                        )
                    },
            )
        }

    private companion object {
        const val WORK_NAME = "bibliarium-book-import"
        const val RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
