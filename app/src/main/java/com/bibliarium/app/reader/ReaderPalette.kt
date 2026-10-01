package com.bibliarium.app.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Тема экрана чтения.
 *
 * Своя, не общая с приложением: читают при другом свете, чем листают полку,
 * и сепия нужна только здесь. На полке и в карточке её нет.
 */
enum class ReaderTheme {
    LIGHT,
    SEPIA,
    DARK,
}

/** Цвета экрана чтения. Значения из блока токенов в макете. */
@Immutable
data class ReaderPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val line: Color,
) {
    /** Светлые ли значки системных панелей поверх такого фона. */
    val lightSystemIcons: Boolean get() = this == Dark

    companion object {
        val Light = ReaderPalette(
            background = Color(0xFFF2F0EA),
            surface = Color(0xFFFAF9F5),
            text = Color(0xFF202321),
            textSecondary = Color(0xFF747872),
            accent = Color(0xFFB44D38),
            line = Color(0xFFD9D5CA),
        )

        val Dark = ReaderPalette(
            background = Color(0xFF141515),
            surface = Color(0xFF1D1F1E),
            text = Color(0xFFECEDE8),
            textSecondary = Color(0xFF999E98),
            accent = Color(0xFFD66C55),
            line = Color(0xFF2C2E2D),
        )

        /**
         * Сепия: в макете заданы только фон и текст, остальное берётся
         * от светлой темы — она и есть основа сепии.
         */
        val Sepia = Light.copy(
            background = Color(0xFFF4ECD8),
            text = Color(0xFF3A3129),
            surface = Color(0xFFFBF5E6),
            line = Color(0xFFE2D7BE),
        )

        fun of(theme: ReaderTheme): ReaderPalette = when (theme) {
            ReaderTheme.LIGHT -> Light
            ReaderTheme.SEPIA -> Sepia
            ReaderTheme.DARK -> Dark
        }
    }
}
