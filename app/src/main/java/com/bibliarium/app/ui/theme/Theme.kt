package com.bibliarium.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalColorTokens = staticCompositionLocalOf<ColorTokens> {
    error("ColorTokens не предоставлены — оберните экран в BibliariumTheme")
}
val LocalTypeTokens = staticCompositionLocalOf<TypeTokens> {
    error("TypeTokens не предоставлены — оберните экран в BibliariumTheme")
}
val LocalSpineStyleTokens = staticCompositionLocalOf<SpineStyleTokens> {
    error("SpineStyleTokens не предоставлены — оберните экран в BibliariumTheme")
}
val LocalSpacingTokens = staticCompositionLocalOf { SpacingTokens() }
val LocalShapeTokens = staticCompositionLocalOf { ShapeTokens() }

/** Единственная точка доступа к токенам из экранов. */
object BibliariumTheme {
    val colors: ColorTokens
        @Composable @ReadOnlyComposable get() = LocalColorTokens.current

    val type: TypeTokens
        @Composable @ReadOnlyComposable get() = LocalTypeTokens.current

    val spacing: SpacingTokens
        @Composable @ReadOnlyComposable get() = LocalSpacingTokens.current

    val shapes: ShapeTokens
        @Composable @ReadOnlyComposable get() = LocalShapeTokens.current

    val spine: SpineStyleTokens
        @Composable @ReadOnlyComposable get() = LocalSpineStyleTokens.current
}

@Composable
fun BibliariumTheme(
    variant: ThemeVariant = ThemeVariant.ARCHIVE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = colorTokensFor(variant, darkTheme)
    val type = typeTokensFor(variant)
    val spine = spineStyleFor(variant)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalColorTokens provides colors,
        LocalTypeTokens provides type,
        LocalSpineStyleTokens provides spine,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(darkTheme),
            typography = type.toMaterialTypography(),
            content = content,
        )
    }
}

/**
 * Material-схема выводится из токенов, а не задаётся отдельно: компоненты M3
 * (Surface, Text, TextField) должны попадать в ту же палитру.
 */
private fun ColorTokens.toMaterialScheme(dark: Boolean) =
    if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceRecessed,
            onSurfaceVariant = textSecondary,
            outline = line,
            outlineVariant = line,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            background = background,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceRecessed,
            onSurfaceVariant = textSecondary,
            outline = line,
            outlineVariant = line,
        )
    }

private fun TypeTokens.toMaterialTypography() = Typography(
    headlineLarge = headlineLg,
    headlineMedium = headlineMd,
    headlineSmall = headlineSm,
    titleLarge = headlineSm,
    bodyLarge = bodyLg,
    bodyMedium = bodyMd,
    bodySmall = bodySm,
    labelLarge = labelLg,
    labelMedium = labelMd,
    labelSmall = labelSm,
)
