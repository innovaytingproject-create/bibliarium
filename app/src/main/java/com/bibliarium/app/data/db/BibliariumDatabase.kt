package com.bibliarium.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Database(
    entities = [
        BookEntity::class,
        HighlightEntity::class,
        ShelfEntity::class,
        ImportQueueEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class BibliariumDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun shelfDao(): ShelfDao
    abstract fun importQueueDao(): ImportQueueDao

    companion object {
        const val NAME = "bibliarium.db"

        /**
         * Третья версия знает, что книгу могло не получиться открыть, и где
         * лежит файл для движка чтения. Снова миграция, а не пересоздание:
         * в библиотеке уже есть добавленные книги.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `books` ADD COLUMN `readerPath` TEXT")
                connection.execSQL("ALTER TABLE `books` ADD COLUMN `openFailure` TEXT")
                connection.execSQL("ALTER TABLE `books` ADD COLUMN `openFailureDetail` TEXT")
            }
        }

        /**
         * Первая версия ничего не знала об отпечатках файлов и о пакетном импорте.
         * Миграция, а не пересоздание базы: на телефоне уже лежат добавленные книги.
         * headHash оставляем пустым — он досчитается по месту при первом поиске.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "ALTER TABLE `books` ADD COLUMN `fileSize` INTEGER NOT NULL DEFAULT 0",
                )
                connection.execSQL("ALTER TABLE `books` ADD COLUMN `headHash` TEXT")
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_books_fileSize` ON `books` (`fileSize`)",
                )
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `import_queue` (
                        `id` TEXT NOT NULL,
                        `batchId` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `failure` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_import_queue_batchId` " +
                        "ON `import_queue` (`batchId`)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_import_queue_status` " +
                        "ON `import_queue` (`status`)",
                )
            }
        }
    }
}
