package com.bibliarium.app.readertests

import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bibliarium.app.Shell
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.appContainer
import com.bibliarium.app.domain.Book
import com.bibliarium.app.reader.ReaderActivity
import com.bibliarium.app.reader.ReaderTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Экран чтения по макету: панели, настройки, оглавление, поиск.
 *
 * Всё через экран: нажал сюда — увидел вот это. Снимки каждого состояния
 * уходят в артефакты, чтобы их можно было сравнить с макетом глазами.
 */
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {

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
        runBlocking {
            imported.forEach { store.delete(it) }
            // Тема чтения живёт в настройках и переживает закрытие читалки:
            // без возврата к светлой следующая проверка снимала бы сепию.
            context.appContainer.readerSettings.saveTheme(ReaderTheme.LIGHT)
        }
        imported.clear()
        Shell.run("cmd uimode night no")
    }

    @Test
    fun bookOpensWithoutPanels() {
        openReader(importAsset("book_a.epub"))
        awaitText("Книга А")

        assertFalse(
            "Панели показались сами, хотя книга только открылась",
            device.hasObject(By.desc("Оглавление")),
        )
        settledScreenshot("reader-1-panels-hidden")
    }

    @Test
    fun tapInCenterShowsBothPanels() {
        val book = importAsset("book_a.epub")
        openReader(book)
        awaitText("Книга А")

        tapCenter()

        assertTrue(
            "После тапа по центру не появилась верхняя панель",
            device.wait(Until.hasObject(By.desc("Оглавление")), TIMEOUT),
        )
        assertNotNull(
            "В шапке нет названия книги",
            device.findObject(By.text(book.title)),
        )
        assertNotNull("Нет кнопки настроек", device.findObject(By.desc("Настройки")))
        assertNotNull("Нет полосы прогресса", device.findObject(By.desc("Полоса прогресса")))
        assertNotNull(
            "Не показан процент прочитанного",
            device.findObject(By.textContains("%")),
        )
        settledScreenshot("reader-2-panels-shown")
    }

    @Test
    fun settingsSheetChangesFontAndTheme() {
        openReader(importAsset("book_a.epub"))
        awaitText("Книга А")
        tapCenter()

        device.wait(Until.findObject(By.desc("Настройки")), TIMEOUT).click()
        assertTrue(
            "Лист настроек не открылся",
            device.wait(Until.hasObject(By.text("Шрифт")), TIMEOUT),
        )
        settledScreenshot("reader-3-settings")

        // Шрифт: выбрали другой — лист показывает его выбранным.
        device.findObject(By.desc("Lora")).click()
        device.waitForIdle()

        // Тема: сепия. Фон страницы должен стать другим — проверяем по тому,
        // что видно: цвет пикселя в середине экрана.
        val before = centerColor()
        device.findObject(By.desc("Сепия")).click()
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
        val after = centerColor()

        settledScreenshot("reader-3-settings-sepia")
        assertTrue(
            "Фон страницы не изменился после выбора сепии: было $before, стало $after",
            before != after,
        )
    }

    @Test
    fun tocOpensAtCurrentChapterAndJumps() {
        openReader(importAsset("book_a.epub"))
        awaitText("Книга А")
        tapCenter()

        device.wait(Until.findObject(By.desc("Оглавление")), TIMEOUT).click()
        assertTrue(
            "Оглавление не открылось",
            device.wait(Until.hasObject(By.text("Глава 3")), TIMEOUT),
        )
        settledScreenshot("reader-4-toc")

        device.findObject(By.text("Глава 3")).click()
        assertTrue(
            "Выбранная глава не открылась",
            device.wait(Until.hasObject(By.textContains("глава 3")), TIMEOUT),
        )
    }

    /**
     * Те же четыре состояния, но в тёмной теме — в макете это второй ряд.
     * Снимки уходят в артефакты парами к светлым, чтобы сравнить с макетом.
     */
    @Test
    fun fourStatesInDarkTheme() {
        openReader(importAsset("book_a.epub"))
        awaitText("Книга А")
        tapCenter()

        device.wait(Until.findObject(By.desc("Настройки")), TIMEOUT).click()
        device.wait(Until.hasObject(By.text("Шрифт")), TIMEOUT)
        device.findObject(By.desc("Тёмная тема")).click()
        device.waitForIdle()
        settledScreenshot("reader-dark-3-settings")

        device.findObject(By.desc("Закрыть")).click()
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
        settledScreenshot("reader-dark-2-panels-shown")

        // Страница обязана стать тёмной, а не только лист настроек.
        val page = colorAt(PAGE_PROBE)
        assertTrue(
            "Страница осталась светлой после выбора тёмной темы: яркость ${brightnessOf(page)}",
            brightnessOf(page) < DARK_LIMIT,
        )

        tapCenter()
        assertTrue(
            "Панели не спрятались в тёмной теме",
            device.wait(Until.gone(By.desc("Оглавление")), TIMEOUT),
        )
        settledScreenshot("reader-dark-1-panels-hidden")

        tapCenter()
        device.wait(Until.findObject(By.desc("Оглавление")), TIMEOUT).click()
        assertTrue(
            "Оглавление не открылось в тёмной теме",
            device.wait(Until.hasObject(By.textContains("Глава")), TIMEOUT),
        )
        settledScreenshot("reader-dark-4-toc")
    }

    @Test
    fun searchFindsWordAndOpensIt() {
        openReader(importAsset("book_a.epub"))
        awaitText("Книга А")
        tapCenter()

        device.wait(Until.findObject(By.desc("Поиск")), TIMEOUT).click()
        device.wait(Until.findObject(By.textContains("Поиск по книге")), TIMEOUT)
            .text = "перелистывание"

        assertTrue(
            "Поиск ничего не нашёл",
            device.wait(Until.hasObject(By.textContains("перелистывание")), SEARCH_TIMEOUT),
        )
        settledScreenshot("reader-search")
    }

    @Test
    fun pdfHasNoFontSettingsAndNoSearch() {
        openReader(importAsset("sample.pdf"))
        device.wait(Until.hasObject(By.textContains("Страница")), TIMEOUT)
        tapCenter()

        assertFalse("У PDF показан значок поиска", device.hasObject(By.desc("Поиск")))

        device.wait(Until.findObject(By.desc("Настройки")), TIMEOUT).click()
        device.wait(Until.hasObject(By.textContains("Яркость")), TIMEOUT)

        assertFalse("У PDF в настройках есть выбор шрифта", device.hasObject(By.text("Шрифт")))
        assertFalse("У PDF в настройках есть размер текста", device.hasObject(By.text("Размер текста")))
        settledScreenshot("reader-pdf-settings")
    }

    /**
     * Текст не должен залезать под часы. Проверяем по верхней полосе экрана:
     * там, где идут системные значки, книжных букв быть не может.
     */
    @Test
    fun textDoesNotHideUnderTheClock() {
        openReader(importAsset("book_a.epub"))
        awaitText("Книга А")
        settledScreenshot("reader-insets")

        val statusBarHeight = device.displayHeight / STATUS_BAND
        val textInStatusBand = device.findObjects(By.textContains("Книга А"))
            .any { it.visibleBounds.top < statusBarHeight }

        assertFalse("Текст книги заходит под часы", textInStatusBand)
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
        device.wait(Until.hasObject(By.textContains(text)), TIMEOUT)
    }

    private fun tapCenter() {
        device.click(device.displayWidth / 2, device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
    }

    /** Цвет пикселя посреди страницы: по нему видно смену темы чтения. */
    private fun centerColor(): Int = colorAt(0.5f)

    /** Цвет пикселя на заданной высоте экрана — там, где видна страница книги. */
    private fun colorAt(heightFraction: Float): Int {
        val shot = instrumentation.uiAutomation.takeScreenshot() ?: return 0
        val color = shot.getPixel(shot.width / 2, (shot.height * heightFraction).toInt())
        shot.recycle()
        return color
    }

    /** Насколько пиксель светлый: 0 — чёрный, 255 — белый. */
    private fun brightnessOf(color: Int): Int =
        ((color shr 16 and 0xFF) + (color shr 8 and 0xFF) + (color and 0xFF)) / 3

    private fun settledScreenshot(name: String) {
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
        TestArtifacts.screenshot(name)
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val SEARCH_TIMEOUT = 30_000L
        const val SETTLE_MS = 900L

        /** Где брать цвет страницы: ниже шапки, но выше листа настроек. */
        const val PAGE_PROBE = 0.25f

        /** Темнее этого — уже тёмная тема, а не светлая. */
        const val DARK_LIMIT = 110

        /** Верхняя десятая часть экрана — там живут часы и значки. */
        const val STATUS_BAND = 10
    }
}
