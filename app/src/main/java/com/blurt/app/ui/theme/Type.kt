package com.blurt.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Blurt's locked type scale — design/BLURT-DESIGN-STANDARD.md §3.
 *
 * SF Pro is the reference: Android ships Roboto as the closest system
 * equivalent, used at the SF scale with SF's tracking values. Typography
 * carries the hierarchy — Display/Title are the brand, everything else
 * defers.
 */
object BlurtType {
    val Display = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        lineHeight = 41.sp,
    )
    val LargeTitle = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        lineHeight = 34.sp,
    )
    val Title1 = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
        lineHeight = 28.sp,
    )
    val Title2 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 25.sp,
    )
    val Title3 = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
        lineHeight = 22.sp,
    )
    val Headline = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
        lineHeight = 22.sp,
    )
    val Body = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 24.sp,
    )
    val Callout = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 21.sp,
    )
    val Subheadline = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 20.sp,
    )
    val Footnote = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 17.sp,
    )
    val Caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
        lineHeight = 15.sp,
    )

    /** Uppercase section labels / small caps (REMINDER, RECENT BLURTS). */
    val CaptionEmphasis = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
        lineHeight = 15.sp,
    )
}

/**
 * Material3 slot mapping onto the locked scale, so components written
 * against MaterialTheme.typography automatically follow the standard.
 */
val BlurtTypography = Typography(
    displaySmall = BlurtType.Display,
    headlineLarge = BlurtType.LargeTitle,
    headlineMedium = BlurtType.Title1,
    headlineSmall = BlurtType.Title3,
    titleLarge = BlurtType.Title2,
    titleMedium = BlurtType.Title3,
    titleSmall = BlurtType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BlurtType.Body,
    bodyMedium = BlurtType.Callout,
    bodySmall = BlurtType.Footnote,
    labelLarge = BlurtType.Headline,
    labelMedium = BlurtType.Caption,
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        lineHeight = 13.sp,
    ),
)
