package com.bibliarium.app.domain

/** Ручная полка пользователя. */
data class Shelf(
    val id: String,
    val name: String,
    val sortOrder: Int,
)
