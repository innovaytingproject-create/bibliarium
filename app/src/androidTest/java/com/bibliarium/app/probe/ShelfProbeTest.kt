package com.bibliarium.app.probe

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.bibliarium.app.domain.Book
import com.bibliarium.app.domain.BookFormat
import com.bibliarium.app.domain.ReadingStatus
import com.bibliarium.app.ui.shelf.BookSpine
import com.bibliarium.app.ui.shelf.ShelfContent
import com.bibliarium.app.ui.theme.BibliariumTheme
import org.junit.Rule
import org.junit.Test

/**
 * Что в ряду корешков убивает эмулятор API 34.
 *
 * Предыдущая проба показала: двадцать корешков подряд эмулятор переживает,
 * пустая полка переживает, а полка с книгами — нет. Значит виновата обвязка
 * ряда, а не рисование. Здесь она собирается по частям: сначала ленивые
 * списки, потом нажатие, потом полка-полоса, потом заголовок яруса.
 *
 * Логи переживают смерть машины, артефакты — нет, поэтому каждая стадия
 * пишется в logcat.
 */
class ShelfProbeTest {

    @get:Rule
    val compose = createComposeRule()

    private val device =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun whichPartOfTheRowKillsTheEmulator() {
        var stage by mutableIntStateOf(0)

        compose.setContent {
            BibliariumTheme {
                Stage(stage)
            }
        }

        for (step in 1..LAST_STAGE) {
            Log.i(TAG, "стадия $step: ${NAMES[step - 1]}")
            compose.runOnUiThread { stage = step }
            compose.waitForIdle()
            Thread.sleep(SETTLE_MS)
            val seen = device.findObject(By.descContains("Книга пробы")) != null
            Log.i(TAG, "стадия $step: пережили, корешок в дереве=$seen")
        }

        Log.i(TAG, "проба пройдена целиком")
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun Stage(stage: Int) {
        val colors = BibliariumTheme.colors
        val books = probeBooks(BOOKS)

        when (stage) {
            STAGE_ROW -> Row { books.forEach { Spine(it) } }

            STAGE_LAZY_ROW -> LazyRow {
                items(items = books, key = { it.id }) { Spine(it) }
            }

            STAGE_LAZY_COLUMN -> LazyColumn {
                item(key = "row") {
                    LazyRow { items(items = books, key = { it.id }) { Spine(it) } }
                }
            }

            STAGE_CLICKABLE -> LazyColumn {
                item(key = "row") {
                    LazyRow {
                        items(items = books, key = { it.id }) { book ->
                            Box(modifier = Modifier.combinedClickable(onClick = {}, onLongClick = {})) {
                                Spine(book)
                            }
                        }
                    }
                }
            }

            STAGE_BOARD -> LazyColumn {
                item(key = "row") {
                    Column {
                        LazyRow {
                            items(items = books, key = { it.id }) { book ->
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {},
                                    ),
                                ) {
                                    Spine(book)
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(BOARD.dp)
                                .background(colors.shelfBoard),
                        ) {
                            HorizontalDivider(thickness = 1.dp, color = colors.shelfBoardEdge)
                        }
                    }
                }
            }

            STAGE_HEADER -> LazyColumn {
                item(key = "row") {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Ярус A · Не начато", color = colors.text)
                            TextButton(onClick = {}) { Text(text = "Все 6") }
                        }
                        LazyRow {
                            items(items = books, key = { it.id }) { book ->
                                Box(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {},
                                    ),
                                ) {
                                    Spine(book)
                                }
                            }
                        }
                    }
                }
            }

            STAGE_SHELF -> ShelfContent(
                books = books,
                isImporting = false,
                onOpenBook = {},
                onMenu = {},
                onAddBook = {},
                onOpenSettings = {},
            )

            else -> Box(modifier = Modifier.fillMaxSize())
        }
    }

    @Composable
    private fun Spine(book: Book) {
        BookSpine(title = book.title, author = book.author)
    }

    private fun probeBooks(count: Int): List<Book> = (0 until count).map { index ->
        Book(
            id = "probe-$index",
            title = "Книга пробы $index",
            author = "Автор ${index % AUTHORS}",
            format = BookFormat.EPUB,
            filePath = "/dev/null",
            coverPath = null,
            addedAt = index.toLong(),
            lastOpenedAt = null,
            progress = 0f,
            locator = null,
            status = ReadingStatus.NOT_STARTED,
            genre = null,
            shelfId = null,
            isFavorite = false,
            fileSize = 0,
            headHash = null,
        )
    }

    private companion object {
        const val TAG = "BibliariumProbe"

        const val STAGE_ROW = 1
        const val STAGE_LAZY_ROW = 2
        const val STAGE_LAZY_COLUMN = 3
        const val STAGE_CLICKABLE = 4
        const val STAGE_BOARD = 5
        const val STAGE_HEADER = 6
        const val STAGE_SHELF = 7
        const val LAST_STAGE = 7

        val NAMES = listOf(
            "обычный ряд корешков",
            "ленивый ряд",
            "ленивый ряд в ленивом столбце",
            "плюс нажатие на корешок",
            "плюс полка-полоса",
            "плюс заголовок яруса",
            "настоящий экран полки",
        )

        const val BOOKS = 6
        const val AUTHORS = 3
        const val BOARD = 12
        const val SETTLE_MS = 700L
    }
}
