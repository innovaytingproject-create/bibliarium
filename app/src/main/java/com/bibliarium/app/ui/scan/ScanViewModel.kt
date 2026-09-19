package com.bibliarium.app.ui.scan

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bibliarium.app.AppContainer
import com.bibliarium.app.data.importer.BatchImporter
import com.bibliarium.app.data.importer.BatchProgress
import com.bibliarium.app.data.scan.DeviceScanner
import com.bibliarium.app.data.scan.ScanUpdate
import com.bibliarium.app.data.settings.AppSettings
import com.bibliarium.app.data.store.BookStore
import com.bibliarium.app.domain.FoundBook
import com.bibliarium.app.domain.FoundBookState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanStage {
    /** Корневая папка ещё не выбрана — спрашиваем один раз. */
    NEED_ROOT,
    READY,
    SCANNING,
    RESULTS,
    IMPORTING,
}

data class ScanUiState(
    val stage: ScanStage = ScanStage.NEED_ROOT,
    val rootUri: String? = null,
    val progress: ScanUpdate.Progress? = null,
    val books: List<FoundBook> = emptyList(),
    val selected: Set<String> = emptySet(),
    /** По умолчанию скрываем уже добавленное: повторный поиск показывает только новое. */
    val onlyNew: Boolean = true,
    val batch: BatchProgress? = null,
) {
    val visibleBooks: List<FoundBook>
        get() = if (onlyNew) books.filter { it.state != FoundBookState.ALREADY_ADDED } else books

    val selectableCount: Int get() = visibleBooks.count { it.selectable }

    val alreadyAddedCount: Int get() = books.count { it.state == FoundBookState.ALREADY_ADDED }

    val allSelected: Boolean
        get() = selectableCount > 0 && selected.size >= selectableCount
}

class ScanViewModel(
    private val scanner: DeviceScanner,
    private val bookStore: BookStore,
    private val settings: AppSettings,
    private val batchImporter: BatchImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var batchJob: Job? = null

    init {
        viewModelScope.launch {
            val root = settings.currentScanRootUri()
            _state.update {
                it.copy(
                    rootUri = root,
                    stage = if (root == null) ScanStage.NEED_ROOT else ScanStage.READY,
                )
            }
            if (root != null) startScan()
        }
    }

    /** Разрешение на дерево уже взято экраном; сюда приходит готовый URI. */
    fun onRootChosen(uri: String) {
        viewModelScope.launch {
            settings.setScanRootUri(uri)
            _state.update { it.copy(rootUri = uri, stage = ScanStage.READY) }
            startScan()
        }
    }

    fun startScan() {
        val root = _state.value.rootUri ?: return
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = ScanStage.SCANNING,
                    books = emptyList(),
                    selected = emptySet(),
                    progress = null,
                    batch = null,
                )
            }

            val known = runCatching { bookStore.fingerprints() }.getOrDefault(emptyMap())

            try {
                scanner.scan(Uri.parse(root), known).collect { update ->
                    when (update) {
                        is ScanUpdate.Progress ->
                            _state.update { it.copy(progress = update) }

                        is ScanUpdate.Found ->
                            _state.update { it.copy(books = update.books) }

                        is ScanUpdate.Title ->
                            _state.update { current ->
                                current.copy(
                                    books = current.books.map { book ->
                                        if (book.uri == update.uri) {
                                            book.copy(title = update.title)
                                        } else {
                                            book
                                        }
                                    },
                                )
                            }

                        ScanUpdate.Finished ->
                            _state.update {
                                it.copy(stage = ScanStage.RESULTS, progress = null)
                            }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Провайдер мог отозвать доступ или отдать битый курсор.
                // Показываем то, что успели найти, вместо падения.
                _state.update { it.copy(stage = ScanStage.RESULTS, progress = null) }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update {
            it.copy(
                stage = if (it.books.isEmpty()) ScanStage.READY else ScanStage.RESULTS,
                progress = null,
            )
        }
    }

    fun toggle(uri: String) {
        _state.update { current ->
            val book = current.books.firstOrNull { it.uri == uri } ?: return@update current
            if (!book.selectable) return@update current
            val selected = if (uri in current.selected) {
                current.selected - uri
            } else {
                current.selected + uri
            }
            current.copy(selected = selected)
        }
    }

    fun toggleSelectAll() {
        _state.update { current ->
            if (current.allSelected) {
                current.copy(selected = emptySet())
            } else {
                current.copy(selected = current.visibleBooks.filter { it.selectable }.map { it.uri }.toSet())
            }
        }
    }

    fun setOnlyNew(onlyNew: Boolean) {
        _state.update { current ->
            val visible = if (onlyNew) {
                current.books.filter { it.state != FoundBookState.ALREADY_ADDED }
            } else {
                current.books
            }
            val visibleUris = visible.map { it.uri }.toSet()
            current.copy(onlyNew = onlyNew, selected = current.selected intersect visibleUris)
        }
    }

    fun importSelected() {
        val current = _state.value
        val chosen = current.books.filter { it.uri in current.selected && it.selectable }
        if (chosen.isEmpty()) return

        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            val batchId = batchImporter.enqueue(chosen)
            _state.update { it.copy(stage = ScanStage.IMPORTING) }
            batchImporter.observe(batchId).collect { progress ->
                _state.update { it.copy(batch = progress) }
            }
        }
    }

    /** После показа итога возвращаемся к списку, убрав добавленное. */
    fun acknowledgeImport() {
        batchJob?.cancel()
        batchJob = null
        val failedUris = _state.value.batch?.failures?.map { it.uri }?.toSet().orEmpty()
        val imported = _state.value.selected - failedUris
        _state.update { current ->
            current.copy(
                stage = ScanStage.RESULTS,
                batch = null,
                selected = emptySet(),
                books = current.books.map { book ->
                    if (book.uri in imported) {
                        book.copy(state = FoundBookState.ALREADY_ADDED)
                    } else {
                        book
                    }
                },
            )
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        batchJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ScanViewModel(
                    scanner = container.deviceScanner,
                    bookStore = container.bookStore,
                    settings = container.settings,
                    batchImporter = container.batchImporter,
                )
            }
        }
    }
}
