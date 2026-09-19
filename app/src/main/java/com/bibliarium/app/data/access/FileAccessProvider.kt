package com.bibliarium.app.data.access

import android.content.Context
import android.net.Uri
import com.bibliarium.app.data.settings.AppSettings

/**
 * Решает, каким способом искать книги прямо сейчас.
 *
 * Полный доступ проверяется при каждом обращении, а не запоминается:
 * пользователь мог отозвать разрешение в системных настройках, пока
 * приложение было свёрнуто.
 */
class FileAccessProvider(
    context: Context,
    private val settings: AppSettings,
) {

    private val appContext = context.applicationContext

    /** Доступен ли режим полного доступа в этой сборке и на этой версии Android. */
    fun allFilesSupported(): Boolean = AllFilesAccess.supported()

    /** Выдан ли полный доступ прямо сейчас. */
    fun allFilesGranted(): Boolean = AllFilesAccess.granted()

    suspend fun current(): FileAccess =
        if (allFilesGranted()) {
            AllFilesAccess(appContext)
        } else {
            TreeFileAccess(appContext, settings.currentScanRoots().map(Uri::parse))
        }
}
