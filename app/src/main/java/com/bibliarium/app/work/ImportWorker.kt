package com.bibliarium.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bibliarium.app.appContainer
import com.bibliarium.app.data.importer.ImportException
import com.bibliarium.app.data.importer.ImportFailure
import com.bibliarium.app.data.importer.ImportStatus

/**
 * Импортирует пачку книг по одной. Битый файл не роняет весь импорт:
 * причина отказа записывается в очередь, и работа идёт дальше.
 */
class ImportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return Result.failure()

        val container = applicationContext.appContainer
        val queue = container.importQueueDao
        val store = container.bookStore

        while (!isStopped) {
            val next = queue.nextPending(batchId) ?: break

            val failure: ImportFailure? = try {
                store.add(Uri.parse(next.uri))
                    .exceptionOrNull()
                    ?.let { error ->
                        (error as? ImportException)?.failure ?: ImportFailure.STORAGE_FAILED
                    }
            } catch (e: Exception) {
                // Сюда попадает всё, что импортёр не успел завернуть сам:
                // отозванное разрешение на папку, исчезнувший файл, нехватка места.
                ImportFailure.STORAGE_FAILED
            }

            if (failure == null) {
                queue.updateStatus(next.id, ImportStatus.DONE.name, null)
            } else {
                queue.updateStatus(next.id, ImportStatus.FAILED.name, failure.name)
            }
        }

        return Result.success()
    }

    companion object {
        const val KEY_BATCH_ID = "batchId"
    }
}
