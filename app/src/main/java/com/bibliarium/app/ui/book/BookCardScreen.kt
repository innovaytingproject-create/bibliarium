package com.bibliarium.app.ui.book

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.bibliarium.app.R
import com.bibliarium.app.domain.Book
import com.bibliarium.app.reader.TocEntry
import com.bibliarium.app.ui.shelf.SpineFace
import com.bibliarium.app.ui.shelf.rememberSpineLook
import com.bibliarium.app.ui.theme.BibliariumTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Карточка книги — то, что открывается тапом по корешку.
 *
 * Между полкой и чтением нужен промежуточный экран: отсюда видно, что это
 * за книга, здесь же правятся название и описание, меняется обложка
 * и начинается чтение.
 */
@Composable
fun BookCardScreen(
    viewModel: BookCardViewModel,
    onBack: () -> Unit,
    onRead: (locator: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val quotes by viewModel.quotesCount.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()

    val colors = BibliariumTheme.colors
    val spacing = BibliariumTheme.spacing

    // Уход с экрана — это действие, а не часть отрисовки: иначе возврат
    // случится прямо посреди композиции.
    LaunchedEffect(deleted) { if (deleted) onBack() }
    if (deleted) return

    val current = book ?: return

    var editing by remember { mutableStateOf<Editing?>(null) }
    var showToc by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let(viewModel::setCover) }

    when (val target = editing) {
        is Editing.Name -> {
            NameEditor(
                book = current,
                onSave = { title, author ->
                    viewModel.rename(title, author)
                    editing = null
                },
                onCancel = { editing = null },
                modifier = modifier,
            )
            return
        }

        is Editing.Description -> {
            DescriptionEditor(
                initial = target.text,
                onSave = {
                    viewModel.setDescription(it)
                    editing = null
                },
                onCancel = { editing = null },
                modifier = modifier,
            )
            return
        }

        null -> Unit
    }

    if (showToc) {
        TocDialog(
            entries = details.tableOfContents,
            onPick = { entry ->
                showToc = false
                onRead(entry.locator.toJSON().toString())
            },
            onDismiss = { showToc = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.card_delete_title), color = colors.text) },
            text = {
                Text(
                    text = stringResource(R.string.card_delete_message, current.title),
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete() }) {
                    Text(stringResource(R.string.card_delete), color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.library_close), color = colors.textSecondary)
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        CardTopBar(
            onBack = onBack,
            book = current,
            onReplaceCover = { coverPicker.launch(IMAGE_FILTER) },
            onRemoveCover = { viewModel.setCover(null) },
            onMarkFinished = viewModel::markFinished,
            onToggleFavorite = viewModel::toggleFavorite,
            onDelete = { confirmDelete = true },
        )

        Cover(
            book = current,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = spacing.sm),
        )

        Titles(book = current, modifier = Modifier.padding(top = spacing.md))

        Facts(
            book = current,
            quotes = quotes,
            pages = details.pages,
            modifier = Modifier.padding(top = spacing.lg),
        )

        FileLine(book = current, modifier = Modifier.padding(top = spacing.sm))

        ReadButton(
            book = current,
            onRead = { onRead(null) },
            modifier = Modifier.padding(horizontal = spacing.margin, vertical = spacing.lg),
        )

        Description(
            text = current.description,
            onEdit = { editing = Editing.Description(current.description.orEmpty()) },
            modifier = Modifier.padding(horizontal = spacing.margin),
        )

        Actions(
            quotes = quotes,
            hasToc = details.tableOfContents.isNotEmpty(),
            onToc = { showToc = true },
            onQuotes = { /* список цитат появится вместе с выделениями */ },
            onRename = { editing = Editing.Name },
            modifier = Modifier.padding(top = spacing.lg),
        )

        current.genre?.takeIf { it.isNotBlank() }?.let { genre ->
            Genres(genre = genre, modifier = Modifier.padding(top = spacing.lg))
        }

        TextButton(
            onClick = { confirmDelete = true },
            modifier = Modifier
                .padding(start = spacing.sm, top = spacing.xl, bottom = spacing.xxl),
        ) {
            Text(
                text = stringResource(R.string.card_delete_book),
                style = BibliariumTheme.type.labelLg,
                color = colors.accent,
            )
        }
    }
}

