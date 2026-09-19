package com.bibliarium.app.data.scan

import android.net.Uri
import com.bibliarium.app.data.importer.BookImporter
import com.bibliarium.app.domain.FoundBook
import com.bibliarium.app.domain.FoundBookState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

enum class ScanPhase {
    WALKING,
    MATCHING,
    READING_TITLES,
}

data class ScanProgress(
    val phase: ScanPhase,
    val scanned: Int = 0,
    val found: Int = 0,
    val processed: Int = 0,
    val total: Int = 0,
)

data class TitleUpdate(
    val uri: String,
    /** null, если название не прочиталось — в списке останется имя файла. */
    val title: String?,
    val processed: Int,
    val total: Int,
)

/**
 * Что происходит с найденными файлами после обхода: сверка с библиотекой
 * и чтение названий. Сам обход живёт за [com.bibliarium.app.data.access.FileAccess],
 * поэтому сканеру всё равно, откуда пришли файлы — из выбранных папок
 * или из полного доступа.
 */
class BookScanner(
    private val importer: BookImporter,
    private val metadataReader: ScanMetadataReader,
) {

    /**
     * Сравниваем с библиотекой по размеру и хэшу первых 64 КБ, а не по имени:
     * одна книга часто лежит в двух местах под разными именами. Хэш считаем
     * только для файлов, чей размер вообще встречается в библиотеке — для
     * остальных читать файл не нужно.
     */
    suspend fun markAlreadyAdded(
        books: List<FoundBook>,
        knownFingerprints: Map<Long, Set<String>>,
        onProgress: (checked: Int, total: Int) -> Unit,
    ): List<FoundBook> = withContext(Dispatchers.IO) {
        if (knownFingerprints.isEmpty()) return@withContext books

        books.mapIndexed { index, book ->
            currentCoroutineContext().ensureActive()

            onProgress(index + 1, books.size)

            val sizeMatches = knownFingerprints[book.sizeBytes]
            if (sizeMatches == null || book.state != FoundBookState.NEW) {
                return@mapIndexed book
            }

            val fingerprint = importer.sourceFingerprint(Uri.parse(book.uri), book.sizeBytes)
            if (fingerprint != null && fingerprint.headHash in sizeMatches) {
                book.copy(state = FoundBookState.ALREADY_ADDED)
            } else {
                book
            }
        }
    }

    /**
     * Названия читаются уже после того, как список показан: открывать каждую
     * книгу движком во время обхода — значит заставить пользователя ждать
     * вместо того, чтобы сразу показать найденное.
     */
    fun readTitles(books: List<FoundBook>): Flow<TitleUpdate> = flow {
        books.forEachIndexed { index, book ->
            currentCoroutineContext().ensureActive()
            val title = metadataReader.titleOf(Uri.parse(book.uri), book.fileName, book.format)
            emit(TitleUpdate(book.uri, title?.takeIf { it.isNotBlank() }, index + 1, books.size))
        }
    }.flowOn(Dispatchers.IO)
}
