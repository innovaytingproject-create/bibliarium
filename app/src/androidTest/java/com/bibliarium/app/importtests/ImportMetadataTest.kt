package com.bibliarium.app.importtests

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.appContainer
import com.bibliarium.app.data.db.BookEntity
import com.bibliarium.app.domain.Book
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Что попадает в библиотеку при добавлении книги: настоящее название,
 * автор и аннотация — а не имя файла.
 */
@RunWith(AndroidJUnit4::class)
class ImportMetadataTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val store get() = context.appContainer.bookStore
    private val dao get() = context.appContainer.bookDaoForTests

    private val imported = mutableListOf<String>()

    @After
    fun tearDown() {
        runBlocking { imported.forEach { store.delete(it) } }
        imported.clear()
    }

    @Test
    fun titleComesFromTheBookNotFromTheFileName() {
        val book = importAs("book_a.epub", "00. книга_а_обложка.epub")

        assertEquals("Название взято не из книги", "Книга А", book.title)
        assertEquals("Автор потерялся", "Иван Тестов", book.author)
    }

    @Test
    fun annotationFromFb2BecomesDescription() {
        val book = importAs("fb2_plain.fb2", "fb2_plain.fb2")

        TestArtifacts.note("import-description", book.description.orEmpty())
        assertNotNull("Аннотация FB2 не попала в описание", book.description)
        assertTrue(
            "Описание не похоже на аннотацию книги: ${book.description}",
            book.description.orEmpty().contains("проверки разбора"),
        )
    }

    @Test
    fun bookWithoutTitleShowsCleanedFileName() {
        // У PDF из проб названия в метаданных нет вовсе — значит название
        // придёт из имени файла, и оно должно быть вычищено.
        val book = importAs("sample.pdf", "00. Кислородные_деньги_—_обложка_«Росток».pdf")

        assertEquals("Имя файла не вычистилось", "Кислородные деньги", book.title)
    }

    /**
     * Книги, добавленные прошлыми версиями, лежат с именем файла вместо
     * названия. Проход по метаданным должен их починить, а правленные
     * руками — не трогать.
     */
    @Test
    fun oldBooksGetTheirTitlesBack() {
        val book = importAs("book_b.epub", "book_b.epub")
        val edited = importAs("book_v.epub", "book_v.epub")

        runBlocking {
            dao.insert(stale(book, title = "00. book_b"))
            dao.insert(stale(edited, title = "Моё название", edited = true))

            val updated = store.refreshMetadata()
            TestArtifacts.note("metadata-refresh", "обновлено записей: $updated")

            assertEquals(
                "Старой книге не вернулось название из метаданных",
                "Книга Б",
                store.get(book.id)?.title,
            )
            assertEquals(
                "Проход затёр название, которое человек исправил руками",
                "Моё название",
                store.get(edited.id)?.title,
            )
        }
    }

    // --- вспомогательное ---------------------------------------------------

    /** Та же книга, но в базе она выглядит как добавленная старой версией. */
    private fun stale(book: Book, title: String, edited: Boolean = false): BookEntity =
        BookEntity(
            id = book.id,
            title = title,
            author = null,
            format = book.format.name,
            filePath = book.filePath,
            coverPath = book.coverPath,
            addedAt = book.addedAt,
            lastOpenedAt = null,
            progress = 0f,
            locator = null,
            status = book.status.name,
            genre = null,
            shelfId = null,
            isFavorite = false,
            description = null,
            customCoverPath = null,
            editedByUser = edited,
            fileSize = book.fileSize,
            headHash = book.headHash,
            readerPath = book.readerPath,
            openFailure = null,
            openFailureDetail = null,
        )

    /** Кладёт книгу из assets под нужным именем файла и добавляет её. */
    private fun importAs(asset: String, fileName: String): Book {
        val target = File(context.cacheDir, fileName)
        instrumentation.context.assets.open(asset).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val book = runBlocking { store.add(Uri.fromFile(target)) }.getOrThrow()
        imported += book.id
        return book
    }
}
