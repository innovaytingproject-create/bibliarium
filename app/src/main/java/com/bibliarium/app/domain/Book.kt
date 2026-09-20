package com.bibliarium.app.domain

/**
 * Книга в терминах предметной области. UI и остальные слои знают только этот тип —
 * ни Room-сущности, ни Readium сюда не протекают.
 */
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val filePath: String,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    /** 0..1 */
    val progress: Float,
    /** Позиция чтения в формате Readium (JSON-локатор). */
    val locator: String?,
    val status: ReadingStatus,
    val genre: String?,
    val shelfId: String?,
    val isFavorite: Boolean,
    /** Размер исходного файла в байтах. */
    val fileSize: Long,
    /** SHA-256 первых 64 КБ исходного файла; null у книг, добавленных до появления отпечатков. */
    val headHash: String?,
)

enum class ReadingStatus {
    NOT_STARTED,
    READING,
    FINISHED,
}

/**
 * DJVU пока не импортируется, но формат в модели уже есть: экран чтения
 * выбирает движок по нему.
 */
enum class BookFormat(val extension: String) {
    EPUB("epub"),
    FB2("fb2"),
    PDF("pdf"),
    DJVU("djvu"),
    ;

    /** Поддерживается ли формат импортом и чтением в текущей версии. */
    val isSupported: Boolean
        get() = this == EPUB || this == FB2 || this == PDF

    companion object {
        fun fromExtension(extension: String): BookFormat? =
            when (extension.lowercase()) {
                "epub" -> EPUB
                "fb2" -> FB2
                "pdf" -> PDF
                "djvu", "djv" -> DJVU
                else -> null
            }
    }
}
