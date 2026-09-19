package com.bibliarium.app

import android.app.Activity
import android.app.Instrumentation
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bibliarium.app.ui.MainActivity
import com.bibliarium.app.ui.TestTags
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Путь пользователя целиком: развилка добавления, экран доступа, выдача
 * разрешения снаружи приложения и возврат обратно.
 */
@RunWith(AndroidJUnit4::class)
class FullAccessFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun setUp() {
        Shell.revokeAllFilesAccess()
    }

    @After
    fun tearDown() {
        Shell.revokeAllFilesAccess()
        BookFixtures.cleanUp()
    }

    @Test
    fun tappingFullAccessSwitch_sendsSystemIntent() {
        Intents.init()
        try {
            // Настоящие системные настройки открывать не нужно — подменяем ответ.
            Intents.intending(hasAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

            ActivityScenario.launch(MainActivity::class.java).use {
                openSettingsScreen()

                compose.onNodeWithTag(TestTags.FULL_ACCESS_SWITCH).assertIsOff()
                TestArtifacts.screenshot("settings-before-grant")

                compose.onNodeWithTag(TestTags.FULL_ACCESS_SWITCH).performClick()
                compose.waitForIdle()

                Intents.intended(hasAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } finally {
            Intents.release()
        }
    }

    /**
     * Ровно то, о чём сообщил пользователь: разрешение выдано в системных
     * настройках, человек вернулся в приложение — и дальше ничего.
     */
    @Test
    fun grantingWhileAppIsRunning_updatesSettingsSwitch() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettingsScreen()
            compose.onNodeWithTag(TestTags.FULL_ACCESS_SWITCH).assertIsOff()

            // Пользователь ушёл в системные настройки и выдал доступ.
            Shell.grantAllFilesAccess()

            // Возврат в приложение: экран снова становится видимым.
            it.moveToState(Lifecycle.State.CREATED)
            it.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()

            TestArtifacts.screenshot("settings-after-grant")
            compose.onNodeWithTag(TestTags.FULL_ACCESS_SWITCH).assertIsOn()
        }
    }

    @Test
    fun grantingWhileAppIsRunning_makesScanScreenReady() {
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Добавить книгу").performClick()
            compose.onNodeWithText("Найти книги на телефоне").performClick()
            compose.waitForIdle()

            compose.onNodeWithText("Искать негде").assertIsDisplayed()
            TestArtifacts.screenshot("scan-no-access")

            Shell.grantAllFilesAccess()

            // Человек возвращается назад и заходит в поиск снова.
            compose.onNodeWithText("Назад").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Найти книги на телефоне").performClick()
            compose.waitForIdle()

            TestArtifacts.screenshot("scan-after-grant")
            compose.onNodeWithText("Начать поиск").assertIsDisplayed()
        }
    }

    private fun openSettingsScreen() {
        compose.onNodeWithText("Добавить книгу").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Доступ к файлам").performClick()
        compose.waitForIdle()
    }
}
