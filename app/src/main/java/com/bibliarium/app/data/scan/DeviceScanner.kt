package com.bibliarium.app.data.scan

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bibliarium.app.data.importer.BookImporter
import com.bibliarium.app.domain.BookFormat
import com.bibliarium.app.domain.FoundBook
import com.bibliarium.app.domain.FoundBookState
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Что происходит с поиском прямо сейчас. */
sealed interface ScanUpdate {

    data class Progress(
        val phase: ScanPhase,
        val foldersScanned: Int,
        val booksFound: Int,
        val currentFolder: String?,
        val processed: Int = 0,
        val total: Int = 0,
    ) : ScanUpdate

    /** Список найденного готов; названия могут быть ещё не прочитаны. */
    data class Found(val books: List<FoundBook>) : ScanUpdate

    /** Название дочиталось для одного файла. */
    data class Title(val uri: String, val title: String) : ScanUpdate

    data object Finished : ScanUpdate
}

enum class ScanPhase {
    WALKING,
    INSPECTING_ARCHIVES,
    MATCHING,
    READING_TITLES,
}

/**
 * Обход дерева, выбранного через ACTION_OPEN_DOCUMENT_TREE.
 *
 * Обход идёт запросами к DocumentsContract, а не через DocumentFile.listFiles():
 * DocumentFile на каждое имя, размер и тип делает отдельный запрос к провайдеру,
 * и на папке с тысячами файлов это занимает минуты. Здесь на каждую папку
 * приходится ровно один курсор со всеми нужными колонками — тот же SAF, просто
 * запрошенный пачкой.
 *
 * Отменяется обычной отменой корутины: и обход, и чтение названий проверяют
 * активность на каждом шаге.
 */
