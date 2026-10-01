package com.bibliarium.app.reader

import android.content.Context
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookFormat
import java.io.File
import org.json.JSONObject
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.streamer.PublicationOpener

/** Чем показывать книгу. Выбирается по [Book.format], а не по догадкам о содержимом. */
enum class ReaderEngine {
    /** EPUB и сконвертированный в него FB2. */
    EPUB,
    PDF,
}

/** Где начинается и где заканчивается глава в книге целиком, 0..1. */
data class ResourceSpan(val start: Double, val end: Double)

/** Открытая книга: публикация Readium плюс всё, что нужно экрану. */
class ReaderContent(
    val book: Book,
    val publication: Publication,
    val engine: ReaderEngine,
    /** Позиция, с которой открывать. null — с начала. */
    val initialLocator: Locator?,
    /** Сколько всего позиций в книге; для PDF это страницы. */
    val totalPositions: Int,
    /** Оглавление: что показать в списке глав и куда по нему прыгать. */
    val tableOfContents: List<TocEntry>,
    /** Границы каждой главы в книге; по ним считается прогресс чтения. */
    private val spans: Map<String, ResourceSpan> = emptyMap(),
    /** Позиции книги: по ним работает перескок полосой прогресса. */
    private val positions: List<Locator> = emptyList(),
) {
    /**
     * Доля прочитанного от всей книги.
     *
     * Брать `totalProgression` из локатора напрямую нельзя: он меняется
     * только при переходе на следующую позицию Readium, а внутри главы стоит
     * на месте. У книги из шести глав это означало ноль на всё чтение первой
     * главы — прогресс не двигался вовсе.
     *
     * Поэтому берётся место главы в книге и внутри него — доля, пройденная
     * по самой главе. Границы глав считает Readium, так что длинная глава
     * весит больше короткой.
     */
    fun progressOf(locator: Locator): Float {
        val within = locator.locations.progression ?: 0.0
        val span = spans[locator.href.toString()]
        val value = when {
            span != null -> span.start + (span.end - span.start) * within
            else -> locator.locations.totalProgression ?: within
        }
        return value.toFloat().coerceIn(0f, 1f)
    }

    /** Место в книге по доле от начала — для перескока полосой прогресса. */
    fun positionAt(fraction: Float): Locator? {
        if (positions.isEmpty()) return null
        val index = (fraction * (positions.size - 1)).toInt().coerceIn(0, positions.size - 1)
        return positions[index]
    }

    /** Какая глава на этой доле книги — для подсказки над пальцем. */
    fun chapterAt(fraction: Float): String? {
        val target = positionAt(fraction) ?: return null
        return tableOfContents.lastOrNull { entry ->
            entry.locator.href == target.href
        }?.title
            ?: tableOfContents.lastOrNull { entry ->
                (entry.locator.locations.totalProgression ?: 0.0) <= fraction
            }?.title
    }

    fun close() {
        runCatching { publication.close() }
    }
}

/**
 * Строка оглавления.
 *
 * [page] у PDF — настоящая страница, у EPUB — номер позиции Readium:
 * единственное, что в книге без вёрстки можно назвать страницей.
 * [depth] — вложенность: подглавы показываются со сдвигом.
 */
data class TocEntry(
    val title: String,
    val locator: Locator,
    val page: Int? = null,
    val depth: Int = 0,
)

sealed interface ReaderOpenError {
    /** Место обрыва словами — то, что стоит показать и записать в лог. */
    val detail: String?

    /** Файла нет на диске — книгу добавили, а файл потом удалили. */
    data object FileMissing : ReaderOpenError {
        override val detail: String? = null
    }

    /**
     * Файл есть, но движок его не разобрал.
     *
     * Причину несём с собой: «книга повреждена» без подробностей не даёт
     * ничего ни человеку, ни разбору потом.
     */
    data class Unreadable(override val detail: String?) : ReaderOpenError

    /** Формат известен модели, но читать его пока нечем. */
    data object UnsupportedFormat : ReaderOpenError {
        override val detail: String? = null
    }

    /** Книга в библиотеке есть, но к чтению не подготовлена. */
    data object NotPrepared : ReaderOpenError {
        override val detail: String? = null
    }
}