private sealed interface Editing {
    data object Name : Editing
    data class Description(val text: String) : Editing
}

@Composable
private fun CardTopBar(
    onBack: () -> Unit,
    book: Book,
    onReplaceCover: () -> Unit,
    onRemoveCover: () -> Unit,
    onMarkFinished: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing
    var menu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.lg, start = spacing.sm, end = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = stringResource(R.string.card_back),
                style = type.labelMd,
                color = colors.text,
            )
        }

        Box {
            TextButton(onClick = { menu = true }) {
                Text(text = MENU_DOTS, style = type.labelLg, color = colors.text)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(R.string.card_cover_replace) {
                    menu = false
                    onReplaceCover()
                }
                if (book.customCoverPath != null) {
                    MenuItem(R.string.card_cover_remove) {
                        menu = false
                        onRemoveCover()
                    }
                }
                MenuItem(R.string.card_mark_finished) {
                    menu = false
                    onMarkFinished()
                }
                MenuItem(
                    if (book.isFavorite) R.string.shelf_menu_unfavorite
                    else R.string.shelf_menu_favorite,
                ) {
                    menu = false
                    onToggleFavorite()
                }
                MenuItem(R.string.card_delete_book) {
                    menu = false
                    onDelete()
                }
            }
        }
    }
}

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    val colors = BibliariumTheme.colors
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes),
                style = BibliariumTheme.type.bodyMd,
                color = colors.text,
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun Cover(book: Book, modifier: Modifier = Modifier) {
    val shapes = BibliariumTheme.shapes
    val cover = book.coverToShow
    val look = rememberSpineLook(book.title, book.author)

    Box(
        modifier = modifier
            .width(COVER_WIDTH.dp)
            .height(COVER_HEIGHT.dp)
            .clip(RoundedCornerShape(shapes.card)),
    ) {
        if (cover != null) {
            AsyncImage(
                model = "file://$cover",
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Обложки нет — тот же корешок, растянутый под пропорции обложки.
            SpineFace(
                title = book.title,
                author = book.author,
                look = look,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Titles(book: Book, modifier: Modifier = Modifier) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.margin),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        book.author?.takeIf { it.isNotBlank() }?.let { author ->
            Text(
                text = author,
                style = type.labelMd,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = book.title,
            style = type.headlineMd,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

@Composable
private fun Facts(book: Book, quotes: Int, pages: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Fact(
            value = stringResource(R.string.card_percent, (book.progress * PERCENT).toInt()),
            label = stringResource(R.string.card_read),
        )
        Fact(value = quotes.toString(), label = stringResource(R.string.card_quotes))
        Fact(
            value = if (pages > 0) pages.toString() else DASH,
            label = stringResource(R.string.card_pages),
        )
    }
}

@Composable
private fun Fact(value: String, label: String) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = type.headlineSm, color = colors.text)
        Text(text = label, style = type.labelSm, color = colors.textSecondary)
    }
}

@Composable
private fun FileLine(book: Book, modifier: Modifier = Modifier) {
    val colors = BibliariumTheme.colors
    val context = LocalContext.current
    val size = remember(book.fileSize) {
        Formatter.formatShortFileSize(context, book.fileSize)
    }
    val added = remember(book.addedAt) {
        DATE_FORMAT.format(Instant.ofEpochMilli(book.addedAt).atZone(ZoneId.systemDefault()))
    }

    Text(
        text = stringResource(R.string.card_file_line, book.format.name, size, added),
        style = BibliariumTheme.type.labelSm,
        color = colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ReadButton(book: Book, onRead: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    Button(
        onClick = onRead,
        enabled = book.isReadable,
        shape = RoundedCornerShape(BibliariumTheme.shapes.button),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(
                if (book.progress > 0f) R.string.card_continue else R.string.card_start,
            ),
            style = type.labelLg,
        )
    }
}

@Composable
private fun Description(text: String?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    var expanded by remember { mutableStateOf(false) }

    if (text.isNullOrBlank()) {
        TextButton(onClick = onEdit, modifier = modifier) {
            Text(
                text = stringResource(R.string.card_add_description),
                style = type.labelMd,
                color = colors.accent,
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = type.bodyMd,
            color = colors.text,
            maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { expanded = !expanded },
        )
        Row {
            TextButton(onClick = { expanded = !expanded }) {
                Text(
                    text = stringResource(
                        if (expanded) R.string.card_less else R.string.card_more,
                    ),
                    style = type.labelMd,
                    color = colors.accent,
                )
            }
            TextButton(onClick = onEdit) {
                Text(
                    text = stringResource(R.string.card_edit_description),
                    style = type.labelMd,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Actions(
    quotes: Int,
    hasToc: Boolean,
    onToc: () -> Unit,
    onQuotes: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (hasToc) {
            ActionRow(stringResource(R.string.card_toc), null, onToc)
        }
        ActionRow(stringResource(R.string.card_quotes_and_notes), quotes.toString(), onQuotes)
        ActionRow(stringResource(R.string.card_edit_name), null, onRename)
    }
}

@Composable
private fun ActionRow(label: String, trailing: String?, onClick: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = spacing.margin, vertical = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = type.bodyMd, color = colors.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailing?.let {
                    Text(
                        text = it,
                        style = type.labelMd,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(end = spacing.sm),
                    )
                }
                Text(text = CHEVRON, style = type.labelMd, color = colors.textSecondary)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.line)
    }
}

@Composable
private fun Genres(genre: String, modifier: Modifier = Modifier) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(modifier = modifier.padding(horizontal = spacing.margin)) {
        Text(
            text = stringResource(R.string.card_genres),
            style = type.labelSm,
            color = colors.textSecondary,
        )
        Text(
            text = genre,
            style = type.bodyMd,
            color = colors.text,
            modifier = Modifier
                .padding(top = spacing.xs)
                .clip(RoundedCornerShape(BibliariumTheme.shapes.chip))
                .background(colors.surfaceRecessed)
                .padding(horizontal = spacing.sm, vertical = spacing.xs),
        )
    }
}

@Composable
private fun TocDialog(
    entries: List<TocEntry>,
    onPick: (TocEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = BibliariumTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.card_toc), color = colors.text) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                entries.forEach { entry ->
                    Text(
                        text = entry.title,
                        style = BibliariumTheme.type.bodyMd,
                        color = colors.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(entry) }
                            .padding(vertical = BibliariumTheme.spacing.sm),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.library_close), color = colors.textSecondary)
            }
        },
    )
}

