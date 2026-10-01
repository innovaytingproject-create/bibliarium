package com.bibliarium.app.cardtests

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bibliarium.app.Shell
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.appContainer
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookRating
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Оценка книги и опрос после прочтения.
 *
 * Долистать книгу до конца в проверке дорого и ненадёжно, поэтому до конца
 * её доводит сохранение прогресса — тем же путём, каким это делает читалка
 * на последней странице. Всё остальное идёт через экран: нажал звезду —
 * увидел оценку.
 */
@RunWith(AndroidJUnit4::class)
class RatingTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store get() = context.appContainer.bookStore
    private val ratings get() = context.appContainer.ratingStore

    private val imported = mutableListOf<String>()

    @Before
    fun setUp() {
        runBlocking { store.observeBooks().first().forEach { store.delete(it.id) } }
    }

    @After
    fun tearDown() {
        device.pressHome()
        runBlocking { imported.forEach { store.delete(it) } }
        imported.clear()
    }

    @Test
    fun surveyAppearsWhenTheBookIsFinishedAndOnlyOnce() {
        val book = importBook()

        openReaderAndFinish(book)

        assertTrue(
            "После того как книга дочитана, опрос не появился",
            device.wait(Until.hasObject(By.text("Книга прочитана")), TIMEOUT),
        )
        settledScreenshot("survey")

        device.findObject(By.text("Пропустить")).click()
        device.waitForIdle()

        // Второй раз опрос сам не всплывает, даже если снова долистать.
        openReaderAndFinish(book)
        assertFalse(
            "Опрос появился второй раз",
            device.wait(Until.hasObject(By.text("Книга прочитана")), SHORT_TIMEOUT),
        )
        device.pressBack()
    }

    @Test
    fun answersAreSavedAndAveraged() {
        val book = importBook()
        openReaderAndFinish(book)
        device.wait(Until.findObject(By.text("Книга прочитана")), TIMEOUT)

        // Отвечаем только на два вопроса из четырёх: среднее должно
        // считаться по двум, а не делиться на четыре.
        device.findObject(By.desc("Польза 5")).click()
        device.findObject(By.desc("Ясность 4")).click()
        device.findObject(By.text("Сохранить")).click()

        val saved = runBlocking { ratings.get(book.id) }
        TestArtifacts.note("rating-saved", saved.toString())
        assertNotNull("Оценка не сохранилась", saved)
        assertEquals("Среднее посчитано не по заполненным", 4.5f, saved?.overall ?: 0f, 0.01f)

        openCard(book)
        assertNotNull(
            "В карточке не видно оценки",
            scrollTo("4,5") ?: scrollTo("4.5"),
        )
        settledScreenshot("card-rated")
    }

    @Test
    fun ratingCanBeSetRightInTheCardWithStars() {
        val book = importBook()
        openCard(book)

        device.wait(Until.findObject(By.desc("Оценка 4")), TIMEOUT).click()

        val saved = runBlocking { ratings.get(book.id) }
        assertEquals("Оценка звёздами в карточке не сохранилась", 4f, saved?.overall ?: 0f, 0.01f)

        // И переживает перезапуск.
        device.pressHome()
        openCard(book)
        assertNotNull("Оценка не пережила перезапуск", scrollTo("4,0") ?: scrollTo("4.0"))
    }

    @Test
    fun ratingCanBeChangedFromTheCard() {
        val book = importBook()
        runBlocking {
            ratings.save(
                BookRating(bookId = book.id, useful = 3, clarity = 3, surveyShown = true),
            )
        }

        openCard(book)
        scrollTo("Изменить оценку")!!.click()

        assertTrue(
            "Опрос не открылся из карточки",
            device.wait(Until.hasObject(By.text("Книга прочитана")), TIMEOUT),
        )
        device.findObject(By.desc("Польза 5")).click()
        device.findObject(By.text("Сохранить")).click()

        val saved = runBlocking { ratings.get(book.id) }
        assertEquals("Исправленная оценка не сохранилась", 4f, saved?.overall ?: 0f, 0.01f)
    }

    // --- вспомогательное ---------------------------------------------------

    /**
     * Долистывает книгу до конца так же, как человек.
     *
     * Сначала прошлый вариант дописывал прогресс прямо в хранилище — и опрос
     * не появлялся, потому что показать его решает экран чтения, а не база.
     * Поэтому здесь настоящий путь: оглавление, последняя глава, тапы
     * до конца.
     */
    private fun openReaderAndFinish(book: Book) {
        openCard(book)
        startReading()
        device.wait(Until.hasObject(By.textContains("Книга А")), TIMEOUT)

        // Долистать всю книгу тапами — это сотня нажатий: в главе
        // семнадцать страниц. Поэтому прыгаем оглавлением в последнюю главу
        // и дальше листаем руками, как сделал бы человек, которому осталось
        // дочитать немного.
        tapCenter()
        device.wait(Until.findObject(By.desc("Оглавление")), TIMEOUT).click()
        device.wait(Until.findObject(By.text("Глава 6")), TIMEOUT).click()
        device.waitForIdle()

        repeat(MAX_TAPS) {
            device.click(device.displayWidth * 5 / 6, device.displayHeight / 2)
            device.waitForIdle()
            Thread.sleep(TAP_SETTLE_MS)
        }
    }

    private fun importBook(): Book {
        val target = File(context.cacheDir, "book_a.epub")
        instrumentation.context.assets.open("book_a.epub").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val book = runBlocking { store.add(Uri.fromFile(target)) }.getOrThrow()
        imported += book.id
        return book
    }

    private fun openCard(book: Book) {
        val intent = context.packageManager
            .getLaunchIntentForPackage(Shell.PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        device.wait(Until.findObject(By.desc(book.title)), TIMEOUT).click()
        device.wait(Until.hasObject(By.text(book.title)), TIMEOUT)
    }

    /**
     * Кнопка чтения называется по-разному: у нетронутой книги «Начать
     * читать», у начатой «Продолжить чтение». Ищем обе — иначе проверка
     * ломается ровно на втором заходе.
     */
    private fun startReading() {
        val button = scrollTo("Продолжить чтение") ?: scrollTo("Начать читать")
        assertNotNull("В карточке нет кнопки чтения", button)
        button!!.click()
    }

    private fun tapCenter() {
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(TAP_SETTLE_MS)
    }

    private fun scrollTo(text: String) = device.run {
        repeat(MAX_SCROLLS) {
            findObject(By.textContains(text))?.let { return@run it }
            swipe(displayWidth / 2, displayHeight * 3 / 4, displayWidth / 2, displayHeight / 4, 10)
            waitForIdle()
            Thread.sleep(SCROLL_SETTLE_MS)
        }
        findObject(By.textContains(text))
    }

    private fun settledScreenshot(name: String) {
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
        TestArtifacts.screenshot(name)
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val SHORT_TIMEOUT = 3_000L
        const val SETTLE_MS = 1_000L
        const val SCROLL_SETTLE_MS = 300L
        const val MAX_SCROLLS = 6
        const val MAX_TAPS = 25
        const val TAP_SETTLE_MS = 450L
    }
}
