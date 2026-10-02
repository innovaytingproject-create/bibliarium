package com.bibliarium.app.probe

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibliarium.app.R
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.ui.shelf.SpineDrawing
import com.bibliarium.app.ui.shelf.SpineLook
import com.bibliarium.app.ui.shelf.SpineOrnament
import com.bibliarium.app.ui.shelf.drawOrnament
import com.bibliarium.app.ui.shelf.drawPattern
import com.bibliarium.app.ui.shelf.drawSpine
import com.bibliarium.app.ui.shelf.drawSpineText
import com.bibliarium.app.ui.shelf.prepareSpine
import com.bibliarium.app.ui.theme.SpineStyleTokens
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Куда уходит время при отрисовке корешка.
 *
 * На полке из 500 книг `dumpsys gfxinfo` показал пропуски кадров, причём
 * видеокарта простаивала, а пропуски помечены «медленный поток интерфейса».
 * То есть время уходит на процессоре, в самой отрисовке. Эта проба отвечает,
 * в какой её части.
 *
 * Главное деление здесь — раскладка против отрисовки. Раскладка считается
 * один раз на книгу и размер: `prepareSpine` живёт в `drawWithCache`.
 * Отрисовка повторяется на каждом кадре, и укладываться в бюджет кадра
 * должна именно она.
 *
 * Рисуем в заранее созданные Bitmap, то есть программно: считается ровно
 * работа процессора, из-за которой пропускаются кадры, и ничего больше —
 * ни выделение памяти под картинки, ни работа видеокарты.
 */
