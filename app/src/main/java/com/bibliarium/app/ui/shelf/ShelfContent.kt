package com.bibliarium.app.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bibliarium.app.R
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.ReadingStatus
import com.bibliarium.app.ui.theme.BibliariumTheme

/**
 * Полка: ярусы корешков или сетка обложек.
 *
 * Корешки рисуются кодом (см. [BookSpine]), обложка из файла идёт только
 * в сетку. Так полка выглядит цельной даже тогда, когда обложек нет ни у
 * одной книги — а это обычное дело для FB2 и сканов.
 */
@Composable
fun ShelfContent(
    books: List<Book>,
    isImporting: Boolean,
    onOpenBook: (Book) -> Unit,
    onMenu: (Book) -> Unit,
    onAddBook: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BibliariumTheme.colors
    val spacing = BibliariumTheme.spacing

    var mode by rememberSaveable { mutableStateOf(ShelfMode.SPINES) }
    var grouping by rememberSaveable { mutableStateOf(ShelfGrouping.STATUS) }
    var filter by rememberSaveable { mutableStateOf(ShelfFilter.ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    val statusNames = mapOf(
        ReadingStatus.READING to stringResource(R.string.shelf_status_reading),
        ReadingStatus.NOT_STARTED to stringResource(R.string.shelf_status_not_started),
        ReadingStatus.FINISHED to stringResource(R.string.shelf_status_finished),
    )
    val unknownAuthor = stringResource(R.string.shelf_unknown_author)
    val unknownGenre = stringResource(R.string.shelf_unknown_genre)

    val tiers = remember(books, grouping, filter, query) {
        buildTiers(
            books = books,
            grouping = grouping,
            filter = filter,
            query = query,
            unknownAuthor = unknownAuthor,
            unknownGenre = unknownGenre,
            statusNames = statusNames,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ShelfHeader(
            booksCount = books.size,
            mode = mode,
            onModeChange = { mode = it },
            grouping = grouping,
            onGroupingChange = { grouping = it },
            searching = searching,
            query = query,
            onQueryChange = { query = it },
        )

        FilterRow(filter = filter, onFilterChange = { filter = it })

        if (isImporting) {
            Text(
                text = stringResource(R.string.library_importing),
                style = BibliariumTheme.type.bodySm,
                color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = spacing.margin),
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            val openTier = expanded?.let { key -> tiers.firstOrNull { it.key == key } }
            when {
                openTier != null -> TierList(
                    tier = openTier,
                    onOpenBook = onOpenBook,
                    onMenu = onMenu,
                    onBack = { expanded = null },
                )

                books.isEmpty() -> EmptyLibrary()

                tiers.isEmpty() -> NothingFound()

                mode == ShelfMode.GRID -> CoverGrid(
                    books = tiers.flatMap { it.books },
                    onOpenBook = onOpenBook,
                    onMenu = onMenu,
                )

                else -> Tiers(
                    tiers = tiers,
                    books = books,
                    onOpenBook = onOpenBook,
                    onMenu = onMenu,
                    onExpand = { expanded = it },
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = colors.line)
        BottomNavigation(
            onLibrary = {
                expanded = null
                filter = ShelfFilter.ALL
                query = ""
                searching = false
            },
            onSearch = { searching = !searching },
            onAdd = onAddBook,
            onMore = onOpenSettings,
            modifier = Modifier.padding(vertical = spacing.xs),
        )
    }
}

@Composable
private fun ShelfHeader(
    booksCount: Int,
    mode: ShelfMode,
    onModeChange: (ShelfMode) -> Unit,
    grouping: ShelfGrouping,
    onGroupingChange: (ShelfGrouping) -> Unit,
    searching: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(modifier = Modifier.padding(horizontal = spacing.margin)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.lg, bottom = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = type.displayLg,
                color = colors.text,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Группировка ушла под значок: строкой вкладок она повторяла
                // фильтры — «По статусу» и «Читаю/Прочитано» про одно и то же.
                GroupingMenu(grouping = grouping, onGroupingChange = onGroupingChange)
                ModeSwitch(mode = mode, onModeChange = onModeChange)
            }
        }

        Text(
            text = stringResource(R.string.library_books_count, booksCount),
            style = type.labelSm,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = spacing.sm),
        )

        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.sm),
                placeholder = {
                    Text(
                        text = stringResource(R.string.shelf_search_hint),
                        style = type.bodySm,
                        color = colors.textSecondary,
                    )
                },
            )
        }
    }
}

