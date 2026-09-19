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
