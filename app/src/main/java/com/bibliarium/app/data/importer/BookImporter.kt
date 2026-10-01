package com.bibliarium.app.data.importer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.bibliarium.app.data.importer.fb2.Fb2ConversionException
import com.bibliarium.app.data.importer.fb2.Fb2ConversionFailure
import com.bibliarium.app.data.importer.fb2.Fb2ToEpubConverter
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookFailure
import com.bibliarium.app.domain.BookFormat
import com.bibliarium.app.domain.ReadingStatus
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.use
import org.readium.r2.streamer.PublicationOpener

/**
 * Кладёт файл книги в [booksDir], вытаскивает метаданные и сразу делает
 * миниатюру обложки — оригинал в списки никогда не попадает (раздел 7 ТЗ).
 */
class BookImporter(
    private val context: Context,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
    private val booksDir: File,
    private val coversDir: File,
    private val fb2Converter: Fb2ToEpubConverter = Fb2ToEpubConverter(),
    /**
     * Нужна только ради метаданных PDF: Readium отдаёт из PDF название
     * и автора, а аннотацию (поле Subject) — нет.
     */
    private val pdfDocumentFactory: PdfDocumentFactory<*>? = null,
) {

    /** Итог подготовки файла к чтению вместе с метаданными. */
    private data class Prepared(
        val metadata: SourceMetadata,
        val readerPath: String?,
        val failure: BookFailure?,
        val failureDetail: String?,
    )

    private data class SourceMetadata(
        val title: String?,
        val author: String?,
        val genre: String?,
        val cover: Bitmap?,
        val description: String? = null,
    )

    /** Что именно выбрал пользователь: формат книги и лежит ли она в zip. */
    private data class SourceKind(
        val format: BookFormat,
        val zipped: Boolean,
    )

    /** Имя и размер документа за content://. */
    data class DocumentInfo(
        val displayName: String?,
        val sizeBytes: Long,
    )

    suspend fun import(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        val document = queryDocument(uri)
        val displayName = document.displayName
        val kind = detectKind(displayName)
            ?: return@withContext Result.failure(ImportException(ImportFailure.UNKNOWN_FORMAT))

        if (!kind.format.isSupported) {
            return@withContext Result.failure(ImportException(ImportFailure.UNSUPPORTED_FORMAT))
        }

        val id = UUID.randomUUID().toString()
        val target = File(booksDir, "$id.${kind.format.extension}")

        try {
            // Отпечаток снимаем с исходного файла до распаковки: повторный поиск
            // сравнивает именно то, что лежит на телефоне.
            val fingerprint = sourceFingerprint(uri, document.sizeBytes)

            if (kind.zipped) {
                extractFb2(uri, target)
            } else {
                copyToStorage(uri, target)
            }

            val prepared = when (kind.format) {
                // И EPUB, и PDF открывает Readium — своих парсеров нет.
                BookFormat.EPUB, BookFormat.PDF -> Prepared(
                    metadata = readWithReadium(target, pdf = kind.format == BookFormat.PDF)
                        ?: throw ImportException(ImportFailure.PARSE_FAILED),
                    readerPath = null,
                    failure = null,
                    failureDetail = null,
                )

                BookFormat.FB2 -> prepareFb2(id, target)
                else -> throw ImportException(ImportFailure.UNSUPPORTED_FORMAT)
            }

            val metadata = prepared.metadata
            val coverPath = metadata.cover?.let { saveThumbnail(id, it) }

            val named = nameOf(metadata, displayName ?: target.name)

            val book = Book(
                id = id,
                title = named.title,
                author = named.author,
                format = kind.format,
                filePath = target.absolutePath,
                coverPath = coverPath,
                addedAt = System.currentTimeMillis(),
                lastOpenedAt = null,
                progress = 0f,
                locator = null,
                status = ReadingStatus.NOT_STARTED,
                genre = metadata.genre?.takeIf { it.isNotBlank() },
                shelfId = null,
                isFavorite = false,
                description = metadata.description?.takeIf { it.isNotBlank() },
                customCoverPath = null,
                editedByUser = false,
                fileSize = fingerprint?.sizeBytes ?: document.sizeBytes,
                headHash = fingerprint?.headHash,
                readerPath = prepared.readerPath,
                openFailure = prepared.failure,
                openFailureDetail = prepared.failureDetail,
            )
            Result.success(book)
        } catch (e: ImportException) {
            target.delete()
            Result.failure(e)
        } catch (e: Exception) {
            target.delete()
            Result.failure(ImportException(ImportFailure.STORAGE_FAILED, e))
        }
    }

    /** Что удалось вычитать из уже лежащего в библиотеке файла. */
    data class FreshMetadata(
        val title: String,
        val author: String?,
        val description: String?,
        val genre: String?,
    )

    /**
     * Перечитывает метаданные книги, которая уже в библиотеке.
     *
     * Нужно для книг, добавленных до того, как мы научились доставать
     * настоящее название: тогда в карточке оставалось имя файла.
     */
    suspend fun readMetadata(filePath: String, format: String): FreshMetadata? =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext null

            val metadata = when (format) {
                BookFormat.FB2.name -> readFb2(file)
                BookFormat.EPUB.name -> readWithReadium(file, pdf = false)
                BookFormat.PDF.name -> readWithReadium(file, pdf = true)
                else -> null
            } ?: return@withContext null

            val named = nameOf(metadata, file.name)
            FreshMetadata(
                title = named.title,
                author = named.author,
                description = metadata.description?.takeIf { it.isNotBlank() },
                genre = metadata.genre?.takeIf { it.isNotBlank() },
            )
        }

    /**
     * Своя обложка: копируется в хранилище обложек, обрезается по центру
     * под пропорции книжной обложки и ужимается. Оригинал не храним — он
     * лежит у человека в галерее, и держать вторую копию незачем.
     */
    suspend fun saveCustomCover(id: String, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = context.contentResolver.openInputStream(source).use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@runCatching null

            val cropped = cropToCover(bitmap)
            val scaled = scaleToFit(cropped, COVER_MAX_PX)
            val file = File(coversDir, "$id-custom.jpg")
            file.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, out)
            }
            if (scaled !== cropped) scaled.recycle()
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()
            file.absolutePath
        }.getOrNull()
    }

    /** Обрезка по центру под пропорции 2:3 — так обложка не искажается. */
    private fun cropToCover(bitmap: Bitmap): Bitmap {
        val targetRatio = COVER_WIDTH.toFloat() / COVER_HEIGHT
        val ratio = bitmap.width.toFloat() / bitmap.height
        return when {
            ratio > targetRatio -> {
                val width = (bitmap.height * targetRatio).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(bitmap, (bitmap.width - width) / 2, 0, width, bitmap.height)
            }
            ratio < targetRatio -> {
                val height = (bitmap.width / targetRatio).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(bitmap, 0, (bitmap.height - height) / 2, bitmap.width, height)
            }
            else -> bitmap
        }
    }

    /** Отпечаток исходного документа; null, если файл не читается. */
    fun sourceFingerprint(uri: Uri, sizeBytes: Long): BookFingerprint? = runCatching {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        BookFingerprint(sizeBytes = sizeBytes, headHash = Fingerprints.headHash(stream))
    }.getOrNull()

    /** Отпечаток уже сохранённого в библиотеке файла — для дозаполнения старых записей. */
    fun fileFingerprint(file: File): BookFingerprint? = runCatching {
        BookFingerprint(sizeBytes = file.length(), headHash = Fingerprints.headHash(file.inputStream()))
    }.getOrNull()

    /**
     * У русских книг самый частый вид — .fb2.zip, поэтому zip разбирается наравне
     * с обычным файлом. Голый .zip тоже пробуем: если внутри нет fb2, импорт
     * откажется с UNKNOWN_FORMAT.
     */
    private fun detectKind(displayName: String?): SourceKind? {
        val name = displayName?.lowercase() ?: return null
        return when {
            name.endsWith(".zip") -> SourceKind(BookFormat.FB2, zipped = true)
            else -> BookFormat.fromExtension(name.substringAfterLast('.', ""))
                ?.let { SourceKind(it, zipped = false) }
        }
    }

    private fun copyToStorage(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException(ImportFailure.UNREADABLE_FILE)
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink) }
        }
    }

    /**
     * Распаковка идёт потоком сразу в целевой файл: ни временного файла на диске,
     * ни книги целиком в памяти. Имена записей читаем как ISO-8859-1 — в русских
     * архивах они бывают в CP866, и разбор UTF-8 на таких именах падает; суффикс
     * .fb2 всё равно ASCII, так что проверка не страдает.
     */
    private fun extractFb2(uri: Uri, target: File) {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw ImportException(ImportFailure.UNREADABLE_FILE)

        input.use { raw ->
            ZipInputStream(raw.buffered(), Charsets.ISO_8859_1).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".fb2")) {
                        target.outputStream().use { sink -> zip.copyTo(sink) }
                        return
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        throw ImportException(ImportFailure.UNKNOWN_FORMAT)
    }

    /** EPUB читает Readium. Своего парсера нет и не будет. */
    /**
     * Название книги: сначала из самой книги, потом из имени файла.
     *
     * В PDF поле Title чаще всего заполнила программа, которой файл печатали
     * («Microsoft Word - Документ1»), поэтому такие названия считаются
     * отсутствующими — см. [FileNameCleaner.isJunkTitle].
     */
    private fun nameOf(metadata: SourceMetadata, fileName: String): FileNameCleaner.CleanedName {
        val fromBook = metadata.title?.trim()?.takeIf { it.isNotEmpty() }
        val author = metadata.author?.trim()?.takeIf { it.isNotEmpty() }

        if (fromBook != null && !FileNameCleaner.isJunkTitle(fromBook, fileName)) {
            return FileNameCleaner.CleanedName(fromBook, author)
        }

        val cleaned = FileNameCleaner.clean(fileName)
        return FileNameCleaner.CleanedName(
            title = cleaned.title.ifBlank { fileName },
            // Автор из имени файла берётся, только если своего нет.
            author = author ?: cleaned.author,
        )
    }

    private suspend fun readWithReadium(file: File, pdf: Boolean): SourceMetadata? {
        val asset = assetRetriever.retrieve(file).getOrElse { return null }
        val publication = publicationOpener
            .open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return null
            }

        return try {
            SourceMetadata(
                title = publication.metadata.title,
                author = publication.metadata.authors.firstOrNull()?.name,
                genre = publication.metadata.subjects.firstOrNull()?.name,
                cover = publication.cover(),
                description = publication.metadata.description?.let(::plainText)
                    ?: if (pdf) pdfSubject(file) else null,
            )
        } finally {
            publication.close()
        }
    }

    /**
     * FB2 превращается в EPUB прямо при добавлении, чтобы дальше книгу читал
     * один движок. Если конвертация не удалась, книга всё равно попадает
     * в библиотеку — с пометкой и причиной. Молча выбрасывать файл нельзя:
     * человек добавил его осознанно и должен видеть, что с ним стало.
     */
    /**
     * Повтор подготовки для книги, которая уже лежит в библиотеке.
     * Наружу отдаётся только то, что нужно хранилищу: метаданные при повторе
     * не трогаем, они уже записаны.
     */
    suspend fun prepareExisting(
        id: String,
        filePath: String,
        format: String,
    ): PreparationResult = withContext(Dispatchers.IO) {
        val source = File(filePath)
        if (!source.exists()) {
            return@withContext PreparationResult(
                readerPath = null,
                failure = BookFailure.FILE_MISSING,
                failureDetail = "Файл книги не найден в памяти телефона.",
            )
        }
        if (format != BookFormat.FB2.name) {
            return@withContext PreparationResult(null, null, null)
        }
        val prepared = prepareFb2(id, source)
        PreparationResult(prepared.readerPath, prepared.failure, prepared.failureDetail)
    }

    private fun prepareFb2(id: String, source: File): Prepared {
        val converted = File(booksDir, "$id.epub")
        val result = fb2Converter.convert({ source.inputStream() }, converted)

        return result.fold(
            onSuccess = { report ->
                Prepared(
                    metadata = SourceMetadata(
                        title = report.info.title,
                        author = report.info.authors.firstOrNull(),
                        genre = report.info.genres.firstOrNull(),
                        cover = fb2Cover(source, report.info.coverId),
                        description = report.info.annotation,
                    ),
                    readerPath = converted.absolutePath,
                    failure = null,
                    failureDetail = null,
                )
            },
            onFailure = { error ->
                // Название и автора берём даже у книги, которую не открыть:
                // в библиотеке она должна выглядеть книгой, а не файлом.
                val fallback = readFb2(source)
                Prepared(
                    metadata = fallback,
                    readerPath = null,
                    failure = BookFailure.CONVERSION_FAILED,
                    failureDetail = describeConversionFailure(error),
                )
            },
        )
    }

    private fun describeConversionFailure(error: Throwable): String {
        val failure = (error as? Fb2ConversionException)?.failure
        return when (failure) {
            Fb2ConversionFailure.NO_CONTENT ->
                "В файле не нашлось ни одной главы — похоже, это не FB2."
            Fb2ConversionFailure.UNREADABLE ->
                "Файл не удалось прочитать до конца."
            null -> "Неожиданная ошибка при разборе файла."
        }
    }

    private fun fb2Cover(file: File, coverId: String?): android.graphics.Bitmap? {
        val id = coverId ?: Fb2MetadataReader.read(file).coverId ?: return null
        val bytes = Fb2MetadataReader.readBinary(file, id) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun readFb2(file: File): SourceMetadata {
        val meta = Fb2MetadataReader.read(file)
        val cover = meta.coverId
            ?.let { Fb2MetadataReader.readBinary(file, it) }
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
        return SourceMetadata(
            title = meta.title,
            author = meta.author,
            genre = meta.genre,
            cover = cover,
            description = meta.description,
        )
    }

    /** Аннотация PDF лежит в поле Subject, а Readium его наружу не отдаёт. */
    private suspend fun pdfSubject(file: File): String? {
        val factory = pdfDocumentFactory ?: return null
        val asset = assetRetriever.retrieve(file).getOrElse { return null }
        return runCatching {
            val resource = (asset as? org.readium.r2.shared.util.asset.ResourceAsset)?.resource
                ?: return@runCatching null
            factory.open(resource, password = null).getOrNull()?.use { document ->
                document.subject?.let(::plainText)
            }
        }.getOrNull().also { asset.close() }
    }

    /**
     * Метаданные приходят и с разметкой тоже. Теги убираем, абзацы оставляем:
     * аннотация в одну строку читается как сплошняк.
     */
    private fun plainText(source: String): String? = source
        .replace(PARAGRAPH_BREAK, "\n\n")
        .replace(TAG, "")
        .replace(NBSP, " ")
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(EXTRA_BREAKS, "\n\n")
        .trim()
        .takeIf { it.isNotEmpty() }

    private fun saveThumbnail(id: String, cover: Bitmap): String? = runCatching {
        val thumbnail = scaleToFit(cover, THUMBNAIL_MAX_PX)
        val file = File(coversDir, "$id.jpg")
        file.outputStream().use { out ->
            thumbnail.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_QUALITY, out)
        }
        if (thumbnail !== cover) thumbnail.recycle()
        file.absolutePath
    }.getOrNull()

    private fun scaleToFit(bitmap: Bitmap, maxPx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxPx) return bitmap
        val ratio = maxPx.toFloat() / longest
        val width = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /** "Толстой. Война и мир.fb2.zip" -> "Толстой. Война и мир". */
    private fun fallbackTitle(displayName: String?, target: File): String {
        var name = displayName ?: return target.name
        if (name.lowercase().endsWith(".zip")) {
            name = name.dropLast(".zip".length)
        }
        val dot = name.lastIndexOf('.')
        if (dot > 0) {
            name = name.substring(0, dot)
        }
        return name.ifBlank { target.name }
    }

    fun queryDocument(uri: Uri): DocumentInfo {
        // При полном доступе книги приходят как file:// — у них нет провайдера,
        // который ответил бы на query, зато имя и размер берутся напрямую.
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = uri.path?.let(::File)
            if (file != null) {
                return DocumentInfo(file.name, file.length())
            }
        }

        context.contentResolver
            .query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else {
                        0L
                    }
                    return DocumentInfo(name ?: uri.lastPathSegment, size)
                }
            }
        return DocumentInfo(uri.lastPathSegment, 0L)
    }

    private companion object {
        const val COVER_MAX_PX = 1200
        const val COVER_QUALITY = 90
        const val COVER_WIDTH = 2
        const val COVER_HEIGHT = 3

        val PARAGRAPH_BREAK = Regex("(?i)</p>|<br\\s*/?>")
        val TAG = Regex("<[^>]+>")
        val NBSP = Regex("[\\u00A0\\u2007\\u202F]")
        val EXTRA_BREAKS = Regex("\\n{3,}")

        const val THUMBNAIL_MAX_PX = 200
        const val THUMBNAIL_QUALITY = 85
    }
}

/** Что удалось подготовить к чтению и что помешало. */
data class PreparationResult(
    val readerPath: String?,
    val failure: BookFailure?,
    val failureDetail: String?,
)
