package com.bibliarium.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Две визуальные темы из раздела 8 ТЗ. Экраны одни и те же, отличаются только токены. */
enum class ThemeVariant {
    ARCHIVE,
    SPINE,
}

/**
 * Все цвета приложения. В экранах не должно быть ни одного литерала Color(...) —
 * только обращения к этим токенам.
 */
@Immutable
data class ColorTokens(
    val background: Color,
    val surface: Color,
    /** Утопленная поверхность: ниша полки, фон карточек второго уровня. */
    val surfaceRecessed: Color,
    /** Полоса полки под рядом корешков. */
    val shelfBoard: Color,
    val shelfBoardEdge: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val line: Color,
    /** Палитра корешков — индекс выбирается из хэша названия (раздел 5 ТЗ). */
    val spinePalette: List<Color>,
)

/** Геометрия и характер корешка, зависящие от темы. */
@Immutable
data class SpineStyleTokens(
    val corner: Dp,
    /** Цилиндрическая тень по бокам — только Archive. */
    val embossed: Boolean,
    /** Шаблоны раскладки значков и линий — только Archive. */
    val patterned: Boolean,
)

/** Отступы из DESIGN.md. */
@Immutable
data class SpacingTokens(
    val gutter: Dp = 16.dp,
    /** Зазор между корешками на полке. */
    val gutterShelf: Dp = 4.dp,
    val margin: Dp = 16.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 28.dp,
    val xxl: Dp = 40.dp,
)

/** Скругления из DESIGN.md. */
@Immutable
data class ShapeTokens(
    val chip: Dp = 4.dp,
    val button: Dp = 4.dp,
    val card: Dp = 6.dp,
    val sheet: Dp = 8.dp,
)

private val ArchiveSpinePalette = listOf(
    Color(0xFFEFE9DC),
    Color(0xFF232624),
    Color(0xFFB44D38),
    Color(0xFFE9E2D2),
    Color(0xFF40584C),
    Color(0xFF2E3330),
    Color(0xFF8A6A3B),
    Color(0xFFF5F1E6),
)

private val SpineSpinePalette = listOf(
    Color(0xFFC6A9D8),
    Color(0xFF2D302E),
    Color(0xFFE8B84B),
    Color(0xFFD96A4A),
    Color(0xFF4A8A7B),
    Color(0xFF5C7FA8),
    Color(0xFFEFEAE0),
    Color(0xFFE8858A),
)

internal val ArchiveLightColors = ColorTokens(
    background = Color(0xFFF2F0EA),
    surface = Color(0xFFFAF9F5),
    surfaceRecessed = Color(0xFFEAE6DC),
    shelfBoard = Color(0xFFE4E0D5),
    shelfBoardEdge = Color(0xFFD4CFC2),
    text = Color(0xFF202321),
    textSecondary = Color(0xFF747872),
    accent = Color(0xFFB44D38),
    onAccent = Color(0xFFF2F0EA),
    line = Color(0xFFD8D4C8),
    spinePalette = ArchiveSpinePalette,
)

internal val ArchiveDarkColors = ColorTokens(
    background = Color(0xFF141515),
    surface = Color(0xFF1D1F1E),
    surfaceRecessed = Color(0xFF222423),
    shelfBoard = Color(0xFF242624),
    shelfBoardEdge = Color(0xFF32352F),
    text = Color(0xFFECEDE8),
    textSecondary = Color(0xFF999E98),
    accent = Color(0xFFD66C55),
    onAccent = Color(0xFF141515),
    line = Color(0xFF2C2E2D),
    spinePalette = ArchiveSpinePalette,
)

internal val SpineLightColors = ColorTokens(
    background = Color(0xFFF7F7F5),
    surface = Color(0xFFFFFFFF),
    surfaceRecessed = Color(0xFFEFEFEC),
    shelfBoard = Color(0xFFEDEDEA),
    shelfBoardEdge = Color(0xFFE2E5E1),
    text = Color(0xFF1A1C1B),
    textSecondary = Color(0xFF717571),
    accent = Color(0xFF426B5A),
    onAccent = Color(0xFFFFFFFF),
    line = Color(0xFFE2E5E1),
    spinePalette = SpineSpinePalette,
)

internal val SpineDarkColors = ColorTokens(
    background = Color(0xFF111211),
    surface = Color(0xFF1B1D1C),
    surfaceRecessed = Color(0xFF1F2220),
    shelfBoard = Color(0xFF212422),
    shelfBoardEdge = Color(0xFF303431),
    text = Color(0xFFF4F5F2),
    textSecondary = Color(0xFF9DA29D),
    accent = Color(0xFF72A58D),
    onAccent = Color(0xFF111211),
    line = Color(0xFF303431),
    spinePalette = SpineSpinePalette,
)

internal fun colorTokensFor(variant: ThemeVariant, dark: Boolean): ColorTokens =
    when (variant) {
        ThemeVariant.ARCHIVE -> if (dark) ArchiveDarkColors else ArchiveLightColors
        ThemeVariant.SPINE -> if (dark) SpineDarkColors else SpineLightColors
    }

internal fun spineStyleFor(variant: ThemeVariant): SpineStyleTokens =
    when (variant) {
        // «Sharp 2px to 4px corners» + цилиндрическая тень из DESIGN.md.
        ThemeVariant.ARCHIVE -> SpineStyleTokens(corner = 3.dp, embossed = true, patterned = true)
        // «корешки плоские, без шаблонов и тени цилиндра, скругление 3dp».
        ThemeVariant.SPINE -> SpineStyleTokens(corner = 3.dp, embossed = false, patterned = false)
    }
