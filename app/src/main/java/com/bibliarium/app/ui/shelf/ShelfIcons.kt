package com.bibliarium.app.ui.shelf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Значки интерфейса полки.
 *
 * Рисуются на Canvas, а не берутся из иконочного набора: набор — это ещё одна
 * зависимость и ещё один шрифт в APK ради семи фигур, каждая из которых
 * складывается из двух-трёх линий.
 */
enum class ShelfIcon {
    /** Настройка группировки: ползунки. */
    TUNE,

    /** Режим корешков: вертикальные бруски. */
    SPINES,

    /** Режим сетки: четыре клетки. */
    GRID,

    LIBRARY,
    SEARCH,
    ADD,
    MORE,
}

@Composable
fun ShelfIconImage(
    icon: ShelfIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = ICON_SIZE.dp,
) {
    Canvas(modifier = modifier.size(size)) { drawIcon(icon, tint) }
}

private fun DrawScope.drawIcon(icon: ShelfIcon, tint: Color) {
    val stroke = Stroke(width = size.minDimension * STROKE_SHARE)
    val step = size.height / 4

    when (icon) {
        ShelfIcon.TUNE -> {
            // Три линии с бегунками на разной высоте.
            listOf(step, step * 2, step * 3).forEachIndexed { index, y ->
                drawLine(
                    color = tint,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke.width,
                )
                val knobX = size.width * KNOBS[index]
                drawCircle(tint, radius = size.width * KNOB_SHARE, center = Offset(knobX, y))
            }
        }

        ShelfIcon.SPINES -> {
            val barWidth = size.width / 5
            listOf(0f, barWidth * 2, barWidth * 4).forEach { x ->
                drawRect(
                    color = tint,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    style = stroke,
                )
            }
        }

        ShelfIcon.GRID -> {
            val cell = size.width / 2 - size.width * GRID_GAP
            listOf(0f, size.width - cell).forEach { x ->
                listOf(0f, size.height - cell).forEach { y ->
                    drawRect(
                        color = tint,
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                        style = stroke,
                    )
                }
            }
        }

        ShelfIcon.LIBRARY -> {
            // Раскрытая книга: корешок посередине и две страницы.
            drawRect(color = tint, topLeft = Offset.Zero, size = size, style = stroke)
            drawLine(
                color = tint,
                start = Offset(size.width / 2, 0f),
                end = Offset(size.width / 2, size.height),
                strokeWidth = stroke.width,
            )
        }

        ShelfIcon.SEARCH -> {
            val radius = size.minDimension * SEARCH_RADIUS
            drawCircle(
                color = tint,
                radius = radius,
                center = Offset(radius, radius),
                style = stroke,
            )
            drawLine(
                color = tint,
                start = Offset(radius * SEARCH_TAIL, radius * SEARCH_TAIL),
                end = Offset(size.width, size.height),
                strokeWidth = stroke.width,
            )
        }

        ShelfIcon.ADD -> {
            drawCircle(
                color = tint,
                radius = size.minDimension / 2 - stroke.width,
                center = center,
                style = stroke,
            )
            val arm = size.minDimension / 4
            drawLine(
                color = tint,
                start = Offset(center.x - arm, center.y),
                end = Offset(center.x + arm, center.y),
                strokeWidth = stroke.width,
            )
            drawLine(
                color = tint,
                start = Offset(center.x, center.y - arm),
                end = Offset(center.x, center.y + arm),
                strokeWidth = stroke.width,
            )
        }

        ShelfIcon.MORE -> {
            val radius = size.minDimension * DOT_SHARE
            listOf(radius, center.x, size.width - radius).forEach { x ->
                drawCircle(tint, radius = radius, center = Offset(x, center.y))
            }
        }
    }
}

/** Галочка «выбрано» для списка группировок. */
internal fun DrawScope.drawCheck(tint: Color) {
    val stroke = size.minDimension * STROKE_SHARE
    drawPath(
        path = Path().apply {
            moveTo(0f, size.height / 2)
            lineTo(size.width / 3, size.height * CHECK_LOW)
            lineTo(size.width, size.height / 4)
        },
        color = tint,
        style = Stroke(width = stroke),
    )
}

private const val ICON_SIZE = 20
private const val STROKE_SHARE = 0.1f
private const val KNOB_SHARE = 0.09f
private const val KNOB_GAP = 0.3f
private val KNOBS = listOf(0.7f, KNOB_GAP, 0.55f)
private const val GRID_GAP = 0.06f
private const val SEARCH_RADIUS = 0.35f
private const val SEARCH_TAIL = 1.7f
private const val DOT_SHARE = 0.11f
private const val CHECK_LOW = 0.8f
