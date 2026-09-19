package com.bibliarium.app.data.access

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bibliarium.app.domain.BookFormat
import com.bibliarium.app.domain.FoundBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Поиск по папкам, выбранным через ACTION_OPEN_DOCUMENT_TREE. Папок может быть
 * несколько: с Android 11 система не даёт выбрать корень внутренней памяти
 * и папку Download, поэтому одной папкой обойтись обычно не выходит.
 *
 * Обход идёт запросами к DocumentsContract, а не через DocumentFile.listFiles():
 * DocumentFile на каждое имя, размер и тип делает отдельный запрос к провайдеру,
 * и на папке с тысячами файлов это занимает минуты. Здесь на каждую папку
 * приходится ровно один курсор со всеми нужными колонками.
 */
class TreeFileAccess(
    context: Context,
    private val roots: List<Uri>,
) : FileAccess {

    private val appContext = context.applicationContext

    override fun isAvailable(): Boolean = roots.isNotEmpty()

    private data class Candidate(
        val uri: Uri,
        val name: String,
        val folder: String,
        val size: Long,
    )

    override suspend fun findBooks(
        onProgress: (scanned: Int, found: Int) -> Unit,
    ): List<FoundBook> = withContext(Dispatchers.IO) {
        val found = mutableListOf<FoundBook>()
        val zipCandidates = mutableListOf<Candidate>()
        var scanned = 0

        for (root in roots) {
            scanned = walk(root, found, zipCandidates, scanned, onProgress)
        }

        for (candidate in zipCandidates) {
            currentCoroutineContext().ensureActive()
            if (candidate.size <= BookFiles.MAX_ZIP_BYTES &&
                BookFiles.containsFb2 { openStream(candidate.uri) }
            ) {
                found += BookFiles.toFoundBook(
                    uri = candidate.uri.toString(),
                    fileName = candidate.name,
                    folder = candidate.folder,
                    sizeBytes = candidate.size,
                    format = BookFormat.FB2,
                )
                onProgress(scanned, found.size)
            }
        }

        found
    }

    private suspend fun walk(
        treeUri: Uri,
        found: MutableList<FoundBook>,
        zipCandidates: MutableList<Candidate>,
        scannedSoFar: Int,
        onProgress: (Int, Int) -> Unit,
    ): Int {
        val resolver = appContext.contentResolver
        val rootId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return scannedSoFar

        // "primary:Download/Книги" -> "Download/Книги": показываем человеку путь,
        // а не идентификатор провайдера.
        val basePath = rootId.substringAfter(':').trim('/')

        val stack = ArrayDeque<Pair<String, String>>()
        stack.addLast(rootId to basePath)
        var scanned = scannedSoFar

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val (documentId, folder) = stack.removeLast()
            scanned++
            onProgress(scanned, found.size + zipCandidates.size)

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
                        if (!BookFiles.isSkippedDirectory(name, childFolder)) {
                            stack.addLast(childId to childFolder)
                        }
                        continue
                    }

                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    val format = BookFiles.formatOf(name)
                    if (format != null) {
                        found += BookFiles.toFoundBook(
                            uri = fileUri.toString(),
                            fileName = name,
                            folder = folder,
                            sizeBytes = size,
                            format = format,
                        )
                    } else if (BookFiles.isZipCandidate(name)) {
                        zipCandidates += Candidate(fileUri, name, folder, size)
                    }
                }
            }
        }

        return scanned
    }

    private fun openStream(uri: Uri) =
        appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Не удалось открыть $uri")

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }
}
