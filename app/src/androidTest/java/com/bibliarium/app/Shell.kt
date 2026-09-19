package com.bibliarium.app

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Разрешения в тестах выдаются через adb, а не руками: MANAGE_EXTERNAL_STORAGE —
 * это appop, и его состояние переключается shell-командой.
 */
object Shell {

    fun run(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    fun grantAllFilesAccess() {
        run("appops set $PACKAGE MANAGE_EXTERNAL_STORAGE allow")
    }

    fun revokeAllFilesAccess() {
        run("appops set $PACKAGE MANAGE_EXTERNAL_STORAGE default")
    }

    fun allFilesAccessState(): String =
        run("appops get $PACKAGE MANAGE_EXTERNAL_STORAGE").trim()

    const val PACKAGE = "com.bibliarium.app"
}
