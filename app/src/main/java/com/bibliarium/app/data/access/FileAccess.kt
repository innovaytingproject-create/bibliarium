package com.bibliarium.app.data.access

import com.bibliarium.app.domain.FoundBook

/**
 * Откуда приложение берёт файлы книг. Две реализации:
 * [TreeFileAccess] — папки, выбранные через SAF, и [AllFilesAccess] — полный
 * доступ ко всей памяти.
 *
 * Экран поиска работает только с этим интерфейсом и не знает, какая реализация
 * включена. Поэтому вариант сборки без MANAGE_EXTERNAL_STORAGE ничего не ломает:
 * [AllFilesAccess.isAvailable] просто всегда возвращает false.
 */
interface FileAccess {

    /** Можно ли сейчас искать: есть ли выбранные папки или выдан полный доступ. */
    fun isAvailable(): Boolean

    /**
     * Обходит доступное дерево и возвращает найденные книги без сверки
     * с библиотекой и без прочитанных названий — этим занимается BookScanner.
     *
     * @param onProgress scanned — сколько папок просмотрено, found — сколько книг найдено.
     */
    suspend fun findBooks(onProgress: (scanned: Int, found: Int) -> Unit): List<FoundBook>
}
