package com.bibliarium.app.data.access

import com.bibliarium.app.domain.BookFormat
import com.bibliarium.app.domain.FoundBook
import com.bibliarium.app.domain.FoundBookState
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Общие правила отбора файлов — одинаковые для обоих режимов доступа. */
internal object BookFiles {

    const val MAX_ZIP_BYTES = 64L * 1024 * 1024
    private const val MAX_ZIP_ENTRIES = 16

    fun formatOf(name: String): BookFormat? {
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

    fun isZipCandidate(name: String): Boolean = name.endsWith(".zip", ignoreCase = true)

    /**
     * Папки, в которые лезть незачем.
     *
     * Android/data и Android/obb закрыты и для SAF, и для полного доступа —
     * это ограничение системы, а не наше решение. Android/media не пропускаем:
     * именно там лежат файлы из Telegram.
     */
    fun isSkippedDirectory(name: String, path: String): Boolean =
        name.startsWith(".") ||
            name.equals("LOST.DIR", ignoreCase = true) ||
            path.equals("Android/data", ignoreCase = true) ||
            path.equals("Android/obb", ignoreCase = true)

    /**
     * Голый .zip может оказаться архивом с fb2 внутри. Проверка ограничена
     * числом записей: иначе один многогигабайтный архив съест весь поиск.
     */
    fun containsFb2(openStream: () -> InputStream): Boolean = runCatching {
        openStream().use { raw ->
            // Имена записей читаем как ISO-8859-1: в русских архивах они бывают
            // в CP866, и разбор UTF-8 на таких именах падает. Суффикс .fb2 — ASCII.
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

    fun toFoundBook(
        uri: String,
        fileName: String,
        folder: String,
        sizeBytes: Long,
        format: BookFormat,
    ): FoundBook = FoundBook(
        uri = uri,
        fileName = fileName,
        folder = folder,
        sizeBytes = sizeBytes,
        format = format,
        title = null,
        state = if (format.isSupported) {
            FoundBookState.NEW
        } else {
            FoundBookState.NOT_SUPPORTED_YET
        },
    )
}
