package com.bibliarium.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibliarium.app.R

/** Одна находка: кусок текста вокруг совпадения и где он в книге. */
data class SearchHit(
    val before: String,
    val match: String,
    val after: String,
    val chapter: String?,
    val locatorJson: String,
)

/**
 * Поиск по книге.
 *
 * Отдельный экран поверх страницы: поле ввода сверху, ниже найденное
 * с кусочком текста вокруг совпадения. Тап переносит на это место.
 *
 * У PDF поиска нет вовсе — Readium ищет только в тексте, а страница PDF
 * это картинка. Значок поиска у PDF поэтому не показывается, и пустого
 * экрана «ничего не найдено» человек там не увидит.
 */
@Composable
fun ReaderSearch(
    palette: ReaderPalette,
    query: String,
    hits: List<SearchHit>,
    searching: Boolean,
    onQuery: (String) -> Unit,
    onPick: (SearchHit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SIDE.dp, vertical = GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.reader_search_hint),
                        color = palette.textSecondary,
                    )
                },
                modifier = Modifier.weight(1f),
            )
            ReaderIcon(ReaderIconKind.CLOSE, palette.text, "Закрыть поиск", onClose)
        }

        when {
            searching && hits.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = palette.accent)
            }

            query.isNotBlank() && hits.isEmpty() -> Message(
                text = androidx.compose.ui.res.stringResource(R.string.reader_search_empty),
                palette = palette,
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(hits) { hit ->
                    HitRow(hit = hit, palette = palette, onClick = { onPick(hit) })
                }
            }
        }
    }
}

@Composable
private fun HitRow(hit: SearchHit, palette: ReaderPalette, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SIDE.dp, vertical = GAP.dp),
    ) {
        hit.chapter?.let { chapter ->
            Text(
                text = chapter,
                style = TextStyle(fontSize = CHAPTER_SIZE.sp),
                color = palette.textSecondary,
            )
        }
        Text(
            text = buildAnnotatedString {
                append(hit.before)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = palette.accent)) {
                    append(hit.match)
                }
                append(hit.after)
            },
            style = TextStyle(fontSize = HIT_SIZE.sp),
            color = palette.text,
            maxLines = HIT_LINES,
        )
    }
    HorizontalDivider(thickness = 1.dp, color = palette.line)
}

@Composable
private fun Message(text: String, palette: ReaderPalette) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = TextStyle(fontSize = HIT_SIZE.sp),
            color = palette.textSecondary,
            modifier = Modifier.padding(SIDE.dp),
        )
    }
}

private const val SIDE = 16
private const val GAP = 10
private const val CHAPTER_SIZE = 11
private const val HIT_SIZE = 14
private const val HIT_LINES = 3
