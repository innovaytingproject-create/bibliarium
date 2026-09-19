package com.bibliarium.app

import android.graphics.Bitmap
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Скриншоты и заметки складываются во внутренний каталог приложения, а забирает
 * их CI через `run-as`.
 *
 * Внешняя память для этого не годится: на Android 11 shell не читает
 * /sdcard/Android/data, и на API 30 артефакты просто не доезжали.
 * Внутренний каталог доступен всегда и не требует никаких разрешений.
 */
object TestArtifacts {

    const val TAG = "BibliariumTest"

    private val directory: File by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "test-artifacts").apply { mkdirs() }
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
        }.onFailure { Log.w(TAG, "Не удалось снять скриншот $name", it) }
    }

    fun note(name: String, text: String) {
        Log.i(TAG, "$name: ${text.replace("\n", " | ")}")
        runCatching { File(directory, "$name.txt").writeText(text) }
            .onFailure { Log.w(TAG, "Не удалось записать заметку $name", it) }
    }
}
