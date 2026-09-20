package com.bibliarium.app.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bibliarium.app.ui.theme.BibliariumTheme
import com.bibliarium.app.ui.theme.SpineStyleTokens

/**
 * Корешок книги.
 *
 * Рисуется из названия, а не берётся из файла: обложки есть не у всех книг,
 * а полка должна выглядеть цельной. Одна и та же книга всегда выглядит
 * одинаково — весь вид выводится из хэша `название|автор` (раздел 5 ТЗ),
 * ничего не хранится и ничего не случайно.
 *
 * Рисование идёт одним Canvas: ни Bitmap, ни вложенных composable. На полке
 * из пятисот книг это разница между плавной прокруткой и слайд-шоу.
 */

/** Значки на корешке. Порядок важен: индекс берётся из хэша. */
enum class SpineOrnament {
    CIRCLE_OUTLINE,
    CIRCLE_FILLED,
    CROSS,
    TICK,
    TWO_LINES,
    ARROW_UP,
    WAVE,
    TRIANGLE,
}

/** Всё, что определяет вид корешка. Считается один раз из хэша. */
@Immutable
data class SpineLook(
    val width: Dp,
    val height: Dp,
    val color: Color,
    /** Цвет букв: по яркости фона, а не на глаз. */
    val ink: Color,
    /** null — вместо значка тонкая линия. */
    val ornament: SpineOrnament?,
    val pattern: Int,
)

/**
 * djb2 из раздела 5 ТЗ.
 *
 * Верхний бит сбрасывается: у отрицательного числа остаток от деления в Kotlin
 * тоже отрицательный, и `palette[hash % size]` падало бы. На постоянство вида
 * это не влияет — для одной книги число всё равно всегда одно и то же.
 */
fun spineHash(title: String, author: String?): Int {
    var hash = DJB2_SEED
    val key = "$title|${author.orEmpty()}"
    for (character in key) {
        hash = hash * DJB2_FACTOR + character.code
    }
    return hash and Int.MAX_VALUE
}

fun spineLookOf(
    title: String,
    author: String?,
    palette: List<Color>,
    inkDark: Color,
    inkLight: Color,
): SpineLook {
    val hash = spineHash(title, author)
    val color = palette[hash % palette.size]
    return SpineLook(
        width = (MIN_WIDTH + (hash shr 3) % WIDTH_STEPS * WIDTH_STEP).dp,
        height = (MIN_HEIGHT + (hash shr 7) % HEIGHT_STEPS * HEIGHT_STEP).dp,
        color = color,
        ink = if (color.luminance() > LIGHT_BACKGROUND) inkDark else inkLight,
        ornament = SpineOrnament.entries[(hash shr 11) % SpineOrnament.entries.size]
            .takeIf { (hash shr 15) % ORNAMENT_SKIP != 0 },
        pattern = (hash shr 19) % PATTERNS,
    )
}

/** Корешок на полке: размер свой, из хэша. */
@Composable
fun BookSpine(
    title: String,
    author: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val look = rememberSpineLook(title, author)
    SpineFace(
        title = title,
        look = look,
        modifier = modifier
            .size(look.width, look.height)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

/**
 * Корешок в заданном размере — для режима сетки, где своей обложки у книги нет.
 * Тот же рисунок, просто крупнее.
 */
@Composable
fun SpineFace(
    title: String,
    look: SpineLook,
    modifier: Modifier = Modifier,
) {
    val style = BibliariumTheme.spine
    val textStyle = BibliariumTheme.type.spineTitle.copy(color = look.ink)
    val measurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(style.corner))
            // Название нужно и человеку с озвучкой, и проверке: нарисованный
            // Canvas текстом наружу не виден.
            .semantics { contentDescription = title },
    ) {
        drawSpine(
            title = title,
            look = look,
            style = style,
            textStyle = textStyle,
            measurer = measurer,
        )
    }
}

