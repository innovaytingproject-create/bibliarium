package com.bibliarium.app.ui

/**
 * Метки для инструментальных тестов. Лежат в основном коде намеренно: иначе
 * тест пришлось бы искать элементы по видимому тексту, и любая правка
 * формулировки ломала бы проверку.
 */
object TestTags {
    const val FULL_ACCESS_SWITCH = "settings:full-access-switch"
    const val SCAN_STAGE = "scan:stage"
}
