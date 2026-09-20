package com.bibliarium.app.data.importer.fb2

/**
 * Метаданные FB2. Пустые поля и несколько авторов — обычное дело, а не ошибка,
 * поэтому всё необязательное и списочное.
 */
data class Fb2BookInfo(
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val translators: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val language: String? = null,
    val year: String? = null,
    val annotation: String? = null,
    val seriesName: String? = null,
    val seriesNumber: Int? = null,
    /** Идентификатор картинки обложки из <coverpage>. */
    val coverId: String? = null,
) {
    val primaryAuthor: String? get() = authors.firstOrNull()
}

/** Что получилось после конвертации. */
data class Fb2ConversionReport(
    val info: Fb2BookInfo,
    val chapters: List<String>,
    val imageCount: Int,
    val noteCount: Int,
    /**
     * Разбор оборвался на сломанном месте, но всё, что успели прочитать,
     * попало в книгу. Читателю лучше половина книги, чем ничего.
     */
    val truncated: Boolean,
)

/** Почему конвертация не удалась совсем. */
enum class Fb2ConversionFailure {
    /** Файл не открылся или оборвался до первой главы. */
    UNREADABLE,

    /** Разобрали, но глав не нашлось — значит это не FB2. */
    NO_CONTENT,
}

class Fb2ConversionException(
    val failure: Fb2ConversionFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)
