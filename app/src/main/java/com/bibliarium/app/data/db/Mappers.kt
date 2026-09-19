package com.bibliarium.app.data.db

import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookFormat
import com.bibliarium.app.domain.Highlight
import com.bibliarium.app.domain.ReadingStatus
import com.bibliarium.app.domain.Shelf

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    format = runCatching { BookFormat.valueOf(format) }.getOrDefault(BookFormat.EPUB),
    filePath = filePath,
    coverPath = coverPath,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    progress = progress,
    locator = locator,
    status = runCatching { ReadingStatus.valueOf(status) }.getOrDefault(ReadingStatus.NOT_STARTED),
    genre = genre,
    shelfId = shelfId,
    isFavorite = isFavorite,
    fileSize = fileSize,
    headHash = headHash,
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    format = format.name,
    filePath = filePath,
    coverPath = coverPath,
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
    progress = progress,
    locator = locator,
    status = status.name,
    genre = genre,
    shelfId = shelfId,
    isFavorite = isFavorite,
    fileSize = fileSize,
    headHash = headHash,
)

fun HighlightEntity.toDomain(): Highlight = Highlight(
    id = id,
    bookId = bookId,
    text = text,
    note = note,
    locator = locator,
    color = color,
    createdAt = createdAt,
)

fun Highlight.toEntity(): HighlightEntity = HighlightEntity(
    id = id,
    bookId = bookId,
    text = text,
    note = note,
    locator = locator,
    color = color,
    createdAt = createdAt,
)

fun ShelfEntity.toDomain(): Shelf = Shelf(id = id, name = name, sortOrder = sortOrder)

fun Shelf.toEntity(): ShelfEntity = ShelfEntity(id = id, name = name, sortOrder = sortOrder)