class DeviceScanner(
    private val context: Context,
    private val importer: BookImporter,
    private val metadataReader: ScanMetadataReader,
) {

    private data class RawFile(
        val uri: Uri,
        val name: String,
        val folder: String,
        val size: Long,
        val format: BookFormat,
    )

    fun scan(treeUri: Uri, knownFingerprints: Map<Long, Set<String>>): Flow<ScanUpdate> = flow {
        val found = mutableListOf<RawFile>()
        val zipCandidates = mutableListOf<RawFile>()

        walk(treeUri, found, zipCandidates)
        inspectArchives(zipCandidates, found)

        val books = matchAgainstLibrary(found, knownFingerprints)
        emit(ScanUpdate.Found(books))

        readTitles(books)
        emit(ScanUpdate.Finished)
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<ScanUpdate>.walk(
        treeUri: Uri,
        found: MutableList<RawFile>,
        zipCandidates: MutableList<RawFile>,
    ) {
        val resolver = context.contentResolver
        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(DocumentsContract.getTreeDocumentId(treeUri) to "")
        var foldersScanned = 0

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val (documentId, folder) = stack.removeLast()
            foldersScanned++
            emit(
                ScanUpdate.Progress(
                    phase = ScanPhase.WALKING,
                    foldersScanned = foldersScanned,
                    booksFound = found.size + zipCandidates.size,
                    currentFolder = folder.ifEmpty { "/" },
                ),
            )

            val childrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            val cursor = runCatching {
                resolver.query(childrenUri, PROJECTION, null, null, null)
            }.getOrNull() ?: continue

            cursor.use { rows ->
                while (rows.moveToNext()) {
                    currentCoroutineContext().ensureActive()

                    val childId = rows.getString(0) ?: continue
                    val name = rows.getString(1) ?: continue
                    val mimeType = rows.getString(2)
                    val size = if (rows.isNull(3)) 0L else rows.getLong(3)

                    if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val childFolder = if (folder.isEmpty()) name else "$folder/$name"
                        if (!isSkipped(name, childFolder)) {
                            stack.addLast(childId to childFolder)
                        }
                        continue
                    }

                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val format = formatOf(name)
                    if (format != null) {
                        found += RawFile(fileUri, name, folder, size, format)
                        // Промежуточный снимок: если обход прервут кнопкой «Отмена»,
                        // найденное до этого момента останется на экране.
                        if (found.size % SNAPSHOT_EVERY == 0) {
                            emit(ScanUpdate.Found(preliminary(found)))
                        }
                    } else if (name.endsWith(".zip", ignoreCase = true)) {
                        zipCandidates += RawFile(fileUri, name, folder, size, BookFormat.FB2)
                    }
                }
            }
        }
    }

    /**
     * Голый .zip может оказаться архивом с fb2 внутри. Проверка ограничена
     * размером архива и числом записей: иначе один многогигабайтный zip
     * съест весь поиск.
     */
    private suspend fun FlowCollector<ScanUpdate>.inspectArchives(
        candidates: List<RawFile>,
        found: MutableList<RawFile>,
    ) {
        if (candidates.isEmpty()) return

        candidates.forEachIndexed { index, candidate ->
            currentCoroutineContext().ensureActive()
            emit(
                ScanUpdate.Progress(
                    phase = ScanPhase.INSPECTING_ARCHIVES,
                    foldersScanned = 0,
                    booksFound = found.size,
                    currentFolder = candidate.name,
                    processed = index + 1,
                    total = candidates.size,
                ),
            )
            if (candidate.size <= MAX_ZIP_BYTES && containsFb2(candidate.uri)) {
                found += candidate
            }
        }
    }

    private fun containsFb2(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw.buffered(), Charsets.ISO_8859_1).use { zip ->
                var checked = 0
                var entry = zip.nextEntry
                while (entry != null && checked < MAX_ZIP_ENTRIES) {
                    if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) {
                        return@runCatching true
                    }
                    checked++
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        false
    }.getOrDefault(false)

    /**
     * Сравниваем с библиотекой по размеру и хэшу первых 64 КБ, а не по имени.
     * Хэш считаем только для файлов, чей размер вообще встречается в библиотеке —
     * для остальных чтение не нужно.
     */
    private suspend fun FlowCollector<ScanUpdate>.matchAgainstLibrary(
        found: List<RawFile>,
        knownFingerprints: Map<Long, Set<String>>,
    ): List<FoundBook> {
        val result = ArrayList<FoundBook>(found.size)

        found.forEachIndexed { index, file ->
            currentCoroutineContext().ensureActive()

            val sizeMatches = knownFingerprints[file.size]
            val alreadyAdded = if (sizeMatches != null) {
                emit(
                    ScanUpdate.Progress(
                        phase = ScanPhase.MATCHING,
                        foldersScanned = 0,
                        booksFound = found.size,
                        currentFolder = file.name,
                        processed = index + 1,
                        total = found.size,
                    ),
                )
                val fingerprint = importer.sourceFingerprint(file.uri, file.size)
                fingerprint != null && fingerprint.headHash in sizeMatches
            } else {
                false
            }

            val state = when {
                alreadyAdded -> FoundBookState.ALREADY_ADDED
                !file.format.isSupported -> FoundBookState.NOT_SUPPORTED_YET
                else -> FoundBookState.NEW
            }

            result += FoundBook(
                uri = file.uri.toString(),
                fileName = file.name,
                folder = file.folder,
                sizeBytes = file.size,
                format = file.format,
                title = null,
                state = state,
            )
        }

        return result
    }

    /**
     * Названия читаются отдельным проходом уже после того, как список показан:
     * открывать каждую книгу движком во время обхода — значит заставить
     * пользователя ждать вместо того, чтобы сразу показать найденное.
     */
    private suspend fun FlowCollector<ScanUpdate>.readTitles(
        books: List<FoundBook>,
    ) {
        books.forEachIndexed { index, book ->
            currentCoroutineContext().ensureActive()
            emit(
                ScanUpdate.Progress(
                    phase = ScanPhase.READING_TITLES,
                    foldersScanned = 0,
                    booksFound = books.size,
                    currentFolder = book.fileName,
                    processed = index + 1,
                    total = books.size,
                ),
            )
            val title = metadataReader.titleOf(Uri.parse(book.uri), book.fileName, book.format)
            if (!title.isNullOrBlank()) {
                emit(ScanUpdate.Title(book.uri, title))
            }
        }
    }

    /** Список без сверки с библиотекой и без названий — только чтобы было что показать. */
    private fun preliminary(found: List<RawFile>): List<FoundBook> = found.map { file ->
        FoundBook(
            uri = file.uri.toString(),
            fileName = file.name,
            folder = file.folder,
            sizeBytes = file.size,
            format = file.format,
            title = null,
            state = if (file.format.isSupported) {
                FoundBookState.NEW
            } else {
                FoundBookState.NOT_SUPPORTED_YET
            },
        )
    }

    private fun formatOf(name: String): BookFormat? {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".epub") -> BookFormat.EPUB
            // Проверяется раньше .zip, иначе архив уедет в кандидаты на разбор.
            lower.endsWith(".fb2.zip") -> BookFormat.FB2
            lower.endsWith(".fb2") -> BookFormat.FB2
            lower.endsWith(".pdf") -> BookFormat.PDF
            else -> null
        }
    }

    /**
     * Скрытые папки пропускаем, Android/data и Android/obb всё равно закрыты
     * для SAF. Android/media не трогаем: именно там лежат файлы из Telegram.
     */
    private fun isSkipped(name: String, path: String): Boolean =
        name.startsWith(".") ||
            path.equals("Android/data", ignoreCase = true) ||
            path.equals("Android/obb", ignoreCase = true)

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        const val MAX_ZIP_BYTES = 64L * 1024 * 1024
        const val MAX_ZIP_ENTRIES = 16
        const val SNAPSHOT_EVERY = 25
    }
}