/** Группировка ярусов: значок в шапке и список под ним. */
@Composable
private fun GroupingMenu(grouping: ShelfGrouping, onGroupingChange: (ShelfGrouping) -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }) {
            ShelfIconImage(
                icon = ShelfIcon.TUNE,
                tint = colors.text,
                modifier = Modifier.semantics {
                    contentDescription = GROUPING_DESCRIPTION
                },
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ShelfGrouping.entries.forEach { value ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(value.labelRes()),
                            style = type.bodyMd,
                            color = if (value == grouping) colors.accent else colors.text,
                        )
                    },
                    onClick = {
                        onGroupingChange(value)
                        open = false
                    },
                )
            }
        }
    }
}

/** Переключатель корешки/сетка — значком, как на макете. */
@Composable
private fun ModeSwitch(mode: ShelfMode, onModeChange: (ShelfMode) -> Unit) {
    val colors = BibliariumTheme.colors
    val shapes = BibliariumTheme.shapes

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(shapes.chip))
            .background(colors.surfaceRecessed),
    ) {
        listOf(ShelfMode.SPINES to ShelfIcon.SPINES, ShelfMode.GRID to ShelfIcon.GRID)
            .forEach { (value, icon) ->
                IconButton(onClick = { onModeChange(value) }) {
                    ShelfIconImage(
                        icon = icon,
                        tint = if (value == mode) colors.accent else colors.textSecondary,
                        modifier = Modifier.semantics {
                            contentDescription = if (value == ShelfMode.SPINES) {
                                SPINES_DESCRIPTION
                            } else {
                                GRID_DESCRIPTION
                            }
                        },
                    )
                }
            }
    }
}

private fun ShelfGrouping.labelRes(): Int = when (this) {
    ShelfGrouping.STATUS -> R.string.shelf_group_status
    ShelfGrouping.AUTHOR -> R.string.shelf_group_author
    ShelfGrouping.GENRE -> R.string.shelf_group_genre
}

@Composable
private fun FilterRow(filter: ShelfFilter, onFilterChange: (ShelfFilter) -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    // Строка прокручивается вбок: «Избранное» иначе переносится по слогам
    // и выглядит как поломка вёрстки.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.margin),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        ShelfFilter.entries.forEach { value ->
            val active = value == filter
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = spacing.xs),
            ) {
                TextButton(
                    onClick = { onFilterChange(value) },
                    contentPadding = PaddingValues(horizontal = spacing.sm),
                ) {
                    Text(
                        text = stringResource(value.labelRes()),
                        style = type.labelMd,
                        color = if (active) colors.text else colors.textSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                // Активный фильтр подчёркнут акцентом — как на макете.
                Box(
                    modifier = Modifier
                        .padding(horizontal = spacing.sm)
                        .fillMaxWidth()
                        .height(UNDERLINE_HEIGHT.dp)
                        .background(if (active) colors.accent else Color.Transparent),
                )
            }
        }
    }
}

private fun ShelfFilter.labelRes(): Int = when (this) {
    ShelfFilter.ALL -> R.string.shelf_filter_all
    ShelfFilter.READING -> R.string.shelf_filter_reading
    ShelfFilter.FINISHED -> R.string.shelf_filter_finished
    ShelfFilter.FAVORITE -> R.string.shelf_filter_favorite
}

@Composable
private fun Tiers(
    tiers: List<ShelfTier>,
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onMenu: (Book) -> Unit,
    onExpand: (String) -> Unit,
) {
    val spacing = BibliariumTheme.spacing
    val reading = remember(books) { currentlyReading(books) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.lg),
    ) {
        itemsIndexed(items = tiers, key = { _, tier -> tier.key }) { index, tier ->
            Tier(
                label = tierLabel(index, tier.name),
                tier = tier,
                onOpenBook = onOpenBook,
                onMenu = onMenu,
                onExpand = { onExpand(tier.key) },
            )
        }

        if (reading != null) {
            item(key = "now-reading") {
                NowReadingCard(book = reading, onContinue = { onOpenBook(reading) })
            }
        }
    }
}

