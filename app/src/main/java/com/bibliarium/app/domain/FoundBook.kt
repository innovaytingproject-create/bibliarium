package com.bibliarium.app.domain

/** Книга, найденная поиском по телефону. Ещё не импортирована. */
data class FoundBook(
    /** content:// документа из выбранного дерева. */
    val uri: String,
    val fileName: String,
    /** Путь папки относительно выбранного корня; пустая строка — сам корень. */
    val folder: String,
    val sizeBytes: Long,
    val format: BookFormat,
    /** Название из метаданных; null, пока не прочитано или не читается. */
    val title: String?,
    val state: FoundBookState,
) {
    val selectable: Boolean
        get() = state == FoundBookState.NEW
}

enum class FoundBookState {
    /** Можно выбрать и добавить. */
    NEW,

    /** Уже есть в библиотеке: совпали размер и хэш начала файла. */
    ALREADY_ADDED,

    /** Формат виден, но в этой версии не добавляется (PDF). */
    NOT_SUPPORTED_YET,
}