@Composable
private fun NameEditor(
    book: Book,
    onSave: (String, String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BibliariumTheme.colors
    val spacing = BibliariumTheme.spacing
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author.orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(spacing.margin),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.card_edit_name),
            style = BibliariumTheme.type.headlineSm,
            color = colors.text,
        )
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(stringResource(R.string.card_title_label)) },
            singleLine = false,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = author,
            onValueChange = { author = it },
            label = { Text(stringResource(R.string.card_author_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(
                onClick = { onSave(title.trim(), author.trim().takeIf { it.isNotEmpty() }) },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(stringResource(R.string.card_save))
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.card_cancel), color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun DescriptionEditor(
    initial: String,
    onSave: (String?) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BibliariumTheme.colors
    val spacing = BibliariumTheme.spacing
    var text by remember { mutableStateOf(initial) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(spacing.margin),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.card_description_title),
            style = BibliariumTheme.type.headlineSm,
            color = colors.text,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(EDITOR_HEIGHT.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(
                onClick = { onSave(text.trim().takeIf { it.isNotEmpty() }) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text(stringResource(R.string.card_save))
            }
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.card_cancel), color = colors.textSecondary)
            }
        }
    }
}

private const val COVER_WIDTH = 150
private const val COVER_HEIGHT = 225
private const val COLLAPSED_LINES = 4
private const val PERCENT = 100
private const val EDITOR_HEIGHT = 220
private const val IMAGE_FILTER = "image/*"
private const val MENU_DOTS = "•••"
private const val CHEVRON = "›"
private const val DASH = "—"
// Локаль задаётся явно: без неё месяц выводился по-английски даже на
// русском интерфейсе.
private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
