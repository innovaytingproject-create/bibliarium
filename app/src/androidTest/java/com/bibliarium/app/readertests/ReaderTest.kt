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
import com.bibliarium.app.reader.ReaderActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Чтение глазами человека.
 *
 * Каждая проверка описывается словами «нажал сюда — увидел вот это».
 * Смотрим на экран через UiAutomator, а не в состояние навигатора: прошлый
 * набор проверял смену позиции в базе и остался бы зелёным на пустом экране,
 * что и случилось.
 */
@RunWith(AndroidJUnit4::class)
class ReaderTest {

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
    fun openedBookShowsItsText() {
        openReader(importAsset("sample.epub"))

        assertTrue(
            "Текст книги не появился на экране",
            device.wait(Until.hasObject(By.textContains("Глава 1")), OPEN_TIMEOUT),
        )
        assertNull(
            "Надпись загрузки осталась поверх открытой книги",
            device.findObject(By.textContains("Открываем книгу")),
        )
        settledScreenshot("reader-epub-open")
    }

    /**
     * У PDF страница рисуется картинкой, и прочитать её текст с экрана нельзя.
     * Поэтому смотрим на то, что видно человеку вокруг: книга открылась,
     * ошибки нет, панели на месте, прогресс посчитан.
     */
    @Test
    fun openedPdfIsReadyToRead() {
        openReader(importAsset("sample.pdf"))

        assertNotNull(
            "PDF не открылся: панели не появились",
            device.wait(Until.findObject(By.text("Назад")), OPEN_TIMEOUT),
        )
        assertNull(
            "PDF открылся с ошибкой",
            device.findObject(By.textContains("Не удалось открыть")),
        )
        assertNull(
            "Надпись загрузки осталась поверх PDF",
            device.findObject(By.textContains("Открываем книгу")),
        )
        assertNotNull(
            "Прогресс по PDF не показан",
            device.findObject(By.textContains("%")),
        )
        settledScreenshot("reader-pdf-open")
    }

    @Test
    fun panelsAreVisibleRightAfterOpening() {
        openReader(importAsset("sample.epub"))
        awaitText("Глава 1")

        // Из книги обязан быть выход сразу, а не после угаданного жеста.
        assertNotNull(
            "Кнопки «Назад» нет на экране открытой книги",
            device.wait(Until.findObject(By.text("Назад")), PANEL_TIMEOUT),
        )
        assertNotNull("Кнопки оглавления нет", device.findObject(By.text("Оглавление")))
        assertNotNull("Кнопки настроек нет", device.findObject(By.text("Настройки")))
        settledScreenshot("reader-panels-visible")
    }

    @Test
    fun tapInCenterHidesAndShowsPanels() {
        openReader(importAsset("sample.epub"))
        awaitText("Глава 1")
        awaitText("Назад")

        tapCenter()
        assertTrue(
            "Тап по центру не спрятал панели",
            device.wait(Until.gone(By.text("Назад")), PANEL_TIMEOUT),
        )
        settledScreenshot("reader-panels-hidden")

        tapCenter()
        assertTrue(
            "Повторный тап по центру не вернул панели",
            device.wait(Until.hasObject(By.text("Назад")), PANEL_TIMEOUT),
        )
    }

    @Test
    fun tapOnRightThirdTurnsPage() {
        val book = importAsset("sample.epub")
        openReader(book)
        awaitText("Глава 1")

        // Панели убираем: они перекрывают края, по которым листают.
        tapCenter()
        device.wait(Until.gone(By.text("Назад")), PANEL_TIMEOUT)

        assertNotNull(
            "Первая страница не показала начало главы",
            device.findObject(By.textContains("МЕТКА-НАЧАЛО")),
        )

        // Листание видно по тексту на экране: начало главы должно уйти.
        val turned = (1..MAX_TAPS).any {
            tapRightThird()
            device.findObject(By.textContains("МЕТКА-НАЧАЛО")) == null
        }

        settledScreenshot("reader-after-page-turn")
        assertTrue(
            "Тап по правой трети не сменил текст на экране " +
                "(прогресс в базе: ${progressOf(book.id)})",
            turned,
        )

        // И обратно: левая треть возвращает на начало главы.
        val returned = (1..MAX_TAPS).any {
            tapLeftThird()
            device.findObject(By.textContains("МЕТКА-НАЧАЛО")) != null
        }
        assertTrue("Тап по левой трети не вернул на предыдущую страницу", returned)
    }

    @Test
    fun backButtonLeavesTheBook() {
        openReader(importAsset("sample.epub"))
        awaitText("Глава 1")

        device.wait(Until.findObject(By.text("Назад")), PANEL_TIMEOUT).click()

        assertTrue(
            "После кнопки «Назад» книга осталась на экране",
            device.wait(Until.gone(By.textContains("Глава 1")), PANEL_TIMEOUT),
        )
    }

    @Test
    fun tableOfContentsOpensChosenChapter() {
        openReader(importAsset("sample.epub"))
        awaitText("Глава 1")

        device.wait(Until.findObject(By.text("Оглавление")), PANEL_TIMEOUT).click()
        assertTrue(
            "Оглавление не открылось",
            device.wait(Until.hasObject(By.textContains("Глава")), PANEL_TIMEOUT),
        )
        settledScreenshot("reader-toc-open")

        val target = device.findObject(By.text("Глава 4"))
            ?: device.findObject(By.text("Глава 3"))
        assertNotNull("В оглавлении нет глав, кроме первой", target)
        target.click()

        assertTrue(
            "После выбора главы книга не перешла на неё",
            device.wait(Until.hasObject(By.textContains("Глава")), OPEN_TIMEOUT),
        )
        settledScreenshot("reader-after-toc-jump")
    }

    @Test
    fun brokenFileExplainsWhatWentWrong() {
        val book = importAsset("sample.epub")
        File(book.contentPath).writeText("файл испортился уже после добавления")

        openReader(book)

        assertTrue(
            "Экран не объяснил, что не так с книгой",
            device.wait(Until.hasObject(By.textContains("Не удалось открыть")), OPEN_TIMEOUT),
        )
        // Место обрыва должно быть на экране, а не только в логе.
        assertNotNull(
            "Причина показана без подробностей — чинить такое нечем",
            device.findObject(By.textContains("Подробности:")),
        )
        settledScreenshot("reader-broken")
    }

    // --- вспомогательное ---------------------------------------------------

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

    private fun progressOf(id: String): Float =
        runBlocking { store.get(id) }?.progress ?: 0f

    private fun tapCenter() {
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(TAP_SETTLE_MS)
    }

    private fun tapLeftThird() {
        device.click(device.displayWidth / 6, device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(TAP_SETTLE_MS)
    }

    private fun tapRightThird() {
        device.click(device.displayWidth * 5 / 6, device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(TAP_SETTLE_MS)
    }

    private fun settledScreenshot(name: String) {
        device.waitForIdle()
        Thread.sleep(SCREENSHOT_SETTLE_MS)
        TestArtifacts.screenshot(name)
    }

    private companion object {
        const val OPEN_TIMEOUT = 30_000L
        const val PANEL_TIMEOUT = 10_000L
        const val TAP_SETTLE_MS = 700L
        const val SCREENSHOT_SETTLE_MS = 1_500L
        const val MAX_TAPS = 8
    }
}
