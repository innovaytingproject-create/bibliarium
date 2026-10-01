package com.bibliarium.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bibliarium.app.AppContainer
import com.bibliarium.app.appContainer
import com.bibliarium.app.reader.ReaderActivity
import com.bibliarium.app.ui.add.AddBookScreen
import com.bibliarium.app.ui.book.BookCardScreen
import com.bibliarium.app.ui.book.BookCardViewModel
import com.bibliarium.app.ui.library.LibraryScreen
import com.bibliarium.app.ui.library.LibraryViewModel
import com.bibliarium.app.ui.scan.ScanScreen
import com.bibliarium.app.ui.scan.ScanViewModel
import com.bibliarium.app.ui.settings.SettingsScreen
import com.bibliarium.app.ui.settings.SettingsViewModel
import com.bibliarium.app.ui.theme.BibliariumTheme
import com.bibliarium.app.ui.theme.ThemeVariant

/**
 * Экранов пока три, поэтому навигация обходится состоянием и BackHandler'ом.
 * Полноценный граф появится на втором этапе вместе с нижней навигацией.
 */
private enum class Screen {
    LIBRARY,
    BOOK_CARD,
    ADD_BOOK,
    SCAN,
    SETTINGS,
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = appContainer

        setContent {
            // Переключатель тем появится в настройках на шестом этапе.
            BibliariumTheme(variant = ThemeVariant.ARCHIVE) {
                BibliariumApp(container)
            }
        }
    }
}

@Composable
private fun BibliariumApp(container: AppContainer) {
    var screen by rememberSaveable { mutableStateOf(Screen.LIBRARY) }
    // Настройки открываются и с полки, и из «Добавить книгу»: возвращаться
    // надо туда, откуда пришли.
    var settingsOrigin by rememberSaveable { mutableStateOf(Screen.ADD_BOOK) }
    var openedBookId by rememberSaveable { mutableStateOf<String?>(null) }

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(container),
    )

    BackHandler(enabled = screen != Screen.LIBRARY) {
        screen = when (screen) {
            Screen.SCAN -> Screen.ADD_BOOK
            Screen.SETTINGS -> settingsOrigin
            else -> Screen.LIBRARY
        }
    }

    when (screen) {
        Screen.LIBRARY -> {
            val context = LocalContext.current
            LibraryScreen(
                viewModel = libraryViewModel,
                onAddBook = { screen = Screen.ADD_BOOK },
                // Тап по корешку ведёт в карточку, а не сразу в чтение:
                // человеку сначала надо увидеть, что это за книга.
                onOpenBook = { bookId ->
                    openedBookId = bookId
                    screen = Screen.BOOK_CARD
                },
                onOpenSettings = {
                    settingsOrigin = Screen.LIBRARY
                    screen = Screen.SETTINGS
                },
            )
        }

        Screen.BOOK_CARD -> {
            val context = LocalContext.current
            val bookId = openedBookId
            if (bookId == null) {
                screen = Screen.LIBRARY
            } else {
                val cardViewModel: BookCardViewModel = viewModel(
                    key = bookId,
                    factory = BookCardViewModel.factory(container, bookId),
                )
                BookCardScreen(
                    viewModel = cardViewModel,
                    onBack = { screen = Screen.LIBRARY },
                    onRead = { locator ->
                        context.startActivity(
                            ReaderActivity.intent(context, bookId, locator),
                        )
                    },
                )
            }
        }

        Screen.ADD_BOOK -> AddBookScreen(
            onScan = { screen = Screen.SCAN },
            onOpenSettings = {
                settingsOrigin = Screen.ADD_BOOK
                screen = Screen.SETTINGS
            },
            onFilePicked = { uri ->
                libraryViewModel.import(uri)
                screen = Screen.LIBRARY
            },
            onBack = { screen = Screen.LIBRARY },
        )

        Screen.SCAN -> {
            val scanViewModel: ScanViewModel = viewModel(
                factory = ScanViewModel.factory(container),
            )
            ScanScreen(
                viewModel = scanViewModel,
                onBack = { screen = Screen.ADD_BOOK },
                onOpenSettings = {
                    settingsOrigin = Screen.SCAN
                    screen = Screen.SETTINGS
                },
            )
        }

        Screen.SETTINGS -> {
            val context = LocalContext.current
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(context, container),
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { screen = settingsOrigin },
            )
        }
    }
}
