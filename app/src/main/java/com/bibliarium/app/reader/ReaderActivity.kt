package com.bibliarium.app.reader

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bibliarium.app.R
import com.bibliarium.app.appContainer
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
 * Содержимое книги показывает навигатор Readium — это фрагмент. Панели
 * сделаны обычными View по образцу демо-приложения Readium (BSD 3-Clause,
 * копия лицензии в licenses/). Наложение на Compose поверх фрагмента
 * перерисовывалось не всегда, и человек оставался на экране «Открываем
 * книгу…» без единой кнопки.
 *
 * Панели показаны сразу после открытия и прячутся тапом по центру:
 * застрять в книге без выхода нельзя.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity() {

    private val viewModel: ReaderViewModel by viewModels {
        ReaderViewModel.factory(appContainer)
    }

    private lateinit var topBar: View
    private lateinit var bottomBar: View
    private lateinit var status: View
    private lateinit var statusText: TextView
    private lateinit var titleView: TextView
    private lateinit var progressView: TextView
    private lateinit var tocButton: Button

    private var navigator: Navigator? = null
    private var pdfView: PDFView? = null
    private var pdfThumbnails: PdfPageThumbnails? = null
    private var content: ReaderContent? = null
    private var panelsVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        // Намеренно не отдаём системе сохранённое состояние фрагментов:
        // навигатор нельзя создать без открытой публикации, а после смерти
        // процесса её ещё нет. Позиция чтения лежит в базе, и книга
        // открывается ровно на ней.
        super.onCreate(null)
        setContentView(R.layout.activity_reader)

        topBar = findViewById(R.id.reader_top_bar)
        bottomBar = findViewById(R.id.reader_bottom_bar)
        status = findViewById(R.id.reader_status)
        statusText = findViewById(R.id.reader_status_text)
        titleView = findViewById(R.id.reader_title)
        progressView = findViewById(R.id.reader_progress)
        tocButton = findViewById(R.id.reader_toc)

        findViewById<Button>(R.id.reader_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.reader_status_close).setOnClickListener { finish() }
        tocButton.setOnClickListener { showTableOfContents() }
        findViewById<Button>(R.id.reader_settings).setOnClickListener { showSettings() }

        showStatus(getString(R.string.reader_loading))
        setPanelsVisible(false)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        if (bookId == null) {
            finish()
            return
        }

        viewModel.open(bookId)
        observeState()
        observePosition()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    when (state) {
                        is ReaderState.Loading ->
                            showStatus(getString(R.string.reader_loading))

                        is ReaderState.Failed ->
                            showStatus(describe(state.error))

                        is ReaderState.Ready -> {
                            if (navigator == null) {
                                installNavigator(state.content)
                            }
                            content = state.content
                            titleView.text = state.content.book.title
                            setUpTocButton(state.content)
                            hideStatus()
                            setPanelsVisible(true)
                        }
                    }
                }
            }
        }
    }

    private fun observePosition() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.position.collectLatest { position ->
                    progressView.text = progressLabel(position)
                }
            }
        }
    }

    private fun progressLabel(position: ReadingPosition): String {
        val percent = (position.progress * 100).toInt()

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
     *
     * Края отданы DirectionalNavigationAdapter: он уже умеет листать с учётом
     * направления письма. Порог в треть задаётся здесь, минимальный размер
     * края обнуляется — иначе на узком экране края вышли бы за треть.
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
                            setPanelsVisible(!panelsVisible)
                            true
                        }
                    }

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

    // --- панели ------------------------------------------------------------

    private fun setPanelsVisible(visible: Boolean) {
        panelsVisible = visible
        val mode = if (visible) View.VISIBLE else View.GONE
        topBar.visibility = mode
        bottomBar.visibility = mode
    }

    private fun showStatus(text: String) {
        statusText.text = text
        status.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        status.visibility = View.GONE
    }

    /**
     * Кнопка называет то, что откроется: у книги с оглавлением — «Оглавление»,
     * у PDF без закладок — «Страницы». Серую кнопку или пустое окно человек
     * читает как поломку, а PDF без закладок — обычное дело: скан это картинки,
     * структуры внутри нет.
     */
    private fun setUpTocButton(content: ReaderContent) {
        val hasToc = content.tableOfContents.isNotEmpty()
        when {
            hasToc -> {
                tocButton.setText(R.string.reader_toc)
                tocButton.visibility = View.VISIBLE
            }

            content.engine == ReaderEngine.PDF -> {
                tocButton.setText(R.string.reader_pages)
                tocButton.visibility = View.VISIBLE
            }

            else -> tocButton.visibility = View.GONE
        }
    }

    private fun showTableOfContents() {
        val current = content ?: return
        val entries = current.tableOfContents

        if (entries.isEmpty()) {
            if (current.engine == ReaderEngine.PDF) {
                showPdfPages(current)
            } else {
                AlertDialog.Builder(this)
                    .setMessage(R.string.reader_toc_empty)
                    .setPositiveButton(R.string.reader_close, null)
                    .show()
            }
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.reader_toc)
            .setItems(entries.map { it.title }.toTypedArray()) { _, index ->
                // У PDF панели остаются: номер страницы — единственный отклик
                // на переход, картинку страницы прочитать нельзя.
                if (!goTo(entries[index])) setPanelsVisible(false)
            }
            .show()
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

    private fun showSettings() {
        val current = content ?: return
        ReaderSettingsDialog(this, viewModel, current.engine).show()
    }

    // --- PDF ---------------------------------------------------------------

    private fun observeNightMode(fragment: Fragment) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pdfNightMode.collectLatest { night ->
                    val pdfView = fragment.view?.findFirstPdfView() ?: return@collectLatest
                    pdfView.setNightMode(night)
                    pdfView.invalidate()
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
        private const val EXTRA_BOOK_ID = "bookId"
        private const val TAG = "navigator"
        private const val GESTURE_TAG = "BibliariumReader"
        private const val MINUTES_IN_HOUR = 60

        fun intent(context: Context, bookId: String): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
