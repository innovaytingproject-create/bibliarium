package com.bibliarium.app.cardtests

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.bibliarium.app.Shell
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.appContainer
import com.bibliarium.app.domain.Book
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Карточка книги глазами человека: тапнул по корешку — увидел книгу,
 * поправил название — оно осталось, заменил обложку — она видна.
 */
@RunWith(AndroidJUnit4::class)
class BookCardTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store get() = context.appContainer.bookStore

    private val imported = mutableListOf<String>()

    @Before
    fun setUp() {
        // Полка должна быть пустой: проверки ищут один конкретный корешок.
        runBlocking { store.observeBooks().first().forEach { store.delete(it.id) } }
    }

    @After
    fun tearDown() {
        device.pressHome()
        runBlocking { imported.forEach { store.delete(it) } }
        imported.clear()
    }

    @Test
    fun tapOnSpineOpensTheCard() {
        val book = importBook()
        openShelf()

        device.wait(Until.findObject(By.desc(book.title)), TIMEOUT).click()

        assertTrue(
            "Карточка не открылась: названия книги нет на экране",
            device.wait(Until.hasObject(By.text(book.title)), TIMEOUT),
        )
        assertNotNull("На карточке нет автора", device.findObject(By.text("Иван Тестов")))
        assertNotNull(
            "На карточке нет кнопки чтения",
            device.findObject(By.textContains("читать")) ?: device.findObject(
                By.textContains("Продолжить"),
            ),
        )
        settledScreenshot("card-light")
    }

    @Test
    fun readingStartsFromTheCard() {
        val book = importBook()
        openCard(book)

        scrollTo("читать")!!.click()

        assertTrue(
            "Из карточки не открылось чтение",
            device.wait(Until.hasObject(By.textContains("Книга А, глава 1")), TIMEOUT),
        )
        device.pressBack()
    }

    @Test
    fun editedTitleSurvivesRestart() {
        val book = importBook()
        openCard(book)

        scrollTo("Изменить название")!!.click()
        val field = device.wait(Until.findObject(By.text(book.title)), TIMEOUT)
        field.text = NEW_TITLE
        device.findObject(By.text("Сохранить")).click()

        assertTrue(
            "Новое название не показалось в карточке",
            device.wait(Until.hasObject(By.text(NEW_TITLE)), TIMEOUT),
        )

        restartApp()
        assertTrue(
            "Новое название не пережило перезапуск",
            device.wait(Until.hasObject(By.desc(NEW_TITLE)), TIMEOUT),
        )
    }

    @Test
    fun addedDescriptionSurvivesRestart() {
        val book = importBook()
        openCard(book)

        scrollTo("Добавить описание")!!.click()
        device.wait(Until.findObject(By.clazz(EDIT_TEXT)), TIMEOUT).text = DESCRIPTION
        device.findObject(By.text("Сохранить")).click()

        assertNotNull("Описание не показалось в карточке", scrollTo(DESCRIPTION))
        settledScreenshot("card-description")

        restartApp()
        device.wait(Until.findObject(By.desc(book.title)), TIMEOUT).click()
        assertNotNull("Описание не пережило перезапуск", scrollTo(DESCRIPTION))
    }

    /**
     * Своя обложка. Картинку подсовываем вместо системного выбора файла:
     * галереей в прогоне управлять нечем, а проверяем мы не её.
     */
    @Test
    fun customCoverCanBeSetAndRemoved() {
        val book = importBook()
        openCard(book)

        Intents.init()
        try {
            val picture = preparePicture()
            Intents.intending(hasAction(Intent.ACTION_GET_CONTENT)).respondWith(
                Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(picture)),
            )

            openMenu()
            device.wait(Until.findObject(By.text("Заменить обложку")), TIMEOUT).click()

            // Своя обложка стоит — значит в меню появился пункт её убрать.
            openMenu()
            assertNotNull(
                "После замены обложки в меню нет пункта её удалить",
                device.wait(Until.findObject(By.text("Удалить обложку")), TIMEOUT),
            )
            device.findObject(By.text("Удалить обложку")).click()
            settledScreenshot("card-cover-removed")

            openMenu()
            assertFalse(
                "Обложку убрали, а пункт удаления остался",
                device.wait(Until.hasObject(By.text("Удалить обложку")), SHORT_TIMEOUT),
            )
            device.pressBack()
        } finally {
            Intents.release()
        }
    }

    @Test
    fun deletingBookRemovesItFromTheShelf() {
        val book = importBook()
        openCard(book)

        scrollTo("Удалить книгу")!!.click()
        device.wait(Until.findObject(By.text("Удалить")), TIMEOUT).click()

        assertTrue(
            "Книга осталась на полке после удаления",
            device.wait(Until.gone(By.desc(book.title)), TIMEOUT),
        )
        assertFalse(
            "Файл книги остался на телефоне",
            File(book.filePath).exists(),
        )
    }

    @Test
    fun cardLooksRightInDarkTheme() {
        val book = importBook()
        Shell.run("cmd uimode night yes")
        openCard(book)

        assertTrue(
            "В тёмной теме карточка не показала книгу",
            device.wait(Until.hasObject(By.text(book.title)), TIMEOUT),
        )
        settledScreenshot("card-dark")
        Shell.run("cmd uimode night no")
    }

    // --- вспомогательное ---------------------------------------------------

    private fun importBook(): Book {
        val target = File(context.cacheDir, "book_a.epub")
        instrumentation.context.assets.open("book_a.epub").use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        val book = runBlocking { store.add(Uri.fromFile(target)) }.getOrThrow()
        imported += book.id
        return book
    }

    private fun openShelf() {
        val intent = context.packageManager
            .getLaunchIntentForPackage(Shell.PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.text("Bibliarium")), TIMEOUT)
    }

    private fun openCard(book: Book) {
        openShelf()
        device.wait(Until.findObject(By.desc(book.title)), TIMEOUT).click()
        device.wait(Until.hasObject(By.text(book.title)), TIMEOUT)
    }

    /**
     * Карточка длиннее экрана: половина строк лежит ниже сгиба, и человек
     * до них доскролливает. Проверка делает то же самое.
     */
    private fun scrollTo(text: String) = device.run {
        repeat(MAX_SCROLLS) {
            findObject(By.textContains(text))?.let { return@run it }
            swipe(displayWidth / 2, displayHeight * 3 / 4, displayWidth / 2, displayHeight / 4, 10)
            waitForIdle()
            Thread.sleep(SCROLL_SETTLE_MS)
        }
        findObject(By.textContains(text))
    }

    private fun openMenu() {
        device.wait(Until.findObject(By.text("•••")), TIMEOUT).click()
        // Меню раскрывается с задержкой: искать пункт сразу — значит
        // искать то, чего на экране ещё нет.
        device.wait(Until.findObject(By.text("Заменить обложку")), TIMEOUT)
    }

    private fun restartApp() {
        device.pressHome()
        openShelf()
    }

    /** Картинка, которую «выбрал» человек. */
    private fun preparePicture(): Uri {
        val bitmap = Bitmap.createBitmap(PICTURE_SIZE, PICTURE_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.MAGENTA)
        val file = File(context.cacheDir, "cover.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    private fun settledScreenshot(name: String) {
        device.waitForIdle()
        Thread.sleep(SETTLE_MS)
        TestArtifacts.screenshot(name)
    }

    private companion object {
        const val TIMEOUT = 20_000L
        const val SHORT_TIMEOUT = 2_000L
        const val SETTLE_MS = 1_000L
        const val SCROLL_SETTLE_MS = 300L
        const val MAX_SCROLLS = 6
        const val PICTURE_SIZE = 400
        const val PNG_QUALITY = 100
        const val NEW_TITLE = "Моё название"
        const val DESCRIPTION = "Книга про то, как считать деньги."
        const val EDIT_TEXT = "android.widget.EditText"
    }
}
