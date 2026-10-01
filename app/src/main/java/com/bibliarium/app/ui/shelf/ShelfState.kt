package com.bibliarium.app.ui.shelf

import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookRating
import com.bibliarium.app.domain.ReadingStatus

/** Что показывает полка: корешки или сетку обложек. */
enum class ShelfMode {
    SPINES,
    GRID,
}

/** По чему собираются ярусы. Раздел 6 ТЗ. */
enum class ShelfGrouping {
    STATUS,
    AUTHOR,
    GENRE,
}

/** Строка фильтров над ярусами. */
enum class ShelfFilter {
    ALL,
    READING,
    FINISHED,
    FAVORITE,

    /** Книги с высокой оценкой — от 4,5. */
    LOVED,
}

/** Ярус: заголовок и книги в нём. */
data class ShelfTier(
    val key: String,
    /** Имя без «Ярус A ·» — приставку добавляет экран, она зависит от порядка. */
    val name: String,
    val books: List<Book>,
)

/** Сколько корешков помещается в ряд до кнопки «Все N». */
const val TIER_LIMIT = 20

/**
 * Ярусы полки.
 *
 * Считается на каждое изменение списка, поэтому здесь нет ничего тяжелее
 * группировки и сортировки: на пятистах книгах это доли миллисекунды, а вот
 * лишний Bitmap или открытие файла на этом месте стоили бы прокрутки.
 */
fun buildTiers(
    books: List<Book>,
    grouping: ShelfGrouping,
    filter: ShelfFilter,
    query: String,
    unknownAuthor: String,
    unknownGenre: String,
    statusNames: Map<ReadingStatus, String>,
    /** Оценки книг: по ним работают «Любимые» и порядок в ярусе «Прочитано». */
    ratings: Map<String, BookRating> = emptyMap(),
): List<ShelfTier> {
    val visible = books
        .filter { it.matches(filter, ratings[it.id]) }
        .filter { it.matches(query) }

    if (visible.isEmpty()) return emptyList()

    val groups = when (grouping) {
        ShelfGrouping.STATUS -> visible.groupBy { statusNames.getValue(it.status) }
        ShelfGrouping.AUTHOR -> visible.groupBy {
            it.author?.trim()?.takeIf(String::isNotEmpty) ?: unknownAuthor
        }
        ShelfGrouping.GENRE -> visible.groupBy {
            it.genre?.trim()?.takeIf(String::isNotEmpty) ?: unknownGenre
        }
    }

    val order = when (grouping) {
        // У статусов порядок свой: сначала то, что человек читает сейчас.
        ShelfGrouping.STATUS -> statusNames.values.toList()
        else -> groups.keys.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    return order.mapNotNull { name ->
        val group = groups[name] ?: return@mapNotNull null
        ShelfTier(
            key = name,
            name = name,
            // Прочитанное выстраивается по оценке: там уже не важно, когда
            // книгу открывали, важно, какой она оказалась. В остальных
            // ярусах впереди недавно открытые.
            books = if (group.all { it.status == ReadingStatus.FINISHED }) {
                group.sortedWith(
                    compareByDescending<Book> { ratings[it.id]?.overall ?: 0f }
                        .thenByDescending { it.lastOpenedAt ?: 0L },
                )
            } else {
                group.sortedWith(
                    compareByDescending<Book> { it.lastOpenedAt ?: 0L }
                        .thenByDescending { it.addedAt },
                )
            },
        )
    }
}

private fun Book.matches(filter: ShelfFilter, rating: BookRating?): Boolean = when (filter) {
    ShelfFilter.ALL -> true
    ShelfFilter.READING -> status == ReadingStatus.READING
    ShelfFilter.FINISHED -> status == ReadingStatus.FINISHED
    ShelfFilter.FAVORITE -> isFavorite
    ShelfFilter.LOVED -> (rating?.overall ?: 0f) >= BookRating.FAVOURITE_FROM
}

private fun Book.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return title.contains(needle, ignoreCase = true) ||
        author.orEmpty().contains(needle, ignoreCase = true)
}

/**
 * Книга для карточки «сейчас читаю»: последняя открытая из начатых.
 * Если ещё ничего не открывали — карточки нет, и это не ошибка.
 */
fun currentlyReading(books: List<Book>): Book? =
    books.filter { it.lastOpenedAt != null && it.status != ReadingStatus.FINISHED }
        .maxByOrNull { it.lastOpenedAt ?: 0L }
