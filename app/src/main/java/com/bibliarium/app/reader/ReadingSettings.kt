package com.bibliarium.app.reader

/**
 * Настройки чтения в человеческих единицах.
 *
 * Readium хранит их по-своему: размер и поля — множителями, тема — своим
 * перечислением. Экран же показывает «18», «средние», «1.7», поэтому между
 * ними есть этот слой перевода, и он один на всё приложение.
 */
data class ReadingSettings(
    val font: ReadingFont,
    /** Кегль в sp, 14..26. */
    val size: Int,
    val margins: ReadingMargins,
    val spacing: ReadingSpacing,
    val theme: ReaderTheme,
    /** Яркость подсветки 0..1. */
    val brightness: Float,
)

enum class ReadingFont(val title: String) {
    LORA("Lora"),
    LITERATA("Literata"),
    PLEX_SANS("Plex Sans"),
    SYSTEM("Системный"),
}

enum class ReadingMargins(val multiplier: Double) {
    NARROW(0.6),
    MEDIUM(1.0),
    WIDE(1.6),
}

enum class ReadingSpacing(val value: Double, val label: String) {
    TIGHT(1.4, "1.4"),
    NORMAL(1.7, "1.7"),
    LOOSE(2.0, "2.0"),
}

/** Базовый кегль из макета: от него считается множитель для Readium. */
const val BASE_FONT_SIZE = 18
