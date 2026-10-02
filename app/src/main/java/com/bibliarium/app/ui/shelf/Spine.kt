package com.bibliarium.app.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bibliarium.app.ui.theme.BibliariumTheme
import com.bibliarium.app.ui.theme.SpineStyleTokens

/**
 * Корешок книги.
 *
 * Рисуется из названия, а не берётся из файла: обложки есть не у всех книг,
 * а полка должна выглядеть цельной. Одна и та же книга всегда выглядит
 * одинаково — весь вид выводится из хэша `название|автор` (раздел 5 ТЗ).
 *
 * Рисование идёт одним Canvas: ни Bitmap, ни вложенных composable. На полке
 * из сотен книг это и есть разница между плавной прокруткой и слайд-шоу.
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

/** Из чего считается вид корешка. */
data class SpineKey(val title: String, val author: String?)

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
    /** Цвет соседа слева: два одинаковых корешка рядом сливаются в пятно. */
    avoid: Color? = null,
): SpineLook {
    val hash = spineHash(title, author)
    var index = hash % palette.size
    if (avoid != null && palette[index] == avoid) {
        index = (index + 1) % palette.size
    }
    val color = palette[index]
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

/**
 * Виды корешков для целого ряда.
 *
 * Считается рядом, а не поодиночке, ровно ради одного: соседи не должны быть
 * одного цвета. Хэш про соседей ничего не знает, поэтому совпадения разводятся
 * здесь — следующим цветом палитры по кругу.
 */
@Composable
fun rememberRowLooks(keys: List<SpineKey>): List<SpineLook> {
    val colors = BibliariumTheme.colors
    return remember(keys, colors) {
        val looks = mutableListOf<SpineLook>()
        keys.forEach { key ->
            looks += spineLookOf(
                title = key.title,
                author = key.author,
                palette = colors.spinePalette,
                inkDark = colors.spineInkDark,
                inkLight = colors.spineInkLight,
                avoid = looks.lastOrNull()?.color,
            )
        }
        looks
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

/** Корешок на полке: размер свой, из хэша. */
@Composable
fun BookSpine(
    title: String,
    author: String?,
    modifier: Modifier = Modifier,
    look: SpineLook = rememberSpineLook(title, author),
    onClick: (() -> Unit)? = null,
) {
    SpineFace(
        title = title,
        author = author,
        look = look,
        modifier = modifier
            .size(look.width, look.height)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
    )
}

/**
 * Корешок в заданном размере — для сетки и карточек, где размер задаёт место
 * на экране, а не хэш. Рисунок тот же.
 */
@Composable
fun SpineFace(
    title: String,
    author: String?,
    look: SpineLook,
    modifier: Modifier = Modifier,
) {
    val style = BibliariumTheme.spine
    val titleStyle = BibliariumTheme.type.spineTitle.copy(color = look.ink)
    val authorStyle = BibliariumTheme.type.spineMeta.copy(color = look.ink.copy(alpha = META_ALPHA))
    val measurer = rememberTextMeasurer()

    // Системный размер шрифта живёт здесь: при его смене меняется density,
    // а значит и лямбда ниже, а значит раскладка считается заново. Если
    // density не читать здесь, а взять изнутри отрисовки, крупный шрифт
    // человека останется с раскладкой от мелкого — и название обрежется.
    val density = LocalDensity.current

    Spacer(
        modifier = modifier
            .clip(RoundedCornerShape(style.corner))
            // Название нужно и человеку с озвучкой, и проверке: нарисованный
            // холст текстом наружу не виден.
            .semantics { contentDescription = title }
            // drawWithCache, а не drawBehind: раскладка текста считается один
            // раз на книгу и размер, а не на каждом кадре. Она же и была
            // главной ценой корешка — 339 мкс из 532 по замеру.
            .drawWithCache {
                val prepared = prepareSpine(
                    title = title,
                    author = author,
                    look = look,
                    style = style,
                    titleStyle = titleStyle,
                    authorStyle = authorStyle,
                    measurer = measurer,
                    density = density,
                    size = size,
                )
                onDrawBehind { drawSpine(look, style, prepared) }
            },
    )
}

/**
 * Всё про корешок, что не зависит от самого кадра: раскладка названия и
 * автора и кисть боковой грани.
 *
 * Считается при смене книги, размера корешка или системного шрифта — то
 * есть редко, — а не при каждой отрисовке.
 */
@Immutable
class SpineDrawing internal constructor(
    internal val titleLayout: TextLayoutResult?,
    internal val authorLayout: TextLayoutResult?,
    internal val edge: Brush?,
)

/**
 * Раскладка корешка.
 *
 * [density] передаётся отдельно, а не берётся из места отрисовки: в нём
 * сидит системный размер шрифта, и именно от него зависит, сколько строк
 * займёт название.
 */
internal fun prepareSpine(
    title: String,
    author: String?,
    look: SpineLook,
    style: SpineStyleTokens,
    titleStyle: TextStyle,
    authorStyle: TextStyle,
    measurer: TextMeasurer,
    density: Density,
    size: Size,
): SpineDrawing {
    val edge = if (style.embossed) {
        // Цилиндр: слева светлая грань, справа затенённая. Из DESIGN.md.
        Brush.horizontalGradient(
            0f to style.highlight,
            EDGE_STOP to Color.Transparent,
            1f - EDGE_STOP to Color.Transparent,
            1f to style.shade,
        )
    } else {
        null
    }

    val inset = size.width * INSET_SHARE
    val along = (size.height - inset * TITLE_MARGIN - ornamentSizeOf(size) * 2).toInt()
    if (along <= 0) return SpineDrawing(null, null, edge)

    val across = size.width - inset * 2
    val wide = size.width >= with(density) { WIDE_WIDTH.dp.toPx() }

    val titleLayout = measurer.measure(
        text = title,
        style = titleStyle,
        maxLines = if (wide) TITLE_LINES_WIDE else 1,
        overflow = TextOverflow.Ellipsis,
        constraints = Constraints(maxWidth = along),
        density = density,
    )

    val authorLayout = author
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { text ->
            measurer.measure(
                text = text,
                style = authorStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = along),
                density = density,
            )
        }
        // Влезает — значит рисуем. Запас в целый отступ выкидывал автора
        // почти со всех корешков, кроме самых широких.
        ?.takeIf { it.size.height + titleLayout.size.height <= across }

    return SpineDrawing(titleLayout, authorLayout, edge)
}

/** Значок считается от ширины, но высота ставит ему предел: на растянутом
 * корешке обложки он иначе раздувался в половину картинки. */
private fun ornamentSizeOf(size: Size): Float =
    minOf(size.width * ORNAMENT_SHARE, size.height * ORNAMENT_MAX_SHARE)

/**
 * Видимость шире `private` намеренно: проба `SpineCostProbeTest` вызывает
 * эту и соседние рисовалки напрямую и замеряет, сколько стоит каждая часть
 * корешка. Иначе замер пришлось бы делать на копии кода, а копия меряет
 * себя, а не нас.
 */
internal fun DrawScope.drawSpine(
    look: SpineLook,
    style: SpineStyleTokens,
    prepared: SpineDrawing,
) {
    drawRect(look.color)
    prepared.edge?.let { drawRect(it) }

    val inset = size.width * INSET_SHARE
    val ornamentSize = ornamentSizeOf(size)
    val ornamentAtTop = look.pattern % 2 == 1
    val ornamentCenter = Offset(
        x = size.width / 2,
        y = if (ornamentAtTop) inset * ORNAMENT_GAP else size.height - inset * ORNAMENT_GAP,
    )

    if (style.patterned) {
        drawPattern(look, inset, ornamentAtTop)
    } else {
        drawRule(look.ink, inset, size.height * TOP_RULE)
    }

    look.ornament?.let { drawOrnament(it, ornamentCenter, ornamentSize, look.ink) }

    drawSpineText(prepared, inset)
}

/**
 * Название вдоль корешка, автор — мельче и ниже.
 *
 * Сюда приходит уже посчитанная раскладка: решение, сколько строк займёт
 * название и влезает ли автор, принимается в prepareSpine. Здесь остаётся
 * только нарисовать буквы.
 */
internal fun DrawScope.drawSpineText(prepared: SpineDrawing, inset: Float) {
    val titleLayout = prepared.titleLayout ?: return
    val authorLayout = prepared.authorLayout

    val block = titleLayout.size.height +
        (authorLayout?.let { it.size.height + inset * AUTHOR_GAP } ?: 0f)

    // Поворот на 90° по часовой: так название читается сверху вниз, как на
    // настоящем корешке. Разворот идёт вокруг центра, поэтому текст остаётся
    // посередине корешка.
    rotate(degrees = TITLE_ROTATION) {
        val top = center.y - block / 2
        drawText(
            textLayoutResult = titleLayout,
            topLeft = Offset(center.x - titleLayout.size.width / 2f, top),
        )
        authorLayout?.let {
            drawText(
                textLayoutResult = it,
                topLeft = Offset(
                    x = center.x - it.size.width / 2f,
                    y = top + titleLayout.size.height + inset * AUTHOR_GAP,
                ),
            )
        }
    }
}

/**
 * Шаблоны Archive. Каждый заметно отличается от соседнего: иначе вся полка
 * выглядит набранной по одному лекалу.
 */
internal fun DrawScope.drawPattern(look: SpineLook, inset: Float, ornamentAtTop: Boolean) {
    val ink = look.ink
    val top = size.height * TOP_RULE
    val bottom = size.height * (1 - TOP_RULE)

    when (look.pattern) {
        // Две линии сверху и одна снизу.
        0 -> {
            drawRule(ink, inset, top)
            drawRule(ink, inset, top + inset * RULE_GAP)
            drawRule(ink, inset, bottom)
        }

        // Рамка по контуру.
        1 -> drawFrame(ink, inset)

        // Пояс поперёк корешка с той стороны, где нет значка.
        2 -> {
            val bandCenter = if (ornamentAtTop) bottom - inset else top + inset
            drawRect(
                color = ink.copy(alpha = BAND_ALPHA),
                topLeft = Offset(0f, bandCenter - inset / 2),
                size = Size(size.width, inset),
            )
        }

        // Три коротких штриха снизу.
        3 -> repeat(SHORT_RULES) { index ->
            val y = bottom - index * inset * RULE_GAP
            drawLine(
                color = ink.copy(alpha = RULE_ALPHA),
                start = Offset(size.width / 2 - inset, y),
                end = Offset(size.width / 2 + inset, y),
                strokeWidth = HAIRLINE,
            )
        }

        // Медальон вокруг значка.
        4 -> {
            val y = if (ornamentAtTop) inset * ORNAMENT_GAP else size.height - inset * ORNAMENT_GAP
            drawCircle(
                color = ink.copy(alpha = RULE_ALPHA),
                radius = size.width * MEDALLION_SHARE,
                center = Offset(size.width / 2, y),
                style = Stroke(width = HAIRLINE),
            )
        }

        // Чистый корешок: только значок и название.
        else -> Unit
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
internal fun DrawScope.drawOrnament(
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

/** С этой ширины название помещается в две строки. */
private const val WIDE_WIDTH = 50
private const val TITLE_LINES_WIDE = 2

private const val EDGE_STOP = 0.18f
private const val INSET_SHARE = 0.18f
private const val TOP_RULE = 0.07f
private const val RULE_GAP = 0.5f
private const val RULE_ALPHA = 0.45f
private const val BAND_ALPHA = 0.22f
private const val SHORT_RULES = 3
private const val MEDALLION_SHARE = 0.3f
private const val HAIRLINE = 1f
private const val ORNAMENT_SHARE = 0.34f
private const val ORNAMENT_MAX_SHARE = 0.06f
private const val ORNAMENT_GAP = 2.2f
private const val ORNAMENT_ALPHA = 0.7f
private const val ORNAMENT_STROKE = 1.4f
private const val META_ALPHA = 0.75f
private const val TITLE_MARGIN = 3f
private const val AUTHOR_GAP = 0.4f
private const val TITLE_ROTATION = 90f
