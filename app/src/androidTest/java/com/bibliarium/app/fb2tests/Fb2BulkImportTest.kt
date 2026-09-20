package com.bibliarium.app.fb2tests

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.appContainer
import com.bibliarium.app.domain.BookFormat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Импорт пачкой: двадцать четыре FB2 подряд, из них большинство с картинками.
 *
 * Проверяется не только «не упало». Если каждая книга держит свой поток,
 * буфер или незакрытый архив, это видно именно здесь: по занятой памяти
 * и по файловым дескрипторам.
 */
@RunWith(AndroidJUnit4::class)
class Fb2BulkImportTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store get() = context.appContainer.bookStore

    private val imported = mutableListOf<String>()
    private lateinit var workDir: File

    @After
    fun tearDown() {
        runBlocking { imported.forEach { store.delete(it) } }
        imported.clear()
        if (::workDir.isInitialized) workDir.deleteRecursively()
    }

    @Test
    fun twentyFourBooksImportWithoutLeaking() {
        workDir = File(context.cacheDir, "fb2-bulk").apply {
            deleteRecursively()
            mkdirs()
        }

        val sources = prepareSources(BOOK_COUNT)
        val usedBefore = usedMemoryBytes()
        val descriptorsBefore = openDescriptors()

        val titles = mutableListOf<String>()
        runBlocking {
            sources.forEach { file ->
                val book = store.add(Uri.fromFile(file)).getOrElse { error ->
                    throw AssertionError("Книга ${file.name} не добавилась: $error")
                }
                imported += book.id
                titles += book.title

                assertEquals(
                    "Формат должен остаться FB2: пользователь добавил именно его",
                    BookFormat.FB2,
                    book.format,
                )
                assertNotNull(
                    "У книги ${file.name} нет подготовленного файла для чтения",
                    book.readerPath,
                )
                assertTrue(
                    "Книга ${file.name} помечена нечитаемой: ${book.openFailureDetail}",
                    book.isReadable,
                )
            }
        }

        val usedAfter = usedMemoryBytes()
        val descriptorsAfter = openDescriptors()
        val growthMb = (usedAfter - usedBefore) / (1024.0 * 1024.0)

        TestArtifacts.note(
            "bulk-import",
            buildString {
                appendLine("книг=${imported.size}")
                appendLine("память до=${usedBefore / 1024 / 1024} МБ")
                appendLine("память после=${usedAfter / 1024 / 1024} МБ")
                appendLine("прирост=%.1f МБ".format(growthMb))
                appendLine("дескрипторов до=$descriptorsBefore после=$descriptorsAfter")
            },
        )

        assertEquals("Добавились не все книги", BOOK_COUNT, imported.size)

        // Каждая книга примерно на килобайт. Если после двух десятков занято
        // на десятки мегабайт больше — значит что-то держится и не отпускается.
        assertTrue(
            "Память выросла на %.1f МБ — похоже, книги не освобождаются".format(growthMb),
            growthMb < MAX_GROWTH_MB,
        )

        // Файловые дескрипторы считаются только там, где procfs доступен.
        if (descriptorsBefore > 0 && descriptorsAfter > 0) {
            assertTrue(
                "Открытых файлов стало $descriptorsAfter против $descriptorsBefore — " +
                    "похоже, потоки не закрываются",
                descriptorsAfter - descriptorsBefore < MAX_DESCRIPTOR_GROWTH,
            )
        }
    }

    /** Копии тестовых книг под разными именами — как будто это разные файлы. */
    private fun prepareSources(count: Int): List<File> {
        val templates = listOf("fb2_images.fb2", "fb2_plain.fb2", "fb2_notes.fb2")
        return (0 until count).map { index ->
            val template = templates[index % templates.size]
            val target = File(workDir, "book_%02d.fb2".format(index))
            instrumentation.context.assets.open(template).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }
    }

    private fun usedMemoryBytes(): Long {
        val runtime = Runtime.getRuntime()
        // Просим собрать мусор дважды: после первого прохода часть объектов
        // ещё достижима из финализаторов.
        repeat(2) {
            runtime.gc()
            Thread.sleep(GC_PAUSE_MS)
        }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun openDescriptors(): Int =
        runCatching { File("/proc/self/fd").listFiles()?.size ?: 0 }.getOrDefault(0)

    private companion object {
        const val BOOK_COUNT = 24
        const val MAX_GROWTH_MB = 24.0
        const val MAX_DESCRIPTOR_GROWTH = 40
        const val GC_PAUSE_MS = 150L
    }
}
