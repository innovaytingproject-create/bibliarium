package com.bibliarium.app.shelftests

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
import com.bibliarium.app.data.db.BookEntity
import com.bibliarium.app.domain.Book
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Полка глазами человека: корешки видно, тап открывает книгу, переключатель
 * и ярусы работают.
 *
 * Корешок рисуется на Canvas, и текста на нём для системы нет — поэтому на
 * каждом корешке висит описание с названием книги. По нему его и находим,
 * как нашёл бы человек с озвучкой.
 */
@RunWith(AndroidJUnit4::class)
class ShelfTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store get() = context.appContainer.bookStore
    private val dao get() = context.appContainer.bookDaoForTests

    private val realBooks = mutableListOf<Book>()
    private val fakeIds = mutableListOf<String>()

    @Before
    fun setUp() {
        clearLibrary()
    }

    @After
    fun tearDown() {
        device.pressHome()
        clearLibrary()
        Shell.run("cmd uimode night no")
    }

    @Test
    fun shelfShowsSpinesAndTapOpensBook() {
        val book = importRealBook()
        openShelf()

        val spine = device.wait(Until.findObject(By.desc(book.title)), UI_TIMEOUT)
        assertNotNull("Корешка книги «${book.title}» на полке нет", spine)
        settledScreenshot("shelf-spines-light")

        spine.click()
        assertTrue(
            "Тап по корешку не открыл книгу",
            device.wait(Until.hasObject(By.textContains("Глава 1")), OPEN_TIMEOUT),
        )
        device.pressBack()
    }

    @Test
    fun modeSwitchShowsCoversAndBack() {
        val book = importRealBook()
        seedFakeBooks(count = 5, fromFile = book.filePath, addedBefore = book.addedAt)
        openShelf()
        device.wait(Until.findObject(By.desc(book.title)), UI_TIMEOUT)

        device.findObject(By.text("Сетка")).click()
        assertTrue(
            "После переключения в сетку названия книг не показались",
            device.wait(Until.hasObject(By.text(book.title)), UI_TIMEOUT),
        )
        settledScreenshot("shelf-grid")

        device.findObject(By.text("Корешки")).click()
        assertNotNull(
            "Не вернулись к корешкам",
            device.wait(Until.findObject(By.desc(book.title)), UI_TIMEOUT),
        )
    }

    @Test
    fun tierScrollsSideways() {
        val book = importRealBook()
        seedFakeBooks(count = 40, fromFile = book.filePath, addedBefore = book.addedAt)
        openShelf()
        device.wait(Until.findObject(By.desc(book.title)), UI_TIMEOUT)
        assertNotNull(
            "Первый корешок яруса не виден",
            device.findObject(By.desc(book.title)),
        )

        // Ярус листается вбок: после свайпа влево на экране другие корешки.
        var moved = false
        repeat(MAX_SWIPES) {
            if (!moved) {
                device.swipe(
                    device.displayWidth * 4 / 5,
                    device.displayHeight / 2,
                    device.displayWidth / 5,
                    device.displayHeight / 2,
                    SWIPE_STEPS,
                )
                device.waitForIdle()
                moved = device.findObject(By.desc(book.title)) == null
            }
        }

        settledScreenshot("shelf-tier-scrolled")
        assertTrue("Ярус не пролистался вбок", moved)
    }

    @Test
    fun darkThemeShelfLooksRight() {
        val book = importRealBook()
        seedFakeBooks(count = 10, fromFile = book.filePath, addedBefore = book.addedAt)

        Shell.run("cmd uimode night yes")
        openShelf()

        assertNotNull(
            "В тёмной теме полка не показала корешки",
            device.wait(Until.findObject(By.desc(book.title)), UI_TIMEOUT),
        )
        settledScreenshot("shelf-spines-dark")
    }

    /**
     * Пятьсот книг и прокрутка — требование раздела 7 ТЗ.
     *
     * Пропуски кадров считает сама система: `dumpsys gfxinfo` сбрасывается
     * перед прокруткой и читается после. Это счётчик снаружи приложения,
     * а не наша самооценка.
     */
    @Test
    fun fiveHundredBooksScrollWithoutJank() {
        val book = importRealBook()
        seedFakeBooks(count = 500, fromFile = book.filePath, addedBefore = book.addedAt)
        openShelf()
        device.wait(Until.findObject(By.desc(book.title)), OPEN_TIMEOUT)

        // По статусу все пятьсот книг попадают в один ярус, а ярус показывает
        // двадцать корешков — прокручивать было бы нечего. По автору ярусов
        // становится двадцать пять, и вот это уже настоящая полка.
        device.findObject(By.text("По автору")).click()
        device.waitForIdle()
        settledScreenshot("shelf-500-books")

        measureScroll("корешки")

        device.findObject(By.text("Сетка")).click()
        device.waitForIdle()
        settledScreenshot("shelf-500-grid")

        measureScroll("сетка")
    }

    /** Прокручивает то, что сейчас на экране, и считает пропущенные кадры. */
    private fun measureScroll(what: String) {
        Shell.run("dumpsys gfxinfo ${Shell.PACKAGE} reset")

        repeat(SCROLL_PASSES) {
            device.swipe(
                device.displayWidth / 2,
                device.displayHeight * 3 / 4,
                device.displayWidth / 2,
                device.displayHeight / 4,
                SWIPE_STEPS,
            )
            device.waitForIdle()
        }

        val report = Shell.run("dumpsys gfxinfo ${Shell.PACKAGE}")
        TestArtifacts.note("shelf-gfxinfo-$what", report.take(GFX_REPORT_LIMIT))

        val total = report.number("Total frames rendered: (\d+)")
        val janky = report.number("Janky frames: (\d+)")
        assertTrue(
            "Кадры при прокрутке ($what) не рисовались вовсе — двигать было нечего?",
            total > MIN_FRAMES,
        )

        val percent = janky * PERCENT / total
        TestArtifacts.note("shelf-jank-$what", "кадров $total, пропущено $janky, это $percent %")
        assertTrue(
            "Прокрутка ($what) рваная: пропущено $janky кадров из $total ($percent %)",
            percent <= MAX_JANK_PERCENT,
        )
    }

    // --- вспомогательное ---------------------------------------------------

    private fun openShelf() {
        val intent = context.packageManager
            .getLaunchIntentForPackage(Shell.PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.text("Bibliarium")), OPEN_TIMEOUT)
    }

    private fun importRealBook(): Book {
        val target = File(context.cacheDir, "sample.epub")
        instrumentation.context.assets.open("sample.epub").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val book = runBlocking { store.add(Uri.fromFile(target)) }.getOrThrow()
        realBooks += book
        return book
    }

    /**
     * Фиктивные книги: строки в базе, все смотрят на один настоящий файл.
     * Так пятьсот корешков появляются мгновенно, а любой из них открывается
     * по-настоящему.
     */
    private fun seedFakeBooks(count: Int, fromFile: String, addedBefore: Long) {
        // Фиктивные книги встают позади настоящей: полка сортирует по времени
        // добавления, и иначе корешок настоящей книги уезжал бы за край экрана,
        // а проверка искала бы то, чего не видно.
        val now = addedBefore - 1_000
        val entities = (0 until count).map { index ->
            val id = "fake-$index"
            fakeIds += id
            BookEntity(
                id = id,
                title = fakeTitle(index),
                author = "Автор ${index % AUTHORS}",
                format = "EPUB",
                filePath = fromFile,
                coverPath = null,
                addedAt = now - index,
                lastOpenedAt = null,
                progress = 0f,
                locator = null,
                status = "NOT_STARTED",
                genre = null,
                shelfId = null,
                isFavorite = false,
                fileSize = 0,
                headHash = null,
                readerPath = null,
                openFailure = null,
                openFailureDetail = null,
            )
        }
        runBlocking { dao.insertAll(entities) }
    }

    private fun fakeTitle(index: Int): String = "Книга номер $index"

    private fun clearLibrary() {
        runBlocking {
            fakeIds.forEach { dao.deleteById(it) }
            realBooks.forEach { store.delete(it.id) }
        }
        fakeIds.clear()
        realBooks.clear()
    }

    private fun String.number(pattern: String): Long =
        Regex(pattern).find(this)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    private fun settledScreenshot(name: String) {
        device.waitForIdle()
        Thread.sleep(SCREENSHOT_SETTLE_MS)
        TestArtifacts.screenshot(name)
    }

    private companion object {
        const val UI_TIMEOUT = 15_000L
        const val OPEN_TIMEOUT = 30_000L
        const val SCREENSHOT_SETTLE_MS = 1_200L
        const val SWIPE_STEPS = 12
        const val MAX_SWIPES = 5
        const val SCROLL_PASSES = 6
        const val AUTHORS = 25
        const val PERCENT = 100
        const val MIN_FRAMES = 20
        const val GFX_REPORT_LIMIT = 4_000

        /**
         * Порог с запасом: эмулятор в CI рисует программно и пропускает кадры
         * сам по себе. Проверка ловит обвал, а не тонкую просадку — точное
         * число всё равно уходит в артефакты, и смотреть надо на него.
         */
        const val MAX_JANK_PERCENT = 50
    }
}
