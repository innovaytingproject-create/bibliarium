package com.bibliarium.app.shelftests

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bibliarium.app.TestArtifacts
import com.bibliarium.app.ui.shelf.BookSpine
import com.bibliarium.app.ui.theme.BibliariumTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Корешок и крупный системный шрифт.
 *
 * Раскладка названия считается один раз и лежит в кэше отрисовки — это и
 * было оптимизацией прокрутки. У кэша есть цена: если он не сбросится при
 * смене системного размера шрифта, человек с крупным шрифтом получит буквы
 * побольше в разметке от мелких, и название обрежется.
 *
 * Проверяется это единственным честным способом: тот же корешок рисуется
 * при обычном и при крупном шрифте, и картинки сравниваются. Совпали —
 * значит раскладка не пересчиталась.
 */
@RunWith(AndroidJUnit4::class)
class SpineFontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val displayDensity = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .resources
        .displayMetrics
        .density

    @Test
    fun spineIsLaidOutAgainWhenSystemFontGrows() {
        val scale = mutableFloatStateOf(NORMAL_SCALE)

        compose.setContent {
            BibliariumTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(displayDensity, scale.floatValue),
                ) {
                    BookSpine(title = TITLE, author = AUTHOR)
                }
            }
        }

        val normal = capture()
        TestArtifacts.screenshot("spine-font-normal")

        compose.runOnUiThread { scale.floatValue = BIG_SCALE }
        compose.waitForIdle()

        val big = capture()
        TestArtifacts.screenshot("spine-font-big")

        assertFalse(
            "Корешок нарисован одинаково при обычном и крупном системном шрифте: " +
                "раскладка осталась в кэше, и у человека с крупным шрифтом " +
                "название обрежется по старой разметке",
            normal.sameAs(big),
        )
    }

    private fun capture() = compose.onNodeWithContentDescription(TITLE)
        .captureToImage()
        .asAndroidBitmap()

    private companion object {
        const val TITLE = "Долгая прогулка до самого конца"
        const val AUTHOR = "Иванов"
        const val NORMAL_SCALE = 1f

        /** Самый крупный системный шрифт в настройках Android. */
        const val BIG_SCALE = 1.3f
    }
}
