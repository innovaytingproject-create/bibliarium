package com.bibliarium.app.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Значки экрана чтения. Рисуются на Canvas — ни файлов, ни зависимостей. */
enum class ReaderIconKind {
    BACK,
    TOC,
    BOOKMARK,
    HIGHLIGHT,
    SETTINGS,
    SEARCH,
    CLOSE,
    BRIGHTNESS,
}

/**
 * Кнопка-значок.
 *
 * Область нажатия 40dp, как в макете: по значку 20dp пальцем не попасть.
 * Описание нужно и человеку с озвучкой, и проверке: нарисованный значок
 * иначе не найти на экране.
 */
@Composable
fun ReaderIcon(
    icon: ReaderIconKind,
    tint: Color,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(TOUCH_TARGET.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        val inset = (size.minDimension - GLYPH.dp.toPx()) / 2
        drawGlyph(icon, tint, inset)
    }
}

private fun DrawScope.drawGlyph(icon: ReaderIconKind, tint: Color, inset: Float) {
    val side = size.minDimension - inset * 2
    val left = inset
    val top = inset
    val right = left + side
    val bottom = top + side
    val stroke = Stroke(width = side * STROKE_SHARE)
    val centerX = left + side / 2
    val centerY = top + side / 2

    when (icon) {
        ReaderIconKind.BACK -> drawPath(
            path = Path().apply {
                moveTo(centerX + side / 4, top)
                lineTo(centerX - side / 4, centerY)
                lineTo(centerX + side / 4, bottom)
            },
            color = tint,
            style = stroke,
        )

        // Оглавление: три линии, как в макете.
        ReaderIconKind.TOC -> listOf(top, centerY, bottom).forEach { y ->
            drawLine(tint, Offset(left, y), Offset(right, y), stroke.width)
        }

        ReaderIconKind.BOOKMARK -> drawPath(
            path = Path().apply {
                moveTo(left + side / 5, top)
                lineTo(right - side / 5, top)
                lineTo(right - side / 5, bottom)
                lineTo(centerX, bottom - side / 3)
                lineTo(left + side / 5, bottom)
                close()
            },
            color = tint,
            style = stroke,
        )

        // Выделение: буква «А» с чертой под ней.
        ReaderIconKind.HIGHLIGHT -> {
            drawLetterA(tint, left, top, side, stroke)
            drawLine(
                tint,
                Offset(left, bottom + stroke.width),
                Offset(right, bottom + stroke.width),
                stroke.width * UNDERLINE_WEIGHT,
            )
        }

        // Настройки: «А» с плюсом — размер текста.
        ReaderIconKind.SETTINGS -> {
            drawLetterA(tint, left, top, side * PLUS_SHRINK, stroke)
            val plusX = right - side / 6
            val plusY = top + side / 5
            val arm = side / 6
            drawLine(tint, Offset(plusX - arm, plusY), Offset(plusX + arm, plusY), stroke.width)
            drawLine(tint, Offset(plusX, plusY - arm), Offset(plusX, plusY + arm), stroke.width)
        }

        ReaderIconKind.SEARCH -> {
            val radius = side * SEARCH_RADIUS
            drawCircle(tint, radius, Offset(left + radius, top + radius), style = stroke)
            drawLine(
                tint,
                Offset(left + radius * SEARCH_TAIL, top + radius * SEARCH_TAIL),
                Offset(right, bottom),
                stroke.width,
            )
        }

        ReaderIconKind.CLOSE -> {
            drawLine(tint, Offset(left, top), Offset(right, bottom), stroke.width)
            drawLine(tint, Offset(right, top), Offset(left, bottom), stroke.width)
        }

        // Яркость: солнце с лучами, как в макете рядом с ползунком.
        ReaderIconKind.BRIGHTNESS -> {
            drawCircle(tint, side * SUN_RADIUS, Offset(centerX, centerY), style = stroke)
            repeat(SUN_RAYS) { index ->
                val angle = index * (2 * PI / SUN_RAYS)
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(
                    tint,
                    Offset(centerX + dx * side * RAY_START, centerY + dy * side * RAY_START),
                    Offset(centerX + dx * side * RAY_END, centerY + dy * side * RAY_END),
                    stroke.width,
                )
            }
        }
    }
}

/**
 * Тот же значок, но без нажатия и без описания: он ничего не делает,
 * а только подписывает соседний ползунок.
 */
@Composable
fun ReaderGlyph(icon: ReaderIconKind, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(GLYPH.dp)) {
        drawGlyph(icon, tint, inset = 0f)
    }
}

private fun DrawScope.drawLetterA(tint: Color, left: Float, top: Float, side: Float, stroke: Stroke) {
    val centerX = left + side / 2
    val bottom = top + side
    drawPath(
        path = Path().apply {
            moveTo(left, bottom)
            lineTo(centerX, top)
            lineTo(left + side, bottom)
        },
        color = tint,
        style = stroke,
    )
    drawLine(
        tint,
        Offset(left + side / 5, bottom - side / 3),
        Offset(left + side - side / 5, bottom - side / 3),
        stroke.width,
    )
}

private const val TOUCH_TARGET = 40
private const val GLYPH = 20
private const val STROKE_SHARE = 0.09f
private const val UNDERLINE_WEIGHT = 1.6f
private const val SEARCH_RADIUS = 0.35f
private const val SEARCH_TAIL = 1.7f
private const val PLUS_SHRINK = 0.8f
private const val SUN_RADIUS = 0.22f
private const val SUN_RAYS = 8
private const val RAY_START = 0.33f
private const val RAY_END = 0.48f
