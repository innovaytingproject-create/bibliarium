package com.bibliarium.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bibliarium.app.R
import com.bibliarium.app.ui.theme.BibliariumTheme
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Настройки чтения. Для PDF настроек шрифта нет вовсе — они не показываются
 * серыми, а просто отсутствуют: серая кнопка выглядит как поломка.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderSettingsPanel(
    viewModel: ReaderViewModel,
    engine: ReaderEngine,
    modifier: Modifier = Modifier,
) {
    val spacing = BibliariumTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        when (engine) {
            ReaderEngine.EPUB -> EpubSettings(viewModel)
            ReaderEngine.PDF -> PdfSettings(viewModel)
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun EpubSettings(viewModel: ReaderViewModel) {
    val preferences by viewModel.epubPreferences.collectAsStateWithLifecycle()

    SettingRow(title = stringResource(R.string.reader_font)) {
        Choice(
            label = stringResource(R.string.reader_font_literata),
            selected = preferences.fontFamily == FontFamily.LITERATA,
            onClick = {
                viewModel.updateEpubPreferences(
                    preferences.copy(fontFamily = FontFamily.LITERATA),
                )
            },
        )
        Choice(
            label = stringResource(R.string.reader_font_lora),
            selected = preferences.fontFamily == FontFamily.LORA,
            onClick = {
                viewModel.updateEpubPreferences(preferences.copy(fontFamily = FontFamily.LORA))
            },
        )
        Choice(
            label = stringResource(R.string.reader_font_system),
            selected = preferences.fontFamily == null,
            onClick = { viewModel.updateEpubPreferences(preferences.copy(fontFamily = null)) },
        )
    }

    StepperRow(
        title = stringResource(R.string.reader_font_size),
        onLess = {
            viewModel.updateEpubPreferences(
                preferences.copy(fontSize = step(preferences.fontSize, 1.0, -0.1, 0.6, 2.5)),
            )
        },
        onMore = {
            viewModel.updateEpubPreferences(
                preferences.copy(fontSize = step(preferences.fontSize, 1.0, 0.1, 0.6, 2.5)),
            )
        },
    )

    StepperRow(
        title = stringResource(R.string.reader_margins),
        onLess = {
            viewModel.updateEpubPreferences(
                preferences.copy(pageMargins = step(preferences.pageMargins, 1.0, -0.2, 0.4, 2.5)),
            )
        },
        onMore = {
            viewModel.updateEpubPreferences(
                preferences.copy(pageMargins = step(preferences.pageMargins, 1.0, 0.2, 0.4, 2.5)),
            )
        },
    )

    StepperRow(
        title = stringResource(R.string.reader_line_height),
        onLess = {
            viewModel.updateEpubPreferences(
                preferences.copy(lineHeight = step(preferences.lineHeight, 1.7, -0.1, 1.0, 2.5)),
            )
        },
        onMore = {
            viewModel.updateEpubPreferences(
                preferences.copy(lineHeight = step(preferences.lineHeight, 1.7, 0.1, 1.0, 2.5)),
            )
        },
    )

    SettingRow(title = stringResource(R.string.reader_theme)) {
        Choice(
            label = stringResource(R.string.reader_theme_light),
            selected = preferences.theme == Theme.LIGHT || preferences.theme == null,
            onClick = { viewModel.updateEpubPreferences(preferences.copy(theme = Theme.LIGHT)) },
        )
        Choice(
            label = stringResource(R.string.reader_theme_sepia),
            selected = preferences.theme == Theme.SEPIA,
            onClick = { viewModel.updateEpubPreferences(preferences.copy(theme = Theme.SEPIA)) },
        )
        Choice(
            label = stringResource(R.string.reader_theme_dark),
            selected = preferences.theme == Theme.DARK,
            onClick = { viewModel.updateEpubPreferences(preferences.copy(theme = Theme.DARK)) },
        )
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun PdfSettings(viewModel: ReaderViewModel) {
    val preferences by viewModel.pdfPreferences.collectAsStateWithLifecycle()
    val nightMode by viewModel.pdfNightMode.collectAsStateWithLifecycle()

    SettingRow(title = stringResource(R.string.reader_theme)) {
        Choice(
            label = stringResource(R.string.reader_theme_light),
            selected = !nightMode,
            onClick = { viewModel.setPdfNightMode(false) },
        )
        Choice(
            label = stringResource(R.string.reader_theme_night),
            selected = nightMode,
            onClick = { viewModel.setPdfNightMode(true) },
        )
    }

    SettingRow(title = stringResource(R.string.reader_fit)) {
        Choice(
            label = stringResource(R.string.reader_fit_width),
            selected = preferences.fit != Fit.HEIGHT,
            onClick = { viewModel.updatePdfPreferences(preferences.copy(fit = Fit.WIDTH)) },
        )
        Choice(
            label = stringResource(R.string.reader_fit_height),
            selected = preferences.fit == Fit.HEIGHT,
            onClick = { viewModel.updatePdfPreferences(preferences.copy(fit = Fit.HEIGHT)) },
        )
    }
}

@Composable
private fun SettingRow(title: String, content: @Composable () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = type.labelMd, color = colors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) { content() }
    }
}

@Composable
private fun StepperRow(title: String, onLess: () -> Unit, onMore: () -> Unit) {
    SettingRow(title = title) {
        Choice(label = "−", selected = false, onClick = onLess)
        Choice(label = "+", selected = false, onClick = onMore)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type
    val spacing = BibliariumTheme.spacing
    val shape = RoundedCornerShape(BibliariumTheme.shapes.chip)

    Text(
        text = label,
        style = type.labelMd,
        color = if (selected) colors.onAccent else colors.text,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.accent else colors.surfaceRecessed)
            .border(1.dp, colors.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
    )
}

/** Шаг настройки с защитой от вылета за разумные границы. */
private fun step(current: Double?, default: Double, delta: Double, min: Double, max: Double): Double =
    ((current ?: default) + delta).coerceIn(min, max)
