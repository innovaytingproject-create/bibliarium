package com.bibliarium.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bibliarium.app.R

/**
 * Шрифты лежат файлами в res/font — приложение офлайн, ничего не тянется по сети.
 * Все четыре семейства вариативные, ось wght Compose подставляет по FontWeight.
 */
private val Literata = FontFamily(
    Font(R.font.literata, FontWeight.Normal),
    Font(R.font.literata, FontWeight.Medium),
    Font(R.font.literata, FontWeight.SemiBold),
    Font(R.font.literata, FontWeight.Bold),
    Font(R.font.literata_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.literata_italic, FontWeight.Medium, FontStyle.Italic),
)

private val IbmPlexSans = FontFamily(
    Font(R.font.ibm_plex_sans, FontWeight.Normal),
    Font(R.font.ibm_plex_sans, FontWeight.Medium),
    Font(R.font.ibm_plex_sans, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans, FontWeight.Bold),
)

private val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal),
    Font(R.font.manrope, FontWeight.Medium),
    Font(R.font.manrope, FontWeight.SemiBold),
    Font(R.font.manrope, FontWeight.Bold),
)

private val Inter = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold),
)

/** Шкала из DESIGN.md. Размеры в sp, чтобы уважать системный масштаб шрифта. */
@Immutable
data class TypeTokens(
    val displayLg: TextStyle,
    val headlineLg: TextStyle,
    val headlineMd: TextStyle,
    val headlineSm: TextStyle,
    val bodyLg: TextStyle,
    val bodyMd: TextStyle,
    val bodySm: TextStyle,
    val spineTitle: TextStyle,
    val spineMeta: TextStyle,
    val labelLg: TextStyle,
    val labelMd: TextStyle,
    val labelSm: TextStyle,
    /** Семейство для текста самой книги — всегда Literata. */
    val reading: FontFamily,
)

internal fun typeTokensFor(variant: ThemeVariant): TypeTokens {
    val heading = when (variant) {
        ThemeVariant.ARCHIVE -> Literata
        ThemeVariant.SPINE -> Manrope
    }
    val ui = when (variant) {
        ThemeVariant.ARCHIVE -> IbmPlexSans
        ThemeVariant.SPINE -> Inter
    }
    val body = when (variant) {
        ThemeVariant.ARCHIVE -> Literata
        ThemeVariant.SPINE -> Inter
    }

    return TypeTokens(
        displayLg = TextStyle(
            fontFamily = heading,
            fontSize = 30.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 38.sp,
            letterSpacing = (-0.01).em,
        ),
        headlineLg = TextStyle(
            fontFamily = heading,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 34.sp,
            letterSpacing = (-0.01).em,
        ),
        headlineMd = TextStyle(
            fontFamily = heading,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 28.sp,
        ),
        headlineSm = TextStyle(
            fontFamily = heading,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        ),
        bodyLg = TextStyle(
            fontFamily = body,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 28.sp,
        ),
        bodyMd = TextStyle(
            fontFamily = body,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 23.sp,
        ),
        bodySm = TextStyle(
            fontFamily = ui,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 18.sp,
        ),
        spineTitle = TextStyle(
            fontFamily = heading,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            letterSpacing = 0.04.em,
        ),
        spineMeta = TextStyle(
            fontFamily = ui,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 12.sp,
            letterSpacing = 0.08.em,
        ),
        labelLg = TextStyle(
            fontFamily = ui,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 18.sp,
            letterSpacing = 0.01.em,
        ),
        labelMd = TextStyle(
            fontFamily = ui,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp,
            letterSpacing = 0.03.em,
        ),
        labelSm = TextStyle(
            fontFamily = ui,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 14.sp,
            letterSpacing = 0.06.em,
        ),
        reading = Literata,
    )
}
