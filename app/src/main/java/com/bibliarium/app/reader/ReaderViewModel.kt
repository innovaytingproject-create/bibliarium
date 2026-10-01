package com.bibliarium.app.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bibliarium.app.AppContainer
import com.bibliarium.app.data.settings.ReaderSettingsStore
import com.bibliarium.app.data.store.BookStore
import com.bibliarium.app.data.store.RatingStore
import kotlin.math.abs
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
import org.readium.r2.navigator.preferences.Theme
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
    /**
     * Название текущей главы — его показывает верхняя панель. Стоит
     * последним: выше идут поля, которые передаются по порядку.
     */
    val chapter: String? = null,
)

@OptIn(ExperimentalReadiumApi::class)
class ReaderViewModel(
    private val bookStore: BookStore,
    private val opener: ReaderContentOpener,
    private val settingsStore: ReaderSettingsStore,
    private val ratingStore: RatingStore,
) : ViewModel() {

    /**
     * Книга дочитана, и опрос по ней ещё не показывали.
     *
     * Экран спрашивает об этом сам: показать опрос — его дело, а не
     * вьюмодели, которая не знает ни про активити, ни про переходы.
     */
    private val _surveyDue = MutableStateFlow<String?>(null)
    val surveyDue: StateFlow<String?> = _surveyDue.asStateFlow()

    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private val _position = MutableStateFlow(ReadingPosition(0f, null))
    val position: StateFlow<ReadingPosition> = _position.asStateFlow()

    private val _epubPreferences = MutableStateFlow(EpubPreferences())
    val epubPreferences: StateFlow<EpubPreferences> = _epubPreferences.asStateFlow()

    private val _pdfPreferences = MutableStateFlow(PdfiumPreferences())
    val pdfPreferences: StateFlow<PdfiumPreferences> = _pdfPreferences.asStateFlow()

    /**
     * Тема чтения. Своя, не общая с приложением: сепия есть только здесь,
     * и переключается она в настройках чтения, а не в системе.
     */
    /** Какая строка оглавления сейчас: по ней подсвечивается текущая глава. */
    private val _tocIndex = MutableStateFlow(0)
    val tocIndex: StateFlow<Int> = _tocIndex.asStateFlow()

    private val _theme = MutableStateFlow(ReaderTheme.LIGHT)
    val theme: StateFlow<ReaderTheme> = _theme.asStateFlow()

    /**
     * Настройки чтения в человеческих единицах: их показывает лист настроек.
     * В единицы Readium они переводятся при записи, а не при показе.
     */
    private val _settings = MutableStateFlow(
        ReadingSettings(
            font = ReadingFont.LITERATA,
            size = BASE_FONT_SIZE,
            margins = ReadingMargins.MEDIUM,
            spacing = ReadingSpacing.NORMAL,
            theme = ReaderTheme.LIGHT,
            brightness = DEFAULT_BRIGHTNESS,
        ),
    )
    val settings: StateFlow<ReadingSettings> = _settings.asStateFlow()

    private val _pdfNightMode = MutableStateFlow(false)
    val pdfNightMode: StateFlow<Boolean> = _pdfNightMode.asStateFlow()

    private var bookId: String? = null

    /** Последнее место чтения: его же сохраняем, когда книга дочитана. */
    private var lastLocator: String? = null

    /**
     * [locatorOverride] — место, заданное снаружи: так из оглавления
     * в карточке книга открывается сразу на нужной главе, а не там, где
     * её закрыли в прошлый раз.
     */
    fun open(id: String, locatorOverride: String? = null) {
        if (bookId == id && _state.value is ReaderState.Ready) return
        bookId = id

        viewModelScope.launch {
            _theme.value = settingsStore.currentTheme()
            _epubPreferences.value = withTheme(
                readerDefaults(settingsStore.currentEpubPreferences()),
                _theme.value,
            )
            _pdfPreferences.value = pdfDefaults(settingsStore.currentPdfPreferences())
            _pdfNightMode.value = _theme.value == ReaderTheme.DARK
            _settings.value = settingsOf(
                _epubPreferences.value,
                _theme.value,
                settingsStore.currentBrightness(),
            )

            val book = bookStore.get(id)
            if (book == null) {
                _state.value = ReaderState.Failed(ReaderOpenError.FileMissing)
                return@launch
            }

            opener.open(book, locatorOverride).fold(
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

        val progress = content.progressOf(locator)

        _position.value = positionOf(
            progress = progress,
            totalPositions = pdfPageCount ?: content.totalPositions,
            engine = content.engine,
            position = pdfPage ?: locator.locations.position,
        ).copy(chapter = locator.title?.trim()?.takeIf { it.isNotEmpty() })

        // След в логе: если место чтения однажды снова начнёт теряться,
        // будет видно, какой книге, какое значение и из какого локатора.
        android.util.Log.i(
            PROGRESS_TAG,
            "книга $id: прогресс ${(progress * 100).toInt()} %, локатор ${locator.serialize()}",
        )

        lastLocator = locator.serialize()
        _tocIndex.value = tocIndexFor(content, locator)
        viewModelScope.launch {
            bookStore.saveProgress(id, progress, lastLocator)
            if (progress >= FINISHED) askAboutBook(id)
        }
    }

    /**
     * Листать дальше некуда — книга дочитана.
     *
     * По доле этого не поймать: Readium считает её по началу видимой
     * страницы, и на последней странице выходит 98–99 %, а не сто.
     * Поэтому конец определяется тем, чем он и является для человека:
     * перелистнуть больше нельзя.
     */
    fun onReachedEnd() {
        val id = bookId ?: return
        if (_position.value.progress >= FINISHED) return

        _position.value = _position.value.copy(progress = 1f)
        viewModelScope.launch {
            // Место чтения остаётся прежним: человек стоит на последней
            // странице, и возвращаться он должен туда же.
            bookStore.saveProgress(id, 1f, lastLocator)
            askAboutBook(id)
        }
    }

    /**
     * Опрос показывается один раз на книгу: отметка ставится сразу, иначе
     * он всплывал бы снова при каждом новом перелистывании последней
     * страницы.
     */
    private suspend fun askAboutBook(id: String) {
        val rating = ratingStore.get(id)
        if (rating?.surveyShown == true) return
        ratingStore.markSurveyShown(id)
        _surveyDue.value = id
    }

    fun surveyShown() {
        _surveyDue.value = null
    }

    fun updateEpubPreferences(preferences: EpubPreferences) {
        _epubPreferences.value = enforcePaginated(preferences)
        viewModelScope.launch { settingsStore.saveEpubPreferences(_epubPreferences.value) }
    }

    fun updatePdfPreferences(preferences: PdfiumPreferences) {
        _pdfPreferences.value = pdfDefaults(preferences)
        viewModelScope.launch { settingsStore.savePdfPreferences(_pdfPreferences.value) }
    }

    /**
     * Тема чтения одна на все книги: и фон страницы, и цвет панелей.
     * У EPUB её применяет Readium, у PDF — ночной режим самого PDFView.
     */
    fun setTheme(theme: ReaderTheme) {
        _theme.value = theme
        _settings.value = _settings.value.copy(theme = theme)
        _epubPreferences.value = withTheme(_epubPreferences.value, theme)
        _pdfNightMode.value = theme == ReaderTheme.DARK
        viewModelScope.launch {
            settingsStore.saveTheme(theme)
            settingsStore.saveEpubPreferences(_epubPreferences.value)
            settingsStore.savePdfNightMode(_pdfNightMode.value)
        }
    }

    fun setFont(font: ReadingFont) {
        _settings.value = _settings.value.copy(font = font)
        updateEpubPreferences(
            _epubPreferences.value.copy(
                fontFamily = when (font) {
                    ReadingFont.LORA -> FontFamily.LORA
                    ReadingFont.LITERATA -> FontFamily.LITERATA
                    ReadingFont.PLEX_SANS -> PLEX_SANS_FAMILY
                    ReadingFont.SYSTEM -> null
                },
            ),
        )
    }

    fun setSize(size: Int) {
        val clamped = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        _settings.value = _settings.value.copy(size = clamped)
        updateEpubPreferences(
            _epubPreferences.value.copy(
                fontSize = clamped.toDouble() / BASE_FONT_SIZE,
            ),
        )
    }

    fun setMargins(margins: ReadingMargins) {
        _settings.value = _settings.value.copy(margins = margins)
        updateEpubPreferences(_epubPreferences.value.copy(pageMargins = margins.multiplier))
    }

    fun setSpacing(spacing: ReadingSpacing) {
        _settings.value = _settings.value.copy(spacing = spacing)
        updateEpubPreferences(_epubPreferences.value.copy(lineHeight = spacing.value))
    }

    /**
     * Яркость подсветки. Меняется только пока открыто приложение: системную
     * настройку трогать нельзя — человек её ставил не для нас.
     */
    fun setBrightness(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _settings.value = _settings.value.copy(brightness = clamped)
        viewModelScope.launch { settingsStore.saveBrightness(clamped) }
    }

    /**
     * Текущая глава в оглавлении.
     *
     * Сначала ищем строку с тем же файлом, что и у места чтения; если глав
     * в файле несколько — берём последнюю, что уже началась.
     */
    private fun tocIndexFor(content: ReaderContent, locator: Locator): Int {
        val entries = content.tableOfContents
        if (entries.isEmpty()) return 0
        val sameHref = entries.withIndex().filter { it.value.locator.href == locator.href }
        if (sameHref.isEmpty()) return _tocIndex.value
        val progression = locator.locations.progression ?: 0.0
        return sameHref.lastOrNull { (_, entry) ->
            (entry.locator.locations.progression ?: 0.0) <= progression
        }?.index ?: sameHref.first().index
    }

    private fun settingsOf(
        preferences: EpubPreferences,
        theme: ReaderTheme,
        brightness: Float,
    ): ReadingSettings = ReadingSettings(
        font = when (preferences.fontFamily) {
            FontFamily.LORA -> ReadingFont.LORA
            FontFamily.LITERATA -> ReadingFont.LITERATA
            PLEX_SANS_FAMILY -> ReadingFont.PLEX_SANS
            else -> ReadingFont.SYSTEM
        },
        size = ((preferences.fontSize ?: 1.0) * BASE_FONT_SIZE).roundToInt()
            .coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE),
        margins = ReadingMargins.entries.minBy {
            kotlin.math.abs(it.multiplier - (preferences.pageMargins ?: 1.0))
        },
        spacing = ReadingSpacing.entries.minBy {
            kotlin.math.abs(it.value - (preferences.lineHeight ?: DEFAULT_LINE_HEIGHT))
        },
        theme = theme,
        brightness = if (brightness < 0f) DEFAULT_BRIGHTNESS else brightness,
    )

    private fun withTheme(preferences: EpubPreferences, theme: ReaderTheme): EpubPreferences =
        preferences.copy(
            theme = when (theme) {
                ReaderTheme.LIGHT -> Theme.LIGHT
                ReaderTheme.SEPIA -> Theme.SEPIA
                ReaderTheme.DARK -> Theme.DARK
            },
        )

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

        /** С этой доли книга считается дочитанной — тот же порог, что в хранилище. */
        private const val FINISHED = 0.99f
        private const val DEFAULT_LINE_HEIGHT = 1.7
        private const val DEFAULT_PAGE_MARGINS = 1.0
        private const val MIN_FONT_SIZE = 14
        private const val MAX_FONT_SIZE = 26

        /** Полная яркость по умолчанию: лист открывается с понятным ползунком. */
        private const val DEFAULT_BRIGHTNESS = 0.8f

        /** Шрифт объявляется навигатору в ReadingFonts.kt под этим же именем. */
        private val PLEX_SANS_FAMILY = FontFamily("IBM Plex Sans")

        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ReaderViewModel(
                    bookStore = container.bookStore,
                    opener = container.readerContentOpener,
                    settingsStore = container.readerSettings,
                    ratingStore = container.ratingStore,
                )
            }
        }
    }

    override fun onCleared() {
        (_state.value as? ReaderState.Ready)?.content?.close()
        super.onCleared()
    }
}
