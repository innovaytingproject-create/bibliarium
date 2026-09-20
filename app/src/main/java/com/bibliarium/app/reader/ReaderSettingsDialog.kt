package com.bibliarium.app.reader

import android.app.AlertDialog
import android.content.Context
import com.bibliarium.app.R
import org.readium.r2.navigator.preferences.Fit
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Настройки чтения простым списком.
 *
 * Для PDF настроек шрифта нет вовсе — они не показываются серыми, а просто
 * отсутствуют: серая кнопка выглядит как поломка.
 */
@OptIn(ExperimentalReadiumApi::class)
class ReaderSettingsDialog(
    private val context: Context,
    private val viewModel: ReaderViewModel,
    private val engine: ReaderEngine,
) {

    private class Action(val title: String, val run: () -> Unit)

    fun show() {
        val actions = when (engine) {
            ReaderEngine.EPUB -> epubActions()
            ReaderEngine.PDF -> pdfActions()
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.reader_settings)
            .setItems(actions.map { it.title }.toTypedArray()) { _, index ->
                actions[index].run()
            }
            .setNegativeButton(R.string.reader_close, null)
            .show()
    }

    private fun epubActions(): List<Action> {
        val preferences = viewModel.epubPreferences.value
        return listOf(
            Action(context.getString(R.string.reader_font_literata)) {
                viewModel.updateEpubPreferences(
                    preferences.copy(fontFamily = FontFamily.LITERATA),
                )
            },
            Action(context.getString(R.string.reader_font_lora)) {
                viewModel.updateEpubPreferences(preferences.copy(fontFamily = FontFamily.LORA))
            },
            Action(context.getString(R.string.reader_font_system)) {
                viewModel.updateEpubPreferences(preferences.copy(fontFamily = null))
            },
            Action(context.getString(R.string.reader_size_bigger)) {
                viewModel.updateEpubPreferences(
                    preferences.copy(fontSize = step(preferences.fontSize, 1.0, 0.1, 0.6, 2.5)),
                )
            },
            Action(context.getString(R.string.reader_size_smaller)) {
                viewModel.updateEpubPreferences(
                    preferences.copy(fontSize = step(preferences.fontSize, 1.0, -0.1, 0.6, 2.5)),
                )
            },
            Action(context.getString(R.string.reader_margins_wider)) {
                viewModel.updateEpubPreferences(
                    preferences.copy(
                        pageMargins = step(preferences.pageMargins, 1.0, 0.2, 0.4, 2.5),
                    ),
                )
            },
            Action(context.getString(R.string.reader_margins_narrower)) {
                viewModel.updateEpubPreferences(
                    preferences.copy(
                        pageMargins = step(preferences.pageMargins, 1.0, -0.2, 0.4, 2.5),
                    ),
                )
            },
            Action(context.getString(R.string.reader_line_height_more)) {
                viewModel.updateEpubPreferences(
                    preferences.copy(lineHeight = step(preferences.lineHeight, 1.7, 0.1, 1.0, 2.5)),
                )
            },
            Action(context.getString(R.string.reader_line_height_less)) {
                viewModel.updateEpubPreferences(
                    preferences.copy(
                        lineHeight = step(preferences.lineHeight, 1.7, -0.1, 1.0, 2.5),
                    ),
                )
            },
            Action(context.getString(R.string.reader_theme_light)) {
                viewModel.updateEpubPreferences(preferences.copy(theme = Theme.LIGHT))
            },
            Action(context.getString(R.string.reader_theme_sepia)) {
                viewModel.updateEpubPreferences(preferences.copy(theme = Theme.SEPIA))
            },
            Action(context.getString(R.string.reader_theme_dark)) {
                viewModel.updateEpubPreferences(preferences.copy(theme = Theme.DARK))
            },
        )
    }

    private fun pdfActions(): List<Action> {
        val preferences = viewModel.pdfPreferences.value
        return listOf(
            Action(context.getString(R.string.reader_theme_light)) {
                viewModel.setPdfNightMode(false)
            },
            Action(context.getString(R.string.reader_theme_night)) {
                viewModel.setPdfNightMode(true)
            },
            Action(context.getString(R.string.reader_fit_width)) {
                viewModel.updatePdfPreferences(preferences.copy(fit = Fit.WIDTH))
            },
            Action(context.getString(R.string.reader_fit_height)) {
                viewModel.updatePdfPreferences(preferences.copy(fit = Fit.HEIGHT))
            },
        )
    }

    private fun step(
        current: Double?,
        default: Double,
        delta: Double,
        min: Double,
        max: Double,
    ): Double = ((current ?: default) + delta).coerceIn(min, max)
}
