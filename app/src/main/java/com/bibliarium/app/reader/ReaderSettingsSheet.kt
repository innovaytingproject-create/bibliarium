package com.bibliarium.app.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bibliarium.app.R

/**
 * Настройки чтения — лист снизу.
 *
 * У PDF из всего этого есть только тема и яркость: шрифт, размер, поля
 * и интервал там менять нечего. Их не показываем вовсе, а не делаем серыми:
 * серая кнопка читается как поломка.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    palette: ReaderPalette,
    settings: ReadingSettings,
    forEpub: Boolean,
    onFont: (ReadingFont) -> Unit,
    onSize: (Int) -> Unit,
    onMargins: (ReadingMargins) -> Unit,
    onSpacing: (ReadingSpacing) -> Unit,
    onTheme: (ReaderTheme) -> Unit,
    onBrightness: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(SHEET_HEIGHT.dp)
            .clip(RoundedCornerShape(topStart = SHEET_CORNER.dp, topEnd = SHEET_CORNER.dp))
            .background(palette.surface)
            .padding(horizontal = SIDE.dp),
    ) {
        Handle(palette)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = GAP.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = textOf(R.string.reader_settings),
                style = TextStyle(fontSize = TITLE_SIZE.sp),
                color = palette.text,
            )
            ReaderIcon(ReaderIconKind.CLOSE, palette.textSecondary, "Закрыть", onClose)
        }

        if (forEpub) {
            Label(textOf(R.string.reader_font), palette)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
            ) {
                ReadingFont.entries.forEach { font ->
                    FontChip(
                        font = font,
                        active = font == settings.font,
                        palette = palette,
                        onClick = { onFont(font) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GAP.dp),
                horizontalArrangement = Arrangement.spacedBy(GAP.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Label(textOf(R.string.reader_size), palette)
                    Stepper(
                        value = settings.size,
                        palette = palette,
                        onChange = onSize,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Label(textOf(R.string.reader_margins), palette)
                    Segments(
                        options = ReadingMargins.entries.map { textOf(it.labelRes()) },
                        activeIndex = ReadingMargins.entries.indexOf(settings.margins),
                        palette = palette,
                        onPick = { onMargins(ReadingMargins.entries[it]) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = GAP.dp),
                horizontalArrangement = Arrangement.spacedBy(GAP.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Label(textOf(R.string.reader_spacing), palette)
                    Segments(
                        options = ReadingSpacing.entries.map { it.label },
                        activeIndex = ReadingSpacing.entries.indexOf(settings.spacing),
                        palette = palette,
                        onPick = { onSpacing(ReadingSpacing.entries[it]) },
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Label(textOf(R.string.reader_theme), palette)
                    Themes(active = settings.theme, palette = palette, onPick = onTheme)
                }
            }
        } else {
            Label(textOf(R.string.reader_theme), palette)
            Themes(active = settings.theme, palette = palette, onPick = onTheme)
        }

        Label(
            text = textOf(R.string.reader_brightness),
            palette = palette,
            modifier = Modifier.padding(top = GAP.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp),
        ) {
            ReaderGlyph(ReaderIconKind.BRIGHTNESS, palette.textSecondary)
            Slider(
                value = settings.brightness,
                onValueChange = onBrightness,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.line,
                ),
                track = { state ->
                    SliderDefaults.Track(
                        sliderState = state,
                        colors = SliderDefaults.colors(
                            activeTrackColor = palette.accent,
                            inactiveTrackColor = palette.line,
                        ),
                        drawStopIndicator = null,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Яркость" },
            )
        }
    }
}

@Composable
private fun Handle(palette: ReaderPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GAP.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(HANDLE_WIDTH.dp)
                .height(HANDLE_HEIGHT.dp)
                .clip(RoundedCornerShape(HANDLE_HEIGHT.dp))
                .background(palette.line),
        )
    }
}

@Composable
private fun Label(text: String, palette: ReaderPalette, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = TextStyle(fontSize = LABEL_SIZE.sp),
        color = palette.textSecondary,
        modifier = modifier.padding(bottom = LABEL_GAP.dp),
    )
}

/** Чип шрифта набран своим начертанием — видно, что выбираешь. */
@Composable
private fun FontChip(
    font: ReadingFont,
    active: Boolean,
    palette: ReaderPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(CHIP_HEIGHT.dp)
            .clip(RoundedCornerShape(CHIP_CORNER.dp))
            .border(
                width = if (active) ACTIVE_BORDER.dp else HAIRLINE.dp,
                color = if (active) palette.accent else palette.line,
                shape = RoundedCornerShape(CHIP_CORNER.dp),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = font.title },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = font.title,
            style = TextStyle(fontSize = CHIP_SIZE.sp, fontFamily = font.preview()),
            color = if (active) palette.accent else palette.text,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Stepper(value: Int, palette: ReaderPalette, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .height(CHIP_HEIGHT.dp)
            .clip(RoundedCornerShape(CHIP_CORNER.dp))
            .background(palette.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("−", palette, "Уменьшить текст") {
            onChange((value - 1).coerceAtLeast(MIN_SIZE))
        }
        Text(
            text = value.toString(),
            style = TextStyle(fontSize = CHIP_SIZE.sp),
            color = palette.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(STEPPER_VALUE.dp),
        )
        StepperButton("+", palette, "Увеличить текст") {
            onChange((value + 1).coerceAtMost(MAX_SIZE))
        }
    }
}

@Composable
private fun StepperButton(
    sign: String,
    palette: ReaderPalette,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(TOUCH.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = sign, style = TextStyle(fontSize = CHIP_SIZE.sp), color = palette.text)
    }
}

