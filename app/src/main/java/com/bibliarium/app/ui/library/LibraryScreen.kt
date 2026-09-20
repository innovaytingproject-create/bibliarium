package com.bibliarium.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.bibliarium.app.R
import com.bibliarium.app.data.importer.ImportFailure
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookFailure
import com.bibliarium.app.ui.theme.BibliariumTheme

/**
 * Первый этап: книги видны простым списком. Полка с корешками — следующий этап,
 * поэтому здесь сознательно нет ни ярусов, ни сетки.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onAddBook: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var explaining by remember { mutableStateOf<Book?>(null) }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = spacing.margin),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.lg, bottom = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = type.displayLg,
                    color = colors.text,
                )
                Text(
                    text = stringResource(R.string.library_books_count, books.size),
                    style = type.labelMd,
                    color = colors.textSecondary,
                )
            }

            HorizontalDivider(thickness = 1.dp, color = colors.line)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Button(
                    onClick = onAddBook,
                    enabled = !isImporting,
                    shape = RoundedCornerShape(BibliariumTheme.shapes.button),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.text,
                        contentColor = colors.surface,
                    ),
                ) {
                    Text(text = stringResource(R.string.library_add_book), style = type.labelLg)
                }

                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent,
                    )
                    Text(
                        text = stringResource(R.string.library_importing),
                        style = type.bodySm,
                        color = colors.textSecondary,
                    )
                }
            }

            if (books.isEmpty()) {
                EmptyShelf(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(items = books, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            onOpen = {
                                if (book.isReadable) onOpenBook(book.id) else explaining = book
                            },
                            onDelete = { viewModel.delete(book.id) },
                        )
                        HorizontalDivider(thickness = 1.dp, color = colors.line)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyShelf(modifier: Modifier = Modifier) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(
        modifier = modifier.padding(top = spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.library_empty_title),
            style = type.headlineMd,
            color = colors.text,
        )
        Text(
            text = stringResource(R.string.library_empty_hint),
            style = type.bodyMd,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun BookRow(
    book: Book,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        CoverThumbnail(book)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = type.headlineSm,
                color = colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author ?: stringResource(R.string.library_no_author),
                style = type.bodySm,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (book.isReadable) {
                    book.format.name
                } else {
                    stringResource(R.string.library_not_readable, book.format.name)
                },
                style = type.labelSm,
                color = if (book.isReadable) colors.textSecondary else colors.accent,
            )
        }

        TextButton(onClick = onDelete) {
            Text(
                text = stringResource(R.string.library_delete),
                style = type.labelMd,
                color = colors.accent,
            )
        }
    }
}

@Composable
private fun CoverThumbnail(book: Book) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val shape = RoundedCornerShape(BibliariumTheme.shapes.chip)

    Box(
        modifier = Modifier
            .width(44.dp)
            .height(64.dp)
            .clip(shape)
            .background(colors.surfaceRecessed)
            .border(1.dp, colors.line, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (book.coverPath != null) {
            AsyncImage(
                model = "file://" + book.coverPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = book.title.take(1).uppercase(),
                style = type.headlineSm,
                color = colors.textSecondary,
            )
        }
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