/** Ярусы называются буквами: «Ярус A · Классика». */
private fun tierLabel(index: Int, name: String): String {
    val letter = ('A' + index % TIER_LETTERS)
    return "$letter · $name"
}

@Composable
private fun Tier(
    label: String,
    tier: ShelfTier,
    onOpenBook: (Book) -> Unit,
    onMenu: (Book) -> Unit,
    onExpand: () -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.margin, end = spacing.margin, top = spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.shelf_tier, label).uppercase(),
                style = type.labelSm,
                color = colors.textSecondary,
            )
            if (tier.books.size > TIER_LIMIT) {
                TextButton(onClick = onExpand) {
                    Text(
                        text = stringResource(R.string.shelf_all, tier.books.size),
                        style = type.labelSm,
                        color = colors.accent,
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.library_books_count, tier.books.size),
                    style = type.labelSm,
                    color = colors.textSecondary,
                )
            }
        }

        val shown = remember(tier.books) { tier.books.take(TIER_LIMIT) }
        val looks = rememberRowLooks(
            remember(shown) { shown.map { SpineKey(it.title, it.author) } },
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = spacing.margin,
            ),
            horizontalArrangement = Arrangement.spacedBy(spacing.gutterShelf),
            verticalAlignment = Alignment.Bottom,
        ) {
            itemsIndexed(items = shown, key = { _, book -> book.id }) { index, book ->
                SpineOnShelf(
                    book = book,
                    look = looks[index],
                    onOpen = { onOpenBook(book) },
                    onMenu = { onMenu(book) },
                )
            }
        }

        // Доска полки из DESIGN.md: светлая линия сверху, сама доска и мягкая
        // тень под ней. Всё одним Canvas — на узел разметки в каждом ярусе
        // меньше, а тень тут не настоящая elevation, а именно рисунок.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height((SHELF_BOARD_HEIGHT + SHELF_SHADOW_HEIGHT).dp),
        ) {
            val board = size.height * SHELF_BOARD_SHARE
            drawRect(color = colors.shelfBoard, size = Size(size.width, board))
            drawLine(
                color = colors.shelfBoardEdge,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1f,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0f to colors.shelfBoardEdge.copy(alpha = SHADOW_ALPHA),
                    1f to Color.Transparent,
                    startY = board,
                    endY = size.height,
                ),
                topLeft = Offset(0f, board),
                size = Size(size.width, size.height - board),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpineOnShelf(
    book: Book,
    look: SpineLook,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    Box(
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onMenu),
    ) {
        BookSpine(title = book.title, author = book.author, look = look)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CoverGrid(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onMenu: (Book) -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing
    val shapes = BibliariumTheme.shapes

    LazyVerticalGrid(
        columns = GridCells.Adaptive(GRID_CELL_WIDTH.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.margin),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        items(items = books, key = { it.id }) { book ->
            Column(
                modifier = Modifier.combinedClickable(
                    onClick = { onOpenBook(book) },
                    onLongClick = { onMenu(book) },
                ),
            ) {
                val cover = book.coverPath
                val look = rememberSpineLook(book.title, book.author)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(GRID_COVER_HEIGHT.dp)
                        .clip(RoundedCornerShape(shapes.card)),
                ) {
                    if (cover != null) {
                        AsyncImage(
                            model = "file://" + cover,
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Обложки нет — показываем тот же корешок, но во всю клетку.
                        SpineFace(
                            title = book.title,
                            author = book.author,
                            look = look,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = book.title,
                    style = type.bodySm,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xs),
                )
                Text(
                    text = book.author.orEmpty(),
                    style = type.labelSm,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TierList(
    tier: ShelfTier,
    onOpenBook: (Book) -> Unit,
    onMenu: (Book) -> Unit,
    onBack: () -> Unit,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.margin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = stringResource(R.string.shelf_back),
                    style = type.labelMd,
                    color = colors.accent,
                )
            }
            Text(text = tier.name, style = type.headlineSm, color = colors.text)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items = tier.books, key = { it.id }) { book ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpenBook(book) },
                            onLongClick = { onMenu(book) },
                        )
                        .padding(horizontal = spacing.margin, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SpineFace(
                        title = book.title,
                        author = book.author,
                        look = rememberSpineLook(book.title, book.author),
                        modifier = Modifier
                            .width(ROW_SPINE_WIDTH.dp)
                            .height(ROW_SPINE_HEIGHT.dp),
                    )
                    Column(modifier = Modifier.padding(start = spacing.md)) {
                        Text(
                            text = book.title,
                            style = type.bodyMd,
                            color = colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = book.author.orEmpty(),
                            style = type.labelSm,
                            color = colors.textSecondary,
                        )
                    }
                }
                HorizontalDivider(thickness = 1.dp, color = colors.line)
            }
        }
    }
}

@Composable
private fun NowReadingCard(book: Book, onContinue: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing
    val shapes = BibliariumTheme.shapes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(spacing.margin)
            .clip(RoundedCornerShape(shapes.card))
            .background(colors.surface)
            .padding(spacing.md),
    ) {
        Text(
            text = stringResource(R.string.shelf_now_reading),
            style = type.labelSm,
            color = colors.textSecondary,
        )
        Row(
            modifier = Modifier.padding(top = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpineFace(
                title = book.title,
                author = book.author,
                look = rememberSpineLook(book.title, book.author),
                modifier = Modifier
                    .width(ROW_SPINE_WIDTH.dp)
                    .height(ROW_SPINE_HEIGHT.dp),
            )
            Column(modifier = Modifier.padding(start = spacing.md)) {
                Text(
                    text = book.title,
                    style = type.bodyMd,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author.orEmpty(),
                    style = type.labelSm,
                    color = colors.textSecondary,
                )
                LinearProgressIndicator(
                    progress = { book.progress },
                    color = colors.accent,
                    trackColor = colors.surfaceRecessed,
                    // Точку в конце полосы Material рисует сам, и человек
                    // читает её как поломку: убираем.
                    drawStopIndicator = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.xs),
                )
            }
        }
        TextButton(onClick = onContinue, modifier = Modifier.padding(top = spacing.sm)) {
            Text(
                text = stringResource(R.string.shelf_continue),
                style = type.labelLg,
                color = colors.accent,
            )
        }
    }
}

