package com.bibliarium.app.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliarium.app.R
import com.bibliarium.app.ui.theme.BibliariumTheme
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Что сейчас поверх книги. Состояние задаёт активность — она же ставит
 * навигатор, поэтому источник один и разойтись им негде.
 */
sealed interface ReaderChromeState {
    data object Loading : ReaderChromeState
    data class Failed(val error: ReaderOpenError) : ReaderChromeState
    data class Content(val title: String, val engine: ReaderEngine) : ReaderChromeState
}

/**
 * Панели поверх текста. Номеров страниц нет намеренно: при смене размера шрифта
 * они врут, поэтому показываем только процент и оценку оставшегося времени.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderChrome(
    viewModel: ReaderViewModel,
    chromeState: ReaderChromeState,
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (chromeState) {
        is ReaderChromeState.Loading -> ReaderMessage(
            text = stringResource(R.string.reader_loading),
            modifier = modifier,
        )

        is ReaderChromeState.Failed -> ReaderFailure(chromeState.error, onClose, modifier)

        is ReaderChromeState.Content -> ReaderPanels(
            viewModel = viewModel,
            title = chromeState.title,
            engine = chromeState.engine,
            visible = visible,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun ReaderPanels(
    viewModel: ReaderViewModel,
    title: String,
    engine: ReaderEngine,
    visible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position by viewModel.position.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }

    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .statusBarsPadding()
                    .padding(horizontal = spacing.margin, vertical = spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = type.labelMd,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = spacing.sm),
                )
                TextButton(onClick = onClose) {
                    Text(
                        text = stringResource(R.string.reader_close),
                        style = type.labelMd,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface),
            ) {
                HorizontalDivider(thickness = 1.dp, color = colors.line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = spacing.margin, vertical = spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { settingsOpen = !settingsOpen }) {
                        Text(
                            text = stringResource(R.string.reader_settings),
                            style = type.labelMd,
                            color = colors.accent,
                        )
                    }
                    Text(
                        text = progressLabel(position),
                        style = type.labelMd,
                        color = colors.textSecondary,
                    )
                }

                if (settingsOpen) {
                    HorizontalDivider(thickness = 1.dp, color = colors.line)
                    ReaderSettingsPanel(
                        viewModel = viewModel,
                        engine = engine,
                        modifier = Modifier.padding(
                            horizontal = spacing.margin,
                            vertical = spacing.sm,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun progressLabel(position: ReadingPosition): String {
    val percent = (position.progress * 100).toInt()
    val minutes = position.minutesLeft ?: return stringResource(R.string.reader_progress, percent)
    return when {
        minutes < 60 -> stringResource(R.string.reader_progress_minutes, percent, minutes)
        else -> stringResource(R.string.reader_progress_hours, percent, minutes / 60)
    }
}

@Composable
private fun ReaderMessage(text: String, modifier: Modifier = Modifier) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = type.bodyMd, color = colors.textSecondary)
    }
}

@Composable
private fun ReaderFailure(
    error: ReaderOpenError,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(
                text = stringResource(
                    when (error) {
                        ReaderOpenError.FileMissing -> R.string.reader_error_missing
                        ReaderOpenError.Unreadable -> R.string.reader_error_unreadable
                        ReaderOpenError.UnsupportedFormat -> R.string.reader_error_unsupported
                    },
                ),
                style = type.bodyMd,
                color = colors.text,
            )
            TextButton(onClick = onClose) {
                Text(
                    text = stringResource(R.string.reader_close),
                    style = type.labelLg,
                    color = colors.accent,
                )
            }
        }
    }
}
