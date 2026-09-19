package com.bibliarium.app.fullaccess

import android.os.Environment
import androidx.compose.ui.test.assertIsDisplayed
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
 * Разрешение выдаётся скриптом ДО запуска: система убивает процесс приложения,
 * когда этот appop меняется, поэтому выдать его внутри теста нельзя — прогон
 * оборвётся вместе с процессом. Здесь проверяется именно то, что происходит
 * после перезапуска, то есть ровно то состояние, в котором пользователь
 * оказывается, вернувшись из системных настроек.
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
     * Отдельным звеном: приложение может считать разрешение выданным и всё равно
     * не видеть файлы — песочница хранилища выдаётся процессу при запуске.
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

    /** Холодный старт с уже выданным доступом: экран поиска обязан быть готов. */
    @Test
    fun scanScreenIsReadyOnColdStart() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Добавить книгу").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Найти книги на телефоне").performClick()

            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithText("Искать негде").fetchSemanticsNodes().isEmpty()
            }

            TestArtifacts.screenshot("fullaccess-scan-ready")
            compose.onNodeWithText("Поиск идёт по всей памяти телефона.").assertIsDisplayed()
        }
    }

    /** Весь путь до списка найденного через интерфейс. */
    @Test
    fun scanFindsSeededBooksThroughUi() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Добавить книгу").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Найти книги на телефоне").performClick()

            compose.waitUntil(timeoutMillis = 30_000) {
                compose.onAllNodesWithText("Начать поиск").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Начать поиск").performClick()

            compose.waitUntil(timeoutMillis = 120_000) {
                compose.onAllNodesWithText("valid.epub").fetchSemanticsNodes().isNotEmpty()
            }

            TestArtifacts.screenshot("fullaccess-scan-results")
        }
    }
}
