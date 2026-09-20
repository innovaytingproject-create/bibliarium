package com.bibliarium.app.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Миниатюры страниц PDF.
 *
 * Рисуются системным `PdfRenderer` — он есть в самой Android и ничего
 * доставлять не нужно. Рисуются по требованию: сетка просит картинку только
 * для тех клеток, которые видно. Иначе книга в тысячу страниц открывала бы
 * окно минуту.
 *
 * Готовое лежит в [LruCache], поэтому прокрутка назад и повторное открытие
 * окна ничего не перерисовывают. `PdfRenderer` не потокобезопасен: страницы
 * рисуются по одной под mutex.
 */
class PdfPageThumbnails(file: File) : AutoCloseable {

    private val descriptor: ParcelFileDescriptor? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }.getOrNull()

    private val renderer: PdfRenderer? = descriptor?.let { fd ->
        runCatching { PdfRenderer(fd) }.getOrNull()
    }

    /** Ноль означает, что страницы показать нечем: файла нет или он не читается. */
    val pageCount: Int = renderer?.pageCount ?: 0

    private val mutex = Mutex()

    private val cache = object : LruCache<Int, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / BYTES_IN_KB
    }

    /** Готовая миниатюра, если она уже нарисована. Без ожидания и без рисования. */
    fun cached(page: Int): Bitmap? = cache[page]

    /** Номер страницы человеческий, с единицы. */
    suspend fun render(page: Int, width: Int): Bitmap? {
        cache[page]?.let { return it }
        val source = renderer ?: return null

        return withContext(Dispatchers.IO) {
            mutex.withLock {
                cache[page] ?: runCatching {
                    source.openPage(page - 1).use { pdfPage ->
                        val height = (width.toLong() * pdfPage.height / pdfPage.width)
                            .toInt()
                            .coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // PdfRenderer рисует по прозрачному фону, а бумага белая.
                        Canvas(bitmap).drawColor(Color.WHITE)
                        pdfPage.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                        )
                        bitmap
                    }
                }.getOrNull()?.also { cache.put(page, it) }
            }
        }
    }

    override fun close() {
        cache.evictAll()
        runCatching { renderer?.close() }
        runCatching { descriptor?.close() }
    }

    private companion object {
        const val BYTES_IN_KB = 1024
        const val MEMORY_SHARE = 8

        fun cacheSizeKb(): Int =
            ((Runtime.getRuntime().maxMemory() / BYTES_IN_KB) / MEMORY_SHARE).toInt()
    }
}
