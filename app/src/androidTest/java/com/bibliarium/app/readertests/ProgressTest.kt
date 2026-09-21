package com.bibliarium.app.readertests

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.appContainer
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.ReadingStatus
import com.bibliarium.app.reader.ReaderActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Позиция чтения у каждой книги своя.
 *
 * Человек читает несколько книг сразу: открыл одну, отложил, взял другую.
 * Если при этом у отложенной слетает место — читать так нельзя вовсе.
 *
 * Проверка идёт тем же путём, каким это заметно человеку: открыть,
 * полистать, выйти, вернуться — и увидеть то же место.
 */
@RunWith(AndroidJUnit4::class)
class ProgressTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store get() = context.appContainer.bookStore

    private val imported = mutableListOf<String>()
    private var scenario: ActivityScenario<ReaderActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
        runBlocking { imported.forEach { store.delete(it) } }
        imported.clear()
    }

    @Test
    fun everyBookKeepsItsOwnPlace() {
        val a = importAsset("book_a.epub")
        val b = importAsset("book_b.epub")
        val v = importAsset("book_v.epub")

        val afterA = readAndLeave(a, "Книга А", taps = 4)
        val afterB = readAndLeave(b, "Книга Б", taps = 9)
        val afterV = readAndLeave(v, "Книга В", taps = 2)

        TestArtifacts.note(
            "progress-after-reading",
            "А: $afterA | Б: $afterB | В: $afterV",
        )

        // Книги должны отличаться друг от друга, иначе проверка ничего
        // не проверяет: три одинаковых нуля тоже «совпали бы».
        assertTrue("Листание не сдвинуло прогресс книги А: $afterA", afterA.percent > 0)
        assertTrue("Листание не сдвинуло прогресс книги Б: $afterB", afterB.percent > 0)
        assertTrue(
            "Прогресс А и Б совпал ($afterA и $afterB) — листали слишком мало",
            afterA.percent != afterB.percent,
        )

        // Убить приложение изнутри нельзя: тест живёт в его же процессе и
        // умрёт вместе с ним. Но и нечему теряться — ничего между открытиями
        // в памяти не держится: у каждого экрана чтения своя вьюмодель,
        // а место хранится в базе. Поэтому закрытие книги и повторное
        // открытие проверяют ровно то же самое.

        assertEquals("Книга А открылась не там, где её оставили", afterA, reopen(a, "Книга А"))
        assertEquals("Книга Б открылась не там, где её оставили", afterB, reopen(b, "Книга Б"))
        assertEquals("Книга В открылась не там, где её оставили", afterV, reopen(v, "Книга В"))
    }

    @Test
    fun everyStartedBookCountsAsBeingRead() {
        val a = importAsset("book_a.epub")
        val b = importAsset("book_b.epub")
        val v = importAsset("book_v.epub")

        readAndLeave(a, "Книга А", taps = 4)
        readAndLeave(b, "Книга Б", taps = 9)
        readAndLeave(v, "Книга В", taps = 2)

        val statuses = runBlocking { listOf(a, b, v).map { store.get(it.id) } }
        TestArtifacts.note(
            "progress-statuses",
            statuses.joinToString(" | ") { "${it?.title}: ${it?.status} ${it?.progress}" },
        )

        statuses.forEach { book ->
            assertNotNull("Книга пропала из библиотеки", book)
            assertTrue(
                "У начатой книги «${book?.title}» прогресс ${book?.progress}",
                (book?.progress ?: 0f) > 0f,
            )
            assertEquals(
                "Начатая книга «${book?.title}» не числится читаемой",
                ReadingStatus.READING,
                book?.status,
            )
        }
    }

    /**
     * «Читаю» — это книги, в которых что-то прочитано, а не те, которые
     * открывали. Открыл и сразу закрыл — книга осталась нетронутой.
     */
    @Test
    fun bookOpenedButNotReadIsNotCountedAsStarted() {
        val a = importAsset("book_a.epub")

        openReader(a)
        awaitText("Книга А")
        scenario?.close()
        scenario = null

        val book = runBlocking { store.get(a.id) }
        assertEquals(
            "Открытая, но не читанная книга числится читаемой",
            ReadingStatus.NOT_STARTED,
            book?.status,
        )
        assertEquals("У нетронутой книги появился прогресс", 0f, book?.progress ?: -1f, 0.001f)
    }

    // --- вспомогательное ---------------------------------------------------

    /** То, что видно человеку в нижней панели. */
    private data class Place(val label: String, val percent: Int)

    private fun readAndLeave(book: Book, title: String, taps: Int): Place {
        openReader(book)
        awaitText(title)
        repeat(taps) { tapRightThird() }
        val place = place()
        scenario?.close()
        scenario = null
        return place
    }

    private fun reopen(book: Book, title: String): Place {
        openReader(book)
        awaitText(title)
        val place = place()
        TestArtifacts.screenshot("progress-reopened-${book.id.take(6)}")
        scenario?.close()
        scenario = null
        return place
    }

    private fun place(): Place {
        val label = device.wait(Until.findObject(By.textContains("%")), OPEN_TIMEOUT)?.text
            ?: return Place("нижней панели нет", -1)
        val percent = PERCENT.find(label)?.groupValues?.get(1)?.toIntOrNull() ?: -1
        return Place(label, percent)
    }

    private fun importAsset(name: String): Book {
        val target = File(context.cacheDir, name)
        instrumentation.context.assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val book = runBlocking { store.add(Uri.fromFile(target)) }.getOrThrow()
        imported += book.id
        return book
    }

    private fun openReader(book: Book) {
        scenario = ActivityScenario.launch(ReaderActivity.intent(context, book.id))
    }

    private fun awaitText(text: String) {
        device.wait(Until.hasObject(By.textContains(text)), OPEN_TIMEOUT)
    }

    private fun tapRightThird() {
        device.click(device.displayWidth * 5 / 6, device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(TAP_SETTLE_MS)
    }

    private companion object {
        const val OPEN_TIMEOUT = 30_000L
        const val TAP_SETTLE_MS = 700L
        val PERCENT = Regex("(\\d+)\\s*%")
    }
}