@Composable
private fun Segments(
    options: List<String>,
    activeIndex: Int,
    palette: ReaderPalette,
    onPick: (Int) -> Unit,
) {
    BoxWithConstraints {
        // «Средние» и «Широкие» не влезали в треть колонки и обрезались
        // на полуслове. Размер подбирается по самой длинной подписи, а не
        // задаётся на глаз: на узком экране и при крупном системном шрифте
        // слово должно остаться целым.
        val cell = maxWidth / options.size - (SEGMENT_PADDING * 2).dp
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val fontSize = remember(options, cell, density) {
            var candidate = SEGMENT_SIZE
            while (candidate > SEGMENT_MIN_SIZE) {
                val widest = options.maxOf { option ->
                    measurer.measure(option, TextStyle(fontSize = candidate.sp)).size.width
                }
                if (with(density) { widest.toDp() } <= cell) break
                candidate--
            }
            candidate
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHIP_HEIGHT.dp)
                .clip(RoundedCornerShape(CHIP_CORNER.dp))
                .background(palette.background),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, option ->
                val active = index == activeIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(CHIP_HEIGHT.dp)
                        .padding(SEGMENT_PADDING.dp)
                        .clip(RoundedCornerShape(SEGMENT_CORNER.dp))
                        .background(if (active) palette.surface else palette.background)
                        .clickable { onPick(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = TextStyle(fontSize = fontSize.sp),
                        color = if (active) palette.text else palette.textSecondary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Темы — тремя квадратиками нужных цветов, активный обведён акцентом. */
@Composable
private fun Themes(active: ReaderTheme, palette: ReaderPalette, onPick: (ReaderTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(CHIP_GAP.dp)) {
        ReaderTheme.entries.forEach { theme ->
            val swatch = ReaderPalette.of(theme)
            Box(
                modifier = Modifier
                    .size(SWATCH.dp)
                    .clip(RoundedCornerShape(SWATCH_CORNER.dp))
                    .background(swatch.background)
                    .border(
                        width = if (theme == active) ACTIVE_BORDER.dp else HAIRLINE.dp,
                        color = if (theme == active) palette.accent else palette.line,
                        shape = RoundedCornerShape(SWATCH_CORNER.dp),
                    )
                    .clickable { onPick(theme) }
                    .semantics { contentDescription = theme.title() },
            )
        }
    }
}

@Composable
private fun textOf(id: Int): String = androidx.compose.ui.res.stringResource(id)

private fun ReaderTheme.title(): String = when (this) {
    ReaderTheme.LIGHT -> "Светлая тема"
    ReaderTheme.SEPIA -> "Сепия"
    ReaderTheme.DARK -> "Тёмная тема"
}

private fun ReadingMargins.labelRes(): Int = when (this) {
    ReadingMargins.NARROW -> R.string.reader_margins_narrow
    ReadingMargins.MEDIUM -> R.string.reader_margins_medium
    ReadingMargins.WIDE -> R.string.reader_margins_wide
}

@Composable
private fun ReadingFont.preview(): FontFamily = when (this) {
    ReadingFont.LORA -> FontFamily(Font(R.font.lora))
    ReadingFont.LITERATA -> FontFamily(Font(R.font.literata))
    ReadingFont.PLEX_SANS -> FontFamily(Font(R.font.ibm_plex_sans))
    ReadingFont.SYSTEM -> FontFamily.Default
}

private const val SHEET_HEIGHT = 462
private const val SHEET_CORNER = 24
private const val SIDE = 20
private const val GAP = 14
private const val LABEL_GAP = 6
private const val TITLE_SIZE = 17
private const val LABEL_SIZE = 12
private const val CHIP_SIZE = 13
private const val SEGMENT_SIZE = 11

/** Мельче этого подпись уже не читается — лучше так, чем обрезок слова. */
private const val SEGMENT_MIN_SIZE = 8
private const val CHIP_HEIGHT = 44
private const val CHIP_CORNER = 12
private const val SEGMENT_CORNER = 9
private const val SEGMENT_PADDING = 3
private const val CHIP_GAP = 8
private const val HANDLE_WIDTH = 36
private const val HANDLE_HEIGHT = 4
private const val SWATCH = 36
private const val SWATCH_CORNER = 10
private const val ACTIVE_BORDER = 2
private const val HAIRLINE = 1
private const val TOUCH = 40
private const val STEPPER_VALUE = 44
private const val MIN_SIZE = 14
private const val MAX_SIZE = 26
