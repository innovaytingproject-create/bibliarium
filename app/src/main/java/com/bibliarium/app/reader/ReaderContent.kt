package com.bibliarium.app.reader

import android.content.Context
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookFormat
import java.io.File
import org.json.JSONObject
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.streamer.PublicationOpener

/** Чем показывать книгу. Выбирается по [Book.format], а не по догадкам о содержимом. */
enum class ReaderEngine {
    /** EPUB и сконвертированный в него FB2. */
    EPUB,
    PDF,
}

/** Открытая книга: публикация Readium плюс всё, что нужно экрану. */
class ReaderContent(
    val book: Book,
    val publication: Publication,
    val engine: ReaderEngine,
    /** Позиция, с которой открывать. null — с начала. */
    val initialLocator: Locator?,
    /** Сколько всего позиций в книге; для PDF это страницы. */
    val totalPositions: Int,
) {
    fun close() {
        runCatching { publication.close() }
    }
}

sealed interface ReaderOpenError {
    /** Файла нет на диске — книгу добавили, а файл потом удалили. */
    data object FileMissing : ReaderOpenError

    /** Файл есть, но движок его не разобрал. */
    data object Unreadable : ReaderOpenError

    /** Формат известен модели, но читать его пока нечем. */
    data object UnsupportedFormat : ReaderOpenError

    /** Книга в библиотеке есть, но к чтению не подготовлена. */
    data object NotPrepared : ReaderOpenError
}

@OptIn(ExperimentalReadiumApi::class)
class ReaderContentOpener(
    private val context: Context,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
) {

    suspend fun open(book: Book): Result<ReaderContent> {
        val engine = when (book.format) {
            // FB2 читается как EPUB: конвертация прошла при импорте.
            BookFormat.EPUB, BookFormat.FB2 -> ReaderEngine.EPUB
            BookFormat.PDF -> ReaderEngine.PDF
            BookFormat.DJVU ->
                return Result.failure(ReaderOpenException(ReaderOpenError.UnsupportedFormat))
        }

        if (book.format == BookFormat.FB2 && book.readerPath == null) {
            // Конвертация не удалась — открывать нечего, и сказать об этом
            // надо честно, а не показывать пустой экран.
            return Result.failure(ReaderOpenException(ReaderOpenError.NotPrepared))
        }

        val file = File(book.contentPath)
        if (!file.exists()) {
            return Result.failure(ReaderOpenException(ReaderOpenError.FileMissing))
        }

        val asset = assetRetriever.retrieve(file)
            .getOrElse { return Result.failure(ReaderOpenException(ReaderOpenError.Unreadable)) }

        val publication = publicationOpener
            .open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return Result.failure(ReaderOpenException(ReaderOpenError.Unreadable))
            }

        val positions = runCatching { publication.positions() }.getOrDefault(emptyList())

        return Result.success(
            ReaderContent(
                book = book,
                publication = publication,
                engine = engine,
                initialLocator = book.locator?.let(::parseLocator),
                totalPositions = positions.size,
            ),
        )
    }

    private fun parseLocator(json: String): Locator? = runCatching {
        Locator.fromJSON(JSONObject(json))
    }.getOrNull()
}

class ReaderOpenException(val error: ReaderOpenError) : Exception(error.toString())

/** Локатор сохраняется в том же JSON, в каком его отдаёт Readium. */
fun Locator.serialize(): String = toJSON().toString()
