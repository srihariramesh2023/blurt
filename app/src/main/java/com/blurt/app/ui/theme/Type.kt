package com.blurt.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Blurt's locked type scale — Apple's Human Interface Guidelines, verbatim
 * (design/BLURT-DESIGN-STANDARD.md §3). SF Pro is the reference; Android
 * ships Roboto as the closest system equivalent, used at the SF scale with
 * SF's tracking. One family, one scale: weight and size carry hierarchy,
 * never color alone. Body text never goes below 17pt; nothing goes below
 * 11pt.
 */
object BlurtType {
    /** Large Title 34 / Bold / −0.4 — screen titles (Blurt, Library, Search). */
    val LargeTitle = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        lineHeight = 41.sp,
    )
    /** Title 1 28 / Bold / −0.3 — hero moments. */
    val Title1 = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        lineHeight = 34.sp,
    )
    /** Title 2 22 / SemiBold / −0.25 — the mic question, the confirm blurt. */
    val Title2 = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.25).sp,
        lineHeight = 28.sp,
    )
    /** Title 3 20 / SemiBold / −0.2 — secondary headings. */
    val Title3 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 25.sp,
    )
    /** Headline 17 / SemiBold — button labels, emphasis. */
    val Headline = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.15).sp,
        lineHeight = 22.sp,
    )
    /** Body 17 — the reading size; content never renders below this. */
    val Body = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 24.sp,
    )
    /** Callout 16 — previews, secondary content. */
    val Callout = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 21.sp,
    )
    /** Subheadline 15 — tertiary content. */
    val Subheadline = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 20.sp,
    )
    /** Footnote 13 — metadata. */
    val Footnote = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        lineHeight = 17.sp,
    )
    /** Caption 1 12 / Medium / +0.3 — small labels. */
    val Caption1 = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp,
        lineHeight = 15.sp,
    )
    /** Caption 2 11 / Medium / +0.5 — the smallest text, metadata only. */
    val Caption2 = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
        lineHeight = 13.sp,
    )

    /** Uppercase section labels (RECENT BLURTS, REMINDER, SUGGESTED). */
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
    displaySmall = BlurtType.LargeTitle,
    headlineLarge = BlurtType.Title1,
    headlineMedium = BlurtType.Title2,
    headlineSmall = BlurtType.Title3,
    titleLarge = BlurtType.Title2,
    titleMedium = BlurtType.Title3,
    titleSmall = BlurtType.Subheadline.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BlurtType.Body,
    bodyMedium = BlurtType.Callout,
    bodySmall = BlurtType.Footnote,
    labelLarge = BlurtType.Headline,
    labelMedium = BlurtType.Caption1,
    labelSmall = BlurtType.Caption2,
)
