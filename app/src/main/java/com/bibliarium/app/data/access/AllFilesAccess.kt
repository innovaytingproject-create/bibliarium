package com.bibliarium.app.data.access

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import com.bibliarium.app.BuildConfig
import com.bibliarium.app.domain.BookFormat
import com.bibliarium.app.domain.FoundBook
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Поиск по всей памяти при выданном MANAGE_EXTERNAL_STORAGE.
 *
 * Разрешение объявлено только в варианте сборки full. В варианте play
 * [BuildConfig.ALL_FILES_ACCESS] равен false, [isAvailable] всегда возвращает
 * false, и приложение целиком работает через [TreeFileAccess] — ничего
 * отключать в коде не нужно.
 *
 * Android/data и Android/obb недоступны даже с полным доступом: это
 * ограничение системы с Android 11. Android/media доступна — там Telegram.
 */
class AllFilesAccess(context: Context) : FileAccess {

    private val appContext = context.applicationContext

    override fun isAvailable(): Boolean = supported() && granted()

    override suspend fun findBooks(
        onProgress: (scanned: Int, found: Int) -> Unit,
    ): List<FoundBook> = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext emptyList()

        @Suppress("DEPRECATION")
        val root = Environment.getExternalStorageDirectory()
            ?: return@withContext emptyList()

        val found = mutableListOf<FoundBook>()
        val archives = mutableListOf<Pair<File, String>>()

        val stack = ArrayDeque<Pair<File, String>>()
        stack.addLast(root to "")
        var scanned = 0

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()

            val (directory, path) = stack.removeLast()
            scanned++
            onProgress(scanned, found.size + archives.size)

            val children = directory.listFiles() ?: continue
            for (child in children) {
                currentCoroutineContext().ensureActive()

                val name = child.name
                if (child.isDirectory) {
                    val childPath = if (path.isEmpty()) name else "$path/$name"
                    if (!BookFiles.isSkippedDirectory(name, childPath)) {
                        stack.addLast(child to childPath)
                    }
                    continue
                }

                val format = BookFiles.formatOf(name)
                if (format != null) {
                    found += BookFiles.toFoundBook(
                        uri = Uri.fromFile(child).toString(),
                        fileName = name,
                        folder = path,
                        sizeBytes = child.length(),
                        format = format,
                    )
                } else if (BookFiles.isZipCandidate(name)) {
                    archives += child to path
                }
            }
        }

        for ((archive, folder) in archives) {
            currentCoroutineContext().ensureActive()
            if (archive.length() <= BookFiles.MAX_ZIP_BYTES &&
                BookFiles.containsFb2 { archive.inputStream() }
            ) {
                found += BookFiles.toFoundBook(
                    uri = Uri.fromFile(archive).toString(),
                    fileName = archive.name,
                    folder = folder,
                    sizeBytes = archive.length(),
                    format = BookFormat.FB2,
                )
                onProgress(scanned, found.size)
            }
        }

        found
    }

    companion object {
        /** Собран ли вариант с разрешением и достаточно ли свежая система. */
        fun supported(): Boolean =
            BuildConfig.ALL_FILES_ACCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        /** Выдано ли разрешение прямо сейчас — пользователь мог отозвать его в настройках. */
        fun granted(): Boolean =
            supported() &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                Environment.isExternalStorageManager()
    }
}
