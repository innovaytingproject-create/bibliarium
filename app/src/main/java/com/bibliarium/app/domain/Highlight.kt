package com.bibliarium.app.domain

/** Выделение или цитата внутри книги. Появится на экранах на четвёртом этапе. */
data class Highlight(
    val id: String,
    val bookId: String,
    val text: String,
    val note: String?,
    /** Локатор Readium — по нему выделение подсвечивается в тексте. */
    val locator: String,
    /** ARGB. */
    val color: Int,
    val createdAt: Long,
)