@RunWith(AndroidJUnit4::class)
class SpineCostProbeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val density = Density(context.resources.displayMetrics.density)
    private val measurer = TextMeasurer(
        createFontFamilyResolver(context),
        density,
        LayoutDirection.Ltr,
    )

    // Те же шрифты и размеры, что у настоящего корешка: на дешёвом
    // системном шрифте замер показал бы не нашу цену.
    private val titleStyle = TextStyle(
        fontFamily = FontFamily(Font(R.font.literata, FontWeight.Medium)),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 14.sp,
        letterSpacing = 0.02.em,
        color = Color.White,
    )
    private val authorStyle = TextStyle(
        fontFamily = FontFamily(Font(R.font.inter, FontWeight.Medium)),
        fontSize = 9.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 11.sp,
        letterSpacing = 0.06.em,
        color = Color.White.copy(alpha = AUTHOR_ALPHA),
    )

    private val style = SpineStyleTokens(
        corner = CORNER.dp,
        embossed = true,
        patterned = true,
        highlight = Color.White.copy(alpha = HIGHLIGHT_ALPHA),
        shade = Color.Black.copy(alpha = SHADE_ALPHA),
    )

    private val looks = List(SPINES) { index ->
        SpineLook(
            width = (MIN_WIDTH + index % WIDTH_STEPS * WIDTH_STEP).dp,
            height = (MIN_HEIGHT + index % HEIGHT_STEPS * HEIGHT_STEP).dp,
            color = PALETTE[index % PALETTE.size],
            ink = Color.White,
            ornament = SpineOrnament.entries[index % SpineOrnament.entries.size],
            pattern = index % PATTERNS,
        )
    }

    // Названия у всех разные: одинаковые раскладка текста закэширует, и
    // замер показал бы попадание в кэш, а не прокрутку мимо новых книг.
    private val titles = List(SPINES) { "Книга номер $it: довольно длинное название" }
    private val authors = List(SPINES) { "Автор ${'А' + it % AUTHORS}" }

    /** По холсту на каждый корешок: память под картинки в замер не входит. */
    private val surfaces = looks.map { look ->
        val width = with(density) { look.width.roundToPx() }
        val height = with(density) { look.height.roundToPx() }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap.asImageBitmap()) to Size(width.toFloat(), height.toFloat())
    }

    /** Готовая раскладка — то, что в приложении лежит в кэше отрисовки. */
    private val prepared: List<SpineDrawing> = looks.mapIndexed { index, look ->
        prepare(index, look, surfaces[index].second)
    }

    @Test
    fun whereTheTimeGoesWhenASpineIsDrawn() {
        val layout = time { index, look, size -> prepare(index, look, size) }
        val frame = time { index, look, _ -> drawSpine(look, style, prepared[index]) }

        val text = time { index, _, size ->
            drawSpineText(prepared[index], size.width * INSET_SHARE)
        }
        val pattern = time { _, look, size ->
            drawPattern(look, size.width * INSET_SHARE, ornamentAtTop = true)
        }
        val ornament = time { _, look, size ->
            drawOrnament(
                look.ornament!!,
                Offset(size.width / 2, size.height / 4),
                size.width * ORNAMENT_SHARE,
                look.ink,
            )
        }
        val edge = time { _, _, _ -> drawRect(EDGE_PROBE) }
        val fill = time { _, look, _ -> drawRect(look.color) }

        val report = buildString {
            appendLine("Корешок, микросекунды на штуку (среднее по $SPINES корешкам)")
            appendLine()
            appendLine(row("раскладка (один раз)", layout))
            appendLine(row("отрисовка (каждый кадр)", frame))
            appendLine(row("  текст", text))
            appendLine(row("  узор", pattern))
            appendLine(row("  значок", ornament))
            appendLine(row("  грань", edge))
            appendLine(row("  заливка", fill))
            appendLine(row("  сумма частей", text + pattern + ornament + edge + fill))
            appendLine()
            appendLine(
                "При 60 кадрах в секунду на кадр есть 16 600 мкс, и на экране разом " +
                    "около $ON_SCREEN корешков: ${frame * ON_SCREEN} мкс на кадр. " +
                    "Раскладка в кадр не входит — она лежит в кэше отрисовки.",
            )
        }

        // note сама пишет и в logcat, и в артефакты: на API 34 эмулятор
        // умирает раньше, чем мы успеваем забрать файлы, и остаётся только лог.
        TestArtifacts.note("spine-cost", report)
    }

    // --- вспомогательное ---------------------------------------------------

    private fun prepare(index: Int, look: SpineLook, size: Size): SpineDrawing = prepareSpine(
        title = titles[index],
        author = authors[index],
        look = look,
        style = style,
        titleStyle = titleStyle,
        authorStyle = authorStyle,
        measurer = measurer,
        density = density,
        size = size,
    )

    /**
     * Среднее время одного вызова в микросекундах.
     *
     * Первые проходы выбрасываются: на них грузятся шрифты и греется JIT.
     * Без этого первый же замер завышал себя втрое и не сходился с суммой
     * частей.
     */
    private fun time(draw: DrawScope.(index: Int, look: SpineLook, size: Size) -> Unit): Long {
        repeat(WARMUPS) { pass(draw) }
        val started = System.nanoTime()
        repeat(ROUNDS) { pass(draw) }
        val spent = System.nanoTime() - started
        return spent / (ROUNDS.toLong() * looks.size) / NANOS_IN_MICRO
    }

    private fun pass(draw: DrawScope.(index: Int, look: SpineLook, size: Size) -> Unit) {
        looks.forEachIndexed { index, look ->
            val (canvas, size) = surfaces[index]
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, size) {
                draw(index, look, size)
            }
        }
    }

    private fun row(name: String, microseconds: Long): String =
        name.padEnd(NAME_WIDTH) + microseconds.toString().padStart(VALUE_WIDTH)

    private companion object {
        const val SPINES = 40
        const val WARMUPS = 2
        const val ROUNDS = 8
        const val NAME_WIDTH = 26
        const val VALUE_WIDTH = 6
        const val NANOS_IN_MICRO = 1_000

        const val MIN_WIDTH = 36
        const val WIDTH_STEPS = 5
        const val WIDTH_STEP = 7
        const val MIN_HEIGHT = 160
        const val HEIGHT_STEPS = 6
        const val HEIGHT_STEP = 10
        const val PATTERNS = 6
        const val AUTHORS = 30

        const val CORNER = 3
        const val INSET_SHARE = 0.18f
        const val ORNAMENT_SHARE = 0.34f
        const val HIGHLIGHT_ALPHA = 0.10f
        const val SHADE_ALPHA = 0.18f
        const val AUTHOR_ALPHA = 0.75f

        /**
         * Кисть грани теперь готовится заранее, вместе с раскладкой: в кадре
         * остаётся только залить ею прямоугольник.
         */
        val EDGE_PROBE = Brush.horizontalGradient(
            0f to Color.White.copy(alpha = HIGHLIGHT_ALPHA),
            EDGE_STOP to Color.Transparent,
            1f - EDGE_STOP to Color.Transparent,
            1f to Color.Black.copy(alpha = SHADE_ALPHA),
        )

        /** Примерно столько корешков видно на экране телефона разом. */
        const val ON_SCREEN = 24

        val PALETTE = listOf(
            Color(0xFF7D4F3A),
            Color(0xFF3E5C4B),
            Color(0xFF2F4858),
            Color(0xFF8C6239),
            Color(0xFF5B4B8A),
        )
    }
}

private const val EDGE_STOP = 0.18f
