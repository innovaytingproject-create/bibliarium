package com.bibliarium.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.bibliarium.app.R

/**
 * Оглавление — панель слева поверх страницы.
 *
 * Открывается прокрученным к текущей главе, а не к началу: человек открывает
 * оглавление, чтобы понять, где он сейчас, и прыгнуть рядом.
 */
@Composable
fun ReaderToc(
    palette: ReaderPalette,
    bookTitle: String,
    entries: List<TocEntry>,
    currentIndex: Int,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Остальная страница затемняется и закрывает оглавление по тапу.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SCRIM)
                .clickable(onClick = onClose),
        )

        val state = rememberLazyListState()
        LaunchedEffect(currentIndex, entries.size) {
            if (currentIndex in entries.indices) {
                state.scrollToItem(currentIndex)
            }
        }

        Column(
            modifier = Modifier
                .width(if (maxWidth * NARROW_SHARE < TOC_WIDTH.dp) maxWidth * NARROW_SHARE else TOC_WIDTH.dp)
                .fillMaxHeight()
                .background(palette.surface)
                .padding(horizontal = SIDE.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = TOP.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.reader_toc),
                    style = TextStyle(fontSize = TITLE_SIZE.sp),
                    color = palette.text,
                )
                ReaderIcon(ReaderIconKind.CLOSE, palette.textSecondary, "Закрыть", onClose)
            }

            Text(
                text = bookTitle,
                style = TextStyle(fontSize = SUBTITLE_SIZE.sp),
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = GAP.dp),
            )

            LazyColumn(state = state) {
                itemsIndexed(entries) { index, entry ->
                    TocRow(
                        entry = entry,
                        active = index == currentIndex,
                        palette = palette,
                        onClick = { onPick(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TocRow(
    entry: TocEntry,
    active: Boolean,
    palette: ReaderPalette,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ROW_CORNER.dp))
            .background(if (active) palette.accent.copy(alpha = ACTIVE_ALPHA) else palette.surface)
            .clickable(onClick = onClick)
            .padding(
                start = (SIDE + entry.depth * NESTING).dp,
                end = SIDE.dp,
                top = ROW_PADDING.dp,
                bottom = ROW_PADDING.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.title.trim(),
            style = TextStyle(fontSize = ROW_SIZE.sp),
            color = if (active) palette.accent else palette.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        entry.page?.let { page ->
            Text(
                text = page.toString(),
                style = TextStyle(fontSize = PAGE_SIZE.sp),
                color = if (active) palette.accent else palette.textSecondary,
                modifier = Modifier.padding(start = GAP.dp),
            )
        }
    }
}

private val SCRIM = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)

private const val TOC_WIDTH = 330

/** На узком экране панель занимает не больше этой доли ширины. */
private const val NARROW_SHARE = 0.85f
private const val SIDE = 16
private const val TOP = 48
private const val GAP = 12
private const val NESTING = 28
private const val TITLE_SIZE = 18
private const val SUBTITLE_SIZE = 12
private const val ROW_SIZE = 13
private const val PAGE_SIZE = 11
private const val ROW_PADDING = 12
private const val ROW_CORNER = 8
private const val ACTIVE_ALPHA = 0.12f
