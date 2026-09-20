package com.bibliarium.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Перечисления хранятся строками, а не через TypeConverter: схема остаётся
 * читаемой, а маппинг в доменную модель — единственным местом, где они трактуются.
 */
@Entity(
    tableName = "books",
    indices = [
        Index("author"),
        Index("status"),
        Index("shelfId"),
        Index("addedAt"),
        Index("lastOpenedAt"),
        // По размеру отбираются кандидаты на совпадение, прежде чем считать хэш.
        Index("fileSize"),
    ],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val format: String,
    val filePath: String,
    val coverPath: String?,
    val addedAt: Long,
    val lastOpenedAt: Long?,
    val progress: Float,
    val locator: String?,
    val status: String,
    val genre: String?,
    val shelfId: String?,
    val isFavorite: Boolean,
    /**
     * Размер и хэш начала ИСХОДНОГО файла — того, который выбрал пользователь,
     * а не того, что лежит в filesDir. Для .fb2.zip это отпечаток архива:
     * иначе повторный поиск не узнал бы уже добавленную книгу.
     */
    val fileSize: Long,
    val headHash: String?,
    /**
     * Файл, который открывает движок чтения. Для FB2 это EPUB после
     * конвертации; filePath при этом продолжает указывать на исходник,
     * чтобы конвертацию можно было повторить.
     */
    val readerPath: String?,
    /** Код причины, по которой книга не открывается; null — всё в порядке. */
    val openFailure: String?,
    /** Та же причина словами, для человека. */
    val openFailureDetail: String?,
)

@Entity(
    tableName = "highlights",
    indices = [Index("bookId")],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class HighlightEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val text: String,
    val note: String?,
    val locator: String,
    val color: Int,
    val createdAt: Long,
)

@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
)

/**
 * Очередь пакетного импорта. Лежит в базе, а не в памяти и не в Data воркера,
 * по трём причинам: импорт переживает сворачивание и смерть процесса, размер
 * пачки ничем не ограничен, и после завершения видно, что именно не прочиталось.
 */
@Entity(
    tableName = "import_queue",
    indices = [Index("batchId"), Index("status")],
)
data class ImportQueueEntity(
    @PrimaryKey val id: String,
    val batchId: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    /** PENDING, DONE, FAILED. */
    val status: String,
    /** Имя значения ImportFailure, если status = FAILED. */
    val failure: String?,
    val createdAt: Long,
)
