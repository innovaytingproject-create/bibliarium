package com.bibliarium.app.reader

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.bibliarium.app.ui.theme.BibliariumTheme

/**
 * Панели экрана чтения.
 *
 * Лежат поверх страницы книги и ничего о ней не знают: что показать, решает
 * экран, а листанием и текстом занимается Readium.
 *
 * Отступы системных панелей учитываются здесь: верхняя панель отодвигается
 * от часов, нижняя — от полосы жеста. Иначе на телефоне с вырезом первая
 * строка уходит под часы.
 */
@Composable
fun ReaderChrome(
    visible: Boolean,
    palette: ReaderPalette,
    title: String,
    chapter: String?,
    progressLabel: String,
    progress: Float,
    actions: ReaderActions,
    modifier: Modifier = Modifier,
    /** Куда перешли, отпустив полосу прогресса. */
    onSeek: (Float) -> Unit = {},
    /** Что показать в подсказке над пальцем для доли книги. */
    seekHint: (Float) -> String = { "" },
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopBar(palette, title, chapter, actions)
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomBar(palette, progress, progressLabel, actions, onSeek, seekHint)
        }
    }
}

/** Что умеют кнопки панелей. Экран решает, что с этим делать. */
data class ReaderActions(
    val onBack: () -> Unit,
    val onToc: () -> Unit,
    val onBookmark: () -> Unit,
    val onHighlight: () -> Unit,
    val onSettings: () -> Unit,
    val onSearch: () -> Unit,
    /** null — поиска у этого формата нет, значок не показывается. */
    val searchAvailable: Boolean = true,
)

@Composable
private fun TopBar(
    palette: ReaderPalette,
    title: String,
    chapter: String?,
    actions: ReaderActions,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .translucent(palette)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(TOP_BAR_HEIGHT.dp)
            .padding(horizontal = SIDE_PADDING.dp),
    ) {
        ReaderIcon(
            icon = ReaderIconKind.BACK,
            tint = palette.text,
            description = "Назад",
            onClick = actions.onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = TITLE_PADDING.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = BibliariumTheme.type.labelMd,
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            chapter?.let {
                Text(
                    text = it,
                    style = BibliariumTheme.type.labelSm,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(ICON_GAP.dp),
        ) {
            ReaderIcon(ReaderIconKind.TOC, palette.text, "Оглавление", actions.onToc)
            ReaderIcon(ReaderIconKind.BOOKMARK, palette.text, "Закладка", actions.onBookmark)
        }
    }
}

@Composable
private fun BottomBar(
    palette: ReaderPalette,
    progress: Float,
    progressLabel: String,
    actions: ReaderActions,
    onSeek: (Float) -> Unit,
    seekHint: (Float) -> String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .translucent(palette)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(BOTTOM_BAR_HEIGHT.dp),
    ) {
        ProgressLine(
            palette = palette,
            progress = progress,
            onSeek = onSeek,
            hint = seekHint,
            modifier = Modifier
                .fillMaxWidth()
                .height(PROGRESS_ROW_HEIGHT.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE_PADDING.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ICON_GAP.dp)) {
                ReaderIcon(
                    ReaderIconKind.HIGHLIGHT,
                    palette.text,
                    "Выделить",
                    actions.onHighlight,
                )
                ReaderIcon(
                    ReaderIconKind.SETTINGS,
                    palette.text,
                    "Настройки",
                    actions.onSettings,
                )
                if (actions.searchAvailable) {
                    ReaderIcon(ReaderIconKind.SEARCH, palette.text, "Поиск", actions.onSearch)
                }
            }

            Text(
                text = progressLabel,
                style = BibliariumTheme.type.labelSm,
                color = palette.textSecondary,
            )
        }
    }
}

/**
 * Полоса прогресса: линия во всю ширину и кружок на текущем месте.
 *
 * Полосу можно тянуть пальцем. Пока тянешь, над пальцем висит подсказка
 * с главой и процентом, а переход случается, когда отпустил: иначе книга
 * перескакивала бы на каждое движение пальца.
 */
@Composable
private fun ProgressLine(
    palette: ReaderPalette,
    progress: Float,
    onSeek: (Float) -> Unit,
    hint: (Float) -> String,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(progress) }
    var width by remember { mutableFloatStateOf(1f) }
    val shown = if (dragging) dragValue else progress

    Box(modifier = modifier) {
        if (dragging) {
            SeekHint(
                text = hint(dragValue),
                palette = palette,
                fraction = dragValue,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            dragValue = (offset.x / width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragging = false
                            onSeek(dragValue)
                        },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            dragValue = (change.position.x / width).coerceIn(0f, 1f)
                        },
                    )
                }
                .semantics { contentDescription = "Полоса прогресса" },
        ) {
            val y = size.height / 2
            drawLine(
                color = palette.line,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = PROGRESS_THICKNESS.dp.toPx(),
            )
            val filled = size.width * shown.coerceIn(0f, 1f)
            drawLine(
                color = palette.accent,
                start = Offset(0f, y),
                end = Offset(filled, y),
                strokeWidth = PROGRESS_THICKNESS.dp.toPx(),
            )
            drawCircle(
                color = palette.accent,
                radius = PROGRESS_KNOB.dp.toPx() / 2 * (if (dragging) KNOB_GROWN else 1f),
                center = Offset(filled, y),
            )
        }
    }
}

/** Подсказка над пальцем: куда попадёшь, если отпустить здесь. */
@Composable
private fun SeekHint(
    text: String,
    palette: ReaderPalette,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val offset = (maxWidth - HINT_WIDTH.dp) * fraction.coerceIn(0f, 1f)
        Text(
            text = text,
            style = BibliariumTheme.type.labelSm,
            color = palette.text,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .offset(x = offset)
                .width(HINT_WIDTH.dp)
                .clip(RoundedCornerShape(HINT_CORNER.dp))
                .background(palette.surface)
                .padding(horizontal = HINT_PADDING.dp, vertical = HINT_PADDING.dp),
        )
    }
}

/**
 * Полупрозрачная подложка панели.
 *
 * Размытие есть только с Android 12: ниже него Modifier.blur ничего не делает,
 * а рисовать его вручную — дорого и заметно тормозит. Поэтому там панель
 * просто непрозрачная, и текст под ней не просвечивает.
 */
private fun Modifier.translucent(palette: ReaderPalette): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this
            .blur(BLUR.dp)
            .background(palette.surface.copy(alpha = PANEL_ALPHA))
    } else {
        this.background(palette.surface)
    }

private const val TOP_BAR_HEIGHT = 88
private const val BOTTOM_BAR_HEIGHT = 98
private const val PROGRESS_ROW_HEIGHT = 44
private const val PROGRESS_THICKNESS = 2
private const val PROGRESS_KNOB = 10
private const val SIDE_PADDING = 16
private const val TITLE_PADDING = 56
private const val ICON_GAP = 4
private const val PANEL_ALPHA = 0.76f
private const val BLUR = 12
private const val KNOB_GROWN = 1.6f
private const val HINT_WIDTH = 150
private const val HINT_CORNER = 8
private const val HINT_PADDING = 6
