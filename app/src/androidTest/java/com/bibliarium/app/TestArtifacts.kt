package com.bibliarium.app

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Скриншоты и прочие следы прогона складываются в каталог приложения на внешней
 * памяти: оттуда их забирает `adb pull` в CI, и приложению для записи туда не нужно
 * никаких разрешений.
 */
object TestArtifacts {

    private val directory: File by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "test-artifacts").apply { mkdirs() }
    }

    fun screenshot(name: String) {
        runCatching {
            val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation()
                .uiAutomation
                .takeScreenshot()
                ?: return

            File(directory, "$name.png").outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
        }
    }

    fun note(name: String, text: String) {
        runCatching { File(directory, "$name.txt").writeText(text) }
    }
}
