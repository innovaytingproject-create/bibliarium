package com.bibliarium.app.reader

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi

/**
 * Шрифты для текста книги. Файлы лежат в assets и отдаются странице EPUB
 * напрямую: подключать что-либо по сети приложение не должно.
 *
 * Оба семейства вариативные, поэтому объявляется диапазон насыщенности,
 * а не отдельный файл на каждое начертание.
 */
@OptIn(ExperimentalReadiumApi::class)
val FontFamily.Companion.LITERATA: FontFamily
    get() = FontFamily("Literata")

@OptIn(ExperimentalReadiumApi::class)
val FontFamily.Companion.LORA: FontFamily
    get() = FontFamily("Lora")

@OptIn(ExperimentalReadiumApi::class)
fun EpubNavigatorFragment.Configuration.declareReadingFonts() {
    servedAssets = listOf("fonts/.*")

    addFontFamilyDeclaration(FontFamily.LITERATA) {
        addFontFace {
            addSource("fonts/literata.ttf")
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(200..900)
        }
        addFontFace {
            addSource("fonts/literata_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(200..900)
        }
    }

    addFontFamilyDeclaration(FontFamily.LORA) {
        addFontFace {
            addSource("fonts/lora.ttf")
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(400..700)
        }
        addFontFace {
            addSource("fonts/lora_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(400..700)
        }
    }
}