@Composable
fun rememberSpineLook(title: String, author: String?): SpineLook {
    val colors = BibliariumTheme.colors
    return remember(title, author, colors) {
        spineLookOf(
            title = title,
            author = author,
            palette = colors.spinePalette,
            inkDark = colors.spineInkDark,
            inkLight = colors.spineInkLight,
        )
    }
}

private fun DrawScope.drawSpine(
    title: String,
    look: SpineLook,
    style: SpineStyleTokens,
    textStyle: TextStyle,
    measurer: TextMeasurer,
) {
    drawRect(look.color)

    if (style.embossed) {
        // Цилиндр: слева светлая грань, справа затенённая. Из DESIGN.md.
        drawRect(
            Brush.horizontalGradient(
                0f to style.highlight,
                EDGE_STOP to Color.Transparent,
                1f - EDGE_STOP to Color.Transparent,
                1f to style.shade,
            ),
        )
    }

    val inset = size.width * INSET_SHARE
    if (style.patterned) {
        drawPattern(look, inset)
    } else {
        drawRule(look.ink, inset, size.height * TOP_RULE)
    }

    val ornamentCenter = if (look.pattern % 2 == 0) {
        Offset(size.width / 2, size.height - inset * ORNAMENT_GAP)
    } else {
        Offset(size.width / 2, inset * ORNAMENT_GAP)
    }
    val ornamentSize = size.width * ORNAMENT_SHARE
    val ornament = look.ornament
    if (ornament != null) {
        drawOrnament(ornament, ornamentCenter, ornamentSize, look.ink)
    } else {
        // Вместо значка — тонкая линия: корешок не должен выглядеть пустым.
        drawRule(look.ink, inset, ornamentCenter.y)
    }

    drawSpineTitle(title, textStyle, measurer, inset, ornamentSize)
}

private fun DrawScope.drawSpineTitle(
    title: String,
    textStyle: TextStyle,
    measurer: TextMeasurer,
    inset: Float,
    ornamentSize: Float,
) {
    val available = (size.height - inset * TITLE_MARGIN - ornamentSize * 2).toInt()
    if (available <= 0) return

    val layout: TextLayoutResult = measurer.measure(
        text = title,
        style = textStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = available),
    )

    // Поворот на 90° по часовой: так название читается сверху вниз, как на
    // настоящем корешке. Разворот идёт вокруг центра, поэтому текст остаётся
    // посередине корешка.
    rotate(degrees = TITLE_ROTATION) {
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                x = center.x - layout.size.width / 2f,
                y = center.y - layout.size.height / 2f,
            ),
        )
    }
}

/** Шаблоны раскладки линий и рамки — только в теме Archive. */
private fun DrawScope.drawPattern(look: SpineLook, inset: Float) {
    val ink = look.ink
    when (look.pattern) {
        0 -> {
            drawRule(ink, inset, size.height * TOP_RULE)
            drawRule(ink, inset, size.height * (1 - TOP_RULE))
        }

        1 -> {
            drawRule(ink, inset, size.height * TOP_RULE)
            drawRule(ink, inset, size.height * TOP_RULE + inset * RULE_GAP)
        }

        2 -> drawFrame(ink, inset)

        3 -> {
            drawRule(ink, inset, size.height * (1 - TOP_RULE))
            drawRule(ink, inset, size.height * (1 - TOP_RULE) - inset * RULE_GAP)
        }

        4 -> {
            drawFrame(ink, inset)
            drawRule(ink, inset, size.height * TOP_RULE)
        }

        else -> drawRule(ink, inset, size.height * (1 - TOP_RULE))
    }
}

private fun DrawScope.drawRule(ink: Color, inset: Float, y: Float) {
    drawLine(
        color = ink.copy(alpha = RULE_ALPHA),
        start = Offset(inset, y),
        end = Offset(size.width - inset, y),
        strokeWidth = HAIRLINE,
    )
}

