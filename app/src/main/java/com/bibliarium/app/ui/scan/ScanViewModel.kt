package com.bibliarium.app.ui.scan

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bibliarium.app.AppContainer
import com.bibliarium.app.data.access.FileAccessProvider
import com.bibliarium.app.data.importer.BatchImporter
import com.bibliarium.app.data.importer.BatchProgress
import com.bibliarium.app.data.scan.BookScanner
import com.bibliarium.app.data.scan.ScanPhase
import com.bibliarium.app.data.scan.ScanProgress
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
    /** Ни папок не выбрано, ни полного доступа — искать негде. */
    NO_ACCESS,
    READY,
    SCANNING,
    RESULTS,
    IMPORTING,
}

data class ScanUiState(
    val stage: ScanStage = ScanStage.NO_ACCESS,
    val progress: ScanProgress? = null,
    val books: List<FoundBook> = emptyList(),
    val selected: Set<String> = emptySet(),
    /** По умолчанию скрываем уже добавленное: повторный поиск показывает только новое. */
    val onlyNew: Boolean = true,
    val batch: BatchProgress? = null,
    val fullAccess: Boolean = false,
) {
    val visibleBooks: List<FoundBook>
        get() = if (onlyNew) books.filter { it.state != FoundBookState.ALREADY_ADDED } else books

    val selectableCount: Int get() = visibleBooks.count { it.selectable }

    val alreadyAddedCount: Int get() = books.count { it.state == FoundBookState.ALREADY_ADDED }

    val allSelected: Boolean
        get() = selectableCount > 0 && selected.size >= selectableCount
}

class ScanViewModel(
    private val accessProvider: FileAccessProvider,
    private val scanner: BookScanner,
    private val bookStore: BookStore,
    private val batchImporter: BatchImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var scanJob: Job? = null
    private var batchJob: Job? = null

    /**
     * Прогресс приходит на каждую просмотренную папку. На телефоне с тысячами
     * папок это тысячи перерисовок подряд — экран начинает захлёбываться,
     * а полезного в них ничего. Поэтому обновляем не чаще, чем раз в 120 мс,
     * и всегда пропускаем смену этапа.
     */
    private var lastProgressAt = 0L
    private var lastPhase: ScanPhase? = null

    private fun publishProgress(progress: ScanProgress) {
        val now = SystemClock.uptimeMillis()
        if (progress.phase == lastPhase && now - lastProgressAt < PROGRESS_INTERVAL_MS) return
        lastProgressAt = now
        lastPhase = progress.phase
        _state.update { it.copy(progress = progress) }
    }

    init {
        refreshAccess(autoStart = true)
    }

    /**
     * Проверяем доступ при каждом заходе на экран: пользователь мог выдать или
     * отозвать полный доступ в системных настройках, пока нас не было видно.
     */
    fun refreshAccess(autoStart: Boolean = false) {
        viewModelScope.launch {
            val access = accessProvider.current()
            val available = access.isAvailable()
            _state.update {
                it.copy(
                    fullAccess = accessProvider.allFilesGranted(),
                    stage = when {
                        !available -> ScanStage.NO_ACCESS
                        it.stage == ScanStage.NO_ACCESS -> ScanStage.READY
                        else -> it.stage
                    },
                )
            }
            if (available && autoStart && _state.value.books.isEmpty()) {
                startScan()
            }
        }
    }

    fun startScan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    stage = ScanStage.SCANNING,
                    books = emptyList(),
                    selected = emptySet(),
                    progress = ScanProgress(ScanPhase.WALKING),
                    batch = null,
                )
            }

            try {
                val access = accessProvider.current()
                if (!access.isAvailable()) {
                    _state.update { it.copy(stage = ScanStage.NO_ACCESS, progress = null) }
                    return@launch
                }

                val raw = access.findBooks { scanned, found ->
                    publishProgress(
                        ScanProgress(
                            phase = ScanPhase.WALKING,
                            scanned = scanned,
                            found = found,
                        ),
                    )
                }

                val known = runCatching { bookStore.fingerprints() }.getOrDefault(emptyMap())
                val checked = scanner.markAlreadyAdded(raw, known) { processed, total ->
                    publishProgress(
                        ScanProgress(
                            phase = ScanPhase.MATCHING,
                            processed = processed,
                            total = total,
                        ),
                    )
                }

                _state.update { it.copy(books = checked, stage = ScanStage.RESULTS) }

                scanner.readTitles(checked).collect { update ->
                    publishProgress(
                        ScanProgress(
                            phase = ScanPhase.READING_TITLES,
                            processed = update.processed,
                            total = update.total,
                        ),
                    )
                    _state.update { current ->
                        current.copy(
                            books = if (update.title == null) {
                                current.books
                            } else {
                                current.books.map { book ->
                                    if (book.uri == update.uri) {
                                        book.copy(title = update.title)
                                    } else {
                                        book
                                    }
                                }
                            },
                        )
                    }
                }

                _state.update { it.copy(progress = null) }
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
                current.copy(
                    selected = current.visibleBooks
                        .filter { it.selectable }
                        .map { it.uri }
                        .toSet(),
                )
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

    /** После показа итога возвращаемся к списку, пометив добавленное. */
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
        private const val PROGRESS_INTERVAL_MS = 120L

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ScanViewModel(
                    accessProvider = container.fileAccessProvider,
                    scanner = container.bookScanner,
                    bookStore = container.bookStore,
                    batchImporter = container.batchImporter,
                )
            }
        }
    }
}