/** Книг нет вовсе — это не «ничего не нашлось», а начало работы. */
@Composable
private fun EmptyLibrary() {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
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
private fun NothingFound() {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.shelf_nothing_found),
            style = type.bodyMd,
            color = colors.textSecondary,
            modifier = Modifier.padding(spacing.lg),
        )
    }
}

@Composable
private fun BottomNavigation(
    onLibrary: () -> Unit,
    onSearch: () -> Unit,
    onAdd: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(
            Triple(R.string.nav_library, ShelfIcon.LIBRARY, onLibrary),
            Triple(R.string.nav_search, ShelfIcon.SEARCH, onSearch),
            Triple(R.string.nav_add, ShelfIcon.ADD, onAdd),
            Triple(R.string.nav_more, ShelfIcon.MORE, onMore),
        ).forEachIndexed { index, (labelRes, icon, action) ->
            // Подсвечен тот пункт, где человек сейчас находится. Полка —
            // первый, остальные уводят на другие экраны.
            val tint = if (index == 0) colors.accent else colors.textSecondary
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(onClick = action)
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
            ) {
                ShelfIconImage(icon = icon, tint = tint)
                Text(
                    text = stringResource(labelRes),
                    style = type.labelSm,
                    color = tint,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
        }
    }
}

private const val GROUPING_DESCRIPTION = "Группировка"
private const val SPINES_DESCRIPTION = "Корешки"
private const val GRID_DESCRIPTION = "Сетка"

private const val TIER_LETTERS = 26
private const val UNDERLINE_HEIGHT = 2
private const val SHELF_SHADOW_HEIGHT = 6
private const val SHELF_BOARD_SHARE = 12f / (12f + 6f)
private const val SHADOW_ALPHA = 0.5f
private const val SHELF_BOARD_HEIGHT = 12
private const val GRID_CELL_WIDTH = 104
private const val GRID_COVER_HEIGHT = 150
private const val ROW_SPINE_WIDTH = 28
private const val ROW_SPINE_HEIGHT = 44