private fun DrawScope.drawFrame(ink: Color, inset: Float) {
    drawRect(
        color = ink.copy(alpha = RULE_ALPHA),
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2, size.height - inset * 2),
        style = Stroke(width = HAIRLINE),
    )
}

/**
 * Значки рисуются векторно, а не иконочным шрифтом: шрифт пришлось бы тащить
 * отдельным файлом, а рисунок здесь и так на Canvas.
 */
private fun DrawScope.drawOrnament(
    ornament: SpineOrnament,
    center: Offset,
    side: Float,
    ink: Color,
) {
    val color = ink.copy(alpha = ORNAMENT_ALPHA)
    val radius = side / 2
    val stroke = Stroke(width = HAIRLINE * ORNAMENT_STROKE)

    when (ornament) {
        SpineOrnament.CIRCLE_OUTLINE ->
            drawCircle(color, radius, center, style = stroke)

        SpineOrnament.CIRCLE_FILLED ->
            drawCircle(color, radius, center)

        SpineOrnament.CROSS -> {
            drawLine(
                color,
                Offset(center.x - radius, center.y),
                Offset(center.x + radius, center.y),
                stroke.width,
            )
            drawLine(
                color,
                Offset(center.x, center.y - radius),
                Offset(center.x, center.y + radius),
                stroke.width,
            )
        }

        SpineOrnament.TICK -> drawPath(
            path = Path().apply {
                moveTo(center.x - radius, center.y)
                lineTo(center.x - radius / 3, center.y + radius / 2)
                lineTo(center.x + radius, center.y - radius / 2)
            },
            color = color,
            style = stroke,
        )

        SpineOrnament.TWO_LINES -> {
            drawLine(
                color,
                Offset(center.x - radius, center.y - radius / 3),
                Offset(center.x + radius, center.y - radius / 3),
                stroke.width,
            )
            drawLine(
                color,
                Offset(center.x - radius, center.y + radius / 3),
                Offset(center.x + radius, center.y + radius / 3),
                stroke.width,
            )
        }

        SpineOrnament.ARROW_UP -> drawPath(
            path = Path().apply {
                moveTo(center.x, center.y - radius)
                lineTo(center.x + radius, center.y + radius / 2)
                lineTo(center.x - radius, center.y + radius / 2)
                close()
            },
            color = color,
            style = stroke,
        )

        SpineOrnament.WAVE -> drawPath(
            path = Path().apply {
                moveTo(center.x - radius, center.y)
                quadraticTo(center.x - radius / 2, center.y - radius, center.x, center.y)
                quadraticTo(center.x + radius / 2, center.y + radius, center.x + radius, center.y)
            },
            color = color,
            style = stroke,
        )

        SpineOrnament.TRIANGLE -> drawPath(
            path = Path().apply {
                moveTo(center.x, center.y + radius)
                lineTo(center.x + radius, center.y - radius / 2)
                lineTo(center.x - radius, center.y - radius / 2)
                close()
            },
            color = color,
        )
    }
}

private const val DJB2_SEED = 5381
private const val DJB2_FACTOR = 33

private const val MIN_WIDTH = 36
private const val WIDTH_STEPS = 5
private const val WIDTH_STEP = 7
private const val MIN_HEIGHT = 160
private const val HEIGHT_STEPS = 6
private const val HEIGHT_STEP = 10
private const val ORNAMENT_SKIP = 3
private const val PATTERNS = 6
private const val LIGHT_BACKGROUND = 0.55f

private const val EDGE_STOP = 0.18f
private const val INSET_SHARE = 0.18f
private const val TOP_RULE = 0.07f
private const val RULE_GAP = 0.5f
private const val RULE_ALPHA = 0.45f
private const val HAIRLINE = 1f
private const val ORNAMENT_SHARE = 0.34f
private const val ORNAMENT_GAP = 2.2f
private const val ORNAMENT_ALPHA = 0.7f
private const val ORNAMENT_STROKE = 1.4f
private const val TITLE_MARGIN = 3f
private const val TITLE_ROTATION = 90f
