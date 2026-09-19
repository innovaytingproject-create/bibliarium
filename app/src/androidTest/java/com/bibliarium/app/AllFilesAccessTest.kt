package com.bibliarium.app

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibliarium.app.data.access.AllFilesAccess
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверяет режим полного доступа по звеньям, от разрешения до найденных файлов.
 * Если сломано — видно, какое именно звено, а не «не работает».
 */
@RunWith(AndroidJUnit4::class)
class AllFilesAccessTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        BookFixtures.cleanUp()
        Shell.revokeAllFilesAccess()
    }

    @Test
    fun withoutPermission_accessIsNotAvailable() {
        Shell.revokeAllFilesAccess()
        TestArtifacts.note("appops-revoked", Shell.allFilesAccessState())

        assertFalse(
            "Без разрешения полный доступ не должен считаться доступным",
            AllFilesAccess(context).isAvailable(),
        )
    }

    @Test
    fun afterGrant_systemReportsManager() {
        Shell.grantAllFilesAccess()
        TestArtifacts.note("appops-granted", Shell.allFilesAccessState())

        assertTrue(
            "appops выдал MANAGE_EXTERNAL_STORAGE, но Environment его не видит",
            Environment.isExternalStorageManager(),
        )
        assertTrue(
            "Environment видит разрешение, но AllFilesAccess считает режим недоступным",
            AllFilesAccess(context).isAvailable(),
        )
    }

    /**
     * Отдельным звеном: приложение может считать разрешение выданным и всё равно
     * не видеть файлы — песочница хранилища выдаётся процессу при запуске.
     */
    @Test
    fun afterGrant_canListSharedStorage() {
        Shell.grantAllFilesAccess()
        BookFixtures.seed()

        val directory = File(BookFixtures.DIRECTORY)
        val names = directory.listFiles()?.map { it.name }?.sorted().orEmpty()
        TestArtifacts.note(
            "shared-storage-listing",
            buildString {
                appendLine("exists=${directory.exists()}")
                appendLine("canRead=${directory.canRead()}")
                appendLine("names=$names")
                @Suppress("DEPRECATION")
                appendLine("root=${Environment.getExternalStorageDirectory()}")
            },
        )

        assertTrue(
            "Файлы положены в ${BookFixtures.DIRECTORY}, но приложение их не видит: $names",
            names.containsAll(BookFixtures.fileNames),
        )
    }

    @Test
    fun afterGrant_findsSeededBooks() = runBlocking {
        Shell.grantAllFilesAccess()
        BookFixtures.seed()

        val found = AllFilesAccess(context).findBooks { _, _ -> }
        val names = found.map { it.fileName }.toSet()
        TestArtifacts.note("found-books", names.sorted().toString())

        assertTrue("EPUB не найден, найдено: $names", "valid.epub" in names)
        assertTrue("FB2 не найден, найдено: $names", "valid.fb2" in names)
        assertTrue("FB2 в архиве не найден, найдено: $names", "archived.fb2.zip" in names)
        assertTrue("Битый файл тоже должен попасть в список", "broken.epub" in names)
    }
}
