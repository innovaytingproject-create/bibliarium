package com.bibliarium.app.data.importer

/**
 * Причина отказа импорта. Текст для пользователя живёт в strings.xml —
 * слой данных о нём не знает.
 */
enum class ImportFailure {
    UNKNOWN_FORMAT,
    UNSUPPORTED_FORMAT,
    UNREADABLE_FILE,
    PARSE_FAILED,
    STORAGE_FAILED,
}

class ImportException(
    val failure: ImportFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)
