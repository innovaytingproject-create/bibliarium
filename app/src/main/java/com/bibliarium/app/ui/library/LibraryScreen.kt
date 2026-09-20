package com.bibliarium.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliarium.app.R
import com.bibliarium.app.data.importer.ImportFailure
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookFailure
import com.bibliarium.app.ui.shelf.ShelfContent
import com.bibliarium.app.ui.theme.BibliariumTheme

/**
 * Библиотека: полка с корешками.
 *
 * Сам экран отвечает только за окружение — сообщения, диалоги и переходы.
 * Всё, что видно человеку на полке, живёт в [ShelfContent].
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onAddBook: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val colors = BibliariumTheme.colors
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var explaining by remember { mutableStateOf<Book?>(null) }
    var menuFor by remember { mutableStateOf<Book?>(null) }

    explaining?.let { book ->
        FailureDialog(
            book = book,
            onRetry = {
                viewModel.retryPreparation(book.id)
                explaining = null
            },
            onDismiss = { explaining = null },
        )
    }

    menuFor?.let { book ->
        BookMenu(
            book = book,
            onOpen = {
                menuFor = null
                openBook(book, onOpenBook) { explaining = it }
            },
            onFavorite = {
                viewModel.toggleFavorite(book)
                menuFor = null
            },
            onDelete = {
                viewModel.delete(book.id)
                menuFor = null
            },
            onDismiss = { menuFor = null },
        )
    }

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val text = when (current) {
            is LibraryMessage.Imported ->
                context.getString(R.string.import_ok, current.title)
            is LibraryMessage.ImportFailed ->
                context.getString(current.failure.messageRes())
            is LibraryMessage.Prepared ->
                context.getString(R.string.library_prepared, current.title)
            is LibraryMessage.PreparationFailed ->
                context.getString(R.string.library_prepare_failed, current.title)
        }
        snackbarHostState.showSnackbar(text)
        viewModel.consumeMessage()
    }

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        ShelfContent(
            books = books,
            isImporting = isImporting,
            onOpenBook = { book -> openBook(book, onOpenBook) { explaining = it } },
            onMenu = { menuFor = it },
            onAddBook = onAddBook,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/** Книга с ошибкой не открывается молча: человеку показывают причину. */
private fun openBook(book: Book, onOpenBook: (String) -> Unit, onExplain: (Book) -> Unit) {
    if (book.isReadable) onOpenBook(book.id) else onExplain(book)
}

/** Меню книги по долгому нажатию на корешок. */
@Composable
private fun BookMenu(
    book: Book,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(text = book.title, style = type.headlineSm, color = colors.text) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                MenuItem(stringResource(R.string.shelf_menu_open), onOpen)
                MenuItem(
                    stringResource(
                        if (book.isFavorite) R.string.shelf_menu_unfavorite
                        else R.string.shelf_menu_favorite,
                    ),
                    onFavorite,
                )
                MenuItem(stringResource(R.string.shelf_menu_delete), onDelete)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.padding(end = spacing.xs)) {
                Text(
                    text = stringResource(R.string.library_close),
                    style = type.labelLg,
                    color = colors.textSecondary,
                )
            }
        },
    )
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = type.bodyMd,
            color = colors.text,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun ImportFailure.messageRes(): Int = when (this) {
    ImportFailure.UNKNOWN_FORMAT -> R.string.import_error_unknown_format
    ImportFailure.UNSUPPORTED_FORMAT -> R.string.import_error_unsupported_format
    ImportFailure.UNREADABLE_FILE -> R.string.import_error_unreadable
    ImportFailure.PARSE_FAILED -> R.string.import_error_parse
    ImportFailure.STORAGE_FAILED -> R.string.import_error_storage
}

/**
 * Книга есть, а открыть её нельзя. Показываем, что именно не получилось,
 * и даём повторить: причина могла быть временной — не хватило места,
 * файл был занят.
 */
@Composable
private fun FailureDialog(
    book: Book,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(text = book.title, style = type.headlineSm, color = colors.text)
        },
        text = {
            Text(
                text = book.openFailureDetail
                    ?: stringResource(book.openFailure.messageRes()),
                style = type.bodyMd,
                color = colors.textSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.library_retry),
                    style = type.labelLg,
                    color = colors.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.library_close),
                    style = type.labelLg,
                    color = colors.textSecondary,
                )
            }
        },
    )
}

private fun BookFailure?.messageRes(): Int = when (this) {
    BookFailure.CONVERSION_FAILED -> R.string.library_failure_conversion
    BookFailure.UNREADABLE -> R.string.library_failure_unreadable
    BookFailure.FILE_MISSING -> R.string.library_failure_missing
    null -> R.string.library_failure_unreadable
}
