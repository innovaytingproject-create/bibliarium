package com.bibliarium.app.probe

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
 * Что именно в отрисовке корешка убивает эмулятор API 34.
 *
 * Эмулятор пропадает целиком: ни падения теста, ни исключения, артефакты
 * с погибшей машины не забрать. Переживает только logcat — он пишется на
 * сторону раннера. Поэтому проба шагает по операциям рисования по одной
 * и на каждом шаге пишет в лог. Последняя строчка перед обрывом и называет
 * виновника.
 */
class SpineProbeTest {

    @get:Rule
    val compose = createComposeRule()

    private val device =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun whichDrawingStageKillsTheEmulator() {
        var stage by mutableIntStateOf(0)

        compose.setContent {
            BibliariumTheme {
                ProbeCanvas(stage)
            }
        }

        for (step in 1..LAST_STAGE) {
            Log.i(TAG, "стадия $step: рисуем ${STAGE_NAMES[step - 1]}")
            compose.runOnUiThread { stage = step }
            compose.waitForIdle()
            Thread.sleep(SETTLE_MS)
            Log.i(TAG, "стадия $step: нарисована, эмулятор жив")

            // Обход дерева доступности — ровно то, чем UiAutomator ищет
            // элементы на экране. Если валит именно он, будет видно здесь.
            val seen = device.findObject(By.text("Bibliarium")) != null
            Log.i(TAG, "стадия $step: дерево доступности обошли, шапку видно=$seen")
        }

        Log.i(TAG, "проба пройдена целиком")
    }

    @Composable
    private fun ProbeCanvas(stage: Int) {
        val colors = BibliariumTheme.colors
        val textStyle = BibliariumTheme.type.spineTitle.copy(color = colors.spineInkLight)
        val measurer = rememberTextMeasurer()

        if (stage >= STAGE_SHELF_EMPTY) {
            ShelfContent(
                books = probeBooks(
                    when {
                        stage >= STAGE_SHELF_MANY -> MANY_BOOKS
                        stage >= STAGE_SHELF_FEW -> FEW_BOOKS
                        else -> 0
                    },
                ),
                isImporting = false,
                onOpenBook = {},
                onMenu = {},
                onAddBook = {},
                onOpenSettings = {},
            )
            return
        }

        if (stage >= STAGE_REAL_SPINE) {
            Row {
                val count = if (stage >= STAGE_MANY_SPINES) MANY else 1
                repeat(count) { index ->
                    BookSpine(title = "Проба номер $index", author = "Автор пробы")
                }
            }
            return
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .size(WIDTH.dp, HEIGHT.dp)
                    .clip(RoundedCornerShape(CORNER.dp)),
            ) {
                if (stage >= STAGE_RECT) {
                    drawRect(colors.spinePalette[1])
                }
                if (stage >= STAGE_GRADIENT) {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to Color.White.copy(alpha = HIGHLIGHT_ALPHA),
                            EDGE to Color.Transparent,
                            1f - EDGE to Color.Transparent,
                            1f to Color.Black.copy(alpha = SHADE_ALPHA),
                        ),
                    )
                }
                if (stage >= STAGE_LINES) {
                    drawLine(
                        color = colors.spineInkLight,
                        start = Offset(size.width * EDGE, size.height * EDGE),
                        end = Offset(size.width * (1 - EDGE), size.height * EDGE),
                        strokeWidth = 1f,
                    )
                }
                if (stage >= STAGE_PATH) {
                    drawPath(
                        path = Path().apply {
                            moveTo(size.width / 4, size.height * PATH_Y)
                            quadraticTo(
                                size.width / 2,
                                size.height * PATH_Y - size.width / 4,
                                size.width * 3 / 4,
                                size.height * PATH_Y,
                            )
                        },
                        color = colors.spineInkLight,
                        style = Stroke(width = 2f),
                    )
                }
                if (stage >= STAGE_ROTATED_TEXT) {
                    val layout = measurer.measure(text = "Проба текста", style = textStyle)
                    rotate(degrees = ROTATION) {
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = center.x - layout.size.width / 2f,
                                y = center.y - layout.size.height / 2f,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Книги для полки делаются прямо здесь: база и файлы для рисования не нужны. */
    private fun probeBooks(count: Int): List<Book> = (0 until count).map { index ->
        Book(
            id = "probe-$index",
            title = "Книга пробы $index",
            author = "Автор ${index % PROBE_AUTHORS}",
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
        const val PROBE_AUTHORS = 7

        const val STAGE_RECT = 1
        const val STAGE_GRADIENT = 2
        const val STAGE_LINES = 3
        const val STAGE_PATH = 4
        const val STAGE_ROTATED_TEXT = 5
        const val STAGE_REAL_SPINE = 6
        const val STAGE_MANY_SPINES = 7
        const val STAGE_SHELF_EMPTY = 8
        const val STAGE_SHELF_FEW = 9
        const val STAGE_SHELF_MANY = 10
        const val LAST_STAGE = STAGE_SHELF_EMPTY

        val STAGE_NAMES = listOf(
            "заливку",
            "градиент",
            "линию",
            "кривую",
            "повёрнутый текст",
            "настоящий корешок",
            "двадцать корешков",
            "пустую полку",
            "полку с шестью книгами",
            "полку с сотней книг",
        )

        const val FEW_BOOKS = 6
        const val MANY_BOOKS = 100

        const val MANY = 20
        const val WIDTH = 50
        const val HEIGHT = 190
        const val CORNER = 3
        const val EDGE = 0.18f
        const val PATH_Y = 0.8f
        const val HIGHLIGHT_ALPHA = 0.25f
        const val SHADE_ALPHA = 0.12f
        const val ROTATION = 90f
        const val SETTLE_MS = 700L
    }
}
