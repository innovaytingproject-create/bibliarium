package com.bibliarium.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверяет, что эмулятор в CI поднялся, приложение поставилось и артефакты
 * прогона доезжают до Actions. Ничего про логику приложения не утверждает.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @Test
    fun applicationIsInstalled() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.bibliarium.app", context.packageName)

        TestArtifacts.note(
            "environment",
            buildString {
                appendLine("sdk=${Build.VERSION.SDK_INT}")
                appendLine("release=${Build.VERSION.RELEASE}")
                appendLine("fingerprint=${Build.FINGERPRINT}")
                appendLine("allFilesAccessBuild=${BuildConfig.ALL_FILES_ACCESS}")
            },
        )
        TestArtifacts.screenshot("smoke")
    }
}
