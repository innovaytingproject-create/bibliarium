package com.bibliarium.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bibliarium.app.AppContainer
import com.bibliarium.app.data.access.FileAccessProvider
import com.bibliarium.app.data.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanRoot(
    val uri: String,
    /** Человеческий путь вида "Download/Книги". */
    val label: String,
)

/** Почему добавление папки не получилось. */
enum class FolderPickFailure {
    /** Система не дала выбрать эту папку — корень памяти, Download, Android/data. */
    BLOCKED_BY_SYSTEM,

    /** Пользователь закрыл выбор, ничего не выбрав. */
    CANCELLED,
}

data class SettingsUiState(
    val roots: List<ScanRoot> = emptyList(),
    val allFilesSupported: Boolean = false,
    val allFilesGranted: Boolean = false,
    val pickFailure: FolderPickFailure? = null,
)

class SettingsViewModel(
    private val context: Context,
    private val settings: AppSettings,
    private val accessProvider: FileAccessProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.scanRoots.collect { uris ->
                _state.update { it.copy(roots = uris.map { uri -> ScanRoot(uri, labelOf(uri)) }) }
            }
        }
        refresh()
    }

    /** Разрешение могли выдать или отозвать в системных настройках — перечитываем при каждом заходе. */
    fun refresh() {
        _state.update {
            it.copy(
                allFilesSupported = accessProvider.allFilesSupported(),
                allFilesGranted = accessProvider.allFilesGranted(),
            )
        }
    }

    fun onFolderPicked(uri: Uri?) {
        if (uri == null) {
            // Отличить отказ системы от «пользователь передумал» нельзя: в обоих
            // случаях приходит null. Подсказка написана так, чтобы подходить к обоим.
            _state.update { it.copy(pickFailure = FolderPickFailure.CANCELLED) }
            return
        }

        if (!canList(uri)) {
            _state.update { it.copy(pickFailure = FolderPickFailure.BLOCKED_BY_SYSTEM) }
            return
        }

        viewModelScope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            settings.addScanRoot(uri.toString())
            _state.update { it.copy(pickFailure = null) }
        }
    }

    fun removeRoot(uri: String) {
        viewModelScope.launch {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            settings.removeScanRoot(uri)
        }
    }

    fun consumePickFailure() {
        _state.update { it.copy(pickFailure = null) }
    }

    /**
     * Программно ни выдать, ни отозвать MANAGE_EXTERNAL_STORAGE нельзя —
     * и включение, и выключение идут через системные настройки.
     */
    fun allFilesAccessIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** Проверяем, что дерево действительно читается, а не только формально выдано. */
    private fun canList(treeUri: Uri): Boolean = runCatching {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        context.contentResolver
            .query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
            ?.use { true }
            ?: false
    }.getOrDefault(false)

    private fun labelOf(uri: String): String = runCatching {
        DocumentsContract.getTreeDocumentId(Uri.parse(uri))
            .substringAfter(':')
            .trim('/')
            .ifEmpty { Uri.parse(uri).lastPathSegment ?: uri }
    }.getOrDefault(uri)

    companion object {
        fun factory(context: Context, container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    SettingsViewModel(
                        context = context.applicationContext,
                        settings = container.settings,
                        accessProvider = container.fileAccessProvider,
                    )
                }
            }
    }
}
