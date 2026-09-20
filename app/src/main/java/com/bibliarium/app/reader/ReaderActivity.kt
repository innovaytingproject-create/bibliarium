package com.bibliarium.app.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bibliarium.app.R
import com.bibliarium.app.appContainer
import com.bibliarium.app.ui.theme.BibliariumTheme
import com.bibliarium.app.ui.theme.ThemeVariant
import com.github.barteksc.pdfviewer.PDFView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.adapter.pdfium.navigator.PdfiumEngineProvider
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFactory
import org.readium.adapter.pdfium.navigator.PdfiumNavigatorFragment
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Экран чтения. Содержимое книги показывает навигатор Readium — это фрагмент,
 * поэтому активность здесь обычная, на AppCompat, а не Compose-only.
 * Панели поверх рисует Compose.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderActivity : AppCompatActivity() {

    private val viewModel: ReaderViewModel by viewModels {
        ReaderViewModel.factory(appContainer)
    }

    private var navigator: VisualNavigator? = null
    private var chromeVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        // Намеренно не отдаём системе сохранённое состояние фрагментов.
        // Навигатор нельзя создать без открытой публикации, а после смерти
        // процесса её ещё нет — восстановление упало бы. Терять тут нечего:
        // позиция чтения лежит в базе, и книга открывается ровно на ней.
        super.onCreate(null)

        setContentView(R.layout.activity_reader)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        if (bookId == null) {
            finish()
            return
        }

        findViewById<ComposeView>(R.id.reader_overlay).setContent {
            BibliariumTheme(variant = ThemeVariant.ARCHIVE) {
                ReaderChrome(
                    viewModel = viewModel,
                    visible = chromeVisible,
                    onClose = { finish() },
                )
            }
        }

        viewModel.open(bookId)
        observeState()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    if (state is ReaderState.Ready && navigator == null) {
                        installNavigator(state.content)
                    }
                    // Пока книга не открылась или открыть не вышло, панели
                    // показываем сразу: иначе экран выглядит пустым и мёртвым.
                    if (state !is ReaderState.Ready) {
                        chromeVisible = true
                    }
                }
            }
        }
    }

    private fun installNavigator(content: ReaderContent) {
        val fragment = when (content.engine) {
            ReaderEngine.EPUB -> installEpubNavigator(content)
            ReaderEngine.PDF -> installPdfNavigator(content)
        }

        navigator = fragment as VisualNavigator
        attachGestures(fragment)
        observeLocator(fragment as VisualNavigator)
        observePreferences(content, fragment)
    }

    private fun installEpubNavigator(content: ReaderContent): Fragment {
        val factory = EpubNavigatorFactory(content.publication)
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
            initialLocator = content.initialLocator,
            initialPreferences = viewModel.epubPreferences.value,
            configuration = EpubNavigatorFragment.Configuration {
                declareReadingFonts()
            },
        )
        supportFragmentManager.commitNow {
            replace(
                R.id.reader_container,
                EpubNavigatorFragment::class.java,
                Bundle(),
                NAVIGATOR_TAG,
            )
        }
        return supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)!!
    }

    private fun installPdfNavigator(content: ReaderContent): Fragment {
        val engineProvider = PdfiumEngineProvider()
        val factory = PdfiumNavigatorFactory(content.publication, engineProvider)
        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
            initialLocator = content.initialLocator,
            initialPreferences = viewModel.pdfPreferences.value,
        )
        supportFragmentManager.commitNow {
            replace(
                R.id.reader_container,
                PdfNavigatorFragment::class.java,
                Bundle(),
                NAVIGATOR_TAG,
            )
        }
        val fragment = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG)!!
        keepPdfFitToWidth(fragment)
        observeNightMode(fragment)
        return fragment
    }

    /**
     * Тап по левой трети — назад, по правой — вперёд, по центральной — панели.
     *
     * Первые две трети отдаём DirectionalNavigationAdapter: он уже умеет
     * листать с учётом направления письма. Порог в треть экрана задаётся
     * здесь же, минимальный размер края обнуляется — иначе на узком экране
     * края расползлись бы за пределы трети.
     */
    private fun attachGestures(fragment: Fragment) {
        val overflowable = fragment as? OverflowableNavigator ?: return
        overflowable.addInputListener(
            DirectionalNavigationAdapter(
                navigator = overflowable,
                tapEdges = setOf(DirectionalNavigationAdapter.TapEdge.Horizontal),
                minimumHorizontalEdgeSize = 0.0,
                horizontalEdgeThresholdPercent = ONE_THIRD,
                animatedTransition = true,
            ),
        )

        (fragment as VisualNavigator).addInputListener(
            object : InputListener {
                override fun onTap(event: TapEvent): Boolean {
                    // Сюда долетает только центральная треть: края забрал адаптер выше.
                    chromeVisible = !chromeVisible
                    return true
                }
            },
        )
    }

    private fun observeLocator(navigator: VisualNavigator) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collectLatest { locator ->
                    viewModel.onLocatorChanged(locator)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
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

    /**
     * Ночной режим PDF делает сам PDFView: в настройках Readium такого поля нет,
     * поэтому инверсию применяем к виджету напрямую.
     */
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
     * Щипок для масштаба разрешён, но после отпускания страница возвращается
     * к вписанной. Иначе человек залипает в увеличенной странице: перелистывание
     * в этом состоянии работает не так, и он теряет навигацию.
     */
    private fun keepPdfFitToWidth(fragment: Fragment) {
        fragment.view?.post {
            val pdfView = fragment.view?.findFirstPdfView() ?: return@post
            pdfView.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    pdfView.postDelayed(
                        {
                            if (pdfView.zoom > 1.01f) pdfView.resetZoomWithAnimation()
                        },
                        ZOOM_RESET_DELAY_MS,
                    )
                }
                // Возвращаем false: PDFView должен обработать жест сам.
                false
            }
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

    companion object {
        private const val EXTRA_BOOK_ID = "bookId"
        private const val NAVIGATOR_TAG = "navigator"
        private const val ONE_THIRD = 1.0 / 3
        private const val ZOOM_RESET_DELAY_MS = 150L

        fun intent(context: Context, bookId: String): Intent =
            Intent(context, ReaderActivity::class.java).putExtra(EXTRA_BOOK_ID, bookId)
    }
}
