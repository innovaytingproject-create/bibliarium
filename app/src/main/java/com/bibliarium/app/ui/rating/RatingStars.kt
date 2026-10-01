package com.bibliarium.app.ui.rating

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bibliarium.app.domain.BookRating
import com.bibliarium.app.ui.theme.BibliariumTheme

/**
 * Ряд из пяти звёзд.
 *
 * Звёзды — обычные знаки, а не картинки: их видно и человеку с озвучкой,
 * и проверке, а в APK не добавляется ни одного файла. У каждой своё
 * описание, поэтому нажать можно именно ту, которую нужно.
 */
@Composable
fun RatingStars(
    value: Int?,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    Row(modifier = modifier) {
        (BookRating.MIN..BookRating.MAX).forEach { star ->
            val filled = value != null && star <= value
            Text(
                text = if (filled) FILLED else EMPTY,
                style = type.headlineSm,
                color = if (filled) colors.accent else colors.textSecondary,
                modifier = Modifier
                    .clickable { onPick(star) }
                    .padding(horizontal = BibliariumTheme.spacing.xs)
                    .semantics { contentDescription = "$label $star" },
            )
        }
    }
}

/** Та же оценка, но только для показа: тапать нечего. */
@Composable
fun RatingStarsStatic(value: Int, modifier: Modifier = Modifier) {
    val colors = BibliariumTheme.colors
    val type = BibliariumTheme.type

    Text(
        text = buildString {
            repeat(BookRating.MAX) { index ->
                append(if (index < value) FILLED else EMPTY)
            }
        },
        style = type.bodyMd,
        color = colors.accent,
        modifier = modifier,
    )
}

private const val FILLED = "★"
private const val EMPTY = "☆"
