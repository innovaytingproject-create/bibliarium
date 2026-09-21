package com.bibliarium.app.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bibliarium.app.AppContainer
import com.bibliarium.app.data.settings.ReaderSettingsStore
import com.bibliarium.app.data.store.BookStore
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Failed(val error: ReaderOpenError) : ReaderState
    data class Ready(val content: ReaderContent) : ReaderState
}

/** Что показывает нижняя панель. */
data class ReadingPosition(
    val progress: Float,
    /** Оценка оставшегося времени в минутах; null, пока считать не из чего. */
    val minutesLeft: Int?,
    /**
     * Номер страницы и сколько их всего — только для PDF: там страница
     * настоящая и постоянная. В EPUB страница зависит от шрифта и полей,
     * и показывать её номер человеку нечестно.
     */
    val page: Int? = null,
    val totalPages: Int? = null,
)

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    private val bookStore: BookStore,
    private val opener: ReaderContentOpener,
    private val settingsStore: ReaderSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _position = MutableStateFlow(ReadingPosition(0f, null))
    val position: StateFlow<ReadingPosition> = _position.asStateFlow()

    private val _epubPreferences = MutableStateFlow(EpubPreferences())
    val epubPreferences: StateFlow<EpubPreferences> = _epubPreferences.asStateFlow()

    private val _pdfPreferences = MutableStateFlow(PdfiumPreferences())
    val pdfPreferences: StateFlow<PdfiumPreferences> = _pdfPreferences.asStateFlow()

    private val _pdfNightMode = MutableStateFlow(false)
    val pdfNightMode: StateFlow<Boolean> = _pdfNightMode.asStateFlow()

    private var bookId: String? = null

    fun open(id: String) {
        if (bookId == id && _state.value is ReaderState.Ready) return
        bookId = id

        viewModelScope.launch {
            _epubPreferences.value = readerDefaults(settingsStore.currentEpubPreferences())
            _pdfPreferences.value = pdfDefaults(settingsStore.currentPdfPreferences())
            _pdfNightMode.value = settingsStore.currentPdfNightMode()

            val book = bookStore.get(id)
            if (book == null) {
                _state.value = ReaderState.Failed(ReaderOpenError.FileMissing)
                return@launch
            }

            opener.open(book).fold(
                onSuccess = { content ->
                    _state.value = ReaderState.Ready(content)
                    _position.value = positionOf(
                        progress = book.progress,
                        totalPositions = content.totalPositions,
                        engine = content.engine,
                    )
                    bookStore.markOpened(id)
                },
                onFailure = { error ->
                    _state.value = ReaderState.Failed(
                        (error as? ReaderOpenException)?.error
                            ?: ReaderOpenError.Unreadable(error.message),
                    )
                },
            )
        }
    }

    /**
     * Вызывается на каждое перелистывание.
     *
     * Для PDF номер страницы приходит от самого PDFView, а не из локатора:
     * в pdfium-адаптере 3.3.0 локатор уходит на страницу вперёд показанной.
     */
    fun onLocatorChanged(locator: Locator, pdfPage: Int? = null, pdfPageCount: Int? = null) {
        val id = bookId ?: return
        val content = (_state.value as? ReaderState.Ready)?.content ?: return

        val progress = (locator.locations.totalProgression ?: locator.locations.progression ?: 0.0)
            .toFloat()
            .coerceIn(0f, 1f)

        _position.value = positionOf(
            progress = progress,
            totalPositions = pdfPageCount ?: content.totalPositions,
            engine = content.engine,
            position = pdfPage ?: locator.locations.position,
        )

        // След в логе: если место чтения однажды снова начнёт теряться,
        // будет видно, какой книге, какое значение и из какого локатора.
        android.util.Log.i(
            PROGRESS_TAG,
            "книга $id: прогресс ${(progress * 100).toInt()} %, локатор ${locator.serialize()}",
        )

        viewModelScope.launch {
            bookStore.saveProgress(id, progress, locator.serialize())
        }
    }

    fun updateEpubPreferences(preferences: EpubPreferences) {
        _epubPreferences.value = enforcePaginated(preferences)
        viewModelScope.launch { settingsStore.saveEpubPreferences(_epubPreferences.value) }
    }

    fun updatePdfPreferences(preferences: PdfiumPreferences) {
        _pdfPreferences.value = pdfDefaults(preferences)
        viewModelScope.launch { settingsStore.savePdfPreferences(_pdfPreferences.value) }
    }

    fun setPdfNightMode(enabled: Boolean) {
        _pdfNightMode.value = enabled
        viewModelScope.launch { settingsStore.savePdfNightMode(enabled) }
    }

    /**
     * Оценка «осталось N минут».
     *
     * Текста в книге мы не считаем — для этого пришлось бы вычитать её целиком.
     * Вместо этого берём позиции Readium: одна позиция это примерно 1024 знака,
     * то есть около 170 слов. При 250 словах в минуту получается ~0,7 минуты
     * на позицию. Для PDF позиция — это страница, и там по две минуты на страницу.
     */
    private fun positionOf(
        progress: Float,
        totalPositions: Int,
        engine: ReaderEngine,
        position: Int? = null,
    ): ReadingPosition {
        if (totalPositions <= 0) return ReadingPosition(progress, null)

        val left = (1f - progress).coerceIn(0f, 1f) * totalPositions
        val minutes = when (engine) {
            ReaderEngine.PDF -> left * MINUTES_PER_PDF_PAGE
            ReaderEngine.EPUB -> left * WORDS_PER_POSITION / WORDS_PER_MINUTE
        }

        val page = when (engine) {
            ReaderEngine.PDF ->
                (position ?: ((progress * totalPositions).toInt() + 1))
                    .coerceIn(1, totalPositions)
            ReaderEngine.EPUB -> null
        }

        return ReadingPosition(
            // У PDF доля считается по страницам: так процент и номер страницы
            // на панели говорят одно и то же.
            progress = page?.let { it.toFloat() / totalPositions } ?: progress,
            minutesLeft = minutes.roundToInt().coerceAtLeast(0),
            page = page,
            totalPages = page?.let { totalPositions },
        )
    }

    /**
     * Вертикальная прокрутка выключена жёстко и не показывается в настройках.
     * Здесь она гасится ещё раз: настройки переживают обновление приложения,
     * и старое сохранённое значение не должно включить прокрутку обратно.
     */
    private fun enforcePaginated(preferences: EpubPreferences): EpubPreferences =
        preferences.copy(scroll = false)

    /**
     * Первое открытие: поля из ТЗ — выключка по ширине, поля 22dp и межстрочный
     * 1.7. Уже выбранные человеком значения не трогаем.
     */
    private fun readerDefaults(saved: EpubPreferences): EpubPreferences =
        enforcePaginated(
            saved.copy(
                fontFamily = saved.fontFamily ?: FontFamily.LITERATA,
                lineHeight = saved.lineHeight ?: DEFAULT_LINE_HEIGHT,
                pageMargins = saved.pageMargins ?: DEFAULT_PAGE_MARGINS,
                textAlign = saved.textAlign ?: TextAlign.JUSTIFY,
            ),
        )

    private fun pdfDefaults(saved: PdfiumPreferences): PdfiumPreferences =
        saved.copy(
            // Горизонтальная ось — это перелистывание вбок, а не прокрутка вниз.
            scrollAxis = Axis.HORIZONTAL,
        )

    companion object {
        private const val PROGRESS_TAG = "BibliariumReader"
        private const val WORDS_PER_MINUTE = 250f
        private const val WORDS_PER_POSITION = 170f
        private const val MINUTES_PER_PDF_PAGE = 2f
        private const val DEFAULT_LINE_HEIGHT = 1.7
        private const val DEFAULT_PAGE_MARGINS = 1.0

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookStore = container.bookStore,
                    opener = container.readerContentOpener,
                    settingsStore = container.readerSettings,
                )
            }
        }
    }

    override fun onCleared() {
        (_state.value as? ReaderState.Ready)?.content?.close()
        super.onCleared()
    }
}
