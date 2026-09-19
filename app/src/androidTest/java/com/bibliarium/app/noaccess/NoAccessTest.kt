package com.bibliarium.app.noaccess

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibliarium.app.BuildConfig
import com.bibliarium.app.Shell
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.data.access.AllFilesAccess
import com.bibliarium.app.ui.MainActivity
import com.bibliarium.app.ui.TestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Прогон без выданного MANAGE_EXTERNAL_STORAGE.
 *
 * Состояние разрешения задаётся скриптом ДО запуска, а не внутри теста:
 * менять appop во время инструментального прогона нельзя — рвётся соединение
 * UiAutomation, и прогон падает целиком.
 */
@RunWith(AndroidJUnit4::class)
class NoAccessTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun checkPreconditions() {
        TestArtifacts.note(
            "noaccess-environment",
            buildString {
                appendLine("sdk=${Build.VERSION.SDK_INT}")
                appendLine("release=${Build.VERSION.RELEASE}")
                appendLine("allFilesAccessBuild=${BuildConfig.ALL_FILES_ACCESS}")
                appendLine("appops=${Shell.allFilesAccessState()}")
            },
        )
    }

    @Test
    fun applicationIsInstalled() {
        assertEquals("com.bibliarium.app", context.packageName)
    }

    @Test
    fun allFilesAccessIsNotAvailable() {
        assertFalse(
            "Разрешение не выдано, но режим полного доступа считает себя доступным",
            AllFilesAccess(context).isAvailable(),
        )
    }

    @Test
    fun scanScreenSaysThereIsNowhereToSearch() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Добавить книгу").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Найти книги на телефоне").performClick()
            compose.waitForIdle()

            TestArtifacts.screenshot("noaccess-scan")
            compose.onNodeWithText("Искать негде").assertIsDisplayed()
        }
    }

    @Test
    fun switchIsOffAndTappingItSendsSystemIntent() {
        Intents.init()
        try {
            // Настоящие системные настройки открывать не нужно — подменяем ответ.
            Intents.intending(hasAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText("Добавить книгу").performClick()
                compose.waitForIdle()
                compose.onNodeWithText("Доступ к файлам").performClick()
                compose.waitForIdle()

                TestArtifacts.screenshot("noaccess-settings")
                compose.onNodeWithTag(TestTags.FULL_ACCESS_SWITCH).assertIsOff()
                compose.onNodeWithTag(TestTags.FULL_ACCESS_SWITCH).performClick()
                compose.waitForIdle()

                Intents.intended(hasAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } finally {
            Intents.release()
        }
    }

    /**
     * Системные настройки должны открываться поверх нашей задачи, а не в своей.
     *
     * С FLAG_ACTIVITY_NEW_TASK экран настроек уходит в отдельную задачу, и
     * кнопка «Назад» из него возвращает не в приложение, а туда, что лежит
     * под ним в той задаче — на многих прошивках это лаунчер. Снаружи это
     * выглядит ровно как «нажал дать доступ, и дальше ничего»: разрешение
     * выдано, но человек в приложение не вернулся.
     */
    @Test
    fun fullAccessIntentOpensOnTopOfOurTask() {
        Intents.init()
        try {
            Intents.intending(hasAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

            ActivityScenario.launch(MainActivity::class.java).use {
                compose.onNodeWithText("Добавить книгу").performClick()
                compose.waitForIdle()
                compose.onNodeWithText("Доступ к файлам").performClick()
                compose.waitForIdle()
                compose.onNodeWithTag(TestTags.FULL_ACCESS_SWITCH).performClick()
                compose.waitForIdle()

                val sent = Intents.getIntents()
                    .first { it.action == Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION }

                TestArtifacts.note(
                    "full-access-intent",
                    "action=${sent.action} data=${sent.data} flags=0x${sent.flags.toString(16)}",
                )

                assertEquals(
                    "Настройки должны открываться для нашего пакета",
                    "package:com.bibliarium.app",
                    sent.data?.toString(),
                )
                assertEquals(
                    "FLAG_ACTIVITY_NEW_TASK уводит настройки в отдельную задачу, " +
                        "и «Назад» не возвращает в приложение",
                    0,
                    sent.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
                )
            }
        } finally {
            Intents.release()
        }
    }
}