@OptIn(ExperimentalReadiumApi::class)
class ReaderContentOpener(
    private val context: Context,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
) {

    suspend fun open(book: Book, locatorOverride: String? = null): Result<ReaderContent> {
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

        val asset = assetRetriever.retrieve(file).getOrElse { error ->
            return Result.failure(
                ReaderOpenException(
                    ReaderOpenError.Unreadable(
                        "не удалось открыть файл (${file.name}): ${describe(error)}",
                    ),
                ),
            )
        }

        val publication = publicationOpener
            .open(asset, allowUserInteraction = false)
            .getOrElse { error ->
                asset.close()
                return Result.failure(
                    ReaderOpenException(
                        ReaderOpenError.Unreadable(
                            "движок не разобрал книгу (${asset.format.mediaType}): " +
                                describe(error),
                        ),
                    ),
                )
            }

        val byResource = runCatching { publication.positionsByReadingOrder() }
            .getOrDefault(emptyList())
        val positions = byResource.flatten()
        val toc = runCatching { buildToc(publication, engine, positions) }
            .getOrDefault(emptyList())

        return Result.success(
            ReaderContent(
                book = book,
                publication = publication,
                engine = engine,
                initialLocator = (locatorOverride ?: book.locator)?.let(::parseLocator),
                totalPositions = positions.size,
                tableOfContents = toc,
                spans = spansOf(byResource),
                positions = positions,
            ),
        )
    }

    /**
     * Оглавление книги.
     *
     * У EPUB, если оглавления нет, вместо него берётся порядок чтения: там это
     * настоящие файлы глав, и список получается осмысленный. У PDF порядок
     * чтения — это весь документ одной строкой, подставлять его незачем.
     * Пустой список у PDF означает ровно одно: закладок в файле нет, и экран
     * покажет вместо оглавления сетку страниц.
     */
    private suspend fun buildToc(
        publication: Publication,
        engine: ReaderEngine,
        positions: List<Locator>,
    ): List<TocEntry> {
        val links = when (engine) {
            ReaderEngine.PDF -> publication.tableOfContents
            ReaderEngine.EPUB ->
                publication.tableOfContents.ifEmpty { publication.readingOrder }
        }
        return flatten(links).mapIndexedNotNull { index, entry ->
            val locator = publication.locatorFromLink(entry.link)
                ?: return@mapIndexedNotNull null
            val title = entry.link.title?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Часть ${index + 1}"
            TocEntry(
                title = title,
                locator = locator,
                page = pageOf(locator) ?: positionOf(locator, positions),
                depth = entry.depth,
            )
        }
    }

    /** Вложенные разделы показываются тем же списком, но со сдвигом. */
    private fun flatten(links: List<Link>, depth: Int = 0): List<FlatLink> =
        links.flatMap { link ->
            listOf(FlatLink(link, depth)) + flatten(link.children, depth + 1)
        }

    /**
     * Номер позиции, с которой начинается глава.
     *
     * В EPUB страниц нет — вёрстка зависит от шрифта и полей, — но человеку
     * нужен хоть какой-то ориентир. Позиции Readium для этого и есть: они
     * не плывут от настроек.
     */
    private fun positionOf(locator: Locator, positions: List<Locator>): Int? =
        positions.firstOrNull { it.href == locator.href }?.locations?.position

    /** Закладка PDF приезжает ссылкой вида `book.pdf#page=7`. */
    private fun pageOf(locator: Locator): Int? =
        locator.locations.fragments.firstNotNullOfOrNull { fragment ->
            PAGE_FRAGMENT.find(fragment)?.groupValues?.get(1)?.toIntOrNull()
        }

    private data class FlatLink(val link: Link, val depth: Int)

    /**
     * Границы глав в книге целиком.
     *
     * Начало главы — доля, на которой стоит её первая позиция; конец — начало
     * следующей главы, а у последней это единица.
     */
    private fun spansOf(byResource: List<List<Locator>>): Map<String, ResourceSpan> {
        val result = mutableMapOf<String, ResourceSpan>()
        byResource.forEachIndexed { index, positions ->
            val first = positions.firstOrNull() ?: return@forEachIndexed
            val start = first.locations.totalProgression ?: return@forEachIndexed
            val next = byResource.drop(index + 1)
                .firstNotNullOfOrNull { it.firstOrNull() }
                ?.locations
                ?.totalProgression
            result[first.href.toString()] = ResourceSpan(start, next ?: 1.0)
        }
        return result
    }

    /** Разворачивает цепочку причин Readium в одну строку. */
    private fun describe(error: org.readium.r2.shared.util.Error): String {
        val chain = generateSequence(error) { it.cause }
            .take(MAX_CAUSE_DEPTH)
            .map { it.message }
            .toList()
        return chain.joinToString(" <- ")
    }

    private fun parseLocator(json: String): Locator? = runCatching {
        Locator.fromJSON(JSONObject(json))
    }.getOrNull()

    private companion object {
        const val MAX_CAUSE_DEPTH = 5

        val PAGE_FRAGMENT = Regex("page=(\\d+)")
    }
}

class ReaderOpenException(val error: ReaderOpenError) : Exception(error.toString())

/** Локатор сохраняется в том же JSON, в каком его отдаёт Readium. */
fun Locator.serialize(): String = toJSON().toString()
