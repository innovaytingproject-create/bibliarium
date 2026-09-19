package com.bibliarium.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [BookEntity::class, HighlightEntity::class, ShelfEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BibliariumDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun shelfDao(): ShelfDao

    companion object {
        const val NAME = "bibliarium.db"
    }
}
