package com.bibliarium.app.readertests

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.appContainer
import com.bibliarium.app.domain.Book
import com.bibliarium.app.reader.ReaderActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Чтение: открытие каждого формата, перелистывание, восстановление позиции
 * после перезапуска и поведение на битом файле.
 *
 * Тестовые книги лежат в androidTest/assets и импортируются штатным путём —
 * тем же, которым пользуется человек.
 */
@RunWith(AndroidJUnit4::class)
class ReaderTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val store get() = context.appContainer.bookStore

    private val imported = mutableListOf<String>()

    @After
    fun tearDown() {
        runBlocking { imported.forEach { store.delete(it) } }
        imported.clear()
    }

    @Test
    fun opensEpub() {
        val book = importAsset("sample.epub")
        openReader(book) {
            val locator = awaitLocator(book.id)
            assertNotNull("EPUB не открылся: навигатор не сообщил позицию", locator)
            assertLoadingGone()
            TestArtifacts.screenshot("reader-epub")
        }
    }

    @Test
    fun opensPdf() {
        val book = importAsset("sample.pdf")
        openReader(book) {
            val locator = awaitLocator(book.id)
            assertNotNull("PDF не открылся: навигатор не сообщил позицию", locator)
            assertLoadingGone()
            TestArtifacts.screenshot("reader-pdf")
        }
    }

    @Test
    fun turnsPagesForwardAndBack() {
        val book = importAsset("sample.epub")
        openReader(book) {
            awaitLocator(book.id)
            val start = progressOf(book.id)

            val forward = tapUntilProgressChanges(book.id, from = start, forward = true)
            TestArtifacts.note("paging", "start=$start forward=$forward")
            assertTrue(
                "Тап по правой трети не пролистал вперёд: было $start, стало $forward",
                forward > start,
            )

            val back = tapUntilProgressChanges(book.id, from = forward, forward = false)
            assertTrue(
                "Тап по левой трети не пролистал назад: было $forward, стало $back",
                back < forward,
            )
        }
    }

    @Test
    fun restoresPositionAfterReopen() {
        val book = importAsset("sample.epub")

        openReader(book) {
            awaitLocator(book.id)
            tapUntilProgressChanges(book.id, from = 0f, forward = true)
        }

        val saved = progressOf(book.id)
        assertTrue("Перед закрытием позиция должна быть не в начале: $saved", saved > 0f)

        // Второй заход — ровно то, что делает человек: закрыл и открыл снова.
        openReader(book) {
            awaitLocator(book.id)
            device.waitForIdle()
            val restored = progressOf(book.id)
            TestArtifacts.note("restore", "saved=$saved restored=$restored")
            assertTrue(
                "Книга открылась не на том же месте: сохранено $saved, открылось $restored",
                kotlin.math.abs(restored - saved) < POSITION_TOLERANCE,
            )
        }
    }

    @Test
    fun brokenFileShowsError() {
        // Импортируем целую книгу, потом портим её файл: так проверяется именно
        // читалка, а не импорт — битый файл импорт бы просто не пропустил.
        val book = importAsset("sample.epub")
        File(book.filePath).writeText("файл испортился уже после добавления")

        openReader(book) {
            compose.waitUntil(timeoutMillis = OPEN_TIMEOUT_MS) {
                compose.onAllNodesWithText(BROKEN_MESSAGE).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(BROKEN_MESSAGE).assertIsDisplayed()
            TestArtifacts.screenshot("reader-broken")
        }
    }

    /** Надпись загрузки не должна оставаться поверх открытой книги. */
    private fun assertLoadingGone() {
        compose.waitUntil(timeoutMillis = OPEN_TIMEOUT_MS) {
            compose.onAllNodesWithText(LOADING_MESSAGE).fetchSemanticsNodes().isEmpty()
        }
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

    private fun openReader(book: Book, body: () -> Unit) {
        ActivityScenario.launch<ReaderActivity>(
            ReaderActivity.intent(context, book.id),
        ).use {
            body()
        }
    }

    private fun awaitLocator(id: String): String? {
        val deadline = System.currentTimeMillis() + OPEN_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val locator = runBlocking { store.get(id) }?.locator
            if (locator != null) return locator
            Thread.sleep(POLL_MS)
        }
        return null
    }

    private fun progressOf(id: String): Float =
        runBlocking { store.get(id) }?.progress ?: 0f

    /**
     * Листает, пока позиция не сдвинется, а не заранее заданное число раз.
     *
     * Первые тапы после открытия могут уйти в пустоту: на медленной машине
     * страница ещё не разложена. Проверяется по-прежнему то же самое — что
     * листание работает, — но тест не зависит от того, за сколько попыток
     * движок успеет проснуться.
     */
    private fun tapUntilProgressChanges(id: String, from: Float, forward: Boolean): Float {
        val deadline = System.currentTimeMillis() + PAGE_TIMEOUT_MS
        var last = from
        while (System.currentTimeMillis() < deadline) {
            if (forward) tapRightThird() else tapLeftThird()
            last = progressOf(id)
            if (kotlin.math.abs(last - from) > PROGRESS_EPSILON) return last
        }
        return last
    }

    private fun tapRightThird() {
        device.click(device.displayWidth * 5 / 6, device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(TAP_SETTLE_MS)
    }

    private fun tapLeftThird() {
        device.click(device.displayWidth / 6, device.displayHeight / 2)
        device.waitForIdle()
        Thread.sleep(TAP_SETTLE_MS)
    }

    private companion object {
        const val OPEN_TIMEOUT_MS = 30_000L
        const val PAGE_TIMEOUT_MS = 40_000L
        const val POLL_MS = 250L
        const val TAP_SETTLE_MS = 600L
        const val PROGRESS_EPSILON = 0.0005f
        const val POSITION_TOLERANCE = 0.02f
        const val BROKEN_MESSAGE = "Не удалось открыть книгу — файл повреждён."
        const val LOADING_MESSAGE = "Открываем книгу…"
    }
}
