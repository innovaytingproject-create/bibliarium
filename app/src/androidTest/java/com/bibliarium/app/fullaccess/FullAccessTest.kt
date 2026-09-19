package com.bibliarium.app.fullaccess

import android.os.Environment
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibliarium.app.BookFixtures
import com.bibliarium.app.Shell
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.data.access.AllFilesAccess
import com.bibliarium.app.ui.MainActivity
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Прогон с уже выданным MANAGE_EXTERNAL_STORAGE.
 *
 * Разрешение выдаётся скриптом ДО запуска: менять appop во время
 * инструментального прогона нельзя, рвётся соединение UiAutomation. Здесь
 * проверяется состояние, в котором приложение стартует уже с доступом —
 * то есть то, в котором пользователь оказывается, вернувшись из системных
 * настроек.
 */
@RunWith(AndroidJUnit4::class)
class FullAccessTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun checkPreconditions() {
        TestArtifacts.note("fullaccess-appops", Shell.allFilesAccessState())
        assertTrue(
            "Разрешение должно быть выдано до запуска прогона: " +
                "appops set com.bibliarium.app MANAGE_EXTERNAL_STORAGE allow",
            Environment.isExternalStorageManager(),
        )
        BookFixtures.seed()
    }

    @After
    fun tearDown() {
        BookFixtures.cleanUp()
    }

    @Test
    fun accessIsAvailable() {
        assertTrue(
            "Разрешение выдано, но AllFilesAccess считает режим недоступным",
            AllFilesAccess(context).isAvailable(),
        )
    }

    /**
     * Отдельным звеном: приложение может считать разрешение выданным и всё
     * равно не видеть файлы.
     */
    @Test
    fun sharedStorageIsReadable() {
        val directory = File(BookFixtures.DIRECTORY)
        val names = directory.listFiles()?.map { it.name }?.sorted().orEmpty()
        TestArtifacts.note(
            "shared-storage-listing",
            buildString {
                appendLine("exists=${directory.exists()}")
                appendLine("canRead=${directory.canRead()}")
                appendLine("names=$names")
            },
        )
        assertTrue(
            "Файлы лежат в ${BookFixtures.DIRECTORY}, но приложение их не видит: $names",
            names.containsAll(BookFixtures.fileNames),
        )
    }

    @Test
    fun findsSeededBooks() = runBlocking {
        val found = AllFilesAccess(context).findBooks { _, _ -> }
        val names = found.map { it.fileName }.toSet()
        TestArtifacts.note("found-books", names.sorted().toString())

        assertTrue("EPUB не найден, найдено: $names", "valid.epub" in names)
        assertTrue("FB2 не найден, найдено: $names", "valid.fb2" in names)
        assertTrue("FB2 в архиве не найден, найдено: $names", "archived.fb2.zip" in names)
        assertTrue("Битый файл тоже должен попасть в список", "broken.epub" in names)
    }

    /** Холодный старт с уже выданным доступом: экран поиска не должен быть тупиком. */
    @Test
    fun scanScreenIsNotBlockedOnColdStart() {
        openScanScreen()

        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithText("Искать негде").fetchSemanticsNodes().isEmpty()
        }

        TestArtifacts.screenshot("fullaccess-scan-opened")
        compose.onNodeWithText("Искать негде").assertDoesNotExist()
    }

    /**
     * Весь путь до списка найденного через интерфейс. Кнопку «Начать поиск»
     * нажимаем, только если она есть: при доступной памяти поиск стартует сам.
     */
    @Test
    fun scanFindsSeededBooksThroughUi() {
        openScanScreen()

        compose.waitUntil(timeoutMillis = 60_000) {
            compose.onAllNodesWithText("Начать поиск").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Отмена").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("valid.epub").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Начать поиск").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Начать поиск").performClick()
        }

        compose.waitUntil(timeoutMillis = 180_000) {
            compose.onAllNodesWithText("valid.epub").fetchSemanticsNodes().isNotEmpty()
        }

        TestArtifacts.screenshot("fullaccess-scan-results")
    }

    private fun openScanScreen() {
        ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithText("Добавить книгу").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Найти книги на телефоне").performClick()
    }
}
