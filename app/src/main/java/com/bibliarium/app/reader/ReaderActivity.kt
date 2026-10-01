package com.bibliarium.app.reader

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bibliarium.app.R
import com.bibliarium.app.appContainer
import com.bibliarium.app.ui.rating.RatingSurveyActivity
import com.bibliarium.app.ui.theme.BibliariumTheme
import com.github.barteksc.pdfviewer.PDFView
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFactory
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFragment
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Экран чтения.
 *
 * Страницу книги показывает навигатор Readium — это фрагмент и обычная View.
 * Панели поверх него собраны на Compose: лист настроек, выезжающее
 * оглавление и ползунок с подсказкой иначе обошлись бы втрое дороже.
 *
 * Системные отступы ставятся здесь: страница книги отодвигается от часов
 * сверху и от полосы жеста снизу, а панели — через windowInsetsPadding.
 * Без этого на телефоне с вырезом первая строка уходит под часы.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity() {

    private val viewModel: ReaderViewModel by viewModels {
        ReaderViewModel.factory(appContainer)
    }

    private var navigator: Navigator? = null
    private var pdfView: PDFView? = null
    private var pdfThumbnails: PdfPageThumbnails? = null
    private var content: ReaderContent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Намеренно не отдаём системе сохранённое состояние фрагментов:
        // навигатор нельзя создать без открытой публикации, а после смерти
        // процесса её ещё нет. Позиция чтения лежит в базе, и книга
        // открывается ровно на ней.
        super.onCreate(null)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_reader)

        applyInsetsToPage()
        setUpOverlay()

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        if (bookId == null) {
            finish()
            return
        }

        viewModel.open(bookId, intent.getStringExtra(EXTRA_LOCATOR))
        observeState()
        observeSurvey()
        observeTheme()
    }

    /**
     * Страница книги отодвигается от системных панелей.
     *
     * Панели поверх неё полупрозрачные и текст перекрывают — так и в макете,
     * но под часы и под полосу жеста текст заходить не должен никогда.
     */
    private fun applyInsetsToPage() {
        val page = findViewById<View>(R.id.reader_container)
        ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun setUpOverlay() {
        findViewById<ComposeView>(R.id.reader_overlay).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                BibliariumTheme {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    val position by viewModel.position.collectAsStateWithLifecycle()
                    val theme by viewModel.theme.collectAsStateWithLifecycle()
                    val palette = ReaderPalette.of(theme)
                    var panelsVisible by remember { mutableStateOf(false) }
                    var settingsOpen by remember { mutableStateOf(false) }
                    var tocOpen by remember { mutableStateOf(false) }
                    val tocIndex by viewModel.tocIndex.collectAsStateWithLifecycle()
                    val settings by viewModel.settings.collectAsStateWithLifecycle()

                    // Экран слушает просьбы показать или спрятать панели:
                    // их шлёт обработчик тапа по центральной трети.
                    panelsRequest = { panelsVisible = it }
                    panelsState = { panelsVisible }
                    settingsRequest = { settingsOpen = true }
                    tocRequest = { tocOpen = true }

                    // Яркость подсветки ставится окну, а не системе: человек
                    // настраивал систему не для нас.
                    LaunchedEffect(settings.brightness) {
                        window.attributes = window.attributes.apply {
                            screenBrightness = settings.brightness
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (val current = state) {
                            is ReaderState.Loading -> Status(
                                text = stringOf(R.string.reader_loading),
                                palette = palette,
                            )

                            is ReaderState.Failed -> Status(
                                text = describe(current.error),
                                palette = palette,
                                onClose = ::finish,
                            )

                            is ReaderState.Ready -> {
                                ReaderChrome(
                                    visible = panelsVisible,
                                    palette = palette,
                                    title = current.content.book.title,
                                    chapter = position.chapter,
                                    progressLabel = progressLabel(position),
                                    progress = position.progress,
                                    actions = actionsFor(current.content),
                                    onSeek = { fraction -> seekTo(current.content, fraction) },
                                    seekHint = { fraction -> seekHint(current.content, fraction) },
                                )

                                if (tocOpen && current.content.tableOfContents.isNotEmpty()) {
                                    ReaderToc(
                                        palette = palette,
                                        bookTitle = current.content.book.title,
                                        entries = current.content.tableOfContents,
                                        currentIndex = tocIndex,
                                        onPick = { index ->
                                            tocOpen = false
                                            if (!goTo(current.content.tableOfContents[index])) {
                                                panelsVisible = false
                                            }
                                        },
                                        onClose = { tocOpen = false },
                                    )
                                }

                                if (settingsOpen) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(SCRIM)
                                            .clickable { settingsOpen = false },
                                    )
                                    ReaderSettingsSheet(
                                        palette = palette,
                                        settings = settings,
                                        forEpub = current.content.engine == ReaderEngine.EPUB,
                                        onFont = viewModel::setFont,
                                        onSize = viewModel::setSize,
                                        onMargins = viewModel::setMargins,
                                        onSpacing = viewModel::setSpacing,
                                        onTheme = viewModel::setTheme,
                                        onBrightness = viewModel::setBrightness,
                                        onClose = { settingsOpen = false },
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .windowInsetsPadding(WindowInsets.navigationBars),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Надпись вместо книги: пока открывается и когда открыть не вышло. */
    @androidx.compose.runtime.Composable
    private fun Status(text: String, palette: ReaderPalette, onClose: (() -> Unit)? = null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .padding(BibliariumTheme.spacing.xl),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                style = BibliariumTheme.type.bodyMd,
                color = palette.text,
                textAlign = TextAlign.Center,
            )
            onClose?.let {
                TextButton(onClick = it) {
                    Text(
                        text = stringOf(R.string.reader_back),
                        style = BibliariumTheme.type.labelLg,
                        color = palette.accent,
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun stringOf(id: Int): String = androidx.compose.ui.res.stringResource(id)

    private fun actionsFor(content: ReaderContent): ReaderActions = ReaderActions(
        onBack = ::finish,
        onToc = { showTableOfContents() },
        onBookmark = { /* закладки появятся вместе с выделениями */ },
        onHighlight = { /* выделение появится в своей части */ },
        onSettings = { settingsRequest() },
        onSearch = { /* поиск по книге появится в своей части */ },
        searchAvailable = content.engine == ReaderEngine.EPUB,
    )

    /** Показать или спрятать панели просит обработчик тапа. */
    private var panelsRequest: (Boolean) -> Unit = {}
    private var settingsRequest: () -> Unit = {}
    private var tocRequest: () -> Unit = {}
    private var panelsState: () -> Boolean = { false }

    private fun togglePanels() {
        panelsRequest(!panelsState())
    }

    private fun setPanelsVisible(visible: Boolean) {
        panelsRequest(visible)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    if (state !is ReaderState.Ready) return@collectLatest
                    if (navigator == null) installNavigator(state.content)
                    content = state.content
                }
            }
        }
    }

    /** Цвет значков системных панелей — по теме чтения, а не наугад. */
    private fun observeTheme() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.theme.collectLatest { theme ->
                    val light = !ReaderPalette.of(theme).lightSystemIcons
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
            }
        }
    }

    /** Книга дочитана — спрашиваем о ней, пока впечатление свежее. */
    private fun observeSurvey() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.surveyDue.collectLatest { bookId ->
                    val id = bookId ?: return@collectLatest
                    startActivity(RatingSurveyActivity.intent(this@ReaderActivity, id))
                    viewModel.surveyShown()
                }
            }
        }
    }

    private fun progressLabel(position: ReadingPosition): String {
        val percent = (position.progress * PERCENT).toInt()

        // У PDF страница настоящая, и её номер человеку виден и полезен.
        val page = position.page
        val total = position.totalPages
        if (page != null && total != null) {
            return getString(R.string.reader_progress_page, page, total, percent)
        }

        val minutes = position.minutesLeft
            ?: return getString(R.string.reader_progress, percent)
        return if (minutes < MINUTES_IN_HOUR) {
            getString(R.string.reader_progress_minutes, percent, minutes)
        } else {
            getString(R.string.reader_progress_hours, percent, minutes / MINUTES_IN_HOUR)
        }
    }

    private fun describe(error: ReaderOpenError): String {
        val base = getString(
            when (error) {
                is ReaderOpenError.FileMissing -> R.string.reader_error_missing
                is ReaderOpenError.Unreadable -> R.string.reader_error_unreadable
                is ReaderOpenError.UnsupportedFormat -> R.string.reader_error_unsupported
                is ReaderOpenError.NotPrepared -> R.string.reader_error_not_prepared
            },
        )
        // Место обрыва показываем прямо на экране: «книга повреждена» без
        // подробностей не даёт ничего ни человеку, ни разбору потом.
        return error.detail?.let { getString(R.string.reader_error_detail, base, it) } ?: base
    }

    // --- навигатор ---------------------------------------------------------

    private fun installNavigator(content: ReaderContent) {
        val fragment = when (content.engine) {
            ReaderEngine.EPUB -> installEpubNavigator(content)
            ReaderEngine.PDF -> installPdfNavigator(content)
        }

        navigator = fragment as Navigator
        attachGestures(fragment)
        observeLocator(fragment as VisualNavigator)
        observePreferences(content, fragment)
    }

    private fun installEpubNavigator(content: ReaderContent): Fragment {
        val factory = EpubNavigatorFactory(content.publication)
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
            initialLocator = content.initialLocator,
            initialPreferences = viewModel.epubPreferences.value,
            configuration = EpubNavigatorFragment.Configuration { declareReadingFonts() },
        )
        supportFragmentManager.commitNow {
            replace(R.id.reader_container, EpubNavigatorFragment::class.java, Bundle(), TAG)
        }
        return supportFragmentManager.findFragmentByTag(TAG)!!
    }

    private fun installPdfNavigator(content: ReaderContent): Fragment {
        val factory = PdfiumNavigatorFactory(content.publication, PdfiumEngineProvider())
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
            initialLocator = content.initialLocator,
            initialPreferences = viewModel.pdfPreferences.value,
        )
        supportFragmentManager.commitNow {
            replace(R.id.reader_container, PdfNavigatorFragment::class.java, Bundle(), TAG)
        }
        val fragment = supportFragmentManager.findFragmentByTag(TAG)!!
        rememberPdfView(fragment)
        observeNightMode(fragment)
        return fragment
    }

    /**
     * Левая треть — назад, правая — вперёд, центральная — панели.
     */
    private fun attachGestures(fragment: Fragment) {
        val overflowable = fragment as? OverflowableNavigator ?: return

        overflowable.addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    val width = overflowable.publicationView.width.toDouble()
                    if (width <= 0) return false

                    // Направление письма учитываем сами: в интерфейсе навигатора
                    // есть только «вперёд» и «назад», а трети — левая и правая.
                    val rightToLeft = overflowable.overflow.value.readingProgression ==
                        ReadingProgression.RTL
                    val x = event.point.x
                    val result = when {
                        x < width / 3 ->
                            if (rightToLeft) {
                                overflowable.goForward(animated = true)
                            } else {
                                overflowable.goBackward(animated = true)
                            }

                        x > width * 2 / 3 ->
                            if (rightToLeft) {
                                overflowable.goBackward(animated = true)
                            } else {
                                overflowable.goForward(animated = true)
                            }

                        else -> {
                            togglePanels()
                            true
                        }
                    }

                    // Листание вперёд не сработало — значит дальше страниц нет
                    // и книга дочитана.
                    if (!result && x > width * 2 / 3) viewModel.onReachedEnd()

                    // Следы жеста: если листание однажды снова перестанет
                    // работать, искать причину будет по чему.
                    android.util.Log.i(
                        GESTURE_TAG,
                        "тап x=$x ширина=$width прокрутка=${overflowable.overflow.value.scroll} " +
                            "страница=${overflowable.currentLocator.value.locations.position} " +
                            "обработано=$result",
                    )
                    return result
                }
            },
        )
    }

    private fun observeLocator(navigator: VisualNavigator) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collectLatest {
                    viewModel.onLocatorChanged(
                        locator = it,
                        // Номер страницы спрашиваем у самого PDFView: локатор
                        // от pdfium в 3.3.0 уходит на страницу вперёд.
                        pdfPage = pdfView?.let { view -> view.currentPage + 1 },
                        pdfPageCount = pdfView?.pageCount,
                    )
                }
            }
        }
    }

    private fun observePreferences(content: ReaderContent, fragment: Fragment) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                when (content.engine) {
                    ReaderEngine.EPUB -> viewModel.epubPreferences.collectLatest { preferences ->
                        (fragment as? EpubNavigatorFragment)?.submitPreferences(preferences)
                    }
                    ReaderEngine.PDF -> viewModel.pdfPreferences.collectLatest { preferences ->
                        (fragment as? PdfiumNavigatorFragment)?.submitPreferences(preferences)
                    }
                }
            }
        }
    }

    // --- оглавление и настройки -------------------------------------------

    /**
     * Оглавление. У книги с закладками — панель слева, у PDF без них —
     * сетка страниц: брать оглавление в скане неоткуда, а листать документ
     * по одной странице не навигация.
     */
    private fun showTableOfContents() {
        val current = content ?: return
        if (current.tableOfContents.isNotEmpty()) {
            tocRequest()
            return
        }

        if (current.engine == ReaderEngine.PDF) {
            showPdfPages(current)
        } else {
            AlertDialog.Builder(this)
                .setMessage(R.string.reader_toc_empty)
                .setPositiveButton(R.string.reader_close, null)
                .show()
        }
    }

    /**
     * Перескок по полосе прогресса.
     *
     * У EPUB место ищется по позициям книги, у PDF — по номеру страницы:
     * и то, и другое Readium считает сам, а делить книгу на равные куски
     * было бы враньём — главы разной длины.
     */
    private fun seekTo(content: ReaderContent, fraction: Float) {
        val share = fraction.coerceIn(0f, 1f)
        if (content.engine == ReaderEngine.PDF) {
            val pages = pdfView?.pageCount ?: return
            jumpToPdfPage((share * pages).toInt().coerceIn(1, pages))
            return
        }
        val target = content.positionAt(share) ?: return
        navigator?.go(target, animated = false)
    }

    /** Что показать в подсказке над пальцем: глава и процент. */
    private fun seekHint(content: ReaderContent, fraction: Float): String {
        val percent = (fraction * PERCENT).toInt()
        val chapter = content.chapterAt(fraction)
        return if (chapter != null) "$chapter · $percent %" else "$percent %"
    }

    private fun showPdfPages(current: ReaderContent) {
        val thumbnails = pdfThumbnails
            ?: PdfPageThumbnails(File(current.book.contentPath)).also { pdfThumbnails = it }

        PdfPagesDialog(
            activity = this,
            thumbnails = thumbnails,
            currentPage = viewModel.position.value.page ?: 1,
            onPick = { page -> jumpToPdfPage(page) },
        ).show()
    }

    /** Возвращает true, если перешли по странице PDF. */
    private fun goTo(entry: TocEntry): Boolean {
        val page = entry.page
        if (page != null && jumpToPdfPage(page)) return true
        navigator?.go(entry.locator, animated = false)
        return false
    }

    /**
     * Переход к странице PDF идёт мимо навигатора, прямо в PDFView.
     *
     * В pdfium-адаптере 3.3.0 номер страницы по дороге уменьшается ещё на
     * единицу, и переход по закладке попадал на страницу раньше нужной
     * (в 3.4.0 этот сдвиг убрали). PDFView считает страницы без сюрпризов.
     */
    private fun jumpToPdfPage(page: Int): Boolean {
        val view = pdfView ?: return false
        if (page < 1 || page > view.pageCount) return false
        view.jumpTo(page - 1, true)
        return true
    }

    // --- PDF ---------------------------------------------------------------

    private fun observeNightMode(fragment: Fragment) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pdfNightMode.collectLatest { night ->
                    val view = fragment.view?.findFirstPdfView() ?: return@collectLatest
                    view.setNightMode(night)
                    view.invalidate()
                }
            }
        }
    }

    /**
     * Запоминает сам PDFView: из него читается номер показанной страницы.
     *
     * Своего обработчика касаний здесь больше нет. PDFView раздаёт жесты через
     * DragPinchManager, а тот сам подписан слушателем касаний, и наш
     * `setOnTouchListener` его снимал: вместе с ним пропадали и перелистывание,
     * и свайп, и щипок. У View слушатель касаний один.
     */
    private fun rememberPdfView(fragment: Fragment) {
        fragment.view?.post {
            pdfView = fragment.view?.findFirstPdfView()
            android.util.Log.i(GESTURE_TAG, "PDFView найден=${pdfView != null}")
        }
    }

    private fun View.findFirstPdfView(): PDFView? {
        if (this is PDFView) return this
        if (this !is ViewGroup) return null
        for (index in 0 until childCount) {
            getChildAt(index).findFirstPdfView()?.let { return it }
        }
        return null
    }

    override fun onDestroy() {
        pdfThumbnails?.close()
        pdfThumbnails = null
        super.onDestroy()
    }

    companion object {
        /** Затемнение под листом настроек. */
        private val SCRIM = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)

        private const val EXTRA_BOOK_ID = "bookId"
        private const val EXTRA_LOCATOR = "locator"
        private const val TAG = "navigator"
        private const val GESTURE_TAG = "BibliariumReader"
        private const val MINUTES_IN_HOUR = 60
        private const val PERCENT = 100

        /**
         * [locator] задаёт место, с которого открыть книгу: по нему приходят
         * из оглавления в карточке. null — открывать там, где закончили.
         */
        fun intent(context: Context, bookId: String, locator: String? = null): Intent =
            Intent(context, ReaderActivity::class.java)
                .putExtra(EXTRA_BOOK_ID, bookId)
                .putExtra(EXTRA_LOCATOR, locator)
    }
}
